/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
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
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.tools.common.ToolException;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.exceptions.WsdlParseException;
import org.niis.xroad.restapi.exceptions.WsdlValidationException;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.wsdl.WsdlParser;
import org.niis.xroad.restapi.wsdl.WsdlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ServiceDescription service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ServiceDescriptionService {

    public static final int DEFAULT_SERVICE_TIMEOUT = 60;

    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final ClientService clientService;
    private final ClientRepository clientRepository;

    /**
     * ServiceDescriptionService constructor
     * @param serviceDescriptionRepository
     * @param clientService
     * @param clientRepository
     */
    @Autowired
    public ServiceDescriptionService(ServiceDescriptionRepository serviceDescriptionRepository,
            ClientService clientService, ClientRepository clientRepository) {
        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.clientService = clientService;
        this.clientRepository = clientRepository;
    }

    /**
     * Disable 1-n services
     * @throws NotFoundException if serviceDescriptions with given ids were not found
     */
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public void disableServices(Collection<Long> serviceDescriptionIds,
            String disabledNotice) {
        toggleServices(false, serviceDescriptionIds, disabledNotice);
    }

    /**
     * Enable 1-n services
     * @throws NotFoundException if serviceDescriptions with given ids were not found
     */
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public void enableServices(Collection<Long> serviceDescriptionIds) {
        toggleServices(true, serviceDescriptionIds, null);
    }

    /**
     * Change 1-n services to enabled/disabled
     * @param serviceDescriptionIds
     * @param disabledNotice
     * @throws NotFoundException if serviceDescriptions with given ids were not found
     */
    private void toggleServices(boolean toEnabled, Collection<Long> serviceDescriptionIds,
            String disabledNotice) {
        List<ServiceDescriptionType> possiblyNullServiceDescriptions = serviceDescriptionRepository
                .getServiceDescriptions(serviceDescriptionIds.toArray(new Long[] {}));

        List<ServiceDescriptionType> serviceDescriptions = possiblyNullServiceDescriptions.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (serviceDescriptions.size() != serviceDescriptionIds.size()) {
            Set<Long> foundIds = serviceDescriptions.stream()
                    .map(serviceDescriptionType -> serviceDescriptionType.getId())
                    .collect(Collectors.toSet());
            Set<Long> missingIds = new HashSet<>(serviceDescriptionIds);
            missingIds.removeAll(foundIds);
            throw new NotFoundException("Service descriptions with ids " + missingIds
                    + " not found");
        }

        serviceDescriptions.stream()
                .forEach(serviceDescriptionType -> {
                    serviceDescriptionType.setDisabled(!toEnabled);
                    if (!toEnabled) {
                        serviceDescriptionType.setDisabledNotice(disabledNotice);
                    }
                    serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);
                });
    }

    /**
     * Add a new WSDL ServiceDescription
     * @param clientId
     * @param url
     * @param ignoreWarnings
     * @throws InvalidParametersException if URL is malformed
     * @throws ConflictException          URL already exists
     */
    @PreAuthorize("hasAuthority('ADD_WSDL')")
    public void addWsdlServiceDescription(ClientId clientId, String url, boolean ignoreWarnings) {
        ClientType client = clientService.getClient(clientId);
        if (client == null) {
            throw new NotFoundException("Client with id " + clientId.toShortString() + " not found");
        }

        // check for valid url (is this not enough??)
        if (!FormatUtils.isValidUrl(url)) {
            throw new InvalidParametersException("Malformed URL");
        }

        List<ServiceType> existingServices = client.getServiceDescription()
                .stream()
                .map(ServiceDescriptionType::getService)
                .flatMap(List::stream).collect(Collectors.toList());

        // check if wsdl already exist
        client.getServiceDescription().forEach(serviceDescription -> {
            if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                throw new ConflictException("WSDL URL already exists");
            }
        });

        // validate wsdl before parsing - if warnings are ignored
        if (!ignoreWarnings) {
            try {
                WsdlValidator.executeValidator(url);
            } catch (ToolException ex) {
                throw new WsdlValidationException("WSDL validation failed", ex);
            }
        }

        Collection<WsdlParser.ServiceInfo> parsedServices;
        try {
            parsedServices = WsdlParser.parseWSDL(url);
        } catch (WsdlParseException ex) {
            throw new BadRequestException("WSDL parsing failed", ex);
        }

        // check if services already exist
        existingServices.forEach(existingService -> parsedServices.forEach(newService -> {
            if (existingService.getServiceCode().equalsIgnoreCase(newService.name)
                    && existingService.getServiceVersion().equalsIgnoreCase(newService.version)) {
                throw new ConflictException("Service already exists");
            }
        }));

        // create new ServiceDescription
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url, DescriptionType.WSDL);

        // create services
        List<ServiceType> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        serviceDescriptionType.getService().addAll(newServices);
        client.getServiceDescription().add(serviceDescriptionType);
        clientRepository.saveOrUpdate(client);
    }

    private ServiceDescriptionType getServiceDescriptionOfType(ClientType client, String url,
            DescriptionType descriptionType) {
        ServiceDescriptionType serviceDescriptionType = new ServiceDescriptionType();
        serviceDescriptionType.setClient(client);
        serviceDescriptionType.setDisabled(true);
        serviceDescriptionType.setDisabledNotice("DEFAULT_DISABLED_NOTICE");
        serviceDescriptionType.setRefreshedDate(new Date());
        serviceDescriptionType.setType(descriptionType);
        serviceDescriptionType.setUrl(url);
        return serviceDescriptionType;
    }

    private ServiceType serviceInfoToServiceType(WsdlParser.ServiceInfo serviceInfo,
            ServiceDescriptionType serviceDescriptionType) {
        ServiceType newService = new ServiceType();
        newService.setServiceCode(serviceInfo.name);
        newService.setServiceVersion(serviceInfo.version);
        newService.setTitle(serviceInfo.title);
        newService.setUrl(serviceInfo.url);
        newService.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        newService.setServiceDescription(serviceDescriptionType);
        return newService;
    }

}
