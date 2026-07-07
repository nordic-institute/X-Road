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
import org.hamcrest.Matcher;
import org.niis.xroad.e2e.EnvSetup;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.Method.GET;
import static io.restassured.http.Method.POST;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.niis.xroad.e2e.EnvSetup.DS_CONTROL_PLANE;

public class DsStepDefs extends BaseE2EStepDefs {

    private static final String MGMT_BASE_URL = "https://%s:%d/api/management/v5beta/participants";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(2);
    private static final int MEMBER_ID_PART_COUNT = 3;
    private static final String MGMT_HOLDER_DID_SUFFIX = ":mgmt";

    private String offerId;
    private String targetAssetId;
    private String permissionJson;
    private String negotiationId;
    private String contractAgreementId;
    private String transferProcessId;

    // --- Control Plane asset/policy/contract management ---

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
        String url = (MGMT_BASE_URL + "/%s/assets").formatted(mapping.host(), mapping.port(), participantContext);
        sendRequest(POST, url, ControlPlaneAuthTokens.forContext(participantContext), request, CREATED_OR_MANAGED);
    }

    @Step("Policy definition allowing only {string} is created in participant context {string} on {string}")
    public void policyDefinitionIsCreated(String consumerDid, String participantContext, String server) {
        var mapping = envSetup.getContainerMapping(server, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);

        // Register a CEL expression that checks the consumer's DID
        String celRequest = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "CelExpression",
                    "@id": "allowed-consumer-cel",
                    "leftOperand": "allowed-consumer",
                    "description": "Only allows the specified consumer participant",
                    "scopes": ["catalog", "contract.negotiation", "transfer.process"],
                    "actions": ["use"],
                    "expression": "ctx.agent.id == '%s'"
                }
                """.formatted(consumerDid);
        String celUrl = "https://%s:%d/api/management/v5beta/celexpressions".formatted(mapping.host(), mapping.port());
        sendRequest(POST, celUrl, ControlPlaneAuthTokens.PROVISIONER, celRequest, CREATED_OR_MANAGED);

        // Create policy with constraint referencing the CEL expression
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@id": "policy-consumer-restricted",
                    "@type": "PolicyDefinition",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "Set",
                        "permission": [
                            {
                                "action": "use",
                                "constraint": [
                                    {
                                        "leftOperand": "allowed-consumer",
                                        "operator": "eq",
                                        "rightOperand": "true"
                                    }
                                ]
                            }
                        ]
                    }
                }
                """;
        String url = (MGMT_BASE_URL + "/%s/policydefinitions").formatted(mapping.host(), mapping.port(), participantContext);
        sendRequest(POST, url, ControlPlaneAuthTokens.forContext(participantContext), request, CREATED_OR_MANAGED);
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
                    "accessPolicyId": "policy-consumer-restricted",
                    "contractPolicyId": "policy-consumer-restricted",
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
        String url = (MGMT_BASE_URL + "/%s/contractdefinitions").formatted(mapping.host(), mapping.port(), participantContext);
        sendRequest(POST, url, ControlPlaneAuthTokens.forContext(participantContext), request, CREATED_OR_MANAGED);
    }

    // --- Cross-server DSP protocol operations ---

    @Step("Catalog can be retrieved using participant context {string} on {string} from {string} on {string}")
    public void catalogCanBeRetrievedUsingParticipantContextFrom(String consumerParticipantContext, String consumerEnv,
                                                                 String providerDid, String providerEnv) {
        String providerCpHost = providerEnv + "-ds-control-plane";
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "CatalogRequest",
                    "counterPartyId": "%s",
                    "counterPartyAddress": "https://%s:%d/api/dsp/xrd-ss0/2025-1",
                    "protocol": "dataspace-protocol-http:2025-1"
                }
                """.formatted(providerDid, providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/catalog/request".formatted(consumerParticipantContext);
        var response = sendRequest(POST, url, ControlPlaneAuthTokens.forContext(consumerParticipantContext), request, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        extractNecessaryPropertiesFromCatalog(body);
        assertNotNull(offerId, "Offer ID should be present in catalog response");
    }

    @Step("Contract negotiation is initiated using participant context {string} on {string} with provider {string} on {string}")
    public void contractNegotiationIsInitiated(String participantContext, String consumerEnv, String providerDid, String providerEnv) {
        String providerCpHost = providerEnv + "-ds-control-plane";

        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "ContractRequest",
                    "counterPartyAddress": "https://%s:%d/api/dsp/xrd-ss0/2025-1",
                    "counterPartyId": "%s",
                    "protocol": "dataspace-protocol-http:2025-1",
                    "policy": {
                        "@context": [
                            "http://www.w3.org/ns/odrl.jsonld",
                            {"xroad": "https://x-road.eu/edc/v1/"}
                        ],
                        "@type": "Offer",
                        "@id": "%s",
                        "assigner": "%s",
                        "target": "%s",
                        "permission": %s
                    }
                }
                """.formatted(providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL,
                providerDid, offerId, providerDid, targetAssetId, permissionJson);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/contractnegotiations".formatted(participantContext);
        var response = sendRequest(POST, url, ControlPlaneAuthTokens.forContext(participantContext), request, HttpStatus.SC_OK);

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
                    var response = doGetRequest(url, ControlPlaneAuthTokens.forContext(participantContext), HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    assertEquals(state, body.get("state"));
                    contractAgreementId = (String) body.get("contractAgreementId");
                    assertNotNull(contractAgreementId, "Contract agreement ID should be present when finalized");
                });
        testReportService.attachJson("Contract Negotiation",
                doGetRequest(url, ControlPlaneAuthTokens.forContext(participantContext), HttpStatus.SC_OK).extract().body().asString());
    }

    @Step("Transfer process is started using participant context {string} on {string} with provider {string} on {string}")
    public void transferProcessIsStarted(String participantContext, String consumerEnv, String providerDid, String providerEnv) {
        String providerCpHost = providerEnv + "-ds-control-plane";
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "TransferRequest",
                    "counterPartyAddress": "https://%s:%d/api/dsp/xrd-ss0/2025-1",
                    "counterPartyId": "%s",
                    "protocol": "dataspace-protocol-http:2025-1",
                    "contractId": "%s",
                    "transferType": "Xrd-PULL",
                    "dataDestination": {
                        "@type": "DataAddress",
                        "type": "HttpProxy"
                    }
                }
                """.formatted(providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL, providerDid, contractAgreementId);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/transferprocesses".formatted(participantContext);
        var response = sendRequest(POST, url, ControlPlaneAuthTokens.forContext(participantContext), request, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        transferProcessId = (String) body.get("@id");
        assertNotNull(transferProcessId, "Transfer process ID should be present in response");
    }

    @Step("Transfer process is in state {string} using participant context {string} on {string}")
    public void transferProcessIsCompleted(String processState, String participantContext, String consumerEnv) {
        String url = getControlPlaneBaseUrl(consumerEnv)
                + "/%s/transferprocesses/%s".formatted(participantContext, transferProcessId);
        awaitResourceState(url, participantContext, processState, "Transfer Process");
    }

    @Step("Credential for X-Road member {string} is revoked at the issuer on {string}")
    public void credentialForMemberIsRevoked(String memberId, String issuerEnv) {
        String base = issuerCredentialsBaseUrl(issuerEnv);
        String credentialId = findIssuedCredentialId(base, memberId);
        assertNotNull(credentialId, "Issuer should hold an issued credential for member " + memberId);
        sendRequest(POST, base + "/%s/revoke".formatted(credentialId), IssuerAuthTokens.PARTICIPANT, "", REVOKE_ACCEPTED);
    }

    @Step("Contract negotiation reaches terminal state {string} using participant context {string} on {string}")
    public void contractNegotiationReachesTerminalState(String state, String participantContext, String consumerEnv) {
        String url = getControlPlaneBaseUrl(consumerEnv)
                + "/%s/contractnegotiations/%s".formatted(participantContext, negotiationId);
        awaitResourceState(url, participantContext, state, "Contract Negotiation");
    }

    @Step("Asset access response is retrieved on {string}")
    public void assetAccessResponseIsRetrieved(String consumerEnv) {
        var mapping = envSetup.getContainerMapping(consumerEnv, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = "https://%s:%d/api/management/v3/edrs/%s/dataaddress"
                .formatted(mapping.host(), mapping.port(), transferProcessId);
        var response = sendGetRequest(url, ControlPlaneAuthTokens.forContext("xrd-" + consumerEnv), HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        assertNotNull(body.get("endpoint"), "Asset access response should contain an endpoint");
    }

    @Step("Asset access is acquired via control plane API for context {string} on {string} from {string} on {string} for asset {string}")
    public void assetAccessIsAcquiredViaControlPlaneApi(
            String participantContext, String consumerEnv, String providerDid, String providerEnv, String assetId) {
        String providerCpHost = providerEnv + "-ds-control-plane";
        String request = """
                {
                    "assetId": "%s",
                    "counterPartyId": "%s",
                    "counterPartyAddress": "https://%s:%d/api/dsp/xrd-ss0/2025-1"
                }
                """.formatted(assetId, providerDid, providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/edr".formatted(participantContext);

        var response = sendRequest(POST, url, ControlPlaneAuthTokens.forContext(participantContext), request, HttpStatus.SC_OK);
        Map<String, Object> body = response.extract().body().as(Map.class);
        assertNotNull(body.get("https://w3id.org/edc/v0.0.1/ns/endpoint"), "Asset access response should contain an endpoint");
    }

    // --- HTTP helpers ---

    private ValidatableResponse sendRequest(Method method, String url, String token, String body, int expectedStatusCode) {
        testReportService.attachJson(method + " " + url, body);
        var response = given()
                .relaxedHTTPSValidation()
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

    // Asset/policy/contract definitions are read-only when auto-provisioned from ServerConf,
    // so a create returns either 200 (created) or 409 (already managed).
    private static final Matcher<Integer> CREATED_OR_MANAGED = anyOf(is(HttpStatus.SC_OK), is(HttpStatus.SC_CONFLICT));

    // Issuer revoke returns 204 (No Content) on success; tolerate 200 across EDC patch versions.
    private static final Matcher<Integer> REVOKE_ACCEPTED = anyOf(is(HttpStatus.SC_OK), is(HttpStatus.SC_NO_CONTENT));

    private ValidatableResponse sendRequest(Method method, String url, String token, String body, Matcher<Integer> statusMatcher) {
        testReportService.attachJson(method + " " + url, body);
        var response = given()
                .relaxedHTTPSValidation()
                .log().all()
                .auth().oauth2(token)
                .contentType(ContentType.JSON)
                .body(body)
                .request(method, url)
                .then()
                .log().all()
                .statusCode(statusMatcher);
        testReportService.attachJson("Response", response.extract().body().asString());
        return response;
    }

    private ValidatableResponse sendGetRequest(String url, String token, int expectedStatusCode) {
        testReportService.attachText("Request", "GET " + url);
        var response = doGetRequest(url, token, expectedStatusCode);
        testReportService.attachJson("Response", response.extract().body().asString());
        return response;
    }

    private ValidatableResponse doGetRequest(String url, String token, int expectedStatusCode) {
        return given()
                .relaxedHTTPSValidation()
                .log().all()
                .auth().oauth2(token)
                .request(GET, url)
                .then()
                .log().all()
                .statusCode(expectedStatusCode);
    }

    private String getControlPlaneBaseUrl(String env) {
        var mapping = envSetup.getContainerMapping(env, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        return MGMT_BASE_URL.formatted(mapping.host(), mapping.port());
    }

    private void awaitResourceState(String url, String participantContext, String expectedState, String reportName) {
        await().atMost(POLL_TIMEOUT)
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    var response = doGetRequest(url, ControlPlaneAuthTokens.forContext(participantContext), HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    assertEquals(expectedState, body.get("state"));
                });
        testReportService.attachJson(reportName,
                doGetRequest(url, ControlPlaneAuthTokens.forContext(participantContext), HttpStatus.SC_OK).extract().body().asString());
    }

    private String issuerCredentialsBaseUrl(String issuerEnv) {
        var mapping = envSetup.getContainerMapping(issuerEnv, EnvSetup.DS_ISSUER_SERVICE, EnvSetup.Port.ISSUER_SERVICE_ADMIN);
        return "https://%s:%d/api/admin/v1beta/participants/issuer/credentials".formatted(mapping.host(), mapping.port());
    }

    @SuppressWarnings("unchecked")
    private String findIssuedCredentialId(String credentialsBaseUrl, String memberId) {
        var response = sendRequest(POST, credentialsBaseUrl + "/query", IssuerAuthTokens.PARTICIPANT, "{}", HttpStatus.SC_OK);
        List<Map<String, Object>> resources = response.extract().body().as(List.class);
        return resources.stream()
                .filter(resource -> hasMemberIdentifier(resource, memberId))
                .filter(resource -> !isManagementHolder(resource))
                .map(resource -> (String) resource.get("id"))
                .findFirst()
                .orElse(null);
    }

    private boolean isManagementHolder(Map<String, Object> resource) {
        return credentialSubjects(resource).stream()
                .anyMatch(subject -> subject.get("id") instanceof String subjectId && subjectId.endsWith(MGMT_HOLDER_DID_SUFFIX));
    }

    private boolean hasMemberIdentifier(Map<String, Object> resource, String memberId) {
        var parts = memberId.split(":");
        if (parts.length != MEMBER_ID_PART_COUNT) {
            return false;
        }
        return credentialSubjects(resource).stream().anyMatch(subject ->
                parts[0].equals(subject.get("xroadInstance"))
                        && parts[1].equals(subject.get("memberClass"))
                        && parts[2].equals(subject.get("memberCode")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> credentialSubjects(Map<String, Object> resource) {
        if (!(resource.get("credential") instanceof Map<?, ?> credential)) {
            return List.of();
        }
        Object subjectObj = ((Map<String, Object>) credential).get("credentialSubject");
        return switch (subjectObj) {
            case List<?> list -> (List<Map<String, Object>>) list;
            case Map<?, ?> single -> List.of((Map<String, Object>) single);
            case null, default -> List.of();
        };
    }

    @SuppressWarnings("unchecked")
    private void extractNecessaryPropertiesFromCatalog(Map<String, Object> catalogBody) {
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
        targetAssetId = (String) dataset.get("@id");

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
        offerId = (String) policy.get("@id");

        var permission = policy.get("permission");
        if (permission == null) {
            permission = dataset.get("odrl:permission");
        }
        permissionJson = JsonMapper.builder().build().writeValueAsString(permission);
    }

    // --- Auth tokens (without "Bearer " prefix — REST Assured .auth().oauth2() adds it) ---

    // Control Plane management API tokens (scope: management-api:admin)
    static class ControlPlaneAuthTokens {
        static final String PROVISIONER = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYz"
                + "g2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LC"
                + "JqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbW"
                + "VudC1hcGk6YWRtaW4ifQ.olwdJ4uNvZ2sgadFyqHBNPz0hCN1E-LorR9X4OCjp9-a0vslHdEW6yQOymTAzj2z48mfnQbV3Hifboz_ItW"
                + "dDIemvC99yaTU923f8O0ORZaSfoCViYxwu4WtBROt9vlnZHVk9nNPQPI5sVsIlnwQ-fuPT1aoVa8fpWFVCzbdmBrM3PdJk7PmOQn2NyPX"
                + "frQwkUSnf7zcECPl1rwT6Ylt95W5zBO0DG7nqQLqpDS9qlbTvHX54waGymFHOdR1uT-8lyYhefJMmOFGHG1er_w4g2w5kklO3C4tlQiBCT"
                + "kqni1dmd0mT7MXf-gWjufjTdrOUpsDtA5B8FFRkySDGVj9nw";

        // Participant-role tokens bound to each SS's control-plane participant context (xrd-ss0 / xrd-ss1).
        static final String PARTICIPANT_SS0 = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMz"
                + "dmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdG"
                + "kiOiJjZTZhMWQwZTAwMDEwMDAwMDAwMDAwMDAwMDAwMDBzczAiLCJyb2xlIjoicGFydGljaXBhbnQiLCJwYXJ0aWNpcGFudF9jb250ZXh0X2lkIj"
                + "oieHJkLXNzMCIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.L6PuH7KMc-LCktTGDkKvkmmJbsiR9"
                + "ugjsnR6_vDiidSSKkja8Lx5C8ZKibiLC3ooFofNB0VvFXYL5hkya8487xHIZqUmlLiUIjXYQzmjaeMZPa_nPai4BMtL1wNZOk-TuyE-5rMNK8u1a"
                + "ikxFB82LW1qSR0eixoMIg6PD-xTlSCetWOfaFhprBBrucjUxpfqJqMbEl6MAtCE37uwamMwqR2WqS-GFuwUsM-5z1XFZnDedVBRSpUi_p5IYTdjI"
                + "XQULGOn8JCklwNt3yQqWUEpenCGNW3WQm5huUjlDH2LAePXFfrd7EhWl0nWuu4PPcVayrtAKtdXIikRu-VkfAnSqA";

        static final String PARTICIPANT_SS1 = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMz"
                + "dmYzg2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdG"
                + "kiOiJjZTZhMWQwZTAwMDEwMDAwMDAwMDAwMDAwMDAwMDBzczEiLCJyb2xlIjoicGFydGljaXBhbnQiLCJwYXJ0aWNpcGFudF9jb250ZXh0X2lkIj"
                + "oieHJkLXNzMSIsInNjb3BlIjoibWFuYWdlbWVudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.kYZXfJCy-9Wg_Ej0mK3kXu7XHtW9_"
                + "WV1yZ_CAwLSV9srpjXXo2rtz6lVx7dFYGmKZRuBZ0tN8qhSesPknYKimvPZhqKZIUwa7EkohQLPWy5_SkkDKUD-i6-ppH6UqksSQhGE46XETaDt4"
                + "aqH34Jh8Ewk4j8K_IeyGmoWpzC150hNlGOgDd6xh5wD0_tP4UpN6QweEMgno1-LT-2FLzVRPwTENafUC7BlZLjWSMuueZ02-tkvlHVcLesgr07EB"
                + "e7fh6XFzFSpbf1hsRUlSJWfnvA5ccBPhOq9aY8xOYFeThklSZhmW0N9M55QDaxAaVXrMdX6s3o-qWt-uXHj9Yax6A";

        static String forContext(String participantContext) {
            return switch (participantContext) {
                case "xrd-ss0" -> PARTICIPANT_SS0;
                case "xrd-ss1" -> PARTICIPANT_SS1;
                default -> throw new IllegalArgumentException("No participant token for context: " + participantContext);
            };
        }
    }

    // Issuer admin API token: participant role bound to the "issuer" participant context
    // (scope: issuer-admin-api:admin), signed by mock-jwks-server.
    static class IssuerAuthTokens {
        static final String PARTICIPANT = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1YT"
                + "c0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZD"
                + "M1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOi"
                + "Jpc3N1ZXIiLCJzY29wZSI6Imlzc3Vlci1hZG1pbi1hcGk6YWRtaW4ifQ.p3HN6vMlOdT581gP0LmGXKtJzvJ2KGlpto3asDzM7Fhs85k"
                + "ZDOuc2okal3_8i59FoEkLqlDl2yCNQz1eygk1hdTYDM6WcgkEvXM_wiFfMZKD3Ob-mjf7MAR72_h6SqVIx8mD0IdheoFcLHudvPZ19du4"
                + "jLRXV1q0TddX3ycu-iSCCo1ZfbMxT3updShfFGpYKlZxduuHl3jER5ur8vCPi0BBSrnk8Urtp823l9GWn5fTejJCiS1Ocu51ouFtUhvcM"
                + "n5tKlTZD-qX5TcyJj5qks2o29qG5N0Offey8iTmjfNxIuyuX6tTGWCh0X_J0OQvMTi5XIGP1Hx6cRsZX4h0Ag";
    }

}
