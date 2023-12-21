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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.EndpointRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_BASE_ENDPOINT_NOT_FOUND;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ILLEGAL_GENERATED_ENDPOINT_REMOVE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_ILLEGAL_GENERATED_ENDPOINT_UPDATE;

@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class EndpointService {

    private final ClientRepository clientRepository;
    private final EndpointRepository endpointRepository;
    private final ServiceService serviceService;

    /**
     * Get endpoint by endpoint id
     *
     * @param id                            endpoint id
     * @return
     * @throws EndpointNotFoundException    endpoint not found with given id
     */
    public EndpointType getEndpoint(Long id) throws EndpointNotFoundException {
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

        EndpointType endpoint = getEndpoint(id);

        if (endpoint.getId().equals(id) && endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointRemoveException(id.toString());
        }

        ClientType clientType = clientRepository.getClientByEndpointId(id);
        clientType.getAcl().removeIf(acl -> acl.getEndpoint().getId().equals(id));
        clientType.getEndpoint().removeIf(ep -> ep.getId().equals(id));
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
     * @throws ClientNotFoundException                  client for the endpoint not found
     * @throws EndpointAlreadyExistsException           equivalent endpoint already exists for this client
     */
    public EndpointType updateEndpoint(Long id, String method, String path)
            throws EndpointNotFoundException, IllegalGeneratedEndpointUpdateException, ClientNotFoundException,
            EndpointAlreadyExistsException {

        if ("".equals(path)) {
            throw new IllegalArgumentException("Path can't be empty string when updating an endpoint: "
                + id.toString());
        }

        if (method == null && path == null) {
            throw new IllegalArgumentException("Method and path can't both be null when updating an endpoint: "
                + id.toString());
        }

        ClientType client = clientRepository.getClientByEndpointId(id);
        Optional<EndpointType> endpointType = client.getEndpoint().stream().filter(e -> e.getId() == id).findFirst();
        if (!endpointType.isPresent()) {
            throw new EndpointNotFoundException(id.toString());
        }

        EndpointType endpoint = endpointType.get();

        if (endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointUpdateException(id.toString());
        }

        if (path != null) {
            endpoint.setPath(path);
        }

        if (method != null) {
            endpoint.setMethod(method);
        }

        if (client.getEndpoint().stream().filter(e -> e.getId() != id).anyMatch(e -> e.isEquivalent(endpoint))) {
            throw new EndpointAlreadyExistsException("Endpoint with equivalent service code, method and path already "
                    + "exists for this client");
        }

        return endpoint;
    }

    /**
     * Get matching base-endpoint for the given client and service.
     *
     * @param serviceType
     * @throws EndpointNotFoundException if base endpoint matching given parameters did not exist
     */
    public EndpointType getServiceBaseEndpoint(ServiceType serviceType)
            throws EndpointNotFoundException {
        ClientType clientType = serviceType.getServiceDescription().getClient();
        String serviceCode = serviceType.getServiceCode();
        return getServiceBaseEndpoint(clientType, serviceCode);
    }

    /**
     * Get matching base-endpoint for the given client and service code.
     * @param clientType
     * @param serviceCode
     * @throws EndpointNotFoundException if base endpoint matching given parameters did not exist
     */
    public EndpointType getServiceBaseEndpoint(ClientType clientType, String serviceCode)
            throws EndpointNotFoundException {
        return clientType.getEndpoint().stream()
                .filter(endpointType -> endpointType.getServiceCode().equals(serviceCode)
                        && endpointType.getMethod().equals(EndpointType.ANY_METHOD)
                        && endpointType.getPath().equals(EndpointType.ANY_PATH))
                .findFirst()
                .orElseThrow(() -> new EndpointNotFoundException(
                        ERROR_BASE_ENDPOINT_NOT_FOUND, "Base endpoint not found for client "
                        + clientType.getIdentifier() + " and servicecode " + serviceCode));
    }

    /**
     * Get matching base-endpoints for the given client and service codes.
     * @param clientType
     * @param serviceCodes
     * @throws EndpointNotFoundException if any base endpoint matching given parameters did not exist
     */
    public List<EndpointType> getServiceBaseEndpoints(ClientType clientType, Set<String> serviceCodes)
            throws EndpointNotFoundException {
        List<EndpointType> baseEndpoints = new ArrayList<>();
        for (String serviceCode: serviceCodes) {
            baseEndpoints.add(getServiceBaseEndpoint(clientType, serviceCode));
        }
        return baseEndpoints;
    }

    /**
     * Get base endpoint for given client and full service code
     * @throws ServiceNotFoundException if no match was found
     */
    public EndpointType getBaseEndpointType(ClientType clientType, String fullServiceCode)
            throws ServiceNotFoundException {
        ServiceType serviceType = serviceService.getServiceFromClient(clientType, fullServiceCode);
        try {
            return getServiceBaseEndpoint(serviceType);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }
    }


    /**
     * Get all endpoints (base and others) for the given client and service code.
     * @param clientType
     * @param serviceCodes
     */
    public List<EndpointType> getServiceEndpoints(ClientType clientType, Set<String> serviceCodes) {
        return clientType.getEndpoint().stream()
                .filter(endpointType -> serviceCodes.contains(endpointType.getServiceCode()))
                .collect(Collectors.toList());
    }


    public static class IllegalGeneratedEndpointUpdateException extends ServiceException {
        private static final String MESSAGE = "Updating generated endpoint is not allowed: %s";

        public IllegalGeneratedEndpointUpdateException(String id) {
            super(String.format(MESSAGE, id), new ErrorDeviation(ERROR_ILLEGAL_GENERATED_ENDPOINT_UPDATE, id));
        }
    }

    public static class IllegalGeneratedEndpointRemoveException extends ServiceException {
        private static final String MESSAGE = "Removing generated endpoint is not allowed: %s";

        public IllegalGeneratedEndpointRemoveException(String id) {
            super(String.format(MESSAGE, id), new ErrorDeviation(ERROR_ILLEGAL_GENERATED_ENDPOINT_REMOVE, id));
        }
    }

}
