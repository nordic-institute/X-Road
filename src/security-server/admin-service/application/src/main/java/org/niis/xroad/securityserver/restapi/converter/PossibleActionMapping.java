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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleActionDto;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link org.niis.xroad.securityserver.restapi.openapi.model.PossibleActionDto} in api (enum) and
 * model {@link PossibleActionEnum} status string
 */
@Getter
@RequiredArgsConstructor
public enum PossibleActionMapping {

    DELETE(PossibleActionEnum.DELETE, PossibleActionDto.DELETE),
    ACTIVATE(PossibleActionEnum.ACTIVATE, PossibleActionDto.ACTIVATE),
    DISABLE(PossibleActionEnum.DISABLE, PossibleActionDto.DISABLE),
    LOGIN(PossibleActionEnum.TOKEN_ACTIVATE, PossibleActionDto.LOGIN),
    LOGOUT(PossibleActionEnum.TOKEN_DEACTIVATE, PossibleActionDto.LOGOUT),
    TOKEN_CHANGE_PIN(PossibleActionEnum.TOKEN_CHANGE_PIN, PossibleActionDto.TOKEN_CHANGE_PIN),
    TOKEN_DELETE(PossibleActionEnum.TOKEN_DELETE, PossibleActionDto.TOKEN_DELETE),
    REGISTER(PossibleActionEnum.REGISTER, PossibleActionDto.REGISTER),
    UNREGISTER(PossibleActionEnum.UNREGISTER, PossibleActionDto.UNREGISTER),
    IMPORT_FROM_TOKEN(PossibleActionEnum.IMPORT_FROM_TOKEN, PossibleActionDto.IMPORT_FROM_TOKEN),
    GENERATE_KEY(PossibleActionEnum.GENERATE_KEY, PossibleActionDto.GENERATE_KEY),
    EDIT_FRIENDLY_NAME(PossibleActionEnum.EDIT_FRIENDLY_NAME, PossibleActionDto.EDIT_FRIENDLY_NAME),
    GENERATE_AUTH_CSR(PossibleActionEnum.GENERATE_AUTH_CSR, PossibleActionDto.GENERATE_AUTH_CSR),
    GENERATE_SIGN_CSR(PossibleActionEnum.GENERATE_SIGN_CSR, PossibleActionDto.GENERATE_SIGN_CSR);

    private final PossibleActionEnum possibleActionEnum;
    private final PossibleActionDto possibleActionDto;

    /**
     * Return matching {@link PossibleActionEnum}, if any
     * @param possibleActionDto
     */
    public static Optional<PossibleActionEnum> map(PossibleActionDto possibleActionDto) {
        return getFor(possibleActionDto).map(PossibleActionMapping::getPossibleActionEnum);
    }

    /**
     * Return matching {@link PossibleActionDto}, if any
     * @param possibleActionEnum
     */
    public static Optional<PossibleActionDto> map(PossibleActionEnum possibleActionEnum) {
        return getFor(possibleActionEnum).map(PossibleActionMapping::getPossibleActionDto);
    }

    /**
     * Return matching {@link PossibleActionMapping}, if any
     * @param possibleActionEnum
     */
    public static Optional<PossibleActionMapping> getFor(PossibleActionEnum possibleActionEnum) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.possibleActionEnum.equals(possibleActionEnum))
                .findFirst();
    }

    /**
     * Return matching {@link PossibleActionMapping}, if any
     * @param possibleActionDto
     */
    public static Optional<PossibleActionMapping> getFor(PossibleActionDto possibleActionDto) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.possibleActionDto.equals(possibleActionDto))
                .findFirst();
    }

}
