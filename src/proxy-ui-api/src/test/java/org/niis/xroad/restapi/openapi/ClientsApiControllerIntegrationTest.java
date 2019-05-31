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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.converter.GlobalConfWrapper;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.CertificateStatus;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ClientStatus;
import org.niis.xroad.restapi.openapi.model.ConnectionType;
import org.niis.xroad.restapi.openapi.model.Group;
import org.niis.xroad.restapi.openapi.model.InlineObject;
import org.niis.xroad.restapi.openapi.model.InlineObject1;
import org.niis.xroad.restapi.repository.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test ClientsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ClientsApiControllerIntegrationTest {
    public static final String CLIENT_ID_SS1 = "FI:GOV:M1:SS1";
    public static final String CLIENT_ID_SS2 = "FI:GOV:M1:SS2";
    public static final String GROUPCODE = "group1";
    public static final String GROUPCODE2 = "group2";
    public static final String NEW_GROUPCODE = "groupx";
    public static final String GROUP_DESC = "GROUP_DESC";

    // this is base64 encoded DER certificate from common-util/test/configuration-anchor.xml
    /**
     * Certificate:
     *     Data:
     *         Version: 3 (0x2)
     *         Serial Number: 1 (0x1)
     *     Signature Algorithm: sha512WithRSAEncryption
     *         Issuer: CN=N/A
     *         Validity
     *             Not Before: Jan  1 00:00:00 1970 GMT
     *             Not After : Jan  1 00:00:00 2038 GMT
     *         Subject: CN=N/A
     */
    private static byte[] certBytes =
            CryptoUtils.decodeBase64("MIICqTCCAZGgAwIBAgIBATANBgkqhkiG9w0BAQ0FADAOMQwwCgYDVQQDDANOL0EwHhcNN\n"
            + "zAwMTAxMDAwMDAwWhcNMzgwMTAxMDAwMDAwWjAOMQwwCgYDVQQDDANOL0EwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK\n"
            + "AoIBAQCdiI++CJsyo19Y0810Q80lOJmJ264CvGGqQuB9VYha4YFsHUhltAp3LIcEpxNPuh8k7Mn+pFoetIXtBh6p5cYGf3n\n"
            + "S0i07xSLaAAkQdGqzI6aiSNiGDhQGL5NdyM/cdthtdheQq3WquN7kNkmXo1c5RM2ZcK4SRy6Q44d+KdzC5O42mUgDdxyY2+\n"
            + "3xpSqcAJq1/2DuDPVzAIkWH/iU2+dgnaPACcNqCgnL8g0ALu2e9vHm/ZYhYpS3+e2xLXEOwRvxlprsGcE1aIjKeFupwoZ4n\n"
            + "nkqmHOA2AYS4wVVpcrmF0lDmemXAfi0gDqWCkyjqo9aWdo952uHVQpJarMBGothAgMBAAGjEjAQMA4GA1UdDwEB/wQEAwIG\n"
            + "QDANBgkqhkiG9w0BAQ0FAAOCAQEAMUt6UKCam3QyJnGeEMDJ0m8WbjSzD5NyUVbpR2EVrO+Kqbu8Kd/vjF8vdQN+TCNabqT\n"
            + "ynnrrmqkc4xBBIXHMJ+xS6SijHQ5+IJ6D/VSx+C3D6XrJbzCby4t+ESqGsqB6ShxiiKOSQ5A6MDaE4Doi00GMB5NymknQrn\n"
            + "wREOMPwTZy68CZEaEQyE4M9KezCeVJMCXmnJt1I9oudsw3xPDjq+aYzRORW74RvNFf+sztBjPGhkqFnkl+glbEK6otefyJP\n"
            + "n5vVwjz/+ywyqzx8YJM0vPkD/PghmJxunsJObbvif9FNZaxOaEzI9QDw0nWzbgvsCAqdcHqRjMEQwtU75fzfg==");

    @MockBean
    private GlobalConfWrapper globalConfWrapper;

    @MockBean
    private TokenRepository tokenRepository;

    @Before
    public void setup() throws Exception {
        when(globalConfWrapper.getMemberName(any())).thenReturn("test-member-name");

        List<TokenInfo> mockTokens = createMockTokenInfos(null);
        when(tokenRepository.getTokens()).thenReturn(mockTokens);
    }

    @Autowired
    private ClientsApiController clientsApiController;

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getClients() {
        ResponseEntity<List<Client>> response =
                clientsApiController.getClients();
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
        assertEquals("test-member-name", client.getMemberName());
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

        response = clientsApiController.updateClient("FI:GOV:M1:SS1", ConnectionType.HTTP);
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
                "id", certBytes, null);
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
        assertEquals(CertificateStatus.IN_USE, onlyCertificate.getStatus());
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
            ResponseEntity<List<Client>> response = clientsApiController.getClients(null, null, null, null, null, null,
                    null);
            fail("should throw AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
    }


    /**
     * @param certificateInfo one certificate to put inside this tokenInfo
     *                        structure
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


    // base64 example certs
    private static final String VALID_CERT =
            "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUIwekNDQVgyZ0F3SUJBZ0lKQU0ra0lkTDRqSTYx"
                    + "TUEwR0NTcUdTSWIzRFFFQkN3VUFNRVV4Q3pBSkJnTlYKQkFZVEFrRlZNUk13RVFZRFZRUUlE"
                    + "QXBUYjIxbExWTjBZWFJsTVNFd0h3WURWUVFLREJoSmJuUmxjbTVsZENCWAphV1JuYVhSeklG"
                    + "QjBlU0JNZEdRd0hoY05NVGt3TkRJME1EWTFPVEF5V2hjTk1qQXdOREl6TURZMU9UQXlXakJG"
                    + "Ck1Rc3dDUVlEVlFRR0V3SkJWVEVUTUJFR0ExVUVDQXdLVTI5dFpTMVRkR0YwWlRFaE1COEdB"
                    + "MVVFQ2d3WVNXNTAKWlhKdVpYUWdWMmxrWjJsMGN5QlFkSGtnVEhSa01Gd3dEUVlKS29aSWh2"
                    + "Y05BUUVCQlFBRFN3QXdTQUpCQU1uRAp5bkQ1dHp5K0YyNUZKbDVOUFJaMlRrclBJV2lmdmR3"
                    + "aVJCYXFudjNYSlNsWllNeHVTbERlblBNYmIwdHhXMUM4CjBxeDVnVVlDRk5xcU5qV0hWSlVD"
                    + "QXdFQUFhTlFNRTR3SFFZRFZSME9CQllFRkxMQ3hCbExXekFIZVE5U1o3b3gKbFYvUE9JUHZN"
                    + "QjhHQTFVZEl3UVlNQmFBRkxMQ3hCbExXekFIZVE5U1o3b3hsVi9QT0lQdk1Bd0dBMVVkRXdR"
                    + "RgpNQU1CQWY4d0RRWUpLb1pJaHZjTkFRRUxCUUFEUVFBY2xuR2JkdGJhVXNOTmEvWHRHYlhD"
                    + "WFpjZERRaWo2SGx3Cmp1ZGRqKzdmR2psSnZMMWF5OUlaYjIxblRJOHpOQXhsb25Ld2YrT1g0"
                    + "ODRQM2ZBVHFCMGIKLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=";
    private static final String INVALID_CERT =
            "dG90YWwgMzYKZHJ3eHJ3eHIteCAzIGphbm5lIGphbm5lIDQwOTYgaHVodGkgMjQgMTY6MjEgLgpkcnd4cn"
                    + "d4ci14IDkgamFubmUgamFubmUgNDA5NiBodWh0aSAyNCAxMToxNSAuLgotcnctcnctci0tIDEg"
                    + "amFubmUgamFubmUgMzEwNSBodWh0aSAyNCAxNjowOSBkZWNvZGVkCi1ydy1ydy1yLS0gMSBqYW"
                    + "5uZSBqYW5uZSAyMjUyIGh1aHRpIDIzIDE0OjEyIGdvb2dsZS1jZXJ0LmRlcgotcnctcnctci0t"
                    + "IDEgamFubmUgamFubmUgMzAwNCBodWh0aSAyNCAxNjowOSBnb29nbGUtY2VydC5kZXIuYmFzZT"
                    + "Y0Ci1ydy1ydy1yLS0gMSBqYW5uZSBqYW5uZSAzMTA1IGh1aHRpIDIzIDE0OjA5IGdvb2dsZS1j"
                    + "ZXJ0LnBlbQotcnctcnctci0tIDEgamFubmUgamFubmUgNDE0MCBodWh0aSAyNCAxNjowOSBnb2"
                    + "9nbGUtY2VydC5wZW0uYmFzZTY0Ci1ydy1ydy1yLS0gMSBqYW5uZSBqYW5uZSAgICAwIGh1aHRp"
                    + "IDI0IDE2OjIxIG5vbi1jZXJ0CmRyd3hyd3hyLXggMiBqYW5uZSBqYW5uZSA0MDk2IGh1aHRpID"
                    + "I0IDE2OjIxIHRpbnkK";

    /**
     * Return a Resource for reading a cert, given as base64 encoded string param
     */
    private static Resource getResourceToCert(String cert) {
        byte[] bytes = Base64.getDecoder().decode(cert);
        return new ByteArrayResource(bytes);
    }

    @Test
    @WithMockUser(authorities = { "ADD_CLIENT_INTERNAL_CERT",
            "VIEW_CLIENT_DETAILS",
            "VIEW_CLIENT_INTERNAL_CERTS" })
    public void addTlsCert() throws Exception {

        ResponseEntity<List<CertificateDetails>> certs = clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1);
        assertEquals(0, certs.getBody().size());

        ResponseEntity<Void> response =
                clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                        getResourceToCert(VALID_CERT));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());

        // cert already exists
        try {
            response = clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                    getResourceToCert(VALID_CERT));
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());

        // cert is invalid
        try {
            response = clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                    getResourceToCert(INVALID_CERT));
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

        ResponseEntity<Void> response =
                clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                        getResourceToCert(VALID_CERT));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());

        ResponseEntity<Void> deleteResponse =
                clientsApiController.deleteClientTlsCertificate(CLIENT_ID_SS1,
                        "63A104B2BAC14667873C5DBD54BE25BC687B3702");
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
        assertEquals(0, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());

        // cert does not exist
        try {
            clientsApiController.deleteClientTlsCertificate(CLIENT_ID_SS1,
                    "63A104B2BAC14667873C5DBD54BE25BC687B3702");
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

        ResponseEntity<Void> response =
                clientsApiController.addClientTlsCertificate(CLIENT_ID_SS1,
                        getResourceToCert(VALID_CERT));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, clientsApiController.getClientTlsCertificates(CLIENT_ID_SS1).getBody().size());

        ResponseEntity<CertificateDetails> findResponse =
                clientsApiController.getClientTlsCertificate(CLIENT_ID_SS1,
                        "63A104B2BAC14667873C5DBD54BE25BC687B3702");
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals("63A104B2BAC14667873C5DBD54BE25BC687B3702", findResponse.getBody().getHash());

        // case insensitive
        findResponse =
                clientsApiController.getClientTlsCertificate(CLIENT_ID_SS1,
                        "63a104b2bac14667873c5dbd54be25bc687b3702");
        assertEquals(HttpStatus.OK, findResponse.getStatusCode());
        assertEquals("63A104B2BAC14667873C5DBD54BE25BC687B3702", findResponse.getBody().getHash());

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
        ResponseEntity<Void> response = clientsApiController.addClientGroup(CLIENT_ID_SS1, createGroup(NEW_GROUPCODE));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "ADD_LOCAL_GROUP" })
    public void getLocalGroup() throws Exception {
        ResponseEntity<Group> response =
                clientsApiController.getGroup(CLIENT_ID_SS1, GROUPCODE2);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "ADD_LOCAL_GROUP" })
    public void getClientGroups() throws Exception {
        ResponseEntity<List<Group>> response =
                clientsApiController.getClientGroups(CLIENT_ID_SS1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_DESC" })
    public void updateGroup() throws Exception {
        clientsApiController.updateGroup(CLIENT_ID_SS1, GROUPCODE, GROUP_DESC);
        ResponseEntity<Group> response =
                clientsApiController.getGroup(CLIENT_ID_SS1, GROUPCODE);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GROUP_DESC, response.getBody().getDescription());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_LOCAL_GROUP", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void deleteLocalGroup() throws Exception {
        ResponseEntity<Void> response =
                clientsApiController.deleteGroup(CLIENT_ID_SS1, GROUPCODE);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        ResponseEntity<List<Group>> responseGroups =
                clientsApiController.getClientGroups(CLIENT_ID_SS1);
        assertEquals(1, responseGroups.getBody().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_MEMBERS" })
    public void addGroupMember() throws Exception {
        ResponseEntity<Void> response =
                clientsApiController.addGroupMember(CLIENT_ID_SS1, GROUPCODE, new InlineObject().id(CLIENT_ID_SS2));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Group> localGroupResponse =
                clientsApiController.getGroup(CLIENT_ID_SS1, GROUPCODE);
        assertEquals(1, localGroupResponse.getBody().getMembers().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_MEMBERS" })
    public void deleteGroupMember() throws Exception {
        ResponseEntity<Void> response =
                clientsApiController.addGroupMember(CLIENT_ID_SS1, GROUPCODE, new InlineObject().id(CLIENT_ID_SS2));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Void> deleteResponse =
                clientsApiController.deleteGroupMember(CLIENT_ID_SS1, GROUPCODE,
                        new InlineObject1().items(Collections.singletonList(CLIENT_ID_SS2)));
        assertEquals(HttpStatus.CREATED, deleteResponse.getStatusCode());
        ResponseEntity<Group> localGroupResponse =
                clientsApiController.getGroup(CLIENT_ID_SS1, GROUPCODE);
        assertEquals(0, localGroupResponse.getBody().getMembers().size());
    }

    private static Group createGroup(String groupCode) {
        Group group = new Group();
        group.setDescription(GROUP_DESC);
        group.setCode(groupCode);
        return group;
    }
}
