/**
 * The MIT License
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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ClientAdd;
import org.niis.xroad.restapi.openapi.model.ClientStatus;
import org.niis.xroad.restapi.openapi.model.InitialServerConf;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionAdd;
import org.niis.xroad.restapi.openapi.model.ServiceDescriptionUpdate;
import org.niis.xroad.restapi.openapi.model.ServiceType;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.util.TestUtils.addApiKeyAuthorizationHeader;

/**
 * test validation of identifier parameters with real requests
 * (can't test binders with regular integration tests, for some reason)
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
public class IdentifierValidationRestTemplateTest {

    public static final String HAS_COLON = "aa:bb";
    public static final String HAS_SEMICOLON = "aa;bb";
    public static final String HAS_PERCENT = "aa%bb";
    public static final String HAS_NON_NORMALIZED = "aa/../bb";
    public static final String HAS_BACKSLASH = "aa\\bb";
    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private CurrentSecurityServerSignCertificates currentSecurityServerSignCertificates;

    @Before
    public void setup() {
        addApiKeyAuthorizationHeader(restTemplate);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });

        when(currentSecurityServerSignCertificates.getSignCertificateInfos()).thenReturn(new ArrayList<>());
    }

    @Test
    public void testAddClient() {
        assertAddClientValidationError(HAS_COLON, null);
        assertAddClientValidationError(HAS_SEMICOLON, null);
        assertAddClientValidationError(HAS_PERCENT, null);
        assertAddClientValidationError(HAS_NON_NORMALIZED, null);
        assertAddClientValidationError(HAS_BACKSLASH, null);
        assertAddClientValidationError("aa", HAS_COLON);
        assertAddClientValidationError("aa", HAS_SEMICOLON);
        assertAddClientValidationError("aa", HAS_PERCENT);
        assertAddClientValidationError("aa", HAS_NON_NORMALIZED);
        assertAddClientValidationError("aa", HAS_BACKSLASH);

        // these ids should be fine by validation rules
        ResponseEntity<Object> response = createTestClient("aa.bb.列.ä", "aa.bb.列.ä");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private void assertAddClientValidationError(String memberCode, String subsystemCode) {
        ResponseEntity<Object> response = createTestClient(memberCode, subsystemCode);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private ResponseEntity<Object> createTestClient(String memberCode, String subsystemCode) {
        Client client = new Client()
                .memberClass("GOV")
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .status(ClientStatus.SAVED);
        ClientAdd clientAdd = new ClientAdd().client(client);
        return restTemplate.postForEntity("/api/clients", clientAdd, Object.class);
    }

    @Test
    public void testAddClientServiceDescription() {
        assertAddClientServiceDescriptionValidationError(HAS_COLON);
        assertAddClientServiceDescriptionValidationError(HAS_SEMICOLON);
        assertAddClientServiceDescriptionValidationError(HAS_PERCENT);
        assertAddClientServiceDescriptionValidationError(HAS_NON_NORMALIZED);
        assertAddClientServiceDescriptionValidationError(HAS_BACKSLASH);

        ResponseEntity<Object> response = createClientServiceDescription("aa.bb.列.ä");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    private ResponseEntity<Object> createClientServiceDescription(String restServiceCode) {
        ServiceDescriptionAdd serviceDescriptionAdd = new ServiceDescriptionAdd()
                .url("http://www.google.com")
                .restServiceCode(restServiceCode)
                .type(ServiceType.REST);
        return restTemplate.postForEntity("/api/clients/FI:GOV:M1:SS1/service-descriptions",
                serviceDescriptionAdd, Object.class);
    }

    private void assertAddClientServiceDescriptionValidationError(String restServiceCode) {
        ResponseEntity<Object> response = createClientServiceDescription(restServiceCode);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    public void testUpdateServiceDescription() {
        assertUpdateServiceDescriptionValidationFailure(HAS_COLON);
        assertUpdateServiceDescriptionValidationFailure(HAS_SEMICOLON);
        assertUpdateServiceDescriptionValidationFailure(HAS_PERCENT);
        assertUpdateServiceDescriptionValidationFailure(HAS_NON_NORMALIZED);
        assertUpdateServiceDescriptionValidationFailure(HAS_BACKSLASH);
    }

    /**
     * @return response body as a Map
     */
    private Object updateServiceDescription(String restServiceCode) {
        ServiceDescriptionUpdate serviceDescriptionUpdate = new ServiceDescriptionUpdate()
                .url("http://www.google.com")
                .restServiceCode("asdf")
                .newRestServiceCode(restServiceCode)
                .type(ServiceType.REST);

        return restTemplate.patchForObject("/api/service-descriptions/1", serviceDescriptionUpdate, Object.class);
    }

    private void assertUpdateServiceDescriptionValidationFailure(String restServiceCode) {
        Map<String, Object> response = (Map<String, Object>) updateServiceDescription(restServiceCode);
        assertEquals(new Integer(HttpStatus.BAD_REQUEST.value()), response.get("status"));
        Map<String, Object> errors = (Map<String, Object>) response.get("error");
        assertEquals("validation_failure", errors.get("code"));
    }

    @Test
    public void initialServerConf() {
        assertInitialServerConfValidationError(HAS_COLON, "aa", "aa");
        assertInitialServerConfValidationError(HAS_SEMICOLON, "aa", "aa");
        assertInitialServerConfValidationError(HAS_PERCENT, "aa", "aa");
        assertInitialServerConfValidationError(HAS_NON_NORMALIZED, "aa", "aa");
        assertInitialServerConfValidationError(HAS_BACKSLASH, "aa", "aa");
        assertInitialServerConfValidationError("aa", HAS_COLON, "aa");
        assertInitialServerConfValidationError("aa", HAS_SEMICOLON, "aa");
        assertInitialServerConfValidationError("aa", HAS_PERCENT, "aa");
        assertInitialServerConfValidationError("aa", HAS_NON_NORMALIZED, "aa");
        assertInitialServerConfValidationError("aa", HAS_BACKSLASH, "aa");
        assertInitialServerConfValidationError("aa", "aa", HAS_COLON);
        assertInitialServerConfValidationError("aa", "aa", HAS_SEMICOLON);
        assertInitialServerConfValidationError("aa", "aa", HAS_PERCENT);
        assertInitialServerConfValidationError("aa", "aa", HAS_NON_NORMALIZED);
        assertInitialServerConfValidationError("aa", "aa", HAS_BACKSLASH);

        // these should pass validation but in the end initializing fails because of missing configuration anchor
        ResponseEntity<Object> response = createInitialServerConf("aa.bb.列.ä", "aa.bb.列.ä", "aa.bb.列.ä");
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    private ResponseEntity<Object> createInitialServerConf(String securityServerCode, String ownerMemberClass,
            String ownerMemberCode) {
        InitialServerConf initialServerConf = new InitialServerConf()
                .securityServerCode(securityServerCode)
                .ownerMemberClass(ownerMemberClass)
                .ownerMemberCode(ownerMemberCode)
                .softwareTokenPin("1234");
        return restTemplate.postForEntity("/api/initialization", initialServerConf, Object.class);
    }

    private void assertInitialServerConfValidationError(String securityServerCode, String ownerMemberClass,
            String ownerMemberCode) {
        ResponseEntity<Object> response = createInitialServerConf(securityServerCode, ownerMemberClass,
                ownerMemberCode);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

}
