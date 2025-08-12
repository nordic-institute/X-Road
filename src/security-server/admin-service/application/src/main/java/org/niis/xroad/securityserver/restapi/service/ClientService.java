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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.niis.xroad.common.core.exception.WarningDeviation;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.cache.SubsystemNameStatus;
import org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage;
import org.niis.xroad.securityserver.restapi.repository.AccessRightRepository;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.IdentifierRepository;
import org.niis.xroad.securityserver.restapi.repository.LocalGroupRepository;
import org.niis.xroad.securityserver.restapi.util.ClientUtils;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.impl.entity.CertificateEntity;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.ClientIdEntity;
import org.niis.xroad.serverconf.impl.entity.LocalGroupEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.impl.entity.ServiceDescriptionEntity;
import org.niis.xroad.serverconf.impl.mapper.CertificateMapper;
import org.niis.xroad.serverconf.impl.mapper.ClientMapper;
import org.niis.xroad.serverconf.impl.mapper.LocalGroupMapper;
import org.niis.xroad.serverconf.impl.mapper.ServiceDescriptionMapper;
import org.niis.xroad.serverconf.impl.mapper.XRoadIdMapper;
import org.niis.xroad.serverconf.model.Certificate;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.LocalGroup;
import org.niis.xroad.serverconf.model.ServiceDescription;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.niis.xroad.common.core.exception.ErrorCodes.INVALID_CLIENT_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_SUBSYSTEM_NAME;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_UNREGISTERED_MEMBER;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.ADDITIONAL_MEMBER_ALREADY_EXISTS;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CANNOT_DELETE_OWNER;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CANNOT_MAKE_OWNER;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CANNOT_REGISTER_OWNER;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CANNOT_UNREGISTER_OWNER;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CLIENT_ALREADY_EXISTS;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.CLIENT_RENAME_ALREADY_SUBMITTED;
import static org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage.FORBIDDEN_DISABLE_MANAGEMENT_SERVICE_CLIENT;
import static org.niis.xroad.securityserver.restapi.util.ClientUtils.doesSupportSubsystemNames;
import static org.niis.xroad.serverconf.model.Client.STATUS_DELINPROG;
import static org.niis.xroad.serverconf.model.Client.STATUS_DISABLED;
import static org.niis.xroad.serverconf.model.Client.STATUS_DISABLING_INPROG;
import static org.niis.xroad.serverconf.model.Client.STATUS_ENABLING_INPROG;
import static org.niis.xroad.serverconf.model.Client.STATUS_GLOBALERR;
import static org.niis.xroad.serverconf.model.Client.STATUS_REGINPROG;
import static org.niis.xroad.serverconf.model.Client.STATUS_REGISTERED;
import static org.niis.xroad.serverconf.model.Client.STATUS_SAVED;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ClientService {
    private static final String INVALID_INSTANCE_IDENTIFIER = "instance identifier is invalid: ";
    private static final String INVALID_MEMBER_CLASS = "member class is invalid: ";

    private final ClientRepository clientRepository;
    private final GlobalConfService globalConfService;
    private final GlobalConfProvider globalConfProvider;
    private final ServerConfService serverConfService;
    private final IdentifierService identifierService;
    private final IdentifierRepository identifierRepository;
    private final LocalGroupRepository localGroupRepository;
    private final AccessRightRepository accessRightRepository;
    private final ManagementRequestSenderService managementRequestSenderService;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final SubsystemNameStatus subsystemNameStatus;
    private final AuditDataHelper auditDataHelper;

    // request scoped contains all certificates of type sign
    private final CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;

    /**
     * return all clients that exist on this security server
     * @return
     */
    public List<Client> getAllLocalClients() {
        return ClientMapper.get().toTargets(clientRepository.getAllLocalClients());
    }

    List<ClientEntity> getAllLocalClientEntities() {
        return clientRepository.getAllLocalClients();
    }

    /**
     * return all members that exist on this security server.
     * There can only be 0, 1 or 2 members
     * @return
     */
    List<ClientEntity> getAllLocalMemberEntities() {
        return getAllLocalClientEntities().stream()
                .filter(ct -> ct.getIdentifier().getSubsystemCode() == null)
                .collect(Collectors.toList());
    }

    /**
     * Return ClientId for all members who have clients on this instance.
     * For example if following clients exist:
     * - XRD:GOV:123 (owner member)
     * - XRD:GOV:123:SS1 (subsystem)
     * - XRD:COM:FOO:SS1 (subsystem)
     * method will return
     * - XRD:GOV:123 (owner member)
     * - XRD:COM:FOO (client subsystem's member)
     * @return
     */
    public Set<ClientId> getLocalClientMemberIds() {
        List<ClientEntity> allClients = getAllLocalClientEntities();
        Set<ClientId> members = new HashSet<>();
        for (ClientEntity client : allClients) {
            ClientId id = client.getIdentifier();
            members.add(ClientId.Conf.create(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode()));
        }
        return members;
    }

    Set<ClientId> getAllClientIds() {
        List<ClientEntity> localClients = getAllLocalClientEntities();
        List<ClientEntity> globalClients = getAllGlobalClientEntities();

        return Stream.concat(localClients.stream(), globalClients.stream())
                .map(ClientEntity::getIdentifier)
                .collect(Collectors.toSet());
    }

    /**
     * return all global clients as ClientTypes
     * @return
     */
    private List<ClientEntity> getAllGlobalClientEntities() {
        return globalConfProvider.getMembers()
                .stream()
                .map(memberInfo -> {
                    ClientEntity clientEntity = new ClientEntity();
                    clientEntity.setIdentifier(XRoadIdMapper.get().toEntity(memberInfo.id()));
                    return clientEntity;
                })
                .collect(Collectors.toList());
    }

    /**
     * Return one client, or null if not found.
     * This method does NOT trigger load of lazy loaded properties.
     * Use {@code getLocalClientIsCerts}, {@code getLocalClientLocalGroups}, and
     * {@code getLocalClientServiceDescriptions} for that
     * @param id
     * @return the client, or null if matching client was not found
     */
    public Client getLocalClient(ClientId id) {
        return ClientMapper.get().toTarget(getLocalClientEntity(id));
    }

    public ClientEntity getLocalClientEntity(ClientId id) {
        return clientRepository.getClient(id);
    }

    /**
     * Returns clientEntity.getCertificates() that has been fetched with Hibernate.init.
     * @param id client id
     * @return list of Certificate, or null if client does not exist
     */
    public List<Certificate> getLocalClientIsCerts(ClientId id) {
        ClientEntity clientEntity = getLocalClientEntity(id);
        if (clientEntity != null) {
            Hibernate.initialize(clientEntity.getCertificates());
            return CertificateMapper.get().toTargets(clientEntity.getCertificates());
        }
        return null;
    }

    /**
     * Returns client.getServiceDescription() that has been fetched with Hibernate.init.
     * Also serviceDescription.services and serviceDescription.client.endpoints have been fetched.
     * @param id
     * @return list of ServiceDescription, or null if client does not exist
     */
    public List<ServiceDescription> getLocalClientServiceDescriptions(ClientId id) {
        ClientEntity clientEntity = getLocalClientEntity(id);
        if (clientEntity != null) {
            for (ServiceDescriptionEntity serviceDescriptionEntity : clientEntity.getServiceDescriptions()) {
                Hibernate.initialize(serviceDescriptionEntity.getServices());
            }
            Hibernate.initialize(clientEntity.getEndpoints());
            return ServiceDescriptionMapper.get().toTargets(clientEntity.getServiceDescriptions());
        }
        return null;
    }

    /**
     * Returns client.getLocalGroup() that has been fetched with Hibernate.init.
     * Also localGroup.groupMembers have been fetched.
     * @param id id
     * @return list of LocalGroup, or null if client does not exist
     */
    public List<LocalGroup> getLocalClientLocalGroups(ClientId id) {
        ClientEntity clientEntity = getLocalClientEntity(id);
        if (clientEntity != null) {
            for (LocalGroupEntity localGroupEntity : clientEntity.getLocalGroups()) {
                Hibernate.initialize(localGroupEntity.getGroupMembers());
            }
            return LocalGroupMapper.get().toTargets(clientEntity.getLocalGroups());
        }
        return null;
    }

    /**
     * Update connection type of an existing client
     * @param id
     * @param connectionType
     * @return
     * @throws IllegalArgumentException if connectionType was not supported value
     * @throws ClientNotFoundException  if client was not found
     */
    public Client updateConnectionType(ClientId id, String connectionType) throws ClientNotFoundException {
        auditDataHelper.put(id);
        ClientEntity clientEntity = getLocalClientEntityOrThrowNotFound(id);
        // validate connectionType param by creating enum out of it
        IsAuthentication enumValue = IsAuthentication.valueOf(connectionType);
        auditDataHelper.put(enumValue);
        clientEntity.setIsAuthentication(connectionType);
        return ClientMapper.get().toTarget(clientEntity);
    }

    /**
     * Get a local client, throw exception if not found
     * @throws ClientNotFoundException if not found
     */
    ClientEntity getLocalClientEntityOrThrowNotFound(ClientId id) throws ClientNotFoundException {
        ClientEntity clientEntity = getLocalClientEntity(id);
        if (clientEntity == null) {
            throw new ClientNotFoundException("client with id " + id + " not found");
        }
        return clientEntity;
    }

    /**
     * @param id
     * @param certBytes either PEM or DER -encoded certificate
     * @return created Certificate with id populated
     * @throws CertificateException              if certBytes was not a valid PEM or DER encoded certificate
     * @throws CertificateAlreadyExistsException if certificate already exists
     * @throws ClientNotFoundException           if client was not found
     */
    public Certificate addTlsCertificate(ClientId id, byte[] certBytes)
            throws CertificateException, CertificateAlreadyExistsException, ClientNotFoundException {
        auditDataHelper.put(id);
        X509Certificate x509Certificate;
        try {
            x509Certificate = CryptoUtils.readCertificate(certBytes);
        } catch (Exception e) {
            throw new CertificateException("cannot convert bytes to certificate", e);
        }
        String hash = calculateCertHexHash(x509Certificate);
        CertificateEntity certificateEntity = new CertificateEntity();
        try {
            certificateEntity.setData(x509Certificate.getEncoded());
        } catch (CertificateEncodingException ex) {
            // client cannot do anything about this
            throw new RuntimeException(ex);
        }
        putCertificateHashToAudit(certificateEntity);

        ClientEntity clientEntity = getLocalClientEntityOrThrowNotFound(id);
        Optional<CertificateEntity> duplicate = clientEntity.getCertificates().stream()
                .filter(cert -> hash.equalsIgnoreCase(calculateCertHexHash(cert.getData())))
                .findFirst();
        if (duplicate.isPresent()) {
            throw new CertificateAlreadyExistsException("certificate already exists");
        }

        clientEntity.getCertificates().add(certificateEntity);
        return CertificateMapper.get().toTarget(certificateEntity);
    }

    private void putCertificateHashToAudit(CertificateEntity certificateEntity) {
        if (certificateEntity != null) {
            auditDataHelper.putCertificateHashAndAlgorithm(certificateEntity.getData());
        }
    }

    /**
     * Convenience / cleanness wrapper
     */
    private String calculateCertHexHash(X509Certificate cert) {
        try {
            return CryptoUtils.calculateCertHexHash(cert);
        } catch (Exception e) {
            throw new RuntimeException("hash calculation failed", e);
        }
    }

    /**
     * Convenience / cleanness wrapper
     */
    private String calculateCertHexHash(byte[] certBytes) {
        try {
            return CryptoUtils.calculateCertHexHash(certBytes);
        } catch (Exception e) {
            throw new RuntimeException("hash calculation failed", e);
        }
    }

    /**
     * Deletes one (and should be the only) certificate with
     * matching hash
     * @param id
     * @param certificateHash
     * @return
     * @throws ClientNotFoundException      if client was not found
     * @throws CertificateNotFoundException if certificate was not found
     */
    public void deleteTlsCertificate(ClientId id, String certificateHash)
            throws ClientNotFoundException, CertificateNotFoundException {

        auditDataHelper.put(id);

        ClientEntity clientEntity = getLocalClientEntityOrThrowNotFound(id);
        Optional<CertificateEntity> certificateEntity = clientEntity.getCertificates().stream()
                .filter(certificate -> calculateCertHexHash(certificate.getData()).equalsIgnoreCase(certificateHash))
                .findAny();
        if (certificateEntity.isEmpty()) {
            throw new CertificateNotFoundException();
        }

        putCertificateHashToAudit(certificateEntity.get());

        clientEntity.getCertificates().remove(certificateEntity.get());
    }

    /**
     * Returns a single client tls certificate that has matching hash
     * @param id
     * @param certificateHash
     * @return
     * @throws ClientNotFoundException if client was not found
     */
    public Optional<Certificate> getTlsCertificate(ClientId id, String certificateHash)
            throws ClientNotFoundException {
        ClientEntity clientEntity = getLocalClientEntityOrThrowNotFound(id);
        return clientEntity.getCertificates().stream()
                .filter(certificate -> calculateCertHexHash(certificate.getData()).equalsIgnoreCase(certificateHash))
                .map(certificateEntity -> CertificateMapper.get().toTarget(certificateEntity))
                .findAny();
    }

    /**
     * Find clients in the local serverconf
     */
    List<ClientEntity> findLocalClientEntities(ClientService.SearchParameters searchParameters) {
        return searchClientEntities(searchParameters, getAllLocalClientEntities());
    }

    /**
     * Find clients in the globalconf
     */
    List<ClientEntity> findGlobalClientEntities(ClientService.SearchParameters searchParameters) {
        return searchClientEntities(searchParameters, getAllGlobalClientEntities());
    }

    List<ClientEntity> searchClientEntities(SearchParameters searchParameters, List<ClientEntity> allClients) {
        Predicate<ClientEntity> matchingSearchTerms = buildClientEntitySearchPredicate(searchParameters);
        return allClients.stream()
                .filter(matchingSearchTerms)
                .filter(c -> searchParameters.showMembers || c.getIdentifier().getSubsystemCode() != null)
                .collect(Collectors.toList());
    }

    /**
     * Find client by ClientId
     * @param clientId
     * @return
     */
    Optional<ClientEntity> findEntityByClientId(ClientId clientId) {
        List<ClientEntity> localClients = getAllLocalClientEntities();
        List<ClientEntity> globalClients = getAllGlobalClientEntities();
        List<ClientEntity> distinctClients = mergeClientEntitiesDistinctively(globalClients, localClients);
        return distinctClients.stream()
                .filter(clientType -> clientType.getIdentifier().toShortString().trim()
                        .equals(clientId.toShortString().trim()))
                .findFirst();
    }

    /**
     * Find from all clients (local or global)
     */
    public List<Client> findClients(ClientService.SearchParameters searchParameters) {
        List<ClientEntity> localClients = findLocalClientEntities(searchParameters);
        if (searchParameters.internalSearch) {
            return ClientMapper.get().toTargets(localClients);
        }

        List<ClientEntity> globalClients = findGlobalClientEntities(searchParameters);
        if (searchParameters.excludeLocal) {
            return subtractLocalFromGlobalClients(globalClients, localClients);
        }

        return ClientMapper.get().toTargets(mergeClientEntitiesDistinctively(globalClients, localClients));
    }

    /**
     * Subtract clients in a list from another list
     * @param globalClients
     * @param localClients
     * @return
     */
    private List<Client> subtractLocalFromGlobalClients(List<ClientEntity> globalClients,
                                                        List<ClientEntity> localClients) {
        List<String> localClientIds = localClients.stream().map(localClient ->
                localClient.getIdentifier().toShortString()).toList();

        return ClientMapper.get().toTargets(
                globalClients.stream()
                        .filter(globalClient -> !localClientIds.contains(globalClient.getIdentifier().toShortString()))
                        .toList()
        );
    }

    /**
     * Registers a client
     * @param clientId client to register
     * @throws GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws CannotRegisterOwnerException
     * @throws ActionNotPossibleException
     * @throws InvalidMemberClassException
     * @throws InvalidInstanceIdentifierException
     */
    public void registerClient(ClientId.Conf clientId) throws GlobalConfOutdatedException, ClientNotFoundException,
                                                              CannotRegisterOwnerException, ActionNotPossibleException,
                                                              InvalidMemberClassException, InvalidInstanceIdentifierException {

        String subsystemName = null;
        var gcVersion = globalConfProvider.getVersion();
        if (gcVersion.isPresent() && doesSupportSubsystemNames(gcVersion.getAsInt())) {
            subsystemName = subsystemNameStatus.getRename(clientId).orElse(null);
            auditDataHelper.put(MEMBER_SUBSYSTEM_NAME, subsystemName);
        }

        auditDataHelper.put(clientId);

        ClientEntity client = getLocalClientEntityOrThrowNotFound(clientId);

        String instanceIdentifier = client.getIdentifier().getXRoadInstance();
        if (!instanceIdentifier.equals(globalConfProvider.getInstanceIdentifier())) {
            throw new InvalidInstanceIdentifierException(INVALID_INSTANCE_IDENTIFIER + instanceIdentifier);
        }

        String memberClass = client.getIdentifier().getMemberClass();
        if (!isValidMemberClass(memberClass)) {
            throw new InvalidMemberClassException(INVALID_MEMBER_CLASS + memberClass);
        }

        ClientIdEntity ownerId = getCurrentSecurityServerOwnerIdEntity();
        if (ownerId.equals(client.getIdentifier())) {
            throw new CannotRegisterOwnerException();
        }
        if (!client.getClientStatus().equals(Client.STATUS_SAVED)) {
            throw new ActionNotPossibleException("Only clients with status 'saved' can be registered");
        }

        Integer requestId = managementRequestSenderService.sendClientRegisterRequest(clientId, subsystemName);
        subsystemNameStatus.submit(clientId);
        client.setClientStatus(Client.STATUS_REGINPROG);
        putClientStatusToAudit(client);
        auditDataHelper.putManagementRequestId(requestId);
    }

    private void putClientStatusToAudit(ClientEntity clientEntity) {
        auditDataHelper.putClientStatus(clientEntity != null ? clientEntity.getClientStatus() : null);
    }

    private ClientIdEntity getCurrentSecurityServerOwnerIdEntity() {
        return XRoadIdMapper.get().toEntity(currentSecurityServerId.getServerId().getOwner());
    }

    /**
     * Unregister a client
     * @param clientId client to unregister
     * @throws GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws CannotUnregisterOwnerException when trying to unregister the security server owner
     * @throws ActionNotPossibleException     when trying do unregister a client that cannot be unregistered
     */
    public void unregisterClient(ClientId.Conf clientId) throws GlobalConfOutdatedException, ClientNotFoundException,
                                                                CannotUnregisterOwnerException, ActionNotPossibleException {

        auditDataHelper.put(clientId);

        ClientEntity client = getLocalClientEntityOrThrowNotFound(clientId);
        List<String> allowedStatuses = Arrays.asList(STATUS_REGISTERED, STATUS_REGINPROG, STATUS_DISABLED);
        if (!allowedStatuses.contains(client.getClientStatus())) {
            throw new ActionNotPossibleException("cannot unregister client with status " + client.getClientStatus());
        }
        ClientId.Conf ownerId = currentSecurityServerId.getServerId().getOwner();
        if (clientId.equals(ownerId)) {
            throw new CannotUnregisterOwnerException();
        }

        Integer requestId = managementRequestSenderService.sendClientUnregisterRequest(clientId);
        putClientStatusToAudit(client);
        auditDataHelper.putManagementRequestId(requestId);
        client.setClientStatus(STATUS_DELINPROG);
    }

    /**
     * Changes Security Server owner
     * @param memberClass   member class of new owner
     * @param memberCode    member code of new owner
     * @param subsystemCode should be null because only member can be owner
     * @throws GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws MemberAlreadyOwnerException
     * @throws ActionNotPossibleException
     */
    public void changeOwner(String memberClass, String memberCode, String subsystemCode)
            throws GlobalConfOutdatedException, ClientNotFoundException, MemberAlreadyOwnerException, ActionNotPossibleException {
        if (subsystemCode != null) {
            throw new ActionNotPossibleException("Only member can be an owner");
        }
        ClientId.Conf clientId =
                ClientId.Conf.create(globalConfProvider.getInstanceIdentifier(), memberClass, memberCode);
        auditDataHelper.put(clientId);
        ClientEntity client = getLocalClientEntityOrThrowNotFound(clientId);
        putClientStatusToAudit(client);
        ClientIdEntity ownerId = getCurrentSecurityServerOwnerIdEntity();
        if (ownerId.equals(client.getIdentifier())) {
            throw new MemberAlreadyOwnerException();
        }
        if (!client.getClientStatus().equals(STATUS_REGISTERED)) {
            throw new ActionNotPossibleException("Only member with status 'registered' can become owner");
        }

        Integer requestId = managementRequestSenderService.sendOwnerChangeRequest(clientId);
        auditDataHelper.putManagementRequestId(requestId);

    }

    /**
     * Disable a client
     * @param clientId client to disable
     * @throws GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws ActionNotPossibleException  when trying to unregister a client that cannot be disabled
     */
    public void disableClient(ClientId.Conf clientId) throws GlobalConfOutdatedException, ClientNotFoundException,
                                                             CannotUnregisterOwnerException, ActionNotPossibleException {

        auditDataHelper.put(clientId);

        ClientEntity client = getLocalClientEntityOrThrowNotFound(clientId);
        if (!STATUS_REGISTERED.equals(client.getClientStatus())) {
            throw new ActionNotPossibleException("cannot disable client with status " + client.getClientStatus());
        }

        if (isManagementServiceProvider(client.getIdentifier())) {
            throw new ConflictException(FORBIDDEN_DISABLE_MANAGEMENT_SERVICE_CLIENT.build());
        }

        Integer requestId = managementRequestSenderService.sendClientDisableRequest(clientId);
        putClientStatusToAudit(client);
        auditDataHelper.putManagementRequestId(requestId);
        client.setClientStatus(STATUS_DISABLING_INPROG);
    }

    public boolean isManagementServiceProvider(ClientId subsystemId) {
        var managementRequestService = globalConfProvider.getManagementRequestService();
        return managementRequestService != null && managementRequestService.equals(subsystemId);
    }

    /**
     * Enable a client
     * @param clientId client to disable
     * @throws GlobalConfOutdatedException
     * @throws ClientNotFoundException
     * @throws ActionNotPossibleException  when trying to unregister a client that cannot be enable
     */
    public void enableClient(ClientId.Conf clientId) throws GlobalConfOutdatedException, ClientNotFoundException,
                                                            CannotUnregisterOwnerException, ActionNotPossibleException {

        auditDataHelper.put(clientId);

        ClientEntity client = getLocalClientEntityOrThrowNotFound(clientId);
        if (!STATUS_DISABLED.equals(client.getClientStatus())) {
            throw new ActionNotPossibleException("cannot enable client with status " + client.getClientStatus());
        }

        Integer requestId = managementRequestSenderService.sendClientEnableRequest(clientId);
        putClientStatusToAudit(client);
        auditDataHelper.putManagementRequestId(requestId);
        client.setClientStatus(STATUS_ENABLING_INPROG);
    }


    /**
     * Merge two client lists into one with only unique clients. The distinct clients in the latter list
     * {@code moreClients} are favoured in the case of duplicates.
     * @param clients     list of clients
     * @param moreClients list of clients (these will override the ones in {@code clients} in the case of duplicates)
     * @return
     */
    private List<ClientEntity> mergeClientEntitiesDistinctively(List<ClientEntity> clients, List<ClientEntity> moreClients) {
        Map<String, ClientEntity> uniqueClientMap = new HashMap<>();
        // add clients into the HashMap with client identifier string as the key
        clients.forEach(clientType -> uniqueClientMap.put(clientType.getIdentifier().toShortString(), clientType));
        /*
          add other clients into the HashMap with client identifier string as the key
          this conveniently overwrites all duplicate keys
         */
        moreClients.forEach(clientType -> uniqueClientMap.put(clientType.getIdentifier().toShortString(), clientType));
        return new ArrayList<>(uniqueClientMap.values());
    }

    private Predicate<ClientEntity> buildClientEntitySearchPredicate(ClientService.SearchParameters searchParameters) {
        Predicate<ClientEntity> clientTypePredicate = clientType -> true;
        if (!StringUtils.isEmpty(searchParameters.name)) {
            clientTypePredicate = clientTypePredicate
                    .and(ct -> {
                        String memberName = globalConfProvider.getMemberName(ct.getIdentifier());
                        String subsystemName = globalConfProvider.getSubsystemName(ct.getIdentifier());
                        return containsIgnoreCase(memberName, searchParameters.name)
                                || containsIgnoreCase(subsystemName, searchParameters.name);
                    });
        }
        if (!StringUtils.isEmpty(searchParameters.instance)) {
            clientTypePredicate = clientTypePredicate
                    .and(ct -> ct.getIdentifier().getXRoadInstance().equalsIgnoreCase(searchParameters.instance));
        }
        if (!StringUtils.isEmpty(searchParameters.memberClass)) {
            clientTypePredicate = clientTypePredicate
                    .and(ct -> containsIgnoreCase(ct.getIdentifier().getMemberClass(), searchParameters.memberClass));
        }
        if (!StringUtils.isEmpty(searchParameters.memberCode)) {
            clientTypePredicate = clientTypePredicate
                    .and(ct -> containsIgnoreCase(ct.getIdentifier().getMemberCode(), searchParameters.memberCode));
        }
        if (!StringUtils.isEmpty(searchParameters.subsystemCode)) {
            clientTypePredicate = clientTypePredicate
                    .and(ct -> containsIgnoreCase(ct.getIdentifier().getSubsystemCode(), searchParameters.subsystemCode));
        }
        if (searchParameters.hasValidLocalSignCert != null) {
            clientTypePredicate = clientTypePredicate
                    .and(ct -> searchParameters.hasValidLocalSignCert.equals(hasValidLocalSignCertCheck(ct)));
        }
        return clientTypePredicate;
    }

    /**
     * Check whether client has valid local sign cert
     * @param clientEntity clientEntity
     * @return boolean
     */
    private boolean hasValidLocalSignCertCheck(ClientEntity clientEntity) {
        List<CertificateInfo> signCertificateInfos = currentSecurityServerSignCertificates.getSignCertificateInfos();
        return ClientUtils.hasValidLocalSignCert(clientEntity.getIdentifier(), signCertificateInfos);
    }

    /**
     * Add a new client to this security server. Can add either a member or a subsystem.
     * Member (added client, or member associated with the client subsystem) can either
     * be one already registered to global conf, or an unregistered one. Unregistered one
     * can only be added with ignoreWarnings = true.
     * <p>
     * Client is added to this instance, it is not possible to add clients who would have
     * different instance_id from this security server's instance.
     * <p>
     * To prevent against two threads both creating "first" additional members,
     * synchronize access to this method on controller layer
     * (synchronizing this method does not help since transaction start & commit
     * are outside of this method).
     * @param memberClass      member class of added client
     * @param memberCode       member code of added client
     * @param subsystemCode    subsystem code of added client (null if adding a member)
     * @param subsystemName    subsystem name of added client (null if adding a member)
     * @param isAuthentication {@code IsAuthentication} value to set for the new client
     * @param ignoreWarnings   if warning about unregistered member should be ignored
     * @return
     * @throws ClientAlreadyExistsException           if client has already been added to security server
     * @throws AdditionalMemberAlreadyExistsException if tried to add a new member, and
     *                                                security server already has owner member + one additional member
     * @throws UnhandledWarningsException             if tried to add client associated with a member which
     *                                                does not exist in global conf yet, and ignoreWarnings was false
     * @throws InvalidMemberClassException            if client has an invalid member class, meaning that the
     *                                                member class that is not defined in this instance's configuration
     */
    public Client addLocalClient(String memberClass,
                                 String memberCode,
                                 String subsystemCode,
                                 String subsystemName,
                                 IsAuthentication isAuthentication,
                                 boolean ignoreWarnings) throws ClientAlreadyExistsException, AdditionalMemberAlreadyExistsException,
                                                                UnhandledWarningsException, InvalidMemberClassException {

        return ClientMapper.get().toTarget(
                addLocalClientEntity(memberClass, memberCode, subsystemCode, subsystemName, isAuthentication, ignoreWarnings));
    }


    public ClientEntity addLocalClientEntity(String memberClass,
                                             String memberCode,
                                             String subsystemCode,
                                             String subsystemName,
                                             IsAuthentication isAuthentication,
                                             boolean ignoreWarnings)
            throws ClientAlreadyExistsException, AdditionalMemberAlreadyExistsException,
                   UnhandledWarningsException, InvalidMemberClassException {

        if (!isValidMemberClass(memberClass)) {
            throw new InvalidMemberClassException(INVALID_MEMBER_CLASS + memberClass);
        }

        ClientId clientId = ClientId.Conf.create(globalConfProvider.getInstanceIdentifier(),
                memberClass,
                memberCode,
                subsystemCode);

        auditDataHelper.put(clientId);
        auditDataHelper.put(isAuthentication);

        ClientEntity existingLocalClient = getLocalClientEntity(clientId);
        ClientIdEntity ownerId = getCurrentSecurityServerOwnerIdEntity();
        if (existingLocalClient != null) {
            throw new ClientAlreadyExistsException("client " + clientId + " already exists");
        }
        if (clientId.getSubsystemCode() == null) {
            // adding member - check that we don't already have owner + one additional member
            List<ClientEntity> existingMembers = getAllLocalMemberEntities();
            Optional<ClientEntity> additionalMember = existingMembers.stream()
                    .filter(m -> !ownerId.equals(m.getIdentifier()))
                    .findFirst();
            if (additionalMember.isPresent()) {
                throw new AdditionalMemberAlreadyExistsException("additional member "
                        + additionalMember.get().getIdentifier() + " already exists");
            }
        }

        // check if the member associated with clientId exists in global conf
        ClientId memberId = clientId.getMemberId();
        if (globalConfProvider.getMemberName(memberId) == null) {
            // unregistered member
            if (!ignoreWarnings) {
                WarningDeviation warning = new WarningDeviation(WARNING_UNREGISTERED_MEMBER, memberId.toShortString());
                throw new UnhandledWarningsException(warning);
            }
        }

        boolean clientRegistered = globalConfService.isSecurityServerClientForThisInstance(clientId);

        ClientEntity client = addClient(clientId,
                serverConfService.getServerConfEntity(),
                isAuthentication,
                clientRegistered ? Client.STATUS_REGISTERED : Client.STATUS_SAVED);
        putClientStatusToAudit(client);

        if (clientId.isSubsystem() && StringUtils.isNotEmpty(subsystemName)) {
            subsystemNameStatus.set(clientId, globalConfProvider.getSubsystemName(clientId), subsystemName);
        }
        return client;
    }

    ClientEntity addClient(ClientId clientId, ServerConfEntity serverConfEntity, IsAuthentication isAuthentication, String status) {
        ClientEntity client = new ClientEntity();
        client.setIdentifier(identifierService.getOrPersistXroadIdEntity(XRoadIdMapper.get().toEntity(clientId)));
        client.setConf(serverConfEntity);
        client.setClientStatus(status);
        client.setIsAuthentication(isAuthentication.name());
        return clientRepository.persist(client);
    }

    /**
     * Checks that the given member class is present in the list of this instance's member classes.
     * @param memberClass
     * @return
     */
    private boolean isValidMemberClass(String memberClass) {
        Optional<String> match = globalConfService.getMemberClassesForThisInstance().stream()
                .filter(mc -> mc.equals(memberClass)).findFirst();
        return match.isPresent();
    }

    /**
     * Delete a local client.
     * @param clientId
     * @throws ActionNotPossibleException if client status did not allow delete
     * @throws CannotDeleteOwnerException if attempted to delete
     * @throws ClientNotFoundException    if local client with given id was not found
     */
    public void deleteLocalClient(ClientId clientId) throws ActionNotPossibleException,
                                                            CannotDeleteOwnerException, ClientNotFoundException {

        auditDataHelper.put(clientId);

        ClientEntity clientEntity = getLocalClientEntityOrThrowNotFound(clientId);
        // cant delete owner
        ClientIdEntity ownerId = getCurrentSecurityServerOwnerIdEntity();
        if (ownerId.equals(clientEntity.getIdentifier())) {
            throw new CannotDeleteOwnerException();
        }
        // cant delete with statuses STATUS_REGINPROG and STATUS_REGISTERED
        Set<String> allowedStatuses = Set.of(STATUS_SAVED, STATUS_DELINPROG, STATUS_GLOBALERR);
        if (!allowedStatuses.contains(clientEntity.getClientStatus())) {
            throw new ActionNotPossibleException("cannot delete client with status " + clientEntity.getClientStatus());
        }
        // we also remove local group members and access rights what is given to this client
        localGroupRepository.deleteGroupMembersByMemberId(clientEntity.getIdentifier());
        accessRightRepository.deleteBySubjectId(clientEntity.getIdentifier());
        removeLocalClient(clientEntity);
        subsystemNameStatus.clear(clientId);
    }

    private void removeLocalClient(ClientEntity clientEntity) {
        ServerConfEntity serverConfEntity = serverConfService.getServerConfEntity();
        if (!serverConfEntity.getClients().remove(clientEntity)) {
            throw new RuntimeException("client to be deleted was somehow missing from server conf");
        }
        clientRepository.remove(clientEntity);
        identifierRepository.remove(clientEntity.getIdentifier());
    }

    public void renameClient(ClientId.Conf clientId, String subsystemName)
            throws ClientNotFoundException, ActionNotPossibleException, GlobalConfOutdatedException {

        var gcVersion = globalConfProvider.getVersion();
        if (gcVersion.isEmpty() || !doesSupportSubsystemNames(gcVersion.getAsInt())) {
            throw new ActionNotPossibleException("Rename operation only supported since Global configuration version 5");
        }

        auditDataHelper.put(clientId);
        auditDataHelper.put(MEMBER_SUBSYSTEM_NAME, subsystemName);

        ClientEntity client = getLocalClientEntityOrThrowNotFound(clientId);

        if (!client.getIdentifier().isSubsystem()) {
            throw new ActionNotPossibleException("Only subsystem can be renamed.");
        }

        var currentName = globalConfProvider.getSubsystemName(client.getIdentifier());
        if (StringUtils.equals(subsystemName, currentName)) {
            throw new ConflictException("Can not change to the same name.", INVALID_CLIENT_NAME.build());
        }

        switch (client.getClientStatus()) {
            case STATUS_SAVED -> subsystemNameStatus.set(client.getIdentifier(), currentName, subsystemName);
            case STATUS_REGISTERED -> {
                try {
                    if (subsystemNameStatus.isSubmitted(client.getIdentifier())) {
                        throw new ConflictException("Subsystem rename change request already submitted.",
                                CLIENT_RENAME_ALREADY_SUBMITTED.build());
                    }

                    Integer requestId = managementRequestSenderService.sendClientRenameRequest(clientId, subsystemName);
                    subsystemNameStatus.submit(client.getIdentifier(), currentName, subsystemName);
                    auditDataHelper.putManagementRequestId(requestId);
                } catch (ManagementRequestSendingFailedException e) {
                    throw new InternalServerErrorException(e);
                }
            }
            case null, default -> throw new ActionNotPossibleException("Only clients in status save or registered can be renamed.");
        }
    }

    /**
     * Thrown when someone attempted to delete client who is this security
     * server's owner member
     */
    public static class CannotDeleteOwnerException extends ConflictException {
        public CannotDeleteOwnerException() {
            super(CANNOT_DELETE_OWNER.build());
        }
    }

    /**
     * Thrown when client that already exists in server conf was tried to add
     */
    public static class ClientAlreadyExistsException extends ConflictException {
        public ClientAlreadyExistsException(String s) {
            super(s, CLIENT_ALREADY_EXISTS.build());
        }
    }

    /**
     * Thrown when someone tries to add another member, and an additional member besides
     * the owner member already exists (there can only be owner member + one additional member)
     */
    public static class AdditionalMemberAlreadyExistsException extends ConflictException {
        public AdditionalMemberAlreadyExistsException(String s) {
            super(s, ADDITIONAL_MEMBER_ALREADY_EXISTS.build());
        }
    }

    /**
     * Thrown when trying to register the owner member
     */
    public static class CannotRegisterOwnerException extends BadRequestException {
        public CannotRegisterOwnerException() {
            super(CANNOT_REGISTER_OWNER.build());
        }
    }

    /**
     * Thrown when trying to unregister the security server owner
     */
    public static class CannotUnregisterOwnerException extends ConflictException {
        public CannotUnregisterOwnerException() {
            super(CANNOT_UNREGISTER_OWNER.build());
        }
    }

    /**
     * Thrown when trying to make the current owner the new owner
     */
    public static class MemberAlreadyOwnerException extends BadRequestException {
        public MemberAlreadyOwnerException() {
            super(CANNOT_MAKE_OWNER.build());
        }
    }

    /**
     * Thrown when client has an invalid member class, meaning that the member class that is not defined
     * in this instance's configuration
     */
    public static class InvalidMemberClassException extends BadRequestException {
        public InvalidMemberClassException(String s) {
            super(s, ErrorMessage.INVALID_MEMBER_CLASS.build());
        }
    }

    /**
     * Thrown when client's instance identifier does not match with the current instance identifier'
     */
    public static class InvalidInstanceIdentifierException extends BadRequestException {
        public InvalidInstanceIdentifierException(String s) {
            super(s, ErrorMessage.INVALID_INSTANCE_IDENTIFIER.build());
        }
    }

    @Builder
    public static class SearchParameters {
        private String name;
        private String instance;
        private String memberClass;
        private String memberCode;
        private String subsystemCode;
        /**
         * include members (without subsystemCode) in the results
         */
        private boolean showMembers;
        /**
         * search only in the local clients
         */
        private boolean internalSearch;
        /**
         * list only clients that are missing from this security server
         */
        private boolean excludeLocal;
        /**
         * true = include only clients who have local valid sign cert (registered & OCSP good) <br>
         * false = include only clients who don't have a local valid sign cert <br>
         * null = don't care whether client has a local valid sign cert <br>
         * NOTE: parameter does not have an effect on whether local or global clients are searched
         */
        private Boolean hasValidLocalSignCert;
    }
}
