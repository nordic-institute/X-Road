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
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.common.util.filewatcher.FileWatchListener;
import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;
import ee.ria.xroad.common.util.filewatcher.FileWatcherStartupListener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
public class CachingKeyConfImpl extends KeyConfImpl {

    // Specifies how long data is cached
    private static final int CACHE_PERIOD_SECONDS = 300;

    private final Cache<ClientId, SigningInfo> signingInfoCache;
    private final Cache<SecurityServerId, AuthKeyInfo> authKeyInfoCache;

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
    }

    @Override
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
            throw new CodedException(ErrorCodes.X_CANNOT_CREATE_SIGNATURE, "Failed to get signing info for member '%s': %s",
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

        org.niis.xroad.signer.api.dto.AuthKeyInfo keyInfo = signerRpcClient.getAuthKey(serverId);

        CertChain certChain = getAuthCertChain(serverId.getXRoadInstance(), keyInfo.getCert().getCertificateBytes());

        List<OCSPResp> ocspResponses = getOcspResponses(certChain.getAdditionalCerts());
        ocspResponses.add(new OCSPResp(keyInfo.getCert().getOcspBytes()));

        PrivateKey key = loadAuthPrivateKey(keyInfo);

        // Lower bound for validity is "now", verify validity of the chain at that time.
        final Date notBefore = new Date();
        CertChainVerifier verifier = new CertChainVerifier(globalConfProvider, certChain);
        verifier.verify(ocspResponses, notBefore);

        final Date notAfter = calculateNotAfter(ocspResponses, certChain.notAfter());
        return new AuthKeyInfo(key, certChain, notBefore, notAfter);
    }

    protected void watcherStarted() {
        //for testability
    }

    public static FileWatcherRunner createChangeWatcher(FileWatchListener onChange) throws IOException, OperatorCreationException {
        return createChangeWatcher(() -> { }, onChange, new FileContentChangeChecker(SystemProperties.getKeyConfFile()));
    }

    static FileWatcherRunner createChangeWatcher(FileWatcherStartupListener onStart,
                                                 FileWatchListener onChange,
                                                 FileContentChangeChecker changeChecker) {
        return FileWatcherRunner.create()
                .watchForChangesIn(Paths.get(changeChecker.getFileName()))
                .listenToCreate()
                .listenToModify()
                .andOnChangeNotify(() -> {
                    boolean changed = true;
                    try {
                        changed = changeChecker.hasChanged();
                    } catch (Exception e) {
                        log.error("Failed to check if key conf has changed", e);
                    }
                    if (changed) onChange.fileModified();
                })
                .andOnStartupNotify(onStart)
                .buildAndStartWatcher();
    }
}
