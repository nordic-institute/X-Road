package ee.cyber.sdsb.signer.protocol.handler;

import java.security.cert.X509Certificate;
import java.util.Date;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.cert.CertHelper;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.signer.conf.ServerConf;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ImportCert;
import ee.cyber.sdsb.signer.protocol.message.ImportCertResponse;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

public class ImportCertRequestHandler
        extends AbstractDeleteFromKeyInfo<ImportCert> {

    @Override
    protected Object handle(ImportCert message) throws Exception {
        X509Certificate cert = null;
        try {
            cert = readCertificate(message.getCertData());
        } catch (Exception e) {
            throw new CodedException(X_INCORRECT_CERTIFICATE, e);
        }

        String keyId = importCertificate(cert, message.getInitialStatus());
        return new ImportCertResponse(keyId);
    }

    private String importCertificate(X509Certificate cert,
            String initialStatus) throws Exception {
        String publicKey = encodeBase64(cert.getPublicKey().getEncoded());

        // Find the key based on the public key of the cert
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (keyInfo.getPublicKey() != null
                        && keyInfo.getPublicKey().equals(publicKey)) {
                    String keyId = keyInfo.getId();
                    LOG.debug("Importing certificate under key '{}'", keyId);

                    importCertificateToKey(keyInfo, cert, initialStatus);
                    return keyId;
                }
            }
        }

        throw new CodedException(X_KEY_NOT_FOUND,
                "Could not find key that has public key that matches the " +
                    "public key of certificate");
    }

    private void importCertificateToKey(KeyInfo keyInfo, X509Certificate cert,
            String initialStatus) throws Exception {
        String certHash = calculateCertHexHash(cert.getEncoded());

        CertificateInfo existingCert =
                TokenManager.getCertificateInfoForCertHash(certHash);
        if (existingCert != null && existingCert.isSavedToConfiguration()) {
            throw new CodedException(X_CERT_EXISTS,
                    "Certificate already exists under key '%s'",
                    keyInfo.getId());
        }

        boolean signing = CertUtils.isSigningCert(cert);
        boolean authentication = CertUtils.isAuthCert(cert);

        if (signing && authentication) {
            throw new CodedException(X_WRONG_CERT_USAGE, "Both signing and " +
                    "authentication, only one of them allowed.");
        }

        KeyUsageInfo keyUsage = getKeyUsage(keyInfo, signing);

        verifyCertForImport(cert);
        validateCertForImport(signing, authentication, keyUsage);

        ClientId memberId = getMemberId(keyUsage, cert);

        boolean active = false;
        if (!authentication) {
            active = true; // TODO: Active based on the cert data?
        }

        if (existingCert != null) {
            TokenManager.removeCert(existingCert.getId());
        }

        CertificateInfo certType = new CertificateInfo(memberId, active, true,
                initialStatus, SignerUtil.randomId(), cert.getEncoded());

        TokenManager.addCert(keyInfo.getId(), certType);
        TokenManager.setKeyUsage(keyInfo.getId(), keyUsage);

        LOG.info("Imported certificate to key '{}', certificate hash:\n{}",
                keyInfo.getId(), certHash);

        deleteCertRequest(keyInfo.getId(), memberId);
    }

    private void validateCertForImport(boolean signing, boolean authentication,
            KeyUsageInfo keyUsage) {
        // Check that the cert is a signing or auth cert
        if (!signing && !authentication) {
            throw new CodedException(X_WRONG_CERT_USAGE,
                    "Certificate cannot be used for signing nor " +
                        "authentication");
        }

        // Check that the key usage and cert usage match
        if (authentication && !signing
                && keyUsage != KeyUsageInfo.AUTHENTICATION) {
            throw new CodedException(X_WRONG_CERT_USAGE,
                    "Authentication certificate cannot be imported to " +
                        "signing keys");
        }

        if (signing && !authentication && keyUsage != KeyUsageInfo.SIGNING) {
            throw new CodedException(X_WRONG_CERT_USAGE,
                    "Signing certificate cannot be imported to " +
                        "authentication keys");
        }
    }

    private void verifyCertForImport(X509Certificate cert) {
        try {
            CertChain chain = CertHelper.buildChain(cert, null);
            chain.verifyChainOnly(new Date());
        } catch (Exception e) {
            throw new CodedException(X_CERT_VALIDATION, e);
        }
    }

    protected void deleteCertRequest(String keyId, ClientId memberId)
            throws Exception {
        CertRequestInfo certReq =
                TokenManager.getCertRequestInfo(keyId, memberId);
        if (certReq != null) {
            deleteCertRequest(certReq.getId());
        }
    }

    private static KeyUsageInfo getKeyUsage(KeyInfo keyInfo, boolean sign) {
        KeyUsageInfo keyUsage = keyInfo.getUsage();
        if (keyUsage == null) {
            return sign ? KeyUsageInfo.SIGNING : KeyUsageInfo.AUTHENTICATION;
        }

        return keyUsage;
    }

    private static ClientId getMemberId(KeyUsageInfo keyUsage,
            X509Certificate cert) throws Exception {
        ClientId memberId = null;
        if (keyUsage == KeyUsageInfo.SIGNING) {
            memberId = GlobalConf.getSubjectName(cert);
            if (memberId == null) {
                throw new CodedException(X_NO_MEMBERID_FROM_CERT,
                        "Certificate does not specify member id");
            }

            if (!ServerConf.hasMember(memberId)) {
                throw new CodedException(X_UNKNOWN_MEMBER,
                        "Member '%s' not found ", memberId);
            }
        }

        return memberId;
    }
}
