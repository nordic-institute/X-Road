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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.controlplane.contract.spi.types.negotiation.ContractNegotiationStates;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.SqlContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.connector.policy.monitor.spi.PolicyMonitorContext;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.Issuer;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.Permission;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.policy.model.PolicyRegistrationTypes;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.sql.lease.BaseSqlLeaseStatements;
import org.eclipse.edc.sql.lease.SqlLeaseContextBuilderImpl;
import org.eclipse.edc.sql.lease.spi.LeaseStatements;
import org.eclipse.edc.sql.testfixtures.PostgresqlStoreSetupExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadLocalGroupMemberConstraintFunction;
import org.niis.xroad.edc.extension.policy.controlplane.util.LocalGroupConstraintValue;
import org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies that the X-Road member identity survives the stock EDC agreement store's JSON persistence of the
 * agreement claims, which is the only source the policy monitor has for it.
 */
@ExtendWith(PostgresqlStoreSetupExtension.class)
class StoredAgreementClaimsPostgresTest {

    private static final ClientId LOCAL_GROUP_OWNER = ClientId.Conf.create("DEV", "COM", "111", "Owner");
    private static final String LOCAL_GROUP = LocalGroupConstraintValue.encode(LOCAL_GROUP_OWNER, "security-server-owners");

    private final LeaseStatements leaseStatements = new BaseSqlLeaseStatements();
    private final PostgresDialectStatements statements = new PostgresDialectStatements(leaseStatements, Clock.systemUTC());

    private PostgresqlStoreSetupExtension extension;
    private ContractNegotiationStore store;

    @BeforeEach
    void setUp(PostgresqlStoreSetupExtension postgresExtension, QueryExecutor queryExecutor) throws IOException {
        this.extension = postgresExtension;
        this.store = buildStore(queryExecutor);
        extension.runQuery(TestUtils.getResourceFileContentAsString("contract-negotiation-schema.sql"));
    }

    @AfterEach
    void tearDown() {
        extension.runQuery("DROP TABLE " + statements.getContractNegotiationTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + statements.getContractAgreementTable() + " CASCADE");
        extension.runQuery("DROP TABLE " + leaseStatements.getLeaseTableName() + " CASCADE");
    }

    @Test
    void memberIdentityIsResolvedFromReloadedAgreementClaims() {
        var context = monitorContextOf(saveAndReload("neg-1"));

        var memberId = PolicyContextHelper.findMemberIdFromContext(context);

        assertThat(memberId).contains(ClientId.Conf.create("DEV", "COM", "222"));
    }

    @Test
    void localGroupConstraintPassesForMemberStillInGroup() {
        var serverConfProvider = mock(ServerConfProvider.class);
        when(serverConfProvider.isLocalGroupMember(any(ClientId.class), any(LocalGroupId.class), any(ClientId.class)))
                .thenReturn(true);

        var result = evaluateLocalGroup(serverConfProvider, monitorContextOf(saveAndReload("neg-1")));

        assertThat(result).isTrue();
    }

    @Test
    void localGroupConstraintFailsForRemovedMember() {
        var serverConfProvider = mock(ServerConfProvider.class);
        when(serverConfProvider.isLocalGroupMember(any(ClientId.class), any(LocalGroupId.class), any(ClientId.class)))
                .thenReturn(false);

        var result = evaluateLocalGroup(serverConfProvider, monitorContextOf(saveAndReload("neg-1")));

        assertThat(result).isFalse();
    }

    private boolean evaluateLocalGroup(ServerConfProvider serverConfProvider, PolicyMonitorContext context) {
        var function = new XRoadLocalGroupMemberConstraintFunction<PolicyMonitorContext>(serverConfProvider, mock(Monitor.class));
        return function.evaluate(Operator.EQ, LOCAL_GROUP, mock(Permission.class), context);
    }

    private static PolicyMonitorContext monitorContextOf(ContractAgreement agreement) {
        return new PolicyMonitorContext(Instant.now(), agreement);
    }

    private ContractAgreement saveAndReload(String negotiationId) {
        var agreementId = "agreement-" + negotiationId;
        var agreement = ContractAgreement.Builder.newInstance()
                .id(agreementId)
                .agreementId("wire-" + negotiationId)
                .providerId("provider-1")
                .consumerId("consumer")
                .assetId("asset-1")
                .contractSigningDate(Instant.now().getEpochSecond())
                .participantContextId("participant-1")
                .policy(Policy.Builder.newInstance().build())
                .claims(Map.of("vc", List.of(membershipCredential("DEV", "COM", "222"))))
                .build();
        var negotiation = ContractNegotiation.Builder.newInstance()
                .id(negotiationId)
                .type(ContractNegotiation.Type.CONSUMER)
                .state(ContractNegotiationStates.FINALIZED.code())
                .correlationId("corr-" + negotiationId)
                .counterPartyAddress("http://provider/dsp")
                .counterPartyId("provider-1")
                .protocol("protocol")
                .participantContextId("participant-1")
                .contractAgreement(agreement)
                .build();
        assertThat(store.save(negotiation).succeeded()).isTrue();

        var reloaded = store.findContractAgreement(agreementId);
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.getClaims().get("vc")).asInstanceOf(InstanceOfAssertFactories.LIST)
                .isNotEmpty()
                .allSatisfy(entry -> assertThat(entry).isInstanceOf(Map.class));
        return reloaded;
    }

    private static VerifiableCredential membershipCredential(String instance, String memberClass, String memberCode) {
        var subject = CredentialSubject.Builder.newInstance()
                .claim("xroadInstance", instance)
                .claim("memberClass", memberClass)
                .claim("memberCode", memberCode)
                .build();
        return VerifiableCredential.Builder.newInstance()
                .type("XRoadMembershipCredential")
                .issuer(new Issuer("did:web:test-issuer"))
                .issuanceDate(Instant.now())
                .credentialSubject(subject)
                .build();
    }

    private ContractNegotiationStore buildStore(QueryExecutor queryExecutor) {
        var manager = new JacksonTypeManager();
        manager.registerTypes(PolicyRegistrationTypes.TYPES.toArray(Class<?>[]::new));

        var leaseContextBuilder = SqlLeaseContextBuilderImpl.with(extension.getTransactionContext(), "test-connector",
                statements.getContractNegotiationTable(), leaseStatements, Clock.systemUTC(), queryExecutor);

        return new SqlContractNegotiationStore(extension.getDataSourceRegistry(), extension.getDatasourceName(),
                extension.getTransactionContext(), manager.getMapper(), statements, leaseContextBuilder, queryExecutor);
    }
}
