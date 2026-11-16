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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.apicatalog.did.Did;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.scheduling.DataspaceParticipantProvisioningWorker;
import org.niis.xroad.securityserver.restapi.scheduling.DataspacePublicationStage;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-lifecycle convergence tests for {@link DataspaceParticipantProvisioningWorker} driven against a
 * real {@link DataspaceProvisioningService}, wired with the stateful in-memory
 * {@link FakeIdentityHubProvisioningClient} and {@link FakeControlPlaneProvisioningClient} plus an
 * in-memory {@code ds_participant} binding table. Assertions target external state — the binding
 * table and the fakes — never internal call sequences, per the feature's testing decisions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspaceParticipantLifecycleConvergenceTest {

    private static final String SS_ADDRESS = "ih.example.test";
    private static final String SERVER_CODE = "SS0";
    private static final String IDENTITY_HUB_HOST = SS_ADDRESS + ":7183";
    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final ClientId OWNER = ClientId.Conf.create("TEST", "GOV", "owner");
    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "COM", "member");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("TEST", "COM", "other");
    private static final String MEMBER_CTX_ID = ParticipantIdentifierScheme.memberCtxId(MEMBER);
    private static final String OTHER_MEMBER_CTX_ID = ParticipantIdentifierScheme.memberCtxId(OTHER_MEMBER);
    private static final String SYSTEM_CTX_ID = ParticipantIdentifierScheme.SYSTEM_SEGMENT;
    private static final String CREDENTIAL_HOLDER_PID_SLOT0 = MEMBER_CTX_ID + "-"
            + ParticipantIdentifierScheme.memberCtxId(OWNER) + ":" + SERVER_CODE + "-xroad-membership-credential-request";

    @Mock
    private ClientRepository clientRepository;
    @Mock
    private ServerConfRepository serverConfRepository;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private GlobalConfProvider globalConfProvider;

    private final FakeIdentityHubProvisioningClient identityHubClient = new FakeIdentityHubProvisioningClient();
    private final FakeControlPlaneProvisioningClient controlPlaneClient = new FakeControlPlaneProvisioningClient();
    private final InMemoryBindingTable bindingTable = new InMemoryBindingTable();

    private DataspaceProvisioningService provisioningService;
    private DataspaceParticipantProvisioningWorker worker;

    @BeforeEach
    void setUp() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + IDENTITY_HUB_HOST.split(":")[0]);
        when(dataspace.getCredentialDefinitionId()).thenReturn("xroad-membership-credential-definition");
        when(dataspace.getMaxHolderPidSlots()).thenReturn(20);
        when(dataspace.getProvisioningBatchSize()).thenReturn(100);
        when(dataspace.getDidPort()).thenReturn(7183);
        when(dataspace.getIdentityHubStsPort()).thenReturn(7184);
        when(dataspace.getIdentityHubCredentialsPort()).thenReturn(7185);
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        when(readinessPredicates.isGlobalConfUsable()).thenReturn(true);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of("did:web:issuer.example.test"));
        bindingTable.wireOnto(dsParticipantRepository);
        givenServerOwnedBy(OWNER);

        var ownSecurityServerResolver = new OwnSecurityServerResolver(
                new ServerConfRepositoryIdentitySource(serverConfRepository), globalConfProvider);
        var didAuthority = new DataspaceDidAuthority(ownSecurityServerResolver, adminServiceProperties);
        provisioningService = new DataspaceProvisioningService(adminServiceProperties, identityHubClient,
                controlPlaneClient, clientRepository, ownSecurityServerResolver, dsParticipantRepository,
                globalConfProvider, didAuthority);
        worker = new DataspaceParticipantProvisioningWorker(provisioningService, readinessPredicates,
                new DataspaceParticipantBindingService(dsParticipantRepository, didAuthority), adminServiceProperties,
                mock(DataspacePublicationStage.class));
    }

    @Test
    void lastClientRemovalConvergesWithinOneTick() {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();

        givenMemberHasNoClients();
        bindingTable.decommission(MEMBER);
        worker.provisionParticipant();

        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
    }

    @Test
    void lastClientRemovalDeletesThePublicationStateTogetherWithTheBindingRow() {
        bindingTable.seedActive(MEMBER);
        bindingTable.markPublished(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        assertThat(bindingTable.publicationStateOf(MEMBER)).contains(PublicationState.PUBLISHED);

        givenMemberHasNoClients();
        bindingTable.decommission(MEMBER);
        worker.provisionParticipant();

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(bindingTable.publicationStateOf(MEMBER)).isEmpty();
    }

    @Test
    void reAddingClientAfterTeardownConvergenceProvisionsFreshContextOnTheNextTick() {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        var firstGeneration = identityHubClient.contextGeneration(MEMBER_CTX_ID).orElseThrow();
        assertThat(identityHubClient.hasCredentialRequest(MEMBER_CTX_ID, CREDENTIAL_HOLDER_PID_SLOT0)).isTrue();

        givenMemberHasNoClients();
        bindingTable.decommission(MEMBER);
        worker.provisionParticipant();
        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(identityHubClient.hasCredentialRequest(MEMBER_CTX_ID, CREDENTIAL_HOLDER_PID_SLOT0)).isFalse();

        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();

        var secondGeneration = identityHubClient.contextGeneration(MEMBER_CTX_ID).orElseThrow();
        assertThat(secondGeneration).isGreaterThan(firstGeneration);
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(identityHubClient.hasCredentialRequest(MEMBER_CTX_ID, CREDENTIAL_HOLDER_PID_SLOT0)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void killAtEachProvisioningStepConvergesOnTheNextCleanTick(int failAtStep) {
        givenMemberHasRegisteredClient(MEMBER);
        failProvisioningStep(failAtStep);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();
        worker.provisionParticipant();

        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasConfig(MEMBER_CTX_ID)).isTrue();
        assertThat(identityHubClient.hasCredentialRequest(MEMBER_CTX_ID, CREDENTIAL_HOLDER_PID_SLOT0)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void killAtEachTeardownStepConvergesOnTheNextCleanTick(int failAtStep) {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        givenMemberHasNoClients();
        bindingTable.decommission(MEMBER);
        failTeardownStep(failAtStep);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();
        worker.provisionParticipant();

        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
    }

    @Test
    void tombstonedMemberIsNeverProvisionedBeforeItsRowIsDeleted() {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        var firstGeneration = identityHubClient.contextGeneration(MEMBER_CTX_ID).orElseThrow();

        // the member's client is still registered (a re-add raced the teardown); this tick's own
        // teardown attempt is made to fail, so the tombstone row survives past it
        bindingTable.decommission(MEMBER);
        controlPlaneClient.failNextDeleteContext(MEMBER_CTX_ID);
        worker.provisionParticipant();

        assertThat(bindingTable.hasRow(MEMBER)).isTrue();
        assertThat(bindingTable.stateOf(MEMBER)).isEqualTo(ParticipantState.DECOMMISSIONED);
        assertThat(identityHubClient.contextGeneration(MEMBER_CTX_ID)).contains(firstGeneration);

        worker.provisionParticipant();

        assertThat(bindingTable.stateOf(MEMBER)).isEqualTo(ParticipantState.ACTIVE);
        var secondGeneration = identityHubClient.contextGeneration(MEMBER_CTX_ID).orElseThrow();
        assertThat(secondGeneration).isGreaterThan(firstGeneration);
    }

    @Test
    void memberWithNoBindingRowIsUntouchedByTeardown() {
        givenMemberHasRegisteredClient(MEMBER);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(bindingTable.stateOf(MEMBER)).isEqualTo(ParticipantState.ACTIVE);
    }

    @Test
    void tombstoneWithAlreadyAbsentContextsConvergesOnFirstTickWithoutErrors() {
        bindingTable.seedDecommissioned(MEMBER);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
    }

    @Test
    void teardownConvergesWithNoCentralServerDependencyInvolved() {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        givenMemberHasNoClients();
        bindingTable.decommission(MEMBER);

        worker.provisionParticipant();

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
    }

    @Test
    void aNewMemberIsBoundBeforeItsContextIsProvisioned() {
        givenMemberHasRegisteredClient(MEMBER);
        identityHubClient.failNextCreateContext(MEMBER_CTX_ID);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        assertThat(bindingTable.stateOf(MEMBER)).isEqualTo(ParticipantState.ACTIVE);
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
    }

    @Test
    void aMemberThatCannotBeBoundIsNotProvisionedAndTheOthersAre() {
        givenMembersHaveRegisteredClients(MEMBER, OTHER_MEMBER);
        bindingTable.failBindOf(MEMBER);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(bindingTable.stateOf(OTHER_MEMBER)).isEqualTo(ParticipantState.ACTIVE);
        assertThat(identityHubClient.hasContext(OTHER_MEMBER_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(OTHER_MEMBER_CTX_ID)).isTrue();
    }

    @Test
    void noMemberIsBoundOrProvisionedWhileDataspaceIsNotReadyAndBothFollowOnceItIs() {
        givenMemberHasRegisteredClient(MEMBER);
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);

        worker.provisionParticipant();

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();

        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        worker.provisionParticipant();

        assertThat(bindingTable.stateOf(MEMBER)).isEqualTo(ParticipantState.ACTIVE);
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();
    }

    @Test
    void aBoundMemberWithoutContextsIsReprovisionedWhileDataspaceIsNotReady() {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);

        worker.provisionParticipant();

        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isTrue();
    }

    @Test
    void noMemberIsBoundOrProvisionedUntilTheAuthCertIsRegistered() {
        givenMemberHasRegisteredClient(MEMBER);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);

        worker.provisionParticipant();

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
    }

    @Test
    void aRemovedMembersTeardownStillRunsWhileDataspaceIsNotReady() {
        bindingTable.seedActive(MEMBER);
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();

        givenMemberHasNoClients();
        bindingTable.decommission(MEMBER);
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);
        worker.provisionParticipant();

        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
    }

    @Test
    void aSecondPassChangesNoBindingRow() {
        givenMembersHaveRegisteredClients(MEMBER, OTHER_MEMBER);
        worker.provisionParticipant();
        var memberRow = bindingTable.findByMember(MEMBER).orElseThrow();
        var memberDid = memberRow.getDid();
        var rowCount = bindingTable.rowCount();

        worker.provisionParticipant();

        assertThat(bindingTable.rowCount()).isEqualTo(rowCount);
        assertThat(bindingTable.findByMember(MEMBER).orElseThrow()).isSameAs(memberRow);
        assertThat(memberRow.getDid()).isEqualTo(memberDid);
        assertThat(identityHubClient.contextDid(MEMBER_CTX_ID)).contains(Did.parse(memberDid));
    }

    @Test
    void theSystemContextIsBoundOnlyOnceTheAuthCertIsRegisteredAndProvisionedAfterIt() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);

        worker.provisionParticipant();

        assertThat(bindingTable.hasSystemRow()).isFalse();
        assertThat(identityHubClient.hasContext(SYSTEM_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isFalse();

        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        worker.provisionParticipant();

        assertThat(bindingTable.systemRow()).hasValueSatisfying(row -> {
            assertThat(row.getState()).isEqualTo(ParticipantState.ACTIVE);
            assertThat(row.getDid()).isEqualTo(ParticipantIdentifierScheme.systemDid(IDENTITY_HUB_HOST).toString());
        });
        assertThat(identityHubClient.hasContext(SYSTEM_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isTrue();
    }

    @Test
    void theSystemContextIsNotProvisionedWhileItCannotBeBound() {
        bindingTable.failSystemBind();

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        assertThat(bindingTable.hasSystemRow()).isFalse();
        assertThat(identityHubClient.hasContext(SYSTEM_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isFalse();
    }

    @Test
    void aSecondPassWritesNoSecondSystemRowAndChangesNoSystemBinding() {
        worker.provisionParticipant();
        var systemRow = bindingTable.systemRow().orElseThrow();
        var systemDid = systemRow.getDid();
        var rowCount = bindingTable.rowCount();

        worker.provisionParticipant();

        assertThat(bindingTable.rowCount()).isEqualTo(rowCount);
        assertThat(bindingTable.systemRow().orElseThrow()).isSameAs(systemRow);
        assertThat(systemRow.getDid()).isEqualTo(systemDid);
        assertThat(identityHubClient.contextDid(SYSTEM_CTX_ID)).contains(Did.parse(systemDid));
    }

    @Test
    void aRejectedHostIsRejectedInTheStatusAndUnboundUntilTheAddressBecomesValid() {
        givenMemberHasRegisteredClient(MEMBER);
        givenRegisteredAddress("b\u00fccher.example");

        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(identityStatuses()).containsExactly(IdentityStatus.REJECTED, IdentityStatus.REJECTED);
        assertThat(bindingTable.rowCount()).isZero();
        assertThat(identityHubClient.hasContext(SYSTEM_CTX_ID)).isFalse();
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isFalse();

        givenRegisteredAddress(SS_ADDRESS);
        worker.provisionParticipant();

        assertThat(identityStatuses()).doesNotContain(IdentityStatus.REJECTED);
        assertThat(bindingTable.hasSystemRow()).isTrue();
        assertThat(bindingTable.stateOf(MEMBER)).isEqualTo(ParticipantState.ACTIVE);
        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();
    }

    @Test
    void aChangedRegisteredAddressLeavesEveryBoundDidUnchangedAndConverged() {
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        var dids = boundAndServedDids();

        givenRegisteredAddress("ss2.example.test");
        worker.provisionParticipant();
        var afterFirstPass = boundAndServedDids();
        worker.provisionParticipant();

        assertThat(afterFirstPass).isEqualTo(dids);
        assertThat(boundAndServedDids()).isEqualTo(dids);
        assertThat(identityStatuses()).containsOnly(IdentityStatus.OK);
        assertThat(bindingTable.rowCount()).isEqualTo(2);
    }

    @Test
    void aChangedDidPortLeavesEveryBoundDidUnchanged() {
        givenMemberHasRegisteredClient(MEMBER);
        worker.provisionParticipant();
        var dids = boundAndServedDids();
        assertThat(dids).hasSize(4).doesNotContainNull();
        assertThat(bindingTable.rowCount()).isEqualTo(2);

        when(dataspace.getDidPort()).thenReturn(8443);
        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(boundAndServedDids()).isEqualTo(dids);
        assertThat(identityStatuses()).containsOnly(IdentityStatus.OK);
    }

    @Test
    void aBoundContextStaysOkAndProvisionedWhenItsAddressBecomesNonMintable() {
        givenMemberHasRegisteredClient(MEMBER);
        bindingTable.seedActive(MEMBER);
        bindingTable.seedSystem();
        givenRegisteredAddress("b\u00fccher.example");

        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(identityStatuses()).containsOnly(IdentityStatus.OK);
        assertThat(identityHubClient.contextDid(MEMBER_CTX_ID))
                .contains(ParticipantIdentifierScheme.memberDid(MEMBER, IDENTITY_HUB_HOST));
        assertThat(identityHubClient.contextDid(SYSTEM_CTX_ID))
                .contains(ParticipantIdentifierScheme.systemDid(IDENTITY_HUB_HOST));
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isTrue();
        assertThat(bindingTable.rowCount()).isEqualTo(2);
    }

    @Test
    void boundContextsLostFromTheHubAreRecreatedWithTheirBoundDidWhileTheRegisteredAddressIsUnknown() {
        givenMembersHaveRegisteredClients(MEMBER, OTHER_MEMBER);
        bindingTable.seedActive(MEMBER);
        bindingTable.seedSystem();
        givenRegisteredAddress(null);

        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(identityHubClient.contextDid(MEMBER_CTX_ID)).contains(Did.parse(bindingTable.didOf(MEMBER_CTX_ID)));
        assertThat(identityHubClient.contextDid(SYSTEM_CTX_ID)).contains(Did.parse(bindingTable.didOf(SYSTEM_CTX_ID)));
        assertThat(controlPlaneClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isTrue();
        assertThat(identityHubClient.hasContext(OTHER_MEMBER_CTX_ID)).isFalse();
        assertThat(controlPlaneClient.hasContext(OTHER_MEMBER_CTX_ID)).isFalse();
        assertThat(bindingTable.didOf(OTHER_MEMBER_CTX_ID)).isNull();
        assertThat(bindingTable.rowCount()).isEqualTo(2);
    }

    @Test
    void aSystemContextWithoutABindingRowIsNotLeftConvergedAndIsBoundAgain() {
        worker.provisionParticipant();
        worker.provisionParticipant();
        bindingTable.removeSystemRow();

        worker.provisionParticipant();

        assertThat(bindingTable.hasSystemRow()).isTrue();
    }

    @Test
    void aBoundSystemContextWithoutHubContextIsReprovisionedWhileDataspaceIsNotReady() {
        bindingTable.seedSystem();
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);

        worker.provisionParticipant();

        assertThat(identityHubClient.hasContext(SYSTEM_CTX_ID)).isTrue();
        assertThat(controlPlaneClient.hasContext(SYSTEM_CTX_ID)).isTrue();
    }

    private void failProvisioningStep(int step) {
        switch (step) {
            case 1 -> identityHubClient.failNextCreateContext(MEMBER_CTX_ID);
            case 2 -> controlPlaneClient.failNextCreateContext(MEMBER_CTX_ID);
            case 3 -> controlPlaneClient.failNextPutConfig(MEMBER_CTX_ID);
            case 4 -> identityHubClient.failNextRequestCredential(MEMBER_CTX_ID);
            default -> throw new IllegalArgumentException("unknown provisioning step " + step);
        }
    }

    private void failTeardownStep(int step) {
        switch (step) {
            case 1 -> controlPlaneClient.failNextDeleteContext(MEMBER_CTX_ID);
            case 2 -> identityHubClient.failNextDeleteContext(MEMBER_CTX_ID);
            case 3 -> bindingTable.failNextDelete();
            default -> throw new IllegalArgumentException("unknown teardown step " + step);
        }
    }

    private void givenServerOwnedBy(ClientId owner) {
        var ownerEntity = mock(ClientEntity.class);
        when(ownerEntity.getIdentifier()).thenReturn(ClientIdEntityFactory.create(owner));
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(ownerEntity);
        when(serverConf.getServerCode()).thenReturn(SERVER_CODE);
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);
        when(globalConfProvider.getSecurityServerAddress(SecurityServerId.Conf.create(owner, SERVER_CODE)))
                .thenReturn(SS_ADDRESS);
    }

    private void givenRegisteredAddress(String address) {
        when(globalConfProvider.getSecurityServerAddress(SecurityServerId.Conf.create(OWNER, SERVER_CODE)))
                .thenReturn(address);
    }

    private List<String> boundAndServedDids() {
        return Stream.of(SYSTEM_CTX_ID, MEMBER_CTX_ID)
                .flatMap(ctxId -> Stream.of(
                        bindingTable.didOf(ctxId),
                        identityHubClient.contextDid(ctxId).map(Did::toString).orElse(null)))
                .toList();
    }

    private List<IdentityStatus> identityStatuses() {
        return provisioningService.participantContexts().stream()
                .map(context -> provisioningService.readContextStatus(context).identityStatus())
                .toList();
    }

    private void givenMemberHasRegisteredClient(ClientId member) {
        var client = mock(ClientEntity.class);
        when(client.getIdentifier()).thenReturn(ClientIdEntityFactory.create(member));
        when(client.getClientStatus()).thenReturn(Client.STATUS_REGISTERED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(client));
    }

    private void givenMembersHaveRegisteredClients(ClientId... members) {
        var clients = Arrays.stream(members).map(member -> {
            var client = mock(ClientEntity.class);
            when(client.getIdentifier()).thenReturn(ClientIdEntityFactory.create(member));
            when(client.getClientStatus()).thenReturn(Client.STATUS_REGISTERED);
            return client;
        }).toList();
        when(clientRepository.getAllLocalClients()).thenReturn(clients);
    }

    private void givenMemberHasNoClients() {
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());
    }

    /**
     * In-memory model of the {@code ds_participant} binding table, wired onto a
     * {@link DsParticipantRepository} mock so the real {@link DataspaceProvisioningService} can be
     * exercised against it without a database.
     */
    private static final class InMemoryBindingTable {
        private final Map<Long, DsParticipantEntity> rowsById = new LinkedHashMap<>();
        private final Map<Long, ClientId> memberById = new HashMap<>();
        private final Map<ClientId, Long> idByMember = new HashMap<>();
        private final AtomicLong idSeq = new AtomicLong();
        private boolean failOnNextDelete;
        private boolean failOnSystemBind;
        private Long systemRowId;
        private final Set<ClientId> unbindable = new HashSet<>();

        void seedActive(ClientId member) {
            seed(member, ParticipantState.ACTIVE);
        }

        void markPublished(ClientId member) {
            rowsById.get(idByMember.get(member)).setPublicationState(PublicationState.PUBLISHED);
        }

        Optional<PublicationState> publicationStateOf(ClientId member) {
            return Optional.ofNullable(idByMember.get(member)).map(rowsById::get).map(DsParticipantEntity::getPublicationState);
        }

        void seedDecommissioned(ClientId member) {
            seed(member, ParticipantState.DECOMMISSIONED);
        }

        boolean decommission(ClientId member) {
            var id = idByMember.get(member);
            if (id == null) {
                return false;
            }
            rowsById.get(id).setState(ParticipantState.DECOMMISSIONED);
            return true;
        }

        boolean delete(Long id) {
            if (failOnNextDelete) {
                failOnNextDelete = false;
                throw new FakeProvisioningException("ds_participant row delete " + id);
            }
            var removed = rowsById.remove(id);
            if (removed == null) {
                return false;
            }
            if (id.equals(systemRowId)) {
                systemRowId = null;
            } else {
                idByMember.remove(memberById.remove(id));
            }
            return true;
        }

        String didOf(String ctxId) {
            return rowsById.values().stream()
                    .filter(row -> row.getCtxId().equals(ctxId))
                    .map(DsParticipantEntity::getDid)
                    .findFirst()
                    .orElse(null);
        }

        Optional<DsParticipantEntity> systemRow() {
            return Optional.ofNullable(systemRowId).map(rowsById::get);
        }

        boolean hasSystemRow() {
            return systemRowId != null;
        }

        void removeSystemRow() {
            delete(systemRowId);
        }

        void failSystemBind() {
            failOnSystemBind = true;
        }

        void seedSystem() {
            bindSystem(ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                    ParticipantIdentifierScheme.systemDid(IDENTITY_HUB_HOST).toString());
        }

        Optional<DsParticipantEntity> findByMember(ClientId member) {
            return Optional.ofNullable(idByMember.get(member)).map(rowsById::get);
        }

        List<DsParticipantEntity> findDecommissioned() {
            return rowsById.values().stream().filter(row -> row.getState() == ParticipantState.DECOMMISSIONED).toList();
        }

        boolean hasRow(ClientId member) {
            return idByMember.containsKey(member);
        }

        ParticipantState stateOf(ClientId member) {
            return rowsById.get(idByMember.get(member)).getState();
        }

        void failBindOf(ClientId member) {
            unbindable.add(member);
        }

        int rowCount() {
            return rowsById.size();
        }

        void failNextDelete() {
            failOnNextDelete = true;
        }

        void wireOnto(DsParticipantRepository repository) {
            when(repository.findByMemberIdentifier(any())).thenAnswer(invocation -> findByMember(invocation.getArgument(0)));
            when(repository.bindMemberParticipant(any(), anyString(), anyString())).thenAnswer(invocation -> {
                ClientId member = invocation.getArgument(0);
                if (unbindable.contains(member)) {
                    throw new FakeProvisioningException("ds_participant row insert " + member);
                }
                return bind(member, invocation.getArgument(1), invocation.getArgument(2));
            });
            when(repository.findSystemParticipant()).thenAnswer(invocation -> systemRow());
            when(repository.bindSystemParticipant(anyString(), anyString())).thenAnswer(invocation -> {
                if (failOnSystemBind) {
                    throw new FakeProvisioningException("ds_participant SYSTEM row insert");
                }
                return bindSystem(invocation.getArgument(0), invocation.getArgument(1));
            });
            when(repository.decommissionMember(any())).thenAnswer(invocation -> decommission(invocation.getArgument(0)));
            when(repository.findDecommissioned()).thenAnswer(invocation -> findDecommissioned());
            when(repository.delete(anyLong())).thenAnswer(invocation -> delete(invocation.getArgument(0)));
        }

        DsParticipantEntity bindSystem(String ctxId, String did) {
            var entity = new DsParticipantEntity();
            var id = idSeq.incrementAndGet();
            entity.setId(id);
            entity.setParticipantType(ParticipantType.SYSTEM);
            entity.setCtxId(ctxId);
            entity.setDid(did);
            entity.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
            entity.setState(ParticipantState.ACTIVE);
            rowsById.put(id, entity);
            systemRowId = id;
            return entity;
        }

        DsParticipantEntity bind(ClientId member, String ctxId, String did) {
            var entity = new DsParticipantEntity();
            var id = idSeq.incrementAndGet();
            entity.setId(id);
            entity.setParticipantType(ParticipantType.MEMBER);
            entity.setMemberIdentifier(ClientIdEntityFactory.create(member));
            entity.setCtxId(ctxId);
            entity.setDid(did);
            entity.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
            entity.setState(ParticipantState.ACTIVE);
            rowsById.put(id, entity);
            memberById.put(id, member);
            idByMember.put(member, id);
            return entity;
        }

        private void seed(ClientId member, ParticipantState state) {
            bind(member, ParticipantIdentifierScheme.memberCtxId(member),
                    ParticipantIdentifierScheme.memberDid(member, IDENTITY_HUB_HOST).toString()).setState(state);
        }
    }
}
