/*
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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.common.identifier.ClientId;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.config.IdentifierValidationConfiguration;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.restapi.openapi.validator.IdentifierValidationErrorInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.Client;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerate;
import org.niis.xroad.securityserver.restapi.openapi.model.Endpoint;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.InitialServerConf;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabel;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelWithCsrGenerate;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyName;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageType;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroup;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAdd;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceType;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceUpdate;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenName;
import org.niis.xroad.securityserver.restapi.service.AnchorNotFoundException;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.addApiKeyAuthorizationHeader;

/**
 * test validation of identifier parameters with real requests
 * (can't test binders with regular integration tests, for some reason)
 *
 * TestRestTemplate requests will not be rolled back so the context will need to be reloaded after this test class
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class IdentifierValidationRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    public static final String HAS_COLON = "aa:bb";
    public static final String HAS_SEMICOLON = "aa;bb";
    public static final String HAS_PERCENT = "aa%bb";
    public static final String HAS_NON_NORMALIZED = "aa/../bb";
    public static final String HAS_BACKSLASH = "aa\\bb";
    public static final String HAS_CONTROL_CHAR = "aa​bb"; // zero-width-space in the middle

    public static final String FIELD_CLIENTADD_MEMBER_CODE = "clientAdd.client.memberCode";
    public static final String FIELD_CLIENTADD_SUBSYSTEM_CODE = "clientAdd.client.subsystemCode";
    public static final String FIELD_LOCALGROUPADD_CODE = "localGroupAdd.code";
    public static final String FIELD_SERVICEUPDATE_URL = "serviceUpdate.url";
    public static final String FIELD_KEYLABEL_LABEL = "keyLabel.label";
    public static final String FIELD_TOKENNAME_NAME = "tokenName.name";
    public static final String FIELD_KEYNAME_NAME = "keyName.name";
    public static final String FIELD_ENDPOINTUPDATE_PATH = "endpointUpdate.path";
    public static final String FIELD_ENDPOINT_PATH = "endpoint.path";
    public static final String FIELD_KEYLABELWITHCSRGENERATE_KEYLABEL = "keyLabelWithCsrGenerate.keyLabel";
    public static final String FIELD_KEYLABELWITHCSRGENERATE_CSRGENERATEREQUEST
            = "keyLabelWithCsrGenerate.csrGenerateRequest";
    public static final String FIELD_LOCALGROUPADD_DESCRIPTION = "localGroupAdd.description";
    public static final String FIELD_LOCALGROUPDESCRIPTION = "localGroupDescription.description";

    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

    private ObjectMapper testObjectMapper = new ObjectMapper();

    @TestConfiguration
    static class TestConf {
        @Bean
        @Primary
        IdentifierValidationConfiguration.Config nonStrictIdentifierValidationConfig() {
            return () -> false;
        }
    }

    @Before
    public void setup() throws Exception {
        addApiKeyAuthorizationHeader(restTemplate);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });
        when(globalConfService.getMemberClassesForThisInstance()).thenReturn(new HashSet<>(MEMBER_CLASSES));
        when(currentSecurityServerSignCertificates.getSignCertificateInfos()).thenReturn(new ArrayList<>());
        when(serverConfService.getSecurityServerId()).thenReturn(OWNER_SERVER_ID);
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
        when(systemService.isAnchorImported()).thenReturn(false);
        when(urlValidator.isValidUrl(any())).thenReturn(true);
        doThrow(new AnchorNotFoundException(""))
                .when(initializationService).initialize(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @WithMockUser(authorities = "ADD_CLIENT")
    public void testAddClient() {
        assertAddClientValidationError(HAS_COLON, null);
        assertAddClientValidationError(HAS_SEMICOLON, null);
        assertAddClientValidationError(HAS_PERCENT, null);
        assertAddClientValidationError(HAS_NON_NORMALIZED, null);
        assertAddClientValidationError(HAS_BACKSLASH, null);
        assertAddClientValidationError(HAS_CONTROL_CHAR, null);
        assertAddClientValidationError("aa", HAS_COLON);
        assertAddClientValidationError("aa", HAS_SEMICOLON);
        assertAddClientValidationError("aa", HAS_PERCENT);
        assertAddClientValidationError("aa", HAS_NON_NORMALIZED);
        assertAddClientValidationError("aa", HAS_BACKSLASH);
        assertAddClientValidationError("aa", HAS_CONTROL_CHAR);

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
        return restTemplate.postForEntity("/api/v1/clients", clientAdd, Object.class);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3")
    public void testAddClientServiceDescription() {
        assertAddClientServiceDescriptionValidationError(HAS_COLON);
        assertAddClientServiceDescriptionValidationError(HAS_SEMICOLON);
        assertAddClientServiceDescriptionValidationError(HAS_PERCENT);
        assertAddClientServiceDescriptionValidationError(HAS_NON_NORMALIZED);
        assertAddClientServiceDescriptionValidationError(HAS_BACKSLASH);
        assertAddClientServiceDescriptionValidationError(HAS_CONTROL_CHAR);

        ResponseEntity<Object> response = createClientServiceDescription("http://www.google.com",
                "aa.bb.列.ä");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Object> invalidUrlResponse
                = createClientServiceDescription("http://www.goo" + HAS_CONTROL_CHAR + "gle.com",
                "validServiceCode");
        assertEquals(HttpStatus.BAD_REQUEST, invalidUrlResponse.getStatusCode());
    }

    private ResponseEntity<Object> createClientServiceDescription(String url, String restServiceCode) {
        ServiceDescriptionAdd serviceDescriptionAdd = new ServiceDescriptionAdd()
                .url(url)
                .restServiceCode(restServiceCode)
                .type(ServiceType.REST);
        return restTemplate.postForEntity("/api/v1/clients/FI:GOV:M1:SS1/service-descriptions",
                serviceDescriptionAdd, Object.class);
    }

    private void assertAddClientServiceDescriptionValidationError(String restServiceCode) {
        ResponseEntity<Object> response = createClientServiceDescription("http://www.google.com",
                restServiceCode);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "EDIT_SERVICE_PARAMS")
    public void testUpdateService() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_SERVICEUPDATE_URL,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        ServiceUpdate serviceUpdate = new ServiceUpdate().url("http://www.goo" + HAS_CONTROL_CHAR + "gle.com")
                .timeout(60).sslAuth(false);
        assertUpdateServiceValidationError("1", serviceUpdate, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_OPENAPI3")
    public void testUpdateServiceDescription() {
        assertUpdateServiceDescriptionValidationFailure("http://www.google.com", HAS_COLON);
        assertUpdateServiceDescriptionValidationFailure("http://www.google.com", HAS_SEMICOLON);
        assertUpdateServiceDescriptionValidationFailure("http://www.google.com", HAS_PERCENT);
        assertUpdateServiceDescriptionValidationFailure("http://www.google.com", HAS_NON_NORMALIZED);
        assertUpdateServiceDescriptionValidationFailure("http://www.google.com", HAS_BACKSLASH);
        assertUpdateServiceDescriptionValidationFailure("http://www.google.com", HAS_CONTROL_CHAR);
        assertUpdateServiceDescriptionValidationFailure("http://www.goo" + HAS_CONTROL_CHAR + "gle.com",
                "validServiceCode");
    }

    /**
     * @return response body as a Map
     */
    private Object updateServiceDescription(String url, String restServiceCode) {
        ServiceDescriptionUpdate serviceDescriptionUpdate = new ServiceDescriptionUpdate()
                .url(url)
                .restServiceCode("asdf")
                .newRestServiceCode(restServiceCode)
                .type(ServiceType.REST);

        return restTemplate.patchForObject("/api/v1/service-descriptions/1", serviceDescriptionUpdate, Object.class);
    }

    private void assertUpdateServiceDescriptionValidationFailure(String url, String restServiceCode) {
        Map<String, Object> response =
                (Map<String, Object>) updateServiceDescription(url, restServiceCode);
        assertEquals(new Integer(HttpStatus.BAD_REQUEST.value()), response.get("status"));
        Map<String, Object> errors = (Map<String, Object>) response.get("error");
        assertEquals("validation_failure", errors.get("code"));
    }

    @Test
    @WithMockUser(authorities = "INIT_CONFIG")
    public void initialServerConf() {
        assertInitialServerConfValidationError(HAS_COLON, "aa", "aa");
        assertInitialServerConfValidationError(HAS_SEMICOLON, "aa", "aa");
        assertInitialServerConfValidationError(HAS_PERCENT, "aa", "aa");
        assertInitialServerConfValidationError(HAS_NON_NORMALIZED, "aa", "aa");
        assertInitialServerConfValidationError(HAS_BACKSLASH, "aa", "aa");
        assertInitialServerConfValidationError(HAS_CONTROL_CHAR, "aa", "aa");
        assertInitialServerConfValidationError("aa", HAS_COLON, "aa");
        assertInitialServerConfValidationError("aa", HAS_SEMICOLON, "aa");
        assertInitialServerConfValidationError("aa", HAS_PERCENT, "aa");
        assertInitialServerConfValidationError("aa", HAS_NON_NORMALIZED, "aa");
        assertInitialServerConfValidationError("aa", HAS_BACKSLASH, "aa");
        assertInitialServerConfValidationError("aa", HAS_CONTROL_CHAR, "aa");
        assertInitialServerConfValidationError("aa", "aa", HAS_COLON);
        assertInitialServerConfValidationError("aa", "aa", HAS_SEMICOLON);
        assertInitialServerConfValidationError("aa", "aa", HAS_PERCENT);
        assertInitialServerConfValidationError("aa", "aa", HAS_NON_NORMALIZED);
        assertInitialServerConfValidationError("aa", "aa", HAS_BACKSLASH);
        assertInitialServerConfValidationError("aa", "aa", HAS_CONTROL_CHAR);

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
        return restTemplate.postForEntity("/api/v1/initialization", initialServerConf, Object.class);
    }

    private void assertInitialServerConfValidationError(String securityServerCode, String ownerMemberClass,
            String ownerMemberCode) {
        ResponseEntity<Object> response = createInitialServerConf(securityServerCode, ownerMemberClass,
                ownerMemberCode);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = "ADD_CLIENT")
    public void testAddClientFieldValidationErrors() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        // member code with control char
        expectedFieldValidationErrors.put(FIELD_CLIENTADD_MEMBER_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.IDENTIFIERS_CHAR.getErrorCode()));
        assertAddClientFieldValidationErrorMessages(HAS_CONTROL_CHAR, "aa", expectedFieldValidationErrors);

        // member code with colon
        expectedFieldValidationErrors.put(FIELD_CLIENTADD_MEMBER_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.IDENTIFIERS_CHAR.getErrorCode()));
        assertAddClientFieldValidationErrorMessages(HAS_COLON, "aa", expectedFieldValidationErrors);

        // member code with colon and a backslash
        expectedFieldValidationErrors.put(FIELD_CLIENTADD_MEMBER_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.IDENTIFIERS_CHAR.getErrorCode()));
        assertAddClientFieldValidationErrorMessages(HAS_COLON + HAS_BACKSLASH, "aa", expectedFieldValidationErrors);

        // member code with colon and a backslash and subsystem code with percent
        expectedFieldValidationErrors.put(FIELD_CLIENTADD_SUBSYSTEM_CODE,
                Arrays.asList(IdentifierValidationErrorInfo.IDENTIFIERS_CHAR.getErrorCode()));
        assertAddClientFieldValidationErrorMessages(HAS_COLON + HAS_BACKSLASH, HAS_PERCENT,
                expectedFieldValidationErrors);
    }

    private void assertAddClientFieldValidationErrorMessages(String memberCode, String subsystemCode,
            Map<String, List<String>> expectedFieldValidationErrors) {
        ResponseEntity<Object> response = createTestClient(memberCode, subsystemCode);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "GENERATE_KEY")
    public void addKeyWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_KEYLABEL_LABEL,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertAddKeyValidationError("1", HAS_CONTROL_CHAR, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_KEY_FRIENDLY_NAME")
    public void updateKeyWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_KEYNAME_NAME,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertKeyNameValidationError("1", new KeyName().name(HAS_CONTROL_CHAR), expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_OPENAPI3_ENDPOINT")
    public void updateEndpointWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_ENDPOINTUPDATE_PATH,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        EndpointUpdate endpointUpdate = new EndpointUpdate()
                .method(EndpointUpdate.MethodEnum.GET).path(HAS_CONTROL_CHAR);
        assertEndpointUpdateValidationError("1", endpointUpdate, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3_ENDPOINT")
    public void addEndpointWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_ENDPOINT_PATH,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        Endpoint endpoint = new Endpoint().path(HAS_CONTROL_CHAR).serviceCode("foobar").method(Endpoint.MethodEnum.GET);
        assertEndpointValidationError("1", endpoint, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_TOKEN_FRIENDLY_NAME")
    public void updateTokenWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_TOKENNAME_NAME,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertTokenNameValidationError("1", HAS_CONTROL_CHAR, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = {"GENERATE_KEY", "GENERATE_AUTH_CERT_REQ", "GENERATE_SIGN_CERT_REQ"})
    public void addKeyAndCsrWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_KEYLABELWITHCSRGENERATE_KEYLABEL,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        CsrGenerate csrGenerate = new CsrGenerate().keyUsageType(KeyUsageType.AUTHENTICATION).caName("foobar");
        KeyLabelWithCsrGenerate keyLabelWithCsrGenerate = new KeyLabelWithCsrGenerate().keyLabel(HAS_CONTROL_CHAR)
                .csrGenerateRequest(csrGenerate);
        assertAddKeyAndCsrValidationError("1", keyLabelWithCsrGenerate, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "ADD_LOCAL_GROUP")
    public void addClientLocalGroupWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        // LocalGroupAdd code with control char
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPADD_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertAddLocalGroupValidationError(HAS_CONTROL_CHAR, "aa", expectedFieldValidationErrors);

        // LocalGroupAdd desc with control char
        expectedFieldValidationErrors.remove(FIELD_LOCALGROUPADD_CODE);
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPADD_DESCRIPTION,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertAddLocalGroupValidationError("aa", HAS_CONTROL_CHAR, expectedFieldValidationErrors);

        // LocalGroupAdd code and desc with control char
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPADD_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertAddLocalGroupValidationError(HAS_CONTROL_CHAR, HAS_CONTROL_CHAR, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_LOCAL_GROUP_DESC")
    public void updateLocalGroupDescriptionWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        // Update LocalGroupDescription with control char
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPDESCRIPTION,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        assertUpdateLocalGroupDescValidationError(HAS_CONTROL_CHAR, expectedFieldValidationErrors);
    }

    private void assertAddLocalGroupValidationError(String localGroupCode, String localGroupDescription,
            Map<String, List<String>> expectedFieldValidationErrors) {
        ResponseEntity<Object> response = createTestLocalGroup(localGroupCode, localGroupDescription);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertAddKeyValidationError(String tokenIdParam, String keyLabelParam,
            Map<String, List<String>> expectedFieldValidationErrors) {
        KeyLabel keyLabel = new KeyLabel().label(keyLabelParam);
        ResponseEntity<Object> response =
                restTemplate.postForEntity("/api/v1/tokens/" + tokenIdParam + "/keys", keyLabel, Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertKeyNameValidationError(String idParam, KeyName keyNameParam,
            Map<String, List<String>> expectedFieldValidationErrors) {
        HttpEntity<KeyName> keyNameEntity = new HttpEntity<>(keyNameParam);
        ResponseEntity<Object> response = restTemplate.exchange("/api/v1/keys/" + idParam,
                HttpMethod.PATCH,
                keyNameEntity,
                Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertTokenNameValidationError(String tokenIdParam, String tokenNameParam,
            Map<String, List<String>> expectedFieldValidationErrors) {
        TokenName tokenName = new TokenName().name(tokenNameParam);
        HttpEntity<TokenName> tokenNameEntity = new HttpEntity<>(tokenName);
        ResponseEntity<Object> response = restTemplate.exchange("/api/v1/tokens/" + tokenIdParam,
                HttpMethod.PATCH,
                tokenNameEntity,
                Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertAddKeyAndCsrValidationError(String tokenIdParam,
            KeyLabelWithCsrGenerate keyLabelWithCsrGenerateParam, Map<String,
            List<String>> expectedFieldValidationErrors) {
        ResponseEntity<Object> response =
                restTemplate.postForEntity("/api/v1/tokens/" + tokenIdParam + "/keys-with-csrs",
                        keyLabelWithCsrGenerateParam, Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertUpdateLocalGroupDescValidationError(String localGroupDescription,
            Map<String, List<String>> expectedFieldValidationErrors) {
        ResponseEntity<Object> response = updateLocalGroupDesc(localGroupDescription);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private ResponseEntity<Object> updateLocalGroupDesc(String newLocalGroupDescription) {
        LocalGroupDescription localGroupDescription = new LocalGroupDescription()
                .description(newLocalGroupDescription);
        HttpEntity<LocalGroupDescription> localGroupDescriptionEntity = new HttpEntity<>(localGroupDescription);
        ParameterizedTypeReference<LocalGroup> typeRef = new ParameterizedTypeReference<LocalGroup>() {
        };
        return restTemplate.exchange("/api/v1/local-groups/0", HttpMethod.PATCH, localGroupDescriptionEntity,
                Object.class);
    }

    private ResponseEntity<Object> createTestLocalGroup(String localGroupCode, String localGroupDescription) {
        LocalGroupAdd localGroupAdd = new LocalGroupAdd().code(localGroupCode).description(localGroupDescription);
        return restTemplate.postForEntity("/api/v1/clients/FI:GOV:M1:SS1/local-groups", localGroupAdd, Object.class);
    }

    private void assertUpdateServiceValidationError(String idParam, ServiceUpdate serviceUpdate,
            Map<String, List<String>> expectedFieldValidationErrors) {
        HttpEntity<ServiceUpdate> serviceUpdateEntity = new HttpEntity<>(serviceUpdate);
        ResponseEntity<Object> response = restTemplate.exchange("/api/v1/services/" + idParam,
                HttpMethod.PATCH,
                serviceUpdateEntity,
                Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertEndpointUpdateValidationError(String idParam, EndpointUpdate update,
            Map<String, List<String>> expectedFieldValidationErrors) {
        HttpEntity<EndpointUpdate> updateEntity = new HttpEntity<>(update);
        ResponseEntity<Object> response = restTemplate.exchange("/api/v1/endpoints/" + idParam,
                HttpMethod.PATCH,
                updateEntity,
                Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertEndpointValidationError(String idParam, Endpoint endpoint,
            Map<String, List<String>> expectedFieldValidationErrors) {
        ResponseEntity<Object> response =
                restTemplate.postForEntity("/api/v1/services/" + idParam + "/endpoints",
                        endpoint, Object.class);
        assertValidationErrors(response, expectedFieldValidationErrors);
    }

    private void assertValidationErrors(ResponseEntity<Object> response,
            Map<String, List<String>> expectedFieldValidationErrors) {
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorInfo errorResponse = testObjectMapper.convertValue(response.getBody(), ErrorInfo.class);
        assertNotNull(errorResponse);
        Map<String, List<String>> actualFieldValidationErrors = errorResponse.getError().getValidationErrors();
        assertEquals(expectedFieldValidationErrors, actualFieldValidationErrors);
    }
}
