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
package org.niis.xroad.signer.jpa.mapper;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.serverconf.impl.entity.SignerKeyEntity;
import org.niis.xroad.serverconf.impl.entity.type.KeyType;
import org.niis.xroad.serverconf.impl.entity.type.KeyUsage;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.model.BasicKeyInfo;
import org.niis.xroad.signer.core.model.HardwareKey;
import org.niis.xroad.signer.core.model.SoftwareKey;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

@ApplicationScoped
@RequiredArgsConstructor
public class KeyMapper implements GenericUniDirectionalMapper<SignerKeyEntity, BasicKeyInfo> {

    @Override
    public BasicKeyInfo toTarget(SignerKeyEntity source) {
        if (source.getType() == KeyType.SOFTWARE) {
            return toSoftwareKey(source);
        } else if (source.getType() == KeyType.HARDWARE) {
            return toHardwareKey(source);
        } else {
            throw new SignerException("Unknown key type: " + source.getType());
        }
    }

    private SoftwareKey toSoftwareKey(SignerKeyEntity source) {
        return new SoftwareKey(
                source.getId(),
                source.getTokenId(),
                source.getExternalId(),
                toTarget(source.getUsage()),
                source.getFriendlyName(),
                source.getLabel(),
                source.getPublicKey(),
                source.getSignMechanismName(),
                source.getKeyStore()
        );
    }

    private HardwareKey toHardwareKey(SignerKeyEntity source) {
        return new HardwareKey(
                source.getId(),
                source.getTokenId(),
                source.getExternalId(),
                toTarget(source.getUsage()),
                source.getFriendlyName(),
                source.getLabel(),
                source.getPublicKey(),
                source.getSignMechanismName()
        );
    }

    public KeyUsageInfo toTarget(KeyUsage keyUsage) {
        if (keyUsage == null) {
            return null;
        }

        return switch (keyUsage) {
            case SIGNING -> KeyUsageInfo.SIGNING;
            case AUTHENTICATION -> KeyUsageInfo.AUTHENTICATION;
        };
    }

}
