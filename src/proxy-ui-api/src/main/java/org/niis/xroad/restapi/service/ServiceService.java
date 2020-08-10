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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVICES;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TIMEOUT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.TLS_AUTH;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.URL;

/**
 * service class for handling services
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ServiceService {

    private final ClientRepository clientRepository;
    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final UrlValidator urlValidator;
    private final AuditDataHelper auditDataHelper;

    @Autowired
    public ServiceService(ClientRepository clientRepository, ServiceDescriptionRepository serviceDescriptionRepository,
            UrlValidator urlValidator, AuditDataHelper auditDataHelper) {
        this.clientRepository = clientRepository;
        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.urlValidator = urlValidator;
        this.auditDataHelper = auditDataHelper;
    }

    /**
     * get ServiceType by ClientId and service code that includes service version
     * see {@link FormatUtils#getServiceFullName(ServiceType)}.
     * ServiceType has serviceType.serviceDescription.client.endpoints lazy field fetched.
     * @param clientId
     * @param fullServiceCode
     * @return
     * @throws ClientNotFoundException if client with given id was not found
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     */
    public ServiceType getService(ClientId clientId, String fullServiceCode) throws ClientNotFoundException,
            ServiceNotFoundException {
        ClientType client = clientRepository.getClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException("Client " + clientId.toShortString() + " not found");
        }

        ServiceType serviceType = getServiceFromClient(client, fullServiceCode);
        Hibernate.initialize(serviceType.getServiceDescription().getClient().getEndpoint());
        return serviceType;
    }

    /**
     * Get {@link ServiceType} from a {@link ClientType} by comparing the full service code (with version).
     * @param client
     * @param fullServiceCode
     * @return ServiceType
     * @throws ServiceNotFoundException if service with fullServiceCode was not found
     */
    public ServiceType getServiceFromClient(ClientType client, String fullServiceCode)
            throws ServiceNotFoundException {
        Optional<ServiceType> foundService = client.getServiceDescription()
                .stream()
                .map(ServiceDescriptionType::getService)
                .flatMap(List::stream)
                .filter(serviceType -> FormatUtils.getServiceFullName(serviceType).equals(fullServiceCode))
                .findFirst();
        return foundService.orElseThrow(() -> new ServiceNotFoundException("Service "
                + fullServiceCode + " not found"));
    }

    /**
     * update a Service. clientId and fullServiceCode identify the updated service.
     * @param clientId clientId of the client associated with the service
     * @param fullServiceCode service code that includes service version
     * see {@link FormatUtils#getServiceFullName(ServiceType)}
     * @param url
     * @param urlAll
     * @param timeout
     * @param timeoutAll
     * @param sslAuth
     * @param sslAuthAll
     * @return ServiceType
     * @throws InvalidUrlException if given url was not valid
     * @throws ServiceNotFoundException if service with given fullServicecode was not found
     * @throws ClientNotFoundException if client with given id was not found
     */
    public ServiceType updateService(ClientId clientId, String fullServiceCode,
            String url, boolean urlAll, Integer timeout, boolean timeoutAll,
            boolean sslAuth, boolean sslAuthAll) throws InvalidUrlException, ServiceNotFoundException,
            ClientNotFoundException {

        auditDataHelper.put(clientId);

        if (!urlValidator.isValidUrl(url)) {
            throw new InvalidUrlException("URL is not valid: " + url);
        }

        ServiceType serviceType = getService(clientId, fullServiceCode);

        if (serviceType == null) {
            throw new ServiceNotFoundException("Service " + fullServiceCode + " not found");
        }

        ServiceDescriptionType serviceDescriptionType = serviceType.getServiceDescription();
        if (DescriptionType.REST.equals(serviceDescriptionType.getType())) {
            serviceDescriptionType.setUrl(url);
        }

        auditDataHelper.putServiceDescriptionUrl(serviceDescriptionType);

        serviceDescriptionType.getService().forEach(service -> {
            updateServiceFromSameDefinition(url, urlAll, timeout,
                    timeoutAll, sslAuth, sslAuthAll,
                    serviceType, service);
        });

        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);

        return serviceType;
    }

    /**
     * @param targetService service we are actually updating
     * @param serviceFromSameDefinition another service from same service definition. Can be == targetService
     */
    private void updateServiceFromSameDefinition(String url, boolean urlAll, Integer timeout,
            boolean timeoutAll, boolean sslAuth, boolean sslAuthAll,
            ServiceType targetService, ServiceType serviceFromSameDefinition) {

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
            auditDataHelper.addListPropertyItem(SERVICES, serviceAuditData);
            serviceAuditData.put(ID, FormatUtils.getServiceFullName(serviceFromSameDefinition));
            serviceAuditData.put(URL, serviceFromSameDefinition.getUrl());
            serviceAuditData.put(TIMEOUT, serviceFromSameDefinition.getTimeout());
            serviceAuditData.put(TLS_AUTH, serviceFromSameDefinition.getSslAuthentication());
        }
    }

    /**
     * Add new endpoint to a service
     *
     * @param serviceType                                                       service where endpoint is added
     * @param method                                                            method
     * @param path                                                              path
     * @return
     * @throws EndpointAlreadyExistsException                                   equivalent endpoint already exists for
     *                                                                          this client
     * @throws ServiceDescriptionService.WrongServiceDescriptionTypeException   if trying to add endpoint to a WSDL
     */
    public EndpointType addEndpoint(ServiceType serviceType, String method, String path)
            throws EndpointAlreadyExistsException, ServiceDescriptionService.WrongServiceDescriptionTypeException {

        if (serviceType.getServiceDescription().getType().equals(DescriptionType.WSDL)) {
            throw new ServiceDescriptionService.WrongServiceDescriptionTypeException("Endpoint can't be added to a "
                    + "WSDL type of Service Description");
        }

        EndpointType endpointType = new EndpointType(serviceType.getServiceCode(), method, path, false);
        ClientType client = serviceType.getServiceDescription().getClient();
        if (client.getEndpoint().stream().anyMatch(existingEp -> existingEp.isEquivalent(endpointType))) {
            throw new EndpointAlreadyExistsException("Endpoint with equivalent service code, method and path already "
                    + "exists for this client");
        }
        client.getEndpoint().add(endpointType);
        clientRepository.saveOrUpdate(client);
        return endpointType;
    }

}
