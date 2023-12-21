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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link TokenStatus} in api (enum) and model {@link TokenStatusInfo}
 */
@Getter
@RequiredArgsConstructor
public enum TokenStatusMapping {
    OK(TokenStatusInfo.OK, TokenStatus.OK),
    USER_PIN_LOCKED(TokenStatusInfo.USER_PIN_LOCKED, TokenStatus.USER_PIN_LOCKED),
    USER_PIN_INCORRECT(TokenStatusInfo.USER_PIN_INCORRECT, TokenStatus.USER_PIN_INCORRECT),
    USER_PIN_INVALID(TokenStatusInfo.USER_PIN_INVALID, TokenStatus.USER_PIN_INVALID),
    USER_PIN_EXPIRED(TokenStatusInfo.USER_PIN_EXPIRED, TokenStatus.USER_PIN_EXPIRED),
    USER_PIN_COUNT_LOW(TokenStatusInfo.USER_PIN_COUNT_LOW, TokenStatus.USER_PIN_COUNT_LOW),
    USER_PIN_FINAL_TRY(TokenStatusInfo.USER_PIN_FINAL_TRY, TokenStatus.USER_PIN_FINAL_TRY);

    private final TokenStatusInfo tokenStatusInfo;
    private final TokenStatus tokenStatus;

    /**
     * Return matching {@link TokenStatusInfo}, if any
     * @param tokenStatus
     */
    public static Optional<TokenStatusInfo> map(TokenStatus tokenStatus) {
        return getFor(tokenStatus).map(TokenStatusMapping::getTokenStatusInfo);
    }

    /**
     * Return matching {@link TokenStatus}, if any
     * @param tokenStatusInfo
     */
    public static Optional<TokenStatus> map(TokenStatusInfo tokenStatusInfo) {
        return getFor(tokenStatusInfo).map(TokenStatusMapping::getTokenStatus);
    }

    /**
     * Return matching {@link TokenStatusMapping}, if any
     * @param tokenStatusInfo
     */
    public static Optional<TokenStatusMapping> getFor(TokenStatusInfo tokenStatusInfo) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.tokenStatusInfo.equals(tokenStatusInfo))
                .findFirst();
    }

    /**
     * Return matching {@link TokenStatusMapping}, if any
     * @param tokenStatus
     */
    public static Optional<TokenStatusMapping> getFor(TokenStatus tokenStatus) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.tokenStatus.equals(tokenStatus))
                .findFirst();
    }

}
