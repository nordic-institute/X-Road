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
package org.niis.xroad.securityserver.restapi.controller;

import ee.ria.xroad.common.identifier.XRoadId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientIdentifierConverter;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientIdentifierDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClient;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClients;
import org.niis.xroad.securityserver.restapi.service.ServiceClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceClientService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Controller layer helper for working with service client identifier parameters, and
 * translating them into XRoadId objects
 */
@Component
@RequiredArgsConstructor
public class ServiceClientHelper {

    private final ServiceClientIdentifierConverter serviceClientIdentifierConverter;
    private final ServiceClientService serviceClientService;

    /**
     * Transform ServiceClients object into a set of XRoadIds.
     * Capable of handling local group ids.
     *
     * @throws ServiceClientIdentifierConverter.BadServiceClientIdentifierException if any encoded service client id
     * was badly formatted
     * @throws ServiceClientNotFoundException if any local group with given ID (PK) does not exist
     */
    public Set<XRoadId.Conf> processServiceClientXRoadIds(ServiceClients serviceClients)
            throws ServiceClientNotFoundException,
            ServiceClientIdentifierConverter.BadServiceClientIdentifierException {
        Set<XRoadId.Conf> ids = new HashSet<>();
        for (ServiceClient serviceClient : serviceClients.getItems()) {
            ids.add(processServiceClientXRoadId(serviceClient.getId()));
        }
        return ids;
    }

    /**
     * Transform single encoded service client id into XRoadId.
     * Capable of handling local group ids.
     *
     * @throws ServiceClientIdentifierConverter.BadServiceClientIdentifierException if encoded service client id
     * was badly formatted
     * @throws ServiceClientNotFoundException if a local group with given ID (PK) does not exist
     */
    public XRoadId.Conf processServiceClientXRoadId(String encodedServiceClientId)
            throws ServiceClientIdentifierConverter.BadServiceClientIdentifierException,
            ServiceClientNotFoundException {
        ServiceClientIdentifierDto dto = serviceClientIdentifierConverter.convertId(encodedServiceClientId);
        return serviceClientService.convertServiceClientIdentifierDtoToXroadId(dto);
    }

    public static final String ERROR_INVALID_SERVICE_CLIENT_ID = "invalid_service_client_id";
    private static final String INVALID_SERVICE_CLIENT_ID = "Invalid service client id: ";

    /**
     * Take ServiceClientIdentifierConverter.BadServiceClientIdentifierException and wrap it in
     * BadRequestException.
     * Error code = invalid_service_client_id
     */
    public BadRequestException wrapInBadRequestException(
            ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
        return new BadRequestException(INVALID_SERVICE_CLIENT_ID + e.getServiceClientIdentifier(),
                new ErrorDeviation(ERROR_INVALID_SERVICE_CLIENT_ID));
    }
}
