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
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatusDto;
import org.niis.xroad.serverconf.model.Client;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between client status in api (enum) and model (string)
 */
@Getter
@RequiredArgsConstructor
public enum ClientStatusMapping {
    SAVED(Client.STATUS_SAVED, ClientStatusDto.SAVED),
    REGISTRATION_IN_PROGRESS(Client.STATUS_REGINPROG, ClientStatusDto.REGISTRATION_IN_PROGRESS),
    REGISTERED(Client.STATUS_REGISTERED, ClientStatusDto.REGISTERED),
    DELETION_IN_PROGRESS(Client.STATUS_DELINPROG, ClientStatusDto.DELETION_IN_PROGRESS),
    GLOBAL_ERROR(Client.STATUS_GLOBALERR, ClientStatusDto.GLOBAL_ERROR),
    DISABLING_IN_PROGRESS(Client.STATUS_DISABLING_INPROG, ClientStatusDto.DISABLING_IN_PROGRESS),
    DISABLED(Client.STATUS_DISABLED, ClientStatusDto.DISABLED),
    ENABLING_IN_PROGRESS(Client.STATUS_ENABLING_INPROG, ClientStatusDto.ENABLING_IN_PROGRESS);


    private final String clientTypeStatus; // ClientType statuses
    private final ClientStatusDto clientStatusDto;

    /**
     * Return matching StatusEnum, if any
     * @param clientTypeStatus
     * @return
     */
    public static Optional<ClientStatusDto> map(String clientTypeStatus) {
        return getFor(clientTypeStatus).map(ClientStatusMapping::getClientStatusDto);
    }

    /**
     * Return matching client type status string, if any
     * @param clientStatusDto
     * @return
     */
    public static Optional<String> map(ClientStatusDto clientStatusDto) {
        return getFor(clientStatusDto).map(ClientStatusMapping::getClientTypeStatus);
    }

    /**
     * return item matching ClientType status, if any
     * @param clientTypeStatus
     * @return
     */
    public static Optional<ClientStatusMapping> getFor(String clientTypeStatus) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.clientTypeStatus.equals(clientTypeStatus))
                .findFirst();
    }

    /**
     * return item matching ClientType status, if any
     * @param clientStatusDto
     * @return
     */
    public static Optional<ClientStatusMapping> getFor(ClientStatusDto clientStatusDto) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.clientStatusDto.equals(clientStatusDto))
                .findFirst();
    }

}
