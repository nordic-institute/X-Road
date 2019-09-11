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
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * converter for ServiceClient and related objects
 */
@Component
public class ServiceClientConverter {

    private final GlobalConfService globalConfService;
    private final ClientConverter clientConverter;
    private final GroupConverter groupConverter;

    @Autowired
    public ServiceClientConverter(GlobalConfService globalConfService,
            ClientConverter clientConverter, GroupConverter groupConverter) {
        this.globalConfService = globalConfService;
        this.clientConverter = clientConverter;
        this.groupConverter = groupConverter;
    }

    /**
     * Convert ServiceClientDto to ServiceClient.
     * @param accessRightHolderDto
     * @return {@link ServiceClient}
     */
    public ServiceClient convertAccessRightHolderDto(AccessRightHolderDto accessRightHolderDto) {
        ServiceClient serviceClient = new ServiceClient();
        serviceClient.setRightsGivenAt(accessRightHolderDto.getRightsGiven());

        XRoadId subjectId = accessRightHolderDto.getSubjectId();

        switch (subjectId.getObjectType()) {
            case MEMBER:
            case SUBSYSTEM:
                ClientId serviceClientId = (ClientId) subjectId;
                serviceClient.setName(globalConfService.getMemberName(serviceClientId));
                serviceClient.setId(clientConverter.convertId(serviceClientId, true));
                break;
            case GLOBALGROUP:
                GlobalGroupId globalGroupId = (GlobalGroupId) subjectId;
                serviceClient.setName(globalConfService.getGlobalGroupDescription(globalGroupId));
                serviceClient.setId(groupConverter.convertId(globalGroupId, true));
                break;
            case LOCALGROUP:
                LocalGroupId localGroupId = (LocalGroupId) subjectId;
                serviceClient.setName(accessRightHolderDto.getLocalGroupDescMap().get(localGroupId.getGroupCode()));
                serviceClient.setId(groupConverter.convertId(localGroupId, true));
                break;
            default:
                break;
        }

        // we don't want to show the ACTUAL AccessRights - only the clients who possess them
        serviceClient.setAccessRights(null);
        return serviceClient;
    }

    /**
     * Convert a list of ServiceClientDtos to ServiceClients
     * @param accessRightHolderDtos
     * @return
     */
    public List<ServiceClient> convertAccessRightHolderDtos(List<AccessRightHolderDto> accessRightHolderDtos) {
        return accessRightHolderDtos
                .stream()
                .map(this::convertAccessRightHolderDto)
                .collect(Collectors.toList());
    }
}
