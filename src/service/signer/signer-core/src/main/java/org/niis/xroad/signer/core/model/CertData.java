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
package org.niis.xroad.signer.core.model;

import ee.ria.xroad.common.identifier.ClientId;

import org.jetbrains.annotations.NotNull;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * @param id                              Internal database ID.
 * @param keyId                           Internal ID of the key that this certificate belongs to.
 * @param externalId                      ID for cert that is used by the OCSP request keys. (optional)
 * @param memberId                        If this certificate belongs to signing key, then this attribute contains
 *                                        identifier of the member that uses this certificate.
 * @param status                          Holds the status of the certificate.
 * @param active                          Indicates whether the certificate is active or not.
 * @param renewedCertHash                 Hash of the newer version of the certificate that is in the process of registration
 * @param renewalError                    Error message thrown during the certificate automatic renewal process
 * @param nextAutomaticRenewalTime        Next planned automatic renewal time.
 * @param certificate                     Holds the certificate instance.
 * @param sha256hash                      Holds the precalculated sha256 hash of the certificate.
 * @param ocspVerifyBeforeActivationError ocsp error that is populated if external request fails.
 */
public record CertData(Long id,
                       String externalId,
                       Long keyId,
                       ClientId.Conf memberId,
                       String status,
                       boolean active,
                       String renewedCertHash,
                       String renewalError,
                       Instant nextAutomaticRenewalTime,
                       String ocspVerifyBeforeActivationError,
                       X509Certificate certificate,
                       String sha256hash) implements BasicCertInfo {

    public static CertData create(String externalId, Long keyId, X509Certificate certificate, String sha256hash) {
        return new CertData(null, externalId, keyId, null, null, false, null, null,
                null, null, certificate, sha256hash);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CertData certData = (CertData) o;
        return active == certData.active
                && Objects.equals(id, certData.id)
                && Objects.equals(keyId, certData.keyId)
                && Objects.equals(status, certData.status)
                && Objects.equals(externalId, certData.externalId)
                && Objects.equals(sha256hash, certData.sha256hash)
                && Objects.equals(renewalError, certData.renewalError)
                && Objects.equals(memberId, certData.memberId)
                && Objects.equals(renewedCertHash, certData.renewedCertHash)
                && Objects.equals(certificate, certData.certificate)
                && Objects.equals(nextAutomaticRenewalTime, certData.nextAutomaticRenewalTime)
                && Objects.equals(ocspVerifyBeforeActivationError, certData.ocspVerifyBeforeActivationError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, keyId, memberId, status, active, renewedCertHash, renewalError, nextAutomaticRenewalTime,
                ocspVerifyBeforeActivationError, certificate, sha256hash);
    }

    @NotNull
    @Override
    public String toString() {
        return new StringJoiner(", ", CertData.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("externalId='" + externalId + "'")
                .add("keyId=" + keyId)
                .add("status='" + status + "'")
                .add("active=" + active)
                .add("memberId=" + memberId)
                .toString();
    }
}
