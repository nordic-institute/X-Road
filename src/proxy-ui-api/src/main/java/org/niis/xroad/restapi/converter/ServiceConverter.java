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

import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.converter.Converters.ENCODED_ID_SEPARATOR;

/**
 * Convert Service related data between openapi and service domain classes
 */
@Component
public class ServiceConverter {

    /**
     * Encoded service id consists of <encoded client id>:<full service code>
     * Separator ':' is ClientConverter.ENCODED_CLIENT_AND_SERVICE_ID_SEPARATOR
     */
    public static final int FULL_SERVICE_CODE_INDEX = 4;

    private ClientConverter clientConverter;

    @Autowired
    public ServiceConverter(ClientConverter clientConverter) {
        this.clientConverter = clientConverter;
    }

    /**
     * Converts a collection of ServiceTypes to a list of Services
     * @param serviceTypes
     * @return
     */
    public List<Service> convertServices(Collection<ServiceType> serviceTypes, ClientId clientId) {
        return serviceTypes.stream()
                .map(serviceType -> convert(serviceType, clientId))
                .collect(Collectors.toList());
    }

    /**
     * Convert a ServiceType into Service.
     * @param serviceType
     * @param clientId
     * @return
     */
    public Service convert(ServiceType serviceType, ClientId clientId) {
        Service service = new Service();

        service.setId(convertId(serviceType, clientId));
        service.setServiceCode(FormatUtils.getServiceFullName(serviceType));
        service.setSslAuth(serviceType.getSslAuthentication());
        service.setTimeout(serviceType.getTimeout());
        service.setUrl(serviceType.getUrl());

        return service;
    }

    /**
     * Convert service and client information into encoded client-service-id,
     * e.g. <code>CS:ORG:Client:myService.v1</code>
     * @param serviceType
     * @param clientId
     * @return
     */
    public String convertId(ServiceType serviceType, ClientId clientId) {
        StringBuilder builder = new StringBuilder();
        builder.append(clientConverter.convertId(clientId));
        builder.append(ENCODED_ID_SEPARATOR);
        builder.append(FormatUtils.getServiceFullName(serviceType));
        return builder.toString();
    }

    /**
     * parse ClientId from encoded service id
     * @param encodedId
     * @return ClientId
     * @throws BadRequestException if encoded id could not be decoded
     */
    public ClientId parseClientId(String encodedId) {
        validateEncodedString(encodedId);
        String encodedClientId = encodedId.substring(0, encodedId.lastIndexOf(
                ENCODED_ID_SEPARATOR));
        return clientConverter.convertId(encodedClientId);
    }

    /**
     * parse service code including version from encoded service id
     * @param encodedId
     * @return ClientId
     * @throws BadRequestException if encoded id could not be decoded
     */
    public String parseFullServiceCode(String encodedId) {
        validateEncodedString(encodedId);
        List<String> parts = new ArrayList<>(
                Arrays.asList(encodedId.split(
                        String.valueOf(ENCODED_ID_SEPARATOR))));
        return parts.get(parts.size() - 1);
    }

    private void validateEncodedString(String encodedId) {
        int separators = FormatUtils.countOccurences(encodedId,
                ENCODED_ID_SEPARATOR);
        if (separators != FULL_SERVICE_CODE_INDEX) {
            throw new BadRequestException("Invalid service id " + encodedId);
        }
    }
}
