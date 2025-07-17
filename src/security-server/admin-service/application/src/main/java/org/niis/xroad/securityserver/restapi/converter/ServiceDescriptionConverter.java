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

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionDto;
import org.niis.xroad.serverconf.model.ServiceDescription;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Convert ServiceDescriptionDto related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class ServiceDescriptionConverter {

    private final ServiceConverter serviceConverter;

    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Converts a group of ServiceDescription to a list of ServiceDescriptions
     * Does a deep conversion, converts ServiceDescription.ServiceTypes.
     * This expects that serviceDescription.client.endpoints have been fetched
     * @param serviceDescriptions serviceDescriptions
     * @return Set<ServiceDescriptionDto>
     */
    public Set<ServiceDescriptionDto> convert(Iterable<ServiceDescription> serviceDescriptions) {
        return Streams.stream(serviceDescriptions)
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    /**
     * Convert a ServiceDescription into ServiceDescriptionDto.
     * Does a deep conversion, converts ServiceDescription.Services.
     * This expects that serviceDescription.client.endpoints have been fetched
     * @param serviceDescription serviceDescription
     * @return ServiceDescriptionDto
     */
    public ServiceDescriptionDto convert(ServiceDescription serviceDescription) {
        ServiceDescriptionDto serviceDescriptionDto = new ServiceDescriptionDto();

        serviceDescriptionDto.setId(String.valueOf(serviceDescription.getId()));
        serviceDescriptionDto.setClientId(clientIdConverter.convertId(
                serviceDescription.getClient().getIdentifier()));
        serviceDescriptionDto.setDisabled(serviceDescription.isDisabled());
        serviceDescriptionDto.setDisabledNotice(serviceDescription.getDisabledNotice());
        serviceDescriptionDto.setRefreshedAt(FormatUtils.fromDateToOffsetDateTime(
                serviceDescription.getRefreshedDate()));
        serviceDescriptionDto.setServices(serviceConverter.convertServices(serviceDescription.getServices(),
                serviceDescription.getClient().getIdentifier()));
        serviceDescriptionDto.setType(ServiceTypeMapping.map(serviceDescription.getType()));
        serviceDescriptionDto.setUrl(serviceDescription.getUrl());

        return serviceDescriptionDto;
    }
}
