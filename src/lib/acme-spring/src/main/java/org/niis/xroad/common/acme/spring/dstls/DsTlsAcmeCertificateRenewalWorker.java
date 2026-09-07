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
package org.niis.xroad.common.acme.spring.dstls;

import ee.ria.xroad.common.crypto.RsaKeyManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.spring.scheduling.AcmeRenewalWorker;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Enrolls and continuously renews a product's own DS TLS certificate via ACME, once DS TLS ACME enrollment is
 * enabled (per {@link DsTlsAcmeHostContext}) and a governing authority has designated an ACME-capable CA for DS
 * TLS in globalconf.
 * <p>
 * Entirely parallel to the member auth/sign {@code AcmeCertificateRenewalWorker}: signer-free, in-process key
 * generation, no {@code KeyUsageInfo}, no member id. Runs on its own {@link CertificateRenewalScheduler}
 * instance, wired by {@link DsTlsAcmeCertificateRenewalSchedulingConfig}.
 * <p>
 * Each cycle: resolve the public hostname from {@link DsTlsAcmeHostContext} (blank/absent means enrollment
 * isn't currently enabled — skip, not a failure; malformed is a real configuration error); find the designated
 * ACME-capable DS TLS CA (zero matches — skip, manual upload remains the path; more than one — fail closed);
 * enroll or renew as needed, regardless of whether the currently stored certificate was obtained manually or
 * via ACME.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DsTlsAcmeCertificateRenewalWorker implements AcmeRenewalWorker {

    private static final int DS_TLS_KEY_LENGTH = 2048;

    private final GlobalConfProvider globalConfProvider;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final DsTlsAcmeService dsTlsAcmeService;
    private final DsTlsAcmeHostContext hostContext;

    @Override
    public void execute(CertificateRenewalScheduler scheduler) {
        log.info("DS TLS ACME certificate renewal cycle started");

        if (!globalConfProvider.isValid()) {
            log.debug("Invalid global configuration, pausing DS TLS ACME renewal");
            if (scheduler != null) {
                scheduler.globalConfInvalidated();
            }
            return;
        }

        String hostname;
        try {
            hostname = hostContext.getPublicHostname();
        } catch (Exception ex) {
            log.error("The configured DS TLS public hostname is malformed", ex);
            String error = describeError(ex);
            if (dsTlsCertificateService.recordAcmeOutcome(error)) {
                hostContext.notifyEnrollmentFailure(hostContext.getConfiguredHostnameSource(), error);
            }
            finishCycle(scheduler, true);
            return;
        }

        if (hostname == null) {
            log.debug("DS TLS ACME enrollment is not currently enabled, skipped");
            dsTlsCertificateService.suspendAcmeScheduling();
            finishCycle(scheduler, false);
            return;
        }

        boolean failed = !runCycle(hostname);
        finishCycle(scheduler, failed);
    }

    /**
     * @return {@code true} on success (including a skipped or not-yet-due cycle), {@code false} on a real failure
     */
    private boolean runCycle(String hostname) {
        List<ApprovedDsTlsCaInfo> acmeCapableCas = globalConfProvider.getApprovedDsTlsCas(globalConfProvider.getInstanceIdentifier())
                .stream()
                .filter(ca -> isNotBlank(ca.getAcmeServerDirectoryUrl()))
                .toList();

        if (acmeCapableCas.isEmpty()) {
            log.debug("No ACME-capable DS TLS CA is designated, DS TLS ACME enrollment skipped");
            dsTlsCertificateService.recordAcmeOutcome(null);
            return true;
        }

        if (acmeCapableCas.size() > 1) {
            String error = "More than one ACME-capable DS TLS CA is designated (%d); refusing to enroll against any of them"
                    .formatted(acmeCapableCas.size());
            log.error(error);
            if (dsTlsCertificateService.recordAcmeOutcome(error)) {
                hostContext.notifyEnrollmentFailure(hostname, error);
            }
            return false;
        }

        try {
            enrollOrRenew(hostname, acmeCapableCas.getFirst());
            return true;
        } catch (Exception ex) {
            log.error("DS TLS ACME enrollment/renewal failed", ex);
            String error = describeError(ex);
            if (dsTlsCertificateService.recordAcmeOutcome(error)) {
                hostContext.notifyEnrollmentFailure(hostname, error);
            }
            return false;
        }
    }

    private void enrollOrRenew(String hostname, ApprovedDsTlsCaInfo caInfo) {
        X509Certificate currentCertificate = dsTlsCertificateService.getStatus().certificate();

        if (currentCertificate != null
                && Instant.now().isBefore(dsTlsAcmeService.getNextRenewalTime(caInfo, currentCertificate))) {
            log.debug("DS TLS certificate is not yet due for renewal");
            dsTlsCertificateService.recordAcmeOutcome(null);
            return;
        }

        KeyPair keyPair = new RsaKeyManager(DS_TLS_KEY_LENGTH).generateKeyPair();
        byte[] certRequest = DsTlsCsrBuilder.build(keyPair, hostname);

        List<X509Certificate> chain = currentCertificate == null
                ? dsTlsAcmeService.enroll(caInfo, hostname, certRequest)
                : dsTlsAcmeService.renew(caInfo, hostname, currentCertificate, certRequest);

        if (chain == null || chain.isEmpty()) {
            throw new IllegalStateException("The ACME server returned no certificate");
        }

        X509Certificate[] chainArray = chain.toArray(X509Certificate[]::new);
        Instant nextRenewalTime = dsTlsAcmeService.getNextRenewalTime(caInfo, chainArray[0]);
        dsTlsCertificateService.storeAcmeEnrolledCertificate(keyPair.getPrivate(), chainArray, nextRenewalTime);

        boolean isRenewal = currentCertificate != null;
        hostContext.notifyEnrollmentSuccess(hostname, isRenewal);
        log.info("DS TLS certificate successfully {} via ACME", isRenewal ? "renewed" : "enrolled");
    }

    private void finishCycle(CertificateRenewalScheduler scheduler, boolean failed) {
        if (scheduler != null) {
            if (failed) {
                scheduler.failure();
            } else {
                scheduler.success();
            }
        }
    }

    private static String describeError(Exception ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
