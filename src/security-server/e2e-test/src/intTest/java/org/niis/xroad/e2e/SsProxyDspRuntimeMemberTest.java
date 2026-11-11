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

import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ds.identity.DspConventions;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.e2e.DidResolutionOps.DidDocumentResponse;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.addRestServiceDescription;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.awaitClientRegistered;
import static org.niis.xroad.e2e.AdminApi.discoverBackendUrl;
import static org.niis.xroad.e2e.AdminApi.enableServiceDescription;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;
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
 * <p><b>Which participant context the exchange actually rides.</b> The new client's service is published
 * only under its own {@code DEV:COM:4321} context, once that context is provisioned — a service whose
 * owning member has no provisioned context yet is not published anywhere at all (XRDADR-41's full
 * pre-provisioning discoverability remains a deferred follow-up). The consumer proxy derives the
 * counter-party coordinates from the provider member id, so it dials {@code DEV:COM:4321} directly;
 * {@link #awaitMemberContextIssued} confirms that context and its membership credential are already
 * provisioned before any traffic flows, so this scenario never exercises the pre-provisioning gap. The
 * exchange rides two member contexts: the consumer side negotiates on the sender member's context
 * ({@code DEV:COM:1234}), the provider side serves the offer on the new member's <b>own</b> context
 * ({@code DEV:COM:4321}), and each side persists its own per-context copy of the one wire agreement. The
 * runtime member's context is thus exercised end-to-end as the provider side's negotiation identity.
 *
 * <p><b>Per-member data-plane resolution is exercised for both members' contexts.</b> The provider
 * side's transfer runs under {@code DEV:COM:4321}, so data-plane selection must resolve a data plane for
 * that member context. The control plane's data-plane store computes it from configuration on demand,
 * with no registration step; a transfer under a context that resolves no data plane terminates with
 * "No dataplane found", so the transfer succeeding is itself the end-to-end proof. The store keeps no
 * record (no table, no listing endpoint), so there is nothing to observe directly; its behaviour is
 * covered by {@code XRoadDataPlaneInstanceStoreTest}.
 *
 * <p><b>Resolvable once provisioned.</b> After the membership credential is ISSUED, the new member's DID
 * must resolve to a document naming it and carrying verification keys. Resolution goes through
 * {@link DidResolutionOps}, from inside the environment, because the hub serves a document only at the
 * DID's registered authority.
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
 * <p>Only k8s and LXD run the dataspace protocol stack; the Compose facade implements neither
 * {@link DsControlPlaneDbOps} nor {@link DidResolutionOps}, so this scenario self-skips there via
 * {@link Assumptions}, exactly like {@link SsProxyDspSelfCallTest}.
 *
 * <p>Runs after {@link SsMessagelogArchiveTest} (its own traffic must not be counted by that class's
 * exact pre-archive messagelog assertions) and after {@link SsProxyDspSelfCallTest}, before
 * {@link SsMonitoringTest} (whose operational-data assertions accumulate over the whole run).
 */
@DisplayName("SS proxy - runtime-provisioned member serves an established member-context consumer, no restart")
@Order(350)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyDspRuntimeMemberTest extends E2eTest {

    private static final String SS0_ENV = "ss0";

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

    private static final MemberSignCertificate NEW_MEMBER_SIGN_CERTIFICATE =
            new MemberSignCertificate(SS0_ENV, NEW_MEMBER_CTX_ID, NEW_MEMBER_CODE, "Sign key 4321");

    private static final String DID_WEB_PREFIX = "did:web:";
    private static final ClientId.Conf NEW_MEMBER = ClientId.Conf.create(X_ROAD_INSTANCE, NEW_MEMBER_CLASS, NEW_MEMBER_CODE);

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
    private static final String EXISTING_SERVICE_ID = Mock1Fixture.MOCK1_SERVICE_ID;

    /**
     * The DSP asset id for the new client's service — its full client id and the REST service code,
     * colon-joined, confirmed live against {@code edc_contract_agreement.asset_id}. Unique to this
     * scenario, so full-string equality on it alone identifies this negotiation.
     */
    private static final String ASSET_ID = NEW_CLIENT_ID + ":" + REST_SERVICE_CODE;

    private static final String ISSUED_CREDENTIAL_STATUS = "ISSUED";

    private static final String REST_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;
    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";

    private static final Duration PROVISIONING_POLL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DID_RESOLUTION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration DID_RESOLUTION_POLL_INTERVAL = Duration.ofSeconds(3);
    private static final Duration CATALOG_VISIBILITY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CATALOG_VISIBILITY_POLL_INTERVAL = Duration.ofSeconds(10);

    @Test
    @DisplayName("A member onboarded to ss0 at runtime serves an established member-context consumer, no restart")
    void memberOnboardedAtRuntimeServesAMemberContextConsumer(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps && env instanceof DidResolutionOps,
                () -> "%s does not run the dataspace protocol stack; runtime member provisioning is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var didResolution = (DidResolutionOps) env;
        var dspAssertions = new DspNegotiationDbAssertions((DsControlPlaneDbOps) env,
                SS0_ENV, ASSET_ID, CONSUMER_MEMBER_CTX_ID, NEW_MEMBER_CTX_ID);

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var caBaseUrl = MemberSignCertificate.caBaseUrl(env);

        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var clientId = when("member DEV:COM:4321's new RuntimeService subsystem is added to ss0 as a local client", () ->
                addLocalClient(ss0BaseUrl, ss0Session, NEW_CLIENT_ID));

        and("a CA-signed sign certificate for the new member is provisioned on ss0's token", () ->
                NEW_MEMBER_SIGN_CERTIFICATE.provision(env, ss0BaseUrl, ss0Session, caBaseUrl));

        and("its registration is submitted from ss0 to the Central Server", () ->
                registerClient(ss0BaseUrl, ss0Session, clientId));

        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        then("the Central Server approves the pending request and ss0 reports the new client as REGISTERED", () ->
                awaitClientRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession, clientId));

        var backendUrl = and("the backend URL of ss0's existing TestService mock1 service is discovered", () ->
                discoverBackendUrl(ss0BaseUrl, ss0Session, EXISTING_SERVICE_ID));

        var serviceDescriptionId = and("a REST service description reusing that backend is added for the new client", () ->
                addRestServiceDescription(ss0BaseUrl, ss0Session, clientId, backendUrl, REST_SERVICE_CODE));

        and("the new service description is enabled", () ->
                enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

        and("the established consumer TestService is granted access to the new client's service", () ->
                grantConsumerAccessRights(ss0BaseUrl, ss0Session, clientId, CONSUMER_CLIENT_ID, REST_SERVICE_CODE));

        then("the new member's own participant context and membership credential are provisioned, independent of "
                + "which context the transfer below ends up riding, with no restart", () ->
                awaitMemberContextIssued(ss0BaseUrl, ss0Session));

        and("the new member's DID now resolves with verification keys", () ->
                awaitKeyedDidDocument(env, didResolution));

        var response = when(
                "a REST request from the established consumer to the new client's service succeeds via the ss0 proxy, "
                        + "within the catalog cache window",
                () -> awaitCallSucceeds(env));

        then("the response carries the expected POST service message", () ->
                response.body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));

        var wireAgreementId = then(
                "the negotiation completes: a CONSUMER row on the sender member's context and a PROVIDER row on the "
                        + "new member's own context share one wire agreement",
                () -> dspAssertions.awaitNegotiationPair());

        and("exactly one per-context edc_contract_agreement copy exists for each side of that wire agreement", () ->
                dspAssertions.assertPerContextAgreementCopies(wireAgreementId));

        and("the transfer over that agreement succeeds", () ->
                dspAssertions.awaitTransferSucceeded(wireAgreementId));
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

    private String newMemberDid(E2eEnvironment env) {
        return DspConventions.memberCounterPartyId(NEW_MEMBER, env.securityServerAddress(SS0_ENV)).toString();
    }

    /** The {@code did:web} resolution URL: the decoded authority, then the remaining DID segments as the path. */
    private String newMemberDidUrl(E2eEnvironment env) {
        var segments = newMemberDid(env).substring(DID_WEB_PREFIX.length()).split(":");
        var path = String.join("/", Arrays.copyOfRange(segments, 1, segments.length));
        return "https://%s/%s/did.json".formatted(URLDecoder.decode(segments[0], StandardCharsets.UTF_8), path);
    }

    /**
     * Polls until the new member's DID resolves to a document carrying verification keys, then checks it
     * names the member. Polled because the hub publishes the keyed document on its own schedule once the
     * participant context exists.
     */
    private void awaitKeyedDidDocument(E2eEnvironment env, DidResolutionOps didResolution) {
        var did = newMemberDid(env);
        var last = new AtomicReference<DidDocumentResponse>();
        Awaitility.await()
                .pollInterval(DID_RESOLUTION_POLL_INTERVAL)
                .timeout(DID_RESOLUTION_TIMEOUT)
                .ignoreExceptions()
                .until(() -> {
                    var response = didResolution.resolveDidDocument(SS0_ENV, newMemberDidUrl(env));
                    last.set(response);
                    return response.status() == 200 && hasVerificationKeys(response);
                });
        assertThat(JsonPath.from(last.get().body()).getString("id")).as("id of the resolved document").isEqualTo(did);
    }

    private static boolean hasVerificationKeys(DidDocumentResponse response) {
        List<Object> verificationMethods = JsonPath.from(response.body()).getList("verificationMethod");
        return verificationMethods != null && !verificationMethods.isEmpty();
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
