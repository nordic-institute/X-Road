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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.EndpointRepository;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.niis.xroad.serverconf.impl.mapper.EndpointMapper;
import org.niis.xroad.serverconf.model.Endpoint;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ILLEGAL_GENERATED_ENDPOINT_REMOVE;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ILLEGAL_GENERATED_ENDPOINT_UPDATE;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_METHOD;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_PATH;

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
     * @param id endpoint id
     * @return Endpoint
     * @throws EndpointNotFoundException endpoint not found with given id
     */
    public Endpoint getEndpoint(Long id) throws EndpointNotFoundException {
        return EndpointMapper.get().toTarget(getEndpointEntity(id));
    }

    EndpointEntity getEndpointEntity(Long id) throws EndpointNotFoundException {
        EndpointEntity endpoint = endpointRepository.getEndpoint(id);
        if (endpoint == null) {
            throw new EndpointNotFoundException(id);
        }
        return endpoint;
    }

    /**
     * Delete endpoint
     * @param id endpoint id
     * @throws EndpointNotFoundException               endpoint not found with given id
     * @throws ClientNotFoundException                 client for the endpoint not found
     * @throws IllegalGeneratedEndpointRemoveException deleting generated endpoint is not allowed
     */
    public void deleteEndpoint(Long id) throws EndpointNotFoundException, ClientNotFoundException,
                                               IllegalGeneratedEndpointRemoveException {

        EndpointEntity endpoint = getEndpointEntity(id);

        if (endpoint.getId().equals(id) && endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointRemoveException(id.toString());
        }

        ClientEntity clientEntity = clientRepository.getClientByEndpointId(id);
        clientEntity.getAccessRights().removeIf(acl -> acl.getEndpoint().getId().equals(id));
        clientEntity.getEndpoints().removeIf(ep -> ep.getId().equals(id));
    }

    /**
     * Update endpoint details
     * @param id     for the endpoint to be updated
     * @param method new value for method
     * @param path   new value for path
     * @return Endpoint
     * @throws EndpointNotFoundException               endpoint not found with given id
     * @throws IllegalGeneratedEndpointUpdateException trying to update that is generated automatically
     * @throws IllegalArgumentException                passing illegal combination of parameters
     * @throws ClientNotFoundException                 client for the endpoint not found
     * @throws EndpointAlreadyExistsException          equivalent endpoint already exists for this client
     */
    public Endpoint updateEndpoint(Long id, String method, String path)
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

        ClientEntity clientEntity = clientRepository.getClientByEndpointId(id);
        Optional<EndpointEntity> optionalEndpointEntity = clientEntity.getEndpoints().stream()
                .filter(e -> e.getId().equals(id)).findFirst();
        if (optionalEndpointEntity.isEmpty()) {
            throw new EndpointNotFoundException(id);
        }

        EndpointEntity endpoint = optionalEndpointEntity.get();

        if (endpoint.isGenerated()) {
            throw new IllegalGeneratedEndpointUpdateException(id.toString());
        }

        if (path != null) {
            endpoint.setPath(path);
        }

        if (method != null) {
            endpoint.setMethod(method);
        }

        if (clientEntity.getEndpoints().stream().filter(e -> !Objects.equals(e.getId(), id)).anyMatch(e -> e.isEquivalent(endpoint))) {
            throw new EndpointAlreadyExistsException("Endpoint with equivalent service code, method and path already "
                    + "exists for this client");
        }

        return EndpointMapper.get().toTarget(endpoint);
    }

    /**
     * Get matching base-endpoint for the given client and service.
     * @param serviceEntity serviceEntity
     * @throws EndpointNotFoundException if base endpoint matching given parameters did not exist
     */
    EndpointEntity getServiceBaseEndpointEntity(ServiceEntity serviceEntity)
            throws EndpointNotFoundException {
        ClientEntity clientEntity = serviceEntity.getServiceDescription().getClient();
        String serviceCode = serviceEntity.getServiceCode();
        return getServiceBaseEndpointEntity(clientEntity, serviceCode);
    }

    /**
     * Get matching base-endpoint for the given client and service code.
     * @param clientEntity clientEntity
     * @param serviceCode  serviceCode
     * @throws EndpointNotFoundException if base endpoint matching given parameters did not exist
     */
    EndpointEntity getServiceBaseEndpointEntity(ClientEntity clientEntity, String serviceCode) throws EndpointNotFoundException {
        return clientEntity.getEndpoints().stream()
                .filter(endpointEntity -> endpointEntity.getServiceCode().equals(serviceCode)
                        && endpointEntity.getMethod().equals(ANY_METHOD)
                        && endpointEntity.getPath().equals(ANY_PATH))
                .findFirst()
                .orElseThrow(() -> new EndpointNotFoundException("Base endpoint not found for client "
                        + clientEntity.getIdentifier() + " and servicecode " + serviceCode, true));
    }

    /**
     * Get matching base-endpoints for the given client and service codes.
     * @param clientEntity clientEntity
     * @param serviceCodes serviceCodes
     * @throws EndpointNotFoundException if any base endpoint matching given parameters did not exist
     */
    List<EndpointEntity> getServiceBaseEndpointEntities(ClientEntity clientEntity, Set<String> serviceCodes)
            throws EndpointNotFoundException {
        List<EndpointEntity> baseEndpoints = new ArrayList<>();
        for (String serviceCode : serviceCodes) {
            baseEndpoints.add(getServiceBaseEndpointEntity(clientEntity, serviceCode));
        }
        return baseEndpoints;
    }

    /**
     * Get base endpoint for given client and full service code
     * @throws ServiceNotFoundException if no match was found
     */
    EndpointEntity getBaseEndpointEntity(ClientEntity clientEntity, String fullServiceCode)
            throws ServiceNotFoundException {
        ServiceEntity serviceEntity = serviceService.getServiceEntityFromClient(clientEntity, fullServiceCode);
        try {
            return getServiceBaseEndpointEntity(serviceEntity);
        } catch (EndpointNotFoundException e) {
            throw new ServiceNotFoundException(e);
        }
    }


    /**
     * Get all endpoints (base and others) for the given client and service code.
     * @param clientEntity clientEntity
     * @param serviceCodes serviceCodes
     */
    List<EndpointEntity> getServiceEndpointEntities(ClientEntity clientEntity, Set<String> serviceCodes) {
        return clientEntity.getEndpoints().stream()
                .filter(endpointEntity -> serviceCodes.contains(endpointEntity.getServiceCode()))
                .collect(Collectors.toList());
    }

    public static class IllegalGeneratedEndpointUpdateException extends BadRequestException {
        private static final String MESSAGE = "Updating generated endpoint is not allowed: %s";

        public IllegalGeneratedEndpointUpdateException(String id) {
            super(String.format(MESSAGE, id), ILLEGAL_GENERATED_ENDPOINT_UPDATE.build(id));
        }
    }

    public static class IllegalGeneratedEndpointRemoveException extends BadRequestException {
        private static final String MESSAGE = "Removing generated endpoint is not allowed: %s";

        public IllegalGeneratedEndpointRemoveException(String id) {
            super(String.format(MESSAGE, id), ILLEGAL_GENERATED_ENDPOINT_REMOVE.build(id));
        }
    }

}
