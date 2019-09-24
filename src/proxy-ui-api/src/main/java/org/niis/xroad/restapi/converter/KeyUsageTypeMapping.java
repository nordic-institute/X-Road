/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import lombok.Getter;
import org.niis.xroad.restapi.openapi.model.KeyUsageType;
import org.niis.xroad.restapi.openapi.model.TokenStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link TokenStatus} in api (enum) and model {@link TokenStatusInfo}
 */
@Getter
public enum KeyUsageTypeMapping {
    AUTHENTICATION(KeyUsageInfo.AUTHENTICATION, KeyUsageType.AUTHENTICATION),
    SIGNING(KeyUsageInfo.SIGNING, KeyUsageType.SIGNING);

    private final KeyUsageInfo keyUsageInfo;
    private final KeyUsageType keyUsageType;

    KeyUsageTypeMapping(KeyUsageInfo keyUsageInfo, KeyUsageType keyUsageType) {
        this.keyUsageInfo = keyUsageInfo;
        this.keyUsageType = keyUsageType;
    }

    /**
     * Return matching {@link KeyUsageInfo}, if any
     * @param keyUsageType
     */
    public static Optional<KeyUsageInfo> map(KeyUsageType keyUsageType) {
        return getFor(keyUsageType).map(KeyUsageTypeMapping::getKeyUsageInfo);
    }

    /**
     * Return matching {@link KeyUsageType}, if any
     * @param keyUsageInfo
     */
    public static Optional<KeyUsageType> map(KeyUsageInfo keyUsageInfo) {
        return getFor(keyUsageInfo).map(KeyUsageTypeMapping::getKeyUsageType);
    }

    /**
     * Return matching {@link KeyUsageTypeMapping}, if any
     * @param keyUsageInfo
     */
    public static Optional<KeyUsageTypeMapping> getFor(KeyUsageInfo keyUsageInfo) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.keyUsageInfo.equals(keyUsageInfo))
                .findFirst();
    }

    /**
     * Return matching {@link KeyUsageTypeMapping}, if any
     * @param keyUsageType
     */
    public static Optional<KeyUsageTypeMapping> getFor(KeyUsageType keyUsageType) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.keyUsageType.equals(keyUsageType))
                .findFirst();
    }

}
