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

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.GlobalConfWrapper;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.ErrorCode;
import org.niis.xroad.restapi.exceptions.NotFoundException;
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
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ClientService {

    public static final String CLIENT_NOT_FOUND_ERROR_CODE = "client_not_found";
    public static final String CERTIFICATE_NOT_FOUND_ERROR_CODE = "certificate_not_found";

    private final ClientRepository clientRepository;
    private final GlobalConfWrapper globalConfWrapper;

    /**
     * ClientService constructor
     * @param clientRepository
     * @param globalConfWrapper
     */
    @Autowired
    public ClientService(ClientRepository clientRepository, GlobalConfWrapper globalConfWrapper) {
        this.clientRepository = clientRepository;
        this.globalConfWrapper = globalConfWrapper;
    }

    /**
     * return all clients that are registered on the instance
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<ClientType> getAllLocalClients() {
        return clientRepository.getAllLocalClients();
    }

    /**
     * return all global clients
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<MemberInfo> getAllGlobalClients() {
        return globalConfWrapper.getGlobalMembers();
    }

    /**
     * return one client
     * @param id
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ClientType getClient(ClientId id) {
        return clientRepository.getClient(id);
    }

    /**
     * Update connection type of an existing client
     * @param id
     * @param connectionType
     * @return
     * @throws org.niis.xroad.restapi.exceptions.NotFoundException if
     *                                                             client was not found
     * @throws IllegalArgumentException                            if connectionType was not supported value
     */
    @PreAuthorize("hasAuthority('EDIT_CLIENT_INTERNAL_CONNECTION_TYPE')")
    public ClientType updateConnectionType(ClientId id, String connectionType) {
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new NotFoundException(("client with id " + id + " not found"));
        }
        // validate connectionType param by creating enum out of it
        IsAuthentication enumValue = IsAuthentication.valueOf(connectionType);
        clientType.setIsAuthentication(connectionType);
        clientRepository.saveOrUpdate(clientType);
        return clientType;
    }

    /**
     * @param id
     * @param certBytes either PEM or DER -encoded certificate
     * @return
     * @throws CertificateException if certBytes was not a valid PEM or DER encoded certificate
     * @throws ConflictException    if the certificate already exists
     */
    @PreAuthorize("hasAuthority('ADD_CLIENT_INTERNAL_CERT')")
    public ClientType addTlsCertificate(ClientId id, byte[] certBytes) throws CertificateException {
        X509Certificate x509Certificate;
        try {
            x509Certificate = CryptoUtils.readCertificate(certBytes);
        } catch (Exception e) {
            throw new CertificateException("cannot convert bytes to certificate", e);
        }
        String hash = calculateCertHexHash(x509Certificate);
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new NotFoundException(("client with id " + id + " not found"));
        }
        clientType.getIsCert().stream()
                .filter(cert -> hash.equalsIgnoreCase(calculateCertHexHash(cert.getData())))
                .findAny()
                .ifPresent(a -> {
                    throw new ConflictException("certificate already exists");
                });
        CertificateType certificateType = new CertificateType();
        try {
            certificateType.setData(x509Certificate.getEncoded());
        } catch (CertificateEncodingException ex) {
            throw new RuntimeException(ex);
        }
        clientType.getIsCert().add(certificateType);
        clientRepository.saveOrUpdate(clientType);
        return clientType;
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
     * @throws NotFoundException if client of certificate was not found
     */
    @PreAuthorize("hasAuthority('DELETE_CLIENT_INTERNAL_CERT')")
    public ClientType deleteTlsCertificate(ClientId id, String certificateHash) {
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new NotFoundException(("client with id " + id + " not found"),
                    ErrorCode.of(CLIENT_NOT_FOUND_ERROR_CODE));
        }
        CertificateType certificateType = clientType.getIsCert().stream()
                .filter(certificate -> calculateCertHexHash(certificate.getData()).equalsIgnoreCase(certificateHash))
                .findAny()
                .orElseThrow(() ->
                        new NotFoundException("certificate with hash " + certificateHash + " not found",
                                ErrorCode.of(CERTIFICATE_NOT_FOUND_ERROR_CODE)));
        clientType.getIsCert().remove(certificateType);
        clientRepository.saveOrUpdate(clientType);
        return clientType;
    }

    /**
     * Returns a single client tls certificate that has matching hash
     * @param id
     * @param certificateHash
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERT_DETAILS')")
    public Optional<CertificateType> getTlsCertificate(ClientId id, String certificateHash) {
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new NotFoundException(("client with id " + id + " not found"));
        }
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
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<ClientType> findLocalClients(String name, String instance, String propertyClass, String memberCode,
            String subsystemCode, boolean showMembers) {
        List<Predicate<ClientType>> searchPredicates = buildLocalClientSearchPredicates(name, instance, propertyClass,
                memberCode, subsystemCode);
        return getAllLocalClients().stream()
                .filter(searchPredicates.stream().reduce(p -> true, Predicate::and))
                .filter(ct -> showMembers || ct.getIdentifier().getSubsystemCode() != null)
                .collect(Collectors.toList());
    }

    /**
     * Find clients in the globalconf. Will return MemberInfos because ClientTypes do not exist in globalconf
     * @param name
     * @param instance
     * @param propertyClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @return ClientId list
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<MemberInfo> findGlobalClients(String name, String instance, String propertyClass, String memberCode,
            String subsystemCode, boolean showMembers) {
        List<Predicate<MemberInfo>> searchPredicates = buildGlobalClientSearchPredicates(name, instance, propertyClass,
                memberCode, subsystemCode);
        return getAllGlobalClients().stream()
                .filter(searchPredicates.stream().reduce(p -> true, Predicate::and))
                .filter(memberInfo -> showMembers || memberInfo.getId().getSubsystemCode() != null)
                .collect(Collectors.toList());
    }

    /**
     * Find from all clients (local and global)
     * @param name
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @param internalSearch search only in the local clients
     * @return MemberInfo list
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<MemberInfo> findFromAllClients(String name, String instance, String memberClass, String memberCode,
            String subsystemCode, boolean showMembers, boolean internalSearch) {
        List<MemberInfo> clients = findLocalClients(name, instance, memberClass, memberCode, subsystemCode,
                showMembers)
                .stream()
                .map(clientType -> new MemberInfo(clientType.getIdentifier(),
                        globalConfWrapper.getMemberName(clientType.getIdentifier())))
                .collect(Collectors.toList());
        if (internalSearch) {
            return clients;
        }
        // find global clients and remove duplicates
        List<MemberInfo> globalClients = findGlobalClients(name, instance, memberClass, memberCode, subsystemCode,
                showMembers)
                .stream()
                .filter(globalClient -> clients.stream()
                        .noneMatch(localClient -> localClient.getId().toShortString()
                                .equals(globalClient.getId().toShortString())))
                .collect(Collectors.toList());
        clients.addAll(globalClients);
        return clients;
    }

    private List<Predicate<ClientType>> buildLocalClientSearchPredicates(String name, String instance,
            String memberClass, String memberCode, String subsystemCode) {
        List<Predicate<ClientType>> searchPredicates = new ArrayList<>();
        if (!StringUtils.isEmpty(name)) {
            searchPredicates.add(ct -> {
                String memberName = globalConfWrapper.getMemberName(ct.getIdentifier());
                return memberName != null && memberName.equalsIgnoreCase(name);
            });
        }
        if (!StringUtils.isEmpty(instance)) {
            searchPredicates.add(ct -> ct.getIdentifier().getXRoadInstance().equalsIgnoreCase(instance));
        }
        if (!StringUtils.isEmpty(memberClass)) {
            searchPredicates.add(ct -> ct.getIdentifier().getMemberClass().equalsIgnoreCase(memberClass));
        }
        if (!StringUtils.isEmpty(memberCode)) {
            searchPredicates.add(ct -> ct.getIdentifier().getMemberCode().equalsIgnoreCase(memberCode));
        }
        if (!StringUtils.isEmpty(subsystemCode)) {
            searchPredicates.add(ct -> ct.getIdentifier().getSubsystemCode() != null
                    && ct.getIdentifier().getSubsystemCode().equalsIgnoreCase(subsystemCode));
        }
        return searchPredicates;
    }

    private List<Predicate<MemberInfo>> buildGlobalClientSearchPredicates(String name, String instance,
            String memberClass, String memberCode, String subsystemCode) {
        List<Predicate<MemberInfo>> searchPredicates = new ArrayList<>();
        if (!StringUtils.isEmpty(name)) {
            searchPredicates.add(memberInfo -> memberInfo.getName() != null
                    && memberInfo.getName().equalsIgnoreCase(name));
        }
        if (!StringUtils.isEmpty(instance)) {
            searchPredicates.add(memberInfo -> memberInfo.getId().getXRoadInstance().equalsIgnoreCase(instance));
        }
        if (!StringUtils.isEmpty(memberClass)) {
            searchPredicates.add(memberInfo -> memberInfo.getId().getMemberClass().equalsIgnoreCase(memberClass));
        }
        if (!StringUtils.isEmpty(memberCode)) {
            searchPredicates.add(memberInfo -> memberInfo.getId().getMemberCode().equalsIgnoreCase(memberCode));
        }
        if (!StringUtils.isEmpty(subsystemCode)) {
            searchPredicates.add(memberInfo -> memberInfo.getId().getSubsystemCode() != null
                    && memberInfo.getId().getSubsystemCode().equalsIgnoreCase(subsystemCode));
        }
        return searchPredicates;
    }
}
