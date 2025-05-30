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
package org.niis.xroad.securityserver.restapi.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between KeyUsageTypeDto in api (enum) and model (KeyUsageInfo)
 */
@Getter
@RequiredArgsConstructor
public enum KeyUsageTypeMapping {
    SIGNING(KeyUsageTypeDto.SIGNING, KeyUsageInfo.SIGNING),
    AUTHENTICATION(KeyUsageTypeDto.AUTHENTICATION, KeyUsageInfo.AUTHENTICATION);

    private final KeyUsageTypeDto keyUsageTypeDto;
    private final KeyUsageInfo keyUsageInfo;

    /**
     * Return matching KeyUsageInfo, if any
     *
     * @param keyUsageTypeDto
     * @return
     */
    public static Optional<KeyUsageInfo> map(KeyUsageTypeDto keyUsageTypeDto) {
        return getFor(keyUsageTypeDto).map(KeyUsageTypeMapping::getKeyUsageInfo);
    }

    /**
     * Return matching KeyUsageTypeDto, if any
     *
     * @param keyUsageInfo
     * @return
     */
    public static Optional<KeyUsageTypeDto> map(KeyUsageInfo keyUsageInfo) {
        return getFor(keyUsageInfo).map(KeyUsageTypeMapping::getKeyUsageTypeDto);
    }

    /**
     * return KeyUsageInfoMapping matching the given KeyUsageTypeDto, if any
     *
     * @param keyUsageTypeDto
     * @return
     */
    public static Optional<KeyUsageTypeMapping> getFor(KeyUsageTypeDto keyUsageTypeDto) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.keyUsageTypeDto.equals(keyUsageTypeDto))
                .findFirst();
    }

    /**
     * return KeyUsageInfoMapping matching the given KeyUsageInfo, if any
     *
     * @param keyUsageInfo
     * @return
     */
    public static Optional<KeyUsageTypeMapping> getFor(KeyUsageInfo keyUsageInfo) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.keyUsageInfo.equals(keyUsageInfo))
                .findFirst();
    }

}
