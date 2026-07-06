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

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.config.IdentifierValidationConfiguration;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.restapi.openapi.validator.IdentifierValidationErrorInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.CsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.InitialServerConfDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyLabelWithCsrGenerateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyNameDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDescriptionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenNameDto;
import org.niis.xroad.securityserver.restapi.service.InitializationService;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

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
 * <p>
 * WebTestClient requests will not be rolled back so the context will need to be reloaded after this test class
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class IdentifierValidationRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    WebTestClient webTestClient;

    private WebTestClient client;

    public static final String HAS_COLON = "aa:bb";
    public static final String HAS_SEMICOLON = "aa;bb";
    public static final String HAS_PERCENT = "aa%bb";
    public static final String HAS_NON_NORMALIZED = "aa/../bb";
    public static final String HAS_BACKSLASH = "aa\\bb";
    public static final String HAS_CONTROL_CHAR = "aa​bb"; // zero-width-space in the middle

    public static final String FIELD_CLIENTADD_MEMBER_CODE = "clientAddDto.client.memberCode";
    public static final String FIELD_CLIENTADD_SUBSYSTEM_CODE = "clientAddDto.client.subsystemCode";
    public static final String FIELD_LOCALGROUPADD_CODE = "localGroupAddDto.code";
    public static final String FIELD_SERVICEUPDATE_URL = "serviceUpdateDto.url";
    public static final String FIELD_KEYLABEL_LABEL = "keyLabelDto.label";
    public static final String FIELD_TOKENNAME_NAME = "tokenNameDto.name";
    public static final String FIELD_KEYNAME_NAME = "keyNameDto.name";
    public static final String FIELD_ENDPOINTUPDATE_PATH = "endpointUpdateDto.path";
    public static final String FIELD_ENDPOINT_PATH = "endpointDto.path";
    public static final String FIELD_KEYLABELWITHCSRGENERATE_KEYLABEL = "keyLabelWithCsrGenerateDto.keyLabel";
    public static final String FIELD_LOCALGROUPADD_DESCRIPTION = "localGroupAddDto.description";
    public static final String FIELD_LOCALGROUPDESCRIPTION = "localGroupDescriptionDto.description";

    private static final String PATTERN_ERROR_CODE = "Pattern";

    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

    private ObjectMapper testObjectMapper = JsonMapper.builder().build();

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
        client = addApiKeyAuthorizationHeader(webTestClient);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfProvider.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
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
        doThrow(new InitializationService.AnchorNotFoundException("err"))
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
        client.post().uri("/api/v1/clients")
                .bodyValue(createClientAddDto("aa.bb.列.ä", "aa.bb.列.ä"))
                .exchange()
                .expectStatus().isCreated();
    }

    private void assertAddClientValidationError(String memberCode, String subsystemCode) {
        client.post().uri("/api/v1/clients")
                .bodyValue(createClientAddDto(memberCode, subsystemCode))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private ClientAddDto createClientAddDto(String memberCode, String subsystemCode) {
        ClientDto clientDto = new ClientDto()
                .memberClass("GOV")
                .memberCode(memberCode)
                .subsystemCode(subsystemCode)
                .status(ClientStatusDto.SAVED);
        return new ClientAddDto().client(clientDto);
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

        client.post().uri("/api/v1/clients/FI:GOV:M1:SS1/service-descriptions")
                .bodyValue(createServiceDescriptionAdd("http://www.google.com", "aa.bb.列.ä"))
                .exchange()
                .expectStatus().isCreated();

        client.post().uri("/api/v1/clients/FI:GOV:M1:SS1/service-descriptions")
                .bodyValue(createServiceDescriptionAdd("http://www.goo" + HAS_CONTROL_CHAR + "gle.com",
                        "validServiceCode"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private ServiceDescriptionAddDto createServiceDescriptionAdd(String url, String restServiceCode) {
        return new ServiceDescriptionAddDto()
                .url(url)
                .restServiceCode(restServiceCode)
                .type(ServiceTypeDto.REST);
    }

    private void assertAddClientServiceDescriptionValidationError(String restServiceCode) {
        client.post().uri("/api/v1/clients/FI:GOV:M1:SS1/service-descriptions")
                .bodyValue(createServiceDescriptionAdd("http://www.google.com", restServiceCode))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(authorities = "EDIT_SERVICE_PARAMS")
    public void testUpdateService() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_SERVICEUPDATE_URL,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        ServiceUpdateDto serviceUpdate = new ServiceUpdateDto().url("http://www.goo" + HAS_CONTROL_CHAR + "gle.com")
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

    private Map updateServiceDescription(String url, String restServiceCode) {
        ServiceDescriptionUpdateDto serviceDescriptionUpdate = new ServiceDescriptionUpdateDto()
                .url(url)
                .restServiceCode("asdf")
                .newRestServiceCode(restServiceCode)
                .type(ServiceTypeDto.REST);

        return client.patch().uri("/api/v1/service-descriptions/1")
                .bodyValue(serviceDescriptionUpdate)
                .exchange()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertUpdateServiceDescriptionValidationFailure(String url, String restServiceCode) {
        Map<String, Object> response = updateServiceDescription(url, restServiceCode);
        assertEquals(Integer.valueOf(HttpStatus.BAD_REQUEST.value()), response.get("status"));
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
        client.post().uri("/api/v1/initialization")
                .bodyValue(createInitialServerConfDto("aa.bb.列.ä", "aa.bb.列.ä",
                        "aa.bb.列.ä"))
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    private InitialServerConfDto createInitialServerConfDto(String securityServerCode, String ownerMemberClass,
                                                            String ownerMemberCode) {
        return new InitialServerConfDto()
                .securityServerCode(securityServerCode)
                .ownerMemberClass(ownerMemberClass)
                .ownerMemberCode(ownerMemberCode)
                .softwareTokenPin("1234");
    }

    private void assertInitialServerConfValidationError(String securityServerCode, String ownerMemberClass,
                                                        String ownerMemberCode) {
        client.post().uri("/api/v1/initialization")
                .bodyValue(createInitialServerConfDto(securityServerCode, ownerMemberClass, ownerMemberCode))
                .exchange()
                .expectStatus().isBadRequest();
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
        Map responseBody = client.post().uri("/api/v1/clients")
                .bodyValue(createClientAddDto(memberCode, subsystemCode))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
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
        assertKeyNameValidationError("1", new KeyNameDto().name(HAS_CONTROL_CHAR), expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_OPENAPI3_ENDPOINT")
    public void updateEndpointWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_ENDPOINTUPDATE_PATH,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        EndpointUpdateDto endpointUpdate = new EndpointUpdateDto()
                .method(EndpointUpdateDto.MethodEnum.GET).path(HAS_CONTROL_CHAR);
        assertEndpointUpdateValidationError("1", endpointUpdate, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "ADD_OPENAPI3_ENDPOINT")
    public void addEndpointWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        expectedFieldValidationErrors.put(FIELD_ENDPOINT_PATH,
                Collections.singletonList(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode()));
        EndpointDto endpoint = new EndpointDto().path(HAS_CONTROL_CHAR).serviceCode("foobar").method(EndpointDto.MethodEnum.GET);
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
        CsrGenerateDto csrGenerate = new CsrGenerateDto().keyUsageType(KeyUsageTypeDto.AUTHENTICATION).caName("foobar");
        KeyLabelWithCsrGenerateDto keyLabelWithCsrGenerate = new KeyLabelWithCsrGenerateDto().keyLabel(HAS_CONTROL_CHAR)
                .csrGenerateRequest(csrGenerate);
        assertAddKeyAndCsrValidationError("1", keyLabelWithCsrGenerate, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "ADD_LOCAL_GROUP")
    public void addClientLocalGroupWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        // LocalGroupAdd code with control char
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPADD_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.IDENTIFIERS_CHAR.getErrorCode()));
        assertAddLocalGroupValidationError(HAS_CONTROL_CHAR, "aa", expectedFieldValidationErrors);

        // LocalGroupAdd desc with control char
        expectedFieldValidationErrors.remove(FIELD_LOCALGROUPADD_CODE);
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPADD_DESCRIPTION,
                List.of(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode(), PATTERN_ERROR_CODE));
        assertAddLocalGroupValidationError("aa", HAS_CONTROL_CHAR, expectedFieldValidationErrors);

        // LocalGroupAdd code and desc with control char
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPADD_CODE,
                Collections.singletonList(IdentifierValidationErrorInfo.IDENTIFIERS_CHAR.getErrorCode()));
        assertAddLocalGroupValidationError(HAS_CONTROL_CHAR, HAS_CONTROL_CHAR, expectedFieldValidationErrors);
    }

    @Test
    @WithMockUser(authorities = "EDIT_LOCAL_GROUP_DESC")
    public void updateLocalGroupDescriptionWithControlCharacter() {
        Map<String, List<String>> expectedFieldValidationErrors = new HashMap<>();
        // Update LocalGroupDescription with control char
        expectedFieldValidationErrors.put(FIELD_LOCALGROUPDESCRIPTION,
                List.of(IdentifierValidationErrorInfo.CONTROL_CHAR.getErrorCode(), PATTERN_ERROR_CODE));
        assertUpdateLocalGroupDescValidationError(HAS_CONTROL_CHAR, expectedFieldValidationErrors);
    }

    private void assertAddLocalGroupValidationError(String localGroupCode, String localGroupDescription,
                                                    Map<String, List<String>> expectedFieldValidationErrors) {
        Map responseBody = createTestLocalGroup(localGroupCode, localGroupDescription);
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertAddKeyValidationError(String tokenIdParam, String keyLabelParam,
                                             Map<String, List<String>> expectedFieldValidationErrors) {
        KeyLabelDto keyLabel = new KeyLabelDto().label(keyLabelParam);
        Map responseBody = client.post().uri("/api/v1/tokens/" + tokenIdParam + "/keys")
                .bodyValue(keyLabel)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertKeyNameValidationError(String idParam, KeyNameDto keyNameParam,
                                              Map<String, List<String>> expectedFieldValidationErrors) {
        Map responseBody = client.patch().uri("/api/v1/keys/" + idParam)
                .bodyValue(keyNameParam)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertTokenNameValidationError(String tokenIdParam, String tokenNameParam,
                                                Map<String, List<String>> expectedFieldValidationErrors) {
        TokenNameDto tokenName = new TokenNameDto().name(tokenNameParam);
        Map responseBody = client.patch().uri("/api/v1/tokens/" + tokenIdParam)
                .bodyValue(tokenName)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertAddKeyAndCsrValidationError(String tokenIdParam,
                                                   KeyLabelWithCsrGenerateDto keyLabelWithCsrGenerateParam, Map<String,
                    List<String>> expectedFieldValidationErrors) {
        Map responseBody = client.post().uri("/api/v1/tokens/" + tokenIdParam + "/keys-with-csrs")
                .bodyValue(keyLabelWithCsrGenerateParam)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertUpdateLocalGroupDescValidationError(String localGroupDescription,
                                                           Map<String, List<String>> expectedFieldValidationErrors) {
        Map responseBody = updateLocalGroupDesc(localGroupDescription);
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private Map updateLocalGroupDesc(String newLocalGroupDescription) {
        LocalGroupDescriptionDto localGroupDescription = new LocalGroupDescriptionDto()
                .description(newLocalGroupDescription);
        return client.patch().uri("/api/v1/local-groups/0")
                .bodyValue(localGroupDescription)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private Map createTestLocalGroup(String localGroupCode, String localGroupDescription) {
        LocalGroupAddDto localGroupAdd = new LocalGroupAddDto().code(localGroupCode).description(localGroupDescription);
        return client.post().uri("/api/v1/clients/FI:GOV:M1:SS1/local-groups")
                .bodyValue(localGroupAdd)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertUpdateServiceValidationError(String idParam, ServiceUpdateDto serviceUpdate,
                                                    Map<String, List<String>> expectedFieldValidationErrors) {
        Map responseBody = client.patch().uri("/api/v1/services/" + idParam)
                .bodyValue(serviceUpdate)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertEndpointUpdateValidationError(String idParam, EndpointUpdateDto update,
                                                     Map<String, List<String>> expectedFieldValidationErrors) {
        Map responseBody = client.patch().uri("/api/v1/endpoints/" + idParam)
                .bodyValue(update)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertEndpointValidationError(String idParam, EndpointDto endpoint,
                                               Map<String, List<String>> expectedFieldValidationErrors) {
        Map responseBody = client.post().uri("/api/v1/services/" + idParam + "/endpoints")
                .bodyValue(endpoint)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        assertValidationErrors(responseBody, expectedFieldValidationErrors);
    }

    private void assertValidationErrors(Map responseBody,
                                        Map<String, List<String>> expectedFieldValidationErrors) {
        assertNotNull(responseBody);
        ErrorInfo errorResponse = testObjectMapper.convertValue(responseBody, ErrorInfo.class);
        assertNotNull(errorResponse);
        Map<String, List<String>> actualFieldValidationErrors = errorResponse.getError().getValidationErrors();
        assertEquals(expectedFieldValidationErrors, actualFieldValidationErrors);
    }
}
