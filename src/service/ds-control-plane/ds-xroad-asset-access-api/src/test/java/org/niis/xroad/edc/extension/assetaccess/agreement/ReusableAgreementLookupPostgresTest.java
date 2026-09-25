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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.policy.model.Action;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyRegistrationTypes;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.BaseSqlLeaseStatements;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilderImpl;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL-backed coverage of {@link ReusableAgreementLookup} against the stock EDC agreement store, since
 * {@code queryAgreements} is unmodified stock behaviour on the X-Road negotiation store wrapper.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class ReusableAgreementLookupPostgresTest {

    private final LeaseStatements leaseStatements = new BaseSqlLeaseStatements();
    private final PostgresDialectStatements statements = new PostgresDialectStatements(leaseStatements, Clock.systemUTC());

    private PostgresqlStoreSetupExtension extension;
    private QueryExecutor queryExecutor;
    private ContractNegotiationStore store;
    private ReusableAgreementLookup lookup;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension postgresExtension, QueryExecutor sqlQueryExecutor) throws IOException {
        this.extension = postgresExtension;
        this.queryExecutor = sqlQueryExecutor;
        this.store = buildStore();
        this.lookup = new ReusableAgreementLookup(store, extension.getTransactionContext());

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
    void findsMatchingAgreement() {
        var participantContextId = "participant-" + UUID.randomUUID();
        saveAgreement(store, "neg-1", participantContextId, "asset-1", "provider-1", Instant.now());

        var result = lookup.find(participantContextId, "consumer", "asset-1", "provider-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("agreement-neg-1");
    }

    @Test
    void newestContractSigningDateWins() {
        var participantContextId = "participant-" + UUID.randomUUID();
        var older = Instant.now().minusSeconds(120);
        var newer = Instant.now();

        saveAgreement(store, "neg-old", participantContextId, "asset-1", "provider-1", older);
        saveAgreement(store, "neg-new", participantContextId, "asset-1", "provider-1", newer);

        var result = lookup.find(participantContextId, "consumer", "asset-1", "provider-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("agreement-neg-new");
    }

    @Test
    void returnsEmptyWhenNoAgreementExists() {
        var result = lookup.find("participant-" + UUID.randomUUID(), "consumer", "asset-1", "provider-1");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForDifferentAsset() {
        var participantContextId = "participant-" + UUID.randomUUID();
        saveAgreement(store, "neg-1", participantContextId, "asset-1", "provider-1", Instant.now());

        var result = lookup.find(participantContextId, "consumer", "other-asset", "provider-1");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForDifferentProvider() {
        var participantContextId = "participant-" + UUID.randomUUID();
        saveAgreement(store, "neg-1", participantContextId, "asset-1", "provider-1", Instant.now());

        var result = lookup.find(participantContextId, "consumer", "asset-1", "other-provider");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForAgreementHeldAsProviderForAnotherConsumer() {
        var participantContextId = "participant-" + UUID.randomUUID();
        saveAgreement(store, "neg-provider-role", participantContextId, "other-consumer", "asset-1", "provider-1",
                ContractNegotiation.Type.PROVIDER, Instant.now());

        var result = lookup.find(participantContextId, "consumer", "asset-1", "provider-1");

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresNewerAgreementOfAnotherConsumerOnTheSameKey() {
        var participantContextId = "participant-" + UUID.randomUUID();
        saveAgreement(store, "neg-own", participantContextId, "consumer", "asset-1", "provider-1",
                ContractNegotiation.Type.CONSUMER, Instant.now().minusSeconds(120));
        saveAgreement(store, "neg-other", participantContextId, "other-consumer", "asset-1", "provider-1",
                ContractNegotiation.Type.PROVIDER, Instant.now());

        var result = lookup.find(participantContextId, "consumer", "asset-1", "provider-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("agreement-neg-own");
    }

    @Test
    void findsAgreementWrittenThroughSecondIndependentlyConstructedStoreInstance() throws IOException {
        var participantContextId = "participant-" + UUID.randomUUID();
        var secondStore = buildStore();

        saveAgreement(secondStore, "neg-1", participantContextId, "asset-1", "provider-1", Instant.now());

        var result = lookup.find(participantContextId, "consumer", "asset-1", "provider-1");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("agreement-neg-1");
    }

    private ContractNegotiationStore buildStore() throws IOException {
        var manager = new JacksonTypeManager();
        manager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));

        var leaseContextBuilder = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), "test-connector",
                statements.getContractNegotiationTable(), leaseStatements, Clock.systemUTC(), queryExecutor);

        return new SqlContractNegotiationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), statements, leaseContextBuilder, queryExecutor);
    }

    private void saveAgreement(ContractNegotiationStore targetStore, String negotiationId, String participantContextId,
                                String assetId, String providerId, Instant signingDate) {
        saveAgreement(targetStore, negotiationId, participantContextId, "consumer", assetId, providerId,
                ContractNegotiation.Type.CONSUMER, signingDate);
    }

    private void saveAgreement(ContractNegotiationStore targetStore, String negotiationId, String participantContextId,
                                String consumerId, String assetId, String providerId, ContractNegotiation.Type type,
                                Instant signingDate) {
        var agreement = ContractAgreement.Builder.newInstance()
                .id("agreement-" + negotiationId)
                .agreementId("wire-" + negotiationId)
                .providerId(providerId)
                .consumerId(consumerId)
                .assetId(assetId)
                .contractSigningDate(signingDate.getEpochSecond())
                .participantContextId(participantContextId)
                .policy(policy())
                .build();

        var negotiation = ContractNegotiation.Builder.newInstance()
                .id(negotiationId)
                .type(type)
                .state(ContractNegotiationStates.FINALIZED.code())
                .correlationId("corr-" + negotiationId)
                .counterPartyAddress("http://provider/dsp")
                .counterPartyId(providerId)
                .protocol("protocol")
                .participantContextId(participantContextId)
                .contractAgreement(agreement)
                .build();

        assertThat(targetStore.save(negotiation).succeeded()).isTrue();
    }

    private Policy policy() {
        return Policy.Builder.newInstance()
                .permission(Permission.Builder.newInstance()
                        .action(Action.Builder.newInstance().type("use").build())
                        .build())
                .build();
    }
}
