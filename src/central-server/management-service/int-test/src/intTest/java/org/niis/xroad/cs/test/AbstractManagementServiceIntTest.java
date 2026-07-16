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
package org.niis.xroad.cs.test;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import jakarta.xml.soap.MessageFactory;
import jakarta.xml.soap.SOAPFault;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestPayload;
import org.niis.xroad.cs.openapi.model.AddressChangeRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ClientDisableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.ClientRenameRequestDto;
import org.niis.xroad.cs.openapi.model.MaintenanceModeDisableRequestDto;
import org.niis.xroad.cs.openapi.model.MaintenanceModeEnableRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.openapi.model.OwnerChangeRequestDto;
import org.niis.xroad.cs.test.container.ManagementServiceIntTestContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import org.xml.sax.InputSource;
import tools.jackson.databind.json.JsonMapper;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.niis.xroad.cs.test.constants.CommonTestData.API_KEY_HEADER_PREFIX;
import static org.niis.xroad.cs.test.container.ManagementServiceIntTestContainerSetup.CS;

/**
 * Shared fixture for every management-service scenario class: SOAP management-request posting via
 * RestAssured - a like-for-like replacement for the legacy Feign {@code FeignManagementRequestsApi}, since
 * the request bytes are already a fully-assembled SOAP multipart payload - plus MockServer stubbing/
 * verification of the Central Server admin API and SOAP fault assertions.
 */
@ExtendWith(ApiStackExtension.class)
@SuppressWarnings("checkstyle:magicnumber")
abstract class AbstractManagementServiceIntTest {

    private static final String MOCK_RESPONSE_ID = "MANAGEMENT-REQUESTS-MOCK";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final MessageFactory SOAP_MESSAGE_FACTORY = createMessageFactory();

    private static final NamespaceContext SOAP_NAMESPACE_CONTEXT = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            return switch (prefix) {
                case "soap" -> "http://schemas.xmlsoap.org/soap/envelope/";
                case "xroad" -> "http://x-road.eu/xsd/xroad.xsd";
                default -> XMLConstants.NULL_NS_URI;
            };
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return null;
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return null;
        }
    };

    protected ManagementServiceIntTestContainerSetup containerSetup;

    private String adminApiToken;

    @BeforeEach
    final void injectContainerSetup(ManagementServiceIntTestContainerSetup setup) {
        this.containerSetup = setup;
    }

    @AfterEach
    final void resetMockServer() {
        containerSetup.mockServerClient().reset();
    }

    protected Response executeRequest(TestManagementRequestPayload payload) {
        return RestAssuredFactory.given()
                .contentType(payload.getContentType())
                .body(payload.getPayload())
                .post(containerSetup.managementServiceUrl());
    }

    protected Response sendRawRequest(String method, String path) {
        var url = containerSetup.baseUrl() + path;
        var request = RestAssuredFactory.given();
        if ("POST".equalsIgnoreCase(method)) {
            request = request.contentType(ContentType.TEXT).body("hello");
        }
        return request.request(Method.valueOf(method.toUpperCase()), url);
    }

    protected ClientId.Conf resolveClientIdFromEncodedStr(String clientIdStr) {
        String[] parts = clientIdStr.split(":");
        return parts.length == 4
                ? ClientId.Conf.create(parts[0], parts[1], parts[2], parts[3])
                : ClientId.Conf.create(parts[0], parts[1], parts[2]);
    }

    protected SecurityServerId.Conf resolveServerIdFromEncodedStr(String serverIdStr) {
        String[] parts = serverIdStr.split(":");
        return SecurityServerId.Conf.create(parts[0], parts[1], parts[2], parts[3]);
    }

    protected void assertResponseAndRequestId(Response response, int statusCode, int requestId) {
        assertThat(response.statusCode()).isEqualTo(statusCode);
        assertThat(xpath(response.asString(), "//xroad:requestId")).isEqualTo(String.valueOf(requestId));
    }

    protected void assertSoapFault(Response response, int statusCode) {
        assertThat(response.statusCode()).isEqualTo(statusCode);
        assertThat(xpathExists(response.asString(), "//soap:Fault")).as("SOAP fault present").isTrue();
    }

    protected void assertSoapFaultCode(Response response, int statusCode, String faultCode) {
        assertThat(response.statusCode()).isEqualTo(statusCode);
        assertThat(soapFault(response).getFaultCode()).isEqualTo(faultCode);
    }

    protected void assertSoapFaultCodeAndString(Response response, int statusCode, String faultCode, String faultString) {
        assertThat(response.statusCode()).isEqualTo(statusCode);
        var fault = soapFault(response);
        assertThat(fault.getFaultCode()).isEqualTo(faultCode);
        assertThat(fault.getFaultString()).isEqualTo(faultString);
    }

    protected void assertNoOtherHeadersThan(Response response, String allowedHeadersCsv) {
        Set<String> allowedHeaders = Arrays.stream(allowedHeadersCsv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        response.getHeaders().forEach(header ->
                assertThat(allowedHeaders).as("unexpected header %s", header.getName()).contains(header.getName().toLowerCase()));
    }

    protected void adminApiIsMocked(int statusCode, ManagementRequestTypeDto type, int requestId) {
        var mockedResponse = switch (type) {
            case AUTH_CERT_REGISTRATION_REQUEST -> new AuthenticationCertificateRegistrationRequestDto();
            case CLIENT_REGISTRATION_REQUEST -> new ClientRegistrationRequestDto();
            case OWNER_CHANGE_REQUEST -> new OwnerChangeRequestDto();
            case CLIENT_DELETION_REQUEST -> new ClientDeletionRequestDto();
            case CLIENT_DISABLE_REQUEST -> new ClientDisableRequestDto();
            case CLIENT_ENABLE_REQUEST -> new ClientEnableRequestDto();
            case CLIENT_RENAME_REQUEST -> new ClientRenameRequestDto();
            case AUTH_CERT_DELETION_REQUEST -> new AuthenticationCertificateDeletionRequestDto();
            case ADDRESS_CHANGE_REQUEST -> new AddressChangeRequestDto();
            case MAINTENANCE_MODE_ENABLE_REQUEST -> new MaintenanceModeEnableRequestDto();
            case MAINTENANCE_MODE_DISABLE_REQUEST -> new MaintenanceModeDisableRequestDto();
        };
        mockedResponse.setId(requestId);
        mockedResponse.setType(type);

        containerSetup.mockServerClient()
                .when(adminApiRequestDefinition())
                .withId(MOCK_RESPONSE_ID)
                .respond(response()
                        .withBody(JSON_MAPPER.writeValueAsBytes(mockedResponse))
                        .withContentType(APPLICATION_JSON)
                        .withStatusCode(statusCode));
    }

    protected void assertAdminApiReceivedNoRequest() {
        containerSetup.mockServerClient().verifyZeroInteractions();
    }

    protected void assertAdminApiReceivedRequest(String expectedJson) {
        var requests = recordedAdminApiRequests();
        var actual = JSON_MAPPER.readValue((String) requests[0].getBody().getValue(), ManagementRequestDto.class);
        var expected = JSON_MAPPER.readValue(expectedJson, ManagementRequestDto.class);
        assertThat(actual).isEqualTo(expected);
    }

    protected void assertAdminApiReceivedAuthCertDeletionRequest(String expectedJson) {
        var requests = recordedAdminApiRequests();
        var actual = JSON_MAPPER.readValue((String) requests[0].getBody().getValue(), AuthenticationCertificateDeletionRequestDto.class);
        var expected = JSON_MAPPER.readValue(expectedJson, AuthenticationCertificateDeletionRequestDto.class);

        assertThat(actual.getType()).isEqualTo(expected.getType());
        assertThat(actual.getOrigin()).isEqualTo(expected.getOrigin());
        assertThat(actual.getSecurityServerId()).isEqualTo(expected.getSecurityServerId());
        assertThat(actual.getAuthenticationCertificate()).isNotEmpty();
    }

    private HttpRequest[] recordedAdminApiRequests() {
        var requests = containerSetup.mockServerClient().retrieveRecordedRequests(adminApiRequestDefinition());
        assertThat(requests).as("admin API requests received").hasSize(1);
        return requests;
    }

    private RequestDefinition adminApiRequestDefinition() {
        return request()
                .withMethod("POST")
                .withHeader("Authorization", API_KEY_HEADER_PREFIX + adminApiToken())
                .withPath("/management-requests");
    }

    private String adminApiToken() {
        if (adminApiToken == null) {
            var result = containerSetup.execInContainer(CS,
                    "/usr/share/xroad/scripts/yaml_helper.sh",
                    "get",
                    "/etc/xroad/conf.d/local-tls.yaml",
                    "xroad.management-service.api-token");
            adminApiToken = result.getStdout().trim();
        }
        return adminApiToken;
    }

    @SneakyThrows
    private SOAPFault soapFault(Response response) {
        var message = SOAP_MESSAGE_FACTORY.createMessage(null, new ByteArrayInputStream(response.asByteArray()));
        return message.getSOAPBody().getFault();
    }

    @SneakyThrows
    private static String xpath(String body, String expression) {
        var xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(SOAP_NAMESPACE_CONTEXT);
        return (String) xPath.evaluate(expression, new InputSource(new StringReader(body)), XPathConstants.STRING);
    }

    private static boolean xpathExists(String body, String expression) {
        var value = xpath(body, expression);
        return value != null && !value.isEmpty();
    }

    private static MessageFactory createMessageFactory() {
        try {
            return MessageFactory.newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create SOAP MessageFactory", e);
        }
    }
}
