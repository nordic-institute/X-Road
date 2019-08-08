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
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.niis.xroad.restapi.converter.CertificateDetailsConverter;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.converter.ConnectionTypeMapping;
import org.niis.xroad.restapi.converter.GroupConverter;
import org.niis.xroad.restapi.converter.ServiceDescriptionConverter;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.Error;
import org.niis.xroad.restapi.exceptions.InvalidParametersException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.openapi.model.ConnectionTypeWrapper;
import org.niis.xroad.restapi.openapi.model.Group;
import org.niis.xroad.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionAdd;
import org.niis.xroad.restapi.openapi.model.ServiceType;
import org.niis.xroad.restapi.service.ClientService;
import org.niis.xroad.restapi.service.GroupService;
import org.niis.xroad.restapi.service.ServiceDescriptionService;
import org.niis.xroad.restapi.service.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.NativeWebRequest;

import java.io.IOException;
import java.security.cert.CertificateException;
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

    private final ClientConverter clientConverter;
    private final ClientService clientService;
    private final GroupConverter groupConverter;
    private final GroupService groupsService;
    private final NativeWebRequest request;
    private final TokenService tokenService;
    private final CertificateDetailsConverter certificateDetailsConverter;
    private final ServiceDescriptionConverter serviceDescriptionConverter;
    private final ServiceDescriptionService serviceDescriptionService;

    /**
     * ClientsApiController constructor
     * @param request
     * @param clientService
     * @param tokenService
     * @param clientConverter
     * @param groupConverter
     * @param groupsService
     * @param serviceDescriptionConverter
     * @param serviceDescriptionService
     */

    @Autowired
    public ClientsApiController(NativeWebRequest request, ClientService clientService, TokenService tokenService,
            ClientConverter clientConverter, GroupConverter groupConverter, GroupService groupsService,
            CertificateDetailsConverter certificateDetailsConverter,
            ServiceDescriptionConverter serviceDescriptionConverter,
            ServiceDescriptionService serviceDescriptionService) {
        this.request = request;
        this.clientService = clientService;
        this.tokenService = tokenService;
        this.clientConverter = clientConverter;
        this.groupConverter = groupConverter;
        this.groupsService = groupsService;
        this.certificateDetailsConverter = certificateDetailsConverter;
        this.serviceDescriptionConverter = serviceDescriptionConverter;
        this.serviceDescriptionService = serviceDescriptionService;
    }

    /**
     * Finds clients matching search terms
     * @param name
     * @param instance
     * @param memberClass
     * @param memberCode
     * @param subsystemCode
     * @param showMembers include members (without susbsystemCode) in the results
     * @param internalSearch search only in the local clients
     * @return
     */
    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENTS')")
    public ResponseEntity<List<Client>> getClients(String name, String instance, String memberClass,
            String memberCode, String subsystemCode, Boolean showMembers, Boolean internalSearch) {
        boolean unboxedShowMembers = Boolean.TRUE.equals(showMembers);
        boolean unboxedInternalSearch = Boolean.TRUE.equals(internalSearch);
        List<Client> clients = clientConverter.convertMemberInfosToClients(clientService.findClients(name,
                instance, memberClass, memberCode, subsystemCode, unboxedShowMembers, unboxedInternalSearch));
        return new ResponseEntity<>(clients, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_DETAILS')")
    public ResponseEntity<Client> getClient(String id) {
        ClientType clientType = getClientType(id);
        Client client = clientConverter.convert(clientType);
        return new ResponseEntity<>(client, HttpStatus.OK);
    }

    /**
     * Read one client from DB
     * @param encodedId id that is encoded with the <INSTANCE>:<MEMBER_CLASS>:....
     * encoding
     * @return
     * @throws NotFoundException   if client does not exist
     * @throws BadRequestException if encodedId was not proper encoded client ID
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
     * @param connectionTypeWrapper wrapper object containing the connection type to set
     * @return
     */
    @PreAuthorize("hasAuthority('EDIT_CLIENT_INTERNAL_CONNECTION_TYPE')")
    @Override
    public ResponseEntity<Client> updateClient(String encodedId, ConnectionTypeWrapper connectionTypeWrapper) {
        if (connectionTypeWrapper == null || connectionTypeWrapper.getConnectionType() == null) {
            throw new InvalidParametersException();
        }
        ConnectionType connectionType = connectionTypeWrapper.getConnectionType();
        ClientId clientId = clientConverter.convertId(encodedId);
        String connectionTypeString = ConnectionTypeMapping.map(connectionType).get();
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
            throw new BadRequestException("cannot read certificate data", ex, new Error(INVALID_UPLOAD_ERROR_CODE));
        }
        ClientId clientId = clientConverter.convertId(encodedId);
        try {
            clientService.addTlsCertificate(clientId, certificateBytes);
        } catch (CertificateException c) {
            throw new BadRequestException(c, new Error(INVALID_CERT_ERROR_CODE));
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

    @Override
    @PreAuthorize("hasAuthority('ADD_LOCAL_GROUP')")
    public ResponseEntity<Void> addClientGroup(String id, Group group) {
        ClientType clientType = getClientType(id);
        groupsService.addLocalGroup(clientType.getIdentifier(), groupConverter.convert(group));
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_LOCAL_GROUPS')")
    public ResponseEntity<List<Group>> getClientGroups(String id) {
        ClientType clientType = getClientType(id);
        List<LocalGroupType> localGroupTypes = clientType.getLocalGroup();
        return new ResponseEntity<>(groupConverter.convert(localGroupTypes), HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ResponseEntity<List<ServiceDescription>> getClientServiceDescriptions(String encodedId) {
        ClientType clientType = getClientType(encodedId);
        List<ServiceDescription> serviceDescriptions = serviceDescriptionConverter.convert(
                clientType.getServiceDescription());
        return new ResponseEntity<>(serviceDescriptions, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('ADD_WSDL')")
    public ResponseEntity<Void> addClientServiceDescription(String id,
            ServiceDescriptionAdd serviceDescription) {
        if (serviceDescription.getType() == ServiceType.WSDL) {
            serviceDescriptionService.addWsdlServiceDescription(clientConverter.convertId(id),
                    serviceDescription.getUrl(), serviceDescription.getIgnoreWarnings());
        } else if (serviceDescription.getType() == ServiceType.REST) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
