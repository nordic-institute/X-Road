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

package org.niis.xroad.edc.extension.policy.dataplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.iam.ClaimToken;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.policy.dataplane.util.DataPlaneTransferPolicyContext;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadDataPlaneAccessControlServiceTest {

    private static final String API_URL = "http://localhost:8080/api/agreements";
    private static final String CONTRACT_ID = "contract-123";

    @Mock
    EdcHttpClient httpClient;
    @Mock
    ObjectMapper mapper;
    @Mock
    TypeTransformerRegistry typeTransformerRegistry;
    @Mock
    JsonLd jsonLd;
    @Mock
    PolicyEngine policyEngine;
    @Mock
    Monitor monitor;
    @Mock
    ClaimToken claimToken;
    @Mock
    DataAddress dataAddress;

    XRoadDataPlaneAccessControlService service;

    @BeforeEach
    void setUp() {
        service = new XRoadDataPlaneAccessControlService(
                httpClient, API_URL, mapper, typeTransformerRegistry, jsonLd, policyEngine, monitor);
    }

    @Test
    void checkAccessHappyPathReturnsSuccess() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        var responseBody = ResponseBody.create("{}", MediaType.parse("application/json"));
        var response = new Response.Builder()
                .request(new Request.Builder().url(API_URL + "/" + CONTRACT_ID).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build();
        when(httpClient.execute(any(Request.class))).thenReturn(response);

        var jsonObject = Json.createObjectBuilder().build();
        when(mapper.readValue(eq("{}"), eq(JsonObject.class))).thenReturn(jsonObject);

        var expandedJsonObject = Json.createObjectBuilder().build();
        when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.success(expandedJsonObject));

        var contractAgreement = mock(ContractAgreement.class);
        var policy = mock(Policy.class);
        when(typeTransformerRegistry.transform(any(JsonObject.class), eq(ContractAgreement.class)))
                .thenReturn(Result.success(contractAgreement));
        when(contractAgreement.getPolicy()).thenReturn(policy);
        when(policyEngine.evaluate(eq(policy), any(DataPlaneTransferPolicyContext.class)))
                .thenReturn(Result.success());

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void checkAccessHttpErrorReturnsFailure() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        var responseBody = ResponseBody.create("Server Error", MediaType.parse("text/plain"));
        var response = new Response.Builder()
                .request(new Request.Builder().url(API_URL + "/" + CONTRACT_ID).build())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Server Error")
                .body(responseBody)
                .build();
        when(httpClient.execute(any(Request.class))).thenReturn(response);

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("HTTP Code was: 500");
    }

    @Test
    void checkAccessNullBodyReturnsFailure() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        var httpResponse = mock(okhttp3.Response.class);
        when(httpResponse.code()).thenReturn(200);
        when(httpResponse.body()).thenReturn(null);
        when(httpClient.execute(any(Request.class))).thenReturn(httpResponse);

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("empty body");
    }

    @Test
    void checkAccessIoExceptionReturnsFailure() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        when(httpClient.execute(any(Request.class))).thenThrow(new IOException("Connection refused"));

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains(CONTRACT_ID);
    }

    @Test
    void checkAccessJsonLdExpandFailureReturnsFailure() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        var responseBody = ResponseBody.create("{}", MediaType.parse("application/json"));
        var response = new Response.Builder()
                .request(new Request.Builder().url(API_URL + "/" + CONTRACT_ID).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build();
        when(httpClient.execute(any(Request.class))).thenReturn(response);

        var jsonObject = Json.createObjectBuilder().build();
        when(mapper.readValue(eq("{}"), eq(JsonObject.class))).thenReturn(jsonObject);
        when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.failure("expand error"));

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void checkAccessTransformFailureReturnsFailure() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        var responseBody = ResponseBody.create("{}", MediaType.parse("application/json"));
        var response = new Response.Builder()
                .request(new Request.Builder().url(API_URL + "/" + CONTRACT_ID).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build();
        when(httpClient.execute(any(Request.class))).thenReturn(response);

        var jsonObject = Json.createObjectBuilder().build();
        when(mapper.readValue(eq("{}"), eq(JsonObject.class))).thenReturn(jsonObject);

        var expandedJsonObject = Json.createObjectBuilder().build();
        when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.success(expandedJsonObject));
        when(typeTransformerRegistry.transform(any(JsonObject.class), eq(ContractAgreement.class)))
                .thenReturn(Result.failure("transform error"));

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.failed()).isTrue();
    }

    @Test
    void checkAccessPolicyDenialReturnsFailure() throws Exception {
        var requestData = createRequestData();
        var additionalData = createAdditionalData();

        var responseBody = ResponseBody.create("{}", MediaType.parse("application/json"));
        var response = new Response.Builder()
                .request(new Request.Builder().url(API_URL + "/" + CONTRACT_ID).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build();
        when(httpClient.execute(any(Request.class))).thenReturn(response);

        var jsonObject = Json.createObjectBuilder().build();
        when(mapper.readValue(eq("{}"), eq(JsonObject.class))).thenReturn(jsonObject);

        var expandedJsonObject = Json.createObjectBuilder().build();
        when(jsonLd.expand(any(JsonObject.class))).thenReturn(Result.success(expandedJsonObject));

        var contractAgreement = mock(ContractAgreement.class);
        var policy = mock(Policy.class);
        when(typeTransformerRegistry.transform(any(JsonObject.class), eq(ContractAgreement.class)))
                .thenReturn(Result.success(contractAgreement));
        when(contractAgreement.getPolicy()).thenReturn(policy);
        when(policyEngine.evaluate(any(Policy.class), any(DataPlaneTransferPolicyContext.class)))
                .thenReturn(Result.failure("policy denied"));

        var result = service.checkAccess(claimToken, dataAddress, requestData, additionalData);

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailureDetail()).contains("policy denied");
    }

    private Map<String, Object> createRequestData() {
        return Map.of("clientId", "CS:ORG:1234:sub", "method", "GET", "resolvedPath", "/api/data");
    }

    private Map<String, Object> createAdditionalData() {
        return Map.of("agreement_id", CONTRACT_ID);
    }
}
