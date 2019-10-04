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
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.Error;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.exceptions.Warning;
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
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    public static final String DEFAULT_DISABLED_NOTICE = "Out of order";

    public static final String ERROR_INVALID_WSDL = "clients.invalid_wsdl";
    public static final String ERROR_WSDL_DOWNLOAD_FAILED = "clients.wsdl_download_failed";
    public static final String ERROR_WSDL_EXISTS = "clients.wsdl_exists";
    public static final String ERROR_SERVICE_EXISTS = "clients.service_exists";
    public static final String ERROR_MALFORMED_URL = "clients.malformed_wsdl_url";
    public static final String ERROR_WRONG_TYPE = "clients.servicedescription_wrong_type";
    public static final String ERROR_WARNINGS_DETECTED = "clients.warnings_detected";

    public static final String WARNING_ADDING_SERVICES = "clients.adding_services";
    public static final String WARNING_DELETING_SERVICES = "clients.deleting_services";
    public static final String WARNING_WSDL_VALIDATION_WARNINGS = "clients.wsdl_validation_warnings";

    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final ServiceChangeChecker serviceChangeChecker;
    private final WsdlValidator wsdlValidator;

    /**
     * ServiceDescriptionService constructor
     * @param serviceDescriptionRepository
     * @param clientService
     * @param clientRepository
     */
    @Autowired
    public ServiceDescriptionService(ServiceDescriptionRepository serviceDescriptionRepository,
            ClientService clientService, ClientRepository clientRepository,
            ServiceChangeChecker serviceChangeChecker,
            WsdlValidator wsdlValidator) {
        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.serviceChangeChecker = serviceChangeChecker;
        this.wsdlValidator = wsdlValidator;
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
     * @return created {@link ServiceDescriptionType}, with id populated
     * @throws InvalidParametersException if URL is malformed
     * @throws ConflictException          URL already exists
     */
    @PreAuthorize("hasAuthority('ADD_WSDL')")
    public ServiceDescriptionType addWsdlServiceDescription(ClientId clientId,
            String url,
            boolean ignoreWarnings) {
        ClientType client = clientService.getClient(clientId);
        if (client == null) {
            throw new NotFoundException("Client with id " + clientId.toShortString() + " not found");
        }

        WsdlProcessingResult wsdlProcessingResult = processWsdl(client, url, null);

        if (!ignoreWarnings && !wsdlProcessingResult.getWarnings().isEmpty()) {
            throw new BadRequestException(new Error(ERROR_WARNINGS_DETECTED),
                    wsdlProcessingResult.getWarnings());
        }

        // create a new ServiceDescription with parsed services
        ServiceDescriptionType serviceDescriptionType = buildWsdlServiceDescription(client,
                wsdlProcessingResult.getParsedServices(), url);

        // get the new endpoints to add - skipping existing ones
        Collection<EndpointType> endpointsToAdd = resolveNewEndpoints(client, serviceDescriptionType);

        client.getEndpoint().addAll(endpointsToAdd);
        client.getServiceDescription().add(serviceDescriptionType);
        clientRepository.saveOrUpdateAndFlush(client);
        return serviceDescriptionType;
    }

    private Collection<EndpointType> resolveNewEndpoints(ClientType client,
            ServiceDescriptionType newServiceDescription) {
        Map<String, EndpointType> endpointMap = new HashMap<>();

        // add all new endpoint into a hashmap with a combination key
        newServiceDescription.getService().forEach(serviceType -> {
            EndpointType endpointType = new EndpointType(serviceType.getServiceCode(), EndpointType.ANY_METHOD,
                    EndpointType.ANY_PATH, true);
            endpointMap.put(endpointType.getServiceCode()
                    + endpointType.getMethod()
                    + endpointType.getPath()
                    + endpointType.isGenerated(), endpointType);
        });

        // remove all existing endpoints by equal combination key
        client.getEndpoint().forEach(endpointType -> endpointMap.remove(endpointType.getServiceCode()
                + endpointType.getMethod()
                + endpointType.getPath()
                + endpointType.isGenerated()));

        return endpointMap.values();
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
        return updateWsdlUrl(serviceDescriptionType, url, ignoreWarnings);
    }

    /**
     * Refresh a ServiceDescription
     * @param id
     * @param ignoreWarnings
     * @return {@link ServiceDescriptionType}
     */
    @PreAuthorize("hasAuthority('REFRESH_WSDL')")
    public ServiceDescriptionType refreshServiceDescription(Long id, boolean ignoreWarnings) {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new NotFoundException("Service description with id " + id.toString() + " not found");
        }

        if (serviceDescriptionType.getType() == DescriptionType.WSDL) {
            String wsdlUrl = serviceDescriptionType.getUrl();
            return updateWsdlUrl(serviceDescriptionType, wsdlUrl, ignoreWarnings);
        }

        // we only have two types at the moment so the type must be OPENAPI3 if we end up this far
        throw new NotImplementedException("REST ServiceDescription refresh not implemented yet");
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

    /**
     * Update the WSDL url of the selected ServiceDescription.
     * Refreshing a WSDL is also an update of wsdl,
     * it just updates to the same URL value
     * @param serviceDescriptionType
     * @param url the new url
     * @return ServiceDescriptionType
     */
    private ServiceDescriptionType updateWsdlUrl(ServiceDescriptionType serviceDescriptionType, String url,
            boolean ignoreWarnings) {
        // Shouldn't be able to edit e.g. REST service descriptions with a WSDL URL
        if (serviceDescriptionType.getType() != DescriptionType.WSDL) {
            throw new BadRequestException("Existing service description (id: "
                    + serviceDescriptionType.getId().toString() + " is not WSDL",
                    new Error(ERROR_WRONG_TYPE));
        }

        ClientType client = serviceDescriptionType.getClient();
        WsdlProcessingResult wsdlProcessingResult = processWsdl(client, url, serviceDescriptionType.getId());

        List<ServiceType> newServices = wsdlProcessingResult.getParsedServices()
                .stream()
                .map(serviceInfo -> serviceInfoToServiceType(serviceInfo, serviceDescriptionType))
                .collect(Collectors.toList());

        // find what services were added or removed
        ServiceChangeChecker.ServiceChanges serviceChanges = serviceChangeChecker.check(
                serviceDescriptionType.getService(),
                newServices);

        // collect all types of warnings, throw Exception if not ignored
        List<Warning> allWarnings = new ArrayList<>();
        allWarnings.addAll(wsdlProcessingResult.getWarnings());
        if (!serviceChanges.isEmpty()) {
            allWarnings.addAll(createServiceChangeWarnings(serviceChanges));
        }
        if (!ignoreWarnings && !allWarnings.isEmpty()) {
            throw new BadRequestException(new Error(ERROR_WARNINGS_DETECTED),
                    allWarnings);
        }

        serviceDescriptionType.setRefreshedDate(new Date());
        serviceDescriptionType.setUrl(url);

        Set<String> oldServiceCodes = serviceDescriptionType.getService()
                .stream()
                .map(ServiceType::getServiceCode)
                .collect(Collectors.toSet());
        // remove all related endpoints
        client.getEndpoint().removeIf(endpointType -> oldServiceCodes.contains(endpointType.getServiceCode()));
        // replace all old services with the new ones
        serviceDescriptionType.getService().clear();
        serviceDescriptionType.getService().addAll(newServices);

        // update endpoints
        Collection<EndpointType> endpointsToAdd = resolveNewEndpoints(client, serviceDescriptionType);
        client.getEndpoint().addAll(endpointsToAdd);
        clientRepository.saveOrUpdate(client);

        return serviceDescriptionType;
    }

    /**
     * @return warnings about adding or deleting services
     */
    private List<Warning> createServiceChangeWarnings(ServiceChangeChecker.ServiceChanges changes) {
        List<Warning> warnings = new ArrayList<>();
        if (!CollectionUtils.isEmpty(changes.getAddedServices())) {
            Warning addedServicesWarning = new Warning(WARNING_ADDING_SERVICES,
                    changes.getAddedServices());
            warnings.add(addedServicesWarning);
        }
        if (!CollectionUtils.isEmpty(changes.getRemovedServices())) {
            Warning deletedServicesWarning = new Warning(WARNING_DELETING_SERVICES,
                    changes.getRemovedServices());
            warnings.add(deletedServicesWarning);
        }
        return warnings;
    }

    /**
     * check for url conflicts for other service descriptions than the
     * one we are updating now.
     */
    private void checkForExistingWsdl(ClientType client, String url,
            Long updatedServiceDescriptionId) throws ConflictException {
        client.getServiceDescription().forEach(serviceDescription -> {
            if (!serviceDescription.getId().equals(updatedServiceDescriptionId)) {
                if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                    throw new ConflictException("WSDL URL already exists", new Error(ERROR_WSDL_EXISTS));
                }
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
            throw new BadRequestException(e, new Error(ERROR_INVALID_WSDL));
        } catch (WsdlNotFoundException e) {
            throw new BadRequestException(e, new Error(ERROR_WSDL_DOWNLOAD_FAILED));
        }
        return parsedServices;
    }

    /**
     * Validate a WSDL in given url. If fatal validation errors, throws exception.
     * If non-fatal warnings, return those.
     * @param url
     * @return list of validation warnings that can be ignored by choice
     * @throws BadRequestException if fatal validation errors occurred
     */
    private List<String> validateWsdl(String url) throws BadRequestException {
        try {
            return wsdlValidator.executeValidator(url);
        } catch (WsdlValidationException e) {
            log.error("WSDL validation failed", e);
            throw new BadRequestException(e, e.getError(), e.getWarnings());
        }
    }

    private List<ServiceType> getClientsExistingServices(ClientType client, Long idToSkip) {
        return client.getServiceDescription()
                .stream()
                .filter(serviceDescriptionType -> !Objects.equals(serviceDescriptionType.getId(), idToSkip))
                .map(ServiceDescriptionType::getService)
                .flatMap(List::stream).collect(Collectors.toList());
    }

    /**
     * Check that the client does not have conflicting service codes
     * in other service descriptions. Throw exception if conflicts
     */
    private void checkForExistingServices(ClientType client,
            Collection<WsdlParser.ServiceInfo> parsedServices,
            Long idToSkip) throws ConflictException {
        List<ServiceType> existingServices = getClientsExistingServices(client, idToSkip);

        Set<ServiceType> conflictedServices = parsedServices
                .stream()
                .flatMap(newService -> existingServices
                        .stream()
                        .filter(existingService -> FormatUtils.getServiceFullName(existingService)
                                .equalsIgnoreCase(FormatUtils.getServiceFullName(newService))))
                .collect(Collectors.toSet());

        // throw error with service metadata if conflicted
        if (!conflictedServices.isEmpty()) {
            List<String> errorMetadata = new ArrayList();
            for (ServiceType conflictedService : conflictedServices) {
                // error metadata contains service name and service description url
                errorMetadata.add(FormatUtils.getServiceFullName(conflictedService));
                errorMetadata.add(conflictedService.getServiceDescription().getUrl());
            }
            throw new ConflictException(new Error(ERROR_SERVICE_EXISTS, errorMetadata));
        }
    }

    @Data
    private class WsdlProcessingResult {
        private Collection<WsdlParser.ServiceInfo> parsedServices = new ArrayList<>();
        private List<Warning> warnings = new ArrayList<>();
    }

    /**
     * Parse and validate a given wsdl and detect problems it may have.
     * Fatal problems result in thrown exception, warnings are returned in
     * WsdlProcessingResult
     * @param client client who is associated with the wsdl
     * @param url url of the wsdl
     * @param updatedServiceDescriptionId id of the service description we
     * will update with this wsdl, or null
     * if we're adding a new one
     * @return parsed and validated wsdl and possible warnings
     */
    private WsdlProcessingResult processWsdl(ClientType client, String url,
            Long updatedServiceDescriptionId) {

        WsdlProcessingResult result = new WsdlProcessingResult();
        // check for valid url (is this not enough??)
        if (!FormatUtils.isValidUrl(url)) {
            throw new BadRequestException("Malformed URL", new Error(ERROR_MALFORMED_URL));
        }
        // check if wsdl already exists
        checkForExistingWsdl(client, url, updatedServiceDescriptionId);

        // parse wsdl
        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check if services exist
        checkForExistingServices(client, parsedServices, updatedServiceDescriptionId);

        // validate wsdl
        List<String> warningStrings = validateWsdl(url);
        List<Warning> warnings = new ArrayList<>();
        if (!warningStrings.isEmpty()) {
            Warning validationWarning = new Warning(WARNING_WSDL_VALIDATION_WARNINGS,
                    warningStrings);
            warnings.add(validationWarning);
        }
        result.setParsedServices(parsedServices);
        result.setWarnings(warnings);
        return result;
    }
}
