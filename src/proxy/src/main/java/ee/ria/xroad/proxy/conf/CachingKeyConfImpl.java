/**
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
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.MemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.GetAuthKey;
import ee.ria.xroad.signer.protocol.message.GetMemberSigningInfo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
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

    private final FileContentChangeChecker keyConfChangeChecker;

    private static final Cache<ClientId, SigningInfo> SIGNING_INFO_CACHE;

    static {
        SIGNING_INFO_CACHE = CacheBuilder.newBuilder()
                .expireAfterWrite(CACHE_PERIOD_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static final Cache<SecurityServerId, AuthKeyInfo> AUTH_KEY_CACHE;

    static {
        AUTH_KEY_CACHE = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(CACHE_PERIOD_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    CachingKeyConfImpl() throws Exception {
        keyConfChangeChecker = getKeyConfChangeChecker();
    }

    protected FileContentChangeChecker getKeyConfChangeChecker() throws Exception {
        return new FileContentChangeChecker(SystemProperties.getKeyConfFile());
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        try {
            if (keyConfHasChanged()) {
                CachingKeyConfImpl.invalidateCaches();
            }
            SigningInfo signingInfo = SIGNING_INFO_CACHE.get(clientId, () -> getSigningInfo(clientId));
            if (!signingInfo.verifyValidity(new Date())) {
                SIGNING_INFO_CACHE.invalidate(clientId);
                signingInfo = SIGNING_INFO_CACHE.get(clientId, () -> getSigningInfo(clientId));
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
    protected static void invalidateCaches() {
        AUTH_KEY_CACHE.invalidateAll();
        SIGNING_INFO_CACHE.invalidateAll();
    }


    private static final AuthKey NULL_AUTH_KEY = new AuthKey(null, null);

    @Override
    public AuthKey getAuthKey() {
        try {
            if (keyConfHasChanged()) {
                CachingKeyConfImpl.invalidateCaches();
            }

            final SecurityServerId serverId = ServerConf.getIdentifier();
            if (serverId == null) {
                return NULL_AUTH_KEY;
            }

            AuthKeyInfo info = AUTH_KEY_CACHE.get(serverId, () -> getAuthKeyInfo(serverId));
            if (!info.verifyValidity(new Date())) {
                // we likely got an old auth key from cache, and refresh should fix this
                AUTH_KEY_CACHE.invalidate(serverId);
                info = AUTH_KEY_CACHE.get(serverId, () -> getAuthKeyInfo(serverId));
            }
            return info.getAuthKey();
        } catch (Exception e) {
            log.error("Failed to get authentication key", e);
            return NULL_AUTH_KEY;
        }
    }

    boolean keyConfHasChanged() {
        try {
            return keyConfChangeChecker.hasChanged();
        } catch (Exception e) {
            log.error("Failed to check if key conf has changed", e);
            return true;
        }
    }

    protected AuthKeyInfo getAuthKeyInfo(SecurityServerId serverId) throws Exception {
        log.debug("Retrieving authentication info for security server '{}'", serverId);

        ee.ria.xroad.signer.protocol.dto.AuthKeyInfo keyInfo = SignerClient.execute(new GetAuthKey(serverId));

        CertChain certChain = getAuthCertChain(serverId.getXRoadInstance(), keyInfo.getCert().getCertificateBytes());

        List<OCSPResp> ocspResponses = getOcspResponses(certChain.getAdditionalCerts());
        ocspResponses.add(new OCSPResp(keyInfo.getCert().getOcspBytes()));

        PrivateKey key = loadAuthPrivateKey(keyInfo);

        return new AuthKeyInfo(key, certChain, ocspResponses);
    }

    protected SigningInfo getSigningInfo(ClientId clientId) throws Exception {
        log.debug("Retrieving signing info for member '{}'", clientId);

        MemberSigningInfo signingInfo = SignerClient.execute(new GetMemberSigningInfo(clientId));

        X509Certificate cert = readCertificate(signingInfo.getCert().getCertificateBytes());
        OCSPResp ocsp = new OCSPResp(signingInfo.getCert().getOcspBytes());

        return new SigningInfo(signingInfo.getKeyId(), signingInfo.getSignMechanismName(), clientId, cert, ocsp);
    }
}
