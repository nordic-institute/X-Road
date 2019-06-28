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
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.exceptions.WsdlParseException;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.wsdl.WsdlParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
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

    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final ClientService clientService;

    /**
     * ServiceDescriptionService constructor
     * @param serviceDescriptionRepository
     * @param clientService
     */
    @Autowired
    public ServiceDescriptionService(ServiceDescriptionRepository serviceDescriptionRepository,
            ClientService clientService) {
        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.clientService = clientService;
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
     * @throws InvalidParametersException if URL is malformed
     * @throws ConflictException URL already exists
     */
    public void addWsdlServiceDescription(ClientId clientId, String url, boolean ignoreWarnings) {
        ClientType client = clientService.getClient(clientId);
        if (client == null) {
            throw new NotFoundException("Client with id " + clientId.toShortString() + " not found");
        }
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new InvalidParametersException("Malformed URL");
        }
        client.getServiceDescription().forEach(serviceDescription -> {
            if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                throw new ConflictException("URL already exists");
            }
        });
        if (!ignoreWarnings) {
            try {
                WsdlParser.parseWSDL(url);
            } catch (Exception e) {
                throw new WsdlParseException("WSDL parsing failed", e);
            }
        }
        ServiceDescriptionType serviceDescriptionType = new ServiceDescriptionType();
    }

}
