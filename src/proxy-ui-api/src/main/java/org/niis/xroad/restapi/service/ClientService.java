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
import org.springframework.util.CollectionUtils;
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
     * return all clients
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public List<ClientType> getAllClients() {
        return clientRepository.getAllClients();
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
     * @param code
     * @param subsystem
     * @param showMembers
     * @return ClientType list
     */
    public List<ClientType> findLocalClients(String name, String instance, String propertyClass, String code,
            String subsystem, boolean showMembers) {
        List<Predicate<ClientType>> searchPredicates = buildLocalClientSearchPredicates(name, instance, propertyClass,
                code, subsystem, showMembers);
        List<ClientType> clientList = getAllClients().stream()
                .filter(searchPredicates.stream().reduce(p -> true, Predicate::and))
                .filter(ct -> showMembers || ct.getIdentifier().getSubsystemCode() != null)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(clientList)) {
            throw new NotFoundException(("no members found with the given search terms"));
        }
        return clientList;
    }

    /**
     * Find clients in the globalconf
     * @param name
     * @param instance
     * @param propertyClass
     * @param code
     * @param subsystem
     * @param showMembers
     * @return ClientId list
     */
    public List<ClientId> findGlobalClients(String name, String instance, String propertyClass, String code,
            String subsystem, boolean showMembers) {
        List<Predicate<ClientId>> searchPredicates = buildGlobalClientSearchPredicates(name, instance, propertyClass,
                code, subsystem, showMembers);
        List<ClientId> globalMembers = globalConfWrapper.getGlobalMembers().stream()
                .map(MemberInfo::getId)
                .filter(searchPredicates.stream().reduce(p -> true, Predicate::and))
                .filter(id -> showMembers || id.getSubsystemCode() != null)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(globalMembers)) {
            throw new NotFoundException(("no members found with the given search terms"));
        }
        return globalMembers;
    }

    private List<Predicate<ClientType>> buildLocalClientSearchPredicates(String name, String instance,
            String propertyClass, String code, String subsystem, boolean showMembers) {
        List<Predicate<ClientType>> searchPredicates = new ArrayList<>();

        if (!StringUtils.isEmpty(name)) {
            searchPredicates.add(ct -> globalConfWrapper.getMemberName(ct.getIdentifier()).equals(name));
        }
        if (!StringUtils.isEmpty(instance)) {
            searchPredicates.add(ct -> ct.getIdentifier().getXRoadInstance().equals(instance));
        }
        if (!StringUtils.isEmpty(propertyClass)) {
            searchPredicates.add(ct -> ct.getIdentifier().getMemberClass().equals(propertyClass));
        }
        if (!StringUtils.isEmpty(code)) {
            searchPredicates.add(ct -> ct.getIdentifier().getMemberCode().equals(code));
        }
        if (!StringUtils.isEmpty(subsystem)) {
            searchPredicates.add(ct -> {
                if (showMembers) {
                    return ct.getIdentifier().getSubsystemCode() == null
                            || ct.getIdentifier().getSubsystemCode().equals(subsystem);
                } else {
                    return ct.getIdentifier().getSubsystemCode() != null
                            && ct.getIdentifier().getSubsystemCode().equals(subsystem);
                }
            });
        }

        return searchPredicates;
    }

    private List<Predicate<ClientId>> buildGlobalClientSearchPredicates(String name, String instance,
            String propertyClass, String code, String subsystem, boolean showMembers) {
        List<Predicate<ClientId>> searchPredicates = new ArrayList<>();

        if (!StringUtils.isEmpty(name)) {
            searchPredicates.add(id -> globalConfWrapper.getMemberName(id).equals(name));
        }
        if (!StringUtils.isEmpty(instance)) {
            searchPredicates.add(id -> id.getXRoadInstance().equals(instance));
        }
        if (!StringUtils.isEmpty(propertyClass)) {
            searchPredicates.add(id -> id.getMemberClass().equals(propertyClass));
        }
        if (!StringUtils.isEmpty(code)) {
            searchPredicates.add(id -> id.getMemberCode().equals(code));
        }
        if (!StringUtils.isEmpty(subsystem)) {
            searchPredicates.add(id -> {
                if (showMembers) {
                    return id.getSubsystemCode() == null
                            || id.getSubsystemCode().equals(subsystem);
                } else {
                    return id.getSubsystemCode() != null
                            && id.getSubsystemCode().equals(subsystem);
                }
            });
        }

        return searchPredicates;
    }
}
