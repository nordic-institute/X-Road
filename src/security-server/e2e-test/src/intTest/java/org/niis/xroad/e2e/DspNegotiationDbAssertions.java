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

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Record-level assertions against a Security Server's ds-control-plane database for one DSP exchange,
 * shared by the scenarios that verify negotiation and transfer state beyond HTTP status
 * ({@link SsProxyDspSelfCallTest}, {@link SsProxyDspRuntimeMemberTest}).
 *
 * <p>The asserted shape: the consumer side negotiates as the sender member's derived participant context
 * ({@code consumerMemberCtxId}) and dials the provider member's derived context
 * ({@code providerMemberCtxId}) — counter-party coordinates are derived per XRDADR-41, so no host-context
 * literal ever appears in a query. Each side persists its agreement copy scoped to its own context, and the
 * pair is grouped by the wire agreement id ({@code agr_agreement_id}) the copies share: two per-context
 * copies when the contexts differ, one converged row (the store's composite-key upsert) when a same-member
 * self-call puts both sides on one context.
 *
 * <p>The pair is identified by shape rather than by recency of creation, so a warm-cache rerun that reuses
 * an existing agreement is detected exactly as reliably as a freshly negotiated one. {@code HAVING COUNT(*) = 2}
 * matters as much as the non-mgmt and asset-id filters: a cross-SS call to the same asset leaves a single
 * provider-side row on this control plane (its consumer row lives on the calling SS), which can outrank the
 * scenario's own pair by recency and starve the poll on an unrelated 1-row group. The scenario-unique asset-id
 * join rules out any other asset's own negotiation pair (e.g. {@link SsMonitoringTest}'s getSecurityServer*
 * self-calls, which match the non-mgmt shape too); management negotiations ride a {@code -mgmt}-suffixed
 * participant context and a {@code :mgmt}-suffixed counterparty DID, so excluding those needs no
 * substrate-specific participant context string.
 */
final class DspNegotiationDbAssertions {

    private static final String TYPE_CONSUMER = "CONSUMER";
    private static final String TYPE_PROVIDER = "PROVIDER";

    private static final int NEGOTIATION_STATE_FINALIZED = 1200;
    private static final int EXPECTED_NEGOTIATION_COUNT = 2;

    /** Column index of {@code agr_agreement_id} in the pair query's (state, type, context, wire-id) rows. */
    private static final int WIRE_AGREEMENT_ID_COLUMN = 3;

    private static final String NON_MGMT_FILTER =
            "n.participant_context_id NOT LIKE '%-mgmt' AND n.counterparty_id NOT LIKE '%:mgmt'";

    /**
     * X-Road repurposes the EDC data plane as a standing message-exchange channel rather than a one-shot
     * dataset pull: a transfer reaching STARTED and staying there is this system's normal successful outcome,
     * not an in-progress state waiting to reach COMPLETED. Both are accepted as "succeeded".
     */
    private static final Set<Integer> TRANSFER_SUCCESS_STATES = Set.of(600, 800);

    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final DsControlPlaneDbOps dbOps;
    private final String envName;
    private final String assetId;
    private final String consumerMemberCtxId;
    private final String providerMemberCtxId;

    DspNegotiationDbAssertions(DsControlPlaneDbOps dbOps, String envName, String assetId,
                               String consumerMemberCtxId, String providerMemberCtxId) {
        this.dbOps = dbOps;
        this.envName = envName;
        this.assetId = assetId;
        this.consumerMemberCtxId = consumerMemberCtxId;
        this.providerMemberCtxId = providerMemberCtxId;
    }

    /**
     * Polls until the asset's latest two-row negotiation group has exactly two FINALIZED rows — a CONSUMER
     * row on {@code consumerMemberCtxId} and a PROVIDER row on {@code providerMemberCtxId}; returns the wire
     * agreement id the pair shares.
     */
    String awaitNegotiationPair() {
        var candidateSql = "SELECT a.agr_agreement_id FROM edc_contract_negotiation n "
                + "JOIN edc_contract_agreement a ON a.agr_id = n.agreement_id "
                + "WHERE n.agreement_id IS NOT NULL AND " + NON_MGMT_FILTER
                + " AND a.asset_id = '" + assetId + "'"
                + " GROUP BY a.agr_agreement_id HAVING COUNT(*) = " + EXPECTED_NEGOTIATION_COUNT
                + " ORDER BY MAX(n.created_at) DESC LIMIT 1";
        var lastSeen = new AtomicReference<>(List.<String[]>of());

        try {
            Awaitility.await()
                    .pollInterval(POLL_INTERVAL)
                    .timeout(POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var candidateId = dbOps.execDsControlPlaneSql(envName, candidateSql).trim();
                        if (candidateId.isBlank()) {
                            lastSeen.set(List.of());
                            return false;
                        }

                        var memberSql = ("SELECT n.state, n.type, n.participant_context_id, a.agr_agreement_id "
                                + "FROM edc_contract_negotiation n "
                                + "JOIN edc_contract_agreement a ON a.agr_id = n.agreement_id "
                                + "WHERE a.agr_agreement_id = '%s' ORDER BY n.id")
                                .formatted(candidateId);
                        var rows = parseRows(dbOps.execDsControlPlaneSql(envName, memberSql));
                        lastSeen.set(rows);
                        return isConsumerAndProviderPairFinalized(rows);
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    ("Timed out waiting for a negotiation pair for asset '%s' (a FINALIZED CONSUMER row on context "
                            + "'%s' and a FINALIZED PROVIDER row on context '%s', sharing one wire agreement id); "
                            + "last observed candidate group's rows (state|type|participant_context_id|agr_agreement_id): %s")
                            .formatted(assetId, consumerMemberCtxId, providerMemberCtxId,
                                    lastSeen.get().stream().map(row -> String.join("|", row)).toList()), e);
        }

        return lastSeen.get().get(0)[WIRE_AGREEMENT_ID_COLUMN];
    }

    private boolean isConsumerAndProviderPairFinalized(List<String[]> rows) {
        if (rows.size() != EXPECTED_NEGOTIATION_COUNT
                || !rows.stream().allMatch(row -> Integer.parseInt(row[0]) == NEGOTIATION_STATE_FINALIZED)) {
            return false;
        }
        var consumerOnMemberCtx = rows.stream().anyMatch(row ->
                TYPE_CONSUMER.equals(row[1]) && consumerMemberCtxId.equals(row[2]));
        var providerOnMemberCtx = rows.stream().anyMatch(row ->
                TYPE_PROVIDER.equals(row[1]) && providerMemberCtxId.equals(row[2]));
        return consumerOnMemberCtx && providerOnMemberCtx;
    }

    /**
     * Each side persists its agreement copy scoped to its own participant context: two per-context copies
     * when consumer and provider contexts differ, one converged row (the store's composite-key upsert) when
     * a same-member self-call puts both sides on one context. Also verifies every negotiation row
     * referencing a copy carries that copy's own context — the invariant the upsert exists to protect.
     */
    void assertPerContextAgreementCopies(String wireAgreementId) {
        var expectedContexts = Stream.of(consumerMemberCtxId, providerMemberCtxId).distinct().sorted().toList();
        var contexts = parseRows(dbOps.execDsControlPlaneSql(envName,
                ("SELECT agr_participant_context_id FROM edc_contract_agreement "
                        + "WHERE agr_agreement_id = '%s' ORDER BY agr_participant_context_id")
                        .formatted(wireAgreementId))).stream().map(row -> row[0]).toList();
        assertThat(contexts)
                .as("per-context edc_contract_agreement copies of wire agreement %s", wireAgreementId)
                .isEqualTo(expectedContexts);

        var mismatchedNegotiations = Integer.parseInt(dbOps.execDsControlPlaneSql(envName,
                ("SELECT COUNT(*) FROM edc_contract_negotiation n "
                        + "JOIN edc_contract_agreement a ON a.agr_id = n.agreement_id "
                        + "WHERE a.agr_agreement_id = '%s' AND n.participant_context_id <> a.agr_participant_context_id")
                        .formatted(wireAgreementId)).trim());
        assertThat(mismatchedNegotiations)
                .as("negotiations referencing a copy of agreement %s carry that copy's own participant context",
                        wireAgreementId)
                .isZero();
    }

    /**
     * Polls until at least one {@code edc_transfer_process} row for the wire agreement reaches a success
     * state, and every such row is in a success state. A transfer's {@code contract_id} references the
     * initiating side's own per-context agreement copy, so rows are matched through the copies sharing the
     * wire agreement id (with the wire id itself accepted directly, for either side referencing it verbatim).
     * No xroad fork changes {@code edc_transfer_process}, so this only needs the stock schema.
     */
    void awaitTransferSucceeded(String wireAgreementId) {
        var sql = ("SELECT transferprocess_id, state FROM edc_transfer_process "
                + "WHERE contract_id IN (SELECT agr_id FROM edc_contract_agreement WHERE agr_agreement_id = '%s') "
                + "OR contract_id = '%s' ORDER BY created_at").formatted(wireAgreementId, wireAgreementId);
        var lastSeen = new AtomicReference<>(List.<String[]>of());

        try {
            Awaitility.await()
                    .pollInterval(POLL_INTERVAL)
                    .timeout(POLL_TIMEOUT)
                    .ignoreExceptions()
                    .until(() -> {
                        var rows = parseRows(dbOps.execDsControlPlaneSql(envName, sql));
                        lastSeen.set(rows);
                        return !rows.isEmpty()
                                && rows.stream().allMatch(row -> TRANSFER_SUCCESS_STATES.contains(Integer.parseInt(row[1])));
                    });
        } catch (ConditionTimeoutException e) {
            throw new ConditionTimeoutException(
                    "Timed out waiting for the transfer over agreement %s to succeed; "
                            + "last observed rows (transferprocess_id|state): %s".formatted(wireAgreementId, lastSeen.get()), e);
        }
    }

    private static List<String[]> parseRows(String tupleOutput) {
        if (tupleOutput.isBlank()) {
            return List.of();
        }
        return tupleOutput.lines().map(line -> line.split("\\|", -1)).toList();
    }
}
