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
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.AdminApi.AdminSession;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.e2e.AdminApi.addLocalClient;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.awaitClientRegistered;
import static org.niis.xroad.e2e.AdminApi.grantConsumerAccessRights;
import static org.niis.xroad.e2e.AdminApi.login;
import static org.niis.xroad.e2e.AdminApi.registerClient;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * A member whose code the context-id scheme percent-encodes acts as the <b>consumer</b> of a negotiation
 * with ss1. Only the consumer's callback address is stored and used (the provider reads it from the DSP
 * message), so a provider-side encoded member would pass without the callback path encoding and prove
 * nothing. Member {@code DEV:COM:222(A)} has the context id {@code DEV:COM:222%28A%29}, so its callback
 * segment travels as {@code 222%2528A%2529}.
 *
 * <p>Parentheses are used because they are DN-safe in the sign certificate CSR, unlike {@code +}.
 *
 * <p>Self-skips outside k8s and LXD, which are the only environments supporting runtime member registration.
 * Runs after {@link SsProxyDspRuntimeMemberTest}, which shares its sign certificate provisioning.
 */
@DisplayName("SS proxy - consumer member with a percent-encoded context id negotiates with ss1")
@Order(360)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyDspEncodedMemberTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String SS1_ENV = "ss1";

    private static final String MEMBER_ID = "DEV:COM:222(A)";
    private static final String CONSUMER_CLIENT_ID = MEMBER_ID + ":RuntimeConsumer";
    private static final String CONSUMER_X_ROAD_CLIENT = "DEV/COM/222(A)/RuntimeConsumer";
    private static final String MEMBER_CTX_ID = "DEV:COM:222%28A%29";
    private static final String CONSUMER_DID_SUFFIX = ":v1:" + MEMBER_CTX_ID;
    private static final String CONSUMER_CALLBACK_SEGMENT = "/DEV:COM:222%2528A%2529/";

    private static final String PROVIDER_CLIENT_ID = "DEV:COM:4321:TestClient";
    private static final String PROVIDER_CTX_ID = "DEV:COM:4321";
    private static final String PROVIDER_SERVICE_CODE = "mock1";
    private static final String PROVIDER_SERVICE_PATH = "/r1/DEV/COM/4321/TestClient/mock1";
    private static final String EXPECTED_RESPONSE_MESSAGE = "Hello, world from POST service!";

    private static final MemberSignCertificate SIGN_CERTIFICATE =
            new MemberSignCertificate(SS0_ENV, MEMBER_ID, "222(A)", "Sign key 222A");

    private static final int NEGOTIATION_STATE_FINALIZED = 1200;
    private static final String COLUMN_SEPARATOR = "|";

    private static final Duration PROVISIONING_POLL_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration PROVISIONING_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration GRANT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration GRANT_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration CALL_POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration NEGOTIATION_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration NEGOTIATION_POLL_INTERVAL = Duration.ofSeconds(2);

    @Test
    @DisplayName("A runtime-registered consumer member with a percent-encoded context id completes a negotiation with ss1")
    void encodedConsumerMemberCompletesNegotiation(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; runtime member provisioning is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var dbOps = (DsControlPlaneDbOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var ss1BaseUrl = adminBaseUrl(env, SS1_ENV);
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var caBaseUrl = MemberSignCertificate.caBaseUrl(env);

        var ss0Session = given("an admin session is established on ss0", () -> login(ss0BaseUrl));
        var ss1Session = given("an admin session is established on ss1", () -> login(ss1BaseUrl));

        var clientId = when("the RuntimeConsumer subsystem of member DEV:COM:222(A) is added to ss0 as a local client", () ->
                addLocalClient(ss0BaseUrl, ss0Session, CONSUMER_CLIENT_ID));

        and("a CA-signed sign certificate for the member is provisioned on ss0's token", () ->
                SIGN_CERTIFICATE.provision(env, ss0BaseUrl, ss0Session, caBaseUrl));

        and("its registration is submitted from ss0 to the Central Server", () ->
                registerClient(ss0BaseUrl, ss0Session, clientId));

        var csSession = given("an admin session is established on the Central Server", () -> login(csBaseUrl));

        then("the Central Server approves the pending request and ss0 reports the client as REGISTERED", () ->
                awaitClientRegistered(ss0BaseUrl, ss0Session, csBaseUrl, csSession, clientId));

        and("ss0 shows the member's credential issued and its DSP base URL published", () ->
                awaitMemberContextPublished(ss0BaseUrl, ss0Session));

        and("ss1 grants the new subsystem access to TestClient's mock1 service", () ->
                awaitGrant(ss1BaseUrl, ss1Session));

        var response = when("a REST request from the new subsystem to TestClient's mock1 succeeds via the ss0 proxy", () ->
                awaitCallSucceeds(env));

        then("the response carries the expected POST service message", () ->
                response.body("message", equalTo(EXPECTED_RESPONSE_MESSAGE)));

        and("ss1 recorded a finalized negotiation whose counterparty id carries the context id decoded exactly once", () ->
                awaitProviderNegotiationWithDecodedCounterparty(dbOps));
    }

    private void awaitMemberContextPublished(String ss0BaseUrl, AdminSession ss0) {
        var memberContext = "participant_contexts.find { it.kind == 'MEMBER' && it.participant_id == '"
                + MEMBER_CTX_ID + "' }";
        Awaitility.await()
                .pollInterval(PROVISIONING_POLL_INTERVAL)
                .timeout(PROVISIONING_POLL_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var response = authed(ss0).get(ss0BaseUrl + "/api/v1/dataspace/provisioning-status");
                    assertThat(response.getStatusCode()).isEqualTo(200);
                    assertThat(response.jsonPath().getString(memberContext + ".credential_status"))
                            .as("credential status of context %s", MEMBER_CTX_ID).isEqualTo("ISSUED");
                    assertThat(response.jsonPath().getString(memberContext + ".publication_state"))
                            .as("publication state of context %s", MEMBER_CTX_ID).isEqualTo("PUBLISHED");
                });
    }

    /** Retried because ss1 can grant access only once its global configuration lists the new subsystem. */
    private void awaitGrant(String ss1BaseUrl, AdminSession ss1) {
        Awaitility.await()
                .pollInterval(GRANT_POLL_INTERVAL)
                .timeout(GRANT_TIMEOUT)
                .untilAsserted(() -> grantConsumerAccessRights(
                        ss1BaseUrl, ss1, PROVIDER_CLIENT_ID, CONSUMER_CLIENT_ID, PROVIDER_SERVICE_CODE));
    }

    private ValidatableResponse awaitCallSucceeds(E2eEnvironment env) {
        var last = new AtomicReference<ValidatableResponse>();
        Awaitility.await()
                .pollInterval(CALL_POLL_INTERVAL)
                .timeout(CALL_TIMEOUT)
                .untilAsserted(() -> {
                    var response = Mock1Fixture.callService(env, SS0_ENV, CONSUMER_X_ROAD_CLIENT, PROVIDER_SERVICE_PATH);
                    last.set(response);
                    assertThat(response.extract().statusCode()).as("call via ss0 proxy").isEqualTo(200);
                });
        return last.get();
    }

    /**
     * ss1 stores the consumer's DID, taken from its token, as the counterparty id and the consumer's callback
     * address as sent. The callback must carry the context id with its percent signs encoded once more, so that
     * ss1's single decode of the path segment yields the context id.
     */
    private void awaitProviderNegotiationWithDecodedCounterparty(DsControlPlaneDbOps dbOps) {
        var sql = "SELECT n.state, n.counterparty_id, n.counterparty_address FROM edc_contract_negotiation n "
                + "WHERE n.type = 'PROVIDER' AND n.participant_context_id = '" + PROVIDER_CTX_ID + "' "
                + "AND n.counterparty_id LIKE '%:v1:DEV:COM:222%' ORDER BY n.id";
        Awaitility.await()
                .pollInterval(NEGOTIATION_POLL_INTERVAL)
                .timeout(NEGOTIATION_TIMEOUT)
                .ignoreExceptions()
                .untilAsserted(() -> {
                    var rows = parseRows(dbOps.execDsControlPlaneSql(SS1_ENV, sql));
                    assertThat(rows).as("ss1 PROVIDER negotiations with a DEV:COM:222* counterparty").isNotEmpty();
                    assertThat(rows)
                            .as("counterparty ids must all end with %s", CONSUMER_DID_SUFFIX)
                            .allMatch(row -> row.get(1).endsWith(CONSUMER_DID_SUFFIX));
                    assertThat(rows)
                            .as("callback addresses must all contain %s", CONSUMER_CALLBACK_SEGMENT)
                            .allMatch(row -> row.getLast().contains(CONSUMER_CALLBACK_SEGMENT));
                    assertThat(rows)
                            .as("a FINALIZED negotiation with the consumer")
                            .anyMatch(row -> Integer.parseInt(row.getFirst()) == NEGOTIATION_STATE_FINALIZED);
                });
    }

    private static List<List<String>> parseRows(String output) {
        return output.lines()
                .filter(line -> !line.isBlank())
                .map(line -> Arrays.stream(line.split("\\" + COLUMN_SEPARATOR, -1)).map(String::trim).toList())
                .toList();
    }
}
