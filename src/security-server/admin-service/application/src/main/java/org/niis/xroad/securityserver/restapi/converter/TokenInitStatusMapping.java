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
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenInitStatusDto;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link TokenInitStatusDto} in api (enum) and model {@link TokenInitStatusInfo}
 */
@Getter
@RequiredArgsConstructor
public enum TokenInitStatusMapping {
    INITIALIZED(TokenInitStatusInfo.INITIALIZED, TokenInitStatusDto.INITIALIZED),
    NOT_INITIALIZED(TokenInitStatusInfo.NOT_INITIALIZED, TokenInitStatusDto.NOT_INITIALIZED),
    UNKNOWN(TokenInitStatusInfo.UNKNOWN, TokenInitStatusDto.UNKNOWN);

    private final TokenInitStatusInfo tokenInitStatusInfo;
    private final TokenInitStatusDto tokenInitStatusDto;

    /**
     * Return matching {@link TokenInitStatusInfo}, if any
     *
     * @param tokenInitStatusDto
     */
    public static Optional<TokenInitStatusInfo> map(TokenInitStatusDto tokenInitStatusDto) {
        return getFor(tokenInitStatusDto).map(TokenInitStatusMapping::getTokenInitStatusInfo);
    }

    /**
     * Return matching {@link TokenInitStatusDto}, if any
     *
     * @param tokenInitStatusInfo
     */
    public static TokenInitStatusDto map(TokenInitStatusInfo tokenInitStatusInfo) {
        return getFor(tokenInitStatusInfo)
                .map(TokenInitStatusMapping::getTokenInitStatusDto)
                .orElse(TokenInitStatusDto.UNKNOWN);
    }

    /**
     * Return matching {@link TokenInitStatusMapping}, if any
     *
     * @param tokenInitStatusInfo
     */
    public static Optional<TokenInitStatusMapping> getFor(TokenInitStatusInfo tokenInitStatusInfo) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.tokenInitStatusInfo.equals(tokenInitStatusInfo))
                .findFirst();
    }

    /**
     * Return matching {@link TokenInitStatusMapping}, if any
     *
     * @param tokenInitStatusDto
     */
    public static Optional<TokenInitStatusMapping> getFor(TokenInitStatusDto tokenInitStatusDto) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.tokenInitStatusDto.equals(tokenInitStatusDto))
                .findFirst();
    }
}
