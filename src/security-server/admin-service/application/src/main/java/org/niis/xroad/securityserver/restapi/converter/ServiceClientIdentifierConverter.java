/**
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientIdentifierDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converter for ServiceClient identifiers
 */
@Component
@RequiredArgsConstructor
public class ServiceClientIdentifierConverter {

    private final GlobalGroupConverter globalGroupConverter;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Convert encoded service client id into ServiceClientIdentifierDto (based on serviceClientType,
     * id and localGroupCode).
     * Correct ServiceClientIdentifierDto is determined based solely on number of encoded id separators
     * and whether the whole string is numeric or not
     * @param encodedServiceClientIdentifier encoded service client id
     * @return ServiceClientIdentifierDto object
     * @throws BadServiceClientIdentifierException if encoded service client id was badly formatted
     */
    public ServiceClientIdentifierDto convertId(String encodedServiceClientIdentifier)
            throws BadServiceClientIdentifierException {
        ServiceClientIdentifierDto dto = new ServiceClientIdentifierDto();
        if (clientIdConverter.isEncodedSubsystemId(encodedServiceClientIdentifier)) {
            // subsystem
            ClientId.Conf clientId = clientIdConverter.convertId(encodedServiceClientIdentifier);
            dto.setXRoadId(clientId);
        } else if (globalGroupConverter.isEncodedGlobalGroupId(encodedServiceClientIdentifier)) {
            GlobalGroupId.Conf globalGroupId = globalGroupConverter.convertId(encodedServiceClientIdentifier);
            dto.setXRoadId(globalGroupId);
        } else if (StringUtils.isNumeric(encodedServiceClientIdentifier)) {
            // local group
            Long id;
            try {
                id = Long.parseLong(encodedServiceClientIdentifier);
            } catch (NumberFormatException e) {
                throw new BadServiceClientIdentifierException(encodedServiceClientIdentifier);
            }
            dto.setLocalGroupId(id);
        } else {
            throw new BadServiceClientIdentifierException(encodedServiceClientIdentifier);
        }
        return dto;
    }

    public static class BadServiceClientIdentifierException extends Exception {
        @Getter
        private String serviceClientIdentifier;

        public BadServiceClientIdentifierException(String serviceClientIdentifier) {
            super();
            this.serviceClientIdentifier = serviceClientIdentifier;
        }
    }

    /**
     * Convert collection of encoded service client ids into ServiceClientIdentifierDtos
     * See {@link #convertId(String)} for details.
     * @throws BadServiceClientIdentifierException if encoded service client id was badly formatted
     */
    public List<ServiceClientIdentifierDto> convertIds(Iterable<String> encodedServiceClientIdentifiers)
            throws BadServiceClientIdentifierException {
        List<ServiceClientIdentifierDto> dtos = new ArrayList<>();
        for (String encodedIdentifier : encodedServiceClientIdentifiers) {
            dtos.add(convertId(encodedIdentifier));
        }
        return dtos;
    }

}
