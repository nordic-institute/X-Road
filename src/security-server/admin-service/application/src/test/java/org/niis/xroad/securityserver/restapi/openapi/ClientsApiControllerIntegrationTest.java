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
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.model.GlobalGroupInfo;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.converter.comparator.ClientSortingComparator;
import org.niis.xroad.securityserver.restapi.converter.comparator.ServiceClientSortingComparator;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRightDto;
import org.niis.xroad.securityserver.restapi.openapi.model.AccessRightsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeWrapperDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDto;
import org.niis.xroad.securityserver.restapi.openapi.model.OrphanInformationDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificateDto;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.CertRequestInfoBuilder;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.securityserver.restapi.wsdl.WsdlValidatorTest;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.MOCK_CERTIFICATE_HASH;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.WIDGITS_CERTIFICATE_HASH;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.getResource;
import static org.niis.xroad.securityserver.restapi.util.DeviationTestUtils.assertErrorWithMetadata;
import static org.niis.xroad.securityserver.restapi.util.DeviationTestUtils.assertErrorWithoutMetadata;
import static org.niis.xroad.securityserver.restapi.util.DeviationTestUtils.assertWarning;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.assertLocationHeader;

/**
 * Test ClientsApiController
 */
public class ClientsApiControllerIntegrationTest extends AbstractApiControllerTestContext {
    @Autowired
    ClientSortingComparator clientSortingComparator;
    @Autowired
    ServiceClientSortingComparator serviceClientSortingComparator;

    private static final SecurityServerId.Conf OWNER_SERVER_ID = SecurityServerId.Conf.create(TestUtils.getM1Ss1ClientId(),
            "owner");
    private List<GlobalGroupInfo> globalGroupInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_FI, TestUtils.GLOBALGROUP),
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_FI, TestUtils.GLOBALGROUP1),
            TestUtils.getGlobalGroupInfo(TestUtils.INSTANCE_EE, TestUtils.GLOBALGROUP2)));
    private Set<String> instanceIdentifiers = new HashSet<>(Arrays.asList(
            TestUtils.INSTANCE_FI,
            TestUtils.INSTANCE_EE));
    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

    @Before
    public void setup() throws Exception {
        when(globalConfProvider.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });
        List<MemberInfo> members = List.of(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM2),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2, TestUtils.SUBSYSTEM3),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M2, null)
        );
        when(globalConfProvider.getMembers()).thenReturn(new ArrayList<>(members));
        List<TokenInfo> mockTokens = createMockTokenInfos();
        doReturn(mockTokens).when(tokenService).getAllTokens();
        when(wsdlValidator.getWsdlValidatorCommand()).thenReturn("src/test/resources/validator/mock-wsdlvalidator.sh");
        when(globalConfProvider.getGlobalGroups()).thenReturn(globalGroupInfos);
        when(globalConfProvider.getGlobalGroups(any(String[].class))).thenReturn(globalGroupInfos);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfProvider.getInstanceIdentifiers()).thenReturn(instanceIdentifiers);
        // mock for URL validator - FormatUtils is tested independently
        when(urlValidator.isValidUrl(any())).thenReturn(true);
        when(managementRequestSenderService.sendClientRegisterRequest(any(), anyString())).thenReturn(0);
        when(managementRequestSenderService.sendOwnerChangeRequest(any())).thenReturn(0);
        when(serverConfService.getSecurityServerId()).thenReturn(OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
        when(globalConfService.getMemberClassesForThisInstance()).thenReturn(new HashSet<>(MEMBER_CLASSES));
    }

    @Autowired
    private ClientsApiController clientsApiController;

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getAllClients() {
        ResponseEntity<Set<ClientDto>> response = clientsApiController.findClients(null, null, null, null, null, true,
                false, null, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(12, response.getBody().size());
        // Test sorting order
        assertEquals(true, TestUtils.isSortOrderCorrect(response.getBody(), clientSortingComparator));
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void ownerMemberFlag() {
        ResponseEntity<Set<ClientDto>> response =
                clientsApiController.findClients(null, null, null, null, null, true,
                        false, null, false);
        assertEquals(12, response.getBody().size());
        List<ClientDto> owners = response.getBody().stream()
                .filter(ClientDto::getOwner)
                .collect(Collectors.toList());
        assertEquals(1, owners.size());
        assertEquals("FI:GOV:M1", owners.iterator().next().getId());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getAllLocalClients() {
        ResponseEntity<Set<ClientDto>> response = clientsApiController.findClients(null, null, null, null, null, true,
                true, null, false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(8, response.getBody().size());
        ClientDto client = response
                .getBody()
                .stream()
                .filter(item -> item.getMemberName().equals(TestUtils.NAME_FOR + "SS1"))
                .findFirst()
                .orElse(null);
        assertEquals(TestUtils.NAME_FOR + "SS1", client.getMemberName());
        assertEquals("M1", client.getMemberCode());
    }

    @WithMockUser(authorities = "VIEW_CLIENT_DETAILS")
    public void getClient() {
        ResponseEntity<ClientDto> response =
                clientsApiController.getClient("FI:GOV:M1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        ClientDto client = response.getBody();
        assertEquals(ConnectionTypeDto.HTTP, client.getConnectionType());
        assertEquals(ClientStatusDto.REGISTERED, client.getStatus());
        assertEquals(TestUtils.NAME_FOR + "test-member", client.getMemberName());
        assertEquals("GOV", client.getMemberClass());
        assertEquals("M1", client.getMemberCode());
        assertEquals("FI:GOV:M1", client.getId());
        assertNull(client.getSubsystemCode());
        response = clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        client = response.getBody();
        assertEquals(ConnectionTypeDto.HTTPS_NO_AUTH, client.getConnectionType());
        assertEquals(ClientStatusDto.REGISTERED, client.getStatus());
        assertEquals(TestUtils.NAME_FOR + "SS1", client.getMemberName());
        assertEquals("GOV", client.getMemberClass());
        assertEquals("M1", client.getMemberCode());
        assertEquals("FI:GOV:M1:SS1", client.getId());
        assertEquals("SS1", client.getSubsystemCode());

        assertThrows(NotFoundException.class, () -> clientsApiController.getClient("FI:GOV:M1:SS3"));
    }

    @Test
    @WithMockUser(authorities = {"EDIT_CLIENT_INTERNAL_CONNECTION_TYPE", "VIEW_CLIENT_DETAILS"})
    public void updateClient() {
        ResponseEntity<ClientDto> response =
                clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(ConnectionTypeDto.HTTPS_NO_AUTH, response.getBody().getConnectionType());
        ConnectionTypeWrapperDto http = new ConnectionTypeWrapperDto();
        http.setConnectionType(ConnectionTypeDto.HTTP);
        response = clientsApiController.updateClient("FI:GOV:M1:SS1", http);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ConnectionTypeDto.HTTP, response.getBody().getConnectionType());
        response = clientsApiController.getClient("FI:GOV:M1:SS1");
        assertEquals(ConnectionTypeDto.HTTP, response.getBody().getConnectionType());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENT_DETAILS")
    public void getClientSignCertificates() {
        ResponseEntity<Set<TokenCertificateDto>> certificates =
                clientsApiController.getClientSignCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(0, certificates.getBody().size());
        CertificateInfo mockCertificate = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(ClientId.Conf.create("FI", "GOV", "M1"))
                .active(true)
                .savedToConfiguration(true)
                .certificateStatus(CertificateInfo.STATUS_REGISTERED)
                .id("id")
                .certificate(CertificateTestUtils.getMockCertificate())
                .build();
        doReturn(Collections.singletonList(mockCertificate)).when(tokenService).getSignCertificates(any());

        certificates = clientsApiController.getClientSignCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(1, certificates.getBody().size());
        Set<TokenCertificateDto> onlyCertificateSet = certificates.getBody();
        TokenCertificateDto onlyCertificate = onlyCertificateSet
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals("N/A", onlyCertificate.getCertificateDetails().getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"),
                onlyCertificate.getCertificateDetails().getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"),
                onlyCertificate.getCertificateDetails().getNotAfter());
        assertEquals("1", onlyCertificate.getCertificateDetails().getSerial());
        assertEquals(Integer.valueOf(3), onlyCertificate.getCertificateDetails().getVersion());
        assertEquals("SHA512withRSA", onlyCertificate.getCertificateDetails().getSignatureAlgorithm());
        assertEquals("RSA", onlyCertificate.getCertificateDetails().getPublicKeyAlgorithm());
        assertEquals(MOCK_CERTIFICATE_HASH, onlyCertificate.getCertificateDetails().getHash());
        assertTrue(onlyCertificate.getCertificateDetails().getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(onlyCertificate.getCertificateDetails().getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(Integer.valueOf(65537), onlyCertificate.getCertificateDetails().getRsaPublicKeyExponent());
        assertEquals(List.of(org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageDto.NON_REPUDIATION),
                new ArrayList<>(onlyCertificate.getCertificateDetails().getKeyUsages()));

        assertThrows(NotFoundException.class, () -> clientsApiController.getClientSignCertificates("FI:GOV:M2"));
    }

    @Test
    @WithMockUser(roles = "WRONG_ROLE")
    public void forbidden() {
        try {
            clientsApiController.findClients(null, null, null, null, null, null,
                    null, null, false);
            fail("should throw AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
    }

    private List<TokenInfo> createMockTokenInfos() {
        List<TokenInfo> mockTokens = new ArrayList<>();

        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .available(false)
                .keyUsageInfo(null)
                .build();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .type("type")
                .friendlyName("friendlyName")
                .readOnly(false)
                .available(false)
                .active(false)
                .status(null)
                .key(keyInfo)
                .build();

        mockTokens.add(tokenInfo);
        return mockTokens;
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_INTERNAL_CERTS", "ADD_CLIENT_INTERNAL_CERT"})
    public void addTlsCert() {
        ResponseEntity<Set<CertificateDetailsDto>> certs = clientsApiController.getClientTlsCertificates(
                TestUtils.CLIENT_ID_SS1);
        assertEquals(0, certs.getBody().size());
        ResponseEntity<CertificateDetailsDto> response =
                clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        CertificateDetailsDto certificateDetails = response.getBody();
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), certificateDetails.getHash());
        assertEquals("O=Internet Widgits Pty Ltd, ST=Some-State, C=AU",
                certificateDetails.getSubjectDistinguishedName());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertLocationHeader("/api/clients/" + TestUtils.CLIENT_ID_SS1 + "/tls-certificates/"
                + certificateDetails.getHash(), response);

        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        // cert already exists
        assertThrows(ConflictException.class, () -> clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                getResource(CertificateTestUtils.getWidgitsCertificateBytes())));
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        // cert is invalid
        assertThrows(BadRequestException.class, () -> clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                getResource(CertificateTestUtils.getInvalidCertBytes())));
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
    }

    @Test
    @WithMockUser(authorities = {"ADD_CLIENT_INTERNAL_CERT", "DELETE_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_INTERNAL_CERTS"})
    public void deleteTlsCert() {
        ResponseEntity<CertificateDetailsDto> response =
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
        assertThrows(NotFoundException.class, () -> clientsApiController.deleteClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                CertificateTestUtils.getWidgitsCertificateHash()));
        assertEquals(0, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
    }

    @Test
    @WithMockUser(authorities = {"ADD_CLIENT_INTERNAL_CERT", "VIEW_CLIENT_INTERNAL_CERTS",
            "VIEW_CLIENT_INTERNAL_CERT_DETAILS"})
    public void findTlsCert() {
        ResponseEntity<CertificateDetailsDto> response =
                clientsApiController.addClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        getResource(CertificateTestUtils.getWidgitsCertificateBytes()));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(TestUtils.CLIENT_ID_SS1).getBody().size());
        ResponseEntity<CertificateDetailsDto> findResponse =
                clientsApiController.getClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                        CertificateTestUtils.getWidgitsCertificateHash());
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), findResponse.getBody().getHash());
        // case insensitive
        findResponse = clientsApiController.getClientTlsCertificate(TestUtils.CLIENT_ID_SS1, WIDGITS_CERTIFICATE_HASH);
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals(CertificateTestUtils.getWidgitsCertificateHash(), findResponse.getBody().getHash());
        // not found
        assertThrows(NotFoundException.class, () -> clientsApiController.getClientTlsCertificate(TestUtils.CLIENT_ID_SS1,
                "63a104b2bac1466"));
    }

    @Test
    @WithMockUser(authorities = {"ADD_LOCAL_GROUP"})
    public void addLocalGroup() {
        ResponseEntity<LocalGroupDto> response = clientsApiController.addClientLocalGroup(TestUtils.CLIENT_ID_SS1,
                createLocalGroupAdd(TestUtils.NEW_GROUPCODE));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        LocalGroupDto localGroup = response.getBody();
        assertEquals(TestUtils.NEW_GROUPCODE, localGroup.getCode());
        assertLocationHeader("/api/local-groups/" + localGroup.getId(), response);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_LOCAL_GROUPS"})
    public void getClientGroups() {
        ResponseEntity<Set<LocalGroupDto>> response =
                clientsApiController.getClientLocalGroups(TestUtils.CLIENT_ID_SS1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(3, response.getBody().size());
    }

    private static LocalGroupAddDto createLocalGroupAdd(String groupCode) {
        LocalGroupAddDto localGroupAdd = new LocalGroupAddDto();
        localGroupAdd.setDescription(TestUtils.GROUP_DESC);
        localGroupAdd.setCode(groupCode);
        return localGroupAdd;
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByAllSearchTermsExcludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1,
                false, false, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        Set<ClientDto> clients = clientsResponse.getBody();
        ClientDto client = clients
                .stream()
                .findFirst()
                .orElse(null);
        assertEquals(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1, client.getMemberName());
        assertEquals(TestUtils.MEMBER_CLASS_GOV, client.getMemberClass());
        assertEquals(TestUtils.MEMBER_CODE_M1, client.getMemberCode());
        assertEquals(TestUtils.SUBSYSTEM1, client.getSubsystemCode());
        assertEquals(ConnectionTypeDto.HTTPS_NO_AUTH, client.getConnectionType());
        assertEquals(ClientStatusDto.REGISTERED, client.getStatus());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClients() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, null, null, null, null,
                true, false, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(12, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByLocalValidSignCert() {
        when(currentSecurityServerSignCertificates.getSignCertificateInfos())
                .thenReturn(createSimpleSignCertList());
        int clientsTotal = 12;
        // FI:GOV:M1, FI:GOV:M1:SS1, FI:GOV:M1:SS3
        int clientsWithValidSignCert = 3;
        // search all
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, null, null, null, null,
                true, false, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(clientsTotal, clientsResponse.getBody().size());

        // search ones with valid sign cert
        clientsResponse = clientsApiController.findClients(null, null, null, null, null,
                true, false, true, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(clientsWithValidSignCert, clientsResponse.getBody().size());

        // search ones without valid sign cert
        clientsResponse = clientsApiController.findClients(null, null, null, null, null,
                true, false, false, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals((clientsTotal - clientsWithValidSignCert), clientsResponse.getBody().size());
    }

    /**
     * Mock sign certs
     * - FI:GOV:M1 has a sign cert "cert1" with ocsp status GOOD (default)
     */
    private List<CertificateInfo> createSimpleSignCertList() {
        List<CertificateInfo> certificateInfos = new ArrayList<>();
        certificateInfos.add(new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(ClientId.Conf.create("FI", "GOV", "M1"))
                .build());
        return certificateInfos;
    }


    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByMemberCodeIncludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, null, null,
                TestUtils.MEMBER_CODE_M1, null, true, false, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(5, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByMemberClassIncludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, null,
                TestUtils.MEMBER_CLASS_PRO,
                null, null, true, false, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(3, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findAllClientsByNameIncludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM2,
                null, null, null, null, false, true, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        // not found
        clientsResponse = clientsApiController.findClients("DOES_NOT_EXIST", null, null, null, null, true, false,
                null, false);
        assertEquals(0, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findInternalClientsByAllSearchTermsExcludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1,
                false, true, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void findInternalClientsBySubsystemExcludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, null, null, null,
                TestUtils.SUBSYSTEM2, false, true, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
        // not found
        clientsResponse = clientsApiController.findClients(null, null, null, null, TestUtils.SUBSYSTEM3, false, true,
                null, false);
        assertEquals(0, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_SERVICES"})
    public void getServiceDescriptions() {
        // client with 0 services
        ResponseEntity<Set<ServiceDescriptionDto>> descriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS2);
        assertEquals(1, descriptions.getBody().size());

        // client not found
        assertThrows(NotFoundException.class, () -> clientsApiController.getClientServiceDescriptions("FI:GOV:M1:NONEXISTENT"));

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
        ServiceDescriptionDto serviceDescription = getDescription(descriptions.getBody(),
                "https://restservice.com/api/v1")
                .get();
        assertEquals(TestUtils.CLIENT_ID_SS1, serviceDescription.getClientId());
        assertEquals(true, serviceDescription.getDisabled());
        assertEquals("Kaputt", serviceDescription.getDisabledNotice());
        assertNotNull(serviceDescription.getRefreshedAt());
        assertEquals(ServiceTypeDto.OPENAPI3, serviceDescription.getType());
        assertEquals(1, serviceDescription.getServices().size());

        ServiceDto service = serviceDescription.getServices().iterator().next();
        assertEquals(TestUtils.CLIENT_ID_SS1 + ":openapi-servicecode.v1", service.getId());
        assertEquals("openapi-servicecode.v1", service.getFullServiceCode());
        assertEquals("openapi-servicecode", service.getServiceCode());
        assertEquals(Integer.valueOf(60), service.getTimeout());
        assertEquals(true, service.getSslAuth());
        assertEquals("https://restservice.com/api/v1", service.getUrl());

        ServiceDescriptionDto wsdlServiceDescription = getDescription(descriptions.getBody(),
                "https://soapservice.com/v1/Endpoint?wsdl")
                .get();
        assertEquals(4, wsdlServiceDescription.getServices().size());

        ServiceDescriptionDto serviceDescriptionDto = getDescription(descriptions.getBody(),
                "https://restservice.com/api/v1/nosuchservice").get();
        assertEquals(ServiceTypeDto.REST, serviceDescriptionDto.getType());
    }

    private Optional<ServiceDescriptionDto> getDescription(Set<ServiceDescriptionDto> descriptions, String url) {
        return descriptions.stream()
                .filter(serviceDescription -> serviceDescription.getUrl().equals(url))
                .findFirst();
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENTS"})
    public void findAllClientsByPartialNameIncludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(TestUtils.SUBSYSTEM3, null,
                null, null, null, false, false, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENTS"})
    public void findAllClientsByPartialSearchTermsIncludeMembers() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, "FI",
                "OV", "1", "1", false, true, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(1, clientsResponse.getBody().size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENTS"})
    public void findAllClientsShouldNotFindByPartialInstance() {
        ResponseEntity<Set<ClientDto>> clientsResponse = clientsApiController.findClients(null, "F",
                "OV", "1", "1", false, true, null, false);
        assertEquals(HttpStatus.OK, clientsResponse.getStatusCode());
        assertEquals(0, clientsResponse.getBody().size());
    }

    private ClientDto createTestClient(String memberClass, String memberCode, String subsystemCode) {
        ClientDto client = new ClientDto();
        client.setMemberClass(memberClass);
        client.setMemberCode(memberCode);
        client.setSubsystemCode(subsystemCode);
        return client;
    }

    @Test
    @WithMockUser(authorities = {"ADD_CLIENT"})
    public void addClient() {
        ClientDto clientToAdd = createTestClient("GOV", "M2", null);
        ResponseEntity<ClientDto> response = clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd).ignoreWarnings(false));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("FI", response.getBody().getInstanceId());
        assertEquals("M2", response.getBody().getMemberCode());
        assertEquals(ClientStatusDto.SAVED, response.getBody().getStatus());
        assertEquals(ConnectionTypeDto.HTTPS, response.getBody().getConnectionType());
        assertFalse(response.getBody().getOwner());
        assertLocationHeader("/api/clients/FI:GOV:M2", response);

        response = clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd
                                .connectionType(ConnectionTypeDto.HTTPS_NO_AUTH)
                                .subsystemCode("SUBSYSTEM1"))
                        .ignoreWarnings(false));
        assertEquals("SUBSYSTEM1", response.getBody().getSubsystemCode());
        assertEquals(ClientStatusDto.SAVED, response.getBody().getStatus());
        assertEquals(ConnectionTypeDto.HTTPS_NO_AUTH, response.getBody().getConnectionType());
        assertLocationHeader("/api/clients/FI:GOV:M2:SUBSYSTEM1", response);
    }

    @Test
    @WithMockUser(authorities = {"ADD_CLIENT"})
    public void addClientConflicts() {
        // conflict: client already exists
        ClientDto clientToAdd = createTestClient("GOV", "M1", null);
        try {
            clientsApiController.addClient(
                    new ClientAddDto().client(clientToAdd).ignoreWarnings(false));
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }

        // conflict: two additional members
        var clientToAdd2 = createTestClient("GOV", "ADDITIONAL1", null);
        ResponseEntity<ClientDto> response = clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd2).ignoreWarnings(true));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        assertThrows(ConflictException.class, () -> clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd2.memberCode("ADDITIONAL2")).ignoreWarnings(true)));
    }

    @Test
    @WithMockUser(authorities = {"ADD_CLIENT"})
    public void addClientBadRequestFromWarnings() {
        // warning about unregistered client
        doReturn(null).when(globalConfProvider).getMemberName(any());
        ClientDto clientToAdd = createTestClient(TestUtils.MEMBER_CLASS_GOV, "B", "C");

        var expected = assertThrows(BadRequestException.class, () -> clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd).ignoreWarnings(false)));
        assertEquals(DeviationCodes.ERROR_WARNINGS_DETECTED, expected.getErrorDeviation().code());

        ResponseEntity<ClientDto> response = clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd).ignoreWarnings(true));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = {"ADD_CLIENT"})
    public void addClientBadRequestFromInvalidMemberClass() {
        // warning about unregistered client
        doReturn(null).when(globalConfProvider).getMemberName(any());
        ClientDto clientToAdd = createTestClient("INVALID", "B", "C");
        var expected = assertThrows(BadRequestException.class, () -> clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd).ignoreWarnings(false)));
        assertEquals(DeviationCodes.ERROR_INVALID_MEMBER_CLASS, expected.getErrorDeviation().code());
    }

    @Test
    @WithMockUser(authorities = {"ADD_WSDL", "VIEW_CLIENT_SERVICES"})
    public void addWsdlServiceDescription() {
        ServiceDescriptionAddDto serviceDescription = new ServiceDescriptionAddDto()
                .url("file:src/test/resources/wsdl/valid.wsdl");
        serviceDescription.setType(ServiceTypeDto.WSDL);
        serviceDescription.setIgnoreWarnings(false);

        ResponseEntity<ServiceDescriptionDto> response = clientsApiController.addClientServiceDescription(
                TestUtils.CLIENT_ID_SS1, serviceDescription);
        ServiceDescriptionDto addedServiceDescription = response.getBody();
        assertNotNull(addedServiceDescription.getId());
        assertEquals(serviceDescription.getUrl(), addedServiceDescription.getUrl());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertLocationHeader("/api/service-descriptions/" + addedServiceDescription.getId(), response);

        ResponseEntity<Set<ServiceDescriptionDto>> descriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1);
        assertEquals(CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT + 1, descriptions.getBody().size());

        serviceDescription.setIgnoreWarnings(true);
        var expected = assertThrows(ConflictException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertEquals(DeviationCodes.ERROR_WSDL_EXISTS, expected.getErrorDeviation().code());

        var serviceDescription2 = new ServiceDescriptionAddDto().url("file:src/test/resources/wsdl/testservice.wsdl");
        serviceDescription2.setType(ServiceTypeDto.WSDL);

        serviceDescription2.setIgnoreWarnings(false);
        expected = assertThrows(ConflictException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription2));

        assertErrorWithMetadata(DeviationCodes.ERROR_SERVICE_EXISTS, expected,
                "xroadGetRandom.v1", "file:src/test/resources/wsdl/valid.wsdl");
    }

    @Test
    @WithMockUser(authorities = {"ADD_WSDL"})
    public void addWsdlServiceDescriptionParserFail() {
        ServiceDescriptionAddDto serviceDescription =
                new ServiceDescriptionAddDto().url("file:src/test/resources/wsdl/invalid.wsdl");
        serviceDescription.setType(ServiceTypeDto.WSDL);

        serviceDescription.setIgnoreWarnings(true);
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertEquals(DeviationCodes.ERROR_INVALID_WSDL, expected.getErrorDeviation().code());
    }

    @Test
    @WithMockUser(authorities = {"ADD_WSDL"})
    public void addWsdlServiceDescriptionBadServiceUrl() {
        ServiceDescriptionAddDto serviceDescription =
                new ServiceDescriptionAddDto().url("file:src/test/resources/wsdl/invalid-serviceurl.wsdl");
        serviceDescription.setType(ServiceTypeDto.WSDL);

        serviceDescription.setIgnoreWarnings(true);
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertEquals(DeviationCodes.ERROR_INVALID_SERVICE_URL, expected.getErrorDeviation().code());
        assertFalse(expected.getErrorDeviation().metadata().isEmpty());
    }

    @Test
    @WithMockUser(authorities = {"ADD_WSDL", "VIEW_CLIENT_SERVICES"})
    public void addWsdlServiceDescriptionWithWarnings() {
        ServiceDescriptionAddDto serviceDescription =
                new ServiceDescriptionAddDto().url("file:src/test/resources/wsdl/warning.wsdl");
        serviceDescription.setType(ServiceTypeDto.WSDL);

        serviceDescription.setIgnoreWarnings(false);
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertErrorWithoutMetadata(DeviationCodes.ERROR_WARNINGS_DETECTED,
                expected);
        assertWarning(DeviationCodes.WARNING_WSDL_VALIDATION_WARNINGS,
                WsdlValidatorTest.MOCK_VALIDATOR_WARNING,
                expected);

        // now lets ignore the warningDeviations
        serviceDescription.setIgnoreWarnings(true);
        clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription);
        ResponseEntity<Set<ServiceDescriptionDto>> descriptions =
                clientsApiController.getClientServiceDescriptions(TestUtils.CLIENT_ID_SS1);
        assertEquals(CLIENT_ID_SS1_INITIAL_SERVICEDESCRIPTION_COUNT + 1, descriptions.getBody().size());
    }

    @Test
    @WithMockUser(authorities = {"ADD_WSDL"})
    public void addWsdlServiceDescriptionValidationFail() {
        ServiceDescriptionAddDto serviceDescription =
                new ServiceDescriptionAddDto().url("file:src/test/resources/wsdl/error.wsdl");
        serviceDescription.setType(ServiceTypeDto.WSDL);

        serviceDescription.setIgnoreWarnings(false);
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertEquals(DeviationCodes.ERROR_INVALID_WSDL, expected.getErrorDeviation().code());

        // cannot ignore these fatal errors
        serviceDescription.setIgnoreWarnings(true);
        expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertEquals(DeviationCodes.ERROR_INVALID_WSDL, expected.getErrorDeviation().code());

    }

    @Test
    @WithMockUser(authorities = {"ADD_WSDL"})
    public void addWsdlServiceDescriptionSkipValidation() {
        ServiceDescriptionAddDto serviceDescription =
                new ServiceDescriptionAddDto().url("file:src/test/resources/wsdl/error.wsdl");
        serviceDescription.setType(ServiceTypeDto.WSDL);
        serviceDescription.setIgnoreWarnings(true);
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.addClientServiceDescription(TestUtils.CLIENT_ID_SS1, serviceDescription));
        assertEquals(DeviationCodes.ERROR_INVALID_WSDL, expected.getErrorDeviation().code());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findAllServiceClientCandidates() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null,
                null, null, null, null, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(10, serviceClients.size());
        // Test sorting order
        assertEquals(true, TestUtils.isSortOrderCorrect(serviceClients, serviceClientSortingComparator));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByName() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM2, null, null, null, null, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByGroupDescription() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.GLOBALGROUP, null, null, null, null, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());

        serviceClientResponse = clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS1,
                "foo", null, null, null, null, null);
        serviceClients = serviceClientResponse.getBody();
        assertEquals(2, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByType() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, ServiceClientTypeDto.LOCALGROUP, null, null, null, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByInstance() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, TestUtils.INSTANCE_EE, null, null, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(6, serviceClients.size()); // includes localgroups

        ResponseEntity<Set<ServiceClientDto>> partialInstanceMatchResponse =
                clientsApiController.findServiceClientCandidates(
                        TestUtils.CLIENT_ID_SS1,
                        null, ServiceClientTypeDto.SUBSYSTEM, "E", null, null, null);
        Set<ServiceClientDto> partialInstanceMatch = partialInstanceMatchResponse.getBody();
        assertEquals(0, partialInstanceMatch.size());
    }


    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByMemberClass() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, null, TestUtils.MEMBER_CLASS_GOV, null, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(3, serviceClients.size());

        ResponseEntity<Set<ServiceClientDto>> partialMemberClassMatchResponse =
                clientsApiController.findServiceClientCandidates(
                        TestUtils.CLIENT_ID_SS1,
                        null, ServiceClientTypeDto.SUBSYSTEM, null, "GO", null, null);
        Set<ServiceClientDto> partialMemberClassMatch = partialMemberClassMatchResponse.getBody();
        assertEquals(0, partialMemberClassMatch.size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByMemberOrGroupCode() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, null, null, TestUtils.MEMBER_CODE_M1, null);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
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
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesBySubsystemCode() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                null, null, null, null, null, TestUtils.SUBSYSTEM2);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesByAllSearchTerms() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM3, ServiceClientTypeDto.SUBSYSTEM,
                TestUtils.INSTANCE_EE,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                TestUtils.SUBSYSTEM3);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(1, serviceClients.size());
    }

    @Test(expected = NotFoundException.class)
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesClientNotFound() {
        clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS4, null, null, null, null, null, null);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void findServiceClientCandidatesNoResults() {
        ResponseEntity<Set<ServiceClientDto>> serviceClientResponse = clientsApiController.findServiceClientCandidates(
                TestUtils.CLIENT_ID_SS1,
                TestUtils.NAME_FOR + TestUtils.SUBSYSTEM3, ServiceClientTypeDto.LOCALGROUP,
                TestUtils.INSTANCE_EE,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                TestUtils.SUBSYSTEM3);
        Set<ServiceClientDto> serviceClients = serviceClientResponse.getBody();
        assertEquals(0, serviceClients.size());

        serviceClientResponse = clientsApiController.findServiceClientCandidates(TestUtils.CLIENT_ID_SS1,
                "nothing", null, null, null, "unknown-code", null);
        serviceClients = serviceClientResponse.getBody();
        assertEquals(0, serviceClients.size());
    }

    @Test
    @WithMockUser(authorities = {"DELETE_CLIENT", "ADD_CLIENT", "VIEW_CLIENT_DETAILS"})
    public void deleteClient() {
        assertThrows(ConflictException.class, () -> clientsApiController.deleteClient("FI:GOV:M1"));
        assertThrows(NotFoundException.class, () -> clientsApiController.deleteClient("FI:GOV:NOT-EXISTING"));
        // create a new client, and then delete it
        ClientDto clientToAdd = createTestClient("GOV", "M3", null);
        ResponseEntity<ClientDto> addResponse = clientsApiController.addClient(
                new ClientAddDto().client(clientToAdd).ignoreWarnings(false));
        assertEquals(HttpStatus.CREATED, addResponse.getStatusCode());

        ResponseEntity<Void> deleteResponse =
                clientsApiController.deleteClient("FI:GOV:M3");
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        assertThrows(NotFoundException.class, () -> clientsApiController.getClient("FI:GOV:M3"));

    }

    @Test
    @WithMockUser(authorities = {"DELETE_CLIENT"})
    public void getOrphans() {
        ClientId.Conf orphanClient = TestUtils.getClientId("FI:GOV:ORPHAN:SS1");
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(new CertRequestInfoBuilder()
                        .clientId(orphanClient)
                        .build())
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .key(keyInfo)
                .build();
        doReturn(Collections.singletonList(tokenInfo)).when(tokenService).getAllTokens();
        ResponseEntity<OrphanInformationDto> orphanResponse = clientsApiController
                .getClientOrphans("FI:GOV:ORPHAN:SS1");
        assertEquals(HttpStatus.OK, orphanResponse.getStatusCode());
        assertEquals(true, orphanResponse.getBody().getOrphansExist());

        assertThrows(NotFoundException.class, () -> clientsApiController.getClientOrphans("FI:GOV:M1:SS777"));
    }

    @Test
    @WithMockUser(authorities = {"DELETE_CLIENT", "DELETE_SIGN_KEY"})
    public void deleteOrphans() throws Exception {
        ClientId.Conf orphanClient = TestUtils.getClientId("FI:GOV:ORPHAN:SS1");
        String orphanKeyId = "orphan-key";
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .id(orphanKeyId)
                .csr(new CertRequestInfoBuilder()
                        .clientId(orphanClient)
                        .build())
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .key(keyInfo)
                .build();
        doReturn(Collections.singletonList(tokenInfo)).when(tokenService).getAllTokens();
        doReturn(tokenInfo).when(tokenService).getTokenForKeyId(any());
        ResponseEntity<Void> orphanResponse = clientsApiController
                .deleteOrphans("FI:GOV:ORPHAN:SS1");
        assertEquals(HttpStatus.NO_CONTENT, orphanResponse.getStatusCode());

        verify(signerRpcClient).deleteKey(orphanKeyId, true);
        verify(signerRpcClient).deleteKey(orphanKeyId, false);
        verifyNoMoreInteractions(signerRpcClient);

        assertThrows(NotFoundException.class, () -> clientsApiController.deleteOrphans("FI:GOV:M1:SS777"));
    }

    @Test
    @WithMockUser(authorities = {"SEND_CLIENT_REG_REQ"})
    public void registerClient() {
        ResponseEntity<Void> response = clientsApiController.registerClient(TestUtils.CLIENT_ID_M2_SS6);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = {"SEND_CLIENT_REG_REQ"})
    public void registerOwner() {
        clientsApiController.registerClient(TestUtils.OWNER_ID);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"SEND_CLIENT_REG_REQ"})
    public void registerClientWrongStatus() {
        clientsApiController.registerClient(TestUtils.CLIENT_ID_SS1);
    }

    @WithMockUser(authorities = {"SEND_CLIENT_REG_REQ"})
    public void registerClientWithInvalidInstanceIdentifier() throws Exception {
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.registerClient(TestUtils.CLIENT_ID_INVALID_INSTANCE_IDENTIFIER));
        assertEquals(DeviationCodes.ERROR_INVALID_INSTANCE_IDENTIFIER,
                expected.getErrorDeviation().code());
    }

    @WithMockUser(authorities = {"SEND_CLIENT_REG_REQ"})
    public void registerClientWithInvalidMemberClass() throws Exception {
        var expected = assertThrows(BadRequestException.class,
                () -> clientsApiController.registerClient(TestUtils.CLIENT_ID_INVALID_MEMBER_CLASS));
        assertEquals(DeviationCodes.ERROR_INVALID_MEMBER_CLASS, expected.getErrorDeviation().code());
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"SEND_CLIENT_DEL_REQ"})
    public void unregisterOwner() {
        clientsApiController.unregisterClient(TestUtils.OWNER_ID);
    }

    @Test
    @WithMockUser(authorities = {"SEND_CLIENT_DEL_REQ"})
    public void unregisterClient() {
        ResponseEntity<Void> response = clientsApiController.unregisterClient(TestUtils.CLIENT_ID_SS1);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"SEND_CLIENT_DEL_REQ"})
    public void unregisterClientWrongStatus() {
        clientsApiController.unregisterClient(TestUtils.CLIENT_ID_M2_SS6);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"SEND_OWNER_CHANGE_REQ", "ADD_CLIENT"})
    public void changeOwnerNotRegistered() {
        clientsApiController.addClient(new ClientAddDto().client(createTestClient(
                "GOV", "M2", null)).ignoreWarnings(true));

        ResponseEntity<Void> response = clientsApiController.changeOwner("FI:GOV:M2");
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = {"SEND_OWNER_CHANGE_REQ"})
    public void changeOwnerCurrentOwner() {
        ResponseEntity<Void> response = clientsApiController.changeOwner("FI:GOV:M1");
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"SEND_OWNER_CHANGE_REQ"})
    public void changeOwnerSubsystem() {
        ResponseEntity<Void> response = clientsApiController.changeOwner("FI:GOV:M1:SS1");
    }

    @Test(expected = NotFoundException.class)
    @WithMockUser(authorities = {"SEND_OWNER_CHANGE_REQ"})
    public void changeOwnerClientDoesNotExist() {
        ClientDto client = new ClientDto();
        client.setInstanceId("non");
        client.setMemberClass("existing");
        client.setMemberCode("client");
        ResponseEntity<Void> response = clientsApiController.changeOwner("FI:non:existing");
    }

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void getServiceClientNotExist() {
        clientsApiController.getServiceClient(TestUtils.CLIENT_ID_SS1, "NoSuchServiceClient");
    }

    @Test(expected = NotFoundException.class)
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void getServiceClientWithClientNotContainingGivenServiceClient() {
        clientsApiController.getServiceClient(TestUtils.CLIENT_ID_SS5, TestUtils.CLIENT_ID_SS1);
    }

    @Test
    @WithMockUser(authorities = {"VIEW_CLIENT_ACL_SUBJECTS"})
    public void getServiceClient() {
        String clientId = TestUtils.CLIENT_ID_SS1;
        String serviceClientId = TestUtils.CLIENT_ID_SS2;
        String localGroupId = TestUtils.DB_LOCAL_GROUP_ID_1;

        // Get subsystem service client
        ServiceClientDto subSystemserviceClient =
                clientsApiController.getServiceClient(clientId, serviceClientId).getBody();
        assertEquals(ServiceClientTypeDto.SUBSYSTEM, subSystemserviceClient.getServiceClientType());
        assertTrue("FI:GOV:M1:SS2".equals(subSystemserviceClient.getId()));

        // Get localgroup service client
        ServiceClientDto localGroupServiceClient = clientsApiController.getServiceClient(clientId, localGroupId).getBody();
        assertTrue("group1".equals(localGroupServiceClient.getLocalGroupCode()));
        assertTrue("1".equals(localGroupServiceClient.getId()));
    }

    @Test
    @WithMockUser(authorities = {"VIEW_ACL_SUBJECT_OPEN_SERVICES"})
    public void getServiceClientAccessRightsTest() {
        String clientId = TestUtils.CLIENT_ID_SS1;
        String serviceClientId = TestUtils.CLIENT_ID_SS2;
        String localGroupId = TestUtils.DB_LOCAL_GROUP_ID_1;

        // Test subsystem service client
        Set<AccessRightDto> accessRights = clientsApiController
                .getServiceClientAccessRights(clientId, serviceClientId).getBody();
        assertTrue(accessRights.size() == 2);
        assertTrue(accessRights.stream().anyMatch(acl -> "getRandom".equals(acl.getServiceCode())));
        assertTrue(accessRights.stream().anyMatch(acl -> "rest-servicecode".equals(acl.getServiceCode())));

        // Test localgroup service client
        Set<AccessRightDto> groupAcls = clientsApiController
                .getServiceClientAccessRights(clientId, localGroupId).getBody();
        assertTrue(groupAcls.size() == 1);
        assertTrue(groupAcls.stream().anyMatch(acl -> "getRandom".equals(acl.getServiceCode())));
    }

    @Test
    @WithMockUser(authorities = {"EDIT_ACL_SUBJECT_OPEN_SERVICES"})
    public void addServiceClientAccessRights() {

        String encodedOwnerId = "FI:GOV:M1:SS1";
        String encodedSubsystemId = "EE:GOV:M2:SS3";
        String encodedLocalGroupId = "2"; // pk
        String encodedGlobalGroupId = TestUtils.INSTANCE_FI + ":" + TestUtils.GLOBALGROUP;
        AccessRightsDto accessRights = new AccessRightsDto();
        accessRights.addItemsItem(new AccessRightDto().serviceCode("calculatePrime"));
        accessRights.addItemsItem(new AccessRightDto().serviceCode("openapi-servicecode"));
        accessRights.addItemsItem(new AccessRightDto().serviceCode("rest-servicecode"));

        ResponseEntity<Set<AccessRightDto>> response = clientsApiController
                .addServiceClientAccessRights(encodedOwnerId, encodedSubsystemId, accessRights);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(3, response.getBody().size());
        AccessRightDto accessRight = findAccessRight("calculatePrime", response.getBody());
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

        // try to add duplicate
        assertThrows(ConflictException.class,
                () -> clientsApiController.addServiceClientAccessRights(encodedOwnerId, encodedGlobalGroupId, accessRights));
    }

    private AccessRightDto findAccessRight(String serviceCode, Set<AccessRightDto> accessRights) {
        return accessRights.stream()
                .filter(dto -> dto.getServiceCode().equals(serviceCode))
                .findFirst()
                .orElse(null);
    }
}
