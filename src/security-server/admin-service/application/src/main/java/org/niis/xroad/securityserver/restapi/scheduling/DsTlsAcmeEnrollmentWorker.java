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

import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.securityserver.restapi.acme.AcmeConfig;
import org.niis.xroad.securityserver.restapi.acme.DsTlsAcmeService;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Drives the dataspace TLS certificate's ACME enrollment and renewal, on the pattern of {@link AcmeClientWorker}
 * (retry/backoff via {@link DsTlsAcmeEnrollmentScheduler}, globalconf-invalidation handling, mail notifications)
 * but entirely parallel to it: no signer, no {@link org.niis.xroad.signer.protocol.dto.KeyUsageInfo}, no member id.
 * The auth/sign worker and scheduler are untouched.
 * <p>
 * Level-triggered: every tick re-derives the public hostname and the designated CA fresh from configuration and
 * globalconf, and re-evaluates whether enrollment or renewal is due against whatever certificate (if any) is
 * currently stored. Matches the member-cert precedent that a manually-uploaded certificate is transparently
 * replaced by ACME once a designated CA exists and the certificate nears expiry.
 */
@Slf4j
@Component
@Transactional
@RequiredArgsConstructor
public class DsTlsAcmeEnrollmentWorker {

    private final AdminServiceProperties adminServiceProperties;
    private final GlobalConfProvider globalConfProvider;
    private final DsTlsAcmeService dsTlsAcmeService;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final AcmeConfig acmeConfig;
    private final ScheduledJobHelper scheduledJobHelper;
    private final MailNotificationHelper mailNotificationHelper;

    public void execute(DsTlsAcmeEnrollmentScheduler scheduler) {
        log.info("Dataspace TLS certificate ACME enrollment cycle started");

        if (!globalConfProvider.isValid()) {
            log.debug("invalid global conf, returning");
            if (scheduler != null) {
                scheduler.globalConfInvalidated();
            }
            return;
        }

        String hostname;
        try {
            hostname = resolvePublicHostname();
        } catch (Exception e) {
            log.error("Dataspace TLS certificate: could not derive the public hostname from the configured "
                    + "IdentityHub URL", e);
            recordOutcome(e.getMessage());
            finish(scheduler, true);
            return;
        }

        if (hostname == null) {
            log.debug("Dataspace TLS certificate: data space feature not enabled, skipping");
            finish(scheduler, false);
            return;
        }

        Optional<ApprovedDsTlsCaInfo> designatedCa = findDesignatedAcmeCa();
        if (designatedCa.isEmpty()) {
            log.debug("Dataspace TLS certificate: no ACME-capable designated CA, skipping (manual path)");
            finish(scheduler, false);
            return;
        }

        finish(scheduler, !enrollOrRenewIfNeeded(designatedCa.get(), hostname));
    }

    /**
     * Derives the public hostname the dataspace TLS certificate must carry as its SAN, from the same
     * configuration the provisioning worker uses for the IdentityHub DID host. Returns {@code null} when the
     * DataSpace feature is not enabled (blank IdentityHub URL) - not a failure. Throws when the URL is configured
     * but malformed, so the caller can route that into the failure/notification path instead of an escaping
     * exception.
     */
    private String resolvePublicHostname() {
        String identityHubUrl = adminServiceProperties.getDataspace().getIdentityHubUrl();
        if (isBlank(identityHubUrl)) {
            return null;
        }
        String host;
        try {
            host = new URI(identityHubUrl).getHost();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Configured IdentityHub URL is malformed: " + identityHubUrl, e);
        }
        if (isBlank(host)) {
            throw new IllegalArgumentException("Configured IdentityHub URL has no host: " + identityHubUrl);
        }
        return host;
    }

    private Optional<ApprovedDsTlsCaInfo> findDesignatedAcmeCa() {
        String instanceIdentifier = globalConfProvider.getInstanceIdentifier();
        return globalConfProvider.getApprovedDsTlsCas(instanceIdentifier).stream()
                .filter(ca -> isNotBlank(ca.getAcmeServerDirectoryUrl()))
                .findFirst();
    }

    private boolean enrollOrRenewIfNeeded(ApprovedDsTlsCaInfo ca, String hostname) {
        dsTlsAcmeService.checkAccountKeyPairAndRenewIfNecessary(ca);

        X509Certificate currentCertificate = readCurrentCertificateOrNull();
        try {
            if (currentCertificate == null) {
                log.info("Dataspace TLS certificate: no certificate stored yet, enrolling via ACME");
                enroll(ca, hostname, null);
            } else if (isRenewalRequired(ca, currentCertificate)) {
                log.info("Dataspace TLS certificate: renewal is due, re-enrolling via ACME");
                enroll(ca, hostname, currentCertificate);
            } else {
                log.debug("Dataspace TLS certificate: renewal not yet required");
            }
            recordOutcome(null);
            return true;
        } catch (Exception e) {
            log.error("Dataspace TLS certificate ACME enrollment/renewal failed", e);
            recordOutcome(e.getMessage());
            return false;
        }
    }

    private void enroll(ApprovedDsTlsCaInfo ca, String hostname, X509Certificate certificateToReplace) {
        KeyPair keyPair = dsTlsAcmeService.generateKeyPair();
        byte[] csr = dsTlsAcmeService.generateCsr(keyPair, hostname);
        List<X509Certificate> chain = certificateToReplace == null
                ? dsTlsAcmeService.orderCertificate(ca, hostname, csr)
                : dsTlsAcmeService.renew(ca, hostname, certificateToReplace, csr);
        if (chain == null || chain.isEmpty()) {
            throw new IllegalStateException("ACME server did not return a certificate chain");
        }
        Instant nextRenewalTime = dsTlsAcmeService.getNextRenewalTime(ca, chain.getFirst());
        dsTlsCertificateService.storeAcmeEnrolledCertificate(keyPair.getPrivate(), chain.toArray(X509Certificate[]::new),
                nextRenewalTime);
        notifySuccess(certificateToReplace == null);
    }

    private boolean isRenewalRequired(ApprovedDsTlsCaInfo ca, X509Certificate certificate) {
        try {
            if (dsTlsAcmeService.hasRenewalInfo(ca)) {
                return dsTlsAcmeService.isRenewalRequired(ca, certificate);
            }
        } catch (Exception e) {
            log.error("Dataspace TLS certificate: retrieving ACME renewal information failed, falling back to "
                    + "fixed renewal time based on certificate expiration date", e);
        }
        int fallbackDaysBeforeExpiration = acmeConfig.getAcmeRenewalTimeBeforeExpirationDate();
        return Instant.now().isAfter(certificate.getNotAfter().toInstant().minus(fallbackDaysBeforeExpiration, ChronoUnit.DAYS));
    }

    private X509Certificate readCurrentCertificateOrNull() {
        try {
            return dsTlsCertificateService.getDataspaceTlsCertificate();
        } catch (NotFoundException e) {
            return null;
        }
    }

    private void recordOutcome(String errorDescription) {
        boolean changed = dsTlsCertificateService.recordAcmeOutcome(errorDescription);
        if (changed && errorDescription != null) {
            String ownerMemberId = ownerMemberId();
            SecurityServerId.Conf securityServerId = scheduledJobHelper.getSecurityServerId();
            mailNotificationHelper.sendDsTlsAcmeFailureNotification(ownerMemberId, securityServerId, errorDescription);
        }
    }

    private void notifySuccess(boolean isEnrollment) {
        String ownerMemberId = ownerMemberId();
        SecurityServerId.Conf securityServerId = scheduledJobHelper.getSecurityServerId();
        mailNotificationHelper.sendDsTlsAcmeSuccessNotification(ownerMemberId, securityServerId, isEnrollment);
    }

    private String ownerMemberId() {
        return scheduledJobHelper.getServerConf().getOwner().getIdentifier().asEncodedId();
    }

    private void finish(DsTlsAcmeEnrollmentScheduler scheduler, boolean failed) {
        if (scheduler != null) {
            if (failed) {
                scheduler.failure();
            } else {
                scheduler.success();
            }
        }
    }
}
