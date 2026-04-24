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
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Base64;
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
import static org.niis.xroad.e2e.EnvSetup.DS_IDENTITY_HUB;
import static org.niis.xroad.e2e.EnvSetup.DS_ISSUER_SERVICE;

public class DsStepDefs extends BaseE2EStepDefs {

    private static final String MGMT_BASE_URL = "http://%s:%d/api/mgmt/v4alpha/participants";
    private static final String IH_BASE_URL = "http://%s:%d/api/identity/v1alpha/participants";
    private static final String IS_ADMIN_BASE_URL = "http://%s:%d/api/admin/v1alpha";
    private static final String IS_IDENTITY_BASE_URL = "http://%s:%d/api/identity/v1alpha/participants";
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration POLL_TIMEOUT = Duration.ofMinutes(2);

    private String offerId;
    private String targetAssetId;
    private String permissionJson;
    private String negotiationId;
    private String contractAgreementId;
    private String transferProcessId;

    // --- Issuer Service provisioning ---

    @Step("Issuer Service participant context {string} with DID {string} and issuer service endpoint {string} is created on {string}")
    public void createIssuerServiceParticipantContext(
            String participantContext, String did, String credentialServiceEndpoint, String env) {
        String request = """
                {
                    "roles": ["admin"],
                    "serviceEndpoints": [
                        {
                            "type": "IssuerService",
                            "serviceEndpoint": "%s",
                            "id": "%s-issuer-service"
                        }
                    ],
                    "active": true,
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "issuer-key",
                        "keyGeneratorParams": {
                            "algorithm": "EdDSA"
                        }
                    }
                }
                """.formatted(credentialServiceEndpoint, did, participantContext, did, did);

        var mapping = envSetup.getContainerMapping(env, DS_ISSUER_SERVICE, EnvSetup.Port.ISSUER_SERVICE_IDENTITY);
        String url = IS_IDENTITY_BASE_URL.formatted(mapping.host(), mapping.port());
        sendRequest(POST, url, IssuerServiceAuthTokens.PROVISIONER, request, HttpStatus.SC_OK);
    }

    @Step("Holder for DID {string} is created for {string} on {string}")
    public void createHolderFor(String did, String issuerParticipantContext, String env) {
        String request = """
                {
                    "did": "%s",
                    "holderId": "%s",
                    "name": "Test Holder",
                    "properties": {
                        "membershipType": "X-Road"
                    }
                }
                """.formatted(did, did);

        var mapping = envSetup.getContainerMapping(env, DS_ISSUER_SERVICE, EnvSetup.Port.ISSUER_SERVICE_ADMIN);
        String url = IS_ADMIN_BASE_URL.formatted(mapping.host(), mapping.port()) + "/participants/%s/holders"
                .formatted(Base64.getUrlEncoder().encodeToString(issuerParticipantContext.getBytes()));
        sendRequest(POST, url, IssuerServiceAuthTokens.PARTICIPANT, request, HttpStatus.SC_CREATED);
    }

    @Step("{string} attestation definition is created for {string} on {string}")
    public void createAttestationDefinition(String attestationDefinition, String issuerParticipantContext, String env) {
        String request = """
                {
                    "attestationType": "holder",
                    "configuration": {},
                    "id": "%s-attestation-definition"
                }
                """.formatted(attestationDefinition);

        var mapping = envSetup.getContainerMapping(env, DS_ISSUER_SERVICE, EnvSetup.Port.ISSUER_SERVICE_ADMIN);
        String url = IS_ADMIN_BASE_URL.formatted(mapping.host(), mapping.port()) + "/participants/%s/attestations"
                .formatted(Base64.getUrlEncoder().encodeToString(issuerParticipantContext.getBytes()));
        sendRequest(POST, url, IssuerServiceAuthTokens.PARTICIPANT, request, HttpStatus.SC_CREATED);
    }

    @Step("{string} credential definition is created for {string} on {string}")
    public void createCredentialDefinition(String credentialDefinition, String issuerParticipantContext, String env) {
        String request = """
                {
                    "attestations": ["%s-attestation-definition"],
                    "credentialType": "MembershipCredential",
                    "id": "%s-credential-definition",
                    "jsonSchema": "{}",
                    "jsonSchemaUrl": "https://example.com/schema/MembershipCredential.json",
                    "mappings": [
                        {
                            "input": "membershipType",
                            "output": "credentialSubject.membershipType",
                            "required": "true"
                        }
                    ],
                    "rules": [],
                    "format": "VC1_0_JWT",
                    "validity": "604800"
                }
                """.formatted(credentialDefinition, credentialDefinition);

        var mapping = envSetup.getContainerMapping(env, DS_ISSUER_SERVICE, EnvSetup.Port.ISSUER_SERVICE_ADMIN);
        String url = IS_ADMIN_BASE_URL.formatted(mapping.host(), mapping.port()) + "/participants/%s/credentialdefinitions"
                .formatted(Base64.getUrlEncoder().encodeToString(issuerParticipantContext.getBytes()));
        sendRequest(POST, url, IssuerServiceAuthTokens.PARTICIPANT, request, HttpStatus.SC_CREATED);
    }

    // --- Identity Hub provisioning ---

    @Step("Identity Hub participant context {string} with DID {string} and credential service endpoint {string} is created on {string}")
    public void createIdentityHubParticipantContext(String participantContext, String did, String credentialServiceEndpoint, String env) {
        String request = """
                {
                    "roles": [],
                    "serviceEndpoints": [
                        {
                            "type": "CredentialService",
                            "serviceEndpoint": "%s",
                            "id": "%s-credential-service"
                        }
                    ],
                    "active": true,
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s-key",
                        "keyGeneratorParams": {
                            "algorithm": "EdDSA"
                        }
                    }
                }
                """.formatted(credentialServiceEndpoint, did, participantContext, did, did, participantContext);

        var mapping = envSetup.getContainerMapping(env, DS_IDENTITY_HUB, EnvSetup.Port.IDENTITY_HUB_IDENTITY);
        String url = IH_BASE_URL.formatted(mapping.host(), mapping.port());
        sendRequest(POST, url, IdentityHubAuthTokens.PROVISIONER, request, HttpStatus.SC_OK);
    }

    @Step("{string} credential request from issuer {string} is submitted for {string} on {string}")
    public void submitCredentialRequest(String credentialDefinition, String issuerDid, String participantContext, String env) {
        String request = """
                {
                    "issuerDid": "%s",
                    "holderPid": "%s-credential-request",
                    "credentials": [
                        {
                            "format": "VC1_0_JWT",
                            "type": "MembershipCredential",
                            "id": "%s-credential-definition"
                        }
                    ]
                }
                """.formatted(issuerDid, credentialDefinition, credentialDefinition);

        var mapping = envSetup.getContainerMapping(env, DS_IDENTITY_HUB, EnvSetup.Port.IDENTITY_HUB_IDENTITY);
        String url = IH_BASE_URL.formatted(mapping.host(), mapping.port()) + "/%s/credentials/request"
                .formatted(Base64.getUrlEncoder().encodeToString(participantContext.getBytes()));
        sendRequest(POST, url, IdentityHubAuthTokens.ADMIN, request, HttpStatus.SC_CREATED);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    @Step("{string} credential request for participant {string} reaches status {string} on {string}")
    public void credentialRequestReachesStatus(String credentialRequest, String participantContext, String expectedStatus, String env) {
        var mapping = envSetup.getContainerMapping(env, DS_IDENTITY_HUB, EnvSetup.Port.IDENTITY_HUB_IDENTITY);
        var b64ContextId = Base64.getUrlEncoder().encodeToString(participantContext.getBytes());
        String url = IH_BASE_URL.formatted(mapping.host(), mapping.port())
                + "/%s/credentials/request/%s-credential-request".formatted(b64ContextId, credentialRequest);

        await().atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(3))
                .until(() -> {
                    var response = doGetRequest(url, IdentityHubAuthTokens.ADMIN, HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    var status = (String) body.get("status");
                    if ("ERROR".equals(status)) {
                        throw new AssertionError("Credential request reached ERROR status. Response: " + body);
                    }
                    return expectedStatus.equals(status);
                });
    }

    // --- Control Plane participant context management ---

    @Step("Participant context {string} with DID {string} is created on {string}")
    public void participantContextIsCreated(String participantContext, String did, String env) {
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "ParticipantContext",
                    "identity": "%s",
                    "@id": "%s"
                }
                """.formatted(did, participantContext);

        var mapping = envSetup.getContainerMapping(env, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        sendRequest(POST, MGMT_BASE_URL.formatted(mapping.host(), mapping.port()),
                ControlPlaneAuthTokens.PROVISIONER, request, HttpStatus.SC_OK);
    }

    @Step("Participant context {string} config with DID {string} is created on {string}")
    public void participantContextConfigIsCreated(String participantContext, String did, String env) {
        String request = """
                {
                     "@context": [
                         "https://w3id.org/edc/connector/management/v2"
                     ],
                     "@type": "ParticipantContextConfig",
                     "entries": {
                         "edc.participant.id": "%s",
                         "edc.iam.issuer.id": "%s",
                         "edc.iam.sts.oauth.token.url": "http://ds-identity-hub:%s/api/sts/token",
                         "edc.iam.sts.oauth.client.id": "%s",
                         "edc.iam.sts.oauth.client.secret.alias": "%s-sts-client-secret"
                     },
                     "privateEntries": {}
                 }
                """.formatted(did, did, EnvSetup.Port.IDENTITY_HUB_STS, did, participantContext);
        var mapping = envSetup.getContainerMapping(env, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = (MGMT_BASE_URL + "/%s/config").formatted(mapping.host(), mapping.port(), participantContext);

        sendRequest(PUT, url, ControlPlaneAuthTokens.PROVISIONER, request, HttpStatus.SC_NO_CONTENT);
    }

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
        sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
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
        String celUrl = "http://%s:%d/api/mgmt/v4alpha/celexpressions".formatted(mapping.host(), mapping.port());
        sendRequest(POST, celUrl, ControlPlaneAuthTokens.PROVISIONER, celRequest, HttpStatus.SC_OK);

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
        sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
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
        sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
    }

    // --- Cross-server DSP protocol operations ---

    @Step("Catalog can be retrieved using participant context {string} on {string} from {string} on {string}")
    public void catalogCanBeRetrievedUsingParticipantContextFrom(String consumerParticipantContext, String consumerEnv,
                                                                 String providerDid, String providerEnv) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "CatalogRequest",
                    "counterPartyId": "%s",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1",
                    "protocol": "dataspace-protocol-http:2025-1"
                }
                """.formatted(providerDid, providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/catalog/request".formatted(consumerParticipantContext);
        var response = sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        extractNecessaryPropertiesFromCatalog(body);
        assertNotNull(offerId, "Offer ID should be present in catalog response");
    }

    @Step("Contract negotiation is initiated using participant context {string} on {string} with provider {string} on {string}")
    public void contractNegotiationIsInitiated(String participantContext, String consumerEnv, String providerDid, String providerEnv) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);

        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "ContractRequest",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1",
                    "counterPartyId": "%s",
                    "protocol": "dataspace-protocol-http:2025-1",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
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
        var response = sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);

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
                    var response = doGetRequest(url, ControlPlaneAuthTokens.PARTICIPANT, HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    assertEquals(state, body.get("state"));
                    contractAgreementId = (String) body.get("contractAgreementId");
                    assertNotNull(contractAgreementId, "Contract agreement ID should be present when finalized");
                });
        testReportService.attachJson("Contract Negotiation", doGetRequest(url, ControlPlaneAuthTokens.PARTICIPANT, HttpStatus.SC_OK)
                .extract().body().asString());
    }

    @Step("Transfer process is started using participant context {string} on {string} with provider {string} on {string}")
    public void transferProcessIsStarted(String participantContext, String consumerEnv, String providerDid, String providerEnv) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "@context": [
                        "https://w3id.org/edc/connector/management/v2"
                    ],
                    "@type": "TransferRequest",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1",
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
        var response = sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);

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
                    var response = doGetRequest(url, ControlPlaneAuthTokens.PARTICIPANT, HttpStatus.SC_OK);
                    Map<String, Object> body = response.extract().body().as(Map.class);
                    assertEquals(processState, body.get("state"));
                });
        testReportService.attachJson("Transfer Process", doGetRequest(url, ControlPlaneAuthTokens.PARTICIPANT, HttpStatus.SC_OK)
                .extract().body().asString());
    }

    @Step("EDR is retrieved on {string}")
    public void edrIsRetrieved(String consumerEnv) {
        var mapping = envSetup.getContainerMapping(consumerEnv, DS_CONTROL_PLANE, EnvSetup.Port.CONTROL_PLANE_MANAGEMENT);
        String url = "http://%s:%d/api/mgmt/v4beta/edrs/%s/dataaddress"
                .formatted(mapping.host(), mapping.port(), transferProcessId);
        var response = sendGetRequest(url, ControlPlaneAuthTokens.PARTICIPANT, HttpStatus.SC_OK);

        Map<String, Object> body = response.extract().body().as(Map.class);
        assertNotNull(body.get("endpoint"), "EDR should contain an endpoint");
    }

    @Step("EDR is acquired via xroad-edr-api for context {string} on {string} from {string} on {string} for asset {string}")
    public void edrIsAcquiredViaXRoadEdrApi(
            String participantContext, String consumerEnv, String providerDid, String providerEnv, String assetId) {
        String providerCpHost = envSetup.getContainerName(providerEnv, DS_CONTROL_PLANE);
        String request = """
                {
                    "assetId": "%s",
                    "counterPartyId": "%s",
                    "counterPartyAddress": "http://%s:%d/api/dsp/test-part-ctx/2025-1"
                }
                """.formatted(assetId, providerDid, providerCpHost, EnvSetup.Port.CONTROL_PLANE_PROTOCOL);
        String url = getControlPlaneBaseUrl(consumerEnv) + "/%s/edr".formatted(participantContext);

        var response = sendRequest(POST, url, ControlPlaneAuthTokens.PARTICIPANT, request, HttpStatus.SC_OK);
        Map<String, Object> body = response.extract().body().as(Map.class);
        assertNotNull(body.get("https://w3id.org/edc/v0.0.1/ns/endpoint"), "EDR should contain an endpoint");
    }

    // --- HTTP helpers ---

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
        return MGMT_BASE_URL.formatted(mapping.host(), mapping.port());
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

    // Control Plane management API tokens (scope: management-api:write management-api:read)
    static class ControlPlaneAuthTokens {
        static final String PROVISIONER = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYz"
                + "g2ODhlN2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LC"
                + "JqdGkiOiI3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoibWFuYWdlbW"
                + "VudC1hcGk6d3JpdGUgbWFuYWdlbWVudC1hcGk6cmVhZCJ9.VtgeUBJXWdZSsemdWTtvSDqdCUa1eBaqMlxbBVAAPsSjyVOb8wiDmxpTqv"
                + "yLKTw9WE2WznmaOUPpWh3s4nDTjHQ51-ke_H__5WHVkwK-E97AFvInue-1lPMdIC1rNGLyZKYmQQ8DtHwZDWkgl-F4zhiyTk8Z3OBzgZp"
                + "Dz3BcyyJT7WLvAHp6Pk0SdHmFhA5ctvXfra4-ZkfUUudXklOEe-8Jj42v2EjF0woUk9nHoNYA_ca2Gi3kHtJrpHhR4_3Ab7KU046-p0dF5"
                + "bVLLhYh3HEg-71R0tO9eytzbHkMZMY353aKF0bUqK4UrKnstDT55yo5j5oLpP0xGA9KGai6Kg";

        static final String PARTICIPANT = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJ0ZXN0LXBhc"
                + "nQtY3R4Iiwic2NvcGUiOiJtYW5hZ2VtZW50LWFwaTp3cml0ZSBtYW5hZ2VtZW50LWFwaTpyZWFkIn0.i7YQln4cjB2xXT5X5Nl48wys-me-HAP"
                + "jfdiVEyRAB-thKDTqODHksijPQFVMQnb5FppbUHdYiO_G2JYBwFYk36fWhpBveRKRMBaurKZZS5tXAV7bsGr9z1jcEUM45tF__kZLCV9VZ0IRp"
                + "ni4B4_AP7vc0YUqLyJ7WZXQfP-N2bBYPf8loi3No_AFEFI7mcknuxOp_oZnD6jRmwjeCdih_Nu-9rNsCpa3BM6L_EozzK3Y61X7D7cWXU7xCtG"
                + "YDcYoRka8AtBTlihXPah3lbTRKwGP1IBDZzfKqSOZDDZK2g8Em3GjuOp6_sOsVL0UwAqlZZiMfyGnPaIkACtszimIjw";
    }

    // Identity Hub identity API tokens (scope: identity-api:write identity-api:read)
    static class IdentityHubAuthTokens {
        static final String PROVISIONER = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhl"
                + "N2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOi"
                + "I3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoiaWRlbnRpdHktYXBpOndya"
                + "XRlIGlkZW50aXR5LWFwaTpyZWFkIn0.nujdj1AdxrI4CqPKruY48nx9itkh_Uf_vB4xCgEssOHdtlwGim_l5KFFxCAFYOllBmj4A91Qdhs0"
                + "04jcQ1pF3Ag7wSoVpYszbWDyJv2zamS72862fuhx0h3BCxQxS4CAsOogxR_kQEqMBnhgAKK5ndTf66kbAS83OpvtaA3DKKuVmByYZAvncLl"
                + "AAgbBf0ATGI3pG1sbHhTJ58AVBi300sp-7-B9uIijw4S-Pd-ww1ah-xc8ep3kr4YpEgODaUKnNOCXPA_vnZa-9BwYOi94kWM_DCzfZTNV2O"
                + "lb3WQojrhZbPiUCALmSmSUFJMvfMp18Z15bDQM0iTLUsVRFZMLTA";

        static final String ADMIN = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJhZG1pbiIsInNjb3BlIjoiaWRlbnRpdHktYXBpOndyaXRlIGlkZW50aXR5LWFwaT"
                + "pyZWFkIn0.vKPkIJ47atCdUnH60TsGSihx54xqbpvmI-QYSIRZprkmY3Fb8zPZ4PYMrTUCu6mw-ISPyzV1fu54s5bYD4q5trjZN8hrocgWyYbPP"
                + "kk-sZDqcKFwFfxcVWP7wPuRZOus_AxcxrSny0IQYzlTmmvhVqPq1MJjLSximHnY3Hg7yti_b0OZE5iBMKuoww8xlr6OXY4dpJHAvwK6vJhscoQa"
                + "5Mm56GsSVto38Y4HHsKlNjJlYPpILdiiytfMS5x6AhCALRoY15_0upP-MWWe7eq_2sAv7TuEZJFIdpfpWKQG4-eMHBTNim17FZx76CG9xoYhlhrJ"
                + "5WGSel_8xTJD3xSVzg";
    }

    // Issuer Service tokens (scope: issuer-admin-api + identity-api)
    static class IssuerServiceAuthTokens {
        static final String PROVISIONER = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhl"
                + "N2Y1YTc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOi"
                + "I3ZDM1YTUwZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwcm92aXNpb25lciIsInNjb3BlIjoiaXNzdWVyLWFkbWluLWFwaT"
                + "p3cml0ZSBpc3N1ZXItYWRtaW4tYXBpOnJlYWQgaWRlbnRpdHktYXBpOndyaXRlIGlkZW50aXR5LWFwaTpyZWFkIn0.bjTA0NoQ-2LDsBM5"
                + "HncXkhe2jM96wekxmE1dj09kQv_neQTP11yrbDInmmNbdTaqnowqRQGSkjRE44Hg-4OmbwHd00LbIWRD1zSOrLeZRXCa1BEym995IJYICKOex"
                + "SYPiGXcu0CCBAtokTjzA5dZAZgALlNIVfAOLh_3WHlAOMYbcUTZZ8yghOhJoy859BnfiVA-b7HERwo-0CboryTvbfYsUN6zyHq-2idTjP10LR"
                + "Tv8BQbQv81hXE9fwwGwIyGCp6vPKP0BdZ50zLy25qdpWOurblH4LcSwkoRaE9SHNn3LpTbxzUv4Zq4X-KVEBMsTwthTgA95vjfINq9KsGxWw";

        static final String PARTICIPANT = "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ijc0ZjM0MjJiMzdmYzg2ODhlN2Y1Y"
                + "Tc0MTYyN2Y4ODg5In0.eyJpc3MiOiJ0ZXN0LWlzc3VlciIsImV4cCI6MTk4OTg0MDk5NywiaWF0IjoxNzY4ODM3Mzk3LCJqdGkiOiI3ZDM1YTU"
                + "wZGNmMmEyNTE2YTE1ZDgwYjJiNDFlZWRmYSIsInJvbGUiOiJwYXJ0aWNpcGFudCIsInBhcnRpY2lwYW50X2NvbnRleHRfaWQiOiJpc3N1ZXIiLC"
                + "JzY29wZSI6Imlzc3Vlci1hZG1pbi1hcGk6d3JpdGUgaXNzdWVyLWFkbWluLWFwaTpyZWFkIn0.dwRKoVpIwSO0DKX6YDQDVT-9ssYH4L93Iaea"
                + "9PA4QISUIZZwvF-UvYPzvNHJ3VpJOQgSK35h-dMxbQ3aEdCs7dAV-3i0DKH4k1TNtV1ObDFcHIJ3d9Rl21Ob-U2K7Gj1zy9qDRE6_hh32Gc6xiXK"
                + "Wicy4wQkzN6Lsi1yyayLJlCHiCjPDrjneYl81c2lRrSJ2tsN6XYPvNE7ctjAnk9ubCu8j7od7XTGNpfcwblsr2PX1W6Il-vtCh8hWyZgOxn-NN4F"
                + "U8Q6rHVMQ7bwaLXbw93mz3A4jvu_i3ID6PLnRGkWZEt3QiHIBwPUzCJ8PWgDem-BO7ck6GqvYvH64m1bYw";
    }
}
