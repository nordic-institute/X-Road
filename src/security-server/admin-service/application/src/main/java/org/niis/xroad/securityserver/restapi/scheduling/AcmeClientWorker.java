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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.securityserver.restapi.facade.SignerProxyFacade;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static ee.ria.xroad.common.util.CertUtils.getCommonName;
import static ee.ria.xroad.common.util.CertUtils.isAuthCert;
import static ee.ria.xroad.common.util.CertUtils.isSigningCert;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static java.lang.String.format;
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
    private final SignerProxyFacade signerProxyFacade;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfRepository serverConfRepository;

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

        cleanUpOldKeysIfNewHasBeenRegistered(certs);

        finishRenewal(acmeRenewalScheduler, failed);
    }

    private List<CertificateInfo> getAllCertificates() throws Exception {
        List<TokenInfo> allTokens = signerProxyFacade.getTokens();
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
                return false;
            }
            clientId = getClientId(certificateInfo, x509Certificate);
            approvedCA = getApprovedCA(clientId, x509Certificate);

            if (approvedCA.getAcmeServerDirectoryUrl() == null) {
                log.debug("Skipping certificate that is not certified by an authority with ACME support {}", certificateInfo.getId());
                return false;
            }
        } catch (Exception ex) {
            log.error("Error when trying to retrieve information about the certificate '{}' to be renewed",
                    certificateInfo.getId(),
                    ex);
            setRenewalError(certificateInfo.getId(),
                    "Error when trying to retrieve information about the certificate: " + ex.getMessage());
            return true;
        }

        boolean isRenewalRequired;
        try {
            isRenewalRequired = isRenewalRequired(clientId.asEncodedId(), approvedCA, x509Certificate, keyUsage);
        } catch (Exception ex) {
            log.error("Error when trying to find out whether renewal is required for certificate '{}'", certificateInfo.getId(), ex);
            setRenewalError(certificateInfo.getId(), ex.getMessage());
            return true;
        }

        X509Certificate newX509Certificate = null;
        if (isRenewalRequired) {
            try {
                newX509Certificate = renewCertificate(clientId, approvedCA, certificateInfo, x509Certificate, keyUsage);
            } catch (Exception ex) {
                log.error("Error when trying to renew certificate '{}'", certificateInfo.getId(), ex);
                setRenewalError(certificateInfo.getId(), ex.getMessage());
                return true;
            }
        }

        setNextPlannedRenewal(clientId.asEncodedId(),
                approvedCA,
                newX509Certificate != null ? newX509Certificate : x509Certificate,
                keyUsage);

        if (isNotBlank(certificateInfo.getRenewalError())) {
            setRenewalError(certificateInfo.getId(), "");
        }
        return false;
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
            signerProxyFacade.setRenewalError(certId, errorDescription);
        } catch (Exception ex) {
            log.error("Error when trying to set the renewal error for the certificate '{}'", certId, ex);
        }
    }

    private void cleanUpOldKeysIfNewHasBeenRegistered(List<CertificateInfo> certs) {
        List<CertificateInfo> certsInProcessOfRenewal = certs.stream().filter(cert -> cert.getRenewedCertHash() != null).toList();
        log.info("Checking if {} old certificate(s) in process of renewal can be removed when new certificate has been registered",
                certsInProcessOfRenewal.size());
        for (CertificateInfo certInProcessOfRenewal : certsInProcessOfRenewal) {
            try {
                CertificateInfo renewedCert = getRenewedCertificate(certInProcessOfRenewal);
                if (renewedCert == null) continue;
                if (renewedCert.getStatus().equals(CertificateInfo.STATUS_REGISTERED)) {
                    SignerProxy.KeyIdInfo oldKeyId =
                            signerProxyFacade.getKeyIdForCertHash(calculateCertHexHash(certInProcessOfRenewal.getCertificateBytes()));
                    removeOldAuthKey(certInProcessOfRenewal, oldKeyId.keyId());
                }
            } catch (Exception ex) {
                log.error("Error when trying to clean up old certificate '{}' that has been renewed", certInProcessOfRenewal.getId(), ex);
                setRenewalError(certInProcessOfRenewal.getId(),
                        format("Error when trying to clean up old certificate '%s' that has been renewed: %s",
                                certInProcessOfRenewal.getId(),
                                ex.getMessage()));
            }
        }
    }

    private CertificateInfo getRenewedCertificate(CertificateInfo certInProcessOfRenewal) throws Exception {
        CertificateInfo renewedCert;
        try {
            renewedCert = signerProxyFacade.getCertForHash(certInProcessOfRenewal.getRenewedCertHash());
        } catch (CodedException e) {
            if (e.getFaultCode().contains("CertNotFound")) {
                return null;
            }
            throw e;
        }
        return renewedCert;
    }

    private ApprovedCAInfo getApprovedCA(ClientId clientId, X509Certificate x509Certificate) throws Exception {
        X509Certificate caX509Certificate = globalConfProvider.getCaCert(clientId.getXRoadInstance(), x509Certificate);
        return globalConfProvider.getApprovedCA(clientId.getXRoadInstance(), caX509Certificate);
    }

    private ClientId getClientId(CertificateInfo certificateInfo, X509Certificate x509Certificate) throws Exception {
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
        int renewalTimeBeforeExpirationDate = SystemProperties.getAcmeRenewalTimeBeforeExpirationDate();
        return Instant.now().isAfter(x509Certificate.getNotAfter().toInstant().minus(renewalTimeBeforeExpirationDate, ChronoUnit.DAYS));
    }

    private void setNextPlannedRenewal(String memberId,
                                       ApprovedCAInfo approvedCA,
                                       X509Certificate newX509Certificate,
                                       KeyUsageInfo keyUsage) {
        try {
            Instant nextRenewalTime = acmeService.getNextRenewalTime(memberId, approvedCA, newX509Certificate, keyUsage);
            CertificateInfo newCertInfo = signerProxyFacade.getCertForHash(calculateCertHexHash(newX509Certificate));
            signerProxyFacade.setNextPlannedRenewal(newCertInfo.getId(), nextRenewalTime);
        } catch (Exception ex) {
            log.error("Error when trying to set the next planned renewal time for the certificate '{}'",
                    newX509Certificate.getSerialNumber(),
                    ex);
        }
    }

    private X509Certificate renewCertificate(ClientId memberId, ApprovedCAInfo approvedCA,
                                             CertificateInfo oldCertInfo,
                                             X509Certificate oldX509Certificate, KeyUsageInfo keyUsage) throws Exception {
        TokenInfoAndKeyId tokenAndOldKeyId = signerProxyFacade.getTokenAndKeyIdForCertHash(calculateCertHexHash(oldX509Certificate));
        String tokenId = tokenAndOldKeyId.getTokenInfo().getId();
        KeyInfo newKeyInfo = signerProxyFacade.generateKey(tokenId, tokenAndOldKeyId.getKeyInfo().getLabel());

        X509Certificate newX509Certificate;
        try {
            String subjectAltName = getSubjectAltName(oldX509Certificate, keyUsage);
            SignerProxy.GeneratedCertRequestInfo generatedCertRequestInfo = signerProxyFacade.generateCertRequest(newKeyInfo.getId(),
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
                                      generatedCertRequestInfo.getCertRequest()
                    );
            if (newCert == null || newCert.isEmpty()) {
                return null;
            }
            newX509Certificate = newCert.getFirst();
            String certStatus = keyUsage == KeyUsageInfo.AUTHENTICATION ? CertificateInfo.STATUS_SAVED : CertificateInfo.STATUS_REGISTERED;
            signerProxyFacade.importCert(newX509Certificate.getEncoded(), certStatus, oldCertInfo.getMemberId());
        } catch (Exception ex) {
            rollback(newKeyInfo.getId());
            throw ex;
        }

        if (keyUsage == KeyUsageInfo.AUTHENTICATION) {
            try {
                String securityServerAddress =
                        globalConfProvider.getSecurityServerAddress(globalConfProvider.getServerId(oldX509Certificate));
                ManagementRequestSender managementRequestSender = createManagementRequestSender();
                managementRequestSender.sendAuthCertRegRequest(getSecurityServerId(),
                        securityServerAddress,
                        newX509Certificate.getEncoded());
                CertificateInfo newCertInfo = signerProxyFacade.getCertForHash(calculateCertHexHash(newX509Certificate));
                signerProxyFacade.setCertStatus(newCertInfo.getId(), CertificateInfo.STATUS_REGINPROG);
                signerProxyFacade.setRenewedCertHash(oldCertInfo.getId(), calculateCertHexHash(newX509Certificate));
            } catch (Exception ex) {
                rollback(newKeyInfo.getId());
                throw ex;
            }
        } else {
            removeOldKey(oldCertInfo, tokenAndOldKeyId.getKeyId());
        }

        return newX509Certificate;
    }

    ManagementRequestSender createManagementRequestSender() {
        ClientId sender = serverConfRepository.getServerConf().getOwner().getIdentifier();
        ClientId receiver = globalConfProvider.getManagementRequestService();
        return new ManagementRequestSender(globalConfProvider, sender, receiver, SystemProperties.getProxyUiSecurityServerUrl());
    }

    private String getSubjectAltName(X509Certificate oldX509Certificate, KeyUsageInfo keyUsage) throws Exception {
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

    private void removeOldAuthKey(CertificateInfo oldCertInfo, String oldKeyId) throws Exception {
        ManagementRequestSender managementRequestSender = createManagementRequestSender();
        managementRequestSender.sendAuthCertDeletionRequest(getSecurityServerId(), oldCertInfo.getCertificateBytes());
        removeOldKey(oldCertInfo, oldKeyId);
    }

    private SecurityServerId.Conf getSecurityServerId() {
        ServerConfType serverConf = serverConfRepository.getServerConf();
        return SecurityServerId.Conf.create(serverConf.getOwner().getIdentifier(), serverConf.getServerCode());
    }

    private void removeOldKey(CertificateInfo oldCertInfo, String oldKeyId) throws Exception {
        signerProxyFacade.setCertStatus(oldCertInfo.getId(), CertificateInfo.STATUS_DELINPROG);
        signerProxyFacade.deleteKey(oldKeyId, false);
        signerProxyFacade.deleteKey(oldKeyId, true);
    }

    private void rollback(String keyId) {
        log.info("Rolling back the creation of new key");
        try {
            signerProxyFacade.deleteKey(keyId, false);
            signerProxyFacade.deleteKey(keyId, true);
        } catch (Exception e) {
            log.error("Rolling back the creation of new key with id '{}' failed", keyId);
        }
    }
}
