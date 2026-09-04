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
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Proves the story's central, otherwise-unprovable claim: a second, distinct X-Road member, newly
 * onboarded to ss0 <b>after</b> the stack is already running, serves a real transfer under its own
 * dataspace participant context, with no proxy or connector restart anywhere in the flow.
 *
 * <p>ss0 already hosts member {@code DEV:COM:1234} (subsystems {@code TestService}/{@code TestSaved}),
 * seeded at environment bring-up. This scenario adds a client for member {@code DEV:COM:4321} — an
 * X-Road member that already exists (it owns ss1) but has never had a client on ss0 — entirely through
 * ss0's own admin API, at test run time: local client add, registration, a REST service description,
 * self access rights, then a same-SS self-call exactly like {@link SsProxyDspSelfCallTest}, reusing that
 * class's converged-negotiation/single-agreement/transfer-succeeded assertions against this member's own,
 * brand-new participant context instead of {@code DEV:COM:1234}'s.
 *
 * <p><b>Environment prerequisite (not covered by this class):</b> a signing certificate for member
 * {@code DEV:COM:4321} must already be imported into ss0's token before this scenario runs — the same
 * one-time, CA-signed key/cert bootstrap already performed for {@code DEV:COM:1234}'s own sign key on
 * ss0, just for the second member's identity. Provisioning that certificate requires talking to the test
 * CA, which only the environment's own bring-up tooling can reach; this class deliberately does not
 * attempt it, matching the CA/AC boundary this suite already keeps (see {@link DsControlPlaneDbOps}'s
 * class doc for the equivalent DB-reach boundary).
 *
 * <p>Only k8s and LXD run the dataspace protocol stack; the Compose facade does not implement
 * {@link DsControlPlaneDbOps}, so this scenario self-skips there via {@link Assumptions}, exactly like
 * {@link SsProxyDspSelfCallTest}.
 *
 * <p>Runs after {@link SsMessagelogArchiveTest} (its own traffic must not be counted by that class's
 * exact pre-archive messagelog assertions) and after {@link SsProxyDspSelfCallTest}, before
 * {@link SsMonitoringTest} (whose operational-data assertions accumulate over the whole run).
 */
@DisplayName("SS proxy - runtime-provisioned member transfers over its own dataspace context")
@Order(350)
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyDspRuntimeMemberTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String CS_ENV = "aux";

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
    private static final String NEW_CLIENT_X_ROAD_ID = "DEV/COM/4321/RuntimeService";
    private static final String NEW_SERVICE_PATH = "/r1/DEV/COM/4321/RuntimeService/mock1";
    private static final String REST_SERVICE_CODE = "mock1";

    /** ss0's pre-existing TestService/mock1 REST service, whose backend URL is reused for the new client. */
    private static final String EXISTING_SERVICE_ID = "DEV:COM:1234:TestService%3Amock1";

    private static final String REGISTERED_STATUS = "REGISTERED";
    private static final String ISSUED_CREDENTIAL_STATUS = "ISSUED";

    private static final String REST_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;
    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";

    private static final int NEGOTIATION_STATE_FINALIZED = 1200;
    private static final int EXPECTED_NEGOTIATION_COUNT = 2;
    private static final Set<Integer> TRANSFER_SUCCESS_STATES = Set.of(600, 800);

    private static final Duration REGISTRATION_POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration REGISTRATION_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Duration PROVISIONING_POLL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration TRANSFER_POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration TRANSFER_POLL_INTERVAL = Duration.ofSeconds(2);

    private record AdminSession(Map<String, String> cookies, String xsrfToken) {
    }

    @Test
    @DisplayName("A member added to ss0 at runtime transfers over its own participant context, no restart")
    void memberAddedAtRuntimeTransfersOverOwnContext(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; runtime member provisioning is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var dbOps = (DsControlPlaneDbOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var csBaseUrl = adminBaseUrl(env, CS_ENV);

        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));

        var clientId = when("member DEV:COM:4321's new RuntimeService subsystem is added to ss0 as a local client", () ->
                addLocalClient(ss0BaseUrl, ss0Session));

        and("its registration is submitted from ss0 to the Central Server", () ->
                registerClient(ss0BaseUrl, ss0Session, clientId));

        and("the Central Server's pending registration request is approved", () ->
                approveLatestWaitingClientRegistration(csBaseUrl, login(csBaseUrl)));

        then("ss0 reports the new client as REGISTERED", () ->
                awaitClientRegistered(ss0BaseUrl, ss0Session, clientId));

        var backendUrl = and("the backend URL of ss0's existing TestService mock1 service is discovered", () ->
                discoverExistingBackendUrl(ss0BaseUrl, ss0Session));

        var serviceDescriptionId = and("a REST service description reusing that backend is added for the new client", () ->
                addRestServiceDescription(ss0BaseUrl, ss0Session, clientId, backendUrl));

        and("the new service description is enabled", () ->
                enableServiceDescription(ss0BaseUrl, ss0Session, serviceDescriptionId));

        and("the new client is granted access to its own service, for the self-call below", () ->
                grantSelfAccessRights(ss0BaseUrl, ss0Session, clientId));

        then("the new member's participant context and membership credential are provisioned, with no restart", () ->
                awaitMemberContextIssued(ss0BaseUrl, ss0Session));

        var response = when("a REST request is sent from the new client to itself via the ss0 proxy", () ->
                sendSelfCallRequest(env));

        then("the response is 200 with the expected POST service message", () ->
                response.statusCode(200).body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));

        var agreementInternalId = then(
                "the self-negotiation converges: two FINALIZED negotiations under the new member's own context FK-resolve to one agreement",
                () -> awaitConvergedNegotiations(dbOps));

        and("exactly one edc_contract_agreement row exists for the converged (agreement id, participant context) pair", () ->
                assertSingleConvergedAgreement(dbOps, agreementInternalId));

        and("the transfer over the converged agreement succeeds", () ->
                awaitTransferSucceeded(dbOps, agreementInternalId));
    }

    private String adminBaseUrl(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.UI, SsStackSetup.Port.UI);
        return "https://%s:%s".formatted(mapping.host(), mapping.port());
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
     * Approves the most recently submitted WAITING client registration request. Tolerates an empty
     * WAITING list: if the Central Server auto-approves client registration requests, nothing is ever
     * left waiting, and {@link #awaitClientRegistered} below still confirms the outcome either way.
     */
    private void approveLatestWaitingClientRegistration(String csBaseUrl, AdminSession cs) {
        var response = authed(cs).get(csBaseUrl + "/api/v1/management-requests?sort=id&desc=true&status=WAITING");
        assertThat(response.getStatusCode()).as("list WAITING management requests").isEqualTo(200);

        List<Object> items = response.jsonPath().getList("items");
        if (items.isEmpty()) {
            log.info("No WAITING management request found for the new client; assuming client registration requests auto-approve");
            return;
        }

        var requestId = response.jsonPath().getInt("items[0].id");
        var approval = authed(cs).post(csBaseUrl + "/api/v1/management-requests/" + requestId + "/approval");
        assertThat(approval.getStatusCode()).as("approve client registration request %s", requestId).isEqualTo(200);
    }

    private void awaitClientRegistered(String ss0BaseUrl, AdminSession ss0, String clientId) {
        Awaitility.await()
                .pollInterval(REGISTRATION_POLL_INTERVAL)
                .timeout(REGISTRATION_POLL_TIMEOUT)
                .untilAsserted(() -> {
                    var response = authed(ss0).get(ss0BaseUrl + "/api/v1/clients/" + clientId);
                    assertThat(response.getStatusCode()).as("GET /clients/%s", clientId).isEqualTo(200);
                    assertThat(response.jsonPath().getString("status"))
                            .as("status of client %s", clientId)
                            .isEqualTo(REGISTERED_STATUS);
                });
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
     * Grants the new client access to its own service, mirroring the environment bring-up's own
     * self-access grant for {@code TestService} (see {@link SsProxyDspSelfCallTest}). Tolerates 409 so
     * this stays safe to run against a substrate where the grant already exists.
     */
    private void grantSelfAccessRights(String ss0BaseUrl, AdminSession ss0, String clientId) {
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
                .post(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-clients/" + clientId + "/access-rights");
        assertThat(response.getStatusCode())
                .as("grant %s access to its own service", clientId)
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

    private ValidatableResponse sendSelfCallRequest(E2eEnvironment env) {
        var mapping = env.getContainerMapping(SS0_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body(REST_REQUEST_BODY)
                .header("Content-Type", "application/json")
                .header("x-road-client", NEW_CLIENT_X_ROAD_ID)
                .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), NEW_SERVICE_PATH))
                .then();
    }

    /**
     * Identical in shape to {@link SsProxyDspSelfCallTest#awaitConvergedNegotiations}, but scoped by the
     * new member's own participant context id rather than an asset-id join plus a non-management filter:
     * {@link #NEW_MEMBER_CTX_ID} is brand-new to this test run, so no other scenario's negotiation can
     * ever share it — unlike {@code DEV:COM:1234}'s context, which several other scenarios also use.
     */
    private String awaitConvergedNegotiations(DsControlPlaneDbOps dbOps) {
        var candidateSql = "SELECT n.agreement_id FROM edc_contract_negotiation n "
                + "JOIN edc_contract_agreement a ON a.agr_id = n.agreement_id "
                + "WHERE n.agreement_id IS NOT NULL AND n.participant_context_id = '" + NEW_MEMBER_CTX_ID + "'"
                + " GROUP BY n.agreement_id HAVING COUNT(*) = " + EXPECTED_NEGOTIATION_COUNT
                + " ORDER BY MAX(n.created_at) DESC LIMIT 1";
        var lastSeen = new AtomicReference<>(List.<String[]>of());

        try {
            Awaitility.await()
                    .pollInterval(TRANSFER_POLL_INTERVAL)
                    .timeout(TRANSFER_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var candidateId = dbOps.execDsControlPlaneSql(SS0_ENV, candidateSql).trim();
                        if (candidateId.isBlank()) {
                            lastSeen.set(List.of());
                            return false;
                        }

                        var memberSql = ("SELECT id, state, agreement_id, participant_context_id "
                                + "FROM edc_contract_negotiation WHERE agreement_id = '%s' ORDER BY id")
                                .formatted(candidateId);
                        var rows = parseRows(dbOps.execDsControlPlaneSql(SS0_ENV, memberSql));
                        lastSeen.set(rows);
                        return rows.size() == EXPECTED_NEGOTIATION_COUNT
                                && rows.stream().allMatch(row -> Integer.parseInt(row[1]) == NEGOTIATION_STATE_FINALIZED)
                                && rows.get(0)[3].equals(rows.get(1)[3]);
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for a converged self-negotiation pair under participant context '%s' "
                            + "(two FINALIZED rows sharing one agreement); last observed candidate group's rows "
                            + "(id|state|agreement_id|participant_context_id): %s".formatted(NEW_MEMBER_CTX_ID, lastSeen.get()), e);
        }

        return lastSeen.get().get(0)[2];
    }

    private void assertSingleConvergedAgreement(DsControlPlaneDbOps dbOps, String agreementInternalId) {
        var agreementRows = parseRows(dbOps.execDsControlPlaneSql(SS0_ENV,
                ("SELECT agr_agreement_id, agr_participant_context_id FROM edc_contract_agreement "
                        + "WHERE agr_id = '%s'").formatted(agreementInternalId)));
        assertThat(agreementRows)
                .as("edc_contract_agreement row for the converged internal id %s", agreementInternalId)
                .hasSize(1);

        var wireAgreementId = agreementRows.get(0)[0];
        var participantContextId = agreementRows.get(0)[1];
        assertThat(participantContextId)
                .as("the converged agreement's participant context")
                .isEqualTo(NEW_MEMBER_CTX_ID);

        var compositeCount = Integer.parseInt(dbOps.execDsControlPlaneSql(SS0_ENV,
                ("SELECT COUNT(*) FROM edc_contract_agreement "
                        + "WHERE agr_agreement_id = '%s' AND agr_participant_context_id = '%s'")
                        .formatted(wireAgreementId, participantContextId)));
        assertThat(compositeCount)
                .as("edc_contract_agreement rows for composite pair (agreement id %s, participant context %s)",
                        wireAgreementId, participantContextId)
                .isEqualTo(1);
    }

    private void awaitTransferSucceeded(DsControlPlaneDbOps dbOps, String agreementInternalId) {
        var sql = ("SELECT transferprocess_id, state FROM edc_transfer_process "
                + "WHERE contract_id = '%s' ORDER BY created_at").formatted(agreementInternalId);
        var lastSeen = new AtomicReference<>(List.<String[]>of());

        try {
            Awaitility.await()
                    .pollInterval(TRANSFER_POLL_INTERVAL)
                    .timeout(TRANSFER_POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var rows = parseRows(dbOps.execDsControlPlaneSql(SS0_ENV, sql));
                        lastSeen.set(rows);
                        return !rows.isEmpty()
                                && rows.stream().allMatch(row -> TRANSFER_SUCCESS_STATES.contains(Integer.parseInt(row[1])));
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the new member's transfer to succeed; "
                            + "last observed rows (transferprocess_id|state): %s".formatted(lastSeen.get()), e);
        }
    }

    private static List<String[]> parseRows(String tupleOutput) {
        if (tupleOutput.isBlank()) {
            return List.of();
        }
        return tupleOutput.lines().map(line -> line.split("\\|", -1)).toList();
    }
}
