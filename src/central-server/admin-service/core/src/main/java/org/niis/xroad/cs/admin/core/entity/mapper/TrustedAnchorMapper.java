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
package org.niis.xroad.cs.admin.core.entity.mapper;


import ee.ria.xroad.common.conf.globalconf.ConfigurationAnchorV2;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.niis.xroad.cs.admin.api.converter.GenericUniDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlCertEntity;
import org.niis.xroad.cs.admin.core.entity.AnchorUrlEntity;
import org.niis.xroad.cs.admin.core.entity.TrustedAnchorEntity;

import static ee.ria.xroad.common.util.CryptoUtils.calculateAnchorHashDelimited;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TrustedAnchorMapper extends GenericUniDirectionalMapper<TrustedAnchorEntity, TrustedAnchor> {

    default TrustedAnchorEntity toEntity(ConfigurationAnchorV2 anchorV2, byte[] anchorFile, TrustedAnchorEntity entity) {
        entity.setInstanceIdentifier(anchorV2.getInstanceIdentifier());
        entity.setTrustedAnchorFile(anchorFile);
        entity.setTrustedAnchorHash(calculateAnchorHashDelimited(anchorFile));
        entity.setGeneratedAt(anchorV2.getGeneratedAt().toInstant());
        entity.getAnchorUrls().clear();
        anchorV2.getLocations()
                .forEach(location -> {
                    final AnchorUrlEntity urlEntity = new AnchorUrlEntity();
                    urlEntity.setUrl(location.getDownloadURL());
                    location.getVerificationCerts().forEach(cert -> {
                        AnchorUrlCertEntity urlCertEntity = new AnchorUrlCertEntity();
                        urlCertEntity.setCert(cert);
                        urlEntity.addAnchorUrlCert(urlCertEntity);
                    });
                    entity.addAnchorUrl(urlEntity);
                });

        return entity;
    }

}
