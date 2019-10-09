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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.wsdl.InvalidWsdlException;
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
    public static final String DEFAULT_DISABLED_NOTICE = "Out of order";

    public static final String WARNING_ADDING_SERVICES = "adding_services";
    public static final String WARNING_DELETING_SERVICES = "deleting_services";
    public static final String WARNING_WSDL_VALIDATION_WARNINGS = "wsdl_validation_warnings";

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
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public void disableServices(Collection<Long> serviceDescriptionIds,
            String disabledNotice) throws ServiceDescriptionNotFoundException {
        toggleServices(false, serviceDescriptionIds, disabledNotice);
    }

    /**
     * Enable 1-n services
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    public void enableServices(Collection<Long> serviceDescriptionIds) throws ServiceDescriptionNotFoundException {
        toggleServices(true, serviceDescriptionIds, null);
    }

    /**
     * Change 1-n services to enabled/disabled
     * @param serviceDescriptionIds
     * @param disabledNotice
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    private void toggleServices(boolean toEnabled, Collection<Long> serviceDescriptionIds,
            String disabledNotice) throws ServiceDescriptionNotFoundException {
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
            throw new ServiceDescriptionNotFoundException("Service descriptions with ids " + missingIds
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
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given id was not found
     */
    @PreAuthorize("hasAuthority('DELETE_WSDL')")
    public void deleteServiceDescription(Long id) throws ServiceDescriptionNotFoundException {
        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionType == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id " + id + " not found");
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
     * @throws ClientNotFoundException if client with id was not found
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     */
    @PreAuthorize("hasAuthority('ADD_WSDL')")
    public ServiceDescriptionType addWsdlServiceDescription(ClientId clientId,
                                          String url,
                                          boolean ignoreWarnings)
            throws InvalidWsdlException,
            WsdlParser.WsdlNotFoundException,
                           ClientNotFoundException,
                           UnhandledWarningsException,
                           ServiceAlreadyExistsException,
                           InvalidUrlException,
                           WsdlUrlAlreadyExistsException {
        ClientType client = clientService.getClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException("Client with id " + clientId.toShortString() + " not found");
        }

        WsdlProcessingResult wsdlProcessingResult = processWsdl(client, url, null);

        if (!ignoreWarnings && !wsdlProcessingResult.getWarnings().isEmpty()) {
            throw new UnhandledWarningsException(wsdlProcessingResult.getWarnings());
        }

        // create a new ServiceDescription with parsed services
        ServiceDescriptionType serviceDescriptionType = buildWsdlServiceDescription(client,
                wsdlProcessingResult.getParsedServices(), url);

        client.getServiceDescription().add(serviceDescriptionType);
        clientRepository.saveOrUpdateAndFlush(client);
        return serviceDescriptionType;
    }

    /**
     * Update the WSDL url of the selected ServiceDescription
     * @param id
     * @param url the new url
     * @return ServiceDescriptionType
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException if SD with given id was not found
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     */
    @PreAuthorize("hasAuthority('EDIT_WSDL')")
    public ServiceDescriptionType updateWsdlUrl(Long id, String url, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
                           ServiceDescriptionNotFoundException,
                           WrongServiceDescriptionTypeException,
                           UnhandledWarningsException,
                           InvalidUrlException,
                           ServiceAlreadyExistsException,
                           WsdlUrlAlreadyExistsException {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id " + id.toString());
        }
        return updateWsdlUrl(serviceDescriptionType, url, ignoreWarnings);
    }

    /**
     * Refresh a ServiceDescription
     * @param id
     * @param ignoreWarnings
     * @return {@link ServiceDescriptionType}
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException if SD with given id was not found
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     */
    @PreAuthorize("hasAuthority('REFRESH_WSDL')")
    public ServiceDescriptionType refreshServiceDescription(Long id, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
                           ServiceDescriptionNotFoundException, WrongServiceDescriptionTypeException,
                           UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
                           WsdlUrlAlreadyExistsException {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id " + id.toString()
                    + " not found");
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
                                                boolean ignoreWarnings)
            throws InvalidWsdlException, WsdlParser.WsdlNotFoundException,
                           WrongServiceDescriptionTypeException, UnhandledWarningsException,
                           ServiceAlreadyExistsException, InvalidUrlException, WsdlUrlAlreadyExistsException {
        // Shouldn't be able to edit e.g. REST service descriptions with a WSDL URL
        if (serviceDescriptionType.getType() != DescriptionType.WSDL) {
            throw new WrongServiceDescriptionTypeException("Existing service description (id: "
                    + serviceDescriptionType.getId().toString() + " is not WSDL");
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
        List<WarningDeviation> allWarnings = new ArrayList<>();
        allWarnings.addAll(wsdlProcessingResult.getWarnings());
        if (!serviceChanges.isEmpty()) {
            allWarnings.addAll(createServiceChangeWarnings(serviceChanges));
        }
        if (!ignoreWarnings && !allWarnings.isEmpty()) {
            throw new UnhandledWarningsException(allWarnings);
        }

        serviceDescriptionType.setRefreshedDate(new Date());
        serviceDescriptionType.setUrl(url);

        // replace all old services with the new ones
        serviceDescriptionType.getService().clear();
        serviceDescriptionType.getService().addAll(newServices);
        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);

        return serviceDescriptionType;
    }

    /**
     * @return warnings about adding or deleting services
     */
    private List<WarningDeviation> createServiceChangeWarnings(ServiceChangeChecker.ServiceChanges changes) {
        List<WarningDeviation> warnings = new ArrayList<>();
        if (!CollectionUtils.isEmpty(changes.getAddedServices())) {
            WarningDeviation addedServicesWarningDeviation = new WarningDeviation(WARNING_ADDING_SERVICES,
                    changes.getAddedServices());
            warnings.add(addedServicesWarningDeviation);
        }
        if (!CollectionUtils.isEmpty(changes.getRemovedServices())) {
            WarningDeviation deletedServicesWarningDeviation = new WarningDeviation(WARNING_DELETING_SERVICES,
                    changes.getRemovedServices());
            warnings.add(deletedServicesWarningDeviation);
        }
        return warnings;
    }

    /**
     * check for url conflicts for other service descriptions than the
     * one we are updating now.
     */
    private void checkForExistingWsdl(ClientType client, String url,
                                      Long updatedServiceDescriptionId) throws WsdlUrlAlreadyExistsException {
        for (ServiceDescriptionType serviceDescription : client.getServiceDescription()) {
            if (!serviceDescription.getId().equals(updatedServiceDescriptionId)) {
                if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                    throw new WsdlUrlAlreadyExistsException("WSDL URL already exists");
                }
            }
        }
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

    private Collection<WsdlParser.ServiceInfo> parseWsdl(String url) throws WsdlParser.WsdlNotFoundException,
            WsdlParser.WsdlParseException {
        Collection<WsdlParser.ServiceInfo> parsedServices;
        parsedServices = WsdlParser.parseWSDL(url);
        return parsedServices;
    }

    /**
     * Validate a WSDL in given url. If fatal validation errors, throws exception.
     * If non-fatal warnings, return those.
     * @param url
     * @return list of validation warnings that can be ignored by choice
     * @throws WsdlValidator.WsdlValidationFailedException
     * @throws WsdlValidator.WsdlValidatorNotExecutableException
     * @throws InvalidUrlException
     */
    private List<String> validateWsdl(String url)
            throws WsdlValidator.WsdlValidationFailedException,
            WsdlValidator.WsdlValidatorNotExecutableException, InvalidUrlException {
        return wsdlValidator.executeValidator(url);
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
            Long idToSkip) throws ServiceAlreadyExistsException {
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
            for (ServiceType conflictedService: conflictedServices) {
                // error metadata contains service name and service description url
                errorMetadata.add(FormatUtils.getServiceFullName(conflictedService));
                errorMetadata.add(conflictedService.getServiceDescription().getUrl());
            }
            throw new ServiceAlreadyExistsException(errorMetadata);
        }
    }


    @Data
    private class WsdlProcessingResult {
        private Collection<WsdlParser.ServiceInfo> parsedServices = new ArrayList<>();
        private List<WarningDeviation> warnings = new ArrayList<>();
    }

    /**
     * Parse and validate a given wsdl and detect problems it may have.
     * Fatal problems result in thrown exception, warnings are returned in
     * WsdlProcessingResult
     * @param client client who is associated with the wsdl
     * @param url url of the wsdl
     * @param updatedServiceDescriptionId id of the service description we
     *                                    will update with this wsdl, or null
     *                                    if we're adding a new one
     * @return parsed and validated wsdl and possible warnings
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidUrlException if url was empty or invalid
     * @throws InvalidWsdlException if wsdl was invalid (either parsing or validation)
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     */
    private WsdlProcessingResult processWsdl(ClientType client, String url,
                                             Long updatedServiceDescriptionId)
            throws WsdlParser.WsdlNotFoundException,
                           InvalidWsdlException,
                           InvalidUrlException,
                           WsdlUrlAlreadyExistsException,
                           ServiceAlreadyExistsException {

        WsdlProcessingResult result = new WsdlProcessingResult();
        // check for valid url (is this not enough??)
        if (!FormatUtils.isValidUrl(url)) {
            throw new InvalidUrlException("Malformed URL");
        }
        // check if wsdl already exists
        checkForExistingWsdl(client, url, updatedServiceDescriptionId);

        // parse wsdl
        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check if services exist
        checkForExistingServices(client, parsedServices, updatedServiceDescriptionId);

        // validate wsdl
        List<String> warningStrings = null;
        try {
            warningStrings = validateWsdl(url);
        } catch (WsdlValidator.WsdlValidatorNotExecutableException e) {
            throw new RuntimeException("could not run validator command", e);
        }
        List<WarningDeviation> warnings = new ArrayList<>();
        if (!warningStrings.isEmpty()) {
            WarningDeviation validationWarningDeviation = new WarningDeviation(WARNING_WSDL_VALIDATION_WARNINGS,
                    warningStrings);
            warnings.add(validationWarningDeviation);
        }
        result.setParsedServices(parsedServices);
        result.setWarnings(warnings);
        return result;
    }

    /**
     * If trying to add a service that already exists
     */
    public static class ServiceAlreadyExistsException extends ServiceException {

        public static final String ERROR_SERVICE_EXISTS = "service_already_exists";

        public ServiceAlreadyExistsException(List<String> metadata) {
            super(new ErrorDeviation(ERROR_SERVICE_EXISTS, metadata));
        }
    }

    public static class WrongServiceDescriptionTypeException extends ServiceException {

        public static final String ERROR_WRONG_TYPE = "wrong_servicedescription_type";


        public WrongServiceDescriptionTypeException(String s) {
            super(s, new ErrorDeviation(ERROR_WRONG_TYPE));
        }
    }

    public static class WsdlUrlAlreadyExistsException extends ServiceException {

        public static final String ERROR_WSDL_EXISTS = "wsdl_exists";

        public WsdlUrlAlreadyExistsException(String s) {
            super(s, new ErrorDeviation(ERROR_WSDL_EXISTS));
        }
    }
}
