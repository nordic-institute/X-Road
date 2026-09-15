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
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

    private static final String HOST_ID = "xrd-ss0";
    private static final String IDENTITY_HUB_HOST = "ih.example.test:7183";
    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final ClientId OWNER = ClientId.Conf.create("TEST", "GOV", "owner");
    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "COM", "member");
    private static final String MEMBER_CTX_ID = ParticipantIdentifierScheme.memberCtxId(MEMBER);
    private static final String CREDENTIAL_HOLDER_PID_SLOT0 = MEMBER_CTX_ID + "-xroad-membership-credential-request";

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

    private DataspaceParticipantProvisioningWorker worker;

    @BeforeEach
    void setUp() {
        when(dataspace.getParticipantId()).thenReturn(HOST_ID);
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + IDENTITY_HUB_HOST.split(":")[0]);
        when(dataspace.getCredentialDefinitionId()).thenReturn("xroad-membership-credential-definition");
        when(dataspace.getMaxHolderPidSlots()).thenReturn(20);
        when(dataspace.getIdentityHubDidPort()).thenReturn(7183);
        when(dataspace.getIdentityHubStsPort()).thenReturn(7184);
        when(dataspace.getIdentityHubCredentialsPort()).thenReturn(7185);
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of("did:web:issuer.example.test"));
        bindingTable.wireOnto(dsParticipantRepository);
        givenServerOwnedBy(OWNER);

        var service = new DataspaceProvisioningService(adminServiceProperties, identityHubClient, controlPlaneClient,
                clientRepository, serverConfRepository, dsParticipantRepository, globalConfProvider);
        worker = new DataspaceParticipantProvisioningWorker(service, readinessPredicates);
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

        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
        var secondGeneration = identityHubClient.contextGeneration(MEMBER_CTX_ID).orElseThrow();
        assertThat(secondGeneration).isGreaterThan(firstGeneration);
    }

    @Test
    void memberWithNoBindingRowIsUntouchedByTeardown() {
        givenMemberHasRegisteredClient(MEMBER);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        assertThat(identityHubClient.hasContext(MEMBER_CTX_ID)).isTrue();
        assertThat(bindingTable.hasRow(MEMBER)).isFalse();
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
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);
    }

    private void givenMemberHasRegisteredClient(ClientId member) {
        var client = mock(ClientEntity.class);
        when(client.getIdentifier()).thenReturn(ClientIdEntityFactory.create(member));
        when(client.getClientStatus()).thenReturn(Client.STATUS_REGISTERED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(client));
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

        void seedActive(ClientId member) {
            seed(member, ParticipantState.ACTIVE);
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
            idByMember.remove(memberById.remove(id));
            return true;
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

        void failNextDelete() {
            failOnNextDelete = true;
        }

        void wireOnto(DsParticipantRepository repository) {
            when(repository.findByMemberIdentifier(any())).thenAnswer(invocation -> findByMember(invocation.getArgument(0)));
            when(repository.decommissionMember(any())).thenAnswer(invocation -> decommission(invocation.getArgument(0)));
            when(repository.findDecommissioned()).thenAnswer(invocation -> findDecommissioned());
            when(repository.delete(anyLong())).thenAnswer(invocation -> delete(invocation.getArgument(0)));
        }

        private void seed(ClientId member, ParticipantState state) {
            var entity = new DsParticipantEntity();
            var id = idSeq.incrementAndGet();
            entity.setId(id);
            entity.setParticipantType(ParticipantType.MEMBER);
            entity.setMemberIdentifier(ClientIdEntityFactory.create(member));
            entity.setCtxId(ParticipantIdentifierScheme.memberCtxId(member));
            entity.setDid(ParticipantIdentifierScheme.memberDid(member, IDENTITY_HUB_HOST));
            entity.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
            entity.setState(state);
            rowsById.put(id, entity);
            memberById.put(id, member);
            idByMember.put(member, id);
        }
    }
}
