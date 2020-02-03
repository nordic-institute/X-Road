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

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ClientService {

    private final ClientRepository clientRepository;
    private final GlobalConfFacade globalConfFacade;

    /**
     * ClientService constructor
     * @param clientRepository
     * @param globalConfFacade
     */
    @Autowired
    public ClientService(ClientRepository clientRepository, GlobalConfFacade globalConfFacade) {
        this.clientRepository = clientRepository;
        this.globalConfFacade = globalConfFacade;
    }

    /**
     * return all clients that exist on this security server
     * @return
     */
    public List<ClientType> getAllLocalClients() {
        return clientRepository.getAllLocalClients();
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
        List<ClientType> allClients = getAllLocalClients();
        Set<ClientId> members = new HashSet<>();
        for (ClientType client: allClients) {
            ClientId id = client.getIdentifier();
            members.add(ClientId.create(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode()));
        }
        return members;
    }


    /**
     * return all global clients as ClientTypes
     * @return
     */
    public List<ClientType> getAllGlobalClients() {
        return globalConfFacade.getMembers()
                .stream()
                .map(memberInfo -> {
                    ClientType clientType = new ClientType();
                    clientType.setIdentifier(memberInfo.getId());
                    return clientType;
                })
                .collect(Collectors.toList());
    }

    /**
     * Return one client, or null if not found.
     * This method does NOT trigger load of lazy loaded properties.
     * Use {@code getClientIsCerts}, {@code getClientLocalGroups}, and
     * {@code getClientServiceDescriptions} for that
     * @param id
     * @return the client, or null if matching client was not found
     */
    public ClientType getClient(ClientId id) {
        ClientType clientType = clientRepository.getClient(id);
        return clientType;
    }

    /**
     * Returns clientType.getIsCert() that has been fetched with Hibernate.init.
     *
     * @param id
     * @return list of CertificateTypes, or null if client does not exist
     */
    public List<CertificateType> getClientIsCerts(ClientId id) {
        ClientType clientType = getClient(id);
        if (clientType != null) {
            Hibernate.initialize(clientType.getIsCert());
            return clientType.getIsCert();
        }
        return null;
    }

    /**
     * Returns clientType.getServiceDescription() that has been fetched with Hibernate.init.
     * Also serviceDescription.services and serviceDescription.client.endpoints have been fetched.
     *
     * @param id
     * @return list of ServiceDescriptionTypes, or null if client does not exist
     */
    public List<ServiceDescriptionType> getClientServiceDescriptions(ClientId id) {
        ClientType clientType = getClient(id);
        if (clientType != null) {
            for (ServiceDescriptionType serviceDescriptionType: clientType.getServiceDescription()) {
                Hibernate.initialize(serviceDescriptionType.getService());
            }
            Hibernate.initialize(clientType.getEndpoint());
            return clientType.getServiceDescription();
        }
        return null;
    }

    /**
     * Returns clientType.getLocalGroup() that has been fetched with Hibernate.init.
     * Also localGroup.groupMembers have been fetched.
     *
     * @param id
     * @return list of LocalGroupTypes, or null if client does not exist
     */
    public List<LocalGroupType> getClientLocalGroups(ClientId id) {
        ClientType clientType = getClient(id);
        if (clientType != null) {
            for (LocalGroupType localGroupType: clientType.getLocalGroup()) {
                Hibernate.initialize(localGroupType.getGroupMember());
            }
            return clientType.getLocalGroup();
        }
        return null;
    }

    /**
     * Update connection type of an existing client
     * @param id
     * @param connectionType
     * @return
     * @throws IllegalArgumentException if connectionType was not supported value
     * @throws ClientNotFoundException if client was not found
     */
    public ClientType updateConnectionType(ClientId id, String connectionType) throws ClientNotFoundException {
        ClientType clientType = getClientType(id);
        // validate connectionType param by creating enum out of it
        IsAuthentication enumValue = IsAuthentication.valueOf(connectionType);
        clientType.setIsAuthentication(connectionType);
        clientRepository.saveOrUpdate(clientType);
        return clientType;
    }

    /**
     * Get a client, throw exception if not found
     * @throws ClientNotFoundException if not found
     */
    private ClientType getClientType(ClientId id) throws ClientNotFoundException {
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new ClientNotFoundException("client with id " + id + " not found");
        }
        return clientType;
    }

    /**
     * @param id
     * @param certBytes either PEM or DER -encoded certificate
     * @return created CertificateType with id populated
     * @throws CertificateException if certBytes was not a valid PEM or DER encoded certificate
     * @throws CertificateAlreadyExistsException if certificate already exists
     * @throws ClientNotFoundException if client was not found
     */
    public CertificateType addTlsCertificate(ClientId id, byte[] certBytes)
            throws CertificateException, CertificateAlreadyExistsException, ClientNotFoundException {
        X509Certificate x509Certificate;
        try {
            x509Certificate = CryptoUtils.readCertificate(certBytes);
        } catch (Exception e) {
            throw new CertificateException("cannot convert bytes to certificate", e);
        }
        String hash = calculateCertHexHash(x509Certificate);
        ClientType clientType = getClientType(id);
        Optional<CertificateType> duplicate = clientType.getIsCert().stream()
                .filter(cert -> hash.equalsIgnoreCase(calculateCertHexHash(cert.getData())))
                .findFirst();
        if (duplicate.isPresent()) {
            throw new CertificateAlreadyExistsException("certificate already exists");
        }

        CertificateType certificateType = new CertificateType();
        try {
            certificateType.setData(x509Certificate.getEncoded());
        } catch (CertificateEncodingException ex) {
            // client cannot do anything about this
            throw new RuntimeException(ex);
        }
        clientType.getIsCert().add(certificateType);
        clientRepository.saveOrUpdateAndFlush(clientType);
        return certificateType;
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
     * @throws ClientNotFoundException if client was not found
     * @throws CertificateNotFoundException if certificate was not found
     */
    public ClientType deleteTlsCertificate(ClientId id, String certificateHash)
            throws ClientNotFoundException, CertificateNotFoundException {
        ClientType clientType = getClientType(id);
        Optional<CertificateType> certificateType = clientType.getIsCert().stream()
                .filter(certificate -> calculateCertHexHash(certificate.getData()).equalsIgnoreCase(certificateHash))
                .findAny();
        if (!certificateType.isPresent()) {
            throw new CertificateNotFoundException();
        }

        clientType.getIsCert().remove(certificateType.get());
        clientRepository.saveOrUpdate(clientType);
        return clientType;
    }

    /**
     * Returns a single client tls certificate that has matching hash
     * @param id
     * @param certificateHash
     * @return
     * @throws ClientNotFoundException if client was not found
     */
    public Optional<CertificateType> getTlsCertificate(ClientId id, String certificateHash)
            throws ClientNotFoundException {
        ClientType clientType = getClientType(id);
        Optional<CertificateType> certificateType = clientType.getIsCert().stream()
                .filter(certificate -> calculateCertHexHash(certificate.getData()).equalsIgnoreCase(certificateHash))
                .findAny();
        return certificateType;
    }

    /**
     * Find clients in the local serverconf
     * @param name
     * @param instance
     * @param propertyClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @return ClientType list
     */
    public List<ClientType> findLocalClients(String name, String instance, String propertyClass, String memberCode,
            String subsystemCode, boolean showMembers) {
        Predicate<ClientType> matchingSearchTerms = buildClientSearchPredicate(name, instance, propertyClass,
                memberCode, subsystemCode);
        return getAllLocalClients().stream()
                .filter(matchingSearchTerms)
                .filter(ct -> showMembers || ct.getIdentifier().getSubsystemCode() != null)
                .collect(Collectors.toList());
    }

    /**
     * Find clients in the globalconf and return them as new ClientTypes
     * @param name
     * @param instance
     * @param propertyClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @return ClientType list
     */
    public List<ClientType> findGlobalClients(String name, String instance, String propertyClass, String memberCode,
            String subsystemCode, boolean showMembers) {
        Predicate<ClientType> matchingSearchTerms = buildClientSearchPredicate(name, instance, propertyClass,
                memberCode, subsystemCode);
        return getAllGlobalClients().stream()
                .filter(matchingSearchTerms)
                .filter(clientType -> showMembers || clientType.getIdentifier().getSubsystemCode() != null)
                .collect(Collectors.toList());
    }

    /**
     * Find client by ClientId
     * @param clientId
     * @return
     */
    public Optional<ClientType> findByClientId(ClientId clientId) {
        return getAllGlobalClients()
                .stream()
                .filter(clientType -> clientType.getIdentifier().toShortString().trim()
                        .equals(clientId.toShortString().trim()))
                .findFirst();
    }

    /**
     * Find from all clients (local or global)
     * @param name
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without subsystemCode) in the results
     * @param internalSearch search only in the local clients
     * @return ClientType list
     */
    public List<ClientType> findClients(String name, String instance, String memberClass, String memberCode,
            String subsystemCode, boolean showMembers, boolean internalSearch) {
        List<ClientType> clients = findLocalClients(name, instance, memberClass, memberCode, subsystemCode,
                showMembers);
        if (internalSearch) {
            return clients;
        }
        Map<String, ClientType> uniqueClientMap = new HashMap<>();
        // add global clients into the HashMap with client identifier string as the key
        findGlobalClients(name, instance, memberClass, memberCode, subsystemCode, showMembers)
                .forEach(clientType -> uniqueClientMap.put(clientType.getIdentifier().toShortString(), clientType));
        /*
          add local clients into the HashMap with client identifier string as the key
          this conveniently overwrites all duplicate keys
         */
        clients.forEach(clientType -> uniqueClientMap.put(clientType.getIdentifier().toShortString(), clientType));
        return new ArrayList<>(uniqueClientMap.values());
    }

    private Predicate<ClientType> buildClientSearchPredicate(String name, String instance,
            String memberClass, String memberCode, String subsystemCode) {
        Predicate<ClientType> clientTypePredicate = clientType -> true;
        if (!StringUtils.isEmpty(name)) {
            clientTypePredicate = clientTypePredicate.and(ct -> {
                String memberName = globalConfFacade.getMemberName(ct.getIdentifier());
                return memberName != null && memberName.toLowerCase().contains(name.toLowerCase());
            });
        }
        if (!StringUtils.isEmpty(instance)) {
            clientTypePredicate = clientTypePredicate.and(ct -> ct.getIdentifier().getXRoadInstance().toLowerCase()
                    .contains(instance.toLowerCase()));
        }
        if (!StringUtils.isEmpty(memberClass)) {
            clientTypePredicate = clientTypePredicate.and(ct -> ct.getIdentifier().getMemberClass().toLowerCase()
                    .contains(memberClass.toLowerCase()));
        }
        if (!StringUtils.isEmpty(memberCode)) {
            clientTypePredicate = clientTypePredicate.and(ct -> ct.getIdentifier().getMemberCode().toLowerCase()
                    .contains(memberCode.toLowerCase()));
        }
        if (!StringUtils.isEmpty(subsystemCode)) {
            clientTypePredicate = clientTypePredicate.and(ct -> ct.getIdentifier().getSubsystemCode() != null
                    && ct.getIdentifier().getSubsystemCode().toLowerCase().contains(subsystemCode.toLowerCase()));
        }
        return clientTypePredicate;
    }
}
