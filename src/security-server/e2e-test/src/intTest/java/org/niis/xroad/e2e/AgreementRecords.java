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

import java.util.List;

/**
 * Negotiation, agreement and transfer records of one asset as one Security Server's ds-control-plane stores
 * them, read through {@link DsControlPlaneDbOps}. Negotiations are matched through the offers they carry, so
 * failed ones count too, transfers through the per-context copies of the wire agreement they run under.
 */
final class AgreementRecords {

    /** The control-plane store's name for the side that asked for access. */
    static final String CONSUMER = "CONSUMER";
    /** The control-plane store's name for the side that granted access. */
    static final String PROVIDER = "PROVIDER";

    static final int NEGOTIATION_FINALIZED = 1200;
    static final int TRANSFER_STARTED = 600;
    static final int TRANSFER_COMPLETED = 800;

    private final DsControlPlaneDbOps dbOps;
    private final String envName;

    AgreementRecords(DsControlPlaneDbOps dbOps, String envName) {
        this.dbOps = dbOps;
        this.envName = envName;
    }

    /**
     * @return the number of negotiations the given side and participant context ran for the asset, in any
     *         state and whether or not they produced an agreement, matched through the asset id carried by
     *         the negotiation's contract offers
     */
    int negotiationCount(String side, String participantContextId, String assetId) {
        var sql = ("SELECT COUNT(*) FROM edc_contract_negotiation n "
                + "WHERE n.type = '%s' AND n.participant_context_id = '%s' AND n.contract_offers::text LIKE '%%\"%s\"%%'")
                .formatted(side, participantContextId, assetId);
        return Integer.parseInt(dbOps.execDsControlPlaneSql(envName, sql).trim());
    }

    /**
     * @return the wire ids of the agreements FINALIZED for the asset on the given side and participant context,
     *         oldest first
     */
    List<String> finalizedAgreementIds(String side, String participantContextId, String assetId) {
        var sql = ("SELECT a.agr_agreement_id FROM edc_contract_negotiation n "
                + "JOIN edc_contract_agreement a ON a.agr_id = n.agreement_id "
                + "WHERE n.type = '%s' AND n.state = %d AND n.participant_context_id = '%s' AND a.asset_id = '%s' "
                + "GROUP BY a.agr_agreement_id ORDER BY MIN(n.created_at)")
                .formatted(side, NEGOTIATION_FINALIZED, participantContextId, assetId);
        return lines(dbOps.execDsControlPlaneSql(envName, sql));
    }

    /**
     * @return the wire ids of the agreements FINALIZED for the asset on the given side and participant context
     *         whose policy names {@code encodedClientId} as the subsystem it was published for, oldest first
     */
    List<String> finalizedAgreementIdsNaming(String side, String participantContextId, String assetId,
                                             String encodedClientId) {
        var sql = ("SELECT a.agr_agreement_id FROM edc_contract_negotiation n "
                + "JOIN edc_contract_agreement a ON a.agr_id = n.agreement_id "
                + "WHERE n.type = '%s' AND n.state = %d AND n.participant_context_id = '%s' AND a.asset_id = '%s' "
                + "AND a.policy::text LIKE '%%\"%s\"%%' "
                + "GROUP BY a.agr_agreement_id ORDER BY MIN(n.created_at)")
                .formatted(side, NEGOTIATION_FINALIZED, participantContextId, assetId, encodedClientId);
        return lines(dbOps.execDsControlPlaneSql(envName, sql));
    }

    /**
     * @return the transfer processes of the given side that run under the wire agreement, oldest first
     */
    List<Transfer> transfers(String side, String wireAgreementId) {
        var sql = ("SELECT t.transferprocess_id, t.state FROM edc_transfer_process t WHERE t.type = '%s' "
                + "AND t.contract_id IN (SELECT agr_id FROM edc_contract_agreement WHERE agr_agreement_id = '%s') "
                + "ORDER BY t.created_at")
                .formatted(side, wireAgreementId);
        return lines(dbOps.execDsControlPlaneSql(envName, sql)).stream()
                .map(row -> row.split("\\|", -1))
                .map(columns -> new Transfer(columns[0], Integer.parseInt(columns[1])))
                .toList();
    }

    /** @return whether this control plane holds a copy of the wire agreement */
    boolean agreementRegistered(String wireAgreementId) {
        var sql = "SELECT COUNT(*) FROM edc_contract_agreement WHERE agr_agreement_id = '%s'".formatted(wireAgreementId);
        return Integer.parseInt(dbOps.execDsControlPlaneSql(envName, sql).trim()) > 0;
    }

    /** One transfer process: its id on its own control plane, which is also the id of its data flow, and its state. */
    record Transfer(String id, int state) {
    }

    private static List<String> lines(String tupleOutput) {
        return tupleOutput.isBlank() ? List.of() : tupleOutput.lines().map(String::trim).toList();
    }
}
