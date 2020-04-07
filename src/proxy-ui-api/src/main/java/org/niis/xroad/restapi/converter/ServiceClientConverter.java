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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import com.google.common.collect.Streams;
import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceClientType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * converter for ServiceClient and related objects
 */
@Component
public class ServiceClientConverter {

    private final GlobalConfFacade globalConfFacade;
    private final ClientConverter clientConverter;
    private final GlobalGroupConverter globalGroupConverter;

    @Autowired
    public ServiceClientConverter(GlobalConfFacade globalConfFacade, ClientConverter clientConverter,
            GlobalGroupConverter globalGroupConverter) {
        this.globalConfFacade = globalConfFacade;
        this.clientConverter = clientConverter;
        this.globalGroupConverter = globalGroupConverter;
    }

    /**
     * Convert ServiceClientDto to ServiceClient. {@link ServiceClient#accessRights} will be set to null because
     * only the access right holders (clients or groups) are needed
     * @param accessRightHolderDto
     * @return {@link ServiceClient}
     */
    public ServiceClient convertAccessRightHolderDto(AccessRightHolderDto accessRightHolderDto) {
        ServiceClient serviceClient = new ServiceClient();
        serviceClient.setRightsGivenAt(accessRightHolderDto.getRightsGiven());

        XRoadId subjectId = accessRightHolderDto.getSubjectId();

        switch (subjectId.getObjectType()) {
            case SUBSYSTEM:
                ClientId serviceClientId = (ClientId) subjectId;
                serviceClient.setMemberNameGroupDescription(globalConfFacade.getMemberName(serviceClientId));
                serviceClient.setId(clientConverter.convertId(serviceClientId));
                serviceClient.setServiceClientType(ServiceClientType.SUBSYSTEM);
                break;
            case GLOBALGROUP:
                GlobalGroupId globalGroupId = (GlobalGroupId) subjectId;
                serviceClient.setMemberNameGroupDescription(globalConfFacade.getGlobalGroupDescription(globalGroupId));
                serviceClient.setId(globalGroupConverter.convertId(globalGroupId));
                serviceClient.setServiceClientType(ServiceClientType.GLOBALGROUP);
                break;
            case LOCALGROUP:
                serviceClient.setId(accessRightHolderDto.getLocalGroupId());
                serviceClient.setLocalGroupCode(accessRightHolderDto.getLocalGroupCode());
                serviceClient.setMemberNameGroupDescription(accessRightHolderDto.getLocalGroupDescription());
                serviceClient.setServiceClientType(ServiceClientType.LOCALGROUP);
                break;
            default:
                break;
        }

        return serviceClient;
    }

    /**
     * Convert a group of ServiceClientDtos to ServiceClients
     * @param accessRightHolderDtos
     * @return
     */
    public List<ServiceClient> convertAccessRightHolderDtos(Iterable<AccessRightHolderDto> accessRightHolderDtos) {
        return Streams.stream(accessRightHolderDtos)
                .map(this::convertAccessRightHolderDto)
                .collect(Collectors.toList());
    }
}
