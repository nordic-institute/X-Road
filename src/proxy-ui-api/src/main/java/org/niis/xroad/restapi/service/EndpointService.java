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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.EndpointType;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.Endpoint;
import org.niis.xroad.restapi.repository.EndpointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static org.niis.xroad.restapi.service.SecurityHelper.verifyAuthority;

@Service
public class EndpointService {

    private final EndpointRepository endpointRepository;

    @Autowired
    public EndpointService(EndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
    }

    public void deleteEndpoint(String id) throws EndpointNotFoundException {
        verifyAuthority("DELETE_ENDPOINT");
        endpointRepository.delete(id);
    }

    public EndpointType updateEndpoint(String id, Endpoint endpointUpdate)
            throws EndpointNotFoundException, IllegalGeneratedEndpointUpdateException {
        verifyAuthority("EDIT_OPENAPI3_ENDPOINT");

        EndpointType endpoint = endpointRepository.getEndpoint(id);
        if (endpoint == null) {
            throw new EndpointService.EndpointNotFoundException(id);
        }

        if (endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointUpdateException(id);
        }

        endpoint.setServiceCode(endpointUpdate.getServiceCode());
        endpoint.setMethod(endpointUpdate.getMethod());
        endpoint.setPath(endpointUpdate.getPath());

        endpointRepository.saveOrUpdate(endpoint);

        return endpoint;
    }


    public static class EndpointNotFoundException extends NotFoundException {
        public static final String ERROR_ENDPOINT_NOT_FOUND = "endpoint_not_found";
        private static final String MESSAGE = "Endpoint not found with id: %s";

        public EndpointNotFoundException(String id) {
            super(String.format(MESSAGE, id), new ErrorDeviation(ERROR_ENDPOINT_NOT_FOUND, id));
        }
    }

    public static class IllegalGeneratedEndpointUpdateException extends ServiceException {
        public static final String ILLEGAL_GENERATED_ENDPOINT_UPDATE = "illegal_generated_endpoint_update";

        private static final String MESSAGE = "Updating generated endpoint is not allowed: %s";

        public IllegalGeneratedEndpointUpdateException(String id) {
            super(String.format(MESSAGE, id), new ErrorDeviation(ILLEGAL_GENERATED_ENDPOINT_UPDATE, id));
        }

    }

}
