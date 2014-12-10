package ee.cyber.sdsb.signer.protocol.handler;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.GetAuthKey;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.tokenmanager.module.SoftwareModuleType;
import ee.cyber.sdsb.signer.tokenmanager.token.SoftwareTokenType;
import ee.cyber.sdsb.signer.tokenmanager.token.SoftwareTokenUtil;

import static ee.cyber.sdsb.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.tokenNotActive;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.tokenNotInitialized;

/**
 * Handles authentication key retrieval requests.
 */
@Slf4j
public class GetAuthKeyRequestHandler
        extends AbstractRequestHandler<GetAuthKey> {

    @Override
    protected Object handle(GetAuthKey message) throws Exception {
        if (!SoftwareTokenUtil.isTokenInitialized()) {
            throw tokenNotInitialized(SoftwareTokenType.ID);
        }

        if (!TokenManager.isTokenActive(SoftwareTokenType.ID)) {
            throw tokenNotActive(SoftwareTokenType.ID);
        }

        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            if (!SoftwareModuleType.TYPE.equals(tokenInfo.getType())) {
                continue;
            }

            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.getUsage() != KeyUsageInfo.AUTHENTICATION) {
                    continue;
                }

                if (!keyInfo.isAvailable()) {
                    continue;
                }

                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (authCertValid(certInfo, message.getSecurityServer())) {
                        return authKeyResponse(keyInfo, certInfo);
                    }
                }
            }
        }

        throw CodedException.tr(X_KEY_NOT_FOUND,
                "auth_key_not_found_for_server",
                "Could not find active authentication key for "
                        + "security server '%s'", message.getSecurityServer());
    }

    private AuthKeyInfo authKeyResponse(KeyInfo keyInfo,
            CertificateInfo certInfo) throws Exception {
        String alias = keyInfo.getId();
        String keyStoreFileName = SoftwareTokenUtil.getKeyStoreFileName(alias);
        char[] password = PasswordStore.getPassword(SoftwareTokenType.ID);

        return new AuthKeyInfo(alias, keyStoreFileName, password, certInfo);
    }

    private boolean authCertValid(CertificateInfo certInfo,
            SecurityServerId securityServer) throws Exception {
        if (!certInfo.isActive()) {
            return false;
        }

        if (!isRegistered(certInfo.getStatus())) {
            return false;
        }

        X509Certificate cert = readCertificate(certInfo.getCertificateBytes());
        try {
            cert.checkValidity();

            if (securityServer.equals(GlobalConf.getServerId(cert))) {
                verifyOcspResponse(securityServer.getSdsbInstance(), cert,
                        certInfo.getOcspBytes());
                return true;
            }
        } catch (Exception e) {
            log.warn("Ignoring authentication certificate '{}' because: {}",
                    cert.getSubjectX500Principal().getName(),
                    e.getMessage());
        }

        return false;
    }

    private void verifyOcspResponse(String instanceIdentifier,
            X509Certificate subject, byte[] ocspBytes) throws Exception {
        if (ocspBytes == null) {
            throw new CertificateException("OCSP response not found");
        }

        OCSPResp ocsp = new OCSPResp(ocspBytes);
        X509Certificate issuer =
                GlobalConf.getCaCert(instanceIdentifier, subject);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false));
        verifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

    private static boolean isRegistered(String status) {
        return status != null
                && status.startsWith(CertificateInfo.STATUS_REGISTERED);
    }

}
