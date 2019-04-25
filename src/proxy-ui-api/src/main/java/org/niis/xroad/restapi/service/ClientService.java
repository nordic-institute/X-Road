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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

/**
 * client service
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ClientService {

    @Autowired
    private ClientRepository clientRepository;

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
     * client was not found
     * @throws IllegalArgumentException if connectionType was not supported value
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
     * TO DO: check old error codes, do we provide enough information to show those?
     * TO DO: must check that the old code still works after hbm.xml change
     * @param id
     * @param certBytes either PEM or DER -encoded certificate
     * @return
     * @throws CertificateException if certBytes was not a valid PEM or DER encoded certificate
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
                .filter(cert -> hash.equals(calculateCertHexHash(cert.getData())))
                .findAny()
                .ifPresent(a -> {
                    throw new ConflictException("clients.cert_exists"); });

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
     * TO DO: error code distinguishing between the two
     */
    @PreAuthorize("hasAuthority('DELETE_CLIENT_INTERNAL_CERT')")
    public ClientType deleteTlsCertificate(ClientId id, String certificateHash) {
        ClientType clientType = clientRepository.getClient(id);
        if (clientType == null) {
            throw new NotFoundException(("client with id " + id + " not found"));
        }
        CertificateType certificateType = clientType.getIsCert().stream()
                .filter(certificate -> calculateCertHexHash(certificate.getData()).equalsIgnoreCase(certificateHash))
                .findAny()
                .orElseThrow(() -> new NotFoundException("certificate with hash " + certificateHash + " not found"));
        clientType.getIsCert().remove(certificateType);
        clientRepository.saveOrUpdate(clientType);
        return clientType;
    }

    /**
     * TO DO: permissions, refactor
     */
    @PreAuthorize("permitAll")
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
}
