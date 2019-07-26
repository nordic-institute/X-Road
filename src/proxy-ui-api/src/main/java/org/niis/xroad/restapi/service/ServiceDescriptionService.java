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
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.Deviation;
import org.niis.xroad.restapi.exceptions.ErrorCode;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.exceptions.WsdlNotFoundException;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.wsdl.WsdlValidator.WSDL_VALIDATION_WARNINGS;

/**
 * ServiceDescription service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ServiceDescriptionService {

    public static final int DEFAULT_SERVICE_TIMEOUT = 60;
    public static final String DEFAULT_DISABLED_NOTICE = "Out of order";
    public static final String ADDING_WSDL_FAILED = "clients.adding_wsdl_failed";
    public static final String INVALID_WSDL = "clients.invalid_wsdl";
    public static final String WSDL_DOWNLOAD_FAILED = "clients.wsdl_download_failed";
    public static final String WSDL_EXISTS = "clients.wsdl_exists";
    public static final String SERVICE_EXISTS = "clients.service_exists";
    public static final String MALFORMED_URL = "clients.malformed_wsdl_url";
    public static final String WRONG_TYPE = "clients.servicedescription_wrong_type";

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
     * Delete one ServiceDescription
     * @throws NotFoundException if serviceDescriptions with given id was not found
     */
    @PreAuthorize("hasAuthority('DELETE_WSDL')")
    public void deleteServiceDescription(Long id) {
        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionType == null) {
            throw new NotFoundException("Service description with id " + id + " not found");
        }
        ClientType client = serviceDescriptionType.getClient();
        client.getServiceDescription().remove(serviceDescriptionType);
        clientRepository.saveOrUpdate(client);
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
            throw new BadRequestException("Malformed URL", ErrorCode.of(MALFORMED_URL));
        }

        // check if wsdl already exists
        checkForExistingWsdl(client, url);

        // parse wsdl
        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check if services exist
        checkForExistingServices(client, parsedServices);

        // try to validate wsdl - unless warnings are ignored
        if (!ignoreWarnings) {
            validateWsdl(url);
        }

        // create a new ServiceDescription with parsed services
        ServiceDescriptionType serviceDescriptionType = buildWsdlServiceDescription(client, parsedServices, url);

        client.getServiceDescription().add(serviceDescriptionType);
        clientRepository.saveOrUpdate(client);
    }

    /**
     * Update the WSDL url of the selected ServiceDescription
     * @param id
     * @param url the new url
     * @return ServiceDescriptionType
     */
    @PreAuthorize("hasAuthority('EDIT_WSDL')")
    public ServiceDescriptionType updateWsdlUrl(Long id, String url, boolean ignoreWarnings) {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new NotFoundException("Service description with id " + id.toString() + " not found");
        }

        // Shouldn't be able to edit e.g. REST service descriptions with a WSDL URL
        if (serviceDescriptionType.getType() != DescriptionType.WSDL) {
            throw new BadRequestException("Existing service description (id: " + id.toString() + " is not WSDL",
                    ErrorCode.of(WRONG_TYPE));
        }

        if (!FormatUtils.isValidUrl(url)) {
            throw new BadRequestException("Malformed URL", ErrorCode.of(MALFORMED_URL));
        }

        ClientType client = serviceDescriptionType.getClient();

        checkForExistingWsdl(client, url);

        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check for existing services but exclude the services in the ServiceDescription that we are updating
        checkForExistingServices(client, parsedServices, id);

        if (!ignoreWarnings) {
            validateWsdl(url);
        }

        serviceDescriptionType.setUrl(url);
        serviceDescriptionType.setRefreshedDate(new Date());

        // create services
        List<ServiceType> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        // replace all old services with the new ones
        serviceDescriptionType.getService().clear();
        serviceDescriptionType.getService().addAll(newServices);
        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);

        return serviceDescriptionType;
    }

    private void checkForExistingWsdl(ClientType client, String url) throws ConflictException {
        client.getServiceDescription().forEach(serviceDescription -> {
            if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                throw new ConflictException("WSDL URL already exists", ErrorCode.of(WSDL_EXISTS));
            }
        });
    }

    private ServiceDescriptionType buildWsdlServiceDescription(ClientType client,
            Collection<WsdlParser.ServiceInfo> parsedServices, String url) {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url, DescriptionType.WSDL);

        // create services
        List<ServiceType> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        serviceDescriptionType.getService().addAll(newServices);

        return serviceDescriptionType;
    }

    /**
     * Get one ServiceDescriptionType by id
     * @param id
     * @return ServiceDescriptionType
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ServiceDescriptionType getServiceDescriptiontype(Long id) {
        return serviceDescriptionRepository.getServiceDescription(id);
    }

    private ServiceDescriptionType getServiceDescriptionOfType(ClientType client, String url,
            DescriptionType descriptionType) {
        ServiceDescriptionType serviceDescriptionType = new ServiceDescriptionType();
        serviceDescriptionType.setClient(client);
        serviceDescriptionType.setDisabled(true);
        serviceDescriptionType.setDisabledNotice(DEFAULT_DISABLED_NOTICE);
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

    private Collection<WsdlParser.ServiceInfo> parseWsdl(String url) throws BadRequestException {
        Collection<WsdlParser.ServiceInfo> parsedServices;
        try {
            parsedServices = WsdlParser.parseWSDL(url);
        } catch (WsdlParseException e) {
            List<Deviation> warnings = new ArrayList();
            warnings.add(new Deviation(INVALID_WSDL, e.getCause().getMessage()));
            throw new BadRequestException(e, ErrorCode.of(ADDING_WSDL_FAILED), warnings);
        } catch (WsdlNotFoundException e) {
            List<Deviation> warnings = new ArrayList();
            warnings.add(new Deviation(WSDL_DOWNLOAD_FAILED, e.getCause().getMessage()));
            throw new BadRequestException(e, ErrorCode.of(ADDING_WSDL_FAILED), warnings);
        }
        return parsedServices;
    }

    private void validateWsdl(String url) throws BadRequestException {
        try {
            new WsdlValidator(url).executeValidator();
        } catch (WsdlValidationException e) {
            log.error("WSDL validation failed", e);
            throw new BadRequestException(e, ErrorCode.of(WSDL_VALIDATION_WARNINGS), e.getWarnings());
        }
    }

    private List<ServiceType> getClientsExistingServices(ClientType client, Long idToSkip) {
        return client.getServiceDescription()
                .stream()
                .filter(serviceDescriptionType -> !Objects.equals(serviceDescriptionType.getId(), idToSkip))
                .map(ServiceDescriptionType::getService)
                .flatMap(List::stream).collect(Collectors.toList());
    }

    private List<ServiceType> getClientsExistingServices(ClientType client) {
        return getClientsExistingServices(client, null);
    }

    private void checkForExistingServices(ClientType client, Collection<WsdlParser.ServiceInfo> parsedServices,
            Long idToSkip) throws ConflictException {
        List<ServiceType> existingServices = getClientsExistingServices(client, idToSkip);

        Set<ServiceType> conflictedServices = parsedServices
                .stream()
                .flatMap(newService -> existingServices
                        .stream()
                        .filter(existingService -> FormatUtils.getServiceFullName(existingService)
                                .equalsIgnoreCase(FormatUtils.getServiceFullName(newService))))
                .collect(Collectors.toSet());

        // create warnings and throw if conflicted
        if (!conflictedServices.isEmpty()) {
            List<Deviation> warnings = new ArrayList();
            List<String> conflictedServiceNames = conflictedServices.stream()
                    .map(conflictedService -> FormatUtils.getServiceFullName(conflictedService))
                    .collect(Collectors.toList());
            warnings.add(new Deviation(SERVICE_EXISTS, conflictedServiceNames));
            throw new ConflictException(ErrorCode.of(ADDING_WSDL_FAILED), warnings);
        }
    }

    private void checkForExistingServices(ClientType client,
            Collection<WsdlParser.ServiceInfo> parsedServices) throws ConflictException {
        checkForExistingServices(client, parsedServices, null);
    }
}
