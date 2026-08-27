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
package org.niis.xroad.securityserver.restapi.dstls;

import ee.ria.xroad.common.crypto.RsaKeyManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.spring.scheduling.AcmeRenewalWorker;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.DsTlsCaInfo;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Enrolls and continuously renews the Security Server's own DS TLS certificate via ACME, once DataSpace is enabled
 * and a governing authority has designated an ACME-capable CA for DS TLS in globalconf.
 * <p>
 * Entirely parallel to the auth/sign {@code AcmeCertificateRenewalWorker}: signer-free, in-process key generation,
 * no {@code KeyUsageInfo}, no member id. Runs on its own {@link CertificateRenewalScheduler} instance, wired by
 * {@link DsTlsAcmeCertificateRenewalSchedulingConfig}.
 * <p>
 * Each cycle: resolve the public hostname (blank means DataSpace isn't enabled — skip, not a failure; malformed is
 * a real configuration error); find the designated ACME-capable DS TLS CA (zero matches — skip, manual upload
 * remains the path; more than one — fail closed); enroll or renew as needed, regardless of whether the currently
 * stored certificate was obtained manually or via ACME.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DsTlsAcmeCertificateRenewalWorker implements AcmeRenewalWorker {

    private static final int DS_TLS_KEY_LENGTH = 2048;

    private final GlobalConfProvider globalConfProvider;
    private final AdminServiceProperties adminServiceProperties;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final DsTlsAcmeService dsTlsAcmeService;

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
            hostname = resolvePublicHostname();
        } catch (Exception ex) {
            log.error("The configured DataSpace public hostname is malformed", ex);
            dsTlsCertificateService.recordAcmeOutcome(describeError(ex));
            finishCycle(scheduler, true);
            return;
        }

        if (hostname == null) {
            log.debug("DataSpace is not enabled, DS TLS ACME enrollment skipped");
            dsTlsCertificateService.suspendAcmeScheduling();
            finishCycle(scheduler, false);
            return;
        }

        boolean failed = !runCycle(hostname);
        finishCycle(scheduler, failed);
    }

    /**
     * @return the host component of the configured DataSpace IdentityHub URL, or {@code null} when DataSpace isn't
     *     enabled (blank URL) — the same value {@code DataspaceProvisioningService} already uses to construct this
     *     server's own {@code did:web} identifier.
     */
    private String resolvePublicHostname() {
        String identityHubUrl = adminServiceProperties.getDataspace().getIdentityHubUrl();
        if (isBlank(identityHubUrl)) {
            return null;
        }

        String host = URI.create(identityHubUrl).getHost();
        if (isBlank(host)) {
            throw new IllegalArgumentException("Configured DataSpace IdentityHub URL has no host: " + identityHubUrl);
        }
        return host;
    }

    /**
     * @return {@code true} on success (including a skipped or not-yet-due cycle), {@code false} on a real failure
     */
    private boolean runCycle(String hostname) {
        List<DsTlsCaInfo> acmeCapableCas = globalConfProvider.getApprovedDsTlsCas(globalConfProvider.getInstanceIdentifier())
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
            dsTlsCertificateService.recordAcmeOutcome(error);
            return false;
        }

        try {
            enrollOrRenew(hostname, acmeCapableCas.getFirst());
            return true;
        } catch (Exception ex) {
            log.error("DS TLS ACME enrollment/renewal failed", ex);
            dsTlsCertificateService.recordAcmeOutcome(describeError(ex));
            return false;
        }
    }

    private void enrollOrRenew(String hostname, DsTlsCaInfo caInfo) {
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

        log.info("DS TLS certificate successfully {} via ACME", currentCertificate == null ? "enrolled" : "renewed");
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
