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
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.openapi.model.AccessRight;
import org.niis.xroad.restapi.openapi.model.AccessRights;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ClientAdd;
import org.niis.xroad.restapi.openapi.model.ClientStatus;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.openapi.model.ConnectionTypeWrapper;
import org.niis.xroad.restapi.openapi.model.LocalGroup;
import org.niis.xroad.restapi.openapi.model.LocalGroupAdd;
import org.niis.xroad.restapi.openapi.model.OrphanInformation;
import org.niis.xroad.restapi.openapi.model.Service;
import org.niis.xroad.restapi.openapi.model.ServiceClient;
import org.niis.xroad.restapi.openapi.model.ServiceClientType;
import org.niis.xroad.restapi.openapi.model.ServiceDescription;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionAdd;
import org.niis.xroad.restapi.openapi.model.ServiceType;
import org.niis.xroad.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.restapi.service.ManagementRequestSenderService;
import org.niis.xroad.restapi.service.TokenService;
import org.niis.xroad.restapi.service.UrlValidator;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertRequestInfoBuilder;
import org.niis.xroad.restapi.util.TestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils.TokenInfoBuilder;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.ServiceAlreadyExistsException.ERROR_SERVICE_EXISTS;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.WARNING_WSDL_VALIDATION_WARNINGS;
import static org.niis.xroad.restapi.service.ServiceDescriptionService.WsdlUrlAlreadyExistsException.ERROR_WSDL_EXISTS;
import static org.niis.xroad.restapi.service.UnhandledWarningsException.ERROR_WARNINGS_DETECTED;
import static org.niis.xroad.restapi.util.CertificateTestUtils.getResource;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithMetadata;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertErrorWithoutMetadata;
import static org.niis.xroad.restapi.util.DeviationTestUtils.assertWarning;
import static org.niis.xroad.restapi.util.TestUtils.CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT;
import static org.niis.xroad.restapi.util.TestUtils.assertLocationHeader;
import static org.niis.xroad.restapi.wsdl.InvalidWsdlException.ERROR_INVALID_WSDL;

/**
 * Test ClientsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ClientsApiControllerIntegrationTest {

    private List<GlobalGroupInfo> globalGroupInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_FI, TestUtils.GLOBALGROUP),
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_FI, TestUtils.GLOBALGROUP1),
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_EE, TestUtils.GLOBALGROUP2)));
    private List<String> instanceIdentifiers = new ArrayList<>(Arrays.asList(
            TestUtils.INSTANCE_FI,
            TestUtils.INSTANCE_EE));

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private TokenService tokenService;

    @SpyBean
    // partial mocking, just override getValidatorCommand()
    private WsdlValidator wsdlValidator;

    @MockBean
    private UrlValidator urlValidator;

    @MockBean
    private ManagementRequestSenderService managementRequestSenderService;

    @Before
    public void setup() throws Exception {
        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });
        when(globalConfFacade.getMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM2),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        TestUtils.SUBSYSTEM3),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M2,
                        null)
        )
        ));
        List<TokenInfo> mockTokens = createMockTokenInfos(null);
        when(tokenService.getAllTokens()).thenReturn(mockTokens);
        when(wsdlValidator.getWsdlValidatorCommand()).thenReturn("src/test/resources/validator/mock-wsdlvalidator.sh");
        when(globalConfFacade.getGlobalGroups(any())).thenReturn(globalGroupInfos);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfFacade.getInstanceIdentifiers()).thenReturn(instanceIdentifiers);
        // mock for URL validator - FormatUtils is tested independently
        when(urlValidator.isValidUrl(any())).thenReturn(true);
        when(managementRequestSenderService.sendClientRegisterRequest(any())).thenReturn(0);
        when(managementRequestSenderService.sendOwnerChangeRequest(any())).thenReturn(0);
    }

    @Autowired
    private ClientsApiController clientsApiController;

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getAllClients() {
        ResponseEntity<List<Client>> response =
                clientsApiController.findClients(null, null, null, null, null, true, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(9, response.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void ownerMemberFlag() {
        ResponseEntity<List<Client>> response =
                clientsApiController.findClients(null, null, null, null, null, true, false);
        assertEquals(9, response.getBody().size());
        List<Client> owners = response.getBody().stream()
                .filter(Client::getOwner)
                .collect(Collectors.toList());
        assertEquals(1, owners.size());
        assertEquals("FI:GOV:M1", owners.iterator().next().getId());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getAllLocalClients() {
        ResponseEntity<List<Client>> response = clientsApiController.findClients(null, null, null, null, null, true,
                true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().size());
        Client client = response.getBody().get(0);
        assertEquals(TestUtils.NAME_FOR + "test-member", client.getMemberName());
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
        assertEquals(TestUtils.NAME_FOR + "test-member", client.getMemberName());
        assertEquals("GOV", client.getMemberClass());
        assertEquals("M1", client.getMemberCode());
        assertEquals("FI:GOV:M1", client.getId());
        assertNull(client.getSubsystemCode());
        response = clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        client = response.getBody();
        assertEquals(ConnectionType.HTTPS_NO_AUTH, client.getConnectionType());
        assertEquals(ClientStatus.REGISTERED, client.getStatus());
        assertEquals(TestUtils.NAME_FOR + "SS1", client.getMemberName());
        assertEquals("GOV", client.getMemberClass());
        assertEquals("M1", client.getMemberCode());
        assertEquals("FI:GOV:M1:SS1", client.getId());
        assertEquals("SS1", client.getSubsystemCode());
        try {
            clientsApiController.getClient("FI:GOV:M1:SS3");
            fail("should throw ResourceNotFoundException to 404");
        } catch (ResourceNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "EDIT_CLIENT_INTERNAL_CONNECTION_TYPE", "VIEW_CLIENT_DETAILS" })
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
    public void getClientSignCertificates() throws Exception {
        ResponseEntity<List<TokenCertificate>> certificates =
                clientsApiController.getClientSignCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(0, certificates.getBody().size());
        CertificateInfo mockCertificate = new CertificateInfo(
                ClientId.create("FI", "GOV", "M1"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "id", CertificateTestUtils.getMockCertificateBytes(), null);
        when(tokenService.getSignCertificates(any())).thenReturn(Collections.singletonList(mockCertificate));

        certificates = clientsApiController.getClientSignCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(1, certificates.getBody().size());
        TokenCertificate onlyCertificate = certificates.getBody().get(0);
        assertEquals("N/A", onlyCertificate.getCertificateDetails().getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"),
                onlyCertificate.getCertificateDetails().getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"),
                onlyCertificate.getCertificateDetails().getNotAfter());
        assertEquals("1", onlyCertificate.getCertificateDetails().getSerial());
        assertEquals(new Integer(3), onlyCertificate.getCertificateDetails().getVersion());
        assertEquals("SHA512withRSA", onlyCertificate.getCertificateDetails().getSignatureAlgorithm());
        assertEquals("RSA", onlyCertificate.getCertificateDetails().getPublicKeyAlgorithm());
        assertEquals("A2293825AA82A5429EC32803847E2152A303969C", onlyCertificate.getCertificateDetails().getHash());
        assertTrue(onlyCertificate.getCertificateDetails().getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(onlyCertificate.getCertificateDetails().getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(new Integer(65537), onlyCertificate.getCertificateDetails().getRsaPublicKeyExponent());
        assertEquals(new ArrayList<>(Arrays.asList(org.niis.xroad.restapi.openapi.model.KeyUsage.NON_REPUDIATION)),
                new ArrayList<>(onlyCertificate.getCertificateDetails().getKeyUsages()));
        try {
            certificates = clientsApiController.getClientSignCertificates("FI:GOV:M2");
            fail("should throw ResourceNotFoundException for 404");
        } catch (ResourceNotFoundException expected) {
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
    @WithMockUser(authorities = { "VIEW_CLIENT_INTERNAL_CERTS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addTlsCert() throws Exception {
        ResponseEntity<List<CertificateDetails>> certs = clientsApiController.getClientTlsCertificates(
                TestUtils.CLIENT_ID_SS1);
        assertEquals(0, certs.getBody().size());
        ResponseEntity<CertificateDetails> response =
                clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        CertificateDetails certificateDetails = response.getBody();
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), certificateDetails.getHash());
        assertEquals("O=Internet Widgits Pty Ltd, ST=Some-State, C=AU",
                certificateDetails.getSubjectDistinguishedName());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertLocationHeader("/api/clients/" + TestUtils.CLIENT_ID_SS1 + "/tls-certificates/"
                + certificateDetails.getHash(), response);

        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        // cert already exists
        try {
            response = clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                    getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        // cert is invalid
        try {
            response = clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                    getResource(CertificateTestUtils.getInvalidCertBytes()));
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
        }
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT_INTERNAL_CERT", "DELETE_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_INTERNAL_CERTS" })
    public void deleteTlsCert() throws Exception {
        ResponseEntity<CertificateDetails> response =
                clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        ResponseEntity<Void> deleteResponse =
                clientsApiController.deleteClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        CertificateTestUtils.getWidgitsCertificateHash());
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        assertEquals(0, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        // cert does not exist
        try {
            clientsApiController.deleteClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                    CertificateTestUtils.getWidgitsCertificateHash());
            fail("should have thrown ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
        }
        assertEquals(0, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT_INTERNAL_CERT", "VIEW_CLIENT_INTERNAL_CERTS",
            "VIEW_CLIENT_INTERNAL_CERT_DETAILS" })
    public void findTlsCert() throws Exception {
        ResponseEntity<CertificateDetails> response =
                clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        ResponseEntity<CertificateDetails> findResponse =
                clientsApiController.getClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        CertificateTestUtils.getWidgitsCertificateHash());
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), findResponse.getBody().getHash());
        // case insensitive
        findResponse =
                clientsApiController.getClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        "63a104b2bac14667873c5dbd54be25bc687b3702");
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), findResponse.getBody().getHash());
        // not found
        try {
            clientsApiController.getClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                    "63a104b2bac1466");
            fail("should have thrown ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP" })
    public void addLocalGroup() throws Exception {
        ResponseEntity<LocalGroup> response = clientsApiController.addClientLocalGroup(TestUtils.CLIENT_ID_SS1,
                createLocalGroupAdd(TestUtils.NEW_GROUPCODE));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LocalGroup localGroup = response.getBody();
        assertEquals(TestUtils.NEW_GROUPCODE, localGroup.getCode());
        assertLocationHeader("/api/local-groups/" + localGroup.getId(), response);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_LOCAL_GROUPS" })
    public void getClientGroups() throws Exception {
        ResponseEntity<List<LocalGroup>> response =
                clientsApiController.getClientLocalGroups(TestUtils.CLIENT_ID_SS1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
    }

    private static LocalGroupAdd createLocalGroupAdd(String groupCode) {
        LocalGroupAdd localGroupAdd = new LocalGroupAdd();
        localGroupAdd.setDescription(TestUtils.GROUP_DESC);
        localGroupAdd.setCode(groupCode);
        return localGroupAdd;
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByAllSearchTermsExcludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1,
                false, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        List<Client> clients = clientsResponse.getBody();
        Client client = clients.get(0);
        assertEquals(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1, client.getMemberName());
        assertEquals(TestUtils.MEMBER_CLASS_GOV, client.getMemberClass());
        assertEquals(TestUtils.MEMBER_CODE_M1, client.getMemberCode());
        assertEquals(TestUtils.SUBSYSTEM1, client.getSubsystemCode());
        assertEquals(ConnectionType.HTTPS_NO_AUTH, client.getConnectionType());
        assertEquals(ClientStatus.REGISTERED, client.getStatus());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClients() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, null, null, null,
                true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(9, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByMemberCodeIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, null,
                TestUtils.MEMBER_CODE_M1, null, true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(5, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByMemberClassIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null,
                TestUtils.MEMBER_CLASS_PRO,
                null, null, true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(2, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByNameIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM2,
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
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1,
                false, true);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findInternalClientsBySubsystemExcludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(null, null, null, null,
                TestUtils.SUBSYSTEM2, false, true);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        // not found
        clientsResponse = clientsApiController.findClients(null, null, null, null, TestUtils.SUBSYSTEM3, false, true);
        assertEquals(0, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_SERVICES" })
    public void getServiceDescriptions() {
        // client with 0 services
        ResponseEntity<List<ServiceDescription>> descriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS2);
        assertEquals(1, descriptions.getBody().size());

        // client not found
        try {
            descriptions = clientsApiController.getClientServiceDescriptions("FI:GOV:M1:NONEXISTENT");
            fail("should throw ResourceNotFoundException to 404");
        } catch (ResourceNotFoundException expected) {
        }

        // bad client id
        try {
            descriptions = clientsApiController.getClientServiceDescriptions("foobar");
            fail("should throw BadRequestException");
        } catch (BadRequestException expected) {
        }

        // client with some services
        descriptions = clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1);
        assertEquals(HttpStatus.OK, descriptions.getStatusCode());
        assertEquals(CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT, descriptions.getBody().size());
        ServiceDescription serviceDescription = getDescription(descriptions.getBody(),
                "https://restservice.com/api/v1")
                .get();
        assertEquals(TestUtils.CLIENT_ID_SS1, serviceDescription.getClientId());
        assertEquals(true, serviceDescription.getDisabled());
        assertEquals("Kaputt", serviceDescription.getDisabledNotice());
        assertNotNull(serviceDescription.getRefreshedAt());
        assertEquals(ServiceType.OPENAPI3, serviceDescription.getType());
        assertEquals(1, serviceDescription.getServices().size());

        Service service = serviceDescription.getServices().iterator().next();
        assertEquals(TestUtils.CLIENT_ID_SS1 + ":openapi-servicecode.v1", service.getId());
        assertEquals("openapi-servicecode.v1", service.getServiceCode());
        assertEquals(Integer.valueOf(60), service.getTimeout());
        assertEquals(true, service.getSslAuth());
        assertEquals("https://restservice.com/api/v1", service.getUrl());

        ServiceDescription wsdlServiceDescription = getDescription(descriptions.getBody(),
                "https://soapservice.com/v1/Endpoint?wsdl")
                .get();
        assertEquals(3, wsdlServiceDescription.getServices().size());

        ServiceDescription serviceDescriptionTypeRest = getDescription(descriptions.getBody(),
                "https://restservice.com/api/v1/nosuchservice").get();
        assertEquals(ServiceType.REST, serviceDescriptionTypeRest.getType());
    }

    private Optional<ServiceDescription> getDescription(List<ServiceDescription> descriptions, String url) {
        return descriptions.stream()
                .filter(serviceDescription -> serviceDescription.getUrl().equals(url))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS" })
    public void findAllClientsByPartialNameIncludeMembers() {
        ResponseEntity<List<Client>> clientsResponse = clientsApiController.findClients(TestUtils.SUBSYSTEM3, null,
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

    private Client createTestClient(String memberClass, String memberCode, String subsystemCode) {
        Client client = new Client();
        client.setMemberClass(memberClass);
        client.setMemberCode(memberCode);
        client.setSubsystemCode(subsystemCode);
        return client;
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT" })
    public void addClient() {
        Client clientToAdd = createTestClient("GOV", "M2", null);
        ResponseEntity<Client> response = clientsApiController.addClient(
                new ClientAdd().client(clientToAdd).ignoreWarnings(false));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("FI", response.getBody().getInstanceId());
        assertEquals("M2", response.getBody().getMemberCode());
        assertEquals(ClientStatus.SAVED, response.getBody().getStatus());
        assertEquals(ConnectionType.HTTPS, response.getBody().getConnectionType());
        assertFalse(response.getBody().getOwner());
        assertLocationHeader("/api/clients/FI:GOV:M2", response);

        response = clientsApiController.addClient(
                new ClientAdd().client(clientToAdd
                        .connectionType(ConnectionType.HTTPS_NO_AUTH)
                        .subsystemCode("SUBSYSTEM1"))
                        .ignoreWarnings(false));
        assertEquals("SUBSYSTEM1", response.getBody().getSubsystemCode());
        assertEquals(ClientStatus.SAVED, response.getBody().getStatus());
        assertEquals(ConnectionType.HTTPS_NO_AUTH, response.getBody().getConnectionType());
        assertLocationHeader("/api/clients/FI:GOV:M2:SUBSYSTEM1", response);
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT" })
    public void addClientConflicts() {
        // conflict: client already exists
        Client clientToAdd = createTestClient("GOV", "M1", null);
        try {
            clientsApiController.addClient(
                    new ClientAdd().client(clientToAdd).ignoreWarnings(false));
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }

        // conflict: two additional members
        clientToAdd = createTestClient("GOV", "ADDITIONAL1", null);
        ResponseEntity<Client> response = clientsApiController.addClient(
                new ClientAdd().client(clientToAdd).ignoreWarnings(true));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        try {
            clientsApiController.addClient(
                    new ClientAdd().client(clientToAdd.memberCode("ADDITIONAL2")).ignoreWarnings(true));
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT" })
    public void addClientBadRequestFromWarnings() {
        // warning about unregistered client
        doReturn(null).when(globalConfFacade).getMemberName(any());
        Client clientToAdd = createTestClient("UNREGISTEREDA", "B", "C");
        try {
            clientsApiController.addClient(
                    new ClientAdd().client(clientToAdd).ignoreWarnings(false));
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertEquals(ERROR_WARNINGS_DETECTED, expected.getErrorDeviation().getCode());
        }

        ResponseEntity<Client> response = clientsApiController.addClient(
                new ClientAdd().client(clientToAdd).ignoreWarnings(true));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescription() {
        ServiceDescriptionAdd serviceDescription = new ServiceDescriptionAdd()
                .url("file:src/test/resources/wsdl/valid.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        serviceDescription.setIgnoreWarnings(false);

        ResponseEntity<ServiceDescription> response = clientsApiController.addClientServiceDescription(
                TestUtils.CLIENT_ID_SS1, serviceDescription);
        ServiceDescription addedServiceDescription = response.getBody();
        assertNotNull(addedServiceDescription.getId());
        assertEquals(serviceDescription.getUrl(), addedServiceDescription.getUrl());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertLocationHeader("/api/service-descriptions/" + addedServiceDescription.getId(), response);

        ResponseEntity<List<ServiceDescription>> descriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1);
        assertEquals(CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT + 1, descriptions.getBody().size());
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
            assertEquals(ERROR_WSDL_EXISTS, expected.getErrorDeviation().getCode());
        }
        serviceDescription = new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/testservice.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(false);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
            assertErrorWithMetadata(ERROR_SERVICE_EXISTS, expected,
                    "xroadGetRandom.v1", "file:src/test/resources/wsdl/valid.wsdl");
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL" })
    public void addWsdlServiceDescriptionParserFail() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/invalid.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertEquals(ERROR_INVALID_WSDL, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL", "VIEW_CLIENT_SERVICES" })
    public void addWsdlServiceDescriptionWithWarnings() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/warning.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(false);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertErrorWithoutMetadata(ERROR_WARNINGS_DETECTED,
                    expected);
            assertWarning(WARNING_WSDL_VALIDATION_WARNINGS,
                    WsdlValidatorTest.MOCK_VALIDATOR_WARNING,
                    expected);
        }

        // now lets ignore the warningDeviations
        serviceDescription.setIgnoreWarnings(true);
        clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
        ResponseEntity<List<ServiceDescription>> descriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1);
        assertEquals(CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT + 1, descriptions.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL" })
    public void addWsdlServiceDescriptionValidationFail() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/error.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(false);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertEquals(ERROR_INVALID_WSDL, expected.getErrorDeviation().getCode());
        }

        // cannot ignore these fatal errors
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertEquals(ERROR_INVALID_WSDL, expected.getErrorDeviation().getCode());
        }

    }

    @Test
    @WithMockUser(authorities = { "ADD_WSDL" })
    public void addWsdlServiceDescriptionSkipValidation() {
        ServiceDescriptionAdd serviceDescription =
                new ServiceDescriptionAdd().url("file:src/test/resources/wsdl/error.wsdl");
        serviceDescription.setType(ServiceType.WSDL);
        try {
            serviceDescription.setIgnoreWarnings(true);
            clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
            fail("should have thrown BadRequestException");
        } catch (BadRequestException expected) {
            assertEquals(ERROR_INVALID_WSDL, expected.getErrorDeviation().getCode());
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findAllServiceClientCandidates() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null,
                null, null, null, null, null);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(10, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByName() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM2, null, null, null, null, null);
        // TO DO: lots of renames
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByGroupDescription() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.GLOBALGROUP, null, null, null, null, null);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());

        serviceClientResponse = clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS1,
                "foo", null, null, null, null, null);
        serviceClients = serviceClientResponse.getBody();
        assertEquals(2, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByType() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, ServiceClientType.LOCALGROUP, null, null, null, null);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByInstance() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, TestUtils.INSTANCE_EE, null, null, null);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(6, serviceClients.size()); // includes localgroups
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByMemberClass() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, null, TestUtils.MEMBER_CLASS_GOV, null, null);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByMemberOrGroupCode() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, null, null, TestUtils.MEMBER_CODE_M1, null);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());

        serviceClientResponse = clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS1,
                null, null, null, null, "group1", null);
        serviceClients = serviceClientResponse.getBody();
        assertEquals(2, serviceClients.size());

        serviceClientResponse = clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS1,
                null, null, null, null, "group2", null);
        serviceClients = serviceClientResponse.getBody();
        assertEquals(2, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesBySubsystemCode() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, null, null, null, TestUtils.SUBSYSTEM2);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesByAllSearchTerms() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM3, ServiceClientType.SUBSYSTEM,
                TestUtils.INSTANCE_EE,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                TestUtils.SUBSYSTEM3);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesClientNotFound() {
        clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS4, null, null, null, null, null, null);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS" })
    public void findServiceClientCandidatesNoResults() {
        ResponseEntity<List<ServiceClient>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM3, ServiceClientType.LOCALGROUP,
                TestUtils.INSTANCE_EE,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                TestUtils.SUBSYSTEM3);
        List<ServiceClient> serviceClients = serviceClientResponse.getBody();
        assertEquals(0, serviceClients.size());

        serviceClientResponse = clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS1,
                "nothing", null, null, null, "unknown-code", null);
        serviceClients = serviceClientResponse.getBody();
        assertEquals(0, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_CLIENT", "ADD_CLIENT", "VIEW_CLIENT_DETAILS" })
    public void deleteClient() {
        try {
            clientsApiController.deleteClient("FI:GOV:M1");
            fail("should have thrown exception (cant delete owner, or registered)");
        } catch (ConflictException expected) {
        }
        try {
            clientsApiController.deleteClient("FI:GOV:NOT-EXISTING");
            fail("should have thrown exception");
        } catch (ResourceNotFoundException expected) {
        }
        // create a new client, and then delete it
        Client clientToAdd = createTestClient("GOV", "M3", null);
        ResponseEntity<Client> addResponse = clientsApiController.addClient(
                new ClientAdd().client(clientToAdd).ignoreWarnings(false));
        assertEquals(HttpStatus.CREATED, addResponse.getStatusCode());

        ResponseEntity<Void> deleteResponse =
                clientsApiController.deleteClient("FI:GOV:M3");
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        try {
            clientsApiController.getClient("FI:GOV:M3");
            fail("should have thrown exception");
        } catch (ResourceNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "DELETE_CLIENT" })
    public void getOrphans() {
        ClientId orphanClient = TestUtils.getClientId("FI:GOV:ORPHAN:SS1");
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(new CertRequestInfoBuilder()
                        .clientId(orphanClient)
                        .build())
                .build();
        TokenInfo tokenInfo = new TokenInfoBuilder()
                .key(keyInfo)
                .build();
        when(tokenService.getAllTokens()).thenReturn(Collections.singletonList(tokenInfo));
        ResponseEntity<OrphanInformation> orphanResponse = clientsApiController
                .getClientOrphans("FI:GOV:ORPHAN:SS1");
        assertEquals(HttpStatus.OK, orphanResponse.getStatusCode());
        assertEquals(true, orphanResponse.getBody().getOrphansExist());

        try {
            clientsApiController.getClientOrphans("FI:GOV:M1:SS777");
            fail("should not find orphans");
        } catch (ResourceNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "DELETE_CLIENT", "DELETE_SIGN_KEY" })
    public void deleteOrphans() throws Exception {
        ClientId orphanClient = TestUtils.getClientId("FI:GOV:ORPHAN:SS1");
        String orphanKeyId = "orphan-key";
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .id(orphanKeyId)
                .csr(new CertRequestInfoBuilder()
                        .clientId(orphanClient)
                        .build())
                .build();
        TokenInfo tokenInfo = new TokenInfoBuilder()
                .key(keyInfo)
                .build();
        when(tokenService.getAllTokens()).thenReturn(Collections.singletonList(tokenInfo));
        when(tokenService.getTokenForKeyId(any())).thenReturn(tokenInfo);
        ResponseEntity<Void> orphanResponse = clientsApiController
                .deleteOrphans("FI:GOV:ORPHAN:SS1");
        assertEquals(HttpStatus.NO_CONTENT, orphanResponse.getStatusCode());

        verify(signerProxyFacade, times(1))
                .deleteKey(orphanKeyId, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(orphanKeyId, false);
        verifyNoMoreInteractions(signerProxyFacade);

        try {
            clientsApiController.deleteOrphans("FI:GOV:M1:SS777");
            fail("should not find orphans");
        } catch (ResourceNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "SEND_CLIENT_REG_REQ" })
    public void registerClient() {
        ResponseEntity<Void> response = clientsApiController.registerClient(TestUtils.CLIENT_ID_M2_SS6);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "SEND_CLIENT_REG_REQ" })
    public void registerOwner() {
        clientsApiController.registerClient(TestUtils.OWNER_ID);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "SEND_CLIENT_REG_REQ" })
    public void registerClientWrongStatus() {
        clientsApiController.registerClient(TestUtils.CLIENT_ID_SS1);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "SEND_CLIENT_DEL_REQ" })
    public void unregisterOwner() {
        clientsApiController.unregisterClient(TestUtils.OWNER_ID);
    }

    @Test
    @WithMockUser(authorities = { "SEND_CLIENT_DEL_REQ" })
    public void unregisterClient() {
        ResponseEntity<Void> response = clientsApiController.unregisterClient(TestUtils.CLIENT_ID_SS1);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "SEND_CLIENT_DEL_REQ" })
    public void unregisterClientWrongStatus() {
        clientsApiController.unregisterClient(TestUtils.CLIENT_ID_M2_SS6);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "SEND_OWNER_CHANGE_REQ", "ADD_CLIENT" })
    public void changeOwnerNotRegistered() {
        clientsApiController.addClient(new ClientAdd().client(createTestClient(
                "GOV", "M2", null)).ignoreWarnings(true));

        ResponseEntity<Void> response = clientsApiController.changeOwner(createTestClient(
                "GOV", "M2", null));
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = { "SEND_OWNER_CHANGE_REQ" })
    public void changeOwnerCurrentOwner() {
        ResponseEntity<Void> response = clientsApiController.changeOwner(createTestClient(
                "GOV", "M1", null));
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = { "SEND_OWNER_CHANGE_REQ" })
    public void changeOwnerSubsystem() {
        ResponseEntity<Void> response = clientsApiController.changeOwner(createTestClient(
                "GOV", "M1", "SS1"));
    }

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = { "SEND_OWNER_CHANGE_REQ" })
    public void changeOwnerClientDoesNotExist() {
        Client client = new Client();
        client.setInstanceId("non");
        client.setMemberClass("existing");
        client.setMemberCode("client");
        ResponseEntity<Void> response = clientsApiController.changeOwner(createTestClient(
                "non", "existing", null));
    }


    @Test
    @WithMockUser(authorities = { "EDIT_ACL_SUBJECT_OPEN_SERVICES" })
    public void addServiceClientAccessRights() throws Exception {

        String encodedOwnerId = "FI:GOV:M1:SS1";
        String encodedSubsystemId = "EE:GOV:M2:SS3";
        String encodedLocalGroupId = "2"; // pk
        String encodedGlobalGroupId = TestUtils.INSTANCE_FI + ":" + TestUtils.GLOBALGROUP;
        Set<String> serviceCodes = new HashSet<>(Arrays.asList(
                "calculatePrime", "openapi-servicecode", "rest-servicecode"));
        AccessRights accessRights = new AccessRights();
        accessRights.addItemsItem(new AccessRight().serviceCode("calculatePrime"));
        accessRights.addItemsItem(new AccessRight().serviceCode("openapi-servicecode"));
        accessRights.addItemsItem(new AccessRight().serviceCode("rest-servicecode"));

        ResponseEntity<List<AccessRight>> response = clientsApiController
                .addServiceClientAccessRights(encodedOwnerId, encodedSubsystemId, accessRights);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        AccessRight accessRight = findAccessRight("calculatePrime", response.getBody());
        assertNotNull(accessRight);
        assertEquals("calculatePrime-title", accessRight.getServiceTitle());
        assertNotNull(accessRight.getRightsGivenAt());
        assertNotNull(findAccessRight("openapi-servicecode", response.getBody()));
        assertNotNull(findAccessRight("rest-servicecode", response.getBody()));

        response = clientsApiController.addServiceClientAccessRights(
                encodedOwnerId, encodedLocalGroupId, accessRights);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3, response.getBody().size());

        response = clientsApiController.addServiceClientAccessRights(
                encodedOwnerId, encodedGlobalGroupId, accessRights);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3, response.getBody().size());

        try {
            // try to add duplicate
            clientsApiController.addServiceClientAccessRights(encodedOwnerId, encodedGlobalGroupId, accessRights);
            fail("should throw exception");
        } catch (ConflictException expected) {
        }
    }

    private AccessRight findAccessRight(String serviceCode, List<AccessRight> accessRights) {
        return accessRights.stream()
                .filter(dto -> dto.getServiceCode().equals(serviceCode))
                .findFirst()
                .orElse(null);
    }
}
