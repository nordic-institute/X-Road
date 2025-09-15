/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.keyconf.impl;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainVerifier;
import org.niis.xroad.keyconf.SigningInfo;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
@Singleton
public class CachingKeyConfImpl extends KeyConfImpl {

    // Specifies how long data is cached
    private static final int CACHE_PERIOD_SECONDS = 300;

    private final Cache<ClientId, SigningInfo> signingInfoCache;
    private final Cache<SecurityServerId, AuthKeyInfo> authKeyInfoCache;

    private final int checkPeriod = 5;
    private final ScheduledExecutorService taskScheduler;

    private Integer previousChecksum;

    private final List<WeakReference<KeyConfChangeListener>> listeners = new ArrayList<>();

    public CachingKeyConfImpl(GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider,
                              SignerRpcClient signerRpcClient) {
        super(globalConfProvider, serverConfProvider, signerRpcClient);
        signingInfoCache = CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_PERIOD_SECONDS, TimeUnit.SECONDS)
                .build();
        authKeyInfoCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(CACHE_PERIOD_SECONDS, TimeUnit.SECONDS)
                .build();
        taskScheduler = Executors.newSingleThreadScheduledExecutor();
        taskScheduler.scheduleAtFixedRate(this::checkForKeyConfChanges, checkPeriod, checkPeriod, TimeUnit.SECONDS);
    }

    @Override
    @PreDestroy
    public void destroy() {
        invalidateCaches();

        super.destroy();
    }

    @Override
    public SigningInfo getSigningInfo(ClientId clientId) {
        try {
            SigningInfo signingInfo = signingInfoCache.get(clientId, () -> createSigningInfo(clientId));
            if (!signingInfo.verifyValidity(new Date())) {
                signingInfoCache.invalidate(clientId);
                signingInfo = signingInfoCache.get(clientId, () -> createSigningInfo(clientId));
            }
            return signingInfo;

        } catch (ExecutionException e) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE, "Failed to get signing info for member '%s': %s",
                    clientId, e);
        }
    }

    /**
     * Invalidates both auth key and signing info caches
     */
    public void invalidateCaches() {
        authKeyInfoCache.invalidateAll();
        signingInfoCache.invalidateAll();
    }

    private static final AuthKey NULL_AUTH_KEY = new AuthKey(null, null);

    @Override
    public AuthKey getAuthKey() {
        try {
            final SecurityServerId serverId = serverConfProvider.getIdentifier();
            if (serverId == null) {
                return NULL_AUTH_KEY;
            }

            AuthKeyInfo info = authKeyInfoCache.get(serverId, () -> getAuthKeyInfo(serverId));
            if (!info.verifyValidity(new Date())) {
                // we likely got an old auth key from cache, and refresh should fix this
                authKeyInfoCache.invalidate(serverId);
                info = authKeyInfoCache.get(serverId, () -> getAuthKeyInfo(serverId));
            }
            return info.getAuthKey();
        } catch (Exception e) {
            log.error("Failed to get authentication key", e);
            return NULL_AUTH_KEY;
        }
    }

    protected AuthKeyInfo getAuthKeyInfo(SecurityServerId serverId)
            throws CertificateException, IOException, UnrecoverableKeyException, KeyStoreException,
            NoSuchAlgorithmException, OCSPException {
        log.debug("Retrieving authentication info for security server '{}'", serverId);

        var keyInfo = signerRpcClient.getAuthKey(serverId);

        CertChain certChain = getAuthCertChain(serverId.getXRoadInstance(), keyInfo.cert().getCertificateBytes());

        List<OCSPResp> ocspResponses = getOcspResponses(certChain.getAdditionalCerts());
        ocspResponses.add(new OCSPResp(keyInfo.cert().getOcspBytes()));

        PrivateKey key = keyInfo.key();

        // Lower bound for validity is "now", verify validity of the chain at that time.
        final Date notBefore = new Date();
        CertChainVerifier verifier = new CertChainVerifier(globalConfProvider, certChain);
        verifier.verify(ocspResponses, notBefore);

        final Date notAfter = calculateNotAfter(ocspResponses, certChain.notAfter());
        return new AuthKeyInfo(key, certChain, notBefore, notAfter);
    }

    void checkForKeyConfChanges() {
        try {
            int checkSum = signerRpcClient.getKeyConfChecksum();
            if (previousChecksum == null || !previousChecksum.equals(checkSum)) {
                log.info("Key conf checksum changed ({}->{}), invalidating CachingKeyConf caches.", previousChecksum, checkSum);
                previousChecksum = checkSum;
                invalidateCaches();
                notifyListeners();
            }
        } catch (Exception e) {
            log.error("Failed to get key conf checksum", e);
            invalidateCaches();
        }
    }

    @Override
    public void addChangeListener(KeyConfChangeListener listener) {
        listeners.add(new WeakReference<>(listener));
    }

    private void notifyListeners() {
        Iterator<WeakReference<KeyConfChangeListener>> it = listeners.iterator();
        while (it.hasNext()) {
            var listener = it.next().get();
            if (listener != null) {
                listener.keyConfChanged();
            } else {
                it.remove();
            }
        }
    }
}
