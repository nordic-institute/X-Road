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

package org.niis.xroad.edc.extension.store.contractnegotiation;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.testfixtures.negotiation.store.ContractNegotiationStoreTestBase;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.policy.model.PolicyRegistrationTypes;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.BaseSqlLeaseStatements;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilderImpl;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.eclipse.edc.sql.testfixtures.LeaseUtil;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.edc.extension.store.contractnegotiation.schema.postgres.XRoadPostgresContractNegotiationStatements;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Runs EDC's own contract negotiation store contract tests against the converge-capable composed store. Every
 * negotiation created by these fixtures gets its own randomly generated wire agreement id, so no two agreements in
 * this suite ever share a composite pair; it proves stock behaviour is preserved for the normal, non-colliding
 * case. Collision convergence itself is covered by {@link XRoadContractNegotiationStoreConvergeTest}.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class XRoadContractNegotiationStorePostgresTest extends ContractNegotiationStoreTestBase {

    private final LeaseStatements leaseStatements = new BaseSqlLeaseStatements();
    private final XRoadPostgresContractNegotiationStatements statements =
            new XRoadPostgresContractNegotiationStatements(leaseStatements, Clock.systemUTC());
    private XRoadContractNegotiationStore store;
    private LeaseUtil leaseUtil;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension extension, QueryExecutor queryExecutor) throws IOException {
        var manager = new JacksonTypeManager();
        manager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));

        var leaseContextBuilder = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), CONNECTOR_NAME,
                statements.getContractNegotiationTable(), leaseStatements, clock, queryExecutor);

        var delegate = new SqlContractNegotiationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), statements, leaseContextBuilder, queryExecutor);

        store = new XRoadContractNegotiationStore(delegate, extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), statements, leaseContextBuilder, queryExecutor);

        var schema = TestUtils.getResourceFileContentAsString("contract-negotiation-schema.sql");
        extension.runQuery(schema);
        leaseUtil = new LeaseUtil(extension.getTransactionContext(), extension::getConnection, statements.getContractNegotiationTable(),
                leaseStatements, clock);
    }

    @AfterEach
    void tearDown(PostgresqlStoreSetupExtension extension) {
        extension.runQuery("DROP TABLE " + statements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + statements.getContractAgreementTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + leaseStatements.getLeaseTableName() + " CASCADE");
    }

    @Override
    protected ContractNegotiationStore getContractNegotiationStore() {
        return store;
    }

    @Override
    protected void leaseEntity(String negotiationId, String owner, Duration duration) {
        leaseUtil.leaseEntity(negotiationId, owner, duration);
    }

    @Override
    protected boolean isLeasedBy(String negotiationId, String owner) {
        return leaseUtil.isLeased(negotiationId, owner);
    }
}
