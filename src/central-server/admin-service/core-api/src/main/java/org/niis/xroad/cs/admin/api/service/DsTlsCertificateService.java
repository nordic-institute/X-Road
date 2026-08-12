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
package org.niis.xroad.cs.admin.api.service;

import org.niis.xroad.cs.admin.api.dto.CertificateDetails;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;

public interface DsTlsCertificateService {

    /**
     * Enrollment method and ACME renewal bookkeeping for the dataspace TLS certificate, as surfaced through the
     * enrollment-status API. {@code NONE} means no certificate has been stored yet - neither uploaded nor
     * enrolled - but a non-null {@code lastError} alongside {@code NONE} means a first ACME enrollment attempt
     * exists and is failing; nothing is configured or served, yet the admin still needs to see why.
     */
    record EnrollmentStatusView(EnrollmentMethod enrollmentMethod, Instant nextRenewalTime, String lastError) {
        public enum EnrollmentMethod { NONE, MANUAL, ACME }
    }

    CertificateDetails getCertificateDetails();

    /**
     * Returns the currently stored dataspace TLS certificate as a raw {@link X509Certificate}, for callers (the
     * ACME enrollment worker) that need to inspect it rather than render it through the REST API.
     *
     * @throws org.niis.xroad.common.exception.NotFoundException when no certificate is stored yet
     */
    X509Certificate getDataspaceTlsCertificate();

    CertificateDetails uploadCertificate(byte[] keyBytes, byte[] certificateChainBytes);

    /**
     * Returns the current enrollment status: {@code NONE} when no certificate is stored at all, {@code MANUAL}
     * when a certificate exists but no enrollment status has ever been recorded for it, or the recorded method
     * otherwise.
     */
    EnrollmentStatusView getEnrollmentStatus();

    /**
     * Stores an ACME-enrolled or ACME-renewed dataspace TLS key and certificate chain, tagging the enrollment
     * method as {@code ACME} and recording when the next renewal attempt is due. Clears any previously recorded
     * ACME error, since a successful (re-)enrollment supersedes it.
     */
    void storeAcmeEnrolledCertificate(PrivateKey privateKey, X509Certificate[] certificateChain, Instant nextRenewalTime);

    /**
     * Records the outcome of an ACME enrollment or renewal attempt that did not replace the stored certificate:
     * a non-null {@code errorDescription} records (or updates) the last error, {@code null} clears it. Preserves
     * the currently recorded enrollment method and next renewal time. A no-op when nothing changed, so callers
     * can call this unconditionally without re-triggering the alerts banner for an unchanged error.
     *
     * @return {@code true} if the recorded error changed (including a transition to or from no error)
     */
    boolean recordAcmeOutcome(String errorDescription);
}
