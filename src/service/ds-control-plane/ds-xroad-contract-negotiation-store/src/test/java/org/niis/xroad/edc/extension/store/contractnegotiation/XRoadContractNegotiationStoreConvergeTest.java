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

import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyRegistrationTypes;
import org.eclipse.edc.spi.types.domain.callback.CallbackAddress;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.BaseSqlLeaseStatements;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilderImpl;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.edc.extension.store.contractnegotiation.schema.postgres.XRoadPostgresContractNegotiationStatements;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;

/**
 * Reproduces the same-context self-negotiation collision (two independently-generated internal ids racing for the
 * same wire agreement id + participant context id pair) directly against Postgres. The in-memory store cannot
 * reproduce this: it does not model the composite unique constraint at all, so it is never used here as evidence.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class XRoadContractNegotiationStoreConvergeTest {

    private final LeaseStatements leaseStatements = new BaseSqlLeaseStatements();
    private final XRoadPostgresContractNegotiationStatements statements =
            new XRoadPostgresContractNegotiationStatements(leaseStatements, Clock.systemUTC());
    private PostgresqlStoreSetupExtension extension;
    private XRoadContractNegotiationStore store;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension setupExtension, QueryExecutor queryExecutor) throws IOException {
        this.extension = setupExtension;
        var manager = new JacksonTypeManager();
        manager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));

        var leaseContextBuilder = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), "test-connector",
                statements.getContractNegotiationTable(), leaseStatements, Clock.systemUTC(), queryExecutor);

        var delegate = new SqlContractNegotiationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), statements, leaseContextBuilder, queryExecutor);

        store = new XRoadContractNegotiationStore(delegate, extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), statements, leaseContextBuilder, queryExecutor);

        var schema = TestUtils.getResourceFileContentAsString("contract-negotiation-schema.sql");
        extension.runQuery(schema);
    }

    @AfterEach
    void tearDown() {
        extension.runQuery("DROP TABLE " + statements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + statements.getContractAgreementTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + leaseStatements.getLeaseTableName() + " CASCADE");
    }

    @Test
    void twoSavesWithDistinctInternalIdsAndSameCompositePairConvergeOnOneAgreementRow() {
        var agreementId = "agreement-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();

        var providerNegotiation = negotiation("neg-provider", participantContextId,
                agreement("internal-provider", agreementId, participantContextId, "provider-claims"));
        var consumerNegotiation = negotiation("neg-consumer", participantContextId,
                agreement("internal-consumer", agreementId, participantContextId, "consumer-claims"));

        assertThat(store.save(providerNegotiation)).isSucceeded();
        assertThat(store.save(consumerNegotiation)).isSucceeded();

        assertThat(agreementRowCount(agreementId, participantContextId)).isEqualTo(1);

        var survivingId = survivingAgreementId(agreementId, participantContextId);
        assertThat(negotiationAgreementFk("neg-provider")).isEqualTo(survivingId);
        assertThat(negotiationAgreementFk("neg-consumer")).isEqualTo(survivingId);
    }

    @Test
    void concurrentSavesWithSameCompositePairConvergeOnOneAgreementRow() throws Exception {
        var agreementId = "agreement-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();

        var providerNegotiation = negotiation("neg-provider", participantContextId,
                agreement("internal-provider", agreementId, participantContextId, "provider-claims"));
        var consumerNegotiation = negotiation("neg-consumer", participantContextId,
                agreement("internal-consumer", agreementId, participantContextId, "consumer-claims"));

        var barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var providerSave = executor.submit(() -> {
                barrier.await();
                return store.save(providerNegotiation);
            });
            var consumerSave = executor.submit(() -> {
                barrier.await();
                return store.save(consumerNegotiation);
            });

            assertThat(providerSave.get(15, TimeUnit.SECONDS)).isSucceeded();
            assertThat(consumerSave.get(15, TimeUnit.SECONDS)).isSucceeded();
        } finally {
            executor.shutdownNow();
        }

        assertThat(agreementRowCount(agreementId, participantContextId)).isEqualTo(1);

        var survivingId = survivingAgreementId(agreementId, participantContextId);
        assertThat(negotiationAgreementFk("neg-provider")).isEqualTo(survivingId);
        assertThat(negotiationAgreementFk("neg-consumer")).isEqualTo(survivingId);
    }

    @Test
    void distinctCompositePairsProduceDistinctAgreementRows() {
        var participantContextId = "participant-" + UUID.randomUUID();

        var negotiation1 = negotiation("neg-1", participantContextId,
                agreement("internal-1", "agreement-" + UUID.randomUUID(), participantContextId, "claims-1"));
        var negotiation2 = negotiation("neg-2", participantContextId,
                agreement("internal-2", "agreement-" + UUID.randomUUID(), participantContextId, "claims-2"));

        assertThat(store.save(negotiation1)).isSucceeded();
        assertThat(store.save(negotiation2)).isSucceeded();

        assertThat(negotiationAgreementFk("neg-1"))
                .isNotEqualTo(negotiationAgreementFk("neg-2"))
                .isEqualTo("internal-1");
        assertThat(negotiationAgreementFk("neg-2")).isEqualTo("internal-2");
    }

    @Test
    void passThroughSaveDoesNotRewriteAgreementRow() {
        var agreementId = "agreement-" + UUID.randomUUID();
        var participantContextId = "participant-" + UUID.randomUUID();

        var negotiation = negotiation("neg-1", participantContextId,
                agreement("internal-1", agreementId, participantContextId, "original-claims"));

        assertThat(store.save(negotiation)).isSucceeded();
        assertThat(claimsFor(agreementId, participantContextId)).contains("original-claims");

        // Simulate the counterpart's converging write landing on the shared row out of band, the way it would
        // during real concurrent negotiation traffic.
        updateClaimsDirectly(agreementId, participantContextId, "counterpart-claims");
        assertThat(claimsFor(agreementId, participantContextId)).contains("counterpart-claims");

        // A negotiation-state-only save on the original negotiation object: same agreement content it already
        // wrote, only the state timestamp moves forward.
        negotiation.updateStateTimestamp();
        assertThat(store.save(negotiation)).isSucceeded();

        assertThat(claimsFor(agreementId, participantContextId))
                .as("pass-through save must not rewrite the agreement row and clobber the counterpart's claims")
                .contains("counterpart-claims");
    }

    private ContractAgreement agreement(String internalId, String agreementId, String participantContextId, String claimValue) {
        return ContractAgreement.Builder.newInstance()
                .id(internalId)
                .agreementId(agreementId)
                .participantContextId(participantContextId)
                .providerId("provider")
                .consumerId("consumer")
                .assetId("asset")
                .contractSigningDate(Instant.now().getEpochSecond())
                .policy(policy())
                .claims(Map.of("claim", claimValue))
                .build();
    }

    private ContractNegotiation negotiation(String id, String participantContextId, ContractAgreement agreement) {
        return ContractNegotiation.Builder.newInstance()
                .id(id)
                .type(ContractNegotiation.Type.CONSUMER)
                .state(ContractNegotiationStates.AGREED.code())
                .correlationId("corr-" + id)
                .counterPartyAddress("counterparty")
                .counterPartyId("counterpartyId")
                .callbackAddresses(List.of(CallbackAddress.Builder.newInstance().uri("local://test").events(Set.of("event")).build()))
                .protocol("protocol")
                .participantContextId(participantContextId)
                .contractAgreement(agreement)
                .build();
    }

    private Policy policy() {
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance()
                        .action(Action.Builder.newInstance().type("use").build())
                        .build())
                .build();
    }

    private long agreementRowCount(String agreementId, String participantContextId) {
        try (
                var connection = extension.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM " + statements.getContractAgreementTable()
                                + " WHERE " + statements.getContractAgreementContractIdColumn() + " = ?"
                                + " AND " + statements.getAgreementParticipantContextIdColumn() + " = ?")
        ) {
            statement.setString(1, agreementId);
            statement.setString(2, participantContextId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String survivingAgreementId(String agreementId, String participantContextId) {
        try (
                var connection = extension.getConnection();
                var statement = connection.prepareStatement(statements.getFindAgreementByCompositeKeyTemplate())
        ) {
            statement.setString(1, agreementId);
            statement.setString(2, participantContextId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(statements.getContractAgreementIdColumn());
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String negotiationAgreementFk(String negotiationId) {
        try (
                var connection = extension.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT " + statements.getContractAgreementIdFkColumn() + " FROM " + statements.getContractNegotiationTable()
                                + " WHERE " + statements.getIdColumn() + " = ?")
        ) {
            statement.setString(1, negotiationId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String claimsFor(String agreementId, String participantContextId) {
        try (
                var connection = extension.getConnection();
                var statement = connection.prepareStatement(
                        "SELECT " + statements.getClaimsColumn() + " FROM " + statements.getContractAgreementTable()
                                + " WHERE " + statements.getContractAgreementContractIdColumn() + " = ?"
                                + " AND " + statements.getAgreementParticipantContextIdColumn() + " = ?")
        ) {
            statement.setString(1, agreementId);
            statement.setString(2, participantContextId);
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateClaimsDirectly(String agreementId, String participantContextId, String claimValue) {
        try (
                var connection = extension.getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE " + statements.getContractAgreementTable() + " SET " + statements.getClaimsColumn() + " = ?::json"
                                + " WHERE " + statements.getContractAgreementContractIdColumn() + " = ?"
                                + " AND " + statements.getAgreementParticipantContextIdColumn() + " = ?")
        ) {
            statement.setString(1, "{\"claim\": \"" + claimValue + "\"}");
            statement.setString(2, agreementId);
            statement.setString(3, participantContextId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
