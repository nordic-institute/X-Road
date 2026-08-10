/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.InternalSSLKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.validator.DsTlsMaterialValidator;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Objects;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@Slf4j
@Service
@RequiredArgsConstructor
public class DsTlsCertificateService {

    /**
     * Enrollment method and ACME renewal bookkeeping for the dataspace TLS certificate, as surfaced through the
     * enrollment-status API. {@code NONE} means no certificate has been stored yet - neither uploaded nor
     * enrolled - but a non-null {@code lastError} alongside {@code NONE} means a first ACME enrollment attempt
     * exists and is failing; nothing is configured or served, yet the admin still needs to see why.
     */
    public record EnrollmentStatusView(EnrollmentMethod enrollmentMethod, Instant nextRenewalTime, String lastError) {
        public enum EnrollmentMethod { NONE, MANUAL, ACME }
    }

    private final VaultClient vaultClient;
    private final DsTlsMaterialValidator dsTlsMaterialValidator;
    private final AuditDataHelper auditDataHelper;

    public X509Certificate getDataspaceTlsCertificate() {
        try {
            return vaultClient.getDsHttpsTlsCredentials().getCertChain()[0];
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(MISSING_SECRET)) {
                throw new NotFoundException(DS_TLS_CERTIFICATE_NOT_FOUND.build());
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to read dataspace TLS certificate", e);
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }
    }

    public X509Certificate uploadDataspaceTlsCertificate(byte[] keyBytes, byte[] certificateChainBytes) {
        var validated = dsTlsMaterialValidator.validate(keyBytes, certificateChainBytes);
        X509Certificate leafCertificate = validated.getCertChain()[0];

        try {
            vaultClient.createDsHttpsTlsCredentials(validated, DsTlsEnrollmentMethod.MANUAL);
            vaultClient.setDsHttpsTlsEnrollmentStatus(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, null, null));
        } catch (Exception e) {
            log.error("Failed to store dataspace TLS certificate", e);
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }

        auditDataHelper.putCertificateHash(leafCertificate);
        return leafCertificate;
    }

    /**
     * Returns the current enrollment status: {@code NONE} when no certificate is stored at all, {@code MANUAL}
     * when a certificate exists but no enrollment status has ever been recorded for it (credentials written by a
     * build predating this status tracking default safely to manual), or the recorded method otherwise.
     * <p>
     * When no certificate is stored yet but an enrollment-status record exists - a first ACME enrollment attempt
     * that has never yet succeeded - the recorded {@code lastError} (and {@code nextRenewalTime}, if the ACME
     * server has ever suggested one) rides along with {@code NONE}: nothing is configured or served, but the
     * admin still needs to see why enrollment keeps failing rather than a bare "not configured".
     */
    public EnrollmentStatusView getEnrollmentStatus() {
        var recordedStatus = vaultClient.getDsHttpsTlsEnrollmentStatus();
        if (!dataspaceTlsCertificateExists()) {
            return recordedStatus
                    .map(status -> new EnrollmentStatusView(
                            EnrollmentStatusView.EnrollmentMethod.NONE,
                            status.nextRenewalTime(),
                            status.lastError()))
                    .orElse(new EnrollmentStatusView(EnrollmentStatusView.EnrollmentMethod.NONE, null, null));
        }
        return recordedStatus
                .map(status -> new EnrollmentStatusView(
                        EnrollmentStatusView.EnrollmentMethod.valueOf(status.method().name()),
                        status.nextRenewalTime(),
                        status.lastError()))
                .orElse(new EnrollmentStatusView(EnrollmentStatusView.EnrollmentMethod.MANUAL, null, null));
    }

    /**
     * Stores an ACME-enrolled or ACME-renewed dataspace TLS key and certificate chain, tagging the enrollment
     * method as {@code ACME} and recording when the next renewal attempt is due. Clears any previously recorded
     * ACME error, since a successful (re-)enrollment supersedes it.
     */
    public void storeAcmeEnrolledCertificate(PrivateKey privateKey, X509Certificate[] certificateChain, Instant nextRenewalTime) {
        try {
            vaultClient.createDsHttpsTlsCredentials(new InternalSSLKey(privateKey, certificateChain), DsTlsEnrollmentMethod.ACME);
            vaultClient.setDsHttpsTlsEnrollmentStatus(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, null));
        } catch (Exception e) {
            log.error("Failed to store ACME-enrolled dataspace TLS certificate", e);
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }
    }

    /**
     * Records the outcome of an ACME enrollment or renewal attempt that did not replace the stored certificate:
     * a non-null {@code errorDescription} records (or updates) the last error, {@code null} clears it. Preserves
     * the currently recorded enrollment method and next renewal time. A no-op when nothing changed, so callers
     * can call this unconditionally without re-triggering a failure notification for an unchanged error.
     *
     * @return {@code true} if the recorded error changed (including a transition to or from no error)
     */
    public boolean recordAcmeOutcome(String errorDescription) {
        var current = vaultClient.getDsHttpsTlsEnrollmentStatus()
                .orElse(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, null, null));
        if (Objects.equals(current.lastError(), errorDescription)) {
            return false;
        }
        vaultClient.setDsHttpsTlsEnrollmentStatus(
                new DsTlsEnrollmentStatus(current.method(), current.nextRenewalTime(), errorDescription));
        return true;
    }

    private boolean dataspaceTlsCertificateExists() {
        try {
            vaultClient.getDsHttpsTlsCredentials();
            return true;
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(MISSING_SECRET)) {
                return false;
            }
            throw e;
        } catch (Exception e) {
            log.error("Failed to read dataspace TLS certificate", e);
            throw XrdRuntimeException.systemException(INTERNAL_ERROR, e);
        }
    }
}
