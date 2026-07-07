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
package org.niis.xroad.ss.test.api;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup.ContainerMapping;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.util.concurrent.TimeUnit;

import static org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.givenSilent;

/**
 * Provisions the single-SS DSP participant stack in the api-test tier using RestAssured.
 * Follows the anonymous-holder credential model: the issuer auto-creates a holder per requesting
 * DID, and the X-Road member identity is carried on the Identity Hub participant context
 * ({@code additionalProperties.xroadMemberId}), mirroring {@code DataspaceProvisioningService}.
 * Idempotent: tolerates HTTP 4xx (conflict on re-run), throws on HTTP 5xx.
 */
@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
class DspBootstrap {

    private static final String ISSUER_DID = "did:web:ds-issuer-service%3A6183:issuer";
    private static final String IH_DID = "did:web:ds-identity-hub%3A7183";
    private static final String IH_DID_MGMT = "did:web:ds-identity-hub%3A7183:mgmt";
    private static final String PARTICIPANT_ID = "ss0";
    private static final String PARTICIPANT_ID_MGMT = "ss0-mgmt";
    private static final String MEMBER_ID = "DEV/COM/1234";
    private static final String MEMBERSHIP_CREDENTIAL_REQUEST = "xroad-membership-credential-request";
    private static final String MEMBERSHIP_MGMT_CREDENTIAL_REQUEST = "xroad-membership-mgmt-credential-request";

    private final SsApiTestContainerSetup containerSetup;

    DspBootstrap(SsApiTestContainerSetup containerSetup) {
        this.containerSetup = containerSetup;
    }

    void bootstrap() {
        awaitIssuerServiceReady();
        provisionIssuerParticipantContext();
        createAttestationDefinition();
        createCredentialDefinition();
        awaitIssuerDidPublished();
        createIdentityHubParticipantContext();
        requestMembershipCredential();
        awaitCredentialIssued();
        createIdentityHubParticipantContextMgmt();
        requestMembershipCredentialMgmt();
        awaitCredentialIssuedMgmt();
        createControlPlaneParticipantContext();
        createControlPlaneParticipantContextConfig();
        createControlPlaneParticipantContextMgmt();
        createControlPlaneParticipantContextConfigMgmt();
    }

    private void awaitIssuerServiceReady() {
        log.info("DSP bootstrap step 0: await issuer identity API ready");
        Awaitility.await()
                .pollInterval(3, TimeUnit.SECONDS)
                .atMost(90, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        var status = givenSilent()
                                .get(issuerServiceIdentityUrl())
                                .then()
                                .extract()
                                .statusCode();
                        if (status != 404) {
                            log.info("Issuer identity API ready (HTTP {})", status);
                            return true;
                        }
                        log.info("Issuer identity API not yet ready (HTTP 404), waiting");
                        return false;
                    } catch (Exception e) {
                        log.info("Issuer identity API not reachable ({}), waiting", e.getMessage());
                        return false;
                    }
                });
    }

    private void provisionIssuerParticipantContext() {
        log.info("DSP bootstrap step 1.1: create issuer participant context");
        var body = """
                {
                    "scopes": ["identity-api:admin", "issuer-admin-api:write", "issuer-admin-api:read"],
                    "serviceEndpoints": [{
                        "type": "IssuerService",
                        "serviceEndpoint": "https://ds-issuer-service:6185/api/issuance/v1beta/participants/issuer",
                        "id": "issuer-issuer-service"
                    }],
                    "active": true,
                    "participantContextId": "issuer",
                    "did": "did:web:ds-issuer-service%3A6183:issuer",
                    "key": {
                        "keyId": "did:web:ds-issuer-service%3A6183:issuer#key-1",
                        "privateKeyAlias": "issuer-key",
                        "keyGeneratorParams": { "algorithm": "EdDSA" }
                    }
                }
                """;
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IS_PROVISIONER)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(issuerServiceIdentityUrl())
                .then()
                .extract()
                .response();
        tolerateConflict("1.1 issuer participant context", response.statusCode());
    }

    private void createAttestationDefinition() {
        log.info("DSP bootstrap step 1.4: create attestation definition");
        var body = """
                {
                    "attestationType": "holder",
                    "configuration": {},
                    "id": "xroad-membership-attestation-definition"
                }
                """;
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IS_PARTICIPANT)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(issuerServiceAdminUrl("/participants/issuer/attestations"))
                .then()
                .extract()
                .response();
        tolerateConflict("1.4 attestation definition", response.statusCode());
    }

    private void createCredentialDefinition() {
        log.info("DSP bootstrap step 1.5: create credential definition");
        var body = """
                {
                    "attestations": ["xroad-membership-attestation-definition"],
                    "credentialType": "XRoadMembershipCredential",
                    "id": "xroad-membership-credential-definition",
                    "jsonSchema": "{}",
                    "jsonSchemaUrl": "https://example.com/schema/XRoadMembershipCredential.json",
                    "mappings": [
                        {"input": "membershipType", "output": "credentialSubject.membershipType", "required": "true"},
                        {"input": "xroadInstance", "output": "credentialSubject.xroadInstance", "required": "true"},
                        {"input": "memberClass", "output": "credentialSubject.memberClass", "required": "true"},
                        {"input": "memberCode", "output": "credentialSubject.memberCode", "required": "true"}
                    ],
                    "rules": [],
                    "format": "VC1_0_JWT",
                    "validity": "2592000"
                }
                """;
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IS_PARTICIPANT)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(issuerServiceAdminUrl("/participants/issuer/credentialdefinitions"))
                .then()
                .extract()
                .response();
        tolerateConflict("1.5 credential definition", response.statusCode());
    }

    private void awaitIssuerDidPublished() {
        log.info("DSP bootstrap step 2 gate: await issuer DID resolvable from inside xrd-ss-network");
        Awaitility.await()
                .pollInterval(2, TimeUnit.SECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    var result = containerSetup.execInContainer(SsApiTestContainerSetup.DS_IDENTITY_HUB,
                            "curl", "-sk", "-o", "/dev/null", "-w", "%{http_code}",
                            "https://ds-issuer-service:6183/issuer/did.json");
                    var code = result.getStdout().trim();
                    if ("200".equals(code)) {
                        log.info("Issuer DID resolvable (200) from IH container");
                        return true;
                    }
                    log.info("Issuer DID not yet resolvable, HTTP {} (curl exit {})", code, result.getExitCode());
                    return false;
                });
    }

    private void createIdentityHubParticipantContext() {
        log.info("DSP bootstrap step 2a.1: create IH participant context ss0");
        var body = """
                {
                    "scopes": [],
                    "serviceEndpoints": [{
                        "type": "CredentialService",
                        "serviceEndpoint": "https://ds-identity-hub:7185/api/credentials/v1/participants/%s",
                        "id": "%s-credential-service"
                    }],
                    "active": true,
                    "additionalProperties": { "xroadMemberId": "%s" },
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s-key",
                        "keyGeneratorParams": { "algorithm": "EdDSA" }
                    }
                }
                """.formatted(PARTICIPANT_ID, IH_DID, MEMBER_ID, PARTICIPANT_ID, IH_DID, IH_DID, IH_DID);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IH_PROVISIONER)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(identityHubManagementUrl(""))
                .then()
                .extract()
                .response();
        tolerateConflict("2a.1 IH participant context", response.statusCode());
    }

    private void requestMembershipCredential() {
        log.info("DSP bootstrap step 2a.2: request MembershipCredential for ss0");
        var body = """
                {
                    "issuerDid": "%s",
                    "holderPid": "xroad-membership-credential-request",
                    "credentials": [
                        { "format": "VC1_0_JWT", "type": "XRoadMembershipCredential", "id": "xroad-membership-credential-definition" }
                    ]
                }
                """.formatted(ISSUER_DID);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IH_ADMIN)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(identityHubManagementUrl("/" + PARTICIPANT_ID + "/credentials/request"))
                .then()
                .extract()
                .response();
        tolerateConflict("2a.2 credential request", response.statusCode());
    }

    private void awaitCredentialIssued() {
        log.info("DSP bootstrap step 2a.3: poll until MembershipCredential status == ISSUED");
        pollCredentialIssued(PARTICIPANT_ID, MEMBERSHIP_CREDENTIAL_REQUEST);
    }

    private void createIdentityHubParticipantContextMgmt() {
        log.info("DSP bootstrap step 2a.4: create IH participant context ss0-mgmt");
        var body = """
                {
                    "scopes": [],
                    "serviceEndpoints": [{
                        "type": "CredentialService",
                        "serviceEndpoint": "https://ds-identity-hub:7185/api/credentials/v1/participants/%s",
                        "id": "%s-credential-service"
                    }],
                    "active": true,
                    "additionalProperties": { "xroadMemberId": "%s" },
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s-key",
                        "keyGeneratorParams": { "algorithm": "EdDSA" }
                    }
                }
                """.formatted(PARTICIPANT_ID_MGMT, IH_DID_MGMT, MEMBER_ID, PARTICIPANT_ID_MGMT, IH_DID_MGMT, IH_DID_MGMT, IH_DID_MGMT);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IH_PROVISIONER)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(identityHubManagementUrl(""))
                .then()
                .extract()
                .response();
        tolerateConflict("2a.4 IH mgmt participant context", response.statusCode());
    }

    private void requestMembershipCredentialMgmt() {
        log.info("DSP bootstrap step 2a.5: request MembershipCredential for ss0-mgmt");
        var body = """
                {
                    "issuerDid": "%s",
                    "holderPid": "%s",
                    "credentials": [
                        { "format": "VC1_0_JWT", "type": "XRoadMembershipCredential", "id": "xroad-membership-credential-definition" }
                    ]
                }
                """.formatted(ISSUER_DID, MEMBERSHIP_MGMT_CREDENTIAL_REQUEST);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.IH_ADMIN)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(identityHubManagementUrl("/" + PARTICIPANT_ID_MGMT + "/credentials/request"))
                .then()
                .extract()
                .response();
        tolerateConflict("2a.5 mgmt credential request", response.statusCode());
    }

    private void awaitCredentialIssuedMgmt() {
        log.info("DSP bootstrap step 2a.6: poll until mgmt MembershipCredential status == ISSUED");
        pollCredentialIssued(PARTICIPANT_ID_MGMT, MEMBERSHIP_MGMT_CREDENTIAL_REQUEST);
    }

    private void pollCredentialIssued(String participantId, String holderPid) {
        Awaitility.await()
                .pollInterval(2, TimeUnit.SECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    var response = givenSilent()
                            .header("Authorization", DspAuthTokens.IH_ADMIN)
                            .accept("application/json")
                            .get(identityHubManagementUrl("/" + participantId + "/credentials/request/" + holderPid))
                            .then()
                            .extract()
                            .response();
                    var httpStatus = response.statusCode();
                    if (httpStatus != 200) {
                        log.info("Credential poll for '{}' returned HTTP {} — retrying", holderPid, httpStatus);
                        return false;
                    }
                    var body = response.asString();
                    var status = response.jsonPath().getString("status");
                    if ("ERROR".equals(status)) {
                        throw new IllegalStateException(
                                "Credential request '%s' for '%s' reached ERROR. Response: %s"
                                        .formatted(holderPid, participantId, body));
                    }
                    log.info("Credential request '{}' for '{}' status: {}", holderPid, participantId, status);
                    return "ISSUED".equals(status);
                });
    }

    private void createControlPlaneParticipantContext() {
        log.info("DSP bootstrap step 3a.1: create CP participant context ss0");
        var body = """
                {
                    "@context": ["https://w3id.org/edc/connector/management/v2"],
                    "@type": "ParticipantContext",
                    "identity": "%s",
                    "@id": "%s"
                }
                """.formatted(IH_DID, PARTICIPANT_ID);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.CP_PROVISIONER)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(controlPlaneManagementUrl(""))
                .then()
                .extract()
                .response();
        tolerateConflict("3a.1 CP participant context", response.statusCode());
    }

    private void createControlPlaneParticipantContextConfig() {
        log.info("DSP bootstrap step 3a.2: set CP participant context config ss0");
        var body = """
                {
                    "@context": ["https://w3id.org/edc/connector/management/v2"],
                    "@type": "ParticipantContextConfig",
                    "entries": {
                        "edc.participant.id": "%s",
                        "edc.participant.did": "%s",
                        "edc.iam.sts.oauth.token.url": "https://ds-identity-hub:7184/api/sts/token",
                        "edc.iam.sts.oauth.client.id": "%s",
                        "edc.iam.sts.oauth.client.secret.alias": "%s-sts-client-secret"
                    },
                    "privateEntries": {}
                }
                """.formatted(IH_DID, IH_DID, IH_DID, PARTICIPANT_ID);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.CP_PROVISIONER)
                .contentType("application/json")
                .body(body)
                .put(controlPlaneManagementUrl("/" + PARTICIPANT_ID + "/config"))
                .then()
                .extract()
                .response();
        tolerateConflict("3a.2 CP participant context config", response.statusCode());
    }

    private void createControlPlaneParticipantContextMgmt() {
        log.info("DSP bootstrap step 3a.3: create CP participant context ss0-mgmt");
        var body = """
                {
                    "@context": ["https://w3id.org/edc/connector/management/v2"],
                    "@type": "ParticipantContext",
                    "identity": "%s",
                    "@id": "%s"
                }
                """.formatted(IH_DID_MGMT, PARTICIPANT_ID_MGMT);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.CP_PROVISIONER)
                .contentType("application/json")
                .accept("application/json")
                .body(body)
                .post(controlPlaneManagementUrl(""))
                .then()
                .extract()
                .response();
        tolerateConflict("3a.3 CP mgmt participant context", response.statusCode());
    }

    private void createControlPlaneParticipantContextConfigMgmt() {
        log.info("DSP bootstrap step 3a.4: set CP participant context config ss0-mgmt");
        var body = """
                {
                    "@context": ["https://w3id.org/edc/connector/management/v2"],
                    "@type": "ParticipantContextConfig",
                    "entries": {
                        "edc.participant.id": "%s",
                        "edc.participant.did": "%s",
                        "edc.iam.sts.oauth.token.url": "https://ds-identity-hub:7184/api/sts/token",
                        "edc.iam.sts.oauth.client.id": "%s",
                        "edc.iam.sts.oauth.client.secret.alias": "%s-sts-client-secret"
                    },
                    "privateEntries": {}
                }
                """.formatted(IH_DID_MGMT, IH_DID_MGMT, IH_DID_MGMT, PARTICIPANT_ID_MGMT);
        var response = givenSilent()
                .header("Authorization", DspAuthTokens.CP_PROVISIONER)
                .contentType("application/json")
                .body(body)
                .put(controlPlaneManagementUrl("/" + PARTICIPANT_ID_MGMT + "/config"))
                .then()
                .extract()
                .response();
        tolerateConflict("3a.4 CP mgmt participant context config", response.statusCode());
    }

    private void tolerateConflict(String stepLabel, int statusCode) {
        if (statusCode >= 500) {
            throw new IllegalStateException(
                    "DSP bootstrap step '%s' failed with HTTP %d.".formatted(stepLabel, statusCode));
        }
        if (statusCode >= 400) {
            log.warn("DSP bootstrap step '{}' returned HTTP {} (tolerated, likely duplicate).", stepLabel, statusCode);
        }
    }

    private String issuerServiceIdentityUrl() {
        var m = containerSetup.getContainerMapping(SsApiTestContainerSetup.DS_ISSUER_SERVICE, Port.DS_ISSUER_SERVICE_IDENTITY);
        return baseUrl(m) + "/api/identity/v1beta/participants";
    }

    private String issuerServiceAdminUrl(String path) {
        var m = containerSetup.getContainerMapping(SsApiTestContainerSetup.DS_ISSUER_SERVICE, Port.DS_ISSUER_SERVICE_ADMIN);
        return baseUrl(m) + "/api/admin/v1beta" + path;
    }

    private String identityHubManagementUrl(String path) {
        var m = containerSetup.getContainerMapping(SsApiTestContainerSetup.DS_IDENTITY_HUB, Port.DS_IDENTITY_HUB_IDENTITY);
        return baseUrl(m) + "/api/identity/v1beta/participants" + path;
    }

    private String controlPlaneManagementUrl(String path) {
        var m = containerSetup.getContainerMapping(SsApiTestContainerSetup.DS_CONTROL_PLANE, Port.DS_CONTROL_PLANE_MANAGEMENT);
        return baseUrl(m) + "/api/management/v5beta/participants" + path;
    }

    private String baseUrl(ContainerMapping m) {
        return "https://%s:%d".formatted(m.host(), m.port());
    }
}
