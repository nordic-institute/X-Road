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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.e2e.container.AuxStackSetup;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * End-to-end regression guard for the provider-side catalog / data-plane member-context story
 * (XRDDEV-3312): a member added to ss0 <b>at runtime</b> becomes a fully functioning dataspace
 * provider without a proxy or control-plane restart, both over the legacy host-context path and
 * over its own participant context.
 *
 * <p>ss0 already hosts member {@code DEV:COM:1234} (subsystems {@code MANAGEMENT}/{@code TestService}/
 * {@code TestSaved}, see {@code setup.hurl}); this scenario adds a second, distinct member
 * ({@code DEV:COM:5678}) with its own subsystem-scoped service, proving multi-member hosting.
 *
 * <p>Consumer-side member-context discoverability is deferred (XRDADR-41): the deprecated static
 * counter-party map the real proxy uses only ever resolves the legacy host context. The
 * member-context negotiation is therefore driven directly against the control plane's internal
 * {@code AssetAccessService} gRPC endpoint (the same one the proxy calls in production) with the
 * new member's context supplied explicitly, standing in for the discoverability the consumer side
 * will gain later. That endpoint requires mutual TLS; a short-lived client certificate is minted
 * from the same OpenBao PKI mount ({@code xrd-pki}, role {@code xrd-internal}) the control plane
 * itself uses, via the same {@code XROAD_SECRET_STORE_TOKEN} already configured for this suite.
 *
 * <p><b>Verification note:</b> {@link #DS_CONTROL_PLANE_TLS_AUTHORITY} assumes the control plane's
 * server TLS certificate carries common name {@code ds-control-plane} (derived from its
 * unprefixed {@code XROAD_HOST} env var, shared by every stack). If the RPC handshake in
 * {@link #buildAssetAccessChannel} fails on a hostname mismatch, adjust that constant to match the
 * certificate's actual subject.
 */
@DisplayName("DS - runtime-added member completes a member-context transfer")
@Order(300)
@SuppressWarnings("checkstyle:magicnumber")
class DsRuntimeMemberTransferTest extends E2eTest {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";

    private static final String INSTANCE = "DEV";

    private static final String NEW_MEMBER_CLASS = "COM";
    private static final String NEW_MEMBER_CODE = "5678";
    private static final String NEW_MEMBER_NAME = "Test provider two";
    private static final String NEW_SUBSYSTEM_CODE = "TestService2";
    private static final String NEW_SERVICE_CODE = "mock1";

    private static final String CONSUMER_CLIENT_ID = "DEV:COM:4321:TestClient";
    private static final String CONSUMER_CLIENT_HEADER = "DEV/COM/4321/TestClient";

    /** Shared wiremock backend already used by member 1234's own {@code mock1} REST service. */
    private static final String IS_REST_URL = "http://isrest:8080/integration/mock_1";

    private static final String SS1_HOST_PARTICIPANT_CONTEXT_ID = "xrd-ss1";
    private static final String SS0_DS_CONTROL_PLANE_ALIAS = "ss0-ds-control-plane";
    private static final String SS0_IDENTITY_HUB_HOST = "ss0-ds-identity-hub:7183";
    private static final String DS_CONTROL_PLANE_TLS_AUTHORITY = "ds-control-plane";
    private static final String DSP_PROFILE_ID = "http-dsp-profile-2025-1";

    private static final String VAULT_CLIENT_TOKEN = "system-test-xroad-token";
    private static final String PKI_ISSUANCE_ROLE = "xrd-internal";

    @Test
    @DisplayName("Runtime-added member on ss0 completes a transfer over its own context with zero restarts")
    void runtimeAddedMemberCompletesTransferOverOwnContextWithZeroRestarts(E2eEnvironment env, ContainerLifecycleOps containerOps) {
        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var containerTimestampsBefore = when("container start timestamps are captured before the scenario", () ->
                captureContainerTimestamps(containerOps));

        and("a second, distinct member with a subsystem-scoped service is added to ss0 at runtime via the admin API", () ->
                provisionNewMemberOnSs0(env));

        then("the provisioning worker creates the member's participant context and issues its membership credential", () ->
                awaitNewMemberProvisioned(env));

        and("the legacy host-context path still serves the new member's service unchanged", () ->
                assertLegacyHostContextPathWorks(env));

        and("catalog, negotiation and transfer against the new member's own context succeed with explicit counter-party targeting", () ->
                assertMemberContextTransferSucceeds(env));

        then("no proxy or control-plane container restarted during the scenario", () ->
                assertNoRestartOccurred(containerTimestampsBefore, containerOps));
    }

    // -- runtime provisioning --------------------------------------------------------------------

    private void provisionNewMemberOnSs0(E2eEnvironment env) {
        var cs = login(baseUrl(env, "aux", AuxStackSetup.CS, AuxStackSetup.Port.UI));
        addMemberOnCentralServer(cs);

        var ss0 = login(baseUrl(env, "ss0", SsStackSetup.UI, SsStackSetup.Port.UI));
        // Adding the local client with ignore_warnings=true (below) does not require the new CS member
        // to already be visible in ss0's synced global configuration — ClientService#addLocalClientEntity
        // only turns that into a suppressible warning (WARNING_UNREGISTERED_MEMBER), never a hard failure.
        // No globalconf-freshness wait is needed here; verified by reading that check rather than assumed.
        var clientId = addClientOnSs0(ss0);
        // Seeded members (1234/4321) get their signing key/certificate from the softtoken seed baked
        // into the stack images at build time. A runtime-added member has none — register fails outright
        // without one ("could not find any certificates for member"), and the DSP provisioning worker's
        // membership-claim signing needs one later too. Provision it exactly the way setup.hurl
        // provisions the seeded members' own sign keys: CSR against ss0's softtoken (already logged in
        // at stack boot, no re-login needed), signed by the stack's testca, then imported back.
        provisionSignCertificateForNewMember(env, ss0);
        registerAndApproveClient(ss0, cs, clientId);
        addAndEnableRestService(ss0, clientId);
    }

    private void provisionSignCertificateForNewMember(E2eEnvironment env, AdminSession ss0) {
        var caName = fetchCaName(ss0);
        var csr = generateSignKeyCsr(ss0, caName);
        var csrPem = fetchCsrPem(ss0, csr.keyId(), csr.csrId());
        var certPem = signWithTestCa(env, csrPem);
        var hash = importSignCertificate(ss0, certPem);
        // Sign-cert import auto-activates (unlike auth certs) and verifies OCSP synchronously as part of
        // activation (see TokenCertificateService#importCertificate's activate=true path and the
        // ocsp-verify-before-activation error it surfaces) — so this should resolve near-instantly. Kept
        // as a bounded wait rather than a one-shot check because register's own retry (below) depends on
        // it and a same-cycle async settle is cheap insurance against a genuine gap.
        awaitOcspGood(ss0, hash);
    }

    private String fetchCaName(AdminSession ss0) {
        var response = ss0.authed().get(ss0.baseUrl() + "/api/v1/certificate-authorities");
        assertStatus(response, 200, "list certificate authorities on ss0");
        return response.jsonPath().getString("[0].name");
    }

    private GeneratedCsr generateSignKeyCsr(AdminSession ss0, String caName) {
        var body = """
                {"key_label": "New member sign key", "csr_generate_request": {
                  "key_usage_type": "SIGNING", "ca_name": "%s", "csr_format": "DER",
                  "member_id": "%s:%s:%s",
                  "subject_field_values": {"CN": "%s", "C": "FI", "O": "%s",
                    "subjectAltName": "localhost", "serialNumber": "DEV/SS0/COM"}
                }}
                """.formatted(caName, INSTANCE, NEW_MEMBER_CLASS, NEW_MEMBER_CODE, NEW_MEMBER_CODE, NEW_MEMBER_NAME);
        var response = ss0.authed().body(body).post(ss0.baseUrl() + "/api/v1/tokens/0/keys-with-csrs");
        // Documented as 201; setup.hurl notes the admin API actually returns 200 for this call today.
        assertStatus(response, 200, "generate sign key CSR for new member on ss0");
        return new GeneratedCsr(response.jsonPath().getString("key.id"), response.jsonPath().getString("csr_id"));
    }

    private String fetchCsrPem(AdminSession ss0, String keyId, String csrId) {
        var response = ss0.authed().get(ss0.baseUrl() + "/api/v1/keys/" + keyId + "/csrs/" + csrId + "?csr_format=PEM");
        assertStatus(response, 200, "fetch new member's sign key CSR as PEM");
        return response.getBody().asString();
    }

    private String signWithTestCa(E2eEnvironment env, String csrPem) {
        var mapping = env.getContainerMapping("aux", AuxStackSetup.TESTCA, AuxStackSetup.Port.CA);
        var response = RestAssuredFactory.givenSilent()
                .multiPart("type", "sign")
                .multiPart("certreq", "sign.csr.pem", csrPem.getBytes(StandardCharsets.UTF_8))
                .post("http://%s:%s/testca/sign".formatted(mapping.host(), mapping.port()));
        assertStatus(response, 200, "sign new member's CSR with the stack's test CA");
        return response.getBody().asString();
    }

    private String importSignCertificate(AdminSession ss0, String certPem) {
        var response = ss0.authedMultipart()
                .multiPart("certificate", "sign.cert.pem", certPem.getBytes(StandardCharsets.UTF_8))
                .post(ss0.baseUrl() + "/api/v1/token-certificates");
        assertStatus(response, 201, "import new member's signed certificate on ss0");
        return response.jsonPath().getString("certificate_details.hash");
    }

    private void awaitOcspGood(AdminSession ss0, String hash) {
        var last = new AtomicReference<Response>();
        try {
            Awaitility.await()
                    .atMost(Duration.ofMinutes(2))
                    .pollInterval(Duration.ofSeconds(5))
                    .until(() -> {
                        var response = ss0.authed().get(ss0.baseUrl() + "/api/v1/token-certificates/" + hash);
                        last.set(response);
                        return response.getStatusCode() == 200
                                && "OCSP_RESPONSE_GOOD".equals(response.jsonPath().getString("ocsp_status"));
                    });
        } catch (ConditionTimeoutException e) {
            var response = last.get();
            throw new AssertionError(
                    "new member's sign certificate never reached OCSP_RESPONSE_GOOD. Last response body: %s"
                            .formatted(response != null ? response.getBody().asString() : "<none>"), e);
        }
    }

    private record GeneratedCsr(String keyId, String csrId) {
    }

    private void addMemberOnCentralServer(AdminSession cs) {
        var body = """
                {"member_id": {"member_class": "%s", "member_code": "%s"}, "member_name": "%s"}
                """.formatted(NEW_MEMBER_CLASS, NEW_MEMBER_CODE, NEW_MEMBER_NAME);
        assertStatus(cs.authed().body(body).post(cs.baseUrl() + "/api/v1/members"), 201, "add member on Central Server");
    }

    private String addClientOnSs0(AdminSession ss0) {
        var body = """
                {"ignore_warnings": true, "client": {"member_class": "%s", "member_code": "%s",
                "subsystem_code": "%s", "connection_type": "HTTP"}}
                """.formatted(NEW_MEMBER_CLASS, NEW_MEMBER_CODE, NEW_SUBSYSTEM_CODE);
        var response = ss0.authed().body(body).post(ss0.baseUrl() + "/api/v1/clients");
        assertStatus(response, 201, "add new member's client on ss0");
        return response.jsonPath().getString("id");
    }

    private void registerAndApproveClient(AdminSession ss0, AdminSession cs, String clientId) {
        // Bounded retry as a safety net for any transient failure at this step (the confirmed root cause
        // of the original 500 here — signer having no certificate yet for the member — is now fixed by
        // provisionSignCertificateForNewMember above) — capturing the last response so a genuine, unrelated
        // failure is diagnosable rather than reported as a bare status-code mismatch.
        awaitStatus("register new member's client on ss0", Duration.ofMinutes(2), 204,
                () -> ss0.authed().put(ss0.baseUrl() + "/api/v1/clients/" + clientId + "/register"));
        approveLatestManagementRequest(cs);
    }

    private void approveLatestManagementRequest(AdminSession cs) {
        var pendingRequests = cs.authed().get(cs.baseUrl() + "/api/v1/management-requests?sort=id&desc=true&status=WAITING");
        assertStatus(pendingRequests, 200, "list pending management requests on Central Server");
        var requestId = pendingRequests.jsonPath().getString("items[0].id");

        var approval = cs.authed().post(cs.baseUrl() + "/api/v1/management-requests/" + requestId + "/approval");
        assertStatus(approval, 200, "approve management request " + requestId);
    }

    private void addAndEnableRestService(AdminSession ss0, String clientId) {
        var descriptionBody = """
                {"url": "%s", "type": "REST", "rest_service_code": "%s"}
                """.formatted(IS_REST_URL, NEW_SERVICE_CODE);
        var descriptionResponse = ss0.authed().body(descriptionBody)
                .post(ss0.baseUrl() + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertStatus(descriptionResponse, 201, "add REST service description for new member's client");
        var serviceDescriptionId = descriptionResponse.jsonPath().getString("id");

        var enableResponse = ss0.authed().put(ss0.baseUrl() + "/api/v1/service-descriptions/" + serviceDescriptionId + "/enable");
        assertThat(enableResponse.getStatusCode())
                .as("enable new member's service description (response body: %s)", enableResponse.getBody().asString())
                .isBetween(200, 299);

        var accessBody = """
                {"items": [{"service_code": "%s"}]}
                """.formatted(NEW_SERVICE_CODE);
        var accessResponse = ss0.authed().body(accessBody)
                .post(ss0.baseUrl() + "/api/v1/clients/" + clientId + "/service-clients/" + CONSUMER_CLIENT_ID + "/access-rights");
        assertStatus(accessResponse, 201, "grant consumer access rights to new member's service");
    }

    private void assertStatus(Response response, int expectedStatus, String context) {
        assertThat(response.getStatusCode())
                .as("%s (response body: %s)", context, response.getBody().asString())
                .isEqualTo(expectedStatus);
    }

    /**
     * Retries {@code call} until it returns {@code expectedStatus} or {@code timeout} elapses, surfacing
     * the last response's body on failure instead of a bare status-code mismatch.
     */
    private Response awaitStatus(String context, Duration timeout, int expectedStatus, Supplier<Response> call) {
        var last = new AtomicReference<Response>();
        try {
            Awaitility.await()
                    .atMost(timeout)
                    .pollInterval(Duration.ofSeconds(5))
                    .until(() -> {
                        var response = call.get();
                        last.set(response);
                        return response.getStatusCode() == expectedStatus;
                    });
        } catch (ConditionTimeoutException e) {
            var response = last.get();
            throw new AssertionError("%s: expected status %d but last attempt returned %d after retrying for %s. Response body: %s"
                    .formatted(context, expectedStatus, response.getStatusCode(), timeout, response.getBody().asString()), e);
        }
        return last.get();
    }

    // -- provisioning wait ------------------------------------------------------------------------

    private void awaitNewMemberProvisioned(E2eEnvironment env) {
        var ss0 = login(baseUrl(env, "ss0", SsStackSetup.UI, SsStackSetup.Port.UI));
        var newMemberCtxId = newMemberCtxId();

        Awaitility.await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofSeconds(5))
                .ignoreExceptions()
                .until(() -> newMemberContextIssued(ss0, newMemberCtxId));
    }

    private boolean newMemberContextIssued(AdminSession ss0, String newMemberCtxId) {
        List<Map<String, Object>> contexts = ss0.authed()
                .get(ss0.baseUrl() + "/api/v1/dataspace/provisioning-status")
                .then().statusCode(200).extract().jsonPath().getList("participant_contexts");
        return contexts.stream().anyMatch(ctx ->
                "MEMBER".equals(ctx.get("kind"))
                        && newMemberCtxId.equals(ctx.get("participant_id"))
                        && "ISSUED".equals(ctx.get("credential_status")));
    }

    // -- legacy host-context regression check ------------------------------------------------------

    private void assertLegacyHostContextPathWorks(E2eEnvironment env) {
        var mapping = env.getContainerMapping("ss1", SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var response = RestAssuredFactory.given()
                .body("{\"data\": 1.0, \"service\": \"random\"}")
                .header("Content-Type", "application/json")
                .header("x-road-client", CONSUMER_CLIENT_HEADER)
                .post("http://%s:%s/r1/%s/%s/%s/%s/%s".formatted(mapping.host(), mapping.port(),
                        INSTANCE, NEW_MEMBER_CLASS, NEW_MEMBER_CODE, NEW_SUBSYSTEM_CODE, NEW_SERVICE_CODE))
                .then();
        response.statusCode(200).body("message", equalTo("Hello, world from POST service!"));
    }

    // -- member-context negotiation + transfer, explicit counter-party targeting -------------------

    private void assertMemberContextTransferSucceeds(E2eEnvironment env) {
        var cert = issueClientCertificate(env, "ss1");
        var channel = buildAssetAccessChannel(env, "ss1", cert);
        try {
            var newMember = ClientId.Conf.create(INSTANCE, NEW_MEMBER_CLASS, NEW_MEMBER_CODE);
            var counterPartyAddress = "https://%s:8183/api/dsp/%s/%s"
                    .formatted(SS0_DS_CONTROL_PLANE_ALIAS, newMemberCtxId(), DSP_PROFILE_ID);
            var assetId = ServiceId.Conf.create(INSTANCE, NEW_MEMBER_CLASS, NEW_MEMBER_CODE, NEW_SUBSYSTEM_CODE, NEW_SERVICE_CODE)
                    .asEncodedId();

            var request = AcquireAssetAccessReq.newBuilder()
                    .setParticipantContextId(SS1_HOST_PARTICIPANT_CONTEXT_ID)
                    .setAssetId(assetId)
                    .setCounterPartyId(ParticipantIdentifierScheme.memberDid(newMember, SS0_IDENTITY_HUB_HOST))
                    .setCounterPartyAddress(counterPartyAddress)
                    .setProtocol(DSP_PROFILE_ID)
                    .build();

            var stub = AssetAccessServiceGrpc.newBlockingStub(channel).withDeadlineAfter(90, TimeUnit.SECONDS);
            var response = stub.acquire(request);

            assertThat(response.getEndpoint())
                    .as("data-plane endpoint acquired for the new member's context via explicit counter-party targeting")
                    .isNotBlank();
        } finally {
            channel.shutdownNow();
        }
    }

    private String newMemberCtxId() {
        return ParticipantIdentifierScheme.memberCtxId(ClientId.Conf.create(INSTANCE, NEW_MEMBER_CLASS, NEW_MEMBER_CODE));
    }

    private VaultLeafCertificate issueClientCertificate(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.OPENBAO, SsStackSetup.Port.OPENBAO);
        var json = RestAssuredFactory.givenSilent()
                .header("X-Vault-Token", VAULT_CLIENT_TOKEN)
                .header("Content-Type", "application/json")
                .body("""
                        {"common_name": "e2e-ds-test-client", "ttl": "5m", "private_key_format": "pkcs8"}
                        """)
                .post("http://%s:%s/v1/xrd-pki/issue/%s".formatted(mapping.host(), mapping.port(), PKI_ISSUANCE_ROLE))
                .then().statusCode(200)
                .extract().jsonPath();

        var certificate = json.getString("data.certificate");
        var issuingCa = json.getString("data.issuing_ca");
        var privateKey = json.getString("data.private_key");
        return new VaultLeafCertificate(certificate + "\n" + issuingCa, privateKey, issuingCa);
    }

    @SneakyThrows
    private ManagedChannel buildAssetAccessChannel(E2eEnvironment env, String envName, VaultLeafCertificate cert) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.DS_CONTROL_PLANE, SsStackSetup.Port.CONTROL_PLANE_RPC);
        var credentials = TlsChannelCredentials.newBuilder()
                .keyManager(
                        new ByteArrayInputStream(cert.certificateAndChainPem().getBytes(StandardCharsets.UTF_8)),
                        new ByteArrayInputStream(cert.privateKeyPem().getBytes(StandardCharsets.UTF_8)))
                .trustManager(new ByteArrayInputStream(cert.trustChainPem().getBytes(StandardCharsets.UTF_8)))
                .build();

        return NettyChannelBuilder.forAddress(mapping.host(), mapping.port(), credentials)
                .overrideAuthority(DS_CONTROL_PLANE_TLS_AUTHORITY)
                .build();
    }

    // -- no-restart guard ---------------------------------------------------------------------------

    private Map<String, String> captureContainerTimestamps(ContainerLifecycleOps containerOps) {
        return Map.of(
                "ss0:" + SsStackSetup.PROXY, containerOps.containerStartedAt("ss0", SsStackSetup.PROXY),
                "ss0:" + SsStackSetup.DS_CONTROL_PLANE, containerOps.containerStartedAt("ss0", SsStackSetup.DS_CONTROL_PLANE),
                "ss1:" + SsStackSetup.PROXY, containerOps.containerStartedAt("ss1", SsStackSetup.PROXY),
                "ss1:" + SsStackSetup.DS_CONTROL_PLANE, containerOps.containerStartedAt("ss1", SsStackSetup.DS_CONTROL_PLANE));
    }

    private void assertNoRestartOccurred(Map<String, String> before, ContainerLifecycleOps containerOps) {
        var after = captureContainerTimestamps(containerOps);
        assertThat(after).as("proxy/control-plane container start timestamps unchanged (no restart)").isEqualTo(before);
    }

    // -- admin session helpers ------------------------------------------------------------------------

    private String baseUrl(E2eEnvironment env, String envName, String service, int port) {
        var mapping = env.getContainerMapping(envName, service, port);
        return "https://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private AdminSession login(String baseUrl) {
        var response = RestAssuredFactory.given()
                .formParam("username", ADMIN_USERNAME)
                .formParam("password", ADMIN_PASSWORD)
                .post(baseUrl + "/login");
        assertThat(response.getStatusCode()).as("login to %s", baseUrl).isEqualTo(200);
        return new AdminSession(baseUrl, response.getCookie("XSRF-TOKEN"), response.getCookies());
    }

    private record AdminSession(String baseUrl, String xsrfToken, Map<String, String> cookies) {
        private RequestSpecification authed() {
            return RestAssuredFactory.given()
                    .cookies(cookies)
                    .header("X-XSRF-TOKEN", xsrfToken)
                    .header("Content-Type", "application/json");
        }

        /** No forced JSON content type — RestAssured sets {@code multipart/form-data} itself once {@code .multiPart(...)} is used. */
        private RequestSpecification authedMultipart() {
            return RestAssuredFactory.given()
                    .cookies(cookies)
                    .header("X-XSRF-TOKEN", xsrfToken);
        }
    }

    private record VaultLeafCertificate(String certificateAndChainPem, String privateKeyPem, String trustChainPem) {
    }
}
