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
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import com.google.common.collect.Streams;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.securityserver.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.securityserver.restapi.dto.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientTypeDto;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * converter for ServiceClient and related objects
 */
@Component
@RequiredArgsConstructor
public class ServiceClientConverter {

    private final GlobalConfProvider globalConfProvider;
    private final GlobalGroupConverter globalGroupConverter;
    private final ServiceClientSortingComparator serviceClientSortingComparator;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Convert ServiceClient to ServiceClientDto.
     * @param serviceClient
     * @return {@link ServiceClientDto}
     */
    public ServiceClientDto convertServiceClientDto(ServiceClient serviceClient) {
        ServiceClientDto serviceClientDto = new ServiceClientDto();
        serviceClientDto.setRightsGivenAt(serviceClient.getRightsGiven());

        XRoadId subjectId = serviceClient.getSubjectId();

        switch (subjectId.getObjectType()) {
            case SUBSYSTEM:
                ClientId serviceClientId = (ClientId) subjectId;
                serviceClientDto.setName(globalConfProvider.getMemberName(serviceClientId));
                serviceClientDto.setId(clientIdConverter.convertId(serviceClientId));
                serviceClientDto.setServiceClientType(ServiceClientTypeDto.SUBSYSTEM);
                break;
            case GLOBALGROUP:
                GlobalGroupId globalGroupId = (GlobalGroupId) subjectId;
                serviceClientDto.setName(serviceClient.getGlobalGroupDescription());
                serviceClientDto.setId(globalGroupConverter.convertId(globalGroupId));
                serviceClientDto.setServiceClientType(ServiceClientTypeDto.GLOBALGROUP);
                break;
            case LOCALGROUP:
                serviceClientDto.setId(serviceClient.getLocalGroupId());
                serviceClientDto.setLocalGroupCode(serviceClient.getLocalGroupCode());
                serviceClientDto.setName(serviceClient.getLocalGroupDescription());
                serviceClientDto.setServiceClientType(ServiceClientTypeDto.LOCALGROUP);
                break;
            default:
                break;
        }

        return serviceClientDto;
    }

    /**
     * Convert a group of ServiceClients to ServiceClientsDtos
     * @param serviceClients
     * @return Set<ServiceClientDto>
     */
    public Set<ServiceClientDto> convertServiceClientDtos(Iterable<ServiceClient> serviceClients) {
        return Streams.stream(serviceClients)
                .map(this::convertServiceClientDto)
                .sorted(serviceClientSortingComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Convert ServiceClientDto into XroadId (based on serviceClientType, id and localGroupCode)
     * @param serviceClientDto
     * @return
     */
    public XRoadId convertId(ServiceClientDto serviceClientDto) {
        XRoadObjectType serviceClientType = ServiceClientTypeMapping.map(serviceClientDto.getServiceClientType()).get();
        String encodedId = serviceClientDto.getId();
        return switch (serviceClientType) {
            case SUBSYSTEM -> {
                if (!clientIdConverter.isEncodedSubsystemId(encodedId)) {
                    throw new BadRequestException("Invalid subsystem id " + encodedId);
                }
                yield clientIdConverter.convertId(encodedId);
            }
            case GLOBALGROUP -> globalGroupConverter.convertId(encodedId);
            case LOCALGROUP -> LocalGroupId.Conf.create(serviceClientDto.getLocalGroupCode());
            default -> throw new BadRequestException("Invalid service client type");
        };
    }

    /**
     * Convert ServiceClientDto into XroadIds (based on serviceClientType, id and localGroupCode)
     * @param serviceClientDtos
     * @return
     */
    public List<XRoadId> convertIds(Iterable<ServiceClientDto> serviceClientDtos) {
        return Streams.stream(serviceClientDtos)
                .map(this::convertId)
                .collect(Collectors.toList());
    }

}
