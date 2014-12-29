package ee.cyber.sdsb.proxy.conf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.globalconf.AuthKey;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.FileContentChangeChecker;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetAuthKey;
import ee.cyber.sdsb.signer.protocol.message.GetMemberSigningInfo;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

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

        CertChain certChain = getAuthCertChain(serverId.getSdsbInstance(),
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
        return cachedInfo == null
                || cachedInfo.getCreatedAt().plusSeconds(CACHE_PERIOD_SECONDS)
                        .isBeforeNow()
                || !cachedInfo.verifyValidity(new Date());
    }
}
