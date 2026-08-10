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
package org.niis.xroad.cs.admin.core.converter;

import ee.ria.xroad.common.util.CertUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.dto.DsTlsCaListItem;
import org.niis.xroad.cs.admin.core.entity.DsTlsCaEntity;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DsTlsCaConverter {
    private final DsTlsCaInfoConverter dsTlsCaInfoConverter;

    public DsTlsCa convert(DsTlsCaEntity entity) {
        final X509Certificate[] certificates = CertUtils.readCertificateChain(entity.getCaInfo().getCert());
        final X509Certificate certificate = certificates[0];

        return new DsTlsCa()
                .setId(entity.getId())
                .setName(entity.getName())
                .setIssuerDistinguishedName(certificate.getIssuerX500Principal().toString())
                .setSubjectDistinguishedName(certificate.getSubjectX500Principal().toString())
                .setNotBefore(entity.getCaInfo().getValidFrom())
                .setNotAfter(entity.getCaInfo().getValidTo())
                .setCertificate(entity.getCaInfo().getCert())
                .setIntermediateCas(entity.getIntermediateCaInfos().stream()
                        .map(dsTlsCaInfoConverter::toDsTlsCaIntermediateCa)
                        .toList())
                .setCreatedAt(entity.getCreatedAt())
                .setUpdatedAt(entity.getUpdatedAt())
                .setAcmeServerDirectoryUrl(entity.getAcmeServerDirectoryUrl())
                .setDsTlsCertificateProfileId(entity.getDsTlsCertificateProfileId());
    }

    public List<DsTlsCaListItem> toListItems(Collection<DsTlsCaEntity> entities) {
        return entities.stream()
                .map(this::toListItem)
                .toList();
    }

    private DsTlsCaListItem toListItem(final DsTlsCaEntity dsTlsCa) {
        return new DsTlsCaListItem()
                .setId(dsTlsCa.getId())
                .setName(dsTlsCa.getName())
                .setNotBefore(dsTlsCa.getCaInfo().getValidFrom())
                .setNotAfter(dsTlsCa.getCaInfo().getValidTo());
    }

}
