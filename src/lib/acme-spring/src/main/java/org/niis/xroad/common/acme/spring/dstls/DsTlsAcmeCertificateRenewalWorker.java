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
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.restapi.dstls.DsTlsCsrBuilder;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Renews a product's own DS TLS certificate via ACME, following the same rule the Security Server's own member
 * auth/sign renewal worker applies to member certificates: never enroll a first certificate, resolve the issuing
 * CA from the stored certificate itself, and renew only once that CA turns out to be both a designated DS TLS CA
 * and ACME-capable.
 * <p>
 * Entirely parallel to the member auth/sign {@code AcmeCertificateRenewalWorker}: signer-free, in-process key
 * generation, no {@code KeyUsageInfo}, no member id. Runs on its own {@link CertificateRenewalScheduler}
 * instance, wired by {@link DsTlsAcmeCertificateRenewalSchedulingConfig}.
 * <p>
 * Each cycle: read the stored certificate (none stored — skip, nothing to renew); resolve its issuing CA among
 * {@link DsTlsAcmeHostContext#getDsTlsCertificationAuthorities()} by matching the certificate against each
 * candidate's chain (no match, or a match with no ACME server — skip); when due (ARI-aware, unchanged), renew
 * from that CA with a fresh key pair, the certificate's own subject and its first DNS Subject Alternative Name
 * (falling back to the product's public hostname when the certificate carries none). A skip never writes
 * bookkeeping, so a last error recorded by a failed administrator-triggered order (see the shared DS TLS
 * certificate service) survives untouched until a real renewal outcome overwrites it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DsTlsAcmeCertificateRenewalWorker implements AcmeRenewalWorker {

    private static final int DS_TLS_KEY_LENGTH = 2048;
    private static final int GENERAL_NAME_DNS = 2;

    private final GlobalConfProvider globalConfProvider;
    private final DsTlsCertificateService dsTlsCertificateService;
    private final DsTlsAcmeService dsTlsAcmeService;
    private final DsTlsAcmeHostContext hostContext;

    @Override
    public void execute(CertificateRenewalScheduler scheduler) {
        log.info("DS TLS ACME certificate renewal cycle started");

        if (hostContext.requiresValidGlobalConf() && !globalConfProvider.isValid()) {
            log.debug("Invalid global configuration, pausing DS TLS ACME renewal");
            if (scheduler != null) {
                scheduler.globalConfInvalidated();
            }
            return;
        }

        X509Certificate currentCertificate = dsTlsCertificateService.getStatus().certificate();
        if (currentCertificate == null) {
            log.debug("No DS TLS certificate is stored, DS TLS ACME renewal skipped");
            finishCycle(scheduler, false);
            return;
        }

        boolean failed = !runCycle(currentCertificate);
        finishCycle(scheduler, failed);
    }

    /**
     * @return {@code true} on success (including a skipped or not-yet-due cycle), {@code false} on a real failure
     */
    private boolean runCycle(X509Certificate currentCertificate) {
        ApprovedDsTlsCaInfo issuingCa = resolveIssuingCa(currentCertificate, hostContext.getDsTlsCertificationAuthorities());
        if (issuingCa == null) {
            log.debug("The DS TLS certificate's issuer is not a designated DS TLS CA, renewal skipped");
            return true;
        }
        if (isBlank(issuingCa.getAcmeServerDirectoryUrl())) {
            log.debug("The DS TLS certificate's issuing CA '{}' has no ACME server, renewal skipped", issuingCa.getName());
            return true;
        }

        try {
            if (Instant.now().isBefore(dsTlsAcmeService.getNextRenewalTime(issuingCa, currentCertificate))) {
                log.debug("DS TLS certificate is not yet due for renewal");
                return true;
            }
            renew(issuingCa, currentCertificate);
            return true;
        } catch (Exception ex) {
            log.error("DS TLS ACME renewal failed", ex);
            String error = describeError(ex);
            if (dsTlsCertificateService.recordAcmeOutcome(error)) {
                hostContext.notifyEnrollmentFailure(identifierForNotification(currentCertificate), error);
            }
            return false;
        }
    }

    private void renew(ApprovedDsTlsCaInfo caInfo, X509Certificate currentCertificate) {
        String subjectAltName = resolveSubjectAltName(currentCertificate);
        String subject = currentCertificate.getSubjectX500Principal().getName();

        KeyPair keyPair = new RsaKeyManager(DS_TLS_KEY_LENGTH).generateKeyPair();
        byte[] certRequest = DsTlsCsrBuilder.buildDer(keyPair.getPrivate(), keyPair.getPublic(), subject, subjectAltName);

        List<X509Certificate> chain = dsTlsAcmeService.renew(caInfo, subjectAltName, currentCertificate, certRequest);
        if (chain == null || chain.isEmpty()) {
            throw new IllegalStateException("The ACME server returned no certificate");
        }

        X509Certificate[] chainArray = chain.toArray(X509Certificate[]::new);
        Instant nextRenewalTime = dsTlsAcmeService.getNextRenewalTime(caInfo, chainArray[0]);
        boolean stored = dsTlsCertificateService.storeRenewedCertificate(currentCertificate, keyPair.getPrivate(), chainArray,
                nextRenewalTime);
        if (!stored) {
            log.info("The DS TLS certificate was replaced while renewing it via ACME from '{}', the renewed certificate is discarded",
                    caInfo.getName());
            return;
        }

        hostContext.notifyEnrollmentSuccess(subjectAltName, true);
        log.info("DS TLS certificate successfully renewed via ACME from '{}'", caInfo.getName());
    }

    /**
     * @return the certificate's first DNS Subject Alternative Name entry, or the product's public hostname when
     *     the certificate carries none
     * @throws IllegalStateException if the certificate has no SAN and no public hostname is configured to fall
     *     back to
     */
    private String resolveSubjectAltName(X509Certificate certificate) {
        String san = firstDnsSan(certificate);
        if (san != null) {
            return san;
        }
        String hostname = hostContext.getPublicHostname();
        if (hostname == null) {
            throw new IllegalStateException(
                    "The DS TLS certificate has no Subject Alternative Name and no public hostname is configured to fall back to");
        }
        return hostname;
    }

    /**
     * A best-effort identifier for a failure notification, never throwing: the certificate's own SAN when it has
     * one, otherwise the raw, possibly-unparseable configured hostname source.
     */
    private String identifierForNotification(X509Certificate certificate) {
        String san = firstDnsSan(certificate);
        return san != null ? san : hostContext.getConfiguredHostnameSource();
    }

    private static String firstDnsSan(X509Certificate certificate) {
        Collection<List<?>> sans;
        try {
            sans = certificate.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            throw XrdRuntimeException.systemException(e);
        }
        if (sans == null) {
            return null;
        }
        return sans.stream()
                .filter(san -> san.size() > 1 && Integer.valueOf(GENERAL_NAME_DNS).equals(san.get(0)))
                .map(san -> san.get(1))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .findFirst()
                .orElse(null);
    }

    /**
     * Resolves {@code certificate}'s issuing CA among {@code designatedCas} — the same approach the Security
     * Server's own member auth/sign renewal worker uses to find a certificate's approved CA, applied here to the
     * designated DS TLS CAs' chains instead.
     */
    private static ApprovedDsTlsCaInfo resolveIssuingCa(X509Certificate certificate, List<ApprovedDsTlsCaInfo> designatedCas) {
        return designatedCas.stream()
                .filter(ca -> isIssuedByCa(certificate, ca))
                .findFirst()
                .orElse(null);
    }

    private static boolean isIssuedByCa(X509Certificate certificate, ApprovedDsTlsCaInfo ca) {
        return Stream.concat(Stream.ofNullable(ca.getTopCaCert()), ca.getIntermediateCaCerts().stream())
                .anyMatch(candidateIssuer -> isIssuedBy(certificate, candidateIssuer));
    }

    /**
     * Whether {@code certificate} was issued by {@code candidateIssuer}, verified by signature wherever possible.
     * A definitive signature mismatch rules the candidate out; any other verification failure (an unusable
     * key/algorithm combination, for instance) falls back to a plain issuer/subject distinguished name
     * comparison rather than ruling the candidate out on a technicality.
     */
    private static boolean isIssuedBy(X509Certificate certificate, X509Certificate candidateIssuer) {
        try {
            certificate.verify(candidateIssuer.getPublicKey());
            return true;
        } catch (SignatureException e) {
            return false;
        } catch (GeneralSecurityException e) {
            return certificate.getIssuerX500Principal().equals(candidateIssuer.getSubjectX500Principal());
        }
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
