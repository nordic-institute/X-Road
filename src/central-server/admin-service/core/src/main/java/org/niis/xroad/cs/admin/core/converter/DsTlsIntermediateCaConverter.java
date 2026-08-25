/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.converter;

import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.cs.admin.api.dto.DsTlsIntermediateCertificateAuthority;
import org.niis.xroad.cs.admin.core.entity.DsTlsIntermediateCaEntity;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CERTIFICATE;

@Component
@RequiredArgsConstructor
public class DsTlsIntermediateCaConverter {

    private final CertificateConverter certConverter;

    public DsTlsIntermediateCertificateAuthority convert(DsTlsIntermediateCaEntity entity) {
        return new DsTlsIntermediateCertificateAuthority()
                .setId(entity.getId())
                .setDsTlsCertificationAuthorityId(entity.getDsTlsCa() == null ? null : entity.getDsTlsCa().getId())
                .setCaCertificate(certConverter.toCertificateDetails(entity.getCert()))
                .setCreatedAt(entity.getCreatedAt())
                .setUpdatedAt(entity.getUpdatedAt());
    }

    public List<DsTlsIntermediateCertificateAuthority> convert(Collection<DsTlsIntermediateCaEntity> entities) {
        return entities.stream()
                .map(this::convert)
                .sorted(Comparator.comparing(DsTlsIntermediateCertificateAuthority::getId))
                .toList();
    }

    public DsTlsIntermediateCaEntity toEntity(byte[] certificate) {
        try {
            CertUtils.readCertificateChain(certificate);
        } catch (Exception e) {
            throw new BadRequestException(e, INVALID_CERTIFICATE.build());
        }
        final var entity = new DsTlsIntermediateCaEntity();
        entity.setCert(certificate);
        return entity;
    }

}
