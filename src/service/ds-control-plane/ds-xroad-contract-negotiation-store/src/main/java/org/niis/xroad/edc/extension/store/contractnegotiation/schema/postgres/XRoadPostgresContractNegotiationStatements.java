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

package org.niis.xroad.edc.extension.store.contractnegotiation.schema.postgres;

import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.niis.xroad.edc.extension.store.contractnegotiation.schema.XRoadContractNegotiationStatements;

import java.time.Clock;

import static java.lang.String.format;

/**
 * Postgres dialect statements for the converge mechanism: the agreement upsert's {@code ON CONFLICT} arbiter moves
 * from the internal primary key to the table's composite unique constraint (agreement id, participant context id),
 * so that two independently-generated internal ids for the same logical agreement land on one physical row. No
 * schema change; the same column list and update set as the stock template, only the conflict target differs.
 */
public class XRoadPostgresContractNegotiationStatements extends PostgresDialectStatements implements XRoadContractNegotiationStatements {

    public XRoadPostgresContractNegotiationStatements(LeaseStatements leaseStatements, Clock clock) {
        super(leaseStatements, clock);
    }

    @Override
    public String getUpsertAgreementTemplate() {
        var stockTemplate = super.getUpsertAgreementTemplate();
        var pkArbiter = "ON CONFLICT (" + getContractAgreementIdColumn() + ")";
        var compositeArbiter = "ON CONFLICT (" + getContractAgreementContractIdColumn() + ", "
                + getAgreementParticipantContextIdColumn() + ")";

        var converged = stockTemplate.replace(pkArbiter, compositeArbiter);
        if (converged.equals(stockTemplate)) {
            throw new IllegalStateException(
                    "Stock upsert agreement template no longer contains the expected primary-key conflict arbiter '"
                            + pkArbiter + "'; the converge fork must be re-verified against the current EDC version.");
        }
        return converged;
    }

    @Override
    public String getFindAgreementByCompositeKeyTemplate() {
        return format("SELECT * FROM %s WHERE %s = ? AND %s = ?;",
                getContractAgreementTable(), getContractAgreementContractIdColumn(), getAgreementParticipantContextIdColumn());
    }
}
