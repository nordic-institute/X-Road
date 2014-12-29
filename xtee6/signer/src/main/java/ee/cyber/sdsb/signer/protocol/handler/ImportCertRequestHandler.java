package ee.cyber.sdsb.signer.protocol.handler;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.cert.CertChainVerifier;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CertUtils;
import ee.cyber.sdsb.signer.certmanager.OcspResponseManager;
import ee.cyber.sdsb.signer.protocol.dto.CertRequestInfo;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyUsageInfo;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ImportCert;
import ee.cyber.sdsb.signer.protocol.message.ImportCertResponse;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;
import ee.cyber.sdsb.signer.util.SignerUtil;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;

/**
 * Handles certificate import requests.
 */
@Slf4j
public class ImportCertRequestHandler
        extends AbstractDeleteFromKeyInfo<ImportCert> {

    @Override
    protected Object handle(ImportCert message) throws Exception {
        X509Certificate cert = null;
        try {
            cert = readCertificate(message.getCertData());
        } catch (Exception e) {
            throw CodedException.tr(X_INCORRECT_CERTIFICATE,
                    "failed_to_parse_cert",
                    "Failed to parse certificate: %s", e.getMessage());
        }

        String keyId = importCertificate(cert, message.getInitialStatus(),
                message.getMemberId());
        return new ImportCertResponse(keyId);
    }

    private String importCertificate(X509Certificate cert,
            String initialStatus, ClientId memberId) throws Exception {
        String publicKey = encodeBase64(cert.getPublicKey().getEncoded());

        // Find the key based on the public key of the cert
        for (TokenInfo tokenInfo : TokenManager.listTokens()) {
            for (KeyInfo keyInfo : tokenInfo.getKeyInfo()) {
                if (matchesPublicKeyOrExistingCert(publicKey, cert, keyInfo)) {
                    String keyId = keyInfo.getId();
                    log.debug("Importing certificate under key '{}'", keyId);

                    importCertificateToKey(keyInfo, cert, initialStatus,
                            memberId);
                    return keyId;
                }
            }
        }

        throw CodedException.tr(X_KEY_NOT_FOUND,
                "key_not_found_for_certificate",
                "Could not find key that has public key that matches the "
                        + "public key of certificate");
    }

    // XXX: #2955 Currently, if the key does not have public key, we also check
    // if the key contains the (unsaved) certificate
    private boolean matchesPublicKeyOrExistingCert(String publicKey,
            X509Certificate cert, KeyInfo keyInfo) throws Exception {
        if (keyInfo.getPublicKey() != null
                && keyInfo.getPublicKey().equals(publicKey)) {
            return true;
        }

        for (CertificateInfo certInfo : keyInfo.getCerts()) {
            if (Arrays.equals(certInfo.getCertificateBytes(),
                    cert.getEncoded())) {
                return true;
            }
        }

        return false;
    }

    private void importCertificateToKey(KeyInfo keyInfo, X509Certificate cert,
            String initialStatus, ClientId memberId) throws Exception {
        String certHash = calculateCertHexHash(cert.getEncoded());

        CertificateInfo existingCert =
                TokenManager.getCertificateInfoForCertHash(certHash);
        if (existingCert != null && existingCert.isSavedToConfiguration()) {
            throw CodedException.tr(X_CERT_EXISTS,
                    "cert_exists_under_key",
                    "Certificate already exists under key '%s'",
                    keyInfo.getFriendlyName() == null
                        ? keyInfo.getId()
                        : keyInfo.getFriendlyName());
        }

        boolean signing = CertUtils.isSigningCert(cert);
        boolean authentication = CertUtils.isAuthCert(cert);

        if (signing && authentication) {
            throw CodedException.tr(X_WRONG_CERT_USAGE,
                    "wrong_cert_usage.both",
                    "Both signing and authentication, "
                            + "only one of them allowed.");
        }

        KeyUsageInfo keyUsage = getKeyUsage(keyInfo, signing);

        verifyCertForImport(cert);
        validateCertForImport(signing, authentication, keyUsage);

        if (existingCert != null) {
            TokenManager.removeCert(existingCert.getId());
        }

        CertificateInfo certType = new CertificateInfo(memberId,
                !authentication, true, initialStatus, SignerUtil.randomId(),
                cert.getEncoded(), null);

        TokenManager.addCert(keyInfo.getId(), certType);
        TokenManager.setKeyUsage(keyInfo.getId(), keyUsage);
        updateOcspResponse(cert);

        log.info("Imported certificate to key '{}', certificate hash:\n{}",
                keyInfo.getId(), certHash);

        deleteCertRequest(keyInfo.getId(), memberId);
    }

    private void updateOcspResponse(X509Certificate cert) {
        try {
            OcspResponseManager.getOcspResponse(getContext(), cert);
        } catch (Exception e) {
            log.error("Failed to update OCSP response for certificate "
                    + cert.getSerialNumber(), e);
        }
    }

    private void validateCertForImport(boolean signing, boolean authentication,
            KeyUsageInfo keyUsage) {
        // Check that the cert is a signing or auth cert
        if (!signing && !authentication) {
            throw CodedException.tr(X_WRONG_CERT_USAGE,
                    "wrong_cert_usage.none",
                    "Certificate cannot be used for signing nor "
                            + "authentication");
        }

        // Check that the key usage and cert usage match
        if (authentication && !signing
                && keyUsage != KeyUsageInfo.AUTHENTICATION) {
            throw CodedException.tr(X_WRONG_CERT_USAGE,
                    "wrong_cert_usage.auth_to_sign",
                    "Authentication certificate cannot be imported to "
                            + "signing keys");
        }

        if (signing && !authentication && keyUsage != KeyUsageInfo.SIGNING) {
            throw CodedException.tr(X_WRONG_CERT_USAGE,
                    "wrong_cert_usage.sign_to_auth",
                    "Signing certificate cannot be imported to "
                            + "authentication keys");
        }
    }

    private void verifyCertForImport(X509Certificate cert) {
        if (CertUtils.isSelfSigned(cert)) {
            // do not verify self-signed certs
            return;
        }

        try {
            CertChain chain = CertChain.create(
                    GlobalConf.getInstanceIdentifier(), cert, null);
            new CertChainVerifier(chain).verifyChainOnly(new Date());
        } catch (Exception e) {
            String message = (e instanceof CodedException)
                    ? ((CodedException) e).getFaultString() : e.getMessage();
            throw CodedException.tr(X_CERT_IMPORT_FAILED,
                    "cert_import_failed",
                    "Certificate import failed: %s", message);
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
}
