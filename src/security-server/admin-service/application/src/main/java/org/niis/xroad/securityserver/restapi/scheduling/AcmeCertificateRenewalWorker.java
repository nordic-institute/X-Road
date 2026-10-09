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
import org.niis.xroad.common.acme.AcmeKeyPurpose;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.spring.scheduling.AcmeRenewalWorker;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.converter.AcmeKeyPurposeMapping;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnIdentity;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static ee.ria.xroad.common.util.CertUtils.getCommonName;
import static ee.ria.xroad.common.util.CertUtils.isAuthCertOrThrow;
import static ee.ria.xroad.common.util.CertUtils.isSigningCert;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashOrThrow;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;
import static org.niis.xroad.common.core.exception.ErrorCode.SECURITY_SERVER_NOT_FOUND;

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
public class AcmeCertificateRenewalWorker implements AcmeRenewalWorker {

    private static final String OWNER_NOT_SET_MESSAGE = "Security Server owner is not set";

    private final AcmeService acmeService;
    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;
    private final GlobalConfProvider globalConfProvider;
    private final VaultKeyProvider vaultKeyProvider;
    private final MailNotificationHelper mailNotificationHelper;
    private final AcmeConfig acmeConfig;
    private final AdminServiceProperties adminServiceProperties;
    private final OwnSecurityServerResolver ownSecurityServerResolver;

    @Override
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
            try {
                if (!renewCertificateIfNeeded(certificateInfo)) {
                    failed = true;
                }
            } catch (Exception ex) {
                log.error("Unexpected error when processing certificate '{}'", certificateInfo.getId(), ex);
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

        acmeService.checkAccountKeyPairAndRenewIfNecessary(clientId.asEncodedId(), approvedCA,
                AcmeKeyPurposeMapping.toAcmeKeyPurpose(keyUsage), mailNotificationHelper.getAcmeContacts(clientId.asEncodedId()));

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
        if (cert.getMemberId() != null) {
            setRenewalErrorAndSendFailureNotification(cert, errorDescription, cert.getMemberId().asEncodedId());
            return;
        }
        switch (ownSecurityServerResolver.identity()) {
            case OwnIdentity.Known known ->
                    setRenewalErrorAndSendFailureNotification(cert, errorDescription, known.id().getOwner().asEncodedId());
            case OwnIdentity.OwnerNotInitialised ignored ->
                    setRenewalErrorWithoutNotification(cert, errorDescription, OWNER_NOT_SET_MESSAGE);
            case OwnIdentity.GlobalConfUnavailable ignored ->
                    setRenewalErrorWithoutNotification(cert, errorDescription, "global configuration is unavailable");
        }
    }

    private void setRenewalErrorAndSendFailureNotification(CertificateInfo cert, String errorDescription, String memberId) {
        if (Objects.equals(cert.getRenewalError(), errorDescription)) {
            return;
        }
        setRenewalError(cert.getId(), errorDescription);
        switch (ownSecurityServerResolver.identity()) {
            case OwnIdentity.Known known ->
                    mailNotificationHelper.sendFailureNotification(memberId, cert, toConf(known), errorDescription);
            case OwnIdentity.OwnerNotInitialised ignored ->
                    logSkippedFailureNotification(cert, OWNER_NOT_SET_MESSAGE);
            case OwnIdentity.GlobalConfUnavailable ignored ->
                    logSkippedFailureNotification(cert, "global configuration is unavailable");
        }
    }

    private void setRenewalErrorWithoutNotification(CertificateInfo cert, String errorDescription, String reason) {
        if (!Objects.equals(cert.getRenewalError(), errorDescription)) {
            setRenewalError(cert.getId(), errorDescription);
            logSkippedFailureNotification(cert, reason);
        }
    }

    private void logSkippedFailureNotification(CertificateInfo cert, String reason) {
        log.warn("Renewal failure notification for certificate '{}' not sent: {}", cert.getId(), reason);
    }

    private SecurityServerId.Conf requireOwnServerId() {
        return switch (ownSecurityServerResolver.identity()) {
            case OwnIdentity.Known known -> toConf(known);
            case OwnIdentity.OwnerNotInitialised ignored -> throw XrdRuntimeException.systemException(
                    MALFORMED_SERVERCONF, OWNER_NOT_SET_MESSAGE);
            case OwnIdentity.GlobalConfUnavailable unavailable -> throw XrdRuntimeException.systemException(unavailable.cause());
        };
    }

    private static SecurityServerId.Conf toConf(OwnIdentity.Known known) {
        return SecurityServerId.Conf.create(known.id().getOwner(), known.id().getServerCode());
    }

    private ApprovedCAInfo getApprovedCA(ClientId clientId, X509Certificate x509Certificate) {
        var caX509Certificate = globalConfProvider.getCaCertOrThrow(clientId.getXRoadInstance(), x509Certificate);
        return globalConfProvider.getApprovedCA(clientId.getXRoadInstance(), caX509Certificate);
    }

    private ClientId getClientId(CertificateInfo certificateInfo, X509Certificate x509Certificate) {
        ClientId clientId = certificateInfo.getMemberId();
        if (clientId == null) {
            SecurityServerId securityServerId = globalConfProvider.getServerIdOrThrow(x509Certificate);
            clientId = securityServerId.getOwner();
        }
        return clientId;
    }

    private static KeyUsageInfo getKeyUsage(X509Certificate x509Certificate) {
        if (isSigningCert(x509Certificate)) {
            return KeyUsageInfo.SIGNING;
        }
        if (isAuthCertOrThrow(x509Certificate)) {
            return KeyUsageInfo.AUTHENTICATION;
        }
        return KeyUsageInfo.KEY_USAGE_UNSPECIFIED;
    }

    private boolean isRenewalRequired(String memberId, ApprovedCAInfo approvedCA, X509Certificate x509Certificate, KeyUsageInfo keyUsage) {
        try {
            AcmeKeyPurpose keyPurpose = AcmeKeyPurposeMapping.toAcmeKeyPurpose(keyUsage);
            List<String> contacts = mailNotificationHelper.getAcmeContacts(memberId);
            if (acmeService.hasRenewalInfo(memberId, approvedCA, keyPurpose, contacts)) {
                return acmeService.isRenewalRequired(memberId, approvedCA, x509Certificate, keyPurpose, contacts);
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
            Instant nextRenewalTime = acmeService.getNextRenewalTime(memberId, approvedCA, newX509Certificate,
                    AcmeKeyPurposeMapping.toAcmeKeyPurpose(keyUsage), mailNotificationHelper.getAcmeContacts(memberId));
            CertificateInfo newCertInfo = signerRpcClient.getCertForHash(calculateCertHexHashOrThrow(newX509Certificate));
            signerRpcClient.setNextPlannedRenewal(newCertInfo.getId(), nextRenewalTime);
        } catch (Exception ex) {
            log.error("Error when trying to set the next planned renewal time for the certificate '{}'",
                    newX509Certificate.getSerialNumber(),
                    ex);
        }
    }

    private X509Certificate renewCertificate(ClientId memberId, ApprovedCAInfo approvedCA,
                                             CertificateInfo oldCertInfo,
                                             X509Certificate oldX509Certificate, KeyUsageInfo keyUsage) {
        log.info("Starting to renew certificate '{}'", oldX509Certificate.getSerialNumber());
        SecurityServerId.Conf ownServerId = requireOwnServerId();
        String subjectAltName = getSubjectAltName(oldX509Certificate, keyUsage);
        TokenInfoAndKeyId tokenAndOldKeyId = signerRpcClient.getTokenAndKeyIdForCertHash(calculateCertHexHashOrThrow(oldX509Certificate));
        String tokenId = tokenAndOldKeyId.getTokenInfo().getId();
        KeyAlgorithm keyAlgorithm = SignMechanism.valueOf(tokenAndOldKeyId.getKeyInfo().getSignMechanismName()).keyAlgorithm();
        KeyInfo newKeyInfo = signerRpcClient.generateKey(tokenId, tokenAndOldKeyId.getKeyInfo().getLabel(), keyAlgorithm);

        X509Certificate newX509Certificate;
        boolean activate;
        try {
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
                            AcmeKeyPurposeMapping.toAcmeKeyPurpose(keyUsage),
                            oldX509Certificate,
                            generatedCertRequestInfo.certRequest(),
                            mailNotificationHelper.getAcmeContacts(memberId.asEncodedId())
                    );
            if (newCert == null || newCert.isEmpty()) {
                return null;
            }
            newX509Certificate = newCert.getFirst();
            String certStatus = keyUsage == KeyUsageInfo.AUTHENTICATION ? CertificateInfo.STATUS_SAVED : CertificateInfo.STATUS_REGISTERED;
            activate = keyUsage == KeyUsageInfo.SIGNING && acmeConfig.isAutomaticActivateAcmeSignCertificate();
            signerRpcClient.importCert(newX509Certificate.getEncoded(), certStatus, oldCertInfo.getMemberId(), activate);
            signerRpcClient.setRenewedCertHash(oldCertInfo.getId(), calculateCertHexHashOrThrow(newX509Certificate));
        } catch (Exception ex) {
            rollback(newKeyInfo.getId());
            throw XrdRuntimeException.systemException(ex);
        }

        CertificateInfo newCertInfo = signerRpcClient.getCertForHash(calculateCertHexHashOrThrow(newX509Certificate));
        if (activate) {
            if (isNotBlank(newCertInfo.getOcspVerifyBeforeActivationError())) {
                mailNotificationHelper.sendCertActivationFailureNotification(memberId.asEncodedId(),
                        newCertInfo.getCertificateDisplayName(),
                        ownServerId,
                        keyUsage,
                        newCertInfo.getOcspVerifyBeforeActivationError());
            } else {
                mailNotificationHelper.sendCertActivatedNotification(memberId.asEncodedId(), ownServerId, newCertInfo, keyUsage);
            }
        }

        finishRenewingCertificate(memberId, ownServerId, oldX509Certificate, keyUsage, newX509Certificate, newCertInfo, newKeyInfo);

        return newX509Certificate;
    }

    private void finishRenewingCertificate(ClientId memberId,
                                           SecurityServerId.Conf securityServerId,
                                           X509Certificate oldX509Certificate,
                                           KeyUsageInfo keyUsage,
                                           X509Certificate newX509Certificate,
                                           CertificateInfo newCertInfo,
                                           KeyInfo newKeyInfo) {
        try {
            if (keyUsage == KeyUsageInfo.AUTHENTICATION) {
                String securityServerAddress = globalConfProvider.getSecurityServerAddress(
                        globalConfProvider.getServerIdOrThrow(oldX509Certificate));
                ManagementRequestSender managementRequestSender = createManagementRequestSender(securityServerId.getOwner());
                managementRequestSender.sendAuthCertRegRequest(securityServerId,
                        securityServerAddress,
                        newX509Certificate.getEncoded(),
                        false);
                signerRpcClient.setCertStatus(newCertInfo.getId(), CertificateInfo.STATUS_REGINPROG);
            }
        } catch (Exception ex) {
            rollback(newKeyInfo.getId());
            throw XrdRuntimeException.systemException(ex);
        }
        log.info("Certificate '{}' renewed successfully. New certificate serial: '{}'",
                oldX509Certificate.getSerialNumber(),
                newX509Certificate.getSerialNumber());
        mailNotificationHelper.sendSuccessNotification(memberId, securityServerId, newCertInfo, keyUsage);
    }

    ManagementRequestSender createManagementRequestSender(ClientId sender) {
        ClientId receiver = globalConfProvider.getManagementRequestService();
        return new ManagementRequestSender(vaultKeyProvider, globalConfProvider, signerRpcClient,
                signerSignClient, sender, receiver, adminServiceProperties.getProxyServerUrl(),
                DigestAlgorithm.ofName(adminServiceProperties.getAuthCertRegSignatureDigestAlgorithmId()),
                adminServiceProperties.getProxyServerConnectTimeout(),
                adminServiceProperties.getProxyServerSocketTimeout(),
                adminServiceProperties.isProxyServerEnableConnectionReuse());
    }

    private String getSubjectAltName(X509Certificate oldX509Certificate, KeyUsageInfo keyUsage) {
        String subjectAltName;
        var x509SubjectAlternativeNames = getX509SubjectAlternativeNames(oldX509Certificate);
        if (x509SubjectAlternativeNames != null) {
            subjectAltName = (String) x509SubjectAlternativeNames.iterator().next().get(1);
        } else {
            if (keyUsage == KeyUsageInfo.AUTHENTICATION) {
                subjectAltName = getCommonName(oldX509Certificate.getSubjectX500Principal().getName());
            } else {
                subjectAltName = getOwnRegisteredAddress();
            }
        }
        return subjectAltName;
    }

    private String getOwnRegisteredAddress() {
        return switch (ownSecurityServerResolver.address()) {
            case OwnAddress.Registered registered -> registered.address();
            case OwnAddress.NotRegistered ignored -> throw XrdRuntimeException.systemException(
                    SECURITY_SERVER_NOT_FOUND, "Security Server is not registered in GlobalConf");
            case OwnAddress.GlobalConfUnavailable unavailable -> throw XrdRuntimeException.systemException(unavailable.cause());
            case OwnAddress.OwnerNotInitialised ignored -> throw XrdRuntimeException.systemException(
                    MALFORMED_SERVERCONF, OWNER_NOT_SET_MESSAGE);
        };
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

    private static Collection<List<?>> getX509SubjectAlternativeNames(X509Certificate certificate) {
        try {
            return certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }
}
