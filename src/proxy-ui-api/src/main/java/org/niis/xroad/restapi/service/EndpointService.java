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

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;

import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.EndpointPathAndMethod;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.EndpointRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.niis.xroad.restapi.service.SecurityHelper.verifyAuthority;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class EndpointService {

    private final ClientRepository clientRepository;
    private final EndpointRepository endpointRepository;

    @Autowired
    public EndpointService(ClientRepository clientRepository, EndpointRepository endpointRepository) {
        this.clientRepository = clientRepository;
        this.endpointRepository = endpointRepository;
    }

    /**
     * Get endpoint by endpoint id
     *
     * @param id                            endpoint id
     * @return
     * @throws EndpointNotFoundException    endpoint not found with given id
     */
    public EndpointType getEndpoint(Long id) throws EndpointNotFoundException {
        verifyAuthority("VIEW_ENDPOINT");
        EndpointType endpoint = endpointRepository.getEndpoint(id);
        if (endpoint == null) {
            throw new EndpointNotFoundException(id.toString());
        }
        return endpoint;
    }

    /**
     * Delete endpoint
     *
     * @param id                                        endpoint id
     * @throws EndpointNotFoundException                endpoint not found with given id
     * @throws ClientNotFoundException                  client for the endpoint not found
     * @throws IllegalGeneratedEndpointRemoveException  deleting generated endpoint is not allowed
     */
    public void deleteEndpoint(Long id) throws EndpointNotFoundException, ClientNotFoundException,
            IllegalGeneratedEndpointRemoveException {
        verifyAuthority("DELETE_ENDPOINT");

        EndpointType endpoint = endpointRepository.getEndpoint(id);

        if (endpoint == null) {
            throw new EndpointService.EndpointNotFoundException(id.toString());
        }

        if (endpoint.getId().equals(id) && endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointRemoveException(id.toString());
        }

        ClientType clientType = clientRepository.getClientByEndpointId(id);

        if (clientType == null) {
            throw new ClientNotFoundException("Client not found for endpoint with id: " + id.toString());
        }
        clientType.getAcl().removeIf(acl -> acl.getEndpoint().getId().equals(id));
        clientType.getEndpoint().removeIf(ep -> ep.getId().equals(id));
        clientRepository.saveOrUpdate(clientType);
    }

    /**
     * Update endpoint details
     *
     * @param id for the endpoint to be updated
     * @param method new value for method
     * @param path new value for path
     * @return
     * @throws EndpointNotFoundException                endpoint not found with given id
     * @throws IllegalGeneratedEndpointUpdateException  trying to update that is generated automatically
     * @throws IllegalArgumentException                 passing illegal combination of parameters
     */
    public EndpointType updateEndpoint(Long id, EndpointPathAndMethod.MethodEnum method, String path)
            throws EndpointNotFoundException, IllegalGeneratedEndpointUpdateException {
        verifyAuthority("EDIT_OPENAPI3_ENDPOINT");

        if ("".equals(path)) {
            throw new IllegalArgumentException("Path can't be empty string when updating an endpoint: "
                + id.toString());
        }

        if (method == null && path == null) {
            throw new IllegalArgumentException("Method and path can't both be null when updating an endpoint: "
                + id.toString());
        }

        EndpointType endpoint = endpointRepository.getEndpoint(id);
        if (endpoint == null) {
            throw new EndpointService.EndpointNotFoundException(id.toString());
        }

        if (endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointUpdateException(id.toString());
        }

        if (path != null) {
            endpoint.setPath(path);
        }

        if (method != null) {
            endpoint.setMethod(method.toString());
        }

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

    public static class IllegalGeneratedEndpointRemoveException extends ServiceException {
        public static final String ILLEGAL_GENERATED_ENDPOINT_REMOVE = "illegal_generated_endpoint_remove";

        private static final String MESSAGE = "Removing generated endpoint is not allowed: %s";

        public IllegalGeneratedEndpointRemoveException(String id) {
            super(String.format(MESSAGE, id), new ErrorDeviation(ILLEGAL_GENERATED_ENDPOINT_REMOVE, id));
        }
    }



}
