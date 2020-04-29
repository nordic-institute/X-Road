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
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.validator.EncodedIdentifierValidator;
import org.niis.xroad.restapi.wsdl.InvalidWsdlException;
import org.niis.xroad.restapi.wsdl.OpenApiParser;
import org.niis.xroad.restapi.wsdl.WsdlParser;
import org.niis.xroad.restapi.wsdl.WsdlValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
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
@Transactional(rollbackFor = ServiceException.class)
@PreAuthorize("isAuthenticated()")
public class ServiceDescriptionService {

    public static final int DEFAULT_SERVICE_TIMEOUT = 60;
    public static final String DEFAULT_DISABLED_NOTICE = "Out of order";

    public static final String WARNING_ADDING_SERVICES = "adding_services";
    public static final String WARNING_DELETING_SERVICES = "deleting_services";
    public static final String WARNING_WSDL_VALIDATION_WARNINGS = "wsdl_validation_warnings";

    public static final String SERVICE_NOT_FOUND_ERROR_MSG = "Service not found from servicedescription with id ";

    public static final String CLIENT_WITH_ID = "Client with id";

    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final ClientService clientService;
    private final ClientRepository clientRepository;
    private final ServiceChangeChecker serviceChangeChecker;
    private final WsdlValidator wsdlValidator;
    private final UrlValidator urlValidator;
    private final OpenApiParser openApiParser;

    /**
     * ServiceDescriptionService constructor
     *
     * @param serviceDescriptionRepository
     * @param clientService
     * @param clientRepository
     * @param urlValidator
     */
    @Autowired
    public ServiceDescriptionService(ServiceDescriptionRepository serviceDescriptionRepository,
            ClientService clientService, ClientRepository clientRepository,
            ServiceChangeChecker serviceChangeChecker,
            WsdlValidator wsdlValidator, UrlValidator urlValidator,
            OpenApiParser openApiParser) {

        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.clientService = clientService;
        this.clientRepository = clientRepository;
        this.serviceChangeChecker = serviceChangeChecker;
        this.wsdlValidator = wsdlValidator;
        this.urlValidator = urlValidator;
        this.openApiParser = openApiParser;
    }

    /**
     * Disable 1-n services
     *
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    public void disableServices(Collection<Long> serviceDescriptionIds,
            String disabledNotice) throws ServiceDescriptionNotFoundException {
        toggleServices(false, serviceDescriptionIds, disabledNotice);
    }

    /**
     * Enable 1-n services
     *
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    public void enableServices(Collection<Long> serviceDescriptionIds) throws ServiceDescriptionNotFoundException {
        toggleServices(true, serviceDescriptionIds, null);
    }

    /**
     * Change 1-n services to enabled/disabled
     *
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
     *
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given id was not found
     */
    public void deleteServiceDescription(Long id) throws ServiceDescriptionNotFoundException {
        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionType == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id " + id + " not found");
        }
        ClientType client = serviceDescriptionType.getClient();
        cleanAccessRights(client, serviceDescriptionType);
        cleanEndpoints(client, serviceDescriptionType);
        client.getServiceDescription().remove(serviceDescriptionType);
        clientRepository.saveOrUpdate(client);
    }

    private void cleanEndpoints(ClientType client, ServiceDescriptionType serviceDescriptionType) {
        Set<String> servicesToRemove = serviceDescriptionType.getService()
                .stream()
                .map(ServiceType::getServiceCode)
                .collect(Collectors.toSet());
        client.getEndpoint().removeIf(endpointType -> servicesToRemove.contains(endpointType.getServiceCode()));
    }

    private void cleanAccessRights(ClientType client, ServiceDescriptionType serviceDescriptionType) {
        Set<String> aclServiceCodesToRemove = serviceDescriptionType.getService()
                .stream()
                .map(ServiceType::getServiceCode)
                .collect(Collectors.toSet());
        client.getAcl().removeIf(accessRightType -> aclServiceCodesToRemove
                .contains(accessRightType.getEndpoint().getServiceCode()));
    }

    /**
     * Add a new WSDL ServiceDescription
     *
     * @param clientId
     * @param url
     * @param ignoreWarnings
     * @return created {@link ServiceDescriptionType}, with id populated
     * @throws ClientNotFoundException          if client with id was not found
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidWsdlException             if WSDL at the url was invalid
     * @throws UnhandledWarningsException       if there were warnings that were not ignored
     * @throws InvalidUrlException              if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException    conflict: another service description has same url
     * @throws ServiceAlreadyExistsException    conflict: same service exists in another SD
     * @throws InterruptedException             if the thread running the WSDL validator is interrupted. <b>The
     * interrupted thread has already been handled with so you can choose to ignore this exception if you so
     * please.</b>
     */
    public ServiceDescriptionType addWsdlServiceDescription(ClientId clientId, String url, boolean ignoreWarnings)
            throws InvalidWsdlException,
            WsdlParser.WsdlNotFoundException,
            ClientNotFoundException,
            UnhandledWarningsException,
            ServiceAlreadyExistsException,
            InvalidUrlException,
            WsdlUrlAlreadyExistsException, InterruptedException {
        ClientType client = clientService.getLocalClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + " not found");
        }

        WsdlProcessingResult wsdlProcessingResult = processWsdl(client, url, null);

        if (!ignoreWarnings && !wsdlProcessingResult.getWarnings().isEmpty()) {
            throw new UnhandledWarningsException(wsdlProcessingResult.getWarnings());
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

    /**
     * Create a new {@link EndpointType} for all Services in the provided {@link ServiceDescriptionType}.
     * If an equal EndpointType already exists for the provided {@link ClientType} it will not be returned
     *
     * @param client
     * @param newServiceDescription
     * @return Only the newly created EndpointTypes
     */
    private Collection<EndpointType> resolveNewEndpoints(ClientType client,
            ServiceDescriptionType newServiceDescription) {
        Map<String, EndpointType> endpointMap = new HashMap<>();

        // add all new endpoints into a hashmap with a combination key
        newServiceDescription.getService().forEach(serviceType -> {
            EndpointType endpointType = new EndpointType(serviceType.getServiceCode(), EndpointType.ANY_METHOD,
                    EndpointType.ANY_PATH, true);
            String endpointKey = createEndpointKey(endpointType);
            endpointMap.put(endpointKey, endpointType);
        });

        // remove all existing endpoints with an equal combination key from the map
        client.getEndpoint().forEach(endpointType -> {
            String endpointKey = createEndpointKey(endpointType);
            endpointMap.remove(endpointKey);
        });

        return endpointMap.values();
    }

    private String createEndpointKey(EndpointType endpointType) {
        return endpointType.getServiceCode() + endpointType.getMethod() + endpointType.getPath()
                + endpointType.isGenerated();
    }


    /**
     * Add openapi3 ServiceDescription
     *
     * @param clientId
     * @param url
     * @param serviceCode
     * @param ignoreWarnings
     * @return
     * @throws OpenApiParser.ParsingException    if parsing openapi3 description results in errors
     * @throws ClientNotFoundException           if client is not found with given id
     * @throws UnhandledWarningsException        if ignoreWarnings is false and parsing openapi3 description results
     *                                           in warnings
     * @throws UrlAlreadyExistsException         if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws MissingParameterException         if given ServiceCode is null
     * @throws InvalidUrlException               if url is invalid
     */
    @PreAuthorize("hasAuthority('ADD_OPENAPI3')")
    public ServiceDescriptionType addOpenApi3ServiceDescription(ClientId clientId, String url,
                                                                String serviceCode, boolean ignoreWarnings)
            throws OpenApiParser.ParsingException, ClientNotFoundException,
            UnhandledWarningsException,
            UrlAlreadyExistsException,
            ServiceCodeAlreadyExistsException,
            MissingParameterException, InvalidUrlException {

        if (serviceCode == null) {
            throw new MissingParameterException("Missing ServiceCode");
        }

        validateUrl(url);

        // Parse openapi definition
        OpenApiParser.Result result = openApiParser.parse(url);

        if (!ignoreWarnings && result.hasWarnings()) {
            WarningDeviation openapiParserWarnings = new WarningDeviation("OpenapiParserWarnings",
                    result.getWarnings());
            throw new UnhandledWarningsException(Arrays.asList(openapiParserWarnings));
        }

        ClientType client = clientService.getLocalClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + " not found");
        }

        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url,
                DescriptionType.OPENAPI3);

        // Initiate default service
        ServiceType serviceType = new ServiceType();
        serviceType.setServiceCode(serviceCode);
        serviceType.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        serviceType.setUrl(url);
        serviceType.setServiceDescription(serviceDescriptionType);

        // Populate ServiceDescription
        serviceDescriptionType.getService().add(serviceType);

        // Create endpoints
        EndpointType endpointType = new EndpointType(serviceCode, EndpointType.ANY_METHOD, EndpointType.ANY_PATH, true);
        List endpoints = new ArrayList<EndpointType>();
        endpoints.add(endpointType);
        endpoints.addAll(result.getOperations().stream()
                .map(operation -> new EndpointType(serviceCode, operation.getMethod(), operation.getPath(), true))
                .collect(Collectors.toList()));

        checkDuplicateUrl(serviceDescriptionType);
        checkDuplicateServiceCodes(serviceDescriptionType);

        // Populate client with new servicedescription and endpoints
        client.getEndpoint().addAll(endpoints);
        client.getServiceDescription().add(serviceDescriptionType);
        clientRepository.saveOrUpdateAndFlush(client);

        return serviceDescriptionType;
    }

    /**
     * Check whether the ServiceDescriptions url already exists in the linked Client
     *
     * @param serviceDescription
     * @throws UrlAlreadyExistsException if trying to add duplicate url
     */
    private void checkDuplicateUrl(ServiceDescriptionType serviceDescription) throws UrlAlreadyExistsException {
        Boolean hasDuplicates = serviceDescription.getClient().getServiceDescription().stream()
                .anyMatch(other -> !serviceDescription.equals(other)
                        && serviceDescription.getUrl().equals(other.getUrl()));

        if (hasDuplicates) {
            throw new UrlAlreadyExistsException(serviceDescription.getUrl());
        }
    }


    /**
     * Check whether the ServiceDescriptions ServiceCode already exists in the linked Client
     *
     * @param serviceDescription
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     */
    private void checkDuplicateServiceCodes(ServiceDescriptionType serviceDescription)
            throws ServiceCodeAlreadyExistsException {

        List<ServiceType> existingServices =
                getClientsExistingServices(serviceDescription.getClient(), serviceDescription.getId());

        Set<ServiceType> duplicateServices = serviceDescription.getService().stream()
                .filter(candidateService -> {
                    String candidateFullServiceCode = FormatUtils.getServiceFullName(candidateService);
                    boolean existsByServiceCode = existingServices.stream()
                            .map(s -> s.getServiceCode())
                            .anyMatch(serviceCode -> serviceCode.equalsIgnoreCase(candidateService.getServiceCode()));
                    boolean existsByFullServiceCode = existingServices.stream()
                            .map(s -> FormatUtils.getServiceFullName(s))
                            .anyMatch(fullServiceCode -> fullServiceCode.equalsIgnoreCase(candidateFullServiceCode));
                    return existsByFullServiceCode || existsByServiceCode;
                })
                .collect(Collectors.toSet());

        // throw error with service metadata if conflicted
        if (!duplicateServices.isEmpty()) {
            List<String> errorMetadata = new ArrayList();
            for (ServiceType service : duplicateServices) {
                // error metadata contains service name and service description url
                errorMetadata.add(FormatUtils.getServiceFullName(service));
                errorMetadata.add(service.getServiceDescription().getUrl());
            }
            throw new ServiceCodeAlreadyExistsException(errorMetadata);
        }

    }

    /**
     * Add a new REST ServiceDescription
     *
     * @param clientId
     * @param url
     * @param serviceCode
     * @return
     * @throws ClientNotFoundException           if client not found with given id
     * @throws MissingParameterException         if given ServiceCode is null
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws UrlAlreadyExistsException         if trying to add duplicate url
     * @throws InvalidUrlException               if url is invalid
     */
    @PreAuthorize("hasAuthority('ADD_OPENAPI3')")
    public ServiceDescriptionType addRestEndpointServiceDescription(ClientId clientId, String url,
            String serviceCode) throws
            ClientNotFoundException, MissingParameterException, ServiceCodeAlreadyExistsException,
            UrlAlreadyExistsException, InvalidUrlException {

        if (serviceCode == null) {
            throw new MissingParameterException("Missing ServiceCode");
        }

        validateUrl(url);

        ClientType client = clientService.getLocalClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + " not found");
        }

        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url,
                DescriptionType.REST);

        // Populate service
        ServiceType serviceType = new ServiceType();
        serviceType.setServiceCode(serviceCode);
        serviceType.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        serviceType.setUrl(url);
        serviceType.setServiceDescription(serviceDescriptionType);

        // Add created servicedescription to client
        serviceDescriptionType.getService().add(serviceType);
        client.getServiceDescription().add(serviceDescriptionType);

        // Add created endpoint to client
        EndpointType endpointType = new EndpointType(serviceCode, EndpointType.ANY_METHOD,
                EndpointType.ANY_PATH, true);
        client.getEndpoint().add(endpointType);

        checkDuplicateServiceCodes(serviceDescriptionType);
        checkDuplicateUrl(serviceDescriptionType);

        clientRepository.saveOrUpdateAndFlush(client);

        return serviceDescriptionType;
    }



    /**
     * Update the WSDL url of the selected ServiceDescription
     *
     * @param id
     * @param url the new url
     * @return ServiceDescriptionType
     * @throws WsdlParser.WsdlNotFoundException     if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException  if SD with given id was not found
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException                 if WSDL at the url was invalid
     * @throws UnhandledWarningsException           if there were warnings that were not ignored
     * @throws InvalidUrlException                  if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException        conflict: another service description has same url
     * @throws ServiceAlreadyExistsException        conflict: same service exists in another SD
     * @throws InterruptedException                 if the thread running the WSDL validator is interrupted. <b>The
     * interrupted thread has already been handled with so you can choose to ignore this exception if you so
     * please.</b>
     */
    public ServiceDescriptionType updateWsdlUrl(Long id, String url, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            ServiceDescriptionNotFoundException,
            WrongServiceDescriptionTypeException,
            UnhandledWarningsException,
            InvalidUrlException,
            ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, InterruptedException {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id " + id.toString());
        }
        return updateWsdlUrl(serviceDescriptionType, url, ignoreWarnings);
    }

    /**
     * Refresh Service Description
     *
     * @param id
     * @param ignoreWarnings
     * @return
     * @throws WsdlParser.WsdlNotFoundException     WSDL not found
     * @throws InvalidWsdlException                 Invalid wsdl
     * @throws ServiceDescriptionNotFoundException  service description is not found
     * @throws WrongServiceDescriptionTypeException wrong type of service description
     * @throws UnhandledWarningsException           Unhandledwarnings in openapi3 or wsdl description
     * @throws InvalidUrlException                  invalid url
     * @throws ServiceAlreadyExistsException        service code already exists if refreshing wsdl
     * @throws WsdlUrlAlreadyExistsException        url is already in use by this client
     * @throws OpenApiParser.ParsingException       openapi3 description parsing fails
     */
    public ServiceDescriptionType refreshServiceDescription(Long id, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            ServiceDescriptionNotFoundException, WrongServiceDescriptionTypeException,
            UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, OpenApiParser.ParsingException, InterruptedException {

        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id "
                    + serviceDescriptionType.toString() + " not found");
        }

        if (serviceDescriptionType.getType().equals(DescriptionType.WSDL)) {
            serviceDescriptionType = refreshWSDLServiceDescription(serviceDescriptionType, ignoreWarnings);
        } else if (serviceDescriptionType.getType().equals(DescriptionType.OPENAPI3)) {
            serviceDescriptionType = refreshOpenApi3ServiceDescription(serviceDescriptionType, ignoreWarnings);
        }

        return serviceDescriptionType;
    }

    /**
     * Refresh a ServiceDescription
     *
     * @param serviceDescriptionType
     * @param ignoreWarnings
     * @return {@link ServiceDescriptionType}
     * @throws WsdlParser.WsdlNotFoundException     if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException  if SD with given id was not found
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException                 if WSDL at the url was invalid
     * @throws UnhandledWarningsException           if there were warnings that were not ignored
     * @throws InvalidUrlException                  if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException        conflict: another service description has same url
     * @throws ServiceAlreadyExistsException        conflict: same service exists in another SD
     * @throws InterruptedException                 if the thread running the WSDL validator is interrupted. <b>The
     * interrupted thread has already been handled with so you can choose to ignore this exception if you so
     * please.</b>
     */
    @PreAuthorize("hasAuthority('REFRESH_WSDL')")
    private ServiceDescriptionType refreshWSDLServiceDescription(ServiceDescriptionType serviceDescriptionType,
            boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            WrongServiceDescriptionTypeException,
            UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, InterruptedException {

        if (!serviceDescriptionType.getType().equals(DescriptionType.WSDL)) {
            throw new WrongServiceDescriptionTypeException("Expected description type WSDL");
        }

        if (serviceDescriptionType.getType() == DescriptionType.WSDL) {
            String wsdlUrl = serviceDescriptionType.getUrl();
            return updateWsdlUrl(serviceDescriptionType, wsdlUrl, ignoreWarnings);
        }

        // we only have two types at the moment so the type must be OPENAPI3 if we end up this far
        throw new NotImplementedException("REST ServiceDescription refresh not implemented yet");
    }

    /**
     * Refresh OPENAPI3 ServiceDescription
     *
     * @param serviceDescriptionType
     * @param ignoreWarnings
     * @return {@link ServiceDescriptionType}
     * @throws WrongServiceDescriptionTypeException if service type is not openapi3
     * @throws UnhandledWarningsException           if unhandled warnings are found and ignoreWarnings if false
     * @throws OpenApiParser.ParsingException       if parsing openapi3 description fails
     * @throws InvalidUrlException                  if url is invalid
     */
    @PreAuthorize("hasAuthority('REFRESH_OPENAPI3')")
    private ServiceDescriptionType refreshOpenApi3ServiceDescription(ServiceDescriptionType serviceDescriptionType,
            boolean ignoreWarnings) throws WrongServiceDescriptionTypeException,
            UnhandledWarningsException, OpenApiParser.ParsingException, InvalidUrlException {

        if (!serviceDescriptionType.getType().equals(DescriptionType.OPENAPI3)) {
            throw new WrongServiceDescriptionTypeException("Expected description type OPENAPI3");
        }

        if (serviceDescriptionType.getService().get(0) == null) {
            throw new DeviationAwareRuntimeException(SERVICE_NOT_FOUND_ERROR_MSG + serviceDescriptionType.getId());
        }

        validateUrl(serviceDescriptionType.getUrl());

        serviceDescriptionType.setRefreshedDate(new Date());

        parseOpenApi3ToServiceDescription(serviceDescriptionType.getUrl(),
                serviceDescriptionType.getService().get(0).getServiceCode(),
                ignoreWarnings,
                serviceDescriptionType);

        clientRepository.saveOrUpdateAndFlush(serviceDescriptionType.getClient());

        return serviceDescriptionType;
    }

    /**
     * Update Rest service description
     *
     * @param id
     * @param url
     * @param restServiceCode
     * @param newRestServiceCode
     * @return {@link ServiceDescriptionType}
     * @throws UrlAlreadyExistsException           if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException   if trying to add duplicate ServiceCode
     * @throws ServiceDescriptionNotFoundException if ServiceDescription not found
     * @throws InvalidUrlException                 if url is invalid
     */
    @PreAuthorize("hasAuthority('EDIT_REST')")
    public ServiceDescriptionType updateRestServiceDescription(Long id, String url, String restServiceCode,
            String newRestServiceCode)
            throws UrlAlreadyExistsException, ServiceCodeAlreadyExistsException, ServiceDescriptionNotFoundException,
            WrongServiceDescriptionTypeException, InvalidUrlException {

        if (newRestServiceCode == null) {
            newRestServiceCode = restServiceCode;
        }

        ServiceDescriptionType serviceDescription = getServiceDescriptiontype(id);
        if (!serviceDescription.getType().equals(DescriptionType.REST)) {
            throw new WrongServiceDescriptionTypeException("Expected description type REST");
        }

        validateUrl(serviceDescription.getUrl());

        if (serviceDescription == null) {
            throw new ServiceDescriptionNotFoundException("ServiceDescription with id: " + id + " wasn't found");
        }

        if (serviceDescription.getService().get(0) == null) {
            throw new DeviationAwareRuntimeException(SERVICE_NOT_FOUND_ERROR_MSG + serviceDescription.getId());
        }

        serviceDescription.setRefreshedDate(new Date());
        serviceDescription.setUrl(url);
        serviceDescription.getService().get(0).setUrl(url);

        updateServiceCodes(restServiceCode, newRestServiceCode, serviceDescription);

        checkDuplicateServiceCodes(serviceDescription);
        checkDuplicateUrl(serviceDescription);

        clientRepository.saveOrUpdateAndFlush(serviceDescription.getClient());
        return serviceDescription;
    }

    /**
     * Update OpenApi3 ServiceDescription
     *
     * @param id
     * @param url
     * @param restServiceCode
     * @param newRestServiceCode
     * @param ignoreWarnings
     * @return
     * @throws UrlAlreadyExistsException         if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws UnhandledWarningsException        if ignoreWarnings false and warning-level issues in openapi3
     *                                           description
     * @throws OpenApiParser.ParsingException    if openapi3 parser finds errors in the parsed document
     * @throws InvalidUrlException               if url is invalid
     */
    @PreAuthorize("hasAuthority('EDIT_OPENAPI3')")
    public ServiceDescriptionType updateOpenApi3ServiceDescription(Long id, String url, String restServiceCode,
            String newRestServiceCode, Boolean ignoreWarnings) throws UrlAlreadyExistsException,
            ServiceCodeAlreadyExistsException, UnhandledWarningsException, OpenApiParser.ParsingException,
            WrongServiceDescriptionTypeException, ServiceDescriptionNotFoundException,
            InvalidUrlException {

        ServiceDescriptionType serviceDescription = getServiceDescriptiontype(id);

        if (serviceDescription == null) {
            throw new ServiceDescriptionNotFoundException("ServiceDescription with id: " + id + " wasn't found");
        }

        if (!serviceDescription.getType().equals(DescriptionType.OPENAPI3)) {
            throw new WrongServiceDescriptionTypeException("Expected description type OPENAPI3");
        }

        validateUrl(url);

        if (newRestServiceCode == null) {
            newRestServiceCode = restServiceCode;
        }

        if (serviceDescription.getService().get(0) == null) {
            throw new DeviationAwareRuntimeException(SERVICE_NOT_FOUND_ERROR_MSG + serviceDescription.getId());
        }

        updateServiceCodes(restServiceCode, newRestServiceCode, serviceDescription);

        // Parse openapi definition and handle updating endpoints and acls
        if (!serviceDescription.getUrl().equals(url)) {
            parseOpenApi3ToServiceDescription(url, newRestServiceCode, ignoreWarnings, serviceDescription);
        }

        serviceDescription.setRefreshedDate(new Date());
        serviceDescription.setUrl(url);
        serviceDescription.getService().get(0).setUrl(url);

        checkDuplicateServiceCodes(serviceDescription);
        checkDuplicateUrl(serviceDescription);

        clientRepository.saveOrUpdateAndFlush(serviceDescription.getClient());

        return serviceDescription;
    }

    /**
     * Parse OpenApi3 description and update endpoints and acls in ServiceDescription accordingly
     *
     * @param url
     * @param serviceCode
     * @param ignoreWarnings
     * @param serviceDescription
     * @throws OpenApiParser.ParsingException if there are errors in the openapi3 description document
     * @throws UnhandledWarningsException     if ignoreWarnings is false and parser returns warnings from openapi
     */
    private void parseOpenApi3ToServiceDescription(String url, String serviceCode, boolean ignoreWarnings,
            ServiceDescriptionType serviceDescription) throws
            OpenApiParser.ParsingException, UnhandledWarningsException {
        OpenApiParser.Result result = openApiParser.parse(url);
        if (!ignoreWarnings && result.hasWarnings()) {
            WarningDeviation openapiParserWarnings = new WarningDeviation("OpenapiParserWarnings",
                    result.getWarnings());
            throw new UnhandledWarningsException(Arrays.asList(openapiParserWarnings));
        }

        // Update url
        updateServiceDescriptionUrl(serviceDescription, serviceCode, url);

        // Create endpoints from parsed results
        List<EndpointType> parsedEndpoints = result.getOperations().stream()
                .map(operation -> new EndpointType(serviceCode, operation.getMethod(), operation.getPath(),
                        true))
                .collect(Collectors.toList());
        parsedEndpoints.add(new EndpointType(serviceCode, "*", "**", true));

        // Change existing, manually added, endpoints to generated if they're found from parsedEndpoints
        serviceDescription.getClient().getEndpoint().forEach(ep -> {
            if (parsedEndpoints.stream().anyMatch(parsedEp -> parsedEp.isEquivalent(ep))) {
                ep.setGenerated(true);
            }
        });

        // Remove ACLs that don't exist in the parsed endpoints list
        serviceDescription.getClient().getAcl().removeIf(acl ->
                acl.getEndpoint().isGenerated()
                        && parsedEndpoints.stream().noneMatch(endpoint -> acl.getEndpoint().isEquivalent(endpoint)));


        // Remove generated endpoints that are not found from the parsed endpoints
        serviceDescription.getClient().getEndpoint().removeIf(ep -> {
            return ep.isGenerated() && parsedEndpoints.stream()
                    .noneMatch(parsedEp -> parsedEp.isEquivalent(ep));
        });

        // Add parsed endpoints to endpoints list if it is not already there
        serviceDescription.getClient().getEndpoint().addAll(
                parsedEndpoints.stream()
                        .filter(parsedEp -> serviceDescription.getClient().getEndpoint()
                                .stream()
                                .noneMatch(ep -> ep.isEquivalent(parsedEp)))
                        .collect(Collectors.toList()));
    }

    /**
     * Updates the ServiceCodes of Endpoints and Service linked to given ServiceDescription
     *
     * @param serviceCode
     * @param newserviceCode
     * @param serviceDescriptiontype
     */
    private void updateServiceCodes(String serviceCode, String newserviceCode,
            ServiceDescriptionType serviceDescriptiontype) {
        // Update endpoint service codes
        ClientType client = serviceDescriptiontype.getClient();
        client.getEndpoint().stream()
                .filter(e -> e.getServiceCode().equals(serviceCode))
                .forEach(e -> e.setServiceCode(newserviceCode));

        // Update service service code
        ServiceType service = serviceDescriptiontype.getService().stream()
                .filter(s -> serviceCode.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow(() -> new DeviationAwareRuntimeException("Service with servicecode: " + serviceCode
                        + " wasn't found from servicedescription with id: " + serviceDescriptiontype.getId()));
        service.setServiceCode(newserviceCode);
    }

    /**
     * Updates the url of the given ServiceDescription and service attached to it with matching ServiceCode to one given
     *
     * @param serviceDescriptionType
     * @param serviceCode
     * @param url
     */
    private void updateServiceDescriptionUrl(ServiceDescriptionType serviceDescriptionType, String serviceCode,
            String url) {
        serviceDescriptionType.setUrl(url);
        ServiceType service = serviceDescriptionType.getService().stream()
                .filter(s -> serviceCode.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow(() -> new DeviationAwareRuntimeException("Service with servicecode: " + serviceCode
                        + " wasn't found from servicedescription with id: " + serviceDescriptionType.getId()));
        service.setUrl(url);
    }

    /**
     * Return matching ServiceDescription or null.
     * serviceDescription.services and serviceDescription.client are always loaded
     * with Hibernate.init()
     *
     * @param id
     * @return ServiceDescriptionType
     */
    public ServiceDescriptionType getServiceDescriptiontype(Long id) {
        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionType != null) {
            Hibernate.initialize(serviceDescriptionType.getService());
            Hibernate.initialize(serviceDescriptionType.getClient().getEndpoint());
        }
        return serviceDescriptionType;
    }

    /**
     * Update the WSDL url of the selected ServiceDescription.
     * Refreshing a WSDL is also an update of wsdl,
     * it just updates to the same URL value
     *
     * @param serviceDescriptionType
     * @param url                    the new url
     * @return ServiceDescriptionType
     */
    private ServiceDescriptionType updateWsdlUrl(ServiceDescriptionType serviceDescriptionType, String url,
            boolean ignoreWarnings)
            throws InvalidWsdlException, WsdlParser.WsdlNotFoundException,
            WrongServiceDescriptionTypeException, UnhandledWarningsException,
            ServiceAlreadyExistsException, InvalidUrlException, WsdlUrlAlreadyExistsException, InterruptedException {

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

        List<String> newServiceCodes = newServices
                .stream()
                .map(ServiceType::getServiceCode)
                .collect(Collectors.toList());

        // service codes that will be REMOVED
        List<String> removedServiceCodes = serviceChanges.getRemovedServices()
                .stream()
                .map(ServiceType::getServiceCode)
                .collect(Collectors.toList());

        // replace all old services with the new ones
        serviceDescriptionType.getService().clear();
        serviceDescriptionType.getService().addAll(newServices);

        // clear AccessRights that belong to non-existing services
        client.getAcl().removeIf(accessRightType -> {
            String serviceCode = accessRightType.getEndpoint().getServiceCode();
            return removedServiceCodes.contains(serviceCode) && !newServiceCodes.contains(serviceCode);
        });

        // remove related endpoints
        client.getEndpoint().removeIf(endpointType -> removedServiceCodes.contains(endpointType.getServiceCode()));

        // add new endpoints
        Collection<EndpointType> endpointsToAdd = resolveNewEndpoints(client, serviceDescriptionType);
        client.getEndpoint().addAll(endpointsToAdd);

        clientRepository.saveOrUpdate(client);

        return serviceDescriptionType;
    }

    /**
     * @return warnings about adding or deleting services
     */
    private List<WarningDeviation> createServiceChangeWarnings(ServiceChangeChecker.ServiceChanges changes) {
        List<WarningDeviation> warnings = new ArrayList<>();
        if (!CollectionUtils.isEmpty(changes.getAddedServices())) {
            WarningDeviation addedServicesWarning = new WarningDeviation(WARNING_ADDING_SERVICES,
                    changes.getAddedFullServiceCodes());
            warnings.add(addedServicesWarning);
        }
        if (!CollectionUtils.isEmpty(changes.getRemovedServices())) {
            WarningDeviation deletedServicesWarning = new WarningDeviation(WARNING_DELETING_SERVICES,
                    changes.getRemovedFullServiceCodes());
            warnings.add(deletedServicesWarning);
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
            Collection<WsdlParser.ServiceInfo> parsedServices,
            String url) {
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
     *
     * @param url
     * @return list of validation warnings that can be ignored by choice
     * @throws WsdlValidator.WsdlValidationFailedException
     * @throws WsdlValidator.WsdlValidatorNotExecutableException
     */
    private List<String> validateWsdl(String url) throws WsdlValidator.WsdlValidationFailedException,
            WsdlValidator.WsdlValidatorNotExecutableException, InterruptedException {
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
            for (ServiceType conflictedService : conflictedServices) {
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

    // check for valid url (is this not enough??
    private void validateUrl(String url) throws InvalidUrlException {
        if (!urlValidator.isValidUrl(url)) {
            throw new InvalidUrlException("Malformed URL");
        }
    }

    /**
     * Parse and validate a given wsdl and detect problems it may have.
     * Fatal problems result in thrown exception, warnings are returned in
     * WsdlProcessingResult
     *
     * @param client                      client who is associated with the wsdl
     * @param url                         url of the wsdl
     * @param updatedServiceDescriptionId id of the service description we
     *                                    will update with this wsdl, or null
     *                                    if we're adding a new one
     * @return parsed and validated wsdl and possible warnings
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidUrlException              if url was empty or invalid
     * @throws InvalidWsdlException             if wsdl was invalid (either parsing or validation)
     * @throws WsdlUrlAlreadyExistsException    conflict: another service description has same url
     * @throws ServiceAlreadyExistsException    conflict: same service exists in another SD
     */
    private WsdlProcessingResult processWsdl(ClientType client, String url,
            Long updatedServiceDescriptionId)
            throws WsdlParser.WsdlNotFoundException,
            InvalidWsdlException,
            InvalidUrlException,
            WsdlUrlAlreadyExistsException,
            ServiceAlreadyExistsException, InterruptedException {

        WsdlProcessingResult result = new WsdlProcessingResult();

        validateUrl(url);

        // check if wsdl already exists
        checkForExistingWsdl(client, url, updatedServiceDescriptionId);

        // parse wsdl
        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check that service identifiers are legal
        validateServiceIdentifierFields(parsedServices);

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
     * validate that all services have legal service code (name) and version
     * @throws InvalidServiceIdentifierException if there was at least one
     * invalid service code or version
     */
    private void validateServiceIdentifierFields(Collection<WsdlParser.ServiceInfo> serviceInfos)
            throws InvalidServiceIdentifierException {
        List<String> invalidIdentifierFields = new ArrayList<>();
        EncodedIdentifierValidator validator = new EncodedIdentifierValidator();
        for (WsdlParser.ServiceInfo serviceInfo: serviceInfos) {
            String serviceCode = serviceInfo.name;
            String version = serviceInfo.version;
            if (!validator.getValidationErrors(serviceCode).isEmpty()) {
                invalidIdentifierFields.add(serviceCode);
            }
            if (!validator.getValidationErrors(version).isEmpty()) {
                invalidIdentifierFields.add(version);
            }
        }
        if (!invalidIdentifierFields.isEmpty()) {
            throw new InvalidServiceIdentifierException(invalidIdentifierFields);
        }
    }

    /**
     * If wsdl had service codes and / or versions with illegal identifier values, such as colons
     */
    public static class InvalidServiceIdentifierException extends InvalidWsdlException {
        public static final String ERROR_INVALID_SERVICE_IDENTIFIER = "invalid_wsdl_service_identifier";

        public InvalidServiceIdentifierException(List<String> invalidIdentifiers) {
            super(new ErrorDeviation(ERROR_INVALID_SERVICE_IDENTIFIER, invalidIdentifiers));
        }
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

    public static class UrlAlreadyExistsException extends ServiceException {

        public static final String ERROR_EXISTING_URL = "url_already_exists";

        public UrlAlreadyExistsException(String s) {
            super(new ErrorDeviation(ERROR_EXISTING_URL, s));
        }
    }

    public static class ServiceCodeAlreadyExistsException extends ServiceException {

        public static final String ERROR_EXISTING_SERVICE_CODE = "service_code_already_exists";

        public ServiceCodeAlreadyExistsException(List<String> metadata) {
            super(new ErrorDeviation(ERROR_EXISTING_SERVICE_CODE, metadata));
        }
    }
}
