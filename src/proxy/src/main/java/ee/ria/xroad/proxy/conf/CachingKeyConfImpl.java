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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.SignerProxy.MemberSigningInfoDto;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;

import java.lang.ref.WeakReference;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Encapsulates KeyConf related functionality.
 */
@Slf4j
class CachingKeyConfImpl extends KeyConfImpl {

    // Specifies how long data is cached
    private static final int CACHE_PERIOD_SECONDS = 300;

    private final Cache<ClientId, SigningInfo> signingInfoCache;
    private final Cache<SecurityServerId, AuthKeyInfo> authKeyInfoCache;
    private FileWatcherRunner keyConfChangeWatcher;

    CachingKeyConfImpl() {
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
        if (keyConfChangeWatcher != null) {
            keyConfChangeWatcher.stop();
        }
        super.destroy();
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        try {
            SigningInfo signingInfo = signingInfoCache.get(clientId, () -> getSigningInfo(clientId));
            if (!signingInfo.verifyValidity(new Date())) {
                signingInfoCache.invalidate(clientId);
                signingInfo = signingInfoCache.get(clientId, () -> getSigningInfo(clientId));
            }
            return signingInfo.getSigningCtx();

        } catch (ExecutionException e) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE, "Failed to get signing info for member '%s': %s",
                    clientId, e);
        }
    }

    /**
     * Invalidates both auth key and signing info caches
     */
    protected void invalidateCaches() {
        authKeyInfoCache.invalidateAll();
        signingInfoCache.invalidateAll();
    }

    private static final AuthKey NULL_AUTH_KEY = new AuthKey(null, null);

    @Override
    public AuthKey getAuthKey() {
        try {
            final SecurityServerId serverId = ServerConf.getIdentifier();
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

    protected AuthKeyInfo getAuthKeyInfo(SecurityServerId serverId) throws Exception {
        log.debug("Retrieving authentication info for security server '{}'", serverId);

        ee.ria.xroad.signer.protocol.dto.AuthKeyInfo keyInfo = SignerProxy.getAuthKey(serverId);

        CertChain certChain = getAuthCertChain(serverId.getXRoadInstance(), keyInfo.getCert().getCertificateBytes());

        List<OCSPResp> ocspResponses = getOcspResponses(certChain.getAdditionalCerts());
        ocspResponses.add(new OCSPResp(keyInfo.getCert().getOcspBytes()));

        PrivateKey key = loadAuthPrivateKey(keyInfo);

        // Lower bound for validity is "now", verify validity of the chain at that time.
        final Date notBefore = new Date();
        CertChainVerifier verifier = new CertChainVerifier(certChain);
        verifier.verify(ocspResponses, notBefore);

        final Date notAfter = calculateNotAfter(ocspResponses, certChain.notAfter());
        return new AuthKeyInfo(key, certChain, notBefore, notAfter);
    }

    protected SigningInfo getSigningInfo(ClientId clientId) throws Exception {
        log.debug("Retrieving signing info for member '{}'", clientId);

        MemberSigningInfoDto signingInfo = SignerProxy.getMemberSigningInfo(clientId);
        X509Certificate cert = readCertificate(signingInfo.getCert().getCertificateBytes());
        OCSPResp ocsp = new OCSPResp(signingInfo.getCert().getOcspBytes());

        //Signer already checks the validity of the signing certificate. Just record the bounds
        //the certificate and ocsp response is valid for.
        Date notAfter = calculateNotAfter(Collections.singletonList(ocsp), cert.getNotAfter());
        return new SigningInfo(signingInfo.getKeyId(), signingInfo.getSignMechanismName(), clientId, cert, new Date(),
                notAfter);
    }

    protected void watcherStarted() {
        //for testability
    }

    /*
     * Upper bound for validity is the minimum of certificates "notAfter" and OCSP responses validity time
     * An OCSP reponse validity time is min(thisUpdate + ocspFresnessSeconds, nextUpdate) or just
     * (thisUpdate + ocspFresnessSeconds) if nextUpdate is not enforced or missing
     */
    static Date calculateNotAfter(List<OCSPResp> ocspResponses, Date notAfter) throws OCSPException {
        final long freshnessMillis = 1000L * GlobalConf.getOcspFreshnessSeconds();
        final boolean verifyNextUpdate = GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate();

        for (OCSPResp resp : ocspResponses) {
            //ok to expect only one response since we request ocsp responses for one certificate at a time
            final SingleResp singleResp = ((BasicOCSPResp) resp.getResponseObject()).getResponses()[0];
            final Date freshUntil = new Date(singleResp.getThisUpdate().getTime() + freshnessMillis);
            if (freshUntil.before(notAfter)) notAfter = freshUntil;
            if (verifyNextUpdate) {
                final Date nextUpdate = singleResp.getNextUpdate();
                if (nextUpdate != null && nextUpdate.before(notAfter)) {
                    notAfter = nextUpdate;
                }
            }
        }
        return notAfter;
    }

    /**
     * Create a new CachingKeyConf instance and set up keyconf change watcher.
     */
    static CachingKeyConfImpl newInstance() throws Exception {
        final FileContentChangeChecker changeChecker = new FileContentChangeChecker(SystemProperties.getKeyConfFile());
        final CachingKeyConfImpl instance = new CachingKeyConfImpl();
        // the change watcher can not be created in the constructor, because that would publish the
        // instance reference to another thread before the constructor finishes.
        instance.keyConfChangeWatcher = createChangeWatcher(new WeakReference<>(instance), changeChecker);
        return instance;
    }

    /* Implementation note:
     * Weak reference for the callback is used so that CachingKeyConf instance can be garbage collected
     * (e.g. after KeyConf reload). Otherwise, the FileWatcher background thread keeps it alive and creates a leak
     * if one fails to call destroy.
     */
    static FileWatcherRunner createChangeWatcher(WeakReference<CachingKeyConfImpl> ref,
            FileContentChangeChecker changeChecker) {
        return FileWatcherRunner.create()
                .watchForChangesIn(Paths.get(changeChecker.getFileName()))
                .listenToCreate()
                .listenToModify()
                .andOnChangeNotify(() -> {
                    final CachingKeyConfImpl conf = ref.get();
                    if (conf == null) {
                        //stop watcher since the CachingKeyConf has become garbage
                        Thread.currentThread().interrupt();
                        return;
                    }
                    boolean changed = true;
                    try {
                        changed = changeChecker.hasChanged();
                    } catch (Exception e) {
                        log.error("Failed to check if key conf has changed", e);
                    }
                    if (changed) conf.invalidateCaches();
                })
                .andOnStartupNotify(() -> {
                    final CachingKeyConfImpl conf = ref.get();
                    if (conf != null) {
                        conf.watcherStarted();
                    }
                })
                .buildAndStartWatcher();
    }
}
