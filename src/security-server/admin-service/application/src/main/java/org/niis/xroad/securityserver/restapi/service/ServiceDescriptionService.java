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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.validation.IdentifierValidator;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.securityserver.restapi.util.EndpointHelper;
import org.niis.xroad.securityserver.restapi.util.SecurityServerFormatUtils;
import org.niis.xroad.securityserver.restapi.wsdl.InvalidWsdlException;
import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.niis.xroad.securityserver.restapi.wsdl.UnsupportedOpenApiVersionException;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlParser;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlValidator;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_EXISTING_SERVICE_CODE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_EXISTING_URL;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_SERVICE_IDENTIFIER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVICE_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WRONG_TYPE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_ADDING_SERVICES;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_DELETING_SERVICES;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_OPENAPI_VALIDATION_WARNINGS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_WSDL_VALIDATION_WARNINGS;

/**
 * ServiceDescription service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ServiceDescriptionService {

    public static final int DEFAULT_SERVICE_TIMEOUT = 60;
    public static final String DEFAULT_DISABLED_NOTICE = "Out of order";
    public static final String SERVICE_NOT_FOUND_ERROR_MSG = "Service not found from servicedescription with id ";
    public static final String CLIENT_WITH_ID = "Client with id";
    public static final String NOT_FOUND = " not found";

    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final ClientService clientService;
    private final ServiceChangeChecker serviceChangeChecker;
    private final ServiceService serviceService;
    private final WsdlValidator wsdlValidator;
    private final UrlValidator urlValidator;
    private final OpenApiParser openApiParser;
    private final AuditDataHelper auditDataHelper;
    private final EndpointHelper endpointHelper;
    private final IdentifierValidator identifierValidator;

    /**
     * Disable 1 services
     *
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    public void disableServices(long serviceDescriptionId,
            String disabledNotice) throws ServiceDescriptionNotFoundException {
        toggleServices(false, serviceDescriptionId, disabledNotice);
    }

    /**
     * Enable 1 service
     *
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    public void enableServices(long serviceDescriptionId) throws ServiceDescriptionNotFoundException {
        toggleServices(true, serviceDescriptionId, null);
    }

    /**
     * Change 1-n services to enabled/disabled
     *
     * @param serviceDescriptionId
     * @param disabledNotice
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    private void toggleServices(boolean toEnabled, long serviceDescriptionId,
            String disabledNotice) throws ServiceDescriptionNotFoundException {

        if (!toEnabled) {
            auditDataHelper.put(RestApiAuditProperty.DISABLED_NOTICE, disabledNotice);
        }

        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository
                .getServiceDescription(serviceDescriptionId);

        if (serviceDescriptionType == null) {
            throw createServiceDescriptionNotFoundException(serviceDescriptionId);
        }

        serviceDescriptionType.setDisabled(!toEnabled);
        if (!toEnabled) {
            serviceDescriptionType.setDisabledNotice(disabledNotice);
        }
        auditDataHelper.put(serviceDescriptionType.getClient().getIdentifier());
        auditDataHelper.putServiceDescriptionUrl(serviceDescriptionType);
    }

    private ServiceDescriptionNotFoundException createServiceDescriptionNotFoundException(long serviceDescriptionId) {
        return new ServiceDescriptionNotFoundException("Service description with id "
                + serviceDescriptionId
                + NOT_FOUND);
    }

    /**
     * Delete one ServiceDescription
     *
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given id was not found
     */
    public void deleteServiceDescription(Long id) throws ServiceDescriptionNotFoundException {
        ServiceDescriptionType serviceDescriptionType = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionType == null) {
            throw createServiceDescriptionNotFoundException(id);
        }
        auditDataHelper.putServiceDescriptionUrl(serviceDescriptionType);
        ClientType client = serviceDescriptionType.getClient();
        auditDataHelper.put(client.getIdentifier());
        cleanAccessRights(client, serviceDescriptionType);
        cleanEndpoints(client, serviceDescriptionType);
        client.getServiceDescription().remove(serviceDescriptionType);
    }

    private void cleanEndpoints(ClientType client, ServiceDescriptionType serviceDescriptionType) {
        Set<String> servicesToRemove = serviceDescriptionType.getService()
                .stream()
                .map(ServiceType::getServiceCode)
                .filter(isServiceUniqueToCurrentDescription(client, serviceDescriptionType))
                .collect(Collectors.toSet());
        client.getEndpoint().removeIf(endpointType -> servicesToRemove.contains(endpointType.getServiceCode()));
    }

    private void cleanAccessRights(ClientType client, ServiceDescriptionType serviceDescriptionType) {
        Set<String> aclServiceCodesToRemove = serviceDescriptionType.getService()
                .stream()
                .map(ServiceType::getServiceCode)
                .filter(isServiceUniqueToCurrentDescription(client, serviceDescriptionType))
                .collect(Collectors.toSet());
        client.getAcl().removeIf(accessRightType -> aclServiceCodesToRemove
                .contains(accessRightType.getEndpoint().getServiceCode()));
    }

    private Predicate<String> isServiceUniqueToCurrentDescription(ClientType client, ServiceDescriptionType current) {
        return (String serviceCode) -> client.getServiceDescription().stream().filter(
                        sd -> !sd.getId().equals(current.getId()))
                .flatMap(sd -> sd.getService().stream())
                .map(ServiceType::getServiceCode)
                .noneMatch(Predicate.isEqual(serviceCode));
    }

    /**
     * Add a new WSDL ServiceDescription
     *
     * @param clientId
     * @param url
     * @param ignoreWarnings
     * @return created {@link ServiceDescriptionType}, with id populated
     * @throws ClientNotFoundException if client with id was not found
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws InvalidServiceUrlException if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     * @throws InterruptedException if the thread running the WSDL validator is interrupted. <b>The
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
            WsdlUrlAlreadyExistsException, InterruptedException, InvalidServiceUrlException {
        ClientType client = clientService.getLocalClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + NOT_FOUND);
        }

        WsdlProcessingResult wsdlProcessingResult = processWsdl(client, url, null);

        validateServiceUrls(wsdlProcessingResult.getParsedServices());

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
        return serviceDescriptionType;
    }

    /**
     * Validate that all service URLs begin with HTTP or HTTPS. This should be checked only when ADDING a new WSDL
     * @param parsedServices
     * @throws InvalidServiceUrlException if one or more URLs do not start with HTTP or HTTPS
     */
    private void validateServiceUrls(Collection<WsdlParser.ServiceInfo> parsedServices) throws
            InvalidServiceUrlException {
        final List<String> invalidUrls = new ArrayList<>();
        parsedServices.forEach(serviceInfo -> {
            if (serviceInfo.url != null && !serviceInfo.url.startsWith(FormatUtils.HTTP_PROTOCOL)
                    && !serviceInfo.url.startsWith(FormatUtils.HTTPS_PROTOCOL)) {
                invalidUrls.add(serviceInfo.url);
            }
        });
        if (!invalidUrls.isEmpty()) {
            throw new InvalidServiceUrlException(invalidUrls);
        }
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
     * @throws OpenApiParser.ParsingException if parsing openapi3 description results in errors
     * @throws ClientNotFoundException if client is not found with given id
     * @throws UnhandledWarningsException if ignoreWarnings is false and parsing openapi3 description results
     * in warnings
     * @throws UrlAlreadyExistsException if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws MissingParameterException if given ServiceCode is null
     * @throws InvalidUrlException if url is invalid'
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    @PreAuthorize("hasAuthority('ADD_OPENAPI3')")
    public ServiceDescriptionType addOpenApi3ServiceDescription(ClientId clientId, String url,
            String serviceCode, boolean ignoreWarnings)
            throws OpenApiParser.ParsingException, ClientNotFoundException,
            UnhandledWarningsException,
            UrlAlreadyExistsException,
            ServiceCodeAlreadyExistsException,
            MissingParameterException, InvalidUrlException, UnsupportedOpenApiVersionException {

        if (serviceCode == null) {
            throw new MissingParameterException("Missing ServiceCode");
        }

        validateUrl(url);

        // Parse openapi definition
        OpenApiParser.Result result = openApiParser.parse(url);

        if (!ignoreWarnings && result.hasWarnings()) {
            WarningDeviation openapiParserWarnings = new WarningDeviation(WARNING_OPENAPI_VALIDATION_WARNINGS,
                    result.getWarnings());
            throw new UnhandledWarningsException(Arrays.asList(openapiParserWarnings));
        }

        ClientType client = clientService.getLocalClient(clientId);
        if (client == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + NOT_FOUND);
        }

        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url,
                DescriptionType.OPENAPI3);

        // Initiate default service
        ServiceType serviceType = new ServiceType();
        serviceType.setServiceCode(serviceCode);
        serviceType.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        serviceType.setUrl(result.getBaseUrl());
        serviceType.setServiceDescription(serviceDescriptionType);

        // Populate ServiceDescription
        serviceDescriptionType.getService().add(serviceType);

        // Create endpoints
        EndpointType endpointType = new EndpointType(serviceCode, EndpointType.ANY_METHOD, EndpointType.ANY_PATH, true);
        List<EndpointType> endpoints = new ArrayList<>();
        endpoints.add(endpointType);
        endpoints.addAll(result.getOperations().stream()
                .map(operation -> new EndpointType(serviceCode, operation.getMethod(), operation.getPath(), true))
                .collect(Collectors.toList()));

        checkDuplicateUrl(serviceDescriptionType);
        checkDuplicateServiceCodes(serviceDescriptionType);

        // Populate client with new servicedescription and endpoints
        client.getEndpoint().addAll(endpoints);
        client.getServiceDescription().add(serviceDescriptionType);

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
     * @throws ClientNotFoundException if client not found with given id
     * @throws MissingParameterException if given ServiceCode is null
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws UrlAlreadyExistsException if trying to add duplicate url
     * @throws InvalidUrlException if url is invalid
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
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + NOT_FOUND);
        }

        ServiceDescriptionType serviceDescriptionType = getServiceDescriptionOfType(client, url,
                DescriptionType.REST);

        // Populate service
        ServiceType serviceType = new ServiceType();
        serviceType.setServiceCode(serviceCode);
        serviceType.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        serviceType.setUrl(url);
        serviceType.setServiceDescription(serviceDescriptionType);
        if (FormatUtils.isHttpsUrl(url)) {
            serviceType.setSslAuthentication(true);
        }

        // Add created servicedescription to client
        serviceDescriptionType.getService().add(serviceType);
        client.getServiceDescription().add(serviceDescriptionType);

        // Add created endpoint to client
        EndpointType endpointType = new EndpointType(serviceCode, EndpointType.ANY_METHOD,
                EndpointType.ANY_PATH, true);
        client.getEndpoint().add(endpointType);

        checkDuplicateServiceCodes(serviceDescriptionType);
        checkDuplicateUrl(serviceDescriptionType);

        return serviceDescriptionType;
    }

    /**
     * Update the WSDL url of the selected ServiceDescription
     *
     * @param id
     * @param url the new url
     * @return ServiceDescriptionType
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException if SD with given id was not found
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws InvalidServiceUrlException if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     * @throws InterruptedException if the thread running the WSDL validator is interrupted. <b>The
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
            WsdlUrlAlreadyExistsException, InterruptedException, InvalidServiceUrlException {
        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw createServiceDescriptionNotFoundException(id);
        }
        return updateWsdlUrl(serviceDescriptionType, url, ignoreWarnings);
    }

    /**
     * Refresh Service Description
     *
     * @param id
     * @param ignoreWarnings
     * @return
     * @throws WsdlParser.WsdlNotFoundException WSDL not found
     * @throws InvalidWsdlException Invalid wsdl
     * @throws ServiceDescriptionNotFoundException service description is not found
     * @throws WrongServiceDescriptionTypeException wrong type of service description
     * @throws UnhandledWarningsException Unhandledwarnings in openapi3 or wsdl description
     * @throws InvalidUrlException invalid url
     * @throws InvalidServiceUrlException if the WSDL has services with invalid urls
     * @throws ServiceAlreadyExistsException service code already exists if refreshing wsdl
     * @throws WsdlUrlAlreadyExistsException url is already in use by this client
     * @throws OpenApiParser.ParsingException openapi3 description parsing fails
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    public ServiceDescriptionType refreshServiceDescription(Long id, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            ServiceDescriptionNotFoundException, WrongServiceDescriptionTypeException,
            UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, OpenApiParser.ParsingException, InterruptedException,
            InvalidServiceUrlException, UnsupportedOpenApiVersionException {

        ServiceDescriptionType serviceDescriptionType = getServiceDescriptiontype(id);
        if (serviceDescriptionType == null) {
            throw createServiceDescriptionNotFoundException(id);
        }

        auditDataHelper.put(serviceDescriptionType.getClient().getIdentifier());
        auditDataHelper.putServiceDescriptionUrl(serviceDescriptionType);

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
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException if SD with given id was not found
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws InvalidServiceUrlException if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     * @throws InterruptedException if the thread running the WSDL validator is interrupted. <b>The
     * interrupted thread has already been handled with so you can choose to ignore this exception if you so
     * please.</b>
     */
    @PreAuthorize("hasAuthority('REFRESH_WSDL')")
    private ServiceDescriptionType refreshWSDLServiceDescription(ServiceDescriptionType serviceDescriptionType,
            boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            WrongServiceDescriptionTypeException,
            UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, InterruptedException, InvalidServiceUrlException {

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
     * @throws UnhandledWarningsException if unhandled warnings are found and ignoreWarnings if false
     * @throws OpenApiParser.ParsingException if parsing openapi3 description fails
     * @throws InvalidUrlException if url is invalid
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    @PreAuthorize("hasAuthority('REFRESH_OPENAPI3')")
    private ServiceDescriptionType refreshOpenApi3ServiceDescription(ServiceDescriptionType serviceDescriptionType,
            boolean ignoreWarnings) throws WrongServiceDescriptionTypeException,
            UnhandledWarningsException, OpenApiParser.ParsingException, InvalidUrlException,
            UnsupportedOpenApiVersionException {

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
     * @throws UrlAlreadyExistsException if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws ServiceDescriptionNotFoundException if ServiceDescription not found
     * @throws InvalidUrlException if url is invalid
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
        auditDataHelper.put(serviceDescription.getClient().getIdentifier());
        auditDataHelper.putServiceDescriptionUrl(serviceDescription);
        auditDataHelper.put(RestApiAuditProperty.URL_NEW, url);
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
     * @throws UrlAlreadyExistsException if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws UnhandledWarningsException if ignoreWarnings false and warning-level issues in openapi3
     * description
     * @throws OpenApiParser.ParsingException if openapi3 parser finds errors in the parsed document
     * @throws InvalidUrlException if url is invalid
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    @PreAuthorize("hasAuthority('EDIT_OPENAPI3')")
    public ServiceDescriptionType updateOpenApi3ServiceDescription(Long id, String url, String restServiceCode,
            String newRestServiceCode, Boolean ignoreWarnings) throws UrlAlreadyExistsException,
            ServiceCodeAlreadyExistsException, UnhandledWarningsException, OpenApiParser.ParsingException,
            WrongServiceDescriptionTypeException, ServiceDescriptionNotFoundException,
            InvalidUrlException, UnsupportedOpenApiVersionException {

        ServiceDescriptionType serviceDescription = getServiceDescriptiontype(id);

        if (serviceDescription == null) {
            throw new ServiceDescriptionNotFoundException("ServiceDescription with id: " + id + " wasn't found");
        }

        auditDataHelper.put(serviceDescription.getClient().getIdentifier());
        auditDataHelper.putServiceDescriptionUrl(serviceDescription);
        auditDataHelper.put(RestApiAuditProperty.URL_NEW, url);

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
        parseOpenApi3ToServiceDescription(url, newRestServiceCode, ignoreWarnings, serviceDescription);

        serviceDescription.setRefreshedDate(new Date());
        serviceDescription.setUrl(url);

        checkDuplicateServiceCodes(serviceDescription);
        checkDuplicateUrl(serviceDescription);

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
     * @throws UnhandledWarningsException if ignoreWarnings is false and parser returns warnings from openapi
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    private void parseOpenApi3ToServiceDescription(String url, String serviceCode, boolean ignoreWarnings,
            ServiceDescriptionType serviceDescription) throws
            OpenApiParser.ParsingException, UnhandledWarningsException, UnsupportedOpenApiVersionException {
        OpenApiParser.Result result = openApiParser.parse(url);
        if (!ignoreWarnings && result.hasWarnings()) {
            WarningDeviation openapiParserWarnings = new WarningDeviation(WARNING_OPENAPI_VALIDATION_WARNINGS,
                    result.getWarnings());
            throw new UnhandledWarningsException(Arrays.asList(openapiParserWarnings));
        }

        List<EndpointType> oldServiceDescriptionEndpoints = endpointHelper.getEndpoints(serviceDescription);

        // Create endpoints from parsed results
        List<EndpointType> parsedEndpoints = result.getOperations().stream()
                .map(operation -> new EndpointType(serviceCode, operation.getMethod(), operation.getPath(),
                        true))
                .collect(Collectors.toList());
        parsedEndpoints.add(new EndpointType(serviceCode, EndpointType.ANY_METHOD, EndpointType.ANY_PATH, true));

        /*
          Change existing, manually added, endpoints to generated if they're found from parsedEndpoints and belong to
          the service description in question
         */
        oldServiceDescriptionEndpoints.forEach(ep -> {
            if (parsedEndpoints.stream().anyMatch(parsedEp -> parsedEp.isEquivalent(ep))) {
                ep.setGenerated(true);
            }
        });

        // Remove ACLs that don't exist in the parsed endpoints list and belong to the service description in question
        List<AccessRightType> aclToBeRemoved = serviceDescription.getClient().getAcl().stream()
                .filter(accessRightType -> {
                    EndpointType endpoint = accessRightType.getEndpoint();
                    return endpoint.isGenerated()
                            && oldServiceDescriptionEndpoints.contains(endpoint)
                            && parsedEndpoints.stream()
                            .noneMatch(parsedEndpoint -> parsedEndpoint.isEquivalent(endpoint));
                })
                .collect(Collectors.toList());
        serviceDescription.getClient().getAcl().removeAll(aclToBeRemoved);

        /*
          Remove generated endpoints that are not found from the parsed endpoints and belong to the service
          description in question
        */
        List<EndpointType> endpointsToBeRemoved = oldServiceDescriptionEndpoints.stream()
                .filter(ep -> ep.isGenerated() && parsedEndpoints.stream()
                        .noneMatch(parsedEp -> parsedEp.isEquivalent(ep)))
                .collect(Collectors.toList());
        serviceDescription.getClient().getEndpoint().removeAll(endpointsToBeRemoved);

        // Add parsed endpoints to endpoints list if it is not already there
        List<EndpointType> endpointsToBeAdded = parsedEndpoints.stream()
                .filter(parsedEp -> serviceDescription.getClient().getEndpoint().stream()
                        .noneMatch(ep -> ep.isEquivalent(parsedEp)))
                .collect(Collectors.toList());
        serviceDescription.getClient().getEndpoint().addAll(endpointsToBeAdded);
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
     * Returns title for client's service with specific serviceCode.
     * If there are multiple versions, the method returns the last title based on a inverse alphabetical comparison.
     *
     * @param clientType
     * @param serviceCode
     * @return title, or null if no title exists.
     */
    public String getServiceTitle(ClientType clientType, String serviceCode) {
        ServiceType service = clientType.getServiceDescription().stream()
                .flatMap(sd -> sd.getService().stream())
                .filter(serviceType -> serviceType.getServiceCode().equals(serviceCode))
                .max((sOne, sTwo) -> sOne.getServiceVersion().compareToIgnoreCase(sTwo.getServiceVersion()))
                .orElse(null);

        return service == null ? null : service.getTitle();
    }

    /**
     * Update the WSDL url of the selected ServiceDescription.
     * Refreshing a WSDL is also an update of wsdl,
     * it just updates to the same URL value
     *
     * @param serviceDescriptionType
     * @param url the new url
     * @return ServiceDescriptionType
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws WrongServiceDescriptionTypeException if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException if WSDL at the url was invalid
     * @throws UnhandledWarningsException if there were warnings that were not ignored
     * @throws InvalidUrlException if url was empty or invalid
     * @throws InvalidServiceUrlException if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     * @throws InterruptedException if the thread running the WSDL validator is interrupted. <b>The
     * interrupted thread has already been handled with so you can choose to ignore this exception if you so
     * please.</b>
     */
    private ServiceDescriptionType updateWsdlUrl(ServiceDescriptionType serviceDescriptionType, String url,
            boolean ignoreWarnings)
            throws InvalidWsdlException, WsdlParser.WsdlNotFoundException,
            WrongServiceDescriptionTypeException, UnhandledWarningsException,
            ServiceAlreadyExistsException, InvalidUrlException, WsdlUrlAlreadyExistsException, InterruptedException,
            InvalidServiceUrlException {

        auditDataHelper.put(serviceDescriptionType.getClient().getIdentifier());
        Map<RestApiAuditProperty, Object> wsdlAuditData = auditDataHelper.putMap(RestApiAuditProperty.WSDL);
        auditDataHelper.putServiceDescriptionUrl(serviceDescriptionType);

        if (auditDataHelper.dataIsForEvent(RestApiAuditEvent.EDIT_SERVICE_DESCRIPTION)) {
            auditDataHelper.put(RestApiAuditProperty.URL_NEW, url);
        }

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

        // On refresh the service properties (URL, timeout, SSL authentication) should not change
        // so the existing values must be kept. This applies to a case when 1) the WSDL URL remains the same
        // and 2) the WSDL URL is changed. When the WSDL URL is changed (2), the service properties must keep
        // the same values in case the WSDL fetched from the new URL contains services with the same service code.
        updateServicePoperties(serviceDescriptionType, newServices);

        wsdlAuditData.put(RestApiAuditProperty.SERVICES_ADDED, serviceChanges.getAddedFullServiceCodes());
        wsdlAuditData.put(RestApiAuditProperty.SERVICES_DELETED, serviceChanges.getRemovedFullServiceCodes());

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

        return serviceDescriptionType;
    }

    /**
     * Update the url, timeout and SSL authentication of each service to match its value before it was refreshed.
     */
    private List<ServiceType> updateServicePoperties(ServiceDescriptionType serviceDescriptionType,
            List<ServiceType> newServices) {
        return newServices.stream()
                .map(newService -> {
                    String newServiceFullName = FormatUtils.getServiceFullName(newService);
                    serviceDescriptionType.getService().forEach(s -> {
                        if (newServiceFullName.equals(FormatUtils.getServiceFullName(s))) {
                            newService.setUrl(s.getUrl());
                            newService.setTimeout(s.getTimeout());
                            newService.setSslAuthentication(s.getSslAuthentication());
                        }
                    });
                    return newService;
                }).collect(Collectors.toList());
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
            WsdlParser.WsdlParseException, InvalidServiceUrlException {
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
                                .equalsIgnoreCase(SecurityServerFormatUtils.getServiceFullName(newService))))
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
     * @param client client who is associated with the wsdl
     * @param url url of the wsdl
     * @param updatedServiceDescriptionId id of the service description we
     * will update with this wsdl, or null
     * if we're adding a new one
     * @return parsed and validated wsdl and possible warnings
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidUrlException if url was empty or invalid
     * @throws InvalidWsdlException if wsdl was invalid (either parsing or validation)
     * @throws InvalidServiceUrlException if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException conflict: another service description has same url
     * @throws ServiceAlreadyExistsException conflict: same service exists in another SD
     */
    private WsdlProcessingResult processWsdl(ClientType client, String url,
            Long updatedServiceDescriptionId)
            throws WsdlParser.WsdlNotFoundException,
            InvalidWsdlException,
            InvalidUrlException,
            WsdlUrlAlreadyExistsException,
            ServiceAlreadyExistsException, InterruptedException, InvalidServiceUrlException {

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
     *
     * @throws InvalidServiceIdentifierException if there was at least one
     * invalid service code or version
     */
    private void validateServiceIdentifierFields(Collection<WsdlParser.ServiceInfo> serviceInfos)
            throws InvalidServiceIdentifierException {
        List<String> invalidIdentifierFields = new ArrayList<>();
        for (WsdlParser.ServiceInfo serviceInfo : serviceInfos) {
            String serviceCode = serviceInfo.name;
            String version = serviceInfo.version;
            if (!identifierValidator.isValid(serviceCode)) {
                invalidIdentifierFields.add(serviceCode);
            }
            if (!identifierValidator.isValid(version)) {
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
        public InvalidServiceIdentifierException(List<String> invalidIdentifiers) {
            super(new ErrorDeviation(ERROR_INVALID_SERVICE_IDENTIFIER, invalidIdentifiers));
        }
    }

    /**
     * If trying to add a service that already exists
     */
    public static class ServiceAlreadyExistsException extends ServiceException {
        public ServiceAlreadyExistsException(List<String> metadata) {
            super(new ErrorDeviation(ERROR_SERVICE_EXISTS, metadata));
        }
    }

    public static class WrongServiceDescriptionTypeException extends ServiceException {
        public WrongServiceDescriptionTypeException(String s) {
            super(s, new ErrorDeviation(ERROR_WRONG_TYPE));
        }
    }

    public static class WsdlUrlAlreadyExistsException extends ServiceException {
        public WsdlUrlAlreadyExistsException(String s) {
            super(s, new ErrorDeviation(ERROR_WSDL_EXISTS));
        }
    }

    public static class UrlAlreadyExistsException extends ServiceException {
        public UrlAlreadyExistsException(String s) {
            super(new ErrorDeviation(ERROR_EXISTING_URL, s));
        }
    }

    public static class ServiceCodeAlreadyExistsException extends ServiceException {
        public ServiceCodeAlreadyExistsException(List<String> metadata) {
            super(new ErrorDeviation(ERROR_EXISTING_SERVICE_CODE, metadata));
        }
    }
}
