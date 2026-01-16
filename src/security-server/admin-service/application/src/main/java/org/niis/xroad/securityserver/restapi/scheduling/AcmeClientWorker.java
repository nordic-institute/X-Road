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
package org.niis.xroad.securityserver.restapi.scheduling;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.securityserver.restapi.acme.AcmeConfig;
import org.niis.xroad.securityserver.restapi.acme.AcmeService;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

import static ee.ria.xroad.common.util.CertUtils.getCommonName;
import static ee.ria.xroad.common.util.CertUtils.isAuthCert;
import static ee.ria.xroad.common.util.CertUtils.isSigningCert;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * This class is responsible for retrieving the ACME certificates renewal information from the ACME
 * server and renewing the certificates as needed.
 * <p>
 * The renewal information is queried from the server at a fixed interval.
 */
@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class AcmeClientWorker {

    private final AcmeService acmeService;
    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfService serverConfService;
    private final VaultKeyProvider vaultKeyProvider;
    private final MailNotificationHelper mailNotificationHelper;
    private final AcmeConfig acmeConfig;
    private final AdminServiceProperties adminServiceProperties;

    public void execute(CertificateRenewalScheduler acmeRenewalScheduler) {
        log.info("ACME certificate renewal cycle started");

        if (!globalConfProvider.isValid()) {
            log.debug("invalid global conf, returning");
            if (acmeRenewalScheduler != null) {
                acmeRenewalScheduler.globalConfInvalidated();
            }
            return;
        }

        List<CertificateInfo> certs;
        try {
            certs = getAllCertificates();
        } catch (Exception e) {
            log.error("Error when trying to retrieve certificates for renewal", e);
            finishRenewal(acmeRenewalScheduler, true);
            return;
        }

        boolean failed = renewCertificatesIfNeeded(certs);

        finishRenewal(acmeRenewalScheduler, failed);
    }

    private List<CertificateInfo> getAllCertificates() {
        List<TokenInfo> allTokens = signerRpcClient.getTokens();
        return allTokens.stream()
                .flatMap(t -> t.getKeyInfo().stream())
                .flatMap(k -> k.getCerts().stream())
                .toList();
    }

    private boolean renewCertificatesIfNeeded(List<CertificateInfo> certs) {
        log.info("Trying to fetch renewal information and renew if needed for {} certificates", certs.size());
        boolean failed = false;
        for (CertificateInfo certificateInfo : certs) {
            if (!CertificateInfo.STATUS_REGISTERED.equals(certificateInfo.getStatus())) {
                log.debug("Skipping non-registered certificate {}", certificateInfo.getId());
                continue;
            }
            if (isNotBlank(certificateInfo.getRenewedCertHash())) {
                log.debug("Skipping certificate {} already in process of renewal", certificateInfo.getId());
                continue;
            }
            if (!renewCertificateIfNeeded(certificateInfo)) {
                failed = true;
            }
        }
        return failed;
    }

    private boolean renewCertificateIfNeeded(CertificateInfo certificateInfo) {
        X509Certificate x509Certificate = readCertificate(certificateInfo.getCertificateBytes());
        KeyUsageInfo keyUsage;
        ClientId clientId;
        ApprovedCAInfo approvedCA;
        try {
            keyUsage = getKeyUsage(x509Certificate);
            if (keyUsage == KeyUsageInfo.KEY_USAGE_UNSPECIFIED) {
                log.debug("Skipping certificate with unspecified key usage {}", certificateInfo.getId());
                return true;
            }
            clientId = getClientId(certificateInfo, x509Certificate);
            approvedCA = getApprovedCA(clientId, x509Certificate);

            if (approvedCA.getAcmeServerDirectoryUrl() == null) {
                log.debug("Skipping certificate that is not certified by an authority with ACME support {}", certificateInfo.getId());
                return true;
            }
        } catch (Exception ex) {
            log.error("Error when trying to retrieve information about the certificate '{}' to be renewed",
                    certificateInfo.getId(),
                    ex);
            setRenewalErrorAndSendFailureNotification(certificateInfo, ex.getMessage());
            return false;
        }

        acmeService.checkAccountKeyPairAndRenewIfNecessary(clientId.asEncodedId(), approvedCA, keyUsage);

        boolean isRenewalRequired;
        try {
            isRenewalRequired = isRenewalRequired(clientId.asEncodedId(), approvedCA, x509Certificate, keyUsage);
        } catch (Exception ex) {
            log.error("Error when trying to find out whether renewal is required for certificate '{}'", certificateInfo.getId(), ex);
            setRenewalErrorAndSendFailureNotification(certificateInfo, ex.getMessage(), clientId.asEncodedId());
            return false;
        }

        X509Certificate newX509Certificate = null;
        if (isRenewalRequired) {
            try {
                newX509Certificate = renewCertificate(clientId, approvedCA, certificateInfo, x509Certificate, keyUsage);
            } catch (Exception ex) {
                log.error("Error when trying to renew certificate '{}'", certificateInfo.getId(), ex);
                setRenewalErrorAndSendFailureNotification(certificateInfo, ex.getMessage(), clientId.asEncodedId());
                return false;
            }
        }

        setNextPlannedRenewal(clientId.asEncodedId(),
                approvedCA,
                newX509Certificate != null ? newX509Certificate : x509Certificate,
                keyUsage);

        if (isNotBlank(certificateInfo.getRenewalError())) {
            setRenewalError(certificateInfo.getId(), "");
        }
        return true;
    }

    private void finishRenewal(CertificateRenewalScheduler acmeRenewalScheduler, boolean failed) {
        if (acmeRenewalScheduler != null) {
            if (failed) {
                acmeRenewalScheduler.failure();
            } else {
                acmeRenewalScheduler.success();
            }
        }
    }

    private void setRenewalError(String certId, String errorDescription) {
        try {
            signerRpcClient.setRenewalError(certId, errorDescription);
        } catch (Exception ex) {
            log.error("Error when trying to set the renewal error for the certificate '{}'", certId, ex);
        }
    }

    private void setRenewalErrorAndSendFailureNotification(CertificateInfo cert, String errorDescription) {
        String memberId = cert.getMemberId() != null
                ? cert.getMemberId().asEncodedId()
                : serverConfService.getSecurityServerOwnerId().asEncodedId();
        setRenewalErrorAndSendFailureNotification(cert, errorDescription, memberId);
    }

    private void setRenewalErrorAndSendFailureNotification(CertificateInfo cert, String errorDescription, String memberId) {
        if (!Objects.equals(cert.getRenewalError(), errorDescription)) {
            setRenewalError(cert.getId(), errorDescription);
            SecurityServerId.Conf securityServerId = getSecurityServerId();
            mailNotificationHelper.sendFailureNotification(memberId, cert, securityServerId, errorDescription);
        }
    }

    private ApprovedCAInfo getApprovedCA(ClientId clientId, X509Certificate x509Certificate)
            throws CertificateEncodingException, IOException {
        X509Certificate caX509Certificate = globalConfProvider.getCaCert(clientId.getXRoadInstance(), x509Certificate);
        return globalConfProvider.getApprovedCA(clientId.getXRoadInstance(), caX509Certificate);
    }

    private ClientId getClientId(CertificateInfo certificateInfo, X509Certificate x509Certificate)
            throws CertificateEncodingException, IOException, OperatorCreationException {
        ClientId clientId = certificateInfo.getMemberId();
        if (clientId == null) {
            SecurityServerId securityServerId = globalConfProvider.getServerId(x509Certificate);
            clientId = securityServerId.getOwner();
        }
        return clientId;
    }

    private static KeyUsageInfo getKeyUsage(X509Certificate x509Certificate) throws CertificateParsingException {
        if (isSigningCert(x509Certificate)) {
            return KeyUsageInfo.SIGNING;
        }
        if (isAuthCert(x509Certificate)) {
            return KeyUsageInfo.AUTHENTICATION;
        }
        return KeyUsageInfo.KEY_USAGE_UNSPECIFIED;
    }

    private boolean isRenewalRequired(String memberId, ApprovedCAInfo approvedCA, X509Certificate x509Certificate, KeyUsageInfo keyUsage) {
        try {
            if (acmeService.hasRenewalInfo(memberId, approvedCA, keyUsage)) {
                return acmeService.isRenewalRequired(memberId, approvedCA, x509Certificate, keyUsage);
            }
        } catch (Exception ex) {
            log.error(
                    "Retrieving renewal information from ACME Server failed. Falling back to fixed renewal time based on certificate "
                            + "expiration date: {}",
                    ex.getMessage());
        }
        int renewalTimeBeforeExpirationDate = acmeConfig.getAcmeRenewalTimeBeforeExpirationDate();
        return Instant.now().isAfter(x509Certificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS));
    }

    private void setNextPlannedRenewal(String memberId,
                                       ApprovedCAInfo approvedCA,
                                       X509Certificate newX509Certificate,
                                       KeyUsageInfo keyUsage) {
        try {
            Instant nextRenewalTime = acmeService.getNextRenewalTime(memberId, approvedCA, newX509Certificate, keyUsage);
            CertificateInfo newCertInfo = signerRpcClient.getCertForHash(calculateCertHexHash(newX509Certificate));
            signerRpcClient.setNextPlannedRenewal(newCertInfo.getId(), nextRenewalTime);
        } catch (Exception ex) {
            log.error("Error when trying to set the next planned renewal time for the certificate '{}'",
                    newX509Certificate.getSerialNumber(),
                    ex);
        }
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private X509Certificate renewCertificate(ClientId memberId, ApprovedCAInfo approvedCA,
                                             CertificateInfo oldCertInfo,
                                             X509Certificate oldX509Certificate, KeyUsageInfo keyUsage)
            throws Exception {
        log.info("Starting to renew certificate '{}'", oldX509Certificate.getSerialNumber());
        TokenInfoAndKeyId tokenAndOldKeyId = signerRpcClient.getTokenAndKeyIdForCertHash(calculateCertHexHash(oldX509Certificate));
        String tokenId = tokenAndOldKeyId.getTokenInfo().getId();
        KeyAlgorithm keyAlgorithm = SignMechanism.valueOf(tokenAndOldKeyId.getKeyInfo().getSignMechanismName()).keyAlgorithm();
        KeyInfo newKeyInfo = signerRpcClient.generateKey(tokenId, tokenAndOldKeyId.getKeyInfo().getLabel(), keyAlgorithm);

        X509Certificate newX509Certificate;
        boolean activate;
        try {
            String subjectAltName = getSubjectAltName(oldX509Certificate, keyUsage);
            SignerRpcClient.GeneratedCertRequestInfo generatedCertRequestInfo = signerRpcClient.generateCertRequest(newKeyInfo.getId(),
                    oldCertInfo.getMemberId(),
                    keyUsage,
                    oldX509Certificate.getSubjectX500Principal().getName(),
                    subjectAltName,
                    CertificateRequestFormat.DER,
                    approvedCA.getCertificateProfileInfo());
            List<X509Certificate> newCert =
                    acmeService.renew(memberId.asEncodedId(),
                            subjectAltName,
                            approvedCA,
                            keyUsage,
                            oldX509Certificate,
                            generatedCertRequestInfo.certRequest()
                    );
            if (newCert == null || newCert.isEmpty()) {
                return null;
            }
            newX509Certificate = newCert.getFirst();
            String certStatus = keyUsage == KeyUsageInfo.AUTHENTICATION ? CertificateInfo.STATUS_SAVED : CertificateInfo.STATUS_REGISTERED;
            activate = keyUsage == KeyUsageInfo.SIGNING && acmeConfig.isAutomaticActivateAcmeSignCertificate();
            signerRpcClient.importCert(newX509Certificate.getEncoded(), certStatus, oldCertInfo.getMemberId(), activate);
            signerRpcClient.setRenewedCertHash(oldCertInfo.getId(), calculateCertHexHash(newX509Certificate));
        } catch (Exception ex) {
            rollback(newKeyInfo.getId());
            throw ex;
        }

        CertificateInfo newCertInfo = signerRpcClient.getCertForHash(calculateCertHexHash(newX509Certificate));
        if (activate) {
            SecurityServerId.Conf securityServerId = getSecurityServerId();
            if (isNotBlank(newCertInfo.getOcspVerifyBeforeActivationError())) {
                mailNotificationHelper.sendCertActivationFailureNotification(memberId.asEncodedId(),
                        newCertInfo.getCertificateDisplayName(),
                        securityServerId,
                        keyUsage,
                        newCertInfo.getOcspVerifyBeforeActivationError());
            } else {
                mailNotificationHelper.sendCertActivatedNotification(memberId.asEncodedId(), securityServerId, newCertInfo, keyUsage);
            }
        }

        finishRenewingCertificate(memberId, oldX509Certificate, keyUsage, newX509Certificate, newCertInfo, newKeyInfo);

        return newX509Certificate;
    }

    @ArchUnitSuppressed("NoVanillaExceptions")
    private void finishRenewingCertificate(ClientId memberId,
                                           X509Certificate oldX509Certificate,
                                           KeyUsageInfo keyUsage,
                                           X509Certificate newX509Certificate,
                                           CertificateInfo newCertInfo,
                                           KeyInfo newKeyInfo) throws Exception {
        SecurityServerId.Conf securityServerId = getSecurityServerId();
        try {
            if (keyUsage == KeyUsageInfo.AUTHENTICATION) {
                String securityServerAddress =
                        globalConfProvider.getSecurityServerAddress(globalConfProvider.getServerId(oldX509Certificate));
                ManagementRequestSender managementRequestSender = createManagementRequestSender();
                managementRequestSender.sendAuthCertRegRequest(securityServerId,
                        securityServerAddress,
                        newX509Certificate.getEncoded(),
                        false);
                signerRpcClient.setCertStatus(newCertInfo.getId(), CertificateInfo.STATUS_REGINPROG);
            }
        } catch (Exception ex) {
            rollback(newKeyInfo.getId());
            throw ex;
        }
        log.info("Certificate '{}' renewed successfully. New certificate serial: '{}'",
                oldX509Certificate.getSerialNumber(),
                newX509Certificate.getSerialNumber());
        mailNotificationHelper.sendSuccessNotification(memberId, securityServerId, newCertInfo, keyUsage);
    }

    ManagementRequestSender createManagementRequestSender() {
        ClientId sender = serverConfService.getSecurityServerOwnerId();
        ClientId receiver = globalConfProvider.getManagementRequestService();
        return new ManagementRequestSender(vaultKeyProvider, globalConfProvider, signerRpcClient,
                signerSignClient, sender, receiver, adminServiceProperties.getProxyServerUrl(),
                DigestAlgorithm.ofName(adminServiceProperties.getAuthCertRegSignatureDigestAlgorithmId()),
                adminServiceProperties.getProxyServerConnectTimeout(),
                adminServiceProperties.getProxyServerSocketTimeout(),
                adminServiceProperties.isProxyServerEnableConnectionReuse());
    }

    private String getSubjectAltName(X509Certificate oldX509Certificate, KeyUsageInfo keyUsage) throws CertificateParsingException {
        String subjectAltName;
        if (oldX509Certificate.getSubjectAlternativeNames() != null) {
            subjectAltName = (String) oldX509Certificate.getSubjectAlternativeNames().iterator().next().get(1);
        } else {
            if (keyUsage == KeyUsageInfo.AUTHENTICATION) {
                subjectAltName = getCommonName(oldX509Certificate.getSubjectX500Principal().getName());
            } else {
                subjectAltName = globalConfProvider.getSecurityServerAddress(getSecurityServerId());
            }
        }
        return subjectAltName;
    }

    private SecurityServerId.Conf getSecurityServerId() {
        return serverConfService.getSecurityServerId();
    }

    private void rollback(String keyId) {
        log.info("Rolling back the creation of new key");
        try {
            signerRpcClient.deleteKey(keyId, false);
            signerRpcClient.deleteKey(keyId, true);
        } catch (Exception e) {
            log.error("Rolling back the creation of new key with id '{}' failed", keyId);
        }
    }
}
