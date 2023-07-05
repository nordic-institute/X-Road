/**
 * The MIT License
 * <p>
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
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.CertificateAuthority;
import org.niis.xroad.cs.admin.core.entity.CaInfoEntity;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_CERTIFICATE;

@Component
@RequiredArgsConstructor
public class CaInfoConverter {

    private final CertificateConverter certConverter;
    private final OcspResponderConverter ocspResponderConverter;

    public CertificateAuthority toCertificateAuthority(CaInfoEntity caInfo) {
        return new CertificateAuthority()
                .setId(caInfo.getId())
                .setCaCertificate(certConverter.toCertificateDetails(caInfo.getCert()))
                .setOcspResponders(caInfo.getOcspInfos().stream()
                        .map(ocspResponderConverter::toModel)
                        .collect(toList()))
                .setUpdatedAt(caInfo.getUpdatedAt())
                .setCreatedAt(caInfo.getCreatedAt());
    }

    public List<CertificateAuthority> toCertificateAuthorities(Collection<CaInfoEntity> caInfos) {
        return caInfos.stream()
                .map(this::toCertificateAuthority)
                .sorted(Comparator.comparing(CertificateAuthority::getId))
                .collect(toList());
    }

    public CaInfoEntity toCaInfo(byte[] certificate) {
        try {
            final X509Certificate[] certificates = CertUtils.readCertificateChain(certificate);
            final X509Certificate cert = certificates[0];

            final var caInfo = new CaInfoEntity();
            caInfo.setCert(certificate);
            caInfo.setValidFrom(cert.getNotBefore().toInstant());
            caInfo.setValidTo(cert.getNotAfter().toInstant());
            return caInfo;
        } catch (Exception e) {
            throw new ValidationFailureException(INVALID_CERTIFICATE);
        }
    }

}
