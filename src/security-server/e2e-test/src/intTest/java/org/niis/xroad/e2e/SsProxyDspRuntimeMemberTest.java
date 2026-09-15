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
package org.niis.xroad.e2e;

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Proves the story's central, otherwise-unprovable claim: a second, distinct X-Road member, newly
 * onboarded to ss0 <b>after</b> the stack is already running, serves a real transfer to a counter-party
 * on the same Security Server, with no proxy or connector restart anywhere in the flow.
 *
 * <p>ss0 already hosts member {@code DEV:COM:1234} (subsystems {@code TestService}/{@code TestSaved}),
 * seeded at environment bring-up. This scenario adds a client for member {@code DEV:COM:4321} — an
 * X-Road member that already exists (it owns ss1) but has never had a client on ss0 — entirely through
 * ss0's own admin API, at test run time: local client add, registration, a REST service description.
 * That new client is the <b>provider</b>. The <b>consumer</b> is {@code DEV:COM:1234:TestService},
 * ss0's own already-registered, already-proven subsystem, granted access to the new client's service.
 *
 * <p><b>Who the consumer is.</b> The consumer is deliberately {@code TestService} — ss0's own,
 * already-registered subsystem — not the new client calling itself. The proxy's consumer path negotiates
 * as the <i>sender member's</i> derived participant context, so {@code TestService}'s calls present
 * {@code DEV:COM:1234} (ss0's owner member). The new client's service is ACL-granted to
 * {@code TestService}, keeping this scenario about one thing: a runtime-onboarded <i>provider</i> serving
 * an established consumer. A same-member self-call of the new client is
 * {@link SsProxyDspSelfCallTest}-shaped, not this scenario.
 *
 * <p><b>Which participant contexts the exchange actually rides.</b> Publication is additive during this
 * epic (the legacy host-context publication is removed only by a later cutover story), so the new
 * client's service is published under the host context in addition to its own {@code DEV:COM:4321}
 * context, and the consumer proxy still addresses the provider's host-context DSP endpoint. The exchange
 * therefore splits: the consumer side negotiates on the sender member's context ({@code DEV:COM:1234}),
 * the provider side serves the offer on the host context — whose literal value differs per environment
 * ({@code xrd-ss0} on k8s, {@code xrd-ss0.lxd} on LXD), which is why the DB assertions never compare it
 * against a constant. Each side persists its own per-context copy of the one wire agreement. This
 * scenario does <b>not</b> exercise the new member's own participant context as the provider side's
 * context — {@link #awaitMemberContextIssued} still confirms that context and its membership credential
 * are independently provisioned, proving the runtime member is a genuine dataspace participant, just not
 * the one this particular offer happens to be served under.
 *
 * <p><b>Per-member data-plane registration is exercised for the consumer's member, not the new one.</b>
 * The consumer side's transfer runs under {@code DEV:COM:1234}, so data-plane selection resolves the
 * member-context instance the registrar created for that context — a transfer under a member context with
 * no registered instance terminates with "No dataplane found". A runtime-registered instance scoped to
 * {@code DEV:COM:4321} is <i>not</i> what this transfer uses. The EDC data-plane instance store is
 * in-memory in the control plane, backed by no table in {@code ds-control-plane} (confirmed: {@code \d}
 * on the live database lists no data-plane-instance table) and no externally reachable listing endpoint
 * the control port exposes (that port carries data-plane signaling callbacks, not a selector query API) —
 * so the new member's own record is not directly observable from an end-to-end test; its registration
 * path is proven by {@code XRoadDataPlaneRegistrarExtensionTest} and the provisioning-service unit tests
 * instead.
 *
 * <p>The scenario provisions its own sign material for the new member: after the local client add,
 * it generates a SIGNING CSR on ss0's token, has the environment's test CA sign it, and imports the
 * certificate back — the same key/cert bootstrap {@code setup.hurl} performs once for
 * {@code DEV:COM:1234}'s own sign key on ss0, just for the second member's identity, done at test run
 * time instead of at bring-up because the admin API rejects a SIGNING CSR for a member id that isn't
 * yet a local client. This needs the test CA reachable from the test JVM (see {@link E2eEnvironment}'s
 * {@code "ca"} environment), the k8s/LXD analogue of the {@code "aux"} Central Server reachability this
 * class already relies on for registration approval.
 *
 * <p>Only k8s and LXD run the dataspace protocol stack; the Compose facade does not implement
 * {@link DsControlPlaneDbOps}, so this scenario self-skips there via {@link Assumptions}, exactly like
 * {@link SsProxyDspSelfCallTest}.
 *
 * <p>Runs after {@link SsMessagelogArchiveTest} (its own traffic must not be counted by that class's
 * exact pre-archive messagelog assertions) and after {@link SsProxyDspSelfCallTest}, before
 * {@link SsMonitoringTest} (whose operational-data assertions accumulate over the whole run).
 */
@DisplayName("SS proxy - runtime-provisioned member serves an established member-context consumer, no restart")
@Order(350)
@Slf4j
@SuppressWarnings({"checkstyle:magicnumber", "unchecked"})
class SsProxyDspRuntimeMemberTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String CS_ENV = "aux";
    private static final String CA_ENV = "ca";

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";

    private static final String X_ROAD_INSTANCE = "DEV";
    private static final String NEW_MEMBER_CLASS = "COM";
    private static final String NEW_MEMBER_CODE = "4321";
    private static final String NEW_SUBSYSTEM_CODE = "RuntimeService";

    /**
     * The new client's full id, deterministic from the constants above. Used directly instead of
     * capturing an "id" field from the add-client response, since that response body is only present
     * on a 201 — a warm rerun's 409 (the client already exists) has none, and this id is recoverable
     * without it either way.
     */
    private static final String NEW_CLIENT_ID =
            X_ROAD_INSTANCE + ":" + NEW_MEMBER_CLASS + ":" + NEW_MEMBER_CODE + ":" + NEW_SUBSYSTEM_CODE;

    /** The ctx-id {@code ParticipantIdentifierScheme.memberCtxId} derives for {@code DEV:COM:4321}. */
    private static final String NEW_MEMBER_CTX_ID = "DEV:COM:4321";

    /** The ctx-id the consumer side negotiates as: {@link #CONSUMER_CLIENT_ID}'s member, ss0's owner. */
    private static final String CONSUMER_MEMBER_CTX_ID = "DEV:COM:1234";
    private static final String NEW_SERVICE_PATH = "/r1/DEV/COM/4321/RuntimeService/mock1";
    private static final String REST_SERVICE_CODE = "mock1";

    /**
     * The consumer: ss0's own, already-registered {@code TestService} subsystem, whose calls negotiate as
     * its member's derived context ({@link #CONSUMER_MEMBER_CTX_ID}). See the class doc's "Who the
     * consumer is".
     */
    private static final String CONSUMER_CLIENT_ID = "DEV:COM:1234:TestService";
    private static final String CONSUMER_X_ROAD_ID = "DEV/COM/1234/TestService";

    /** ss0's pre-existing TestService/mock1 REST service, whose backend URL is reused for the new client. */
    private static final String EXISTING_SERVICE_ID = "DEV:COM:1234:TestService:mock1";

    /**
     * The DSP asset id for the new client's service — its full client id and the REST service code,
     * colon-joined, confirmed live against {@code edc_contract_agreement.asset_id}. Unique to this
     * scenario, so full-string equality on it alone identifies this negotiation. The provider side's
     * context is the environment's host context — {@code xrd-ss0} on k8s but {@code xrd-ss0.lxd} on
     * LXD — so it is read from the matched rows and asserted for consistency, never against a literal;
     * the only literal exclusion is the {@code -mgmt} companion context.
     */
    private static final String ASSET_ID = NEW_CLIENT_ID + ":" + REST_SERVICE_CODE;

    /**
     * ss0's own token, addressed the same way {@code setup.hurl}'s ss0 sign-key block does
     * ({@code /tokens/0/...}), not ss1's captured hardware-token id.
     */
    private static final String SIGN_KEY_TOKEN_ID = "0";
    private static final String SIGN_KEY_LABEL = "Sign key 4321";
    /** Same DN convention {@code setup.hurl} uses for every key generated on ss0's token, regardless of member. */
    private static final String SIGN_KEY_SERIAL_NUMBER = "DEV/SS0/COM";

    private static final String REGISTERED_STATUS = "REGISTERED";
    private static final String ISSUED_CREDENTIAL_STATUS = "ISSUED";

    private static final String REST_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;
    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";

    private static final Duration REGISTRATION_POLL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration REGISTRATION_POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration PROVISIONING_POLL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration CATALOG_VISIBILITY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CATALOG_VISIBILITY_POLL_INTERVAL = Duration.ofSeconds(10);

    private record AdminSession(Map<String, String> cookies, String xsrfToken) {
    }

    private record GeneratedCsr(String keyId, String csrId) {
    }

    private record ImportedCertificate(String hash, boolean active) {
    }

    /**
     * The outcome of scanning ss0's token for sign material already belonging to the new member:
     * either a certificate ({@code certHash} set), a CSR generated but never signed/imported on a
     * previous, interrupted run ({@code pendingCsrKeyId}/{@code pendingCsrId} set), or neither.
     */
    private record TokenScanResult(String certHash, boolean active, String pendingCsrKeyId, String pendingCsrId) {
        static TokenScanResult none() {
            return new TokenScanResult(null, false, null, null);
        }
    }

    @Test
    @DisplayName("A member onboarded to ss0 at runtime serves an established member-context consumer, no restart")
    void memberOnboardedAtRuntimeServesAMemberContextConsumer(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; runtime member provisioning is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var dspAssertions = new DspNegotiationDbAssertions((DsControlPlaneDbOps) env,
                SS0_ENV, ASSET_ID, CONSUMER_MEMBER_CTX_ID);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = adminBaseUrl(env, CS_ENV);
        var caBaseUrl = caBaseUrl(env);

        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var clientId = when("member DEV:COM:4321's new RuntimeService subsystem is added to ss0 as a local client", () ->
                addLocalClient(ss0BaseUrl, ss0Session));

        and("a CA-signed sign certificate for the new member is provisioned on ss0's token", () ->
                provisionSignCertificate(env, ss0BaseUrl, ss0Session, caBaseUrl));

        and("its registration is submitted from ss0 to the Central Server", () ->
                registerClient(ss0BaseUrl, ss0Session, clientId));

        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        then("the Central Server approves the pending request and ss0 reports the new client as REGISTERED", () ->
                awaitClientRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession, clientId));

        var backendUrl = and("the backend URL of ss0's existing TestService mock1 service is discovered", () ->
                discoverExistingBackendUrl(ss0BaseUrl, ss0Session));

        var serviceDescriptionId = and("a REST service description reusing that backend is added for the new client", () ->
                addRestServiceDescription(ss0BaseUrl, ss0Session, clientId, backendUrl));

        and("the new service description is enabled", () ->
                enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

        and("the established consumer TestService is granted access to the new client's service", () ->
                grantConsumerAccessRights(ss0BaseUrl, ss0Session, clientId, CONSUMER_CLIENT_ID));

        then("the new member's own participant context and membership credential are provisioned, independent of "
                + "which context the transfer below ends up riding, with no restart", () ->
                awaitMemberContextIssued(ss0BaseUrl, ss0Session));

        var response = when(
                "a REST request from the established consumer to the new client's service succeeds via the ss0 proxy, "
                        + "within the catalog cache window",
                () -> awaitCallSucceeds(env));

        then("the response carries the expected POST service message", () ->
                response.body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));

        var wireAgreementId = then(
                "the negotiation completes: a CONSUMER row on the sender member's context and a PROVIDER row on the "
                        + "host context share one wire agreement",
                () -> dspAssertions.awaitNegotiationPair());

        and("exactly one per-context edc_contract_agreement copy exists for each side of that wire agreement", () ->
                dspAssertions.assertPerContextAgreementCopies(wireAgreementId));

        and("the transfer over that agreement succeeds", () ->
                dspAssertions.awaitTransferSucceeded(wireAgreementId));
    }

    private String adminBaseUrl(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.UI, SsStackSetup.Port.UI);
        return "https://%s:%s".formatted(mapping.host(), mapping.port());
    }

    /**
     * The test CA's cert-issuance endpoint is plain HTTP, unlike the admin APIs above, mirroring
     * {@code setup.hurl}'s own {@code http://{{ca_host}}:8888/testca/sign} calls.
     */
    private String caBaseUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CA_ENV, SsStackSetup.CA, SsStackSetup.Port.CA_API);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private AdminSession login(String baseUrl) {
        var response = RestAssuredFactory.given()
                .formParam("username", ADMIN_USERNAME)
                .formParam("password", ADMIN_PASSWORD)
                .post(baseUrl + "/login");
        assertThat(response.getStatusCode()).as("login to %s", baseUrl).isEqualTo(200);
        return new AdminSession(response.getCookies(), response.getCookie("XSRF-TOKEN"));
    }

    private RequestSpecification authed(AdminSession session) {
        return RestAssuredFactory.given()
                .cookies(session.cookies())
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    /**
     * Adds the new local client, tolerating a warm rerun where it already exists: {@code ClientService}
     * rejects a duplicate with {@code ClientAlreadyExistsException}, a {@code ConflictException} mapped
     * to 409. Either way the returned id is {@link #NEW_CLIENT_ID}, deterministic from the member/
     * subsystem constants, so no response-body lookup is needed on either status.
     */
    private String addLocalClient(String ss0BaseUrl, AdminSession ss0) {
        var body = """
                {
                  "ignore_warnings": true,
                  "client": {
                    "member_class": "%s",
                    "member_code": "%s",
                    "subsystem_code": "%s",
                    "connection_type": "HTTP"
                  }
                }
                """.formatted(NEW_MEMBER_CLASS, NEW_MEMBER_CODE, NEW_SUBSYSTEM_CODE);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients");
        assertThat(response.getStatusCode())
                .as("add local client for the new member (201 first run, 409 if it already exists on a warm rerun)")
                .isIn(201, 409);
        return NEW_CLIENT_ID;
    }

    /**
     * Provisions a CA-signed sign certificate for the new member on ss0's token, mirroring
     * {@code setup.hurl}'s own ss0 sign-key block (keys-with-csrs -&gt; fetch CSR PEM -&gt; test CA
     * {@code /testca/sign} -&gt; import) for {@link #NEW_MEMBER_CTX_ID} instead of ss0's own owner
     * member. The admin API rejects a SIGNING CSR for a member id that is not yet a local client
     * (see {@code TokenCertificateService.generateCertRequest}), which is why this runs after
     * {@link #addLocalClient}, not at environment bring-up.
     *
     * <p>Tolerant of a warm rerun at every stage: an existing certificate for the member is reused
     * outright; an existing CSR with no certificate yet (a previous run that crashed mid-flow) is
     * signed and imported without generating a new key. A certificate the signer could not activate
     * inline — the import call verifies the OCSP response synchronously and only activates on
     * success, so a transient OCSP hiccup would otherwise leave it inactive — is activated explicitly
     * through the same endpoint an administrator would use, rather than waiting on the signer's
     * periodic OCSP refresh (whose default interval does not fit inside this scenario's
     * provisioning-status timeout). An inactive sign certificate cannot issue the member's dataspace
     * membership credential.
     */
    private void provisionSignCertificate(E2eEnvironment env, String ss0BaseUrl, AdminSession ss0, String caBaseUrl) {
        var existing = scanTokenForNewMemberSignMaterial(ss0BaseUrl, ss0);
        String certHash = existing.certHash();
        boolean active = existing.active();

        if (certHash == null) {
            var pending = existing.pendingCsrKeyId() != null
                    ? new GeneratedCsr(existing.pendingCsrKeyId(), existing.pendingCsrId())
                    : generateSignCsr(ss0BaseUrl, ss0, env.securityServerAddress(SS0_ENV));
            var csrBytes = fetchCsrBytes(ss0BaseUrl, ss0, pending.keyId(), pending.csrId());
            var certBytes = signWithTestCa(caBaseUrl, csrBytes);
            var imported = importSignCertificate(ss0BaseUrl, ss0, certBytes);
            certHash = imported.hash();
            active = imported.active();
        }

        if (!active) {
            activateSignCertificate(ss0BaseUrl, ss0, certHash);
        }
    }

    /**
     * Looks for sign material already belonging to {@link #NEW_MEMBER_CTX_ID} on ss0's token: a
     * {@code Key}'s {@code certificates}/{@code certificate_signing_requests} both carry an
     * {@code owner_id} field identifying the client they were issued for, so a warm rerun is
     * detected without needing a deterministic key label (key generation has none — see
     * {@code KeyService.addKey} — so re-running the generate step unconditionally would accumulate a
     * new key on every rerun instead of reusing the existing one).
     */
    private TokenScanResult scanTokenForNewMemberSignMaterial(String ss0BaseUrl, AdminSession ss0) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID);
        assertThat(response.getStatusCode()).as("look up ss0's token %s", SIGN_KEY_TOKEN_ID).isEqualTo(200);

        for (Map<String, Object> key : response.jsonPath().getList("keys", Map.class)) {
            for (Map<String, Object> cert : (List<Map<String, Object>>) key.get("certificates")) {
                if (NEW_MEMBER_CTX_ID.equals(cert.get("owner_id"))) {
                    var certificateDetails = (Map<String, Object>) cert.get("certificate_details");
                    return new TokenScanResult(
                            (String) certificateDetails.get("hash"), Boolean.TRUE.equals(cert.get("active")), null, null);
                }
            }
            for (Map<String, Object> csr : (List<Map<String, Object>>) key.get("certificate_signing_requests")) {
                if (NEW_MEMBER_CTX_ID.equals(csr.get("owner_id"))) {
                    return new TokenScanResult(null, false, (String) key.get("id"), (String) csr.get("id"));
                }
            }
        }
        return TokenScanResult.none();
    }

    private String fetchCaName(String ss0BaseUrl, AdminSession ss0) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/certificate-authorities");
        assertThat(response.getStatusCode()).as("look up ss0's approved certificate authorities").isEqualTo(200);
        return response.jsonPath().getString("[0].name");
    }

    /**
     * Generates the new member's SIGNING key/CSR on ss0's token, same DN field conventions as
     * {@code setup.hurl}'s ss0 sign-key block for {@code DEV:COM:1234} (CN/C/O/subjectAltName/
     * serialNumber), just for {@link #NEW_MEMBER_CTX_ID}'s identity instead.
     */
    private GeneratedCsr generateSignCsr(String ss0BaseUrl, AdminSession ss0, String ss0SecurityServerAddress) {
        var caName = fetchCaName(ss0BaseUrl, ss0);
        var body = """
                {
                  "key_label": "%s",
                  "csr_generate_request": {
                    "key_usage_type": "SIGNING",
                    "ca_name": "%s",
                    "csr_format": "DER",
                    "member_id": "%s",
                    "subject_field_values": {
                      "CN": "%s",
                      "C": "FI",
                      "O": "Test client",
                      "subjectAltName": "%s",
                      "serialNumber": "%s"
                    }
                  }
                }
                """.formatted(SIGN_KEY_LABEL, caName, NEW_MEMBER_CTX_ID, NEW_MEMBER_CODE,
                ss0SecurityServerAddress, SIGN_KEY_SERIAL_NUMBER);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/tokens/" + SIGN_KEY_TOKEN_ID + "/keys-with-csrs");
        // Same quirk as setup.hurl's ss0 sign-key block: the API returns 200, not the 201 its
        // own definition promises.
        assertThat(response.getStatusCode())
                .as("generate a SIGNING CSR for %s on ss0's token", NEW_MEMBER_CTX_ID)
                .isEqualTo(200);
        return new GeneratedCsr(response.jsonPath().getString("key.id"), response.jsonPath().getString("csr_id"));
    }

    private byte[] fetchCsrBytes(String ss0BaseUrl, AdminSession ss0, String keyId, String csrId) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/keys/" + keyId + "/csrs/" + csrId + "?csr_format=PEM");
        assertThat(response.getStatusCode()).as("fetch CSR %s PEM for key %s", csrId, keyId).isEqualTo(200);
        return response.getBody().asByteArray();
    }

    /**
     * Same test CA the environment's own bring-up uses (the CA needs a filename on the CSR part,
     * which {@code RestAssuredFactory}'s multipart support handles directly, unlike
     * {@code setup.hurl}, which has to hand-roll the multipart body for this same reason).
     */
    private byte[] signWithTestCa(String caBaseUrl, byte[] csrBytes) {
        var response = RestAssuredFactory.given()
                .multiPart("certreq", "sign.csr.pem", csrBytes, "application/octet-stream")
                .multiPart("type", "sign")
                .post(caBaseUrl + "/testca/sign");
        assertThat(response.getStatusCode()).as("sign the new member's sign CSR with the test CA").isEqualTo(200);
        return response.getBody().asByteArray();
    }

    /**
     * Imports the CA-signed certificate, tolerating a warm rerun where it was already imported by an
     * earlier attempt at this same run's key/CSR: {@code TokenCertificateService.importCertificate}
     * rejects re-importing an identical, already-saved certificate with
     * {@code CertificateAlreadyExistsException} (an {@code XrdRuntimeException} tagged
     * {@code CERT_EXISTS}), a {@code ConflictException} mapped to 409. The 409 body carries no
     * identifiers, so the existing certificate is recovered the same way {@link #provisionSignCertificate}
     * detects one from a previous run entirely.
     */
    private ImportedCertificate importSignCertificate(String ss0BaseUrl, AdminSession ss0, byte[] certBytes) {
        var response = authed(ss0)
                .multiPart("certificate", "sign_key_cert.pem", certBytes, "application/octet-stream")
                .post(ss0BaseUrl + "/api/v1/token-certificates");
        if (response.getStatusCode() == 409) {
            log.info("Sign certificate for {} already imported on a warm rerun; looking it up instead of importing again",
                    NEW_MEMBER_CTX_ID);
            var existing = scanTokenForNewMemberSignMaterial(ss0BaseUrl, ss0);
            assertThat(existing.certHash())
                    .as("an existing sign certificate for %s after a 409 on import", NEW_MEMBER_CTX_ID)
                    .isNotBlank();
            return new ImportedCertificate(existing.certHash(), existing.active());
        }
        assertThat(response.getStatusCode()).as("import the new member's sign certificate").isEqualTo(201);
        return new ImportedCertificate(response.jsonPath().getString("certificate_details.hash"), response.jsonPath().getBoolean("active"));
    }

    /**
     * Explicit activation trigger: {@code ActivateCertReqHandler} re-runs the same synchronous OCSP
     * verification the import call performs, so this recovers a certificate the import left inactive
     * without waiting on the signer's periodic OCSP refresh job. Tolerates 409: {@code PossibleActionEnum
     * .ACTIVATE} is only offered for a currently-inactive certificate, so a certificate the import call
     * already activated makes this a no-op conflict rather than an error.
     */
    private void activateSignCertificate(String ss0BaseUrl, AdminSession ss0, String certHash) {
        var response = authed(ss0).put(ss0BaseUrl + "/api/v1/token-certificates/" + certHash + "/activate");
        assertThat(response.getStatusCode())
                .as("activate the new member's sign certificate %s (204 first attempt, 409 if already active)", certHash)
                .isIn(204, 409);
    }

    /**
     * Submits the client registration request, tolerating a warm rerun where the client is already past
     * {@code SAVED} status: {@code ClientService.registerClient} rejects that with
     * {@code ActionNotPossibleException}, a {@code ConflictException} mapped to 409. Either status leaves
     * {@link #awaitClientRegistered} to confirm the actual outcome.
     */
    private void registerClient(String ss0BaseUrl, AdminSession ss0, String clientId) {
        var response = authed(ss0).put(ss0BaseUrl + "/api/v1/clients/" + clientId + "/register");
        assertThat(response.getStatusCode())
                .as("submit client registration for %s (204 first run, 409 if it is no longer SAVED on a warm rerun)", clientId)
                .isIn(204, 409);
    }

    /**
     * Waits until ss0 reports the new client as REGISTERED, approving the Central Server's pending
     * request within the same loop. Environments differ: k8s auto-approves, so the request never
     * surfaces as WAITING; LXD requires an explicit approval and the request may take a moment to
     * appear as WAITING, then several globalconf-distribution cycles to propagate back to ss0 as
     * REGISTERED. Approving on each tick — rather than once, up front — covers both, and tolerates
     * the request not being WAITING yet at the first look.
     */
    private void awaitClientRegistered(String ss0BaseUrl, AdminSession ss0, String csBaseUrl, AdminSession cs, String clientId) {
        Awaitility.await()
                .pollInterval(REGISTRATION_POLL_INTERVAL)
                .timeout(REGISTRATION_POLL_TIMEOUT)
                .untilAsserted(() -> {
                    approvePendingRegistrationIfPresent(csBaseUrl, cs, clientId);
                    var response = authed(ss0).get(ss0BaseUrl + "/api/v1/clients/" + clientId);
                    assertThat(response.getStatusCode()).as("GET /clients/%s", clientId).isEqualTo(200);
                    assertThat(response.jsonPath().getString("status"))
                            .as("status of client %s", clientId)
                            .isEqualTo(REGISTERED_STATUS);
                });
    }

    /**
     * Approves this test's own WAITING client registration request if one is present — matched by
     * request type and the client's encoded id, so a warm shared environment's unrelated pending
     * requests are never touched. No match means either the environment auto-approved it or it has
     * not surfaced yet; both are handled by the surrounding registration-status poll, so this
     * returns quietly rather than asserting.
     */
    private void approvePendingRegistrationIfPresent(String csBaseUrl, AdminSession cs, String clientId) {
        var response = authed(cs).get(csBaseUrl + "/api/v1/management-requests?sort=id&desc=true&status=WAITING");
        assertThat(response.getStatusCode()).as("list WAITING management requests").isEqualTo(200);

        Integer requestId = response.jsonPath().get(
                "items.find { it.type == 'CLIENT_REGISTRATION_REQUEST' && it.client_id?.encoded_id == '%s' }?.id"
                        .formatted(clientId));
        if (requestId == null) {
            return;
        }

        var approval = authed(cs).post(csBaseUrl + "/api/v1/management-requests/" + requestId + "/approval");
        assertThat(approval.getStatusCode())
                .as("approve client registration request %s (409 if a prior tick already approved it)", requestId)
                .isIn(200, 409);
    }

    private String discoverExistingBackendUrl(String ss0BaseUrl, AdminSession ss0) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/services/" + EXISTING_SERVICE_ID);
        assertThat(response.getStatusCode()).as("look up ss0's existing TestService mock1 service").isEqualTo(200);
        return response.jsonPath().getString("url");
    }

    /**
     * Adds the REST service description, tolerating a warm rerun where one with {@link #REST_SERVICE_CODE}
     * already exists on this client: {@code ServiceDescriptionService} rejects the duplicate service code
     * with {@code ServiceCodeAlreadyExistsException} (checked ahead of the URL-duplicate case, so a rerun
     * always hits this one), a {@code ConflictException} mapped to 409. Unlike the client-id case, the
     * service description's id is not deterministic, so a 409 falls back to
     * {@link #findExistingServiceDescriptionId} instead of returning one.
     */
    private String addRestServiceDescription(String ss0BaseUrl, AdminSession ss0, String clientId, String backendUrl) {
        var body = """
                {
                  "url": "%s",
                  "type": "REST",
                  "rest_service_code": "%s"
                }
                """.formatted(backendUrl, REST_SERVICE_CODE);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        if (response.getStatusCode() == 409) {
            log.info("REST service description '{}' already exists for {}; looking up its id instead of adding it again",
                    REST_SERVICE_CODE, clientId);
            return findExistingServiceDescriptionId(ss0BaseUrl, ss0, clientId, backendUrl);
        }
        assertThat(response.getStatusCode()).as("add REST service description for %s", clientId).isEqualTo(201);
        return response.jsonPath().getString("id");
    }

    /**
     * Recovers the service description id a 409 from {@link #addRestServiceDescription} could not return:
     * there is no get-by-url endpoint, so the client's service descriptions are listed and matched by the
     * backend URL this scenario always uses.
     */
    private String findExistingServiceDescriptionId(String ss0BaseUrl, AdminSession ss0, String clientId, String backendUrl) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertThat(response.getStatusCode()).as("list service descriptions for %s", clientId).isEqualTo(200);

        var id = response.jsonPath().getString("find { it.url == '" + backendUrl + "' }.id");
        assertThat(id)
                .as("an existing service description for %s with backend url %s", clientId, backendUrl)
                .isNotBlank();
        return id;
    }

    /**
     * Enables the service description. Unlike the earlier steps, this one needs no rerun tolerance:
     * {@code ServiceDescriptionService.toggleServices} has no already-enabled check and unconditionally
     * flips the disabled flag, so calling it again on an already-enabled description is a plain,
     * idempotent 200.
     */
    private void enableServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId) {
        var response = authed(ss0).put(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/enable");
        assertThat(response.getStatusCode()).as("enable service description %s", serviceDescriptionId).isEqualTo(200);
    }

    /**
     * Grants the established consumer ({@link #CONSUMER_CLIENT_ID}) access to the new provider
     * client's service — the new client cannot legitimately call its own service (see the class doc),
     * so this is an access right onto a different subject than the environment bring-up's own
     * {@code TestService} self-access grant. Tolerates 409 so this stays safe to run against a
     * substrate where the grant already exists.
     */
    private void grantConsumerAccessRights(String ss0BaseUrl, AdminSession ss0, String providerClientId, String consumerClientId) {
        var body = """
                {
                  "items": [
                    { "service_code": "%s" }
                  ]
                }
                """.formatted(REST_SERVICE_CODE);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients/" + providerClientId + "/service-clients/" + consumerClientId + "/access-rights");
        assertThat(response.getStatusCode())
                .as("grant %s access to %s's service", consumerClientId, providerClientId)
                .isIn(201, 409);
    }

    /**
     * Polls the same read-only status endpoint the environment's own bring-up gates on for the HOST
     * context (see {@code core/development/hurl/scenarios/setup.hurl}'s "DSP readiness gates"), here for
     * the new member's context instead. This is the assertion that distinguishes a missing
     * distribution/data-plane record (the catalog entry would come back with no transfer format, and the
     * scenario would fail here, before any negotiation is attempted) from a transfer-level failure
     * (which only the later negotiation/agreement/transfer assertions can catch).
     */
    private void awaitMemberContextIssued(String ss0BaseUrl, AdminSession ss0) {
        try {
            Awaitility.await()
                    .pollInterval(PROVISIONING_POLL_INTERVAL)
                    .timeout(PROVISIONING_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/dataspace/provisioning-status");
                        if (response.getStatusCode() != 200) {
                            return false;
                        }
                        var credentialStatus = response.jsonPath().getString(
                                "participant_contexts.find { it.kind == 'MEMBER' && it.participant_id == '"
                                        + NEW_MEMBER_CTX_ID + "' }.credential_status");
                        return ISSUED_CREDENTIAL_STATUS.equals(credentialStatus);
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    ("Timed out waiting for participant context '%s' to reach an ISSUED membership credential; "
                            + "without this, the new member's catalog entry has no usable transfer format and any "
                            + "negotiation attempt fails at offer selection, before negotiation begins")
                            .formatted(NEW_MEMBER_CTX_ID), e);
        }
    }

    /**
     * The provider control plane serves the catalog from an enumeration cache with a bounded TTL
     * (default 60 s), and service-description changes deliberately carry no invalidation signal — a
     * freshly added service is contractually visible only after the cache expires. Retries the
     * consumer's call across that window instead of asserting on the first attempt.
     */
    private ValidatableResponse awaitCallSucceeds(E2eEnvironment env) {
        var last = new AtomicReference<ValidatableResponse>();
        Awaitility.await()
                .pollInterval(CATALOG_VISIBILITY_POLL_INTERVAL)
                .timeout(CATALOG_VISIBILITY_TIMEOUT)
                .untilAsserted(() -> {
                    var response = sendConsumerCallRequest(env);
                    last.set(response);
                    assertThat(response.extract().statusCode())
                            .as("consumer call via ss0 proxy (catalog visibility is bounded by the enumeration cache TTL)")
                            .isEqualTo(200);
                });
        return last.get();
    }

    /**
     * Sends the REST request as {@link #CONSUMER_CLIENT_ID} — ss0's own {@code TestService}, not the new
     * client — to the new client's service. See the class doc's "Why the consumer cannot be the new
     * client calling itself".
     */
    private ValidatableResponse sendConsumerCallRequest(E2eEnvironment env) {
        var mapping = env.getContainerMapping(SS0_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body(REST_REQUEST_BODY)
                .header("Content-Type", "application/json")
                .header("x-road-client", CONSUMER_X_ROAD_ID)
                .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), NEW_SERVICE_PATH))
                .then();
    }

}
