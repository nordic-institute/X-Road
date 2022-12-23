/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.test.glue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nortal.test.asserts.Assertion;
import com.nortal.test.asserts.AssertionOperation;
import io.cucumber.java.en.Step;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateDeletionRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.eclipse.jetty.http.HttpMethod.POST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.niis.xroad.cs.test.constants.CommonTestData.API_KEY_HEADER_PREFIX;
import static org.niis.xroad.cs.test.constants.CommonTestData.API_KEY_TOKEN_WITH_ALL_ROLES;

public class AdminApiMockStepDefs extends BaseStepDefs {
    @Autowired
    private ObjectMapper objectMapper;

    private static final String MOCK_RESPONSE_ID = "MANAGEMENT-REQUESTS-MOCK";

    @Step("Admin api is mocked with a response with status-code {int}, type {managementRequestTypeDto} and id {int}")
    public void adminApiIsMocked(Integer statusCode, ManagementRequestTypeDto type, Integer id) throws Exception {
        var response = new ManagementRequestDto();
        response.setId(id);
        response.setType(type);

        mockServerClient
                .when(getManagementRequestsDefinition())
                .withId(MOCK_RESPONSE_ID)
                .respond(response()
                        .withBody(objectMapper.writeValueAsBytes(response))
                        .withContentType(APPLICATION_JSON)
                        .withStatusCode(statusCode));
    }

    @Step("Admin api has not received any request")
    public void adminApiValidateNoInteraction() {
        mockServerClient.verifyZeroInteractions();
    }

    @Step("Admin api has received following request")
    public void adminApiValidateMock(String expectedRequest) throws JsonProcessingException {
        cucumberScenarioProvider.getCucumberScenario()
                .attach(expectedRequest, MediaType.APPLICATION_JSON_VALUE, "Expected json");

        HttpRequest[] httpRequests = mockServerClient.retrieveRecordedRequests(getManagementRequestsDefinition());

        var actualBody = httpRequests[0].getBody().getValue();
        validate(httpRequests)
                .assertion(new Assertion.Builder()
                        .message("Verify single request")
                        .operation(AssertionOperation.EXPRESSION)
                        .expression("length == 1")
                        .build())
                .assertion(new Assertion.Builder()
                        .message("Verify body")
                        .expression("=")
                        .expectedValue(objectMapper.readValue(expectedRequest, ManagementRequestDto.class))
                        .actualValue(objectMapper.readValue((String) actualBody, ManagementRequestDto.class))
                        .build())
                .execute();
    }

    @Step("Admin api has received following auth cert deletion request")
    public void adminApiValidateAuthCertDeletionMock(String expectedRequest) throws JsonProcessingException {
        HttpRequest[] httpRequests = mockServerClient.retrieveRecordedRequests(getManagementRequestsDefinition());

        var actualBody = httpRequests[0].getBody().getValue();
        validate(httpRequests)
                .assertion(new Assertion.Builder()
                        .message("Verify single request")
                        .operation(AssertionOperation.EXPRESSION)
                        .expression("length == 1")
                        .build())
                .execute();

        var expected = objectMapper.readValue(expectedRequest, AuthenticationCertificateDeletionRequestDto.class);
        validate(objectMapper.readValue((String) actualBody, AuthenticationCertificateDeletionRequestDto.class))
                .assertion(new Assertion.Builder()
                        .message("assert type")
                        .expression("type")
                        .expectedValue(expected.getType())
                        .build())
                .assertion(new Assertion.Builder()
                        .message("assert origin")
                        .expression("origin")
                        .expectedValue(expected.getOrigin())
                        .build())
                .assertion(new Assertion.Builder()
                        .message("assert securityServerId")
                        .expression("securityServerId")
                        .expectedValue(expected.getSecurityServerId())
                        .build())
                .assertion(new Assertion.Builder()
                        .message("assert securityServerId")
                        .operation(AssertionOperation.EXPRESSION)
                        .expression("authenticationCertificate.length > 0")
                        .build())
                .execute();
    }

    private RequestDefinition getManagementRequestsDefinition() {
        return request()
                .withMethod(POST.name())
                .withHeader(HttpHeaders.AUTHORIZATION, API_KEY_HEADER_PREFIX + API_KEY_TOKEN_WITH_ALL_ROLES)
                .withPath("/management-requests");
    }
}
