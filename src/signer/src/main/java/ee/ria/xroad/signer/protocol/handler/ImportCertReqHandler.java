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
package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.protocol.AbstractRpcHandler;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.mapper.ClientIdMapper;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.SignerUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.proto.ImportCertReq;
import org.niis.xroad.signer.proto.ImportCertResp;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Date;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_IMPORT_FAILED;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Handles certificate import requests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportCertReqHandler extends AbstractRpcHandler<ImportCertReq, ImportCertResp> {
    private final DeleteCertRequestReqHandler deleteCertRequestReqHandler;
    private final OcspResponseManager ocspResponseManager;

    @Override
    protected ImportCertResp handle(ImportCertReq request) throws Exception {
        X509Certificate cert = null;
        try {
            cert = readCertificate(request.getCertData().toByteArray());
        } catch (Exception e) {
            throw CodedException.tr(X_INCORRECT_CERTIFICATE,
                    "failed_to_parse_cert",
                    "Failed to parse certificate: %s", e.getMessage());
        }

        String keyId = importCertificate(cert, request.getInitialStatus(),
                request.hasMemberId() ? ClientIdMapper.fromDto(request.getMemberId()) : null);

        return ImportCertResp.newBuilder()
                .setKeyId(keyId)
                .build();
    }

    public String importCertificate(X509Certificate cert,
                                    String initialStatus, ClientId.Conf memberId) throws Exception {
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
                                        String initialStatus, ClientId.Conf memberId) throws Exception {
        String certHash = calculateCertHexHash(cert);

        CertificateInfo existingCert = TokenManager.getCertificateInfoForCertHash(certHash);
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

        validateCertKeyUsage(signing, authentication, keyUsage);
        verifyCertChain(cert);

        if (existingCert != null) {
            TokenManager.removeCert(existingCert.getId());
        }

        TokenManager.addCert(keyInfo.getId(), memberId,
                !authentication, true, initialStatus, SignerUtil.randomId(),
                cert.getEncoded());
        TokenManager.setKeyUsage(keyInfo.getId(), keyUsage);
        updateOcspResponse(cert);

        log.info("Imported certificate to key '{}', certificate hash:\n{}",
                keyInfo.getId(), certHash);

        deleteCertRequest(keyInfo.getId(), memberId);
    }

    private void updateOcspResponse(X509Certificate cert) {
        try {
            ocspResponseManager.getOcspResponse(cert);
        } catch (Exception e) {
            log.error("Failed to update OCSP response for certificate "
                    + cert.getSerialNumber(), e);
        }
    }

    private void validateCertKeyUsage(boolean signing, boolean authentication,
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

    private void verifyCertChain(X509Certificate cert) {
        if (CertUtils.isSelfSigned(cert)) {
            // do not verify self-signed certs
            return;
        }

        GlobalConf.verifyValidity();
        try {
            CertChain chain = CertChain.create(
                    GlobalConf.getInstanceIdentifier(), cert, null);
            new CertChainVerifier(chain).verifyChainOnly(new Date());
        } catch (Exception e) {
            log.error("Failed to import certificate", e);
            throw CodedException.tr(X_CERT_IMPORT_FAILED,
                    "cert_import_failed", "%s", "Certificate is not valid");
        }
    }

    private void deleteCertRequest(String keyId, ClientId memberId) throws Exception {
        CertRequestInfo certReq = TokenManager.getCertRequestInfo(keyId, memberId);
        if (certReq != null) {
            deleteCertRequestReqHandler.deleteCertRequest(certReq.getId());
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
