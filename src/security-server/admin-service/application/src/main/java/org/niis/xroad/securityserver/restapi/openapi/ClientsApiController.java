/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.restapi.util.ResourceUtils;
import org.niis.xroad.securityserver.restapi.controller.ServiceClientHelper;
import org.niis.xroad.securityserver.restapi.converter.AccessRightConverter;
import org.niis.xroad.securityserver.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.securityserver.restapi.converter.ClientConverter;
import org.niis.xroad.securityserver.restapi.converter.ConnectionTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.LocalGroupConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientIdentifierConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceClientTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.ServiceDescriptionConverter;
import org.niis.xroad.securityserver.restapi.converter.ServiceTypeMapping;
import org.niis.xroad.securityserver.restapi.converter.TokenCertificateConverter;
import org.niis.xroad.securityserver.restapi.dto.ServiceClient;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRightDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRightsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeWrapperDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OrphanInformationDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificateDto;
import org.niis.xroad.securityserver.restapi.service.AccessRightService;
import org.niis.xroad.securityserver.restapi.service.ActionNotPossibleException;
import org.niis.xroad.securityserver.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.securityserver.restapi.service.CertificateNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.service.GlobalConfOutdatedException;
import org.niis.xroad.securityserver.restapi.service.LocalGroupService;
import org.niis.xroad.securityserver.restapi.service.OrphanRemovalService;
import org.niis.xroad.securityserver.restapi.service.ServiceClientNotFoundException;
import org.niis.xroad.securityserver.restapi.service.ServiceClientService;
import org.niis.xroad.securityserver.restapi.service.ServiceDescriptionService;
import org.niis.xroad.securityserver.restapi.service.ServiceNotFoundException;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.model.Certificate;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.LocalGroup;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.cert.CertificateException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_CLIENT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_CLIENT_INTERNAL_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_LOCAL_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_SERVICE_CLIENT_ACCESS_RIGHTS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_SERVICE_DESCRIPTION;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_CLIENT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_CLIENT_INTERNAL_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_ORPHANS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DISABLE_CLIENT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ENABLE_CLIENT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REGISTER_CLIENT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REMOVE_SERVICE_CLIENT_ACCESS_RIGHTS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.SEND_OWNER_CHANGE_REQ;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.SET_CONNECTION_TYPE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UNREGISTER_CLIENT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DISABLED;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.REFRESHED_DATE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.UPLOAD_FILE_NAME;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_CERT;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_CONNECTION_TYPE;
import static org.niis.xroad.restapi.exceptions.ErrorDeviation.newError;
import static org.niis.xroad.restapi.openapi.ControllerUtil.createCreatedResponse;

/**
 * clients api
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ClientsApiController implements ClientsApi {
    private final ClientConverter clientConverter;
    private final ClientService clientService;
    private final LocalGroupConverter localGroupConverter;
    private final LocalGroupService localGroupService;
    private final TokenService tokenService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final ServiceDescriptionConverter serviceDescriptionConverter;
    private final ServiceDescriptionService serviceDescriptionService;
    private final AccessRightService accessRightService;
    private final TokenCertificateConverter tokenCertificateConverter;
    private final OrphanRemovalService orphanRemovalService;
    private final ServiceClientConverter serviceClientConverter;
    private final AccessRightConverter accessRightConverter;
    private final ServiceClientService serviceClientService;
    private final ServiceClientHelper serviceClientHelper;
    private final AuditDataHelper auditDataHelper;

    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    /**
     * Finds clients matching search terms
     * @param name
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without subsystemCode) in the results
     * @param localValidSignCert true = include only clients who have local valid sign cert (registered & OCSP good)
     *                              false = include only clients who don't have a local valid sign cert
     *                              null = don't care whether client has a local valid sign cert
     *                              NOTE: parameter does not have an effect on whether local or global clients are
     *                              searched
     * @param internalSearch search only in the local clients
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<Set<ClientDto>> findClients(String name, String instance, String memberClass,
                                                      String memberCode, String subsystemCode, Boolean showMembers, Boolean internalSearch,
                                                      Boolean localValidSignCert, Boolean excludeLocal) {
        ClientService.SearchParameters searchParams = ClientService.SearchParameters.builder()
                .name(name)
                .instance(instance)
                .memberClass(memberClass)
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .showMembers(showMembers)
                .internalSearch(internalSearch)
                .excludeLocal(excludeLocal)
                .hasValidLocalSignCert(localValidSignCert)
                .build();
        Set<ClientDto> clients = clientConverter.convert(clientService.findClients(searchParams));
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<ClientDto> getClient(String id) {
        Client client = getClientFromDb(id);
        ClientDto clientDto = clientConverter.convert(client);
        return new ResponseEntity<>(clientDto, HttpStatus.OK);
    }

    /**
     * Read one client from DB
     * @param encodedId id that is encoded with the <INSTANCE>:<MEMBER_CLASS>:....
     * encoding
     * @return Client
     * @throws ResourceNotFoundException if client does not exist
     * @throws BadRequestException if encodedId was not proper encoded client ID
     */
    private Client getClientFromDb(String encodedId) {
        ClientId clientId = clientIdConverter.convertId(encodedId);
        Client client = clientService.getLocalClient(clientId);
        if (client == null) {
            throw new ResourceNotFoundException("client with id " + encodedId + " not found");
        }
        return client;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<Set<TokenCertificateDto>> getClientSignCertificates(String encodedId) {
        Client client = getClientFromDb(encodedId);
        List<CertificateInfo> certificateInfos = tokenService.getSignCertificates(client);
        Set<TokenCertificateDto> certificates = new HashSet<>(tokenCertificateConverter.convert(certificateInfos));
        return new ResponseEntity<>(certificates, HttpStatus.OK);
    }

    /**
     * Update a client's connection type
     * @param encodedId
     * @param connectionTypeWrapper wrapper object containing the connection type to set
     * @return
     */
    @PreAuthorize("hasAuthority('EDIT_CLIENT_INTERNAL_CONNECTION_TYPE')")
    @Override
    @AuditEventMethod(event = SET_CONNECTION_TYPE)
    public ResponseEntity<ClientDto> updateClient(String encodedId, ConnectionTypeWrapperDto connectionTypeWrapper) {
        if (connectionTypeWrapper == null || connectionTypeWrapper.getConnectionType() == null) {
            throw new BadRequestException();
        }
        ConnectionTypeDto connectionType = connectionTypeWrapper.getConnectionType();
        ClientId clientId = clientIdConverter.convertId(encodedId);
        String connectionTypeString = ConnectionTypeMapping.map(connectionType).get().name();
        Client changed = null;
        try {
            changed = clientService.updateConnectionType(clientId, connectionTypeString);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        ClientDto result = clientConverter.convert(changed);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_CLIENT_INTERNAL_CERT')")
    @AuditEventMethod(event = ADD_CLIENT_INTERNAL_CERT)
    public ResponseEntity<CertificateDetailsDto> addClientTlsCertificate(String encodedId,
                                                                         Resource body) {
        // there's no filename since we only get a binary application/octet-stream.
        // Have audit log anyway (null behaves as no-op) in case different content type is added later
        String filename = body.getFilename();
        auditDataHelper.put(UPLOAD_FILE_NAME, filename);

        byte[] certificateBytes = ResourceUtils.springResourceToBytesOrThrowBadRequest(body);
        ClientId clientId = clientIdConverter.convertId(encodedId);
        Certificate certificate = null;
        try {
            certificate = clientService.addTlsCertificate(clientId, certificateBytes);
        } catch (CertificateException c) {
            throw new BadRequestException(c, new ErrorDeviation(ERROR_INVALID_CERT));
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (CertificateAlreadyExistsException e) {
            throw new ConflictException(e);
        }
        CertificateDetailsDto certificateDetails = certificateDetailsConverter.convert(certificate);
        return createCreatedResponse("/api/clients/{id}/tls-certificates/{hash}", certificateDetails, encodedId,
                certificateDetails.getHash());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT_INTERNAL_CERT')")
    @AuditEventMethod(event = DELETE_CLIENT_INTERNAL_CERT)
    public ResponseEntity<Void> deleteClientTlsCertificate(String encodedId, String hash) {
        ClientId clientId = clientIdConverter.convertId(encodedId);
        try {
            clientService.deleteTlsCertificate(clientId, hash);
        } catch (ClientNotFoundException | CertificateNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERT_DETAILS')")
    public ResponseEntity<CertificateDetailsDto> getClientTlsCertificate(String encodedId, String certHash) {
        ClientId clientId = clientIdConverter.convertId(encodedId);
        Optional<Certificate> certificate;
        try {
            certificate = clientService.getTlsCertificate(clientId, certHash);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        if (!certificate.isPresent()) {
            throw new ResourceNotFoundException("certificate with hash " + certHash
                    + ", client id " + encodedId + " not found");
        }
        return new ResponseEntity<>(certificateDetailsConverter.convert(certificate.get()), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERTS')")
    public ResponseEntity<Set<CertificateDetailsDto>> getClientTlsCertificates(String encodedId) {
        Client client = getClientFromDb(encodedId);
        Set<CertificateDetailsDto> certificates = clientService.getLocalClientIsCerts(client.getIdentifier())
                .stream()
                .map(certificateDetailsConverter::convert)
                .collect(toSet());
        return new ResponseEntity<>(certificates, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_LOCAL_GROUP')")
    @AuditEventMethod(event = ADD_LOCAL_GROUP)
    public ResponseEntity<LocalGroupDto> addClientLocalGroup(String id, LocalGroupAddDto localGroupAdd) {
        Client client = getClientFromDb(id);
        LocalGroup localGroup = null;
        try {
            localGroup = localGroupService.addLocalGroup(client.getIdentifier(),
                    localGroupConverter.convert(localGroupAdd));
        } catch (LocalGroupService.DuplicateLocalGroupCodeException e) {
            throw new ConflictException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        LocalGroupDto createdGroupDto = localGroupConverter.convert(localGroup);
        return createCreatedResponse("/api/local-groups/{id}", createdGroupDto, localGroup.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_LOCAL_GROUPS')")
    public ResponseEntity<Set<LocalGroupDto>> getClientLocalGroups(String encodedId) {
        Client client = getClientFromDb(encodedId);
        List<LocalGroup> localGroups = clientService.getLocalClientLocalGroups(client.getIdentifier());
        Set<LocalGroupDto> localGroupDtos = localGroupConverter.convert(localGroups);
        return new ResponseEntity<>(localGroupDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<Set<ServiceDescriptionDto>> getClientServiceDescriptions(String encodedId) {
        Client client = getClientFromDb(encodedId);
        Set<ServiceDescriptionDto> serviceDescriptionDtos = serviceDescriptionConverter.convert(
                clientService.getLocalClientServiceDescriptions(client.getIdentifier()));

        return new ResponseEntity<>(serviceDescriptionDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('ADD_WSDL', 'ADD_OPENAPI3')")
    @AuditEventMethod(event = ADD_SERVICE_DESCRIPTION)
    public ResponseEntity<ServiceDescriptionDto> addClientServiceDescription(String id,
                                                                          ServiceDescriptionAddDto serviceDescriptionAddDto) {
        ClientId clientId = clientIdConverter.convertId(id);
        String url = serviceDescriptionAddDto.getUrl();
        boolean ignoreWarnings = serviceDescriptionAddDto.getIgnoreWarnings();
        String restServiceCode = serviceDescriptionAddDto.getRestServiceCode();

        // audit logging from controller works better here since logic is split into 3 different methods
        auditDataHelper.put(clientId);
        auditDataHelper.putServiceDescriptionUrl(url, ServiceTypeMapping.map(serviceDescriptionAddDto.getType()));

        ServiceDescriptionDto addedServiceDescriptionDto = serviceDescriptionService.addServiceDescription(
                        ServiceTypeMapping.map(serviceDescriptionAddDto.getType()),
                        clientId,
                        url,
                        restServiceCode,
                        ignoreWarnings);

        auditDataHelper.put(DISABLED, addedServiceDescriptionDto.getDisabled());
        auditDataHelper.putDateTime(REFRESHED_DATE, addedServiceDescriptionDto.getRefreshedAt());

        return createCreatedResponse("/api/service-descriptions/{id}", addedServiceDescriptionDto,
                addedServiceDescriptionDto.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<Set<ServiceClientDto>> findServiceClientCandidates(String encodedClientId,
                                                                             String memberNameOrGroupDescription,
                                                                             ServiceClientTypeDto serviceClientTypeDto,
                                                                             String instance,
                                                                             String memberClass,
                                                                             String memberGroupCode,
                                                                             String subsystemCode) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        XRoadObjectType xRoadObjectType = ServiceClientTypeMapping.map(serviceClientTypeDto).orElse(null);
        List<ServiceClient> serviceClients = null;
        try {
            serviceClients = accessRightService.findAccessRightHolderCandidates(clientId,
                    memberNameOrGroupDescription, xRoadObjectType, instance, memberClass, memberGroupCode,
                    subsystemCode);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        Set<ServiceClientDto> serviceClientDtos = serviceClientConverter.convertServiceClientDtos(serviceClients);

        return new ResponseEntity<>(serviceClientDtos, HttpStatus.OK);
    }

    /**
     * This method is synchronized (like client add in old Ruby implementation)
     * to prevent a problem with two threads both creating "first" additional members.
     */
    @Override
    @PreAuthorize("hasAuthority('ADD_CLIENT')")
    @AuditEventMethod(event = ADD_CLIENT)
    public synchronized ResponseEntity<ClientDto> addClient(ClientAddDto clientAddDto) {
        boolean ignoreWarnings = clientAddDto.getIgnoreWarnings();
        IsAuthentication isAuthentication = null;

        try {
            isAuthentication = ConnectionTypeMapping.map(clientAddDto.getClient().getConnectionType()).get();
        } catch (Exception e) {
            throw new BadRequestException(e, newError(ERROR_INVALID_CONNECTION_TYPE));
        }
        Client added = null;
        try {
            added = clientService.addLocalClient(clientAddDto.getClient().getMemberClass(),
                    clientAddDto.getClient().getMemberCode(),
                    clientAddDto.getClient().getSubsystemCode(),
                    isAuthentication, ignoreWarnings);
        } catch (ClientService.ClientAlreadyExistsException
                 | ClientService.AdditionalMemberAlreadyExistsException e) {
            throw new ConflictException(e);
        } catch (UnhandledWarningsException | ClientService.InvalidMemberClassException e) {
            throw new BadRequestException(e);
        }
        ClientDto result = clientConverter.convert(added);

        return createCreatedResponse("/api/clients/{id}", result, result.getId());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    @AuditEventMethod(event = DELETE_CLIENT)
    public ResponseEntity<Void> deleteClient(String encodedClientId) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        try {
            clientService.deleteLocalClient(clientId);
        } catch (ActionNotPossibleException | ClientService.CannotDeleteOwnerException e) {
            throw new ConflictException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    @AuditEventMethod(event = DELETE_ORPHANS)
    public ResponseEntity<Void> deleteOrphans(String encodedClientId) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        try {
            orphanRemovalService.deleteOrphans(clientId);
        } catch (OrphanRemovalService.OrphansNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT')")
    public ResponseEntity<OrphanInformationDto> getClientOrphans(String encodedClientId) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        boolean orphansExist = orphanRemovalService.orphansExist(clientId);
        if (orphansExist) {
            OrphanInformationDto info = new OrphanInformationDto().orphansExist(true);
            return new ResponseEntity<>(info, HttpStatus.OK);
        } else {
            throw new ResourceNotFoundException();
        }
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_CLIENT_REG_REQ')")
    @AuditEventMethod(event = REGISTER_CLIENT)
    public ResponseEntity<Void> registerClient(String encodedClientId) {
        ClientId.Conf clientId = clientIdConverter.convertId(encodedClientId);
        try {
            clientService.registerClient(clientId);
        } catch (ClientService.CannotRegisterOwnerException
                 | ClientService.InvalidMemberClassException | ClientService.InvalidInstanceIdentifierException e) {
            throw new BadRequestException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_CLIENT_DEL_REQ')")
    @AuditEventMethod(event = UNREGISTER_CLIENT)
    public ResponseEntity<Void> unregisterClient(String encodedClientId) {
        ClientId.Conf clientId = clientIdConverter.convertId(encodedClientId);
        try {
            clientService.unregisterClient(clientId);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException
                 | ClientService.CannotUnregisterOwnerException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('SEND_OWNER_CHANGE_REQ')")
    @AuditEventMethod(event = SEND_OWNER_CHANGE_REQ)
    public ResponseEntity<Void> changeOwner(String encodedClientId) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        try {
            clientService.changeOwner(clientId.getMemberClass(), clientId.getMemberCode(),
                    clientId.getSubsystemCode());
        } catch (ClientService.MemberAlreadyOwnerException e) {
            throw new BadRequestException(e);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('DISABLE_CLIENT')")
    @AuditEventMethod(event = DISABLE_CLIENT)
    public ResponseEntity<Void> disableClient(String encodedClientId) {
        ClientId.Conf clientId = clientIdConverter.convertId(encodedClientId);
        try {
            clientService.disableClient(clientId);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException
                 | ClientService.CannotUnregisterOwnerException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('ENABLE_CLIENT')")
    @AuditEventMethod(event = ENABLE_CLIENT)
    public ResponseEntity<Void> enableClient(String encodedClientId) {
        ClientId.Conf clientId = clientIdConverter.convertId(encodedClientId);
        try {
            clientService.enableClient(clientId);
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (GlobalConfOutdatedException | ActionNotPossibleException
                 | ClientService.CannotUnregisterOwnerException e) {
            throw new ConflictException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<Set<ServiceClientDto>> getClientServiceClients(String id) {
        ClientId clientId = clientIdConverter.convertId(id);
        Set<ServiceClientDto> serviceClientDtos = null;
        try {
            serviceClientDtos = serviceClientConverter.
                    convertServiceClientDtos(serviceClientService.getServiceClientsByClient(clientId));
        } catch (ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        return new ResponseEntity<>(serviceClientDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public ResponseEntity<ServiceClientDto> getServiceClient(String id, String scId) {
        ClientId clientIdentifier = clientIdConverter.convertId(id);
        ServiceClientDto serviceClientDto = null;
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(scId);
            serviceClientDto = serviceClientConverter.convertServiceClientDto(
                    serviceClientService.getServiceClient(clientIdentifier, serviceClientId));
        } catch (ClientNotFoundException | ServiceClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }

        return new ResponseEntity<>(serviceClientDto, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_ACL_SUBJECT_OPEN_SERVICES')")
    public ResponseEntity<Set<AccessRightDto>> getServiceClientAccessRights(String id, String scId) {
        ClientId clientIdentifier = clientIdConverter.convertId(id);
        Set<AccessRightDto> accessRightDtos = null;
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(scId);
            accessRightDtos = accessRightConverter.convert(
                    serviceClientService.getServiceClientAccessRights(clientIdentifier, serviceClientId));
        } catch (ServiceClientNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(accessRightDtos, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ACL_SUBJECT_OPEN_SERVICES')")
    @AuditEventMethod(event = ADD_SERVICE_CLIENT_ACCESS_RIGHTS)
    public ResponseEntity<Set<AccessRightDto>> addServiceClientAccessRights(String encodedClientId,
                                                                         String endcodedServiceClientId, AccessRightsDto accessRightDtos) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        Set<String> serviceCodes = getServiceCodes(accessRightDtos);
        List<ServiceClientAccessRightDto> serviceClientAccessRightDtos = null;
        try {
            XRoadId.Conf serviceClientId = serviceClientHelper.processServiceClientXRoadId(endcodedServiceClientId);
            serviceClientAccessRightDtos = accessRightService.addServiceClientAccessRights(clientId, serviceCodes, serviceClientId);
        } catch (ServiceClientNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceNotFoundException e) {
            throw new BadRequestException(e);
        } catch (AccessRightService.DuplicateAccessRightException e) {
            throw new ConflictException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(accessRightConverter.convert(serviceClientAccessRightDtos), HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_ACL_SUBJECT_OPEN_SERVICES')")
    @AuditEventMethod(event = REMOVE_SERVICE_CLIENT_ACCESS_RIGHTS)
    public ResponseEntity<Void> deleteServiceClientAccessRights(String encodedClientId,
                                                                String endcodedServiceClientId, AccessRightsDto accessRightDtos) {
        ClientId clientId = clientIdConverter.convertId(encodedClientId);
        Set<String> serviceCodes = getServiceCodes(accessRightDtos);
        try {
            XRoadId serviceClientId = serviceClientHelper.processServiceClientXRoadId(endcodedServiceClientId);
            accessRightService.deleteServiceClientAccessRights(clientId, serviceCodes, serviceClientId);
        } catch (ServiceClientNotFoundException | ClientNotFoundException e) {
            throw new ResourceNotFoundException(e);
        } catch (ServiceNotFoundException e) {
            throw new BadRequestException(e);
        } catch (AccessRightService.AccessRightNotFoundException e) {
            throw new ConflictException(e);
        } catch (ServiceClientIdentifierConverter.BadServiceClientIdentifierException e) {
            throw serviceClientHelper.wrapInBadRequestException(e);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    private Set<String> getServiceCodes(AccessRightsDto accessRightDtos) {
        Set<String> serviceCodes = new HashSet<>();
        for (AccessRightDto accessRightDto : accessRightDtos.getItems()) {
            serviceCodes.add(accessRightDto.getServiceCode());
        }
        return serviceCodes;
    }
}
