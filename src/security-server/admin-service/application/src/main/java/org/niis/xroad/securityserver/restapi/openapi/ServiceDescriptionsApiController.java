/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.InternalServerErrorException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.converter.ServiceConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceDescriptionConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.IgnoreWarnings;
import org.niis.xroad.securityserver.restapi.openapi.model.Service;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionDisabledNotice;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceType;
import org.niis.xroad.securityserver.restapi.service.InvalidServiceUrlException;
import org.niis.xroad.securityserver.restapi.service.InvalidUrlException;
import org.niis.xroad.securityserver.restapi.service.ServiceDescriptionNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceDescriptionService;
import org.niis.xroad.securityserver.restapi.wsdl.InvalidWsdlException;
import org.niis.xroad.securityserver.restapi.wsdl.OpenApiParser;
import org.niis.xroad.securityserver.restapi.wsdl.UnsupportedOpenApiVersionException;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_SERVICE_DESCRIPTION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DISABLE_SERVICE_DESCRIPTION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_SERVICE_DESCRIPTION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ENABLE_SERVICE_DESCRIPTION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REFRESH_SERVICE_DESCRIPTION;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WSDL_VALIDATOR_INTERRUPTED;

/**
 * service descriptions api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ServiceDescriptionsApiController implements ServiceDescriptionsApi {
    private final ServiceDescriptionService serviceDescriptionService;
    private final ServiceDescriptionConverter serviceDescriptionConverter;
    private final ServiceConverter serviceConverter;

    @Override
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    @AuditEventMethod(event = ENABLE_SERVICE_DESCRIPTION)
    public ResponseEntity<Void> enableServiceDescription(String id) {
        Long serviceDescriptionId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            serviceDescriptionService.enableServices(serviceDescriptionId.longValue());
        } catch (ServiceDescriptionNotFoundException e) {
            throw new ResourceNotFoundException();
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ENABLE_DISABLE_WSDL')")
    @AuditEventMethod(event = DISABLE_SERVICE_DESCRIPTION)
    public ResponseEntity<Void> disableServiceDescription(String id,
                                                          ServiceDescriptionDisabledNotice serviceDescriptionDisabledNotice) {
        String disabledNotice = null;
        if (serviceDescriptionDisabledNotice != null) {
            disabledNotice = serviceDescriptionDisabledNotice.getDisabledNotice();
        }
        Long serviceDescriptionId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            serviceDescriptionService.disableServices(serviceDescriptionId.longValue(),
                    disabledNotice);
        } catch (ServiceDescriptionNotFoundException e) {
            throw new ResourceNotFoundException();
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_WSDL')")
    @AuditEventMethod(event = DELETE_SERVICE_DESCRIPTION)
    public ResponseEntity<Void> deleteServiceDescription(String id) {
        Long serviceDescriptionId = FormatUtils.parseLongIdOrThrowNotFound(id);
        try {
            serviceDescriptionService.deleteServiceDescription(serviceDescriptionId);
        } catch (ServiceDescriptionNotFoundException e) {
            throw new ResourceNotFoundException();
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('EDIT_WSDL', 'EDIT_OPENAPI3', 'EDIT_REST')")
    @AuditEventMethod(event = EDIT_SERVICE_DESCRIPTION)
    public ResponseEntity<ServiceDescription> updateServiceDescription(String id,
                                                                       ServiceDescriptionUpdate serviceDescriptionUpdate) {
        Long serviceDescriptionId = FormatUtils.parseLongIdOrThrowNotFound(id);
        ServiceDescriptionType updatedServiceDescription = null;

        try {

            if (serviceDescriptionUpdate.getType() == ServiceType.WSDL) {
                updatedServiceDescription = serviceDescriptionService.updateWsdlUrl(
                        serviceDescriptionId, serviceDescriptionUpdate.getUrl(),
                        serviceDescriptionUpdate.getIgnoreWarnings());
            } else if (serviceDescriptionUpdate.getType() == ServiceType.OPENAPI3) {
                if (serviceDescriptionUpdate.getRestServiceCode() == null) {
                    throw new BadRequestException("Missing parameter rest_service_code");
                }
                updatedServiceDescription =
                        serviceDescriptionService.updateOpenApi3ServiceDescription(serviceDescriptionId,
                                serviceDescriptionUpdate.getUrl(), serviceDescriptionUpdate.getRestServiceCode(),
                                serviceDescriptionUpdate.getNewRestServiceCode(),
                                serviceDescriptionUpdate.getIgnoreWarnings());
            } else if (serviceDescriptionUpdate.getType() == ServiceType.REST) {
                if (serviceDescriptionUpdate.getRestServiceCode() == null) {
                    throw new BadRequestException("Missing parameter rest_service_code");
                }
                updatedServiceDescription = serviceDescriptionService.updateRestServiceDescription(serviceDescriptionId,
                        serviceDescriptionUpdate.getUrl(), serviceDescriptionUpdate.getRestServiceCode(),
                        serviceDescriptionUpdate.getNewRestServiceCode());
            } else {
                throw new BadRequestException("ServiceType not recognized");
            }

        } catch (WsdlParser.WsdlNotFoundException | OpenApiParser.ParsingException | UnhandledWarningsException
                 | InvalidUrlException | ServiceDescriptionService.WrongServiceDescriptionTypeException
                 | InvalidWsdlException | InvalidServiceUrlException | UnsupportedOpenApiVersionException e) {
            throw new BadRequestException(e);
        } catch (ServiceDescriptionService.ServiceAlreadyExistsException
                 | ServiceDescriptionService.WsdlUrlAlreadyExistsException
                 | ServiceDescriptionService.UrlAlreadyExistsException
                 | ServiceDescriptionService.ServiceCodeAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (ServiceDescriptionNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(new ErrorDeviation(ERROR_WSDL_VALIDATOR_INTERRUPTED));
        }

        ServiceDescription serviceDescription = serviceDescriptionConverter.convert(updatedServiceDescription);
        return new ResponseEntity<>(serviceDescription, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('REFRESH_WSDL', 'REFRESH_REST', 'REFRESH_OPENAPI3')")
    @AuditEventMethod(event = REFRESH_SERVICE_DESCRIPTION)
    public ResponseEntity<ServiceDescription> refreshServiceDescription(String id, IgnoreWarnings ignoreWarnings) {
        Long serviceDescriptionId = FormatUtils.parseLongIdOrThrowNotFound(id);
        ServiceDescription serviceDescription = null;
        try {
            serviceDescription = serviceDescriptionConverter.convert(
                    serviceDescriptionService.refreshServiceDescription(serviceDescriptionId,
                            ignoreWarnings.getIgnoreWarnings()));
        } catch (WsdlParser.WsdlNotFoundException | UnhandledWarningsException | InvalidUrlException
                 | InvalidWsdlException | ServiceDescriptionService.WrongServiceDescriptionTypeException
                 | OpenApiParser.ParsingException | InvalidServiceUrlException | UnsupportedOpenApiVersionException e) {
            throw new BadRequestException(e);
        } catch (ServiceDescriptionService.ServiceAlreadyExistsException
                 | ServiceDescriptionService.WsdlUrlAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (ServiceDescriptionNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (InterruptedException e) {
            throw new InternalServerErrorException(new ErrorDeviation(ERROR_WSDL_VALIDATOR_INTERRUPTED));
        }
        return new ResponseEntity<>(serviceDescription, HttpStatus.OK);
    }

    /**
     * Returns one service description, using primary key id.
     * {@inheritDoc}
     *
     * @param id primary key of service description
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<ServiceDescription> getServiceDescription(String id) {
        ServiceDescriptionType serviceDescriptionType =
                getServiceDescriptionType(id);
        return new ResponseEntity<>(
                serviceDescriptionConverter.convert(serviceDescriptionType),
                HttpStatus.OK);
    }

    /**
     * Returns services of one service description.
     * Id = primary key of service description.
     * {@inheritDoc}
     *
     * @param id primary key of service description
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<Set<Service>> getServiceDescriptionServices(String id) {
        ServiceDescriptionType serviceDescriptionType =
                getServiceDescriptionType(id);
        ClientId clientId = serviceDescriptionType.getClient().getIdentifier();
        Set<Service> services = serviceDescriptionType.getService().stream()
                .map(serviceType -> serviceConverter.convert(serviceType, clientId))
                .collect(Collectors.toSet());
        return new ResponseEntity<>(services, HttpStatus.OK);
    }

    /**
     * return matching ServiceDescriptionType, or throw ResourceNotFoundException
     */
    private ServiceDescriptionType getServiceDescriptionType(String id) {
        Long serviceDescriptionId = FormatUtils.parseLongIdOrThrowNotFound(id);
        ServiceDescriptionType serviceDescriptionType =
                serviceDescriptionService.getServiceDescriptiontype(serviceDescriptionId);
        if (serviceDescriptionType == null) {
            throw new ResourceNotFoundException();
        }
        return serviceDescriptionType;
    }


}
