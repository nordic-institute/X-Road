/*
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.validation.IdentifierValidator;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.converter.ServiceDescriptionConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionDto;
import org.niis.xroad.securityserver.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.securityserver.restapi.util.EndpointHelper;
import org.niis.xroad.securityserver.restapi.util.SecurityServerFormatUtils;
import org.niis.xroad.securityserver.restapi.util.ServiceFormatter;
import org.niis.xroad.securityserver.restapi.wsdl.InvalidWsdlException;
import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.niis.xroad.securityserver.restapi.wsdl.UnsupportedOpenApiVersionException;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlParser;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlValidator;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceEntity;
import org.niis.xroad.serverconf.impl.mapper.EndpointMapper;
import org.niis.xroad.serverconf.impl.mapper.ServiceDescriptionMapper;
import org.niis.xroad.serverconf.model.DescriptionType;
import org.niis.xroad.serverconf.model.ServiceDescription;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
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
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_VALIDATOR_INTERRUPTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_ADDING_ENDPOINTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_ADDING_SERVICES;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_DELETING_ENDPOINTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_DELETING_SERVICES;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_OPENAPI_VALIDATION_WARNINGS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_WSDL_VALIDATION_WARNINGS;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_METHOD;
import static org.niis.xroad.serverconf.model.BaseEndpoint.ANY_PATH;

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
    private final EndpointEntityChangeChecker endpointEntityChangeChecker;
    private final ServiceDescriptionConverter serviceDescriptionConverter;
    private final WsdlValidator wsdlValidator;
    private final UrlValidator urlValidator;
    private final OpenApiParser openApiParser;
    private final AuditDataHelper auditDataHelper;
    private final EndpointHelper endpointHelper;
    private final IdentifierValidator identifierValidator;
    private final WsdlParser wsdlParser;

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
     * @param toEnabled toEnabled
     * @param serviceDescriptionId serviceDescriptionId
     * @param disabledNotice disabledNotice
     * @throws ServiceDescriptionNotFoundException if serviceDescriptions with given ids were not found
     */
    private void toggleServices(boolean toEnabled, long serviceDescriptionId, String disabledNotice)
            throws ServiceDescriptionNotFoundException {

        if (!toEnabled) {
            auditDataHelper.put(RestApiAuditProperty.DISABLED_NOTICE, disabledNotice);
        }

        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionRepository.getServiceDescription(serviceDescriptionId);

        if (serviceDescriptionEntity == null) {
            throw createServiceDescriptionNotFoundException(serviceDescriptionId);
        }

        serviceDescriptionEntity.setDisabled(!toEnabled);
        if (!toEnabled) {
            serviceDescriptionEntity.setDisabledNotice(disabledNotice);
        }
        auditDataHelper.put(serviceDescriptionEntity.getClient().getIdentifier());
        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);
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
        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionEntity == null) {
            throw createServiceDescriptionNotFoundException(id);
        }
        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);
        ClientEntity clientEntity = serviceDescriptionEntity.getClient();
        auditDataHelper.put(clientEntity.getIdentifier());
        cleanAccessRights(clientEntity, serviceDescriptionEntity);
        cleanEndpoints(clientEntity, serviceDescriptionEntity);
        clientEntity.getServiceDescriptions().remove(serviceDescriptionEntity);
    }

    private void cleanEndpoints(ClientEntity clientEntity, ServiceDescriptionEntity serviceDescriptionEntity) {
        Set<String> servicesToRemove = serviceDescriptionEntity.getServices()
                .stream()
                .map(ServiceEntity::getServiceCode)
                .filter(isServiceUniqueToCurrentDescription(clientEntity, serviceDescriptionEntity))
                .collect(Collectors.toSet());
        clientEntity.getEndpoints().removeIf(endpointEntity -> servicesToRemove.contains(endpointEntity.getServiceCode()));
    }

    private void cleanAccessRights(ClientEntity client, ServiceDescriptionEntity serviceDescriptionEntity) {
        Set<String> aclServiceCodesToRemove = serviceDescriptionEntity.getServices()
                .stream()
                .map(ServiceEntity::getServiceCode)
                .filter(isServiceUniqueToCurrentDescription(client, serviceDescriptionEntity))
                .collect(Collectors.toSet());
        client.getAccessRights().removeIf(accessRightEntity -> aclServiceCodesToRemove
                .contains(accessRightEntity.getEndpoint().getServiceCode()));
    }

    private Predicate<String> isServiceUniqueToCurrentDescription(ClientEntity clientEntity, ServiceDescriptionEntity current) {
        return (String serviceCode) -> clientEntity.getServiceDescriptions().stream().filter(
                        sd -> !sd.getId().equals(current.getId()))
                .flatMap(sd -> sd.getServices().stream())
                .map(ServiceEntity::getServiceCode)
                .noneMatch(Predicate.isEqual(serviceCode));
    }

    @SuppressWarnings({"java:S3776"}) // won't fix: too high cognitive complexity.
    // should be fixed when this method is updated next.
    public ServiceDescriptionDto addServiceDescription(DescriptionType descriptionType, ClientId clientId, String url,
                                                       String restServiceCode, boolean ignoreWarnings) {

        ServiceDescriptionEntity addedServiceDescriptionEntity = null;
        if (descriptionType == DescriptionType.WSDL) {
            try {
                addedServiceDescriptionEntity = addWsdlServiceDescription(clientId, url, ignoreWarnings);
            } catch (WsdlParser.WsdlNotFoundException | UnhandledWarningsException | InvalidUrlException
                     | InvalidWsdlException | InvalidServiceUrlException e) {
                // deviation data (errorcode + warnings) copied
                throw new BadRequestException(e);
            } catch (ClientNotFoundException e) {
                // deviation data (errorcode + warnings) copied
                throw new ResourceNotFoundException(e);
            } catch (ServiceDescriptionService.ServiceAlreadyExistsException
                     | ServiceDescriptionService.WsdlUrlAlreadyExistsException e) {
                // deviation data (errorcode + warnings) copied
                throw new ConflictException(e);
            } catch (InterruptedException e) {
                throw new InternalServerErrorException(new ErrorDeviation(ERROR_WSDL_VALIDATOR_INTERRUPTED));
            }
        } else if (descriptionType == DescriptionType.OPENAPI3) {
            try {
                addedServiceDescriptionEntity = addOpenApi3ServiceDescription(clientId, url, restServiceCode, ignoreWarnings);
            } catch (OpenApiParser.ParsingException | UnhandledWarningsException | MissingParameterException
                     | InvalidUrlException | UnsupportedOpenApiVersionException e) {
                throw new BadRequestException(e);
            } catch (ClientNotFoundException e) {
                throw new ResourceNotFoundException(e);
            } catch (ServiceDescriptionService.UrlAlreadyExistsException
                     | ServiceDescriptionService.ServiceCodeAlreadyExistsException e) {
                throw new ConflictException(e);
            }
        } else if (descriptionType == DescriptionType.REST) {
            try {
                addedServiceDescriptionEntity = addRestEndpointServiceDescription(clientId,
                        url, restServiceCode);
            } catch (ClientNotFoundException e) {
                throw new ResourceNotFoundException(e);
            } catch (MissingParameterException | InvalidUrlException e) {
                throw new BadRequestException(e);
            } catch (ServiceDescriptionService.ServiceCodeAlreadyExistsException
                     | ServiceDescriptionService.UrlAlreadyExistsException e) {
                throw new ConflictException(e);
            }
        }

        return serviceDescriptionConverter.convert(ServiceDescriptionMapper.get().toTarget(addedServiceDescriptionEntity));
    }

    /**
     * Add a new WSDL ServiceDescriptionEntity
     *
     * @param clientId clientId
     * @param url url
     * @param ignoreWarnings ignoreWarnings
     * @return created {@link ServiceDescription}, with id populated
     * @throws ClientNotFoundException          if client with id was not found
     * @throws WsdlParser.WsdlNotFoundException if a wsdl was not found at the url
     * @throws InvalidWsdlException             if WSDL at the url was invalid
     * @throws UnhandledWarningsException       if there were warnings that were not ignored
     * @throws InvalidUrlException              if url was empty or invalid
     * @throws InvalidServiceUrlException       if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException    conflict: another service description has same url
     * @throws ServiceAlreadyExistsException    conflict: same service exists in another SD
     * @throws InterruptedException             if the thread running the WSDL validator is interrupted. <b>The
     *                                          interrupted thread has already been handled with so you can choose to ignore this exception
     *                                          if you so please.</b>
     */
    ServiceDescriptionEntity addWsdlServiceDescription(ClientId clientId, String url, boolean ignoreWarnings)
            throws InvalidWsdlException,
            WsdlParser.WsdlNotFoundException,
            ClientNotFoundException,
            UnhandledWarningsException,
            ServiceAlreadyExistsException,
            InvalidUrlException,
            WsdlUrlAlreadyExistsException, InterruptedException, InvalidServiceUrlException {
        ClientEntity clientEntity = clientService.getLocalClientEntity(clientId);
        if (clientEntity == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + NOT_FOUND);
        }

        WsdlProcessingResult wsdlProcessingResult = processWsdl(clientEntity, url, null);

        validateServiceUrls(wsdlProcessingResult.getParsedServices());

        if (!ignoreWarnings && !wsdlProcessingResult.getWarnings().isEmpty()) {
            throw new UnhandledWarningsException(wsdlProcessingResult.getWarnings());
        }

        // create a new ServiceDescription with parsed services
        ServiceDescriptionEntity serviceDescriptionEntity = buildWsdlServiceDescription(clientEntity,
                wsdlProcessingResult.getParsedServices(), url);

        // get the new endpoints to add - skipping existing ones
        Collection<EndpointEntity> endpointsToAdd = resolveNewEndpoints(clientEntity, serviceDescriptionEntity);

        serviceDescriptionRepository.persist(serviceDescriptionEntity);  // explicit persist to get the id to the return value

        clientEntity.getEndpoints().addAll(endpointsToAdd);
        clientEntity.getServiceDescriptions().add(serviceDescriptionEntity);
        return serviceDescriptionEntity;
    }

    /**
     * Validate that all service URLs begin with HTTP or HTTPS. This should be checked only when ADDING a new WSDL
     *
     * @param parsedServices parsedServices
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
     * Create a new {@link EndpointEntity} for all Services in the provided {@link ServiceDescriptionEntity}.
     * If an equal EndpointEntity already exists for the provided {@link ClientEntity} it will not be returned
     *
     * @param clientEntity clientEntity
     * @param newServiceDescription newServiceDescription
     * @return Only the newly created EndpointEntity
     */
    private Collection<EndpointEntity> resolveNewEndpoints(ClientEntity clientEntity, ServiceDescriptionEntity newServiceDescription) {
        Map<String, EndpointEntity> endpointMap = new HashMap<>();

        // add all new endpoints into a hashmap with a combination key
        newServiceDescription.getServices().stream()
                .map(serviceEntity -> EndpointEntity.create(
                        serviceEntity.getServiceCode(), ANY_METHOD, ANY_PATH, true))
                .forEach(endpointEntity -> endpointMap.put(createEndpointKey(endpointEntity), endpointEntity));

        // remove all existing endpoints with an equal combination key from the map
        clientEntity.getEndpoints().forEach(endpointEntity -> endpointMap.remove(createEndpointKey(endpointEntity)));

        return endpointMap.values();
    }

    private String createEndpointKey(EndpointEntity endpointEntity) {
        return endpointEntity.getServiceCode() + endpointEntity.getMethod() + endpointEntity.getPath()
                + endpointEntity.isGenerated();
    }

    /**
     * Add openapi3 ServiceDescriptionEntity
     *
     * @param clientId clientId
     * @param url url
     * @param serviceCode serviceCode
     * @param ignoreWarnings ignoreWarnings
     * @return ServiceDescriptionEntity
     * @throws OpenApiParser.ParsingException     if parsing openapi3 description results in errors
     * @throws ClientNotFoundException            if client is not found with given id
     * @throws UnhandledWarningsException         if ignoreWarnings is false and parsing openapi3 description results
     *                                            in warnings
     * @throws UrlAlreadyExistsException          if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException  if trying to add duplicate ServiceCode
     * @throws MissingParameterException          if given ServiceCode is null
     * @throws InvalidUrlException                if url is invalid'
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    @PreAuthorize("hasAuthority('ADD_OPENAPI3')")
    ServiceDescriptionEntity addOpenApi3ServiceDescription(ClientId clientId, String url, String serviceCode, boolean ignoreWarnings)
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
            throw new UnhandledWarningsException(List.of(openapiParserWarnings));
        }

        ClientEntity clientEntity = clientService.getLocalClientEntity(clientId);
        if (clientEntity == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + NOT_FOUND);
        }

        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(clientEntity, url,
                DescriptionType.OPENAPI3);

        // Initiate default service
        ServiceEntity serviceEntity = new ServiceEntity();
        serviceEntity.setServiceCode(serviceCode);
        serviceEntity.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        serviceEntity.setUrl(result.getBaseUrl());
        serviceEntity.setServiceDescription(serviceDescriptionEntity);

        // Populate ServiceDescription
        serviceDescriptionEntity.getServices().add(serviceEntity);

        // Create endpoints
        EndpointEntity endpointEntity = EndpointEntity.create(serviceCode, ANY_METHOD, ANY_PATH, true);
        List<EndpointEntity> endpoints = new ArrayList<>();
        endpoints.add(endpointEntity);
        endpoints.addAll(result.getOperations().stream()
                .map(operation -> EndpointEntity.create(serviceCode, operation.getMethod(), operation.getPath(), true))
                .toList());

        checkDuplicateUrl(serviceDescriptionEntity);
        checkDuplicateServiceCodes(serviceDescriptionEntity);

        serviceDescriptionRepository.persist(serviceDescriptionEntity);  // explicit persist to get the id to the return value

        // Populate client with new service description and endpoints
        clientEntity.getEndpoints().addAll(endpoints);
        clientEntity.getServiceDescriptions().add(serviceDescriptionEntity);

        return serviceDescriptionEntity;
    }

    /**
     * Check whether the ServiceDescriptions url already exists in the linked Client
     *
     * @param serviceDescriptionEntity serviceDescriptionEntity
     * @throws UrlAlreadyExistsException if trying to add duplicate url
     */
    private void checkDuplicateUrl(ServiceDescriptionEntity serviceDescriptionEntity) throws UrlAlreadyExistsException {
        boolean hasDuplicates = serviceDescriptionEntity.getClient().getServiceDescriptions().stream()
                .anyMatch(other -> !serviceDescriptionEntity.equals(other)
                        && serviceDescriptionEntity.getUrl().equals(other.getUrl()));

        if (hasDuplicates) {
            throw new UrlAlreadyExistsException(serviceDescriptionEntity.getUrl());
        }
    }

    /**
     * Check whether the ServiceDescriptions ServiceCode already exists in the linked Client
     *
     * @param serviceDescriptionEntity serviceDescriptionEntity
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     */
    private void checkDuplicateServiceCodes(ServiceDescriptionEntity serviceDescriptionEntity)
            throws ServiceCodeAlreadyExistsException {

        List<ServiceEntity> existingServices =
                getClientsExistingServices(serviceDescriptionEntity.getClient(), serviceDescriptionEntity.getId());

        Set<ServiceEntity> duplicateServices = serviceDescriptionEntity.getServices().stream()
                .filter(candidateService -> {
                    String candidateFullServiceCode = ServiceFormatter.getServiceFullName(candidateService);
                    boolean existsByServiceCode = existingServices.stream()
                            .map(ServiceEntity::getServiceCode)
                            .anyMatch(serviceCode -> serviceCode.equalsIgnoreCase(candidateService.getServiceCode()));
                    boolean existsByFullServiceCode = existingServices.stream()
                            .map(ServiceFormatter::getServiceFullName)
                            .anyMatch(fullServiceCode -> fullServiceCode.equalsIgnoreCase(candidateFullServiceCode));
                    return existsByFullServiceCode || existsByServiceCode;
                })
                .collect(Collectors.toSet());

        // throw error with service metadata if conflicted
        if (!duplicateServices.isEmpty()) {
            List<String> errorMetadata = new ArrayList<>();
            for (ServiceEntity service : duplicateServices) {
                // error metadata contains service name and service description url
                errorMetadata.add(ServiceFormatter.getServiceFullName(service));
                errorMetadata.add(service.getServiceDescription().getUrl());
            }
            throw new ServiceCodeAlreadyExistsException(errorMetadata);
        }

    }

    /**
     * Add a new REST ServiceDescription
     *
     * @param clientId clientId
     * @param url url
     * @param serviceCode serviceCode
     * @return ServiceDescriptionEntity
     * @throws ClientNotFoundException           if client not found with given id
     * @throws MissingParameterException         if given ServiceCode is null
     * @throws ServiceCodeAlreadyExistsException if trying to add duplicate ServiceCode
     * @throws UrlAlreadyExistsException         if trying to add duplicate url
     * @throws InvalidUrlException               if url is invalid
     */
    @PreAuthorize("hasAuthority('ADD_OPENAPI3')")
    ServiceDescriptionEntity addRestEndpointServiceDescription(ClientId clientId, String url, String serviceCode)
            throws
            ClientNotFoundException,
            MissingParameterException,
            ServiceCodeAlreadyExistsException,
            UrlAlreadyExistsException,
            InvalidUrlException {

        if (serviceCode == null) {
            throw new MissingParameterException("Missing ServiceCode");
        }

        validateUrl(url);

        ClientEntity client = clientService.getLocalClientEntity(clientId);
        if (client == null) {
            throw new ClientNotFoundException(CLIENT_WITH_ID + " " + clientId.toShortString() + NOT_FOUND);
        }

        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(client, url,
                DescriptionType.REST);

        // Populate service
        ServiceEntity serviceEntity = new ServiceEntity();
        serviceEntity.setServiceCode(serviceCode);
        serviceEntity.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        serviceEntity.setUrl(url);
        serviceEntity.setServiceDescription(serviceDescriptionEntity);
        if (FormatUtils.isHttpsUrl(url)) {
            serviceEntity.setSslAuthentication(true);
        }

        // Add created service description to client
        serviceDescriptionEntity.getServices().add(serviceEntity);
        client.getServiceDescriptions().add(serviceDescriptionEntity);

        // Add created endpoint to client
        EndpointEntity endpointEntity = EndpointEntity.create(serviceCode, ANY_METHOD,
                ANY_PATH, true);
        client.getEndpoints().add(endpointEntity);

        checkDuplicateServiceCodes(serviceDescriptionEntity);
        checkDuplicateUrl(serviceDescriptionEntity);

        serviceDescriptionRepository.persist(serviceDescriptionEntity);  // explicit persist to get the id to the return value

        return serviceDescriptionEntity;
    }

    /**
     * Update the WSDL url of the selected ServiceDescription
     *
     * @param id
     * @param url the new url
     * @return ServiceDescription
     * @throws WsdlParser.WsdlNotFoundException     if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException  if SD with given id was not found
     * @throws WrongServiceDescriptionException     if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException                 if WSDL at the url was invalid
     * @throws UnhandledWarningsException           if there were warnings that were not ignored
     * @throws InvalidUrlException                  if url was empty or invalid
     * @throws InvalidServiceUrlException           if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException        conflict: another service description has same url
     * @throws ServiceAlreadyExistsException        conflict: same service exists in another SD
     * @throws InterruptedException                 if the thread running the WSDL validator is interrupted. <b>The
     *                                              interrupted thread has already been handled with
     *                                              so you can choose to ignore this exception if you so  please.</b>
     */
    public ServiceDescription updateWsdlUrl(Long id, String url, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            ServiceDescriptionNotFoundException,
            WrongServiceDescriptionException,
            UnhandledWarningsException,
            InvalidUrlException,
            ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, InterruptedException, InvalidServiceUrlException {
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(id);
        if (serviceDescriptionEntity == null) {
            throw createServiceDescriptionNotFoundException(id);
        }
        return ServiceDescriptionMapper.get().toTarget(updateWsdlUrl(serviceDescriptionEntity, url, ignoreWarnings));
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
     * @throws WrongServiceDescriptionException     wrong type of service description
     * @throws UnhandledWarningsException           Unhandledwarnings in openapi3 or wsdl description
     * @throws InvalidUrlException                  invalid url
     * @throws InvalidServiceUrlException           if the WSDL has services with invalid urls
     * @throws ServiceAlreadyExistsException        service code already exists if refreshing wsdl
     * @throws WsdlUrlAlreadyExistsException        url is already in use by this client
     * @throws OpenApiParser.ParsingException       openapi3 description parsing fails
     * @throws UnsupportedOpenApiVersionException   if the openapi version is not supported
     */
    public ServiceDescription refreshServiceDescription(Long id, boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException,
            ServiceDescriptionNotFoundException, WrongServiceDescriptionException,
            UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, OpenApiParser.ParsingException, InterruptedException,
            InvalidServiceUrlException, UnsupportedOpenApiVersionException {

        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(id);
        if (serviceDescriptionEntity == null) {
            throw createServiceDescriptionNotFoundException(id);
        }

        auditDataHelper.put(serviceDescriptionEntity.getClient().getIdentifier());
        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);

        if (serviceDescriptionEntity.getType().equals(DescriptionType.WSDL)) {
            serviceDescriptionEntity = refreshWSDLServiceDescription(serviceDescriptionEntity, ignoreWarnings);
        } else if (serviceDescriptionEntity.getType().equals(DescriptionType.OPENAPI3)) {
            serviceDescriptionEntity = refreshOpenApi3ServiceDescription(serviceDescriptionEntity, ignoreWarnings);
        }

        return ServiceDescriptionMapper.get().toTarget(serviceDescriptionEntity);
    }

    /**
     * Refresh a ServiceDescriptionEntity
     *
     * @param serviceDescriptionEntity serviceDescriptionEntity
     * @param ignoreWarnings
     * @return {@link ServiceDescription}
     * @throws WsdlParser.WsdlNotFoundException     if a wsdl was not found at the url
     * @throws ServiceDescriptionNotFoundException  if SD with given id was not found
     * @throws WrongServiceDescriptionException     if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException                 if WSDL at the url was invalid
     * @throws UnhandledWarningsException           if there were warnings that were not ignored
     * @throws InvalidUrlException                  if url was empty or invalid
     * @throws InvalidServiceUrlException           if the WSDL has services with invalid urls
     * @throws WsdlUrlAlreadyExistsException        conflict: another service description has same url
     * @throws ServiceAlreadyExistsException        conflict: same service exists in another SD
     * @throws InterruptedException                 if the thread running the WSDL validator is interrupted. <b>The
     *                                              interrupted thread has already been handled with so you can choose
     *                                              to ignore this exception if you so  please.</b>
     */
    @PreAuthorize("hasAuthority('REFRESH_WSDL')")
    private ServiceDescriptionEntity refreshWSDLServiceDescription(ServiceDescriptionEntity serviceDescriptionEntity,
                                                                   boolean ignoreWarnings)
            throws WsdlParser.WsdlNotFoundException, InvalidWsdlException, WrongServiceDescriptionException,
            UnhandledWarningsException, InvalidUrlException, ServiceAlreadyExistsException,
            WsdlUrlAlreadyExistsException, InterruptedException {

        if (!serviceDescriptionEntity.getType().equals(DescriptionType.WSDL)) {
            throw new WrongServiceDescriptionException("Expected description type WSDL");
        }

        String wsdlUrl = serviceDescriptionEntity.getUrl();
        return updateWsdlUrl(serviceDescriptionEntity, wsdlUrl, ignoreWarnings);

        // we only have two types at the moment so the type must be OPENAPI3 if we end up this far
    }

    /**
     * Refresh OPENAPI3 ServiceDescription
     *
     * @param serviceDescriptionEntity serviceDescriptionEntity
     * @param ignoreWarnings ignoreWarnings
     * @return {@link ServiceDescriptionEntity}
     * @throws WrongServiceDescriptionException     if service type is not openapi3
     * @throws UnhandledWarningsException           if unhandled warnings are found and ignoreWarnings if false
     * @throws OpenApiParser.ParsingException       if parsing openapi3 description fails
     * @throws InvalidUrlException                  if url is invalid
     * @throws UnsupportedOpenApiVersionException   if the openapi version is not supported
     */
    @PreAuthorize("hasAuthority('REFRESH_OPENAPI3')")
    private ServiceDescriptionEntity refreshOpenApi3ServiceDescription(ServiceDescriptionEntity serviceDescriptionEntity,
                                                                       boolean ignoreWarnings)
            throws WrongServiceDescriptionException,
            UnhandledWarningsException,
            OpenApiParser.ParsingException, InvalidUrlException,
            UnsupportedOpenApiVersionException {

        if (!serviceDescriptionEntity.getType().equals(DescriptionType.OPENAPI3)) {
            throw new WrongServiceDescriptionException("Expected description type OPENAPI3");
        }

        if (serviceDescriptionEntity.getServices().getFirst() == null) {
            throw new DeviationAwareRuntimeException(SERVICE_NOT_FOUND_ERROR_MSG + serviceDescriptionEntity.getId());
        }

        validateUrl(serviceDescriptionEntity.getUrl());

        serviceDescriptionEntity.setRefreshedDate(new Date());

        parseOpenApi3ToServiceDescription(serviceDescriptionEntity.getUrl(),
                serviceDescriptionEntity.getServices().getFirst().getServiceCode(),
                ignoreWarnings,
                serviceDescriptionEntity);

        return serviceDescriptionEntity;
    }

    /**
     * Update Rest service description
     *
     * @param id
     * @param url
     * @param restServiceCode
     * @param newRestServiceCode
     * @return {@link ServiceDescription}
     * @throws UrlAlreadyExistsException           if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException   if trying to add duplicate ServiceCode
     * @throws ServiceDescriptionNotFoundException if ServiceDescription not found
     * @throws InvalidUrlException                 if url is invalid
     */
    @PreAuthorize("hasAuthority('EDIT_REST')")
    public ServiceDescription updateRestServiceDescription(Long id, String url, String restServiceCode,
                                                           String newRestServiceCode)
            throws UrlAlreadyExistsException, ServiceCodeAlreadyExistsException, ServiceDescriptionNotFoundException,
            WrongServiceDescriptionException, InvalidUrlException {

        if (newRestServiceCode == null) {
            newRestServiceCode = restServiceCode;
        }

        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(id);
        if (serviceDescriptionEntity == null) {
            throw new ServiceDescriptionNotFoundException("Service description with id: " + id + " wasn't found");
        }

        auditDataHelper.put(serviceDescriptionEntity.getClient().getIdentifier());
        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);
        auditDataHelper.put(RestApiAuditProperty.URL_NEW, url);
        if (!serviceDescriptionEntity.getType().equals(DescriptionType.REST)) {
            throw new WrongServiceDescriptionException("Expected description type REST");
        }

        validateUrl(serviceDescriptionEntity.getUrl());

        if (serviceDescriptionEntity.getServices().getFirst() == null) {
            throw new DeviationAwareRuntimeException(SERVICE_NOT_FOUND_ERROR_MSG + serviceDescriptionEntity.getId());
        }

        serviceDescriptionEntity.setRefreshedDate(new Date());
        serviceDescriptionEntity.setUrl(url);
        serviceDescriptionEntity.getServices().getFirst().setUrl(url);

        updateServiceCodes(restServiceCode, newRestServiceCode, serviceDescriptionEntity);

        checkDuplicateServiceCodes(serviceDescriptionEntity);
        checkDuplicateUrl(serviceDescriptionEntity);

        return ServiceDescriptionMapper.get().toTarget(serviceDescriptionEntity);
    }

    private void putServiceDescriptionUrlAndTypeToAudit(ServiceDescriptionEntity serviceDescriptionEntity) {
        if (serviceDescriptionEntity != null) {
            auditDataHelper.putServiceDescriptionUrlAndType(serviceDescriptionEntity.getUrl(), serviceDescriptionEntity.getType());
        }
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
     * @throws UrlAlreadyExistsException          if trying to add duplicate url
     * @throws ServiceCodeAlreadyExistsException  if trying to add duplicate ServiceCode
     * @throws UnhandledWarningsException         if ignoreWarnings false and warning-level issues in openapi3
     *                                            description
     * @throws OpenApiParser.ParsingException     if openapi3 parser finds errors in the parsed document
     * @throws InvalidUrlException                if url is invalid
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    @PreAuthorize("hasAuthority('EDIT_OPENAPI3')")
    public ServiceDescription updateOpenApi3ServiceDescription(Long id, String url, String restServiceCode,
                                                               String newRestServiceCode, Boolean ignoreWarnings)
            throws UrlAlreadyExistsException,
            ServiceCodeAlreadyExistsException, UnhandledWarningsException, OpenApiParser.ParsingException,
            WrongServiceDescriptionException, ServiceDescriptionNotFoundException,
            InvalidUrlException, UnsupportedOpenApiVersionException {

        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(id);

        if (serviceDescriptionEntity == null) {
            throw new ServiceDescriptionNotFoundException("ServiceDescription with id: " + id + " wasn't found");
        }

        auditDataHelper.put(serviceDescriptionEntity.getClient().getIdentifier());
        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);
        auditDataHelper.put(RestApiAuditProperty.URL_NEW, url);

        if (!serviceDescriptionEntity.getType().equals(DescriptionType.OPENAPI3)) {
            throw new WrongServiceDescriptionException("Expected description type OPENAPI3");
        }

        validateUrl(url);

        if (newRestServiceCode == null) {
            newRestServiceCode = restServiceCode;
        }

        if (serviceDescriptionEntity.getServices().getFirst() == null) {
            throw new DeviationAwareRuntimeException(SERVICE_NOT_FOUND_ERROR_MSG + serviceDescriptionEntity.getId());
        }

        updateServiceCodes(restServiceCode, newRestServiceCode, serviceDescriptionEntity);

        // Parse openapi definition and handle updating endpoints and acls
        parseOpenApi3ToServiceDescription(url, newRestServiceCode, ignoreWarnings, serviceDescriptionEntity);

        serviceDescriptionEntity.setRefreshedDate(new Date());
        serviceDescriptionEntity.setUrl(url);

        checkDuplicateServiceCodes(serviceDescriptionEntity);
        checkDuplicateUrl(serviceDescriptionEntity);

        return ServiceDescriptionMapper.get().toTarget(serviceDescriptionEntity);
    }

    /**
     * Parse OpenApi3 description and update endpoints and acls in ServiceDescription accordingly
     *
     * @param url
     * @param serviceCode
     * @param ignoreWarnings
     * @param serviceDescription
     * @throws OpenApiParser.ParsingException     if there are errors in the openapi3 description document
     * @throws UnhandledWarningsException         if ignoreWarnings is false and parser returns warnings from openapi
     * @throws UnsupportedOpenApiVersionException if the openapi version is not supported
     */
    private void parseOpenApi3ToServiceDescription(String url, String serviceCode,
                                                   boolean ignoreWarnings,
                                                   ServiceDescriptionEntity serviceDescription)
            throws OpenApiParser.ParsingException,
            UnhandledWarningsException,
            UnsupportedOpenApiVersionException {
        OpenApiParser.Result result = openApiParser.parse(url);

        // Create endpoints from parsed results
        List<EndpointEntity> newEndpoints = endpointHelper.getNewEndpoints(serviceCode, result);

        List<EndpointEntity> oldEndpoints = endpointHelper.getEndpoints(serviceDescription);

        /*
          Change existing, manually added, endpoints to generated if they're found from parsedEndpoints and belong to
          the service description in question
         */
        oldEndpoints.stream()
                .filter(ep -> EndpointMapper.get().toTargets(newEndpoints).stream()
                        .anyMatch(parsedEp -> parsedEp.isEquivalent(EndpointMapper.get().toTarget(ep))))
                .forEach(ep -> ep.setGenerated(true));

        // find what services were added or removed
        EndpointEntityChangeChecker.ServiceChanges serviceChanges = endpointEntityChangeChecker.check(
                serviceDescription.getClient().getEndpoints(),
                oldEndpoints,
                newEndpoints,
                serviceDescription.getClient().getAccessRights()
        );

        handleWarnings(ignoreWarnings, result, serviceChanges);

        // Remove ACLs that don't exist in the parsed endpoints list and belong to the service description in question
        serviceDescription.getClient().getAccessRights().removeAll(serviceChanges.getRemovedAcls());

        /*
          Remove generated endpoints that are not found from the parsed endpoints and belong to the service
          description in question
        */
        serviceDescription.getClient().getEndpoints().removeAll(serviceChanges.getRemovedEndpoints());

        // Add parsed endpoints to endpoints list if it is not already there
        serviceDescription.getClient().getEndpoints().addAll(serviceChanges.getAddedEndpoints());
    }

    private void handleWarnings(boolean ignoreWarnings,
                                OpenApiParser.Result result,
                                EndpointEntityChangeChecker.ServiceChanges serviceChanges)
            throws UnhandledWarningsException {

        if (ignoreWarnings || (!result.hasWarnings() && serviceChanges.isEmpty())) {
            return;
        }

        // collect all types of warnings, throw Exception if not ignored
        List<WarningDeviation> allWarnings = new ArrayList<>();
        if (result.hasWarnings()) {
            allWarnings.add(new WarningDeviation(WARNING_OPENAPI_VALIDATION_WARNINGS, result.getWarnings()));
        }
        if (!serviceChanges.isEmpty()) {
            allWarnings.addAll(createServiceChangeWarnings(serviceChanges));
        }
        throw new UnhandledWarningsException(allWarnings);
    }

    /**
     * Updates the ServiceCodes of Endpoints and Service linked to given ServiceDescription
     *
     * @param serviceCode
     * @param newServiceCode
     * @param serviceDescriptionEntity
     */
    private void updateServiceCodes(String serviceCode, String newServiceCode, ServiceDescriptionEntity serviceDescriptionEntity) {
        // Update endpoint service codes
        ClientEntity clientEntity = serviceDescriptionEntity.getClient();
        clientEntity.getEndpoints().stream()
                .filter(e -> e.getServiceCode().equals(serviceCode))
                .forEach(e -> e.setServiceCode(newServiceCode));

        // Update service's service code
        ServiceEntity serviceEntity = serviceDescriptionEntity.getServices().stream()
                .filter(s -> serviceCode.equals(s.getServiceCode()))
                .findFirst()
                .orElseThrow(() -> new DeviationAwareRuntimeException("Service with service code: " + serviceCode
                        + " wasn't found from service description with id: " + serviceDescriptionEntity.getId()));
        serviceEntity.setServiceCode(newServiceCode);
    }

    /**
     * Return matching ServiceDescription or null.
     * serviceDescription.services and serviceDescription.client are always loaded
     * with Hibernate.init()
     *
     * @param id id
     * @return ServiceDescription
     */
    public ServiceDescription getServiceDescription(Long id) {
        return ServiceDescriptionMapper.get().toTarget(getServiceDescriptionEntity(id));
    }

    ServiceDescriptionEntity getServiceDescriptionEntity(Long id) {
        ServiceDescriptionEntity serviceDescriptionEntity = serviceDescriptionRepository.getServiceDescription(id);
        if (serviceDescriptionEntity != null) {
            Hibernate.initialize(serviceDescriptionEntity.getServices());
            Hibernate.initialize(serviceDescriptionEntity.getClient().getEndpoints());
        }
        return serviceDescriptionEntity;
    }

    /**
     * Returns title for client's service with specific serviceCode.
     * If there are multiple versions, the method returns the last title based on a inverse alphabetical comparison.
     *
     * @param clientEntity clientEntity
     * @param serviceCode serviceCode
     * @return title, or null if no title exists.
     */
    String getServiceTitle(ClientEntity clientEntity, String serviceCode) {
        ServiceEntity service = clientEntity.getServiceDescriptions().stream()
                .flatMap(sd -> sd.getServices().stream())
                .filter(serviceEntity -> serviceEntity.getServiceCode().equals(serviceCode))
                .max((sOne, sTwo) -> sOne.getServiceVersion().compareToIgnoreCase(sTwo.getServiceVersion()))
                .orElse(null);

        return service == null ? null : service.getTitle();
    }

    /**
     * Update the WSDL url of the selected ServiceDescriptionEntity.
     * Refreshing a WSDL is also an update of wsdl,
     * it just updates to the same URL value
     *
     * @param serviceDescriptionEntity serviceDescriptionEntity
     * @param url                    the new url
     * @return ServiceDescriptionEntity
     * @throws WsdlParser.WsdlNotFoundException     if a wsdl was not found at the url
     * @throws WrongServiceDescriptionException     if SD with given id was not a WSDL based one
     * @throws InvalidWsdlException                 if WSDL at the url was invalid
     * @throws UnhandledWarningsException           if there were warnings that were not ignored
     * @throws InvalidUrlException                  if url was empty or invalid
     * @throws WsdlUrlAlreadyExistsException        conflict: another service description has same url
     * @throws ServiceAlreadyExistsException        conflict: same service exists in another SD
     * @throws InterruptedException                 if the thread running the WSDL validator is interrupted. <b>The
     *                                              interrupted thread has already been handled with so you can choose
     *                                              to ignore this exception if you so  please.</b>
     */
    private ServiceDescriptionEntity updateWsdlUrl(ServiceDescriptionEntity serviceDescriptionEntity, String url, boolean ignoreWarnings)
            throws InvalidWsdlException, WsdlParser.WsdlNotFoundException, WrongServiceDescriptionException, UnhandledWarningsException,
            ServiceAlreadyExistsException, InvalidUrlException, WsdlUrlAlreadyExistsException, InterruptedException {

        auditDataHelper.put(serviceDescriptionEntity.getClient().getIdentifier());
        Map<RestApiAuditProperty, Object> wsdlAuditData = auditDataHelper.putMap(RestApiAuditProperty.WSDL);
        putServiceDescriptionUrlAndTypeToAudit(serviceDescriptionEntity);

        if (auditDataHelper.dataIsForEvent(RestApiAuditEvent.EDIT_SERVICE_DESCRIPTION)) {
            auditDataHelper.put(RestApiAuditProperty.URL_NEW, url);
        }

        // Shouldn't be able to edit e.g. REST service descriptions with a WSDL URL
        if (serviceDescriptionEntity.getType() != DescriptionType.WSDL) {
            throw new WrongServiceDescriptionException("Existing service description (id: "
                    + serviceDescriptionEntity.getId().toString() + " is not WSDL");
        }

        ClientEntity clientEntity = serviceDescriptionEntity.getClient();
        WsdlProcessingResult wsdlProcessingResult = processWsdl(clientEntity, url, serviceDescriptionEntity.getId());

        List<ServiceEntity> newServices = wsdlProcessingResult.getParsedServices()
                .stream()
                .map(serviceInfo -> serviceInfoToServiceEntity(serviceInfo, serviceDescriptionEntity))
                .collect(Collectors.toList());

        // find what services were added or removed
        ServiceChangeChecker.ServiceChanges serviceChanges = serviceChangeChecker.check(
                serviceDescriptionEntity.getServices(),
                newServices);

        // On refresh the service properties (URL, timeout, SSL authentication) should not change
        // so the existing values must be kept. This applies to a case when 1) the WSDL URL remains the same
        // and 2) the WSDL URL is changed. When the WSDL URL is changed (2), the service properties must keep
        // the same values in case the WSDL fetched from the new URL contains services with the same service code.
        updateServiceProperties(serviceDescriptionEntity, newServices);

        wsdlAuditData.put(RestApiAuditProperty.SERVICES_ADDED, serviceChanges.getAddedFullServiceCodes());
        wsdlAuditData.put(RestApiAuditProperty.SERVICES_DELETED, serviceChanges.getRemovedFullServiceCodes());

        // collect all types of warnings, throw Exception if not ignored
        List<WarningDeviation> allWarnings = new ArrayList<>(wsdlProcessingResult.getWarnings());
        if (!serviceChanges.isEmpty()) {
            allWarnings.addAll(createServiceChangeWarnings(serviceChanges));
        }
        if (!ignoreWarnings && !allWarnings.isEmpty()) {
            throw new UnhandledWarningsException(allWarnings);
        }

        serviceDescriptionEntity.setRefreshedDate(new Date());
        serviceDescriptionEntity.setUrl(url);

        List<String> newServiceCodes = newServices
                .stream()
                .map(ServiceEntity::getServiceCode)
                .toList();

        // service codes that will be REMOVED
        List<String> removedServiceCodes = serviceChanges.getRemovedServices()
                .stream()
                .map(ServiceEntity::getServiceCode)
                .toList();

        // replace all old services with the new ones
        serviceDescriptionEntity.getServices().clear();
        serviceDescriptionEntity.getServices().addAll(newServices);

        // clear AccessRightEntities that belong to non-existing services
        clientEntity.getAccessRights().removeIf(accessRightEntity -> {
            String serviceCode = accessRightEntity.getEndpoint().getServiceCode();
            return removedServiceCodes.contains(serviceCode) && !newServiceCodes.contains(serviceCode);
        });

        // remove related endpoints
        clientEntity.getEndpoints().removeIf(endpointEntity -> removedServiceCodes.contains(endpointEntity.getServiceCode()));

        // add new endpoints
        Collection<EndpointEntity> endpointsToAdd = resolveNewEndpoints(clientEntity, serviceDescriptionEntity);
        clientEntity.getEndpoints().addAll(endpointsToAdd);

        return serviceDescriptionEntity;
    }

    /**
     * Update the url, timeout and SSL authentication of each service to match its value before it was refreshed.
     */
    private void updateServiceProperties(ServiceDescriptionEntity serviceDescriptionEntity, List<ServiceEntity> newServiceEntities) {
        newServiceEntities
                .forEach(newServiceEntity -> {
                    String newServiceFullName = ServiceFormatter.getServiceFullName(newServiceEntity);
                    serviceDescriptionEntity.getServices().forEach(s -> {
                        if (newServiceFullName.equals(ServiceFormatter.getServiceFullName(s))) {
                            newServiceEntity.setUrl(s.getUrl());
                            newServiceEntity.setTimeout(s.getTimeout());
                            newServiceEntity.setSslAuthentication(s.getSslAuthentication());
                        }
                    });
                });
    }

    /**
     * @return warnings about adding or deleting endpoints
     */
    private List<WarningDeviation> createServiceChangeWarnings(EndpointEntityChangeChecker.ServiceChanges changes) {
        List<WarningDeviation> warnings = new ArrayList<>();
        if (!CollectionUtils.isEmpty(changes.getAddedEndpoints())) {
            warnings.add(new WarningDeviation(WARNING_ADDING_ENDPOINTS, changes.getAddedEndpointsCodes()));
        }
        if (!CollectionUtils.isEmpty(changes.getRemovedEndpoints())) {
            warnings.add(new WarningDeviation(WARNING_DELETING_ENDPOINTS, changes.getRemovedEndpointsCodes()));
        }
        return warnings;
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
    private void checkForExistingWsdl(ClientEntity client, String url,
                                      Long updatedServiceDescriptionId) throws WsdlUrlAlreadyExistsException {
        for (ServiceDescriptionEntity serviceDescription : client.getServiceDescriptions()) {
            if (!serviceDescription.getId().equals(updatedServiceDescriptionId)) {
                if (serviceDescription.getUrl().equalsIgnoreCase(url)) {
                    throw new WsdlUrlAlreadyExistsException("WSDL URL already exists");
                }
            }
        }
    }

    private ServiceDescriptionEntity buildWsdlServiceDescription(ClientEntity clientEntity,
                                                                 Collection<WsdlParser.ServiceInfo> parsedServices,
                                                                 String url) {
        ServiceDescriptionEntity serviceDescriptionEntity = getServiceDescriptionEntity(clientEntity, url, DescriptionType.WSDL);

        // create services
        List<ServiceEntity> newServices = parsedServices
                .stream()
                .map(serviceInfo -> serviceInfoToServiceEntity(serviceInfo, serviceDescriptionEntity))
                .toList();

        serviceDescriptionEntity.getServices().addAll(newServices);

        return serviceDescriptionEntity;
    }

    private ServiceDescriptionEntity getServiceDescriptionEntity(ClientEntity clientEntity, String url, DescriptionType descriptionType) {
        ServiceDescriptionEntity serviceDescriptionEntity = new ServiceDescriptionEntity();
        serviceDescriptionEntity.setClient(clientEntity);
        serviceDescriptionEntity.setDisabled(true);
        serviceDescriptionEntity.setDisabledNotice(DEFAULT_DISABLED_NOTICE);
        serviceDescriptionEntity.setRefreshedDate(new Date());
        serviceDescriptionEntity.setType(descriptionType);
        serviceDescriptionEntity.setUrl(url);
        return serviceDescriptionEntity;
    }

    private ServiceEntity serviceInfoToServiceEntity(WsdlParser.ServiceInfo serviceInfo,
                                                   ServiceDescriptionEntity serviceDescriptionEntity) {
        ServiceEntity newService = new ServiceEntity();
        newService.setServiceCode(serviceInfo.name);
        newService.setServiceVersion(serviceInfo.version);
        newService.setTitle(serviceInfo.title);
        newService.setUrl(serviceInfo.url);
        newService.setTimeout(DEFAULT_SERVICE_TIMEOUT);
        newService.setServiceDescription(serviceDescriptionEntity);
        return newService;
    }

    private Collection<WsdlParser.ServiceInfo> parseWsdl(String url) throws WsdlParser.WsdlNotFoundException,
            WsdlParser.WsdlParseException {
        Collection<WsdlParser.ServiceInfo> parsedServices;
        parsedServices = wsdlParser.parseWSDL(url);
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

    private List<ServiceEntity> getClientsExistingServices(ClientEntity clientEntity, Long idToSkip) {
        return clientEntity.getServiceDescriptions()
                .stream()
                .filter(serviceDescriptionEntity -> !Objects.equals(serviceDescriptionEntity.getId(), idToSkip))
                .map(ServiceDescriptionEntity::getServices)
                .flatMap(List::stream).toList();
    }

    /**
     * Check that the client does not have conflicting service codes
     * in other service descriptions. Throw exception if conflicts
     */
    private void checkForExistingServices(ClientEntity clientEntity,
                                          Collection<WsdlParser.ServiceInfo> parsedServices,
                                          Long idToSkip) throws ServiceAlreadyExistsException {
        List<ServiceEntity> existingServices = getClientsExistingServices(clientEntity, idToSkip);

        Set<ServiceEntity> conflictedServices = parsedServices
                .stream()
                .flatMap(newService -> existingServices
                        .stream()
                        .filter(existingService -> ServiceFormatter.getServiceFullName(existingService)
                                .equalsIgnoreCase(SecurityServerFormatUtils.getServiceFullName(newService))))
                .collect(Collectors.toSet());

        // throw error with service metadata if conflicted
        if (!conflictedServices.isEmpty()) {
            List<String> errorMetadata = new ArrayList<>();
            for (ServiceEntity conflictedService : conflictedServices) {
                // error metadata contains service name and service description url
                errorMetadata.add(ServiceFormatter.getServiceFullName(conflictedService));
                errorMetadata.add(conflictedService.getServiceDescription().getUrl());
            }
            throw new ServiceAlreadyExistsException(errorMetadata);
        }
    }

    @Data
    private static final class WsdlProcessingResult {
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
     * @param clientEntity                      client who is associated with the wsdl
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
    private WsdlProcessingResult processWsdl(ClientEntity clientEntity, String url,
                                             Long updatedServiceDescriptionId)
            throws WsdlParser.WsdlNotFoundException,
            InvalidWsdlException,
            InvalidUrlException,
            WsdlUrlAlreadyExistsException,
            ServiceAlreadyExistsException, InterruptedException {

        WsdlProcessingResult result = new WsdlProcessingResult();

        validateUrl(url);

        // check if wsdl already exists
        checkForExistingWsdl(clientEntity, url, updatedServiceDescriptionId);

        // parse wsdl
        Collection<WsdlParser.ServiceInfo> parsedServices = parseWsdl(url);

        // check that service identifiers are legal
        validateServiceIdentifierFields(parsedServices);

        // check if services exist
        checkForExistingServices(clientEntity, parsedServices, updatedServiceDescriptionId);

        // validate wsdl
        List<String> warningStrings;
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
     *                                           invalid service code or version
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

    public static class WrongServiceDescriptionException extends ServiceException {
        public WrongServiceDescriptionException(String s) {
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
