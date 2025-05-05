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
package org.niis.xroad.signer.core.tokenmanager.mapper;

import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.niis.xroad.serverconf.impl.converter.GenericBiDirectionalMapper;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertRequestEntity;
import org.niis.xroad.serverconf.impl.mapper.XRoadIdMapper;
import org.niis.xroad.signer.core.model.CertRequest;
import org.niis.xroad.signer.core.util.SignerUtil;

import java.util.List;

@Mapper(uses = XRoadIdMapper.class,
        componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        injectionStrategy = InjectionStrategy.SETTER,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class CertRequestMapper implements GenericBiDirectionalMapper<KeyConfCertRequestEntity, CertRequest> {
    @Setter
    private XRoadIdMapper xroadIdMapper;

    @Override
    public CertRequest toTarget(KeyConfCertRequestEntity type) {
        return new CertRequest(
                getCertReqId(type),
                xroadIdMapper.toTarget(type.getMemberId()),
                type.getSubjectName(),
                type.getSubjectAlternativeName(),
                type.getCertificateProfile());
    }

    @Override
    public KeyConfCertRequestEntity toSource(CertRequest certRequest) {
        var entity = new KeyConfCertRequestEntity();
        entity.setCertRequestId(certRequest.id());
        entity.setMemberId(xroadIdMapper.toEntity(certRequest.memberId()));
        entity.setSubjectName(certRequest.subjectName());
        entity.setSubjectAlternativeName(certRequest.subjectAltName());
        entity.setCertificateProfile(certRequest.certificateProfile());

        return entity;
    }

    abstract List<CertRequest> toTargets(List<KeyConfCertRequestEntity> entities);

    abstract List<KeyConfCertRequestEntity> toEntities(List<CertRequest> domains);

    private static String getCertReqId(KeyConfCertRequestEntity type) {
        return ObjectUtils.defaultIfNull(type.getCertRequestId(), SignerUtil.randomId());
    }
}
