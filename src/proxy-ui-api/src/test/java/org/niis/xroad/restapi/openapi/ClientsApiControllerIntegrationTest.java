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

import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ClientStatus;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.openapi.model.ConnectionTypeWrapper;
import org.niis.xroad.restapi.openapi.model.LocalGroup;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionAdd;
import org.niis.xroad.restapi.openapi.model.ServiceType;
import org.niis.xroad.restapi.openapi.model.Subject;
import org.niis.xroad.restapi.openapi.model.SubjectType;
import org.niis.xroad.restapi.repository.TokenRepository;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.niis.xroad.restapi.util.TestUtils;
import org.niis.xroad.restapi.wsdl.WsdlValidator;
import org.niis.xroad.restapi.wsdl.WsdlValidatorTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.ERROR_INVALID_WSDL;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.ERROR_SERVICE_EXISTS;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.ERROR_WARNINGS_DETECTED;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.ERROR_WSDL_EXISTS;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.WARNING_WSDL_VALIDATION_WARNINGS;
import static org.niis.xroad.restapi.util.CertificateTestUtils.getResource;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithMetadata;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithoutMetadata;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertWarning;
import static org.niis.xroad.restapi.util.TestUtils.assertLocationHeader;

/**
 * Test ClientsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ClientsApiControllerIntegrationTest {
    public static final String CLIENT_ID_SS1 = "FI:GOV:M1:SS1";
    public static final String CLIENT_ID_SS2 = "FI:GOV:M1:SS2";
    public static final String CLIENT_ID_SS4 = "FI:GOV:M1:SS4";
    public static final String NEW_GROUPCODE = "groupx";
    public static final String GROUP_DESC = "GROUP_DESC";
    public static final String NAME_APPENDIX = "-name";
    private static final String INSTANCE_FI = "FI";
    private static final String INSTANCE_EE = "EE";
    private static final String MEMBER_CLASS_GOV = "GOV";
    private static final String MEMBER_CLASS_PRO = "PRO";
    private static final String MEMBER_CODE_M1 = "M1";
    private static final String MEMBER_CODE_M2 = "M2";
    private static final String SUBSYSTEM1 = "SS1";
    private static final String SUBSYSTEM2 = "SS2";
    private static final String SUBSYSTEM3 = "SS3";
    private static final String GLOBAL_GROUP = "global-group";
    private List<GlobalGroupInfo> globalGroupInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getGlobalGroupInfo(INSTANCE_FI, "global-group"),
            TestUtils.getGlobalGroupInfo(INSTANCE_FI, "global-group-1"),
            TestUtils.getGlobalGroupInfo(INSTANCE_EE, "global-group-2")));

    @MockBean
    private GlobalConfService globalConfService;

    @MockBean
    private TokenRepository tokenRepository;

    @SpyBean
    // partial mocking, just override getValidatorCommand()
    private WsdlValidator wsdlValidator;

    @Before
    public void setup() throws Exception {
        when(globalConfService.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? identifier.getSubsystemCode() + NAME_APPENDIX
                    : "test-member" + NAME_APPENDIX;
        });
        when(globalConfService.getGlobalMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1),
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM2),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M2, SUBSYSTEM3),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M1, SUBSYSTEM1),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M2, null))
        ));
        List<TokenInfo> mockTokens = createMockTokenInfos(null);
        when(tokenRepository.getTokens()).thenReturn(mockTokens);
        when(wsdlValidator.getWsdlValidatorCommand()).thenReturn("src/test/resources/validator/mock-wsdlvalidator.sh");
        when(globalConfService.getGlobalGroups(any())).thenReturn(globalGroupInfos);
    }

    @Autowired
    private ClientsApiController clientsApiController;

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getAllClients() {
        ResponseEntity<List<Client>> response =
                clientsApiController.findClients(null, null, null, null, null, true, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(7, response.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getAllLocalClients() {
        ResponseEntity<List<Client>> response = clientsApiController.findClients(null, null, null, null, null, true,
                true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        Client client = response.getBody().get(0);
        assertEquals("test-member-name", client.getMemberName());
        assertEquals("M1", client.getMemberCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENT_DETAILS")
    public void getClient() {
        ResponseEntity<Client> response =
                clientsApiController.getClient("FI:GOV:M1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Client client = response.getBody();
        assertEquals(ConnectionType.HTTP, client.getConnectionType());
        assertEquals(ClientStatus.REGISTERED, client.getStatus());
        assertEquals("test-member-name", client.getMemberName());
        assertEquals("GOV", client.getMemberClass());
        assertEquals("M1", client.getMemberCode());
        assertEquals("FI:GOV:M1", client.getId());
        assertNull(client.getSubsystemCode());
        response = clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        client = response.getBody();
        assertEquals(ConnectionType.HTTPS_NO_AUTH, client.getConnectionType());
        assertEquals(ClientStatus.REGISTERED, client.getStatus());
        assertEquals("SS1-name", client.getMemberName());
        assertEquals("GOV", client.getMemberClass());
        assertEquals("M1", client.getMemberCode());
        assertEquals("FI:GOV:M1:SS1", client.getId());
        assertEquals("SS1", client.getSubsystemCode());
        try {
            clientsApiController.getClient("FI:GOV:M1:SS3");
            fail("should throw NotFoundException to 404");
        } catch (NotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "EDIT_CLIENT_INTERNAL_CONNECTION_TYPE",
            "VIEW_CLIENT_DETAILS" })
    public void updateClient() throws Exception {
        ResponseEntity<Client> response =
                clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(ConnectionType.HTTPS_NO_AUTH, response.getBody().getConnectionType());
        ConnectionTypeWrapper http = new ConnectionTypeWrapper();
        http.setConnectionType(ConnectionType.HTTP);
        response = clientsApiController.updateClient("FI:GOV:M1:SS1", http);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ConnectionType.HTTP, response.getBody().getConnectionType());
        response = clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(ConnectionType.HTTP, response.getBody().getConnectionType());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENT_DETAILS")
    public void getClientCertificates() throws Exception {
        ResponseEntity<List<CertificateDetails>> certificates =
                clientsApiController.getClientCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(0, certificates.getBody().size());
        CertificateInfo mockCertificate = new CertificateInfo(
                ClientId.create("FI", "GOV", "M1"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "id", CertificateTestUtils.getMockCertificateBytes(), null);
        when(tokenRepository.getTokens()).thenReturn(createMockTokenInfos(mockCertificate));
        certificates = clientsApiController.getClientCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(1, certificates.getBody().size());
        CertificateDetails onlyCertificate = certificates.getBody().get(0);
        assertEquals("N/A", onlyCertificate.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"), onlyCertificate.getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"), onlyCertificate.getNotAfter());
        assertEquals("1", onlyCertificate.getSerial());
        assertEquals(new Integer(3), onlyCertificate.getVersion());
        assertEquals("SHA512withRSA", onlyCertificate.getSignatureAlgorithm());
        assertEquals("RSA", onlyCertificate.getPublicKeyAlgorithm());
        assertEquals("A2293825AA82A5429EC32803847E2152A303969C", onlyCertificate.getHash());
        assertTrue(onlyCertificate.getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(onlyCertificate.getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(new Integer(65537), onlyCertificate.getRsaPublicKeyExponent());
        assertEquals(new ArrayList<>(Arrays.asList(org.niis.xroad.restapi.openapi.model.KeyUsage.NON_REPUDIATION)),
                new ArrayList<>(onlyCertificate.getKeyUsages()));
        try {
            certificates = clientsApiController.getClientCertificates("FI:GOV:M2");
            fail("should throw NotFoundException for 404");
        } catch (NotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(roles = "WRONG_ROLE")
    public void forbidden() {
        try {
            ResponseEntity<List<Client>> response = clientsApiController.findClients(null, null, null, null, null, null,
                    null);
            fail("should throw AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
    }

    /**
     * @param certificateInfo one certificate to put inside this tokenInfo
     * structure
     * @return
     */
    private List<TokenInfo> createMockTokenInfos(CertificateInfo certificateInfo) {
        List<TokenInfo> mockTokens = new ArrayList<>();
        List<CertificateInfo> certificates = new ArrayList<>();
        if (certificateInfo != null) {
            certificates.add(certificateInfo);
        }
        KeyInfo keyInfo = new KeyInfo(false, null,
                "friendlyName", "id", "label", "publicKey",
                certificates, new ArrayList<CertRequestInfo>(),
                "signMecchanismName");
        TokenInfo tokenInfo = new TokenInfo("type",
                "friendlyName", "id",
                false, false, false,
                "serialNumber", "label", -1,
                null, Arrays.asList(keyInfo), null);
        mockTokens.add(tokenInfo);
        return mockTokens;
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_INTERNAL_CERTS" })
    public void addTlsCert() throws Exception {
        ResponseEntity<List<CertificateDetails>> certs = clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1);
        assertEquals(0, certs.getBody().size());
        ResponseEntity<CertificateDetails> response =
                clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        CertificateDetails certificateDetails = response.getBody();
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), certificateDetails.getHash());
        assertEquals("O=Internet Widgits Pty Ltd, ST=Some-State, C=AU",
                certificateDetails.getSubjectDistinguishedName());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertLocationHeader("/api/certificates/" + certificateDetails.getHash(), response);

        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
        // cert already exists
        try {
            response = clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                    getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
        // cert is invalid
        try {
            response = clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                    getResource(CertificateTestUtils.getInvalidCertBytes()));
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
        }
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_DETAILS",
            "DELETE_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_INTERNAL_CERTS" })
    public void deleteTlsCert() throws Exception {
        ResponseEntity<CertificateDetails> response =
                clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
        ResponseEntity<Void> deleteResponse =
                clientsApiController.deleteClientTlsCertificate(CLIENT_ID_SS1,
                        CertificateTestUtils.getWidgitsCertificateHash());
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertEquals(0, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
        // cert does not exist
        try {
            clientsApiController.deleteClientTlsCertificate(CLIENT_ID_SS1,
                    CertificateTestUtils.getWidgitsCertificateHash());
            fail("should have thrown NotFoundException");
        } catch (NotFoundException expected) {
        }
        assertEquals(0, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_INTERNAL_CERT_DETAILS",
            "VIEW_CLIENT_INTERNAL_CERTS" })
    public void findTlsCert() throws Exception {
        ResponseEntity<CertificateDetails> response =
                clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());
        ResponseEntity<CertificateDetails> findResponse =
                clientsApiController.getClientTlsCertificate(CLIENT_ID_SS1,
                        CertificateTestUtils.getWidgitsCertificateHash());
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), findResponse.getBody().getHash());
        // case insensitive
        findResponse =
                clientsApiController.getClientTlsCertificate(CLIENT_ID_SS1,
                        "63a104b2bac14667873c5dbd54be25bc687b3702");
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), findResponse.getBody().getHash());
        // not found
        try {
            clientsApiController.getClientTlsCertificate(CLIENT_ID_SS1,
                    "63a104b2bac1466");
            fail("should have thrown NotFoundException");
        } catch (NotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_LOCAL_GROUP" })
    public void addLocalGroup() throws Exception {
        ResponseEntity<LocalGroup> response = clientsApiController.addClientGroup(CLIENT_ID_SS1,
                createGroup(NEW_GROUPCODE));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LocalGroup localGroup = response.getBody();
        assertEquals(NEW_GROUPCODE, localGroup.getCode());
        assertLocationHeader("/api/local-groups/" + localGroup.getId(), response);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "ADD_LOCAL_GROUP" })
    public void getClientGroups() throws Exception {
        ResponseEntity<List<LocalGroup>> response =
                clientsApiController.getClientGroups(CLIENT_ID_SS1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    private static LocalGroup createGroup(String groupCode) {
        LocalGroup localGroup = new LocalGroup();
        localGroup.setDescription(GROUP_DESC);
        localGroup.setCode(groupCode);
        return localGroup;
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByAllSearchTermsExcludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(SUBSYSTEM1 + NAME_APPENDIX,
                INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1, false, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        List<Client> clients = clientsResponse.getBody();
        Client client = clients.get(0);
        assertEquals(SUBSYSTEM1 + NAME_APPENDIX, client.getMemberName());
        assertEquals(MEMBER_CLASS_GOV, client.getMemberClass());
        assertEquals(MEMBER_CODE_M1, client.getMemberCode());
        assertEquals(SUBSYSTEM1, client.getSubsystemCode());
        assertEquals(ConnectionType.HTTPS_NO_AUTH, client.getConnectionType());
        assertEquals(ClientStatus.REGISTERED, client.getStatus());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClients() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, null, null, null,
                true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(7, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByMemberCodeIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, null,
                MEMBER_CODE_M1, null, true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(5, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByMemberClassIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, MEMBER_CLASS_PRO,
                null, null, true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(2, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByNameIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(SUBSYSTEM2 + NAME_APPENDIX,
                null, null, null, null, false, true);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        // not found
        clientsResponse = clientsApiController.findClients("DOES_NOT_EXIST", null, null, null, null, true, false);
        assertEquals(0, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findInternalClientsByAllSearchTermsExcludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(SUBSYSTEM1 + NAME_APPENDIX,
                INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1, false, true);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findInternalClientsBySubsystemExcludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, null, null,
                SUBSYSTEM2, false, true);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        // not found
        clientsResponse = clientsApiController.findClients(null, null, null, null, SUBSYSTEM3, false, true);
        assertEquals(0, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void getServiceDescriptions() {
        // client with 0 services
        ResponseEntity<List<ServiceDescription>> descriptions =
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS2);
        assertEquals(1, descriptions.getBody().size());

        // client not found
        try {
            descriptions = clientsApiController.getClientServiceDescriptions("FI:GOV:M1:NONEXISTENT");
            fail("should throw NotFoundException to 404");
        } catch (NotFoundException expected) {
        }

        // bad client id
        try {
            descriptions = clientsApiController.getClientServiceDescriptions("foobar");
            fail("should throw BadRequestException");
        } catch (BadRequestException expected) {
        }

        // client with some services
        descriptions = clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1);
        assertEquals(HttpStatus.OK, descriptions.getStatusCode());
        assertEquals(3, descriptions.getBody().size());
        ServiceDescription serviceDescription = getDescription(descriptions.getBody(),
                "https://restservice.com/api/v1")
                .get();
        assertEquals(CLIENT_ID_SS1, serviceDescription.getClientId());
        assertEquals(true, serviceDescription.getDisabled());
        assertEquals("Kaputt", serviceDescription.getDisabledNotice());
        assertNotNull(serviceDescription.getRefreshedAt());
        assertEquals(ServiceType.REST, serviceDescription.getType());
        assertEquals(1, serviceDescription.getServices().size());

        Service service = serviceDescription.getServices().iterator().next();
        assertEquals(CLIENT_ID_SS1 + ":test-rest-servicecode.v1", service.getId());
        assertEquals("test-rest-servicecode.v1", service.getServiceCode());
        assertEquals(Integer.valueOf(60), service.getTimeout());
        assertEquals(true, service.getSslAuth());
        assertEquals("https://restservice.com/api/v1", service.getUrl());

        ServiceDescription wsdlServiceDescription = getDescription(descriptions.getBody(),
                "https://soapservice.com/v1/Endpoint?wsdl")
                .get();
        assertEquals(3, wsdlServiceDescription.getServices().size());
    }

    private Optional<ServiceDescription> getDescription(List<ServiceDescription> descriptions, String url) {
        return descriptions.stream()
                .filter(serviceDescription -> serviceDescription.getUrl().equals(url))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS" })
    public void findAllClientsByPartialNameIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(SUBSYSTEM3, null,
                null, null, null, false, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS" })
    public void findAllClientsByPartialSearchTermsIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, "F",
                "OV", "1", "1", false, true);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescription() {
        ServiceDescriptionAdd serviceDescription = new ServiceDescriptionAdd()
                .url("file:src/test/resources/wsdl/valid.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        serviceDescription.setIgnoreWarnings(false);

        ResponseEntity<ServiceDescription> response = clientsApiController.addClientServiceDescription(
                CLIENT_ID_SS1, serviceDescription);
        ServiceDescription addedServiceDescription = response.getBody();
        assertNotNull(addedServiceDescription.getId());
        assertEquals(serviceDescription.getUrl(), addedServiceDescription.getUrl());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertLocationHeader("/api/service-descriptions/" + addedServiceDescription.getId(), response);

        ResponseEntity<List<ServiceDescription>> descriptions =
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1);
        assertEquals(4, descriptions.getBody().size());
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
            assertEquals(ERROR_WSDL_EXISTS, expected.getError().getCode());
        }
        serviceDescription = new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/testservice.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(false);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
            assertErrorWithMetadata(ERROR_SERVICE_EXISTS, expected,
                    "xroadGetRandom.v1", "file:src/test/resources/wsdl/valid.wsdl");
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescriptionParserFail() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/invalid.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertErrorWithoutMetadata(ERROR_INVALID_WSDL, expected);
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescriptionWithWarnings() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/warning.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(false);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertErrorWithoutMetadata(ERROR_WARNINGS_DETECTED,
                    expected);
            assertWarning(WARNING_WSDL_VALIDATION_WARNINGS,
                    WsdlValidatorTest.MOCK_VALIDATOR_WARNING,
                    expected);
        }

        // now lets ignore the warnings
        serviceDescription.setIgnoreWarnings(true);
        clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
        ResponseEntity<List<ServiceDescription>> descriptions =
                clientsApiController.getClientServiceDescriptions(CLIENT_ID_SS1);
        assertEquals(4, descriptions.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescriptionValidationFail() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/error.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(false);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertErrorWithMetadata(WsdlValidator.ERROR_WSDL_VALIDATION_FAILED,
                    WsdlValidatorTest.MOCK_VALIDATOR_ERROR, expected);
        }

        // cannot ignore these fatal errors
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertErrorWithMetadata(WsdlValidator.ERROR_WSDL_VALIDATION_FAILED,
                    WsdlValidatorTest.MOCK_VALIDATOR_ERROR, expected);
        }

    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescriptionSkipValidation() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/error.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertErrorWithMetadata(WsdlValidator.ERROR_WSDL_VALIDATION_FAILED,
                    WsdlValidatorTest.MOCK_VALIDATOR_ERROR, expected);
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findAllSubjects() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1, null,
                null, null, null, null, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(9, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByName() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                SUBSYSTEM2 + NAME_APPENDIX, null, null, null, null, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(1, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByGroupDescription() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                GLOBAL_GROUP, null, null, null, null, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(3, subjects.size());

        subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                "foo", null, null, null, null, null);
        subjects = subjectsResponse.getBody();
        assertEquals(2, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByType() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, SubjectType.LOCALGROUP, null, null, null, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(2, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByInstance() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, null, INSTANCE_EE, null, null, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(5, subjects.size()); // includes localgroups
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByMemberClass() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, null, null, MEMBER_CLASS_GOV, null, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(3, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByMemberOrGroupCode() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, null, null, null, MEMBER_CODE_M1, null);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(3, subjects.size());

        subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, null, null, null, "group1", null);
        subjects = subjectsResponse.getBody();
        assertEquals(1, subjects.size());

        subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, null, null, null, "global-group-2", null);
        subjects = subjectsResponse.getBody();
        assertEquals(1, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsBySubsystemCode() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                null, null, null, null, null, SUBSYSTEM2);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(1, subjects.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsByAllSearchTerms() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                SUBSYSTEM3 + NAME_APPENDIX, SubjectType.SUBSYSTEM, INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M2,
                SUBSYSTEM3);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(1, subjects.size());
    }

    @Test(expected = NotFoundException.class)
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsClientNotFound() {
        clientsApiController.findSubjects(CLIENT_ID_SS4, null, null, null, null, null, null);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findSubjectsNoResults() {
        ResponseEntity<List<Subject>> subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                SUBSYSTEM3 + NAME_APPENDIX, SubjectType.LOCALGROUP, INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M2,
                SUBSYSTEM3);
        List<Subject> subjects = subjectsResponse.getBody();
        assertEquals(0, subjects.size());

        subjectsResponse = clientsApiController.findSubjects(CLIENT_ID_SS1,
                "nothing", null, null, null, "unknown-code", null);
        subjects = subjectsResponse.getBody();
        assertEquals(0, subjects.size());
    }
}
