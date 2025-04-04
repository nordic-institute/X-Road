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

import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDto;
import org.niis.xroad.securityserver.restapi.util.EndpointHelper;
import org.niis.xroad.securityserver.restapi.util.ServiceFormatter;
import org.niis.xroad.serverconf.model.Service;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.identifier.XRoadId.ENCODED_ID_SEPARATOR;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

/**
 * Convert Service related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class ServiceConverter {

    /**
     * Encoded service id consists of <encoded client id>:<full service code>
     * Separator ':' is Converters.ENCODED_ID_SEPARATOR
     */
    public static final int FULL_SERVICE_CODE_INDEX = 4;

    private final EndpointConverter endpointConverter;
    private final EndpointHelper endpointHelper;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Converts a group of Services to a list of Services and sorts the list alphabetically by fullServiceCode.
     * This expects that serviceType.serviceDescription.client.endpoints have been fetched
     * @param services
     * @return
     */
    public Set<ServiceDto> convertServices(Iterable<Service> services, ClientId clientId) {
        return Streams.stream(services)
                .map(service -> convert(service, clientId))
                .collect(Collectors.toSet());
    }

    /**
     * Convert a Service into Service.
     * This expects that serviceType.serviceDescription.client.endpoints has been fetched
     * @param service service
     * @param clientId clientId
     * @return ServiceDto
     */
    public ServiceDto convert(Service service, ClientId clientId) {
        ServiceDto serviceDto = new ServiceDto();

        serviceDto.setId(convertId(service, clientId));
        serviceDto.setServiceCode(service.getServiceCode());
        serviceDto.setFullServiceCode(
                ServiceFormatter.getServiceFullName(service.getServiceCode(), service.getServiceVersion()));
        if (service.getUrl().startsWith(FormatUtils.HTTP_PROTOCOL)) {
            serviceDto.setSslAuth(false);
        } else {
            serviceDto.setSslAuth(defaultIfNull(service.getSslAuthentication(), true));
        }
        serviceDto.setTimeout(service.getTimeout());
        serviceDto.setUrl(service.getUrl());
        serviceDto.setTitle(service.getTitle());
        serviceDto.setEndpoints(this.endpointConverter.convert(service.getEndpoints()));

        return serviceDto;
    }

    /**
     * Convert service and client information into encoded client-service-id,
     * e.g. <code>CS:ORG:Client:myService.v1</code>
     * @param service
     * @param clientId
     * @return
     */
    public String convertId(Service service, ClientId clientId) {
        StringBuilder builder = new StringBuilder();
        builder.append(clientIdConverter.convertId(clientId));
        builder.append(ENCODED_ID_SEPARATOR);
        builder.append(ServiceFormatter.getServiceFullName(service.getServiceCode(), service.getServiceVersion()));
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
        return clientIdConverter.convertId(encodedClientId);
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
