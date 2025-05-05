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

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import lombok.Setter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertRequestEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfCertificateEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfKeyEntity;
import org.niis.xroad.serverconf.impl.entity.type.KeyUsage;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.Objects;

import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;

@Mapper(uses = {CertRequestMapper.class, CertMapper.class},
        componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        injectionStrategy = InjectionStrategy.SETTER,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class KeyMapper {
    @Setter
    private CertRequestMapper certRequestMapper;
    @Setter
    private CertMapper certMapper;

    public KeyConfKeyEntity toTarget(Key key) {
        var keyType = new KeyConfKeyEntity();
        keyType.setFriendlyName(key.getFriendlyName());
        keyType.setLabel(key.getLabel());
        keyType.setKeyId(key.getId());
        keyType.setUsage(toSource(key.getUsage()));
        keyType.setSignMechanismName(key.getSignMechanismName());

        if (key.getPublicKey() != null) {
            keyType.setPublicKey(key.getPublicKey());
        }

        keyType.getCertificates().addAll(certMapper.toEntities(key.getCerts()));
        keyType.getCertRequests().addAll(certRequestMapper.toEntities(key.getCertRequests()));


        return keyType;
    }

    public Key toSource(Token device, KeyConfKeyEntity keyType, SignMechanism defaultSigneMechanism) {
        Key key = new Key(device, keyType.getKeyId(), Objects.requireNonNullElse(keyType.getSignMechanismName(), defaultSigneMechanism));
        key.setFriendlyName(keyType.getFriendlyName());
        key.setLabel(keyType.getLabel());
        key.setUsage(toTarget(keyType.getUsage()));

        if (keyType.getPublicKey() != null) {
            key.setPublicKey(encodeBase64(keyType.getPublicKey()));
        }

        for (KeyConfCertificateEntity certType : keyType.getCertificates()) {
            key.addCert(certMapper.toTarget(certType));
        }

        for (KeyConfCertRequestEntity certRequestType : keyType.getCertRequests()) {
            key.addCertRequest(certRequestMapper.toTarget(certRequestType));
        }

        return key;
    }

    abstract KeyUsageInfo toTarget(KeyUsage keyUsage);

    KeyUsage toSource(KeyUsageInfo keyUsageInfo) {
        switch (keyUsageInfo) {
            case SIGNING -> {
                return KeyUsage.SIGNING;
            }
            case AUTHENTICATION -> {
                return KeyUsage.AUTHENTICATION;
            }
            default -> {
                return null;
            }
        }
    }
}
