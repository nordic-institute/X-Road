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

package org.niis.xroad.ss.test.ds.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.ds.api.FeignControlPlaneManagementApi;
import org.niis.xroad.ss.test.ds.api.FeignIdentityHubManagementApi;
import org.niis.xroad.ss.test.ds.api.FeignIssuerServiceAdminApi;
import org.niis.xroad.ss.test.ds.api.FeignIssuerServiceIdentityApi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.DS_IDENTITY_HUB;
import static org.niis.xroad.ss.test.ds.bootstrap.DspAuthTokens.CP_PROVISIONER;
import static org.niis.xroad.ss.test.ds.bootstrap.DspAuthTokens.IH_ADMIN;
import static org.niis.xroad.ss.test.ds.bootstrap.DspAuthTokens.IH_PROVISIONER;
import static org.niis.xroad.ss.test.ds.bootstrap.DspAuthTokens.IS_PARTICIPANT;
import static org.niis.xroad.ss.test.ds.bootstrap.DspAuthTokens.IS_PROVISIONER;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings({"checkstyle:MagicNumber", "SpringJavaInjectionPointsAutowiringInspection"})
public class DspBootstrap {

    private static final String ISSUER_DID = "did:web:ds-issuer-service%3A6183:issuer";
    private static final String IH_DID = "did:web:ds-identity-hub%3A7183";
    private static final String IH_DID_MGMT = "did:web:ds-identity-hub%3A7183:mgmt";
    private static final String PARTICIPANT_ID = "ss0";
    private static final String PARTICIPANT_ID_MGMT = "ss0-mgmt";
    private static final String MEMBER_ID = "DEV:COM:1234";
    private static final String MEMBERSHIP_CREDENTIAL_REQUEST = "xroad-membership-credential-request";
    private static final String MEMBERSHIP_MGMT_CREDENTIAL_REQUEST = "xroad-membership-mgmt-credential-request";

    private final FeignIssuerServiceIdentityApi issuerServiceIdentityApi;
    private final FeignIssuerServiceAdminApi issuerServiceAdminApi;
    private final FeignIdentityHubManagementApi identityHubManagementApi;
    private final FeignControlPlaneManagementApi controlPlaneManagementApi;
    private final SsSystemTestContainerSetup containerSetup;

    /**
     * Provisions the single-SS DSP participant stack (issuer ctx, IH ctx + MembershipCredential, CP ctx + config).
     * Idempotent: tolerates HTTP 4xx (conflict on re-run), throws on HTTP 5xx.
     */
    public void bootstrap() {
        provisionIssuerParticipantContext();
        createIssuerHolder();
        createIssuerHolderMgmt();
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

    private void provisionIssuerParticipantContext() {
        log.info("DSP bootstrap step 1.1: create issuer participant context");
        var body = """
                {
                    "roles": ["admin"],
                    "serviceEndpoints": [{
                        "type": "IssuerService",
                        "serviceEndpoint": "https://ds-issuer-service:6185/api/issuance/v1alpha/participants/issuer",
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
        var response = issuerServiceIdentityApi.createParticipant(IS_PROVISIONER, body);
        tolerateConflict("1.1 issuer participant context", response);
    }

    private void createIssuerHolder() {
        log.info("DSP bootstrap step 1.2: create ss0 holder in issuer");
        var body = """
                {
                    "did": "%s",
                    "holderId": "%s",
                    "name": "SS0 Holder (dev mock)",
                    "properties": {
                        "membershipType": "X-Road",
                        "xrdMemberIdentifier": "%s"
                    }
                }
                """.formatted(IH_DID, IH_DID, MEMBER_ID);
        var response = issuerServiceAdminApi.createHolder(IS_PARTICIPANT, "issuer", body);
        tolerateConflict("1.2 ss0 holder", response);
    }

    private void createIssuerHolderMgmt() {
        log.info("DSP bootstrap step 1.3a: create ss0-mgmt holder in issuer");
        var body = """
                {
                    "did": "%s",
                    "holderId": "%s",
                    "name": "SS0 Mgmt Holder (dev mock)",
                    "properties": {
                        "membershipType": "X-Road",
                        "xrdMemberIdentifier": "%s"
                    }
                }
                """.formatted(IH_DID_MGMT, IH_DID_MGMT, MEMBER_ID);
        var response = issuerServiceAdminApi.createHolder(IS_PARTICIPANT, "issuer", body);
        tolerateConflict("1.3a ss0-mgmt holder", response);
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
        var response = issuerServiceAdminApi.createAttestationDefinition(IS_PARTICIPANT, "issuer", body);
        tolerateConflict("1.4 attestation definition", response);
    }

    private void createCredentialDefinition() {
        log.info("DSP bootstrap step 1.5: create credential definition");
        var body = """
                {
                    "attestations": ["xroad-membership-attestation-definition"],
                    "credentialType": "MembershipCredential",
                    "id": "xroad-membership-credential-definition",
                    "jsonSchema": "{}",
                    "jsonSchemaUrl": "https://example.com/schema/MembershipCredential.json",
                    "mappings": [
                        {"input": "membershipType", "output": "credentialSubject.membershipType", "required": "true"},
                        {"input": "xrdMemberIdentifier", "output": "credentialSubject.xrdMemberIdentifier", "required": "true"}
                    ],
                    "rules": [],
                    "format": "VC1_0_JWT",
                    "validity": "2592000"
                }
                """;
        var response = issuerServiceAdminApi.createCredentialDefinition(IS_PARTICIPANT, "issuer", body);
        tolerateConflict("1.5 credential definition", response);
    }

    private void awaitIssuerDidPublished() {
        log.info("DSP bootstrap step 2 gate: await issuer DID resolvable from inside xrd-ss-network");
        Awaitility.await()
                .pollInterval(2, TimeUnit.SECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .until(() -> {
                    var result = containerSetup.execInContainer(DS_IDENTITY_HUB,
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
                    "roles": [],
                    "serviceEndpoints": [{
                        "type": "CredentialService",
                        "serviceEndpoint": "https://ds-identity-hub:7185/api/credentials/v1/participants/%s",
                        "id": "%s-credential-service"
                    }],
                    "active": true,
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s-key",
                        "keyGeneratorParams": { "algorithm": "EdDSA" }
                    }
                }
                """.formatted(PARTICIPANT_ID, IH_DID, PARTICIPANT_ID, IH_DID, IH_DID, IH_DID);
        var response = identityHubManagementApi.createParticipant(IH_PROVISIONER, body);
        tolerateConflict("2a.1 IH participant context", response);
    }

    private void requestMembershipCredential() {
        log.info("DSP bootstrap step 2a.2: request MembershipCredential for ss0");
        var body = """
                {
                    "issuerDid": "%s",
                    "holderPid": "xroad-membership-credential-request",
                    "credentials": [
                        { "format": "VC1_0_JWT", "type": "MembershipCredential", "id": "xroad-membership-credential-definition" }
                    ]
                }
                """.formatted(ISSUER_DID);
        var response = identityHubManagementApi.requestCredential(IH_ADMIN, PARTICIPANT_ID, body);
        tolerateConflict("2a.2 credential request", response);
    }

    private void awaitCredentialIssued() {
        log.info("DSP bootstrap step 2a.3: poll until MembershipCredential status == ISSUED");
        pollCredentialIssued(PARTICIPANT_ID, MEMBERSHIP_CREDENTIAL_REQUEST);
    }

    private void createIdentityHubParticipantContextMgmt() {
        log.info("DSP bootstrap step 2a.4: create IH participant context ss0-mgmt");
        var body = """
                {
                    "roles": [],
                    "serviceEndpoints": [{
                        "type": "CredentialService",
                        "serviceEndpoint": "https://ds-identity-hub:7185/api/credentials/v1/participants/%s",
                        "id": "%s-credential-service"
                    }],
                    "active": true,
                    "participantContextId": "%s",
                    "did": "%s",
                    "key": {
                        "keyId": "%s#key-1",
                        "privateKeyAlias": "%s-key",
                        "keyGeneratorParams": { "algorithm": "EdDSA" }
                    }
                }
                """.formatted(PARTICIPANT_ID_MGMT, IH_DID_MGMT, PARTICIPANT_ID_MGMT, IH_DID_MGMT, IH_DID_MGMT, IH_DID_MGMT);
        var response = identityHubManagementApi.createParticipant(IH_PROVISIONER, body);
        tolerateConflict("2a.4 IH mgmt participant context", response);
    }

    private void requestMembershipCredentialMgmt() {
        log.info("DSP bootstrap step 2a.5: request MembershipCredential for ss0-mgmt");
        var body = """
                {
                    "issuerDid": "%s",
                    "holderPid": "%s",
                    "credentials": [
                        { "format": "VC1_0_JWT", "type": "MembershipCredential", "id": "xroad-membership-credential-definition" }
                    ]
                }
                """.formatted(ISSUER_DID, MEMBERSHIP_MGMT_CREDENTIAL_REQUEST);
        var response = identityHubManagementApi.requestCredential(IH_ADMIN, PARTICIPANT_ID_MGMT, body);
        tolerateConflict("2a.5 mgmt credential request", response);
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
                    var response = identityHubManagementApi.getCredentialRequestStatus(
                            IH_ADMIN, participantId, holderPid);
                    var status = extractStatus(response);
                    if ("ERROR".equals(status)) {
                        throw new IllegalStateException(
                                "Credential request '%s' for '%s' reached ERROR. Response: %s"
                                        .formatted(holderPid, participantId, response.getBody()));
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
        var response = controlPlaneManagementApi.createParticipantContext(CP_PROVISIONER, body);
        tolerateConflict("3a.1 CP participant context", response);
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
        var response = controlPlaneManagementApi.createParticipantContextConfig(CP_PROVISIONER, PARTICIPANT_ID, body);
        tolerateVoidConflict("3a.2 CP participant context config", response);
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
        var response = controlPlaneManagementApi.createParticipantContext(CP_PROVISIONER, body);
        tolerateConflict("3a.3 CP mgmt participant context", response);
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
        var response = controlPlaneManagementApi.createParticipantContextConfig(CP_PROVISIONER, PARTICIPANT_ID_MGMT, body);
        tolerateVoidConflict("3a.4 CP mgmt participant context config", response);
    }

    private void tolerateConflict(String stepLabel, ResponseEntity<Map<String, Object>> response) {
        var status = response.getStatusCode().value();
        if (status >= 500) {
            throw new IllegalStateException(
                    "DSP bootstrap step '%s' failed with HTTP %d. Body: %s".formatted(stepLabel, status, response.getBody()));
        }
        if (status >= 400) {
            log.warn("DSP bootstrap step '{}' returned HTTP {} (tolerated, likely duplicate). Body: {}",
                    stepLabel, status, response.getBody());
        }
    }

    private void tolerateVoidConflict(String stepLabel, ResponseEntity<Void> response) {
        var status = response.getStatusCode().value();
        if (status >= 500) {
            throw new IllegalStateException(
                    "DSP bootstrap step '%s' failed with HTTP %d.".formatted(stepLabel, status));
        }
        if (status >= 400) {
            log.warn("DSP bootstrap step '{}' returned HTTP {} (tolerated, likely duplicate).", stepLabel, status);
        }
    }

    private String extractStatus(ResponseEntity<Map<String, Object>> response) {
        var body = response.getBody();
        if (body == null) {
            return null;
        }
        var status = body.get("status");
        return status != null ? status.toString() : null;
    }

    @SuppressWarnings("checkstyle:SneakyThrowsCheck")
    private HttpClient buildTrustAllHttpClient() {
        try {
            var trustAll = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAll}, null);
            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build trust-all HTTP client", e);
        }
    }
}
