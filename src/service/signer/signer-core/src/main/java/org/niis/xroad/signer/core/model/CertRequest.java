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

import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.protocol.dto.CertRequestInfoProto;

import static java.util.Optional.ofNullable;

/**
 * Model object representing the certificate request.
 *
 * @param id                 the unique request id
 * @param memberId           the client id of the member
 * @param subjectName        the subject name
 * @param subjectAltName     the subject alternative name
 * @param certificateProfile the certificate profile
 */
public record CertRequest(
        String id,
        ClientId.Conf memberId,
        String subjectName,
        String subjectAltName,
        String certificateProfile) {

    /**
     * Converts this object to value object.
     *
     * @return the value object
     */
    public CertRequestInfoProto toProtoDTO() {
        final CertRequestInfoProto.Builder builder = CertRequestInfoProto.newBuilder()
                .setId(id)
                .setSubjectName(subjectName);

        ofNullable(subjectAltName).ifPresent(builder::setSubjectAltName);
        ofNullable(certificateProfile).ifPresent(builder::setCertificateProfile);
        ofNullable(memberId).map(ClientIdMapper::toDto).ifPresent(builder::setMemberId);
        return builder.build();
    }

    /**
     * Converts this object to value object.
     *
     * @return the value object
     */
    public CertRequestInfo toDTO() {
        return new CertRequestInfo(toProtoDTO());
    }
}
