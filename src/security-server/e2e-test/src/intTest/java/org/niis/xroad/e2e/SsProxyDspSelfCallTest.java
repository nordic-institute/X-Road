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
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * Same-SS dataspace-protocol self-call: ss0 reaches its own {@code TestService} through its own proxy, so ss0
 * plays both the consumer and the provider role for one exchange. Proves the own-context routing fix and the
 * converge contract-negotiation store work together on a live stack, not just at the unit/store level, by
 * asserting the resulting negotiation and agreement rows directly in the ds-control-plane database — the one
 * scenario in this suite that reaches into DSP record-level state; every other scenario stops at HTTP status.
 *
 * <p>Only k8s and LXD run the dataspace protocol stack; the Compose facade does not implement
 * {@link DsControlPlaneDbOps}, so this scenario self-skips there via {@link Assumptions}.
 *
 * <p>Runs after {@link SsMessagelogArchiveTest}: its self-call traffic on ss0 would otherwise be counted by
 * that class's exact pre-archive messagelog assertions.
 */
@DisplayName("SS proxy - same-SS dataspace self-call")
@Order(300)
@SuppressWarnings("checkstyle:magicnumber")
class SsProxyDspSelfCallTest extends E2eTest {

    private static final String SELF_CALL_ENV = "ss0";
    private static final String SELF_CALL_X_ROAD_CLIENT = "DEV/COM/1234/TestService";
    private static final String SELF_CALL_SERVICE_PATH = "/r1/DEV/COM/1234/TestService/mock1";
    private static final String REST_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;

    private static final int NEGOTIATION_STATE_FINALIZED = 1200;
    private static final int EXPECTED_NEGOTIATION_COUNT = 2;

    /**
     * X-Road repurposes the EDC data plane as a standing message-exchange channel rather than a one-shot
     * dataset pull: a transfer reaching STARTED and staying there is this system's normal successful outcome,
     * not an in-progress state waiting to reach COMPLETED. Both are accepted as "succeeded".
     */
    private static final Set<Integer> TRANSFER_SUCCESS_STATES = Set.of(600, 800);

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    @Test
    @DisplayName("Self-call converges its consumer and provider negotiation onto one agreement, and the transfer succeeds")
    void selfCallConvergesOntoOneAgreementAndTransfers(E2eEnvironment env) {
        Assumptions.assumeTrue(env instanceof DsControlPlaneDbOps,
                () -> "%s does not run the dataspace protocol stack; same-SS self-call is only wired for k8s and LXD"
                        .formatted(env.getClass().getSimpleName()));
        var dbOps = (DsControlPlaneDbOps) env;

        given("the environment is initialized", () -> assertThat(env.isInitialized()).isTrue());

        var negotiationWatermark = given("the current negotiation watermark is captured", () ->
                latestNegotiationCreatedAt(dbOps));

        var response = given("a REST request is sent from TestService to itself via the ss0 proxy", () ->
                sendSelfCallRequest(env));

        then("the response is 200 with the expected POST service message", () ->
                response.statusCode(200).body("message", equalTo("Hello, world from POST service!")));

        var agreementInternalId = then("both new negotiations FINALIZE and FK-resolve to one shared agreement", () ->
                awaitConvergedNegotiations(dbOps, negotiationWatermark));

        and("exactly one edc_contract_agreement row exists for the converged (agreement id, participant context) pair", () ->
                assertSingleConvergedAgreement(dbOps, agreementInternalId));

        and("the transfer over the converged agreement succeeds", () ->
                awaitTransferSucceeded(dbOps, agreementInternalId));
    }

    private ValidatableResponse sendSelfCallRequest(E2eEnvironment env) {
        var mapping = env.getContainerMapping(SELF_CALL_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body(REST_REQUEST_BODY)
                .header("Content-Type", "application/json")
                .header("x-road-client", SELF_CALL_X_ROAD_CLIENT)
                .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), SELF_CALL_SERVICE_PATH))
                .then();
    }

    private long latestNegotiationCreatedAt(DsControlPlaneDbOps dbOps) {
        var result = dbOps.execDsControlPlaneSql(SELF_CALL_ENV,
                "SELECT COALESCE(MAX(created_at), 0) FROM edc_contract_negotiation");
        return Long.parseLong(result.trim());
    }

    /**
     * Polls until exactly two negotiation rows created after {@code watermark} exist, both FINALIZED and both
     * pointing at the same {@code agreement_id} foreign key; returns that shared internal agreement id.
     */
    private String awaitConvergedNegotiations(DsControlPlaneDbOps dbOps, long watermark) {
        var sql = ("SELECT id, state, agreement_id FROM edc_contract_negotiation "
                + "WHERE created_at > %d ORDER BY created_at, id").formatted(watermark);
        var lastSeen = new AtomicReference<>(List.<String[]>of());

        try {
            Awaitility.await()
                    .pollInterval(POLL_INTERVAL)
                    .timeout(POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var rows = parseRows(dbOps.execDsControlPlaneSql(SELF_CALL_ENV, sql));
                        lastSeen.set(rows);
                        return rows.size() == EXPECTED_NEGOTIATION_COUNT
                                && rows.stream().allMatch(row -> Integer.parseInt(row[1]) == NEGOTIATION_STATE_FINALIZED)
                                && !rows.get(0)[2].isBlank()
                                && rows.get(0)[2].equals(rows.get(1)[2]);
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the self-call's two negotiations to converge and finalize; "
                            + "last observed rows (id|state|agreement_id): %s".formatted(lastSeen.get()), e);
        }

        return lastSeen.get().get(0)[2];
    }

    private void assertSingleConvergedAgreement(DsControlPlaneDbOps dbOps, String agreementInternalId) {
        var agreementRows = parseRows(dbOps.execDsControlPlaneSql(SELF_CALL_ENV,
                ("SELECT agr_agreement_id, agr_participant_context_id FROM edc_contract_agreement "
                        + "WHERE agr_id = '%s'").formatted(agreementInternalId)));
        assertThat(agreementRows)
                .as("edc_contract_agreement row for the converged internal id %s", agreementInternalId)
                .hasSize(1);

        var wireAgreementId = agreementRows.get(0)[0];
        var participantContextId = agreementRows.get(0)[1];

        var compositeCount = Integer.parseInt(dbOps.execDsControlPlaneSql(SELF_CALL_ENV,
                ("SELECT COUNT(*) FROM edc_contract_agreement "
                        + "WHERE agr_agreement_id = '%s' AND agr_participant_context_id = '%s'")
                        .formatted(wireAgreementId, participantContextId)));
        assertThat(compositeCount)
                .as("edc_contract_agreement rows for composite pair (agreement id %s, participant context %s)",
                        wireAgreementId, participantContextId)
                .isEqualTo(1);
    }

    /**
     * Polls until at least one {@code edc_transfer_process} row for the converged agreement's internal id
     * (its {@code contract_id}, the same value as {@code edc_contract_negotiation.agreement_id}) reaches a
     * success state, and every such row is in a success state. No xroad fork changes
     * {@code edc_transfer_process}, so this only needs the stock schema.
     */
    private void awaitTransferSucceeded(DsControlPlaneDbOps dbOps, String agreementInternalId) {
        var sql = ("SELECT transferprocess_id, state FROM edc_transfer_process "
                + "WHERE contract_id = '%s' ORDER BY created_at").formatted(agreementInternalId);
        var lastSeen = new AtomicReference<>(List.<String[]>of());

        try {
            Awaitility.await()
                    .pollInterval(POLL_INTERVAL)
                    .timeout(POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var rows = parseRows(dbOps.execDsControlPlaneSql(SELF_CALL_ENV, sql));
                        lastSeen.set(rows);
                        return !rows.isEmpty()
                                && rows.stream().allMatch(row -> TRANSFER_SUCCESS_STATES.contains(Integer.parseInt(row[1])));
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the self-call's transfer to succeed; "
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
