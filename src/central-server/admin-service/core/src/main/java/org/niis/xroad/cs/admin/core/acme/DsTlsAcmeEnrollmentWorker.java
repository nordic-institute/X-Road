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
package org.niis.xroad.cs.admin.core.acme;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.service.DsTlsCasService;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificateService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProperties;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Drives the dataspace TLS certificate's ACME enrollment and renewal for the Central Server's co-located Issuer
 * Service, mirroring the Security Server's worker but simpler in two respects that don't transfer:
 * <ul>
 *     <li>the Central Server is the globalconf <em>producer</em>, not a consumer, so there is no globalconf
 *     validity gate - the designated CA list is read straight from this product's own {@link DsTlsCasService};</li>
 *     <li>the public hostname is already a bare hostname ({@link DataspaceIssuerProperties#getHost()}), not a
 *     URL to parse, so there is no malformed-URL failure mode to guard against.</li>
 * </ul>
 * Level-triggered: every tick re-derives the designated CA fresh and re-evaluates whether enrollment or renewal
 * is due against whatever certificate (if any) is currently stored. Failures are recorded on the enrollment
 * status record; the Central Server's alerts banner ({@code NotificationService}) surfaces them by reading that
 * record live, so no separate notification call is needed here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DsTlsAcmeEnrollmentWorker {

    private final DataspaceIssuerProperties dataspaceIssuerProperties;
    private final DsTlsCasService dsTlsCasService;
    private final DsTlsAcmeService dsTlsAcmeService;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final AcmeConfig acmeConfig;

    public void execute(DsTlsAcmeEnrollmentScheduler scheduler) {
        log.info("Dataspace TLS certificate ACME enrollment cycle started");

        String hostname = dataspaceIssuerProperties.getHost();
        if (isBlank(hostname)) {
            log.debug("Dataspace TLS certificate: issuer host not configured, skipping");
            finish(scheduler, false);
            return;
        }

        Optional<DsTlsCa> designatedCa = findDesignatedAcmeCa();
        if (designatedCa.isEmpty()) {
            log.debug("Dataspace TLS certificate: no ACME-capable designated CA, skipping (manual path)");
            finish(scheduler, false);
            return;
        }

        finish(scheduler, !enrollOrRenewIfNeeded(designatedCa.get(), hostname));
    }

    private Optional<DsTlsCa> findDesignatedAcmeCa() {
        return dsTlsCasService.findAll().stream()
                .filter(ca -> isNotBlank(ca.getAcmeServerDirectoryUrl()))
                .findFirst();
    }

    private boolean enrollOrRenewIfNeeded(DsTlsCa ca, String hostname) {
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
            dsTlsCertificateService.recordAcmeOutcome(null);
            return true;
        } catch (Exception e) {
            log.error("Dataspace TLS certificate ACME enrollment/renewal failed", e);
            dsTlsCertificateService.recordAcmeOutcome(e.getMessage());
            return false;
        }
    }

    private void enroll(DsTlsCa ca, String hostname, X509Certificate certificateToReplace) {
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
    }

    private boolean isRenewalRequired(DsTlsCa ca, X509Certificate certificate) {
        try {
            if (dsTlsAcmeService.hasRenewalInfo(ca)) {
                return dsTlsAcmeService.isRenewalRequired(ca, certificate);
            }
        } catch (Exception e) {
            log.error("Dataspace TLS certificate: retrieving ACME renewal information failed, falling back to "
                    + "fixed renewal time based on certificate expiration date", e);
        }
        int fallbackDaysBeforeExpiration = acmeConfig.getRenewalTimeBeforeExpirationDate();
        return Instant.now().isAfter(certificate.getNotAfter().toInstant().minus(fallbackDaysBeforeExpiration, ChronoUnit.DAYS));
    }

    private X509Certificate readCurrentCertificateOrNull() {
        try {
            return dsTlsCertificateService.getDataspaceTlsCertificate();
        } catch (NotFoundException e) {
            return null;
        }
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
