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

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.niis.xroad.serverconf.impl.converter.GenericBiDirectionalMapper;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertificateEntity;
import org.niis.xroad.serverconf.impl.mapper.XRoadIdMapper;
import org.niis.xroad.signer.core.model.Cert;
import org.niis.xroad.signer.core.util.SignerUtil;

import java.util.List;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

@Slf4j
@RequiredArgsConstructor
@Mapper(uses = XRoadIdMapper.class,
        componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        injectionStrategy = InjectionStrategy.SETTER,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class CertMapper implements GenericBiDirectionalMapper<KeyConfCertificateEntity, Cert> {
    @Setter
    private XRoadIdMapper xroadIdMapper;

    @Override
    public Cert toTarget(KeyConfCertificateEntity source) {
        var cert = new Cert(getCertId(source));
        cert.setMemberId(xroadIdMapper.toTarget(source.getMemberId()));
        cert.setActive(source.getActive());
        cert.setStatus(source.getStatus());
        cert.setSavedToConfiguration(true);
        cert.setCertificate(source.getContents());
        cert.setOcspVerifyBeforeActivationError(source.getOcspVerifyError());
        cert.setRenewedCertHash(source.getRenewedCertHash());
        cert.setRenewalError(source.getRenewalError());

        if (source.getNextRenewalTime() != null) {
            cert.setNextAutomaticRenewalTime(source.getNextRenewalTime());
        }

        return cert;
    }

    @Override
    public KeyConfCertificateEntity toSource(Cert cert) {
        var entity = new KeyConfCertificateEntity();
        entity.setCertId(cert.getId());
        entity.setMemberId(XRoadIdMapper.get().toEntity(cert.getMemberId()));
        entity.setActive(cert.isActive());
        entity.setStatus(cert.getStatus());
        entity.setContents(cert.getBytes());
        entity.setOcspVerifyError(cert.getOcspVerifyBeforeActivationError());
        entity.setRenewedCertHash(cert.getRenewedCertHash());
        entity.setRenewalError(cert.getRenewalError());
        entity.setNextRenewalTime(cert.getNextAutomaticRenewalTime());

        return entity;
    }

    abstract List<Cert> toTargets(List<KeyConfCertificateEntity> entities);

    abstract List<KeyConfCertificateEntity> toEntities(List<Cert> domains);

    private String getCertId(KeyConfCertificateEntity type) {
        if (type.getCertId() != null) {
            return type.getCertId();
        } else {
            try {
                return calculateCertHexHash(type.getContents());
            } catch (Exception e) {
                log.error("Failed to calculate certificate hash for {}", type, e);

                return SignerUtil.randomId();
            }
        }
    }

}
