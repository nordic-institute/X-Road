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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.restapi.converter.ServiceClientConverter;
import org.niis.xroad.restapi.converter.ServiceConverter;
import org.niis.xroad.restapi.converter.SubjectConverter;
import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.restapi.openapi.model.Subject;
import org.niis.xroad.restapi.openapi.model.SubjectType;
import org.niis.xroad.restapi.openapi.model.Subjects;
import org.niis.xroad.restapi.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * services api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class ServicesApiController implements ServicesApi {

    private final ServiceConverter serviceConverter;
    private final ServiceClientConverter serviceClientConverter;
    private final ServiceService serviceService;
    private final SubjectConverter subjectConverter;

    @Autowired
    public ServicesApiController(ServiceConverter serviceConverter, ServiceClientConverter serviceClientConverter,
            ServiceService serviceService, SubjectConverter subjectConverter) {
        this.serviceConverter = serviceConverter;
        this.serviceClientConverter = serviceClientConverter;
        this.serviceService = serviceService;
        this.subjectConverter = subjectConverter;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<Service> getService(String id) {
        ServiceType serviceType = getServiceType(id);
        ClientId clientId = serviceConverter.parseClientId(id);
        Service service = serviceConverter.convert(serviceType, clientId);
        return new ResponseEntity<>(service, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_SERVICE_PARAMS')")
    public ResponseEntity<Service> updateService(String id, ServiceUpdate serviceUpdate) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        Service service = serviceUpdate.getService();
        Service updatedService = serviceConverter.convert(
                serviceService.updateService(clientId, fullServiceCode, service.getUrl(), serviceUpdate.getUrlAll(),
                        service.getTimeout(), serviceUpdate.getTimeoutAll(),
                        Boolean.TRUE.equals(service.getSslAuth()), serviceUpdate.getSslAuthAll()),
                clientId);
        return new ResponseEntity<>(updatedService, HttpStatus.OK);
    }

    private ServiceType getServiceType(String id) {
        ClientId clientId = serviceConverter.parseClientId(id);
        String fullServiceCode = serviceConverter.parseFullServiceCode(id);
        return serviceService.getService(clientId, fullServiceCode);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SERVICE_ACL')")
    public ResponseEntity<List<ServiceClient>> getServiceAccessRights(String encodedServiceId) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        List<AccessRightHolderDto> accessRightHolderDtos =
                serviceService.getAccessRightHoldersByService(clientId, fullServiceCode);
        List<ServiceClient> serviceClients = serviceClientConverter.convertAccessRightHolderDtos(accessRightHolderDtos);
        return new ResponseEntity<>(serviceClients, HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('EDIT_SERVICE_ACL')")
    @Override
    public ResponseEntity<Void> deleteServiceAccessRight(String encodedServiceId, Subjects subjects) {
        ClientId clientId = serviceConverter.parseClientId(encodedServiceId);
        String fullServiceCode = serviceConverter.parseFullServiceCode(encodedServiceId);
        // LocalGroups with numeric ids (PK)
        Set<Long> localGroupIds = subjects.getItems()
                .stream()
                .filter(hasNumericIdAndIsLocalGroup)
                .map(subject -> Long.parseLong(subject.getId()))
                .collect(Collectors.toSet());
        subjects.getItems().removeIf(hasNumericIdAndIsLocalGroup);
        // Converter handles other errors such as unknown types and ids
        List<XRoadId> xRoadIds = subjectConverter.convert(subjects.getItems());
        serviceService.deleteServiceAccessRights(clientId, fullServiceCode, new HashSet<>(xRoadIds), localGroupIds);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private Predicate<Subject> hasNumericIdAndIsLocalGroup = subject -> {
        boolean hasNumericId = StringUtils.isNumeric(subject.getId());
        boolean isLocalGroup = subject.getSubjectType() == SubjectType.LOCALGROUP;
        if (!hasNumericId && isLocalGroup) {
            throw new BadRequestException("LocalGroup id is not numeric: " + subject.getId());
        }
        return hasNumericId && isLocalGroup;
    };
}
