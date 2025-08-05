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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.common.core.exception.WarningDeviation;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.util.ServiceFormatter;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.niis.xroad.serverconf.impl.mapper.EndpointMapper;
import org.niis.xroad.serverconf.impl.mapper.ServiceMapper;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.niis.xroad.serverconf.model.Endpoint;
import org.niis.xroad.serverconf.model.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLHandshakeException;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_INTERNAL_SERVER_SSL_ERROR;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_INTERNAL_SERVER_SSL_HANDSHAKE_ERROR;

/**
 * service class for handling services
 */
@Slf4j
@org.springframework.stereotype.Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ServiceService {
    public static final String NOT_FOUND = " not found";
    private final ClientRepository clientRepository;
    private final UrlValidator urlValidator;
    private final AuditDataHelper auditDataHelper;
    private final InternalServerTestService internalServerTestService;
    private final ReservedServiceCodesProvider reservedServiceCodesProvider;

    /**
     * get Service by ClientId and service code that includes service version
     * see {@link ServiceFormatter#getServiceFullName(String, String)}.
     * Service has service.serviceDescription.client.endpoints lazy field fetched.
     *
     * @param clientId clientId
     * @param fullServiceCode fullServiceCode
     * @return Service
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     */
    public Service getService(ClientId clientId, String fullServiceCode) throws ClientNotFoundException, ServiceNotFoundException {
        return ServiceMapper.get().toTarget(getServiceEntity(clientId, fullServiceCode));
    }

    private ServiceEntity getServiceEntity(ClientId clientId, String fullServiceCode) throws ClientNotFoundException,
            ServiceNotFoundException {
        ClientEntity clientEntity = clientRepository.getClient(clientId);
        if (clientEntity == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + NOT_FOUND);
        }

        ServiceEntity serviceEntity = getServiceEntityFromClient(clientEntity, fullServiceCode);
        Hibernate.initialize(serviceEntity.getServiceDescription().getClient().getEndpoints());
        return serviceEntity;
    }

    /**
     * Get {@link ServiceEntity} from a {@link ClientEntity} by comparing the full service code (with version).
     *
     * @param clientEntity clientEntity
     * @param fullServiceCode fullServiceCode
     * @return ServiceEntity
     * @throws ServiceNotFoundException if service with fullServiceCode was not found
     */
    ServiceEntity getServiceEntityFromClient(ClientEntity clientEntity, String fullServiceCode)
            throws ServiceNotFoundException {
        Optional<ServiceEntity> foundService = clientEntity.getServiceDescriptions()
                .stream()
                .map(ServiceDescriptionEntity::getServices)
                .flatMap(List::stream)
                .filter(serviceEntity -> ServiceFormatter.getServiceFullName(serviceEntity).equals(fullServiceCode))
                .findFirst();
        return foundService.orElseThrow(() -> new ServiceNotFoundException("Service "
                + fullServiceCode + NOT_FOUND));
    }

    /**
     * update a Service. clientId and fullServiceCode identify the updated service.
     *
     * @param clientId clientId of the client associated with the service
     * @param fullServiceCode service code that includes service version
     * see {@link ServiceFormatter#getServiceFullName(String, String)}
     * @param url url
     * @param urlAll urlAll
     * @param timeout timeout
     * @param timeoutAll timeoutAll
     * @param sslAuth sslAuth
     * @param sslAuthAll sslAuthAll
     * @return Service
     * @throws InvalidUrlException if given url was not valid
     * @throws InvalidHttpsUrlException if given url does not use https and https is required
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws ClientNotFoundException if client with given id was not found
     * @throws UnhandledWarningsException if SSL auth is enabled and verification of the SSL connection between the
     * @throws org.niis.xroad.securityserver.restapi.service.ServiceDescriptionService.UrlAlreadyExistsException if url already
     *         exists for other rest service
     * Security Server and information system fails, and ignoreWarnings was false
     */
    public Service updateService(ClientId clientId, String fullServiceCode,
                                 String url, boolean urlAll, Integer timeout, boolean timeoutAll,
                                 boolean sslAuth, boolean sslAuthAll, boolean ignoreWarnings) throws InvalidUrlException,
            ServiceDescriptionService.UrlAlreadyExistsException,
            ServiceNotFoundException, ClientNotFoundException, UnhandledWarningsException, InvalidHttpsUrlException {

        auditDataHelper.put(clientId);

        if (!urlValidator.isValidUrl(url)) {
            throw new InvalidUrlException("URL is not valid: " + url);
        }
        if (sslAuth && !FormatUtils.isHttpsUrl(url)) {
            throw new InvalidHttpsUrlException("HTTPS must be used when SSL authentication is enabled");
        }

        ServiceEntity serviceEntity = getServiceEntity(clientId, fullServiceCode);

        if (sslAuth && !ignoreWarnings) {
            ClientEntity clientEntity = serviceEntity.getServiceDescription().getClient();
            try {
                internalServerTestService.testHttpsConnection(clientEntity.getCertificates(), url);
            } catch (SSLHandshakeException she) {
                throw new UnhandledWarningsException(
                        new WarningDeviation(WARNING_INTERNAL_SERVER_SSL_HANDSHAKE_ERROR, url));
            } catch (Exception e) {
                throw new UnhandledWarningsException(new WarningDeviation(WARNING_INTERNAL_SERVER_SSL_ERROR, url));
            }
        }

        ServiceDescriptionEntity serviceDescriptionEntity = serviceEntity.getServiceDescription();
        if (DescriptionType.REST.equals(serviceDescriptionEntity.getType())) {
            serviceDescriptionEntity.setUrl(url);
            checkDuplicateUrl(serviceDescriptionEntity);
        }

        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);

        serviceDescriptionEntity.getServices().forEach(service ->
                updateServiceFromSameDefinition(url, urlAll, timeout,
                    timeoutAll, sslAuth, sslAuthAll,
                    serviceEntity, service)
        );

        return ServiceMapper.get().toTarget(serviceEntity);
    }

    private void putServiceDescriptionUrlAndTypeToAudit(ServiceDescriptionEntity serviceDescriptionEntity) {
        if (serviceDescriptionEntity != null) {
            auditDataHelper.putServiceDescriptionUrlAndType(serviceDescriptionEntity.getUrl(), serviceDescriptionEntity.getType());
        }
    }

    private void checkDuplicateUrl(
            ServiceDescriptionEntity serviceDescription) throws ServiceDescriptionService.UrlAlreadyExistsException {
        boolean hasDuplicates = serviceDescription.getClient().getServiceDescriptions().stream()
                .anyMatch(other -> !serviceDescription.equals(other)
                        && serviceDescription.getUrl().equals(other.getUrl()));
        if (hasDuplicates) {
            throw new ServiceDescriptionService.UrlAlreadyExistsException(serviceDescription.getUrl());
        }
    }

    /**
     * @param targetService service we are actually updating
     * @param serviceFromSameDefinition another service from same service definition. Can be == targetService
     */
    private void updateServiceFromSameDefinition(String url, boolean urlAll, Integer timeout,
                                                 boolean timeoutAll, boolean sslAuth, boolean sslAuthAll,
                                                 ServiceEntity targetService, ServiceEntity serviceFromSameDefinition) {

        boolean serviceMatch = serviceFromSameDefinition == targetService;
        if (urlAll || serviceMatch) {
            serviceFromSameDefinition.setUrl(url);
        }
        if (timeoutAll || serviceMatch) {
            serviceFromSameDefinition.setTimeout(timeout);
        }
        if (sslAuthAll || serviceMatch) {
            if (FormatUtils.isHttpsUrl(serviceFromSameDefinition.getUrl())) {
                serviceFromSameDefinition.setSslAuthentication(sslAuth);
            } else {
                serviceFromSameDefinition.setSslAuthentication(null);
            }
        }
        if (urlAll || timeoutAll || sslAuthAll || serviceMatch) {
            // new audit log data item
            HashMap<RestApiAuditProperty, Object> serviceAuditData = new LinkedHashMap<>();
            auditDataHelper.addListPropertyItem(RestApiAuditProperty.SERVICES, serviceAuditData);
            serviceAuditData.put(RestApiAuditProperty.ID, ServiceFormatter.getServiceFullName(serviceFromSameDefinition));
            serviceAuditData.put(RestApiAuditProperty.URL, serviceFromSameDefinition.getUrl());
            serviceAuditData.put(RestApiAuditProperty.TIMEOUT, serviceFromSameDefinition.getTimeout());
            serviceAuditData.put(RestApiAuditProperty.TLS_AUTH, serviceFromSameDefinition.getSslAuthentication());
        }
    }

    /**
     * Add new endpoint to a service
     *
     * @param clientId service clientId where endpoint is added
     * @param fullServiceCode service fullServiceCode where endpoint is added
     * @param method method
     * @param path path
     * @return Endpoint
     * @throws EndpointAlreadyExistsException equivalent endpoint already exists for
     * this client
     * @throws ServiceDescriptionService.WrongServiceDescriptionException if trying to add endpoint to a WSDL
     */
    public Endpoint addEndpoint(ClientId clientId, String fullServiceCode, String method, String path)
            throws EndpointAlreadyExistsException, ServiceDescriptionService.WrongServiceDescriptionException,
            ClientNotFoundException, ServiceNotFoundException {

        ServiceEntity serviceEntity = getServiceEntity(clientId, fullServiceCode);

        if (serviceEntity.getServiceDescription().getType().equals(DescriptionType.WSDL)) {
            throw new ServiceDescriptionService.WrongServiceDescriptionException("Endpoint can't be added to a "
                    + "WSDL type of Service Description");
        }

        EndpointEntity endpointEntity = EndpointEntity.create(serviceEntity.getServiceCode(), method, path, false);
        ClientEntity clientEntity = serviceEntity.getServiceDescription().getClient();
        if (clientEntity.getEndpoints().stream()
                .anyMatch(existingEp -> existingEp.isEquivalent(endpointEntity))) {
            throw new EndpointAlreadyExistsException("Endpoint with equivalent service code, method and path already "
                    + "exists for this client");
        }
        clientEntity.getEndpoints().add(endpointEntity);
        clientRepository.merge(clientEntity, false);
        return EndpointMapper.get().toTarget(endpointEntity);
    }
}
