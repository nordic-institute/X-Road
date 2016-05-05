/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.FileContentChangeChecker;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.MemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.GetAuthKey;
import ee.ria.xroad.signer.protocol.message.GetMemberSigningInfo;

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

    private final Map<ClientId, CachedSigningInfoImpl> signingInfoCache =
            new HashMap<>();

    private CachedAuthKeyInfoImpl authKeyInfo;

    CachingKeyConfImpl() throws Exception {
        keyConfChangeChecker =
                new FileContentChangeChecker(SystemProperties.getKeyConfFile());
    }

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        CachedSigningInfoImpl signingInfo = signingInfoCache.get(clientId);
        try {
            if (hasExpired(signingInfo) || keyConfHasChanged()) {
                signingInfo = getSigningInfo(clientId);
            }

            signingInfoCache.put(clientId, signingInfo);
            return signingInfo.getSigningCtx();
        } catch (Exception e) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                    "Failed to get signing info for member '%s': %s",
                    clientId, e);
        }
    }

    @Override
    public AuthKey getAuthKey() {
        try {
            if (hasExpired(authKeyInfo) || keyConfHasChanged()) {
                authKeyInfo = getAuthKeyInfo();
            }

            return authKeyInfo.getAuthKey();
        } catch (Exception e) {
            log.error("Failed to get authentication key", e);
            return new AuthKey(null, null);
        }
    }

    boolean keyConfHasChanged() {
        try {
            boolean changed = keyConfChangeChecker.hasChanged();
            log.trace("KeyConf has{} changed!", !changed ? " not" : "");
            return changed;
        } catch (Exception e) {
            log.error("Failed to check if key conf has changed", e);
            return true;
        }
    }

    private CachedAuthKeyInfoImpl getAuthKeyInfo() throws Exception {
        SecurityServerId serverId = ServerConf.getIdentifier();
        log.debug("Retrieving authentication info for security server '{}'",
                serverId);

        AuthKeyInfo keyInfo = SignerClient.execute(new GetAuthKey(serverId));

        CertChain certChain = getAuthCertChain(serverId.getXRoadInstance(),
                keyInfo.getCert().getCertificateBytes());

        List<OCSPResp> ocspResponses =
                getOcspResponses(certChain.getAdditionalCerts());
        ocspResponses.add(new OCSPResp(keyInfo.getCert().getOcspBytes()));

        PrivateKey key = loadAuthPrivateKey(keyInfo);
        return new CachedAuthKeyInfoImpl(key, certChain, ocspResponses);
    }

    private static CachedSigningInfoImpl getSigningInfo(ClientId clientId)
            throws Exception {
        log.debug("Retrieving signing info for member '{}'", clientId);

        MemberSigningInfo signingInfo =
                SignerClient.execute(new GetMemberSigningInfo(clientId));

        X509Certificate cert =
                readCertificate(signingInfo.getCert().getCertificateBytes());
        OCSPResp ocsp = new OCSPResp(signingInfo.getCert().getOcspBytes());

        return new CachedSigningInfoImpl(signingInfo.getKeyId(), clientId,
                cert, ocsp);
    }

    private static boolean hasExpired(AbstractCachedInfo cachedInfo) {
        log.trace("CachingKeyConfImpl.hasExpired cachedInfo={}", cachedInfo);
        return cachedInfo == null
                || cachedInfo.getCreatedAt().plusSeconds(CACHE_PERIOD_SECONDS)
                        .isBeforeNow()
                || !cachedInfo.verifyValidity(new Date());
    }
}
