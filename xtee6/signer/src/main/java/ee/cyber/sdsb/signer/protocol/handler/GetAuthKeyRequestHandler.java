package ee.cyber.sdsb.signer.protocol.handler;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.util.PasswordStore;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.core.device.SoftwareDeviceType;
import ee.cyber.sdsb.signer.core.device.SoftwareTokenType;
import ee.cyber.sdsb.signer.core.token.SoftwareTokenUtil;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.AuthKeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.GetAuthKey;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

public class GetAuthKeyRequestHandler
        extends AbstractRequestHandler<GetAuthKey> {

    @Override
    protected Object handle(GetAuthKey message) throws Exception {
        if (!SoftwareTokenUtil.isTokenInitialized()) {
            throw new CodedException(X_TOKEN_NOT_INITIALIZED,
                    "Software token not initialized");
        }

        if (!TokenManager.isTokenActive(SoftwareTokenType.ID)) {
            throw new CodedException(X_TOKEN_NOT_ACTIVE,
                    "Software token not active");
        }

        // TODO: How the auth key and certificate are chosen?
        // XXX: Currently choose the first softKey token and select suitable certificate
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            if (!SoftwareDeviceType.TYPE.equals(tokenInfo.getType())) {
                continue;
            }

            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.getUsage() != KeyUsageInfo.AUTHENTICATION) {
                    continue;
                }

                for (CertificateInfo certInfo : keyInfo.getCerts()) {
                    if (authCertValid(certInfo, message.getSecurityServer())) {
                        return authKeyResponse(keyInfo, certInfo);
                    }
                }
            }
        }

        throw new CodedException(X_KEY_NOT_FOUND,
                "Could not find active authentication key for " +
                "security server '%s'", message.getSecurityServer());
    }

    private AuthKeyInfo authKeyResponse(KeyInfo keyInfo,
            CertificateInfo certInfo) throws Exception {
        String alias = keyInfo.getId();
        String keyStoreFileName = SoftwareTokenUtil.getKeyStoreFileName(alias);
        char[] password = PasswordStore.getPassword(SoftwareTokenType.ID);
        byte[] cert = certInfo.getCertificateBytes();

        return new AuthKeyInfo(alias, keyStoreFileName, password, cert);
    }

    private boolean authCertValid(CertificateInfo certInfo,
            SecurityServerId securityServer) throws Exception {
        if (certInfo.isActive()) {
            X509Certificate cert =
                    readCertificate(certInfo.getCertificateBytes());
            try {
                cert.checkValidity();
                return GlobalConf.hasAuthCert(cert, securityServer);
            } catch (CertificateExpiredException
                    | CertificateNotYetValidException e) {
                LOG.warn("Ignoring authentication certificate '{}' because: {}",
                        cert.getSubjectX500Principal().getName(),
                        e.getMessage());
            }
        }

        return false;
    }

}
