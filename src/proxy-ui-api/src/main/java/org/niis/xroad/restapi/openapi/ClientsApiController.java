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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.restapi.converter.CertificateConverter;
import org.niis.xroad.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.converter.ConnectionTypeMapping;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ErrorCode;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.service.ClientService;
import org.niis.xroad.restapi.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

/**
 * clients api
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class ClientsApiController implements ClientsApi {

    private final NativeWebRequest request;

    @Autowired
    private ClientService clientService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ClientConverter clientConverter;

    @Autowired
    private CertificateConverter certificateConverter;

    @Autowired
    private CertificateDetailsConverter certificateDetailsConverter;

    @Autowired
    public ClientsApiController(NativeWebRequest request) {
        this.request = request;
    }

    /**
     * Example exception
     */
    @ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such Thing there")
    public static class RestNotFoundException extends RuntimeException {
        public RestNotFoundException(String s) {
            super(s);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<List<Client>> getClients(String name, String instance, String propertyClass, String code,
                                                   String subsystem, Boolean showMembers, Boolean internalSearch) {
        // no filtering / search yet, returns all
        List<ClientType> clientTypes = clientService.getAllClients();
        List<Client> clients = new ArrayList<>();
        for (ClientType clientType : clientTypes) {
            clients.add(clientConverter.convert(clientType));
        }
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    /**
     * No argument version to return all clients
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<List<Client>> getClients() {
        final String ignoredSearchParam = null;
        return getClients(ignoredSearchParam, ignoredSearchParam, ignoredSearchParam, ignoredSearchParam,
                ignoredSearchParam, null, null);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<Client> getClient(String id) {
        ClientType clientType = getClientType(id);
        Client client = clientConverter.convert(clientType);
        return new ResponseEntity<>(client, HttpStatus.OK);
    }

    /**
     * Read one client from DB, throw NotFoundException or
     * BadRequestException is needed
     */
    private ClientType getClientType(String encodedId) {
        ClientId clientId = clientConverter.convertId(encodedId);
        ClientType clientType = clientService.getClient(clientId);
        if (clientType == null) {
            throw new NotFoundException("client with id " + encodedId + " not found");
        }
        return clientType;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<List<CertificateDetails>> getClientCertificates(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        try {
            List<CertificateDetails> certificates = tokenService.getAllTokens(clientType)
                    .stream()
                    .map(certificateDetailsConverter::convert)
                    .collect(toList());
            return new ResponseEntity<>(certificates, HttpStatus.OK);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Update a client's connection type
     * @param encodedId
     * @param connectiontype
     * @return
     */
    @PreAuthorize("hasAuthority('EDIT_CLIENT_INTERNAL_CONNECTION_TYPE')")
    @Override
    public ResponseEntity<Client> updateClient(String encodedId, ConnectionType connectiontype) {
        ClientId clientId = clientConverter.convertId(encodedId);
        String connectionTypeString = ConnectionTypeMapping.map(connectiontype).get();
        ClientType changed = clientService.updateConnectionType(clientId, connectionTypeString);
        Client result = clientConverter.convert(changed);
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    public static final String INVALID_UPLOAD_ERROR_CODE = "invalid_upload";
    public static final String INVALID_CERT_ERROR_CODE = "invalid_cert";

    @Override
    @PreAuthorize("hasAuthority('ADD_CLIENT_INTERNAL_CERT')")
    public ResponseEntity<Void> addClientTlsCertificate(String encodedId,
                                                        Resource body) {
        byte[] certificateBytes;
        try {
            certificateBytes = IOUtils.toByteArray(body.getInputStream());
        } catch (IOException ex) {
            throw new BadRequestException("cannot read certificate data", ex, ErrorCode.of(INVALID_UPLOAD_ERROR_CODE));
        }
        ClientId clientId = clientConverter.convertId(encodedId);
        try {
            clientService.addTlsCertificate(clientId, certificateBytes);
        } catch (CertificateException c) {
            throw new BadRequestException(c, ErrorCode.of(INVALID_CERT_ERROR_CODE));
        }

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_CLIENT_INTERNAL_CERT')")
    public ResponseEntity<Void> deleteClientTlsCertificate(String encodedId, String hash) {
        ClientId clientId = clientConverter.convertId(encodedId);
        clientService.deleteTlsCertificate(clientId, hash);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERT_DETAILS')")
    public ResponseEntity<CertificateDetails> getClientTlsCertificate(String encodedId, String certHash) {
        ClientId clientId = clientConverter.convertId(encodedId);
        Optional<CertificateType> certificateType = clientService.getTlsCertificate(clientId, certHash);
        if (!certificateType.isPresent()) {
            throw new NotFoundException("certificate with hash " + certHash
                    + ", client id " + encodedId + " not found");
        }
        return new ResponseEntity<>(certificateDetailsConverter.convert(certificateType.get()), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_INTERNAL_CERTS')")
    public ResponseEntity<List<CertificateDetails>> getClientTlsCertificates(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        List<CertificateDetails> certificates = clientType.getIsCert()
                .stream()
                .map(certificateDetailsConverter::convert)
                .collect(toList());
        return new ResponseEntity<>(certificates, HttpStatus.OK);
    }

    @Override
    public Optional<NativeWebRequest> getRequest() {
        return Optional.ofNullable(request);
    }

}
