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
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.securityserver.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientType;
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

    private final GlobalConfFacade globalConfFacade;
    private final GlobalGroupConverter globalGroupConverter;
    private final ServiceClientSortingComparator serviceClientSortingComparator;

    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Convert ServiceClientDto to ServiceClient.
     * @param serviceClientDto
     * @return {@link ServiceClient}
     */
    public ServiceClient convertServiceClientDto(ServiceClientDto serviceClientDto) {
        ServiceClient serviceClient = new ServiceClient();
        serviceClient.setRightsGivenAt(serviceClientDto.getRightsGiven());

        XRoadId subjectId = serviceClientDto.getSubjectId();

        switch (subjectId.getObjectType()) {
            case SUBSYSTEM:
                ClientId serviceClientId = (ClientId) subjectId;
                serviceClient.setName(globalConfFacade.getMemberName(serviceClientId));
                serviceClient.setId(clientIdConverter.convertId(serviceClientId));
                serviceClient.setServiceClientType(ServiceClientType.SUBSYSTEM);
                break;
            case GLOBALGROUP:
                GlobalGroupId globalGroupId = (GlobalGroupId) subjectId;
                serviceClient.setName(serviceClientDto.getGlobalGroupDescription());
                serviceClient.setId(globalGroupConverter.convertId(globalGroupId));
                serviceClient.setServiceClientType(ServiceClientType.GLOBALGROUP);
                break;
            case LOCALGROUP:
                serviceClient.setId(serviceClientDto.getLocalGroupId());
                serviceClient.setLocalGroupCode(serviceClientDto.getLocalGroupCode());
                serviceClient.setName(serviceClientDto.getLocalGroupDescription());
                serviceClient.setServiceClientType(ServiceClientType.LOCALGROUP);
                break;
            default:
                break;
        }

        return serviceClient;
    }

    /**
     * Convert a group of ServiceClientDtos to ServiceClients
     * @param serviceClientDtos
     * @return
     */
    public Set<ServiceClient> convertServiceClientDtos(Iterable<ServiceClientDto> serviceClientDtos) {
        return Streams.stream(serviceClientDtos)
                .map(this::convertServiceClientDto)
                .sorted(serviceClientSortingComparator)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Convert ServiceClient into XroadId (based on serviceClientType, id and localGroupCode)
     * @param serviceClient
     * @return
     */
    public XRoadId convertId(ServiceClient serviceClient) {
        XRoadObjectType serviceClientType = ServiceClientTypeMapping.map(serviceClient.getServiceClientType()).get();
        String encodedId = serviceClient.getId();
        XRoadId xRoadId;
        switch (serviceClientType) {
            case SUBSYSTEM:
                if (!clientIdConverter.isEncodedSubsystemId(encodedId)) {
                    throw new BadRequestException("Invalid subsystem id " + encodedId);
                }
                xRoadId = clientIdConverter.convertId(encodedId);
                break;
            case GLOBALGROUP:
                xRoadId = globalGroupConverter.convertId(encodedId);
                break;
            case LOCALGROUP:
                xRoadId = LocalGroupId.Conf.create(serviceClient.getLocalGroupCode());
                break;
            default:
                throw new BadRequestException("Invalid service client type");
        }
        return xRoadId;
    }

    /**
     * Convert ServiceClients into XroadIds (based on serviceClientType, id and localGroupCode)
     * @param serviceClients
     * @return
     */
    public List<XRoadId> convertIds(Iterable<ServiceClient> serviceClients) {
        return Streams.stream(serviceClients)
                .map(this::convertId)
                .collect(Collectors.toList());
    }

}
