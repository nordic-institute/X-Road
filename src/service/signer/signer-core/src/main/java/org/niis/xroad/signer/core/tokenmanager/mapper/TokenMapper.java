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
package org.niis.xroad.signer.core.tokenmanager.mapper;

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import lombok.Setter;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import org.niis.xroad.serverconf.impl.converter.GenericBiDirectionalMapper;
import org.niis.xroad.serverconf.impl.entity.KeyConfDeviceEntity;
import org.niis.xroad.serverconf.impl.entity.KeyConfKeyEntity;
import org.niis.xroad.signer.core.model.Key;
import org.niis.xroad.signer.core.model.Token;
import org.niis.xroad.signer.core.tokenmanager.module.SoftwareModuleType;

import java.util.Set;

import static java.util.Objects.requireNonNullElse;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI,
        injectionStrategy = InjectionStrategy.SETTER,
        unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class TokenMapper implements GenericBiDirectionalMapper<KeyConfDeviceEntity, Token> {
    @Setter
    private KeyMapper keyMapper;

    @Override
    public Token toTarget(KeyConfDeviceEntity source) {
        var token = new Token(source.getDeviceType(), source.getDeviceId(), source.getId());
        token.setFriendlyName(source.getFriendlyName());
        token.setSlotIndex(source.getPinIndex() != null ? source.getPinIndex() : 0);
        token.setSerialNumber(source.getTokenId());
        token.setLabel(source.getSlotId());

        // software token forgets batch signing setting
        if (SoftwareModuleType.TYPE.equals(token.getType())) {
            token.setBatchSigningEnabled(true);
        }

        for (KeyConfKeyEntity keyType : source.getKeys()) {
            var key = keyMapper.toSource(token, keyType, requireNonNullElse(source.getSignMechanismName(), SignMechanism.CKM_RSA_PKCS));
            token.addKey(key);
        }

        return token;
    }

    @Override
    public KeyConfDeviceEntity toSource(Token token) {
        var entity = new KeyConfDeviceEntity();
        entity.setDeviceId(token.getId());
        entity.setDeviceType(token.getType());
        entity.setFriendlyName(token.getFriendlyName());
        entity.setTokenId(token.getId());
        entity.setPinIndex(token.getSlotIndex());
        entity.setTokenId(token.getSerialNumber());
        entity.setSlotId(token.getLabel());

        token.getKeys().stream()
                .filter(Key::hasCertsOrCertRequests)
                .forEach(key -> entity.getKeys().add(keyMapper.toTarget(key)));

        return entity;
    }

    public abstract Set<Token> toTargets(Set<KeyConfDeviceEntity> entities);

    public abstract Set<KeyConfDeviceEntity> toEntities(Set<Token> domains);

}
