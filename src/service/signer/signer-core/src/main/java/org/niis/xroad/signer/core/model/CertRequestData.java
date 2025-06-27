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
package org.niis.xroad.signer.core.model;

import ee.ria.xroad.common.identifier.ClientId;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Model object representing the certificate request.
 *
 * @param id                 the unique request id
 * @param externalId         the external id of the DTO
 * @param keyId              the key id to which the request belongs
 * @param memberId           the client id of the member
 * @param subjectName        the subject name
 * @param subjectAltName     the subject alternative name
 * @param certificateProfile the certificate profile
 * @param createdAt          when the DB record was created
 * @param updatedAt          when the DB record was last updated
 */
public record CertRequestData(
        Long id,
        String externalId,
        Long keyId,
        ClientId.Conf memberId,
        String subjectName,
        String subjectAltName,
        String certificateProfile,
        Instant createdAt,
        Instant updatedAt) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CertRequestData that = (CertRequestData) o;
        return Objects.equals(id, that.id)
                && Objects.equals(externalId, that.externalId)
                && Objects.equals(subjectName, that.subjectName)
                && Objects.equals(subjectAltName, that.subjectAltName)
                && Objects.equals(memberId, that.memberId)
                && Objects.equals(certificateProfile, that.certificateProfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, externalId, memberId, subjectName, subjectAltName, certificateProfile);
    }

    @NotNull
    @Override
    public String toString() {
        return new StringJoiner(", ", CertRequestData.class.getSimpleName() + "[", "]")
                .add("id=" + id)
                .add("externalId='" + externalId + "'")
                .add("createdAt=" + createdAt)
                .add("updatedAt=" + updatedAt)
                .toString();
    }
}
