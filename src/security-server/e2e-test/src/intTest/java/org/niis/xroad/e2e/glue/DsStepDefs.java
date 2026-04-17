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

package org.niis.xroad.e2e.glue;

import io.cucumber.java.en.Step;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.ValidatableResponse;
import org.apache.http.HttpStatus;
import org.niis.xroad.e2e.EnvSetup;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.Method.GET;
import static io.restassured.http.Method.POST;
import static io.restassured.http.Method.PUT;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.niis.xroad.e2e.EnvSetup.DS_CONTROL_PLANE;

public class DsStepDefs extends BaseE2EStepDefs {

    private static final String BASE_URL = "http://%s:%d/api/mgmt/v4alpha/participants";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(2);

    private String offerId;
    private String negotiationId;
    private String contractAgreementId;
    private String transferProcessId;

    @Step("Participant context {string} is created on {string}")
    public void participantContextIsCreated(String participantContext, String env) {
        String requestTemplate = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "ParticipantContext",
                    "identity": "test-identity-1",
                    "@id": "%s"
                }
                """;

        var mapping = envSetup.getContainerMapping(env, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);

        sendRequest(POST, BASE_URL.formatted(mapping.host(), mapping.port()),
                AuthTokens.PROVISIONER, requestTemplate.formatted(participantContext), HttpStatus.SC_OK);
    }

    private ValidatableResponse sendRequest(Method method, String url, String token, String body, int expectedStatusCode) {
        testReportService.attachJson(method + " " + url, body);
        var response = given()
                .log().all()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(body)
                .request(method, url)
                .then()
                .log().all()
                .statusCode(expectedStatusCode);
        testReportService.attachJson("Response", response.extract().body().asString());
        return response;
    }

    @Step("Participant context {string} config is created on {string}")
    public void participantContextConfigIsCreated(String participantContext, String env) {
        String request = """
                {
                     "@context": [
                         "https://w3id.org/edc/connector/management/v2"
                     ],
                     "@type": "ParticipantContextConfig",
                     "entries": {
                         "edc.participant.id": "test-identity-1"
                     },
                     "privateEntries": {}
                 }
                """;
        var mapping = envSetup.getContainerMapping(env, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = (BASE_URL + "/%s/config").formatted(mapping.host(), mapping.port(), participantContext);

        sendRequest(PUT, url, AuthTokens.PROVISIONER,
                request, HttpStatus.SC_NO_CONTENT);
    }

    @Step("Asset is created in participant context {string} on {string}")
    public void assetIsCreated(String participantContext, String server) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "asset-1",
                    "@type": "Asset",
                    "properties": {
                        "name": "Mock rest service",
                        "contenttype": "application/json"
                    },
                    "dataAddress": {
                        "@type": "DataAddress",
                        "type": "HttpData",
                        "name": "Test asset",
                        "baseUrl": "http://isrest:8080/integration/mock_1",
                        "proxyPath": "true"
                    }
                }
                """;

        var mapping = envSetup.getContainerMapping(server, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = (BASE_URL + "/%s/assets").formatted(mapping.host(), mapping.port(), participantContext);
        sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
    }

    @Step("Policy definition is created in participant context {string} on {string}")
    public void policyDefinitionIsCreated(String participantContext, String server) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "policy-allow-all",
                    "@type": "PolicyDefinition",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "Set",
                        "permission": [
                            {
                                "action": "use"
                            }
                        ]
                    }
                }
                """;

        var mapping = envSetup.getContainerMapping(server, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = (BASE_URL + "/%s/policydefinitions").formatted(mapping.host(), mapping.port(), participantContext);
        sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
    }

    @Step("Contract definition is created in participant context {string} on {string}")
    public void contractDefinitionIsCreated(String participantContext, String server) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "contract-def-1",
                    "@type": "ContractDefinition",
                    "accessPolicyId": "policy-allow-all",
                    "contractPolicyId": "policy-allow-all",
                    "assetsSelector": [
                        {
                            "@type": "Criterion",
                            "operandLeft": "https://w3id.org/edc/v0.0.1/ns/id",
                            "operator": "=",
                            "operandRight": "asset-1"
                        }
                    ]
                }
                """;
        var mapping = envSetup.getContainerMapping(server, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = (BASE_URL + "/%s/contractdefinitions").formatted(mapping.host(), mapping.port(), participantContext);
        sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
    }

    @Step("Catalog can be retrieved using participant context {string} on {string} from {string}")
    public void catalogCanBeRetrievedUsingParticipantContextFrom(String participantContext, String consumerEnv,
                                                                 String providerEnv) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "CatalogRequest",
                    "counterPartyId": "test-identity-1",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1",
                    "protocol": "dataspace-protocol-http:2025-1"
                }
                """.formatted(providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/catalog/request".formatted(participantContext);
        var response = sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        offerId = extractOfferId(body);
        assertNotNull(offerId, "Offer ID should be present in catalog response");
    }

    @Step("Contract negotiation is initiated using participant context {string} on {string} with provider {string}")
    public void contractNegotiationIsInitiated(String participantContext, String consumerEnv, String providerEnv) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "ContractRequest",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1",
                    "counterPartyId": "test-identity-1",
                    "protocol": "dataspace-protocol-http:2025-1",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "Offer",
                        "@id": "%s",
                        "assigner": "test-identity-1",
                        "target": "asset-1",
                        "permission": [
                            {
                                "action": "use"
                            }
                        ]
                    }
                }
                """.formatted(providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL, offerId);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/contractnegotiations".formatted(participantContext);
        var response = sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        negotiationId = (String) body.get("@id");
        assertNotNull(negotiationId, "Negotiation ID should be present in response");
    }

    @Step("Contract negotiation state is {string} using participant context {string} on {string}")
    public void contractNegotiationIsCompleted(String state, String participantContext, String consumerEnv) {
        String url = getControlPlaneBaseUrl(consumerEnv)
                + "/%s/contractnegotiations/%s".formatted(participantContext, negotiationId);

        await().atMost(POLL_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    var response = doGetRequest(url, AuthTokens.PARTICIPANT, HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    assertEquals(state, body.get("state"));
                    contractAgreementId = (String) body.get("contractAgreementId");
                    assertNotNull(contractAgreementId, "Contract agreement ID should be present when finalized");
                });
        testReportService.attachJson("Contract Negotiation", doGetRequest(url, AuthTokens.PARTICIPANT, HttpStatus.SC_OK)
                .extract().body().asString());
    }

    @Step("Transfer process is started using participant context {string} on {string} with provider {string}")
    public void transferProcessIsStarted(String participantContext, String consumerEnv, String providerEnv) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "TransferRequest",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1",
                    "counterPartyId": "test-identity-1",
                    "protocol": "dataspace-protocol-http:2025-1",
                    "contractId": "%s",
                    "transferType": "Xrd-PULL",
                    "dataDestination": {
                        "@type": "DataAddress",
                        "type": "HttpProxy"
                    }
                }
                """.formatted(providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL, contractAgreementId);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/transferprocesses".formatted(participantContext);
        var response = sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        transferProcessId = (String) body.get("@id");
        assertNotNull(transferProcessId, "Transfer process ID should be present in response");
    }

    @Step("Transfer process is in state {string} using participant context {string} on {string}")
    public void transferProcessIsCompleted(String processState, String participantContext, String consumerEnv) {
        String url = getControlPlaneBaseUrl(consumerEnv)
                + "/%s/transferprocesses/%s".formatted(participantContext, transferProcessId);

        await().atMost(POLL_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    var response = doGetRequest(url, AuthTokens.PARTICIPANT, HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    assertEquals(processState, body.get("state"));
                });
        testReportService.attachJson("Transfer Process", doGetRequest(url, AuthTokens.PARTICIPANT, HttpStatus.SC_OK)
                .extract().body().asString());
    }

    @Step("EDR is retrieved on {string}")
    public void edrIsRetrieved(String consumerEnv) {
        var mapping = envSetup.getContainerMapping(consumerEnv, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = "http://%s:%d/api/mgmt/v4beta/edrs/%s/dataaddress"
                .formatted(mapping.host(), mapping.port(), transferProcessId);
        var response = sendGetRequest(url, AuthTokens.PARTICIPANT, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        assertNotNull(body.get("endpoint"), "EDR should contain an endpoint");
    }

    @Step("asset access is acquired via xroad-asset-access-api for context {string} on {string} from {string} for asset {string}")
    public void assetAccessIsAcquiredViaXRoadAssetAccessApi(
            String participantContext, String consumerEnv, String providerEnv, String assetId) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "assetId": "%s",
                    "counterPartyId": "test-identity-1",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1"
                }
                """.formatted(assetId, providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/asset-access".formatted(participantContext);

        var response = sendRequest(POST, url, AuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
        Map<String, Object> body = response.extract().body().as(Map.class);
        assertNotNull(body.get("https://w3id.org/edc/v0.0.1/ns/endpoint"), "Asset access response should contain an endpoint");
    }

    private ValidatableResponse sendGetRequest(String url, String token, int expectedStatusCode) {
        testReportService.attachText("Request", "GET " + url);
        var response = doGetRequest(url, token, expectedStatusCode);
        testReportService.attachJson("Response", response.extract().body().asString());
        return response;
    }

    private ValidatableResponse doGetRequest(String url, String token, int expectedStatusCode) {
        return given()
                .log().all()
                .auth().oauth2(token)
                .request(GET, url)
                .then()
                .log().all()
                .statusCode(expectedStatusCode);
    }

    private String getControlPlaneBaseUrl(String env) {
        var mapping = envSetup.getContainerMapping(env, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        return BASE_URL.formatted(mapping.host(), mapping.port());
    }

    @SuppressWarnings("unchecked")
    private String extractOfferId(Map<String, Object> catalogBody) {
        Object datasetObj = catalogBody.get("dataset");
        if (datasetObj == null) {
            datasetObj = catalogBody.get("dcat:dataset");
        }
        Map<String, Object> dataset;
        if (datasetObj instanceof List<?> list) {
            dataset = (Map<String, Object>) list.getFirst();
        } else {
            dataset = (Map<String, Object>) datasetObj;
        }

        Object policyObj = dataset.get("hasPolicy");
        if (policyObj == null) {
            policyObj = dataset.get("odrl:hasPolicy");
        }
        Map<String, Object> policy;
        if (policyObj instanceof List<?> list) {
            policy = (Map<String, Object>) list.getFirst();
        } else {
            policy = (Map<String, Object>) policyObj;
        }
        return (String) policy.get("@id");
    }

    static class AuthTokens {
        static final String PROVISIONER = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYz"
                + "g2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LC"
                + "JqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbW"
                + "VudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqv"
                + "yLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZp"
                + "Dz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5"
                + "bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg";

        static final String ADMIN = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2"
                + "Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTUw"
                + "ZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJhZG1pbiIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcG"
                + "k6cmVhZCJ9.KNlPjFo4AdTbOVMtDPbNo2K1r76E3rvpl_mwNSuUrxHqRPMST4jxpccUsi706XcljRTs41JknmaZ5sX1fLs38RqjRsw4owCCQVublwY"
                + "m_I4RT9kHuCupMATZ-DbvlzSfEuK_qB_g6OLMOB35PTCC7MF8n9gZDx8TIFhyKJe1Rviq-9pU3fpVHZo6ZYg7szUNt8ldbM7oyLqA-GIdIQGrAhJTH"
                + "dGJawEYhtqSUps2q0yT-LWYm7JBGTKX0BBD4N7joWIM_c1W8QfwRSVW_JurrknVqEuyyNDFkAykJv0pEpA6l2U6SDdFWUbuS7IcnHpnM2ZcRKSxceH"
                + "EaQGAQQbi1g";

        static final String PARTICIPANT = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhc"
                + "nQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAP"
                + "jfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRp"
                + "ni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtG"
                + "YDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw";
    }
}
