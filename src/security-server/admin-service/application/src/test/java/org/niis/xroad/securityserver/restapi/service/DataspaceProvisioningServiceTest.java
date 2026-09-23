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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apicatalog.did.Did;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.CredentialStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.IdentityHubProvisioningClient.CreateParticipantContextRequest;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceProvisioningServiceTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final String PARTICIPANT_ID = "test-participant";
    private static final String HOLDER_PID_SLOT0 = PARTICIPANT_ID + "-xroad-membership-credential-request";
    private static final String HOLDER_PID_SLOT1 = PARTICIPANT_ID + "-xroad-membership-credential-request-1";
    private static final String HOLDER_PID_SLOT2 = PARTICIPANT_ID + "-xroad-membership-credential-request-2";

    private static final ClientId OWNER = ClientId.Conf.create("TEST", "ORG", "OWNER");
    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "ORG", "MEMBER");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("TEST", "ORG", "OTHER");
    private static final SecurityServerId.Conf SERVER_ID = SecurityServerId.Conf.create(OWNER, "SS0");
    private static final String SS_ADDRESS = "ss.example.test";
    private static final String SS_HOST = SS_ADDRESS + ":7183";

    private static final ParticipantContext HOST_CONTEXT =
            new ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, OWNER);
    private static final ParticipantContext MEMBER_CONTEXT =
            new ParticipantContext(ParticipantIdentifierScheme.memberCtxId(MEMBER), ParticipantKind.MEMBER, MEMBER);
    private static final ParticipantContext SYSTEM_CONTEXT =
            new ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ParticipantKind.SYSTEM, OWNER);
    private static final ParticipantContext SYSTEM_CONTEXT_OTHER_OWNER =
            new ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ParticipantKind.SYSTEM, OTHER_MEMBER);
    private static final ParticipantContext SYSTEM_CONTEXT_NO_OWNER =
            new ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ParticipantKind.SYSTEM, null);

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;
    @Mock
    private IdentityHubProvisioningClient identityHubClient;
    @Mock
    private ControlPlaneProvisioningClient controlPlaneClient;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private ServerConfRepository serverConfRepository;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private GlobalConfProvider globalConfProvider;

    private DataspaceProvisioningService service;

    @BeforeEach
    void setUp() {
        lenient().when(dataspace.getParticipantId()).thenReturn(PARTICIPANT_ID);
        lenient().when(dataspace.getIdentityHubUrl()).thenReturn("https://ih.example.test");
        lenient().when(dataspace.getCredentialDefinitionId()).thenReturn("xroad-membership-credential-definition");
        lenient().when(dataspace.getMaxHolderPidSlots()).thenReturn(20);
        lenient().when(dataspace.getIdentityHubDidPort()).thenReturn(7183);
        lenient().when(dataspace.getIdentityHubStsPort()).thenReturn(7184);
        lenient().when(dataspace.getIdentityHubCredentialsPort()).thenReturn(7185);
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        lenient().when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        var ownerEntity = mock(ClientEntity.class);
        lenient().when(ownerEntity.getIdentifier()).thenReturn(ClientIdEntityFactory.create(OWNER));
        var serverConf = mock(ServerConfEntity.class);
        lenient().when(serverConf.getOwner()).thenReturn(ownerEntity);
        lenient().when(serverConf.getServerCode()).thenReturn(SERVER_ID.getServerCode());
        lenient().when(serverConfRepository.getServerConf()).thenReturn(serverConf);
        lenient().when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(SS_ADDRESS);
        lenient().when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        lenient().when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER))
                .thenReturn(List.of("did:web:issuer.example.test%3A6183:issuer"));
        var ownSecurityServerResolver = new OwnSecurityServerResolver(serverConfRepository, globalConfProvider);
        service = new DataspaceProvisioningService(adminServiceProperties, identityHubClient, controlPlaneClient,
                clientRepository, ownSecurityServerResolver, dsParticipantRepository, globalConfProvider,
                new DataspaceDidAuthority(ownSecurityServerResolver, adminServiceProperties));
    }

    // --- ensureMembershipCredential ---

    @Test
    void ensureMembershipCredentialSubmitsIntoSlot0WhenNoExistingRequest() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(null);

        assertThat(service.ensureMembershipCredential(HOST_CONTEXT)).isEqualTo(CredentialStatus.PENDING);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), any(), eq(HOLDER_PID_SLOT0),
                anyString(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "REQUESTING", "REQUESTED"})
    void ensureMembershipCredentialNoOpWhenSlot0IsInFlight(String hubState) {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(hubState);

        assertThat(service.ensureMembershipCredential(HOST_CONTEXT)).isEqualTo(CredentialStatus.PENDING);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialNoOpWhenSlot0IsIssued() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        assertThat(service.ensureMembershipCredential(HOST_CONTEXT)).isEqualTo(CredentialStatus.ISSUED);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialAdvancesPastErrorSlot() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(null);

        service.ensureMembershipCredential(HOST_CONTEXT);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), any(), eq(HOLDER_PID_SLOT1),
                anyString(), anyString(), anyString());
    }

    @Test
    void ensureMembershipCredentialAdvancesPastMultipleErrorSlots() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT2)).thenReturn(null);

        service.ensureMembershipCredential(HOST_CONTEXT);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), any(), eq(HOLDER_PID_SLOT2),
                anyString(), anyString(), anyString());
    }

    @Test
    void ensureMembershipCredentialNoSubmitWhenAllSlotsExhausted() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ERROR.name());

        assertThat(service.ensureMembershipCredential(HOST_CONTEXT)).isEqualTo(CredentialStatus.ERROR);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialReturnsAbsentAndAttemptsNothingWhenNotDataspaceEnabled() {
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.of());

        assertThat(service.ensureMembershipCredential(HOST_CONTEXT)).isEqualTo(CredentialStatus.ABSENT);

        verify(identityHubClient, never()).getCredentialRequestState(any(), any());
        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialPassesTheFullTrustedIssuerSetToTheIdentityHub() {
        var trustedDids = Set.of("did:web:cs1.example.test%3A6183:issuer", "did:web:cs2.example.test%3A6183:issuer");
        when(globalConfProvider.getIssuerDids(INSTANCE_IDENTIFIER)).thenReturn(List.copyOf(trustedDids));
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(null);

        service.ensureMembershipCredential(HOST_CONTEXT);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), eq(trustedDids), eq(HOLDER_PID_SLOT0),
                anyString(), anyString(), anyString());
    }

    // --- readCredentialStatus ---

    @Test
    void readCredentialStatusReturnsNullWhenNoRequests() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(eq(PARTICIPANT_ID), anyString())).thenReturn(null);

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isNull();
    }

    @Test
    void readCredentialStatusReturnsIssuedWhenSlot0Issued() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.ISSUED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATED", "REQUESTING", "REQUESTED"})
    void readCredentialStatusMapsInFlightHubStateToPending(String hubState) {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(hubState);

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
    }

    @Test
    void readCredentialStatusSkipsErrorSlotAndReturnsNextActiveStatus() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn("REQUESTED");

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
    }

    @Test
    void readCredentialStatusReturnsErrorWhenAllSlotsError() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ERROR.name());

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.ERROR);
    }

    @Test
    void readCredentialStatusReturnsNullWhenAllSlotsAbsent() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(null);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(null);

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isNull();
    }

    @Test
    void readCredentialStatusStopsAtFirstActiveSlotAfterErrors() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ISSUED.name());

        var status = service.readCredentialStatus(HOST_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.ISSUED);
        // slot2..N are not queried
        verify(identityHubClient, times(2)).getCredentialRequestState(eq(PARTICIPANT_ID), anyString());
    }

    @Test
    void readCredentialStatusMapsUnrecognizedHubStateToUnknown() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn("APPROVED");

        assertThat(service.readCredentialStatus(HOST_CONTEXT)).isEqualTo(CredentialStatus.UNKNOWN);
    }

    @Test
    void ensureMembershipCredentialLeavesUnrecognizedHubStateUntouched() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn("APPROVED");

        assertThat(service.ensureMembershipCredential(HOST_CONTEXT)).isEqualTo(CredentialStatus.UNKNOWN);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    // --- participantContexts ---

    @Test
    void participantContextsReturnsHostAndSystemWhenNoClientIsRegisteredYet() {
        givenServerConfWithOwner(OWNER);
        var savedOwner = clientWith(OWNER, Client.STATUS_SAVED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(savedOwner));

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, OWNER),
                new DataspaceProvisioningService.ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                        ParticipantKind.SYSTEM, OWNER));
    }

    @Test
    void participantContextsAddsOwnerMemberContextOnceItsOwnClientIsRegistered() {
        givenServerConfWithOwner(OWNER);
        var ownerClient = clientWith(OWNER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(ownerClient));

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, OWNER),
                new DataspaceProvisioningService.ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                        ParticipantKind.SYSTEM, OWNER),
                new DataspaceProvisioningService.ParticipantContext(ParticipantIdentifierScheme.memberCtxId(OWNER),
                        ParticipantKind.MEMBER, OWNER));
    }

    @Test
    void participantContextsIncludesManagementContextWhenManagementRegistered() {
        givenServerConfWithOwner(OWNER);
        var ownerClient = clientWith(OWNER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(ownerClient));

        var contexts = service.participantContexts(true);

        assertThat(contexts).extracting(DataspaceProvisioningService.ParticipantContext::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.SYSTEM, ParticipantKind.MANAGEMENT, ParticipantKind.MEMBER);
        assertThat(contexts.get(2).participantId()).isEqualTo(PARTICIPANT_ID + "-mgmt");
    }

    @Test
    void participantContextsCarriesOwnerAsSystemCredentialSubjectWhenOwnerKnown() {
        givenServerConfWithOwner(OWNER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());

        var contexts = service.participantContexts(false);

        assertThat(contexts).filteredOn(ctx -> ctx.kind() == ParticipantKind.SYSTEM)
                .extracting(DataspaceProvisioningService.ParticipantContext::memberId)
                .containsExactly(OWNER);
    }

    @Test
    void participantContextsAddsOneMemberContextPerHostedMember() {
        givenServerConfWithOwner(OWNER);
        var ownerClient = clientWith(OWNER);
        var memberClient = clientWith(MEMBER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(ownerClient, memberClient));

        var contexts = service.participantContexts(false);

        assertThat(contexts).extracting(DataspaceProvisioningService.ParticipantContext::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.SYSTEM, ParticipantKind.MEMBER, ParticipantKind.MEMBER);
        assertThat(contexts).extracting(DataspaceProvisioningService.ParticipantContext::participantId)
                .contains(ParticipantIdentifierScheme.memberCtxId(OWNER), ParticipantIdentifierScheme.memberCtxId(MEMBER));
    }

    @Test
    void participantContextsCollapsesSubsystemOfAlreadyProvisionedMemberIntoNoNewContext() {
        givenServerConfWithOwner(OWNER);
        var subsystem = ClientId.Conf.create(MEMBER.getXRoadInstance(), MEMBER.getMemberClass(), MEMBER.getMemberCode(), "SUB");
        var memberClient = clientWith(MEMBER);
        var subsystemClient = clientWith(subsystem);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(memberClient, subsystemClient));

        var contexts = service.participantContexts(false);

        // host + SYSTEM + MEMBER once, even though a subsystem of MEMBER is also a local client
        assertThat(contexts).hasSize(3);
        assertThat(contexts).filteredOn(ctx -> ctx.kind() == ParticipantKind.MEMBER)
                .extracting(DataspaceProvisioningService.ParticipantContext::participantId)
                .containsExactly(ParticipantIdentifierScheme.memberCtxId(MEMBER));
    }

    @Test
    void participantContextsSkipsMembersWithoutAnyRegisteredClient() {
        givenServerConfWithOwner(OWNER);
        var savedOwner = clientWith(OWNER, Client.STATUS_SAVED);
        var savedClient = clientWith(MEMBER, Client.STATUS_SAVED);
        var registeredOther = clientWith(OTHER_MEMBER, Client.STATUS_REGISTERED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(savedOwner, savedClient, registeredOther));

        var contexts = service.participantContexts(false);

        assertThat(contexts).filteredOn(ctx -> ctx.kind() == ParticipantKind.MEMBER)
                .extracting(DataspaceProvisioningService.ParticipantContext::participantId)
                .containsExactly(ParticipantIdentifierScheme.memberCtxId(OTHER_MEMBER));
    }

    @Test
    void participantContextsIncludesMemberOnceAnyOfItsClientsIsRegistered() {
        givenServerConfWithOwner(OWNER);
        var subsystem = ClientId.Conf.create(MEMBER.getXRoadInstance(), MEMBER.getMemberClass(), MEMBER.getMemberCode(), "SUB");
        var savedMemberClient = clientWith(MEMBER, Client.STATUS_SAVED);
        var registeredSubsystemClient = clientWith(subsystem, Client.STATUS_REGISTERED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(savedMemberClient, registeredSubsystemClient));

        var contexts = service.participantContexts(false);

        assertThat(contexts).filteredOn(ctx -> ctx.kind() == ParticipantKind.MEMBER)
                .extracting(DataspaceProvisioningService.ParticipantContext::participantId)
                .containsExactly(ParticipantIdentifierScheme.memberCtxId(MEMBER));
    }

    @Test
    void participantContextsReturnsHostAndSystemWithNullSubjectWhenOwnerNotYetSet() {
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(null);
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, null),
                new DataspaceProvisioningService.ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                        ParticipantKind.SYSTEM, null));
    }

    @Test
    void participantContextsReturnsHostAndSystemWhenServerConfNotInitialized() {
        when(serverConfRepository.getServerConf())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.MALFORMED_SERVERCONF).build());

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, null),
                new DataspaceProvisioningService.ParticipantContext(ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                        ParticipantKind.SYSTEM, null));
    }

    @Test
    void participantContextsRethrowsServerConfErrorsOtherThanUninitialized() {
        when(serverConfRepository.getServerConf())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).build());

        assertThatThrownBy(() -> service.participantContexts(false))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_ERROR.code()));
    }

    // --- ensureParticipantContext (HOST / MANAGEMENT) ---

    @Test
    void ensureParticipantContextCreatesIhAndCpForHostParticipant() {
        service.ensureParticipantContext(HOST_CONTEXT);

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(PARTICIPANT_ID);
        assertThat(request.memberId()).isEqualTo(slashForm(OWNER));
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
        verify(controlPlaneClient).createParticipantContext(eq(PARTICIPANT_ID), any());
        verify(controlPlaneClient).putParticipantContextConfig(eq(PARTICIPANT_ID), any(), any());
        verify(dsParticipantRepository, never()).findByMemberIdentifier(any());
    }

    @Test
    void ensureParticipantContextUsesMgmtDidSuffixForManagementParticipant() {
        var mgmtId = PARTICIPANT_ID + "-mgmt";

        service.ensureParticipantContext(new ParticipantContext(mgmtId, ParticipantKind.MANAGEMENT, OWNER));

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(mgmtId);
        assertThat(request.did().toString()).endsWith(":mgmt");
        assertThat(request.memberId()).isEqualTo(slashForm(OWNER));
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
    }

    // --- ensureParticipantContext (MEMBER) ---

    @Test
    void ensureParticipantContextDerivesUnboundDidWhenNoRowExists() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST);

        service.ensureParticipantContext(MEMBER_CONTEXT);

        var request = capturedIhCreateRequest();
        assertThat(request.did()).isEqualTo(expectedDid);
        assertThat(request.memberId()).isEqualTo(slashForm(MEMBER));
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
    }

    @Test
    void ensureParticipantContextUsesConfiguredStsAndCredentialsPorts() {
        when(dataspace.getIdentityHubStsPort()).thenReturn(8184);
        when(dataspace.getIdentityHubCredentialsPort()).thenReturn(8185);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var ctxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST);

        service.ensureParticipantContext(new ParticipantContext(ctxId, ParticipantKind.MEMBER, MEMBER));

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(ctxId);
        assertThat(request.did()).isEqualTo(expectedDid);
        assertThat(request.credentialServiceUrl()).startsWith("https://ih.example.test:8185/api/credentials/");
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
        verify(controlPlaneClient).putParticipantContextConfig(eq(ctxId), eq(expectedDid),
                eq("https://ih.example.test:8184/api/sts/token"));
    }

    @Test
    void ensureParticipantContextMintsDidUnderConfiguredDidPort() {
        when(dataspace.getIdentityHubDidPort()).thenReturn(8183);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var ctxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_ADDRESS + ":8183");

        service.ensureParticipantContext(new ParticipantContext(ctxId, ParticipantKind.MEMBER, MEMBER));

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(ctxId);
        assertThat(request.did()).isEqualTo(expectedDid);
    }

    @Test
    void ensureParticipantContextDerivesDidFromRegisteredAddressNotIdentityHubHost() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var ctxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);

        service.ensureParticipantContext(new ParticipantContext(ctxId, ParticipantKind.MEMBER, MEMBER));

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(ctxId);
        assertThat(request.did()).isEqualTo(ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST));
        assertThat(request.credentialServiceUrl()).startsWith("https://ih.example.test:7185/api/credentials/");
    }

    @Test
    void ensureParticipantContextRefusesToProvisionWithoutRegisteredAddress() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.ensureParticipantContext(MEMBER_CONTEXT))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DSP_PROVISIONING_FAILED.code()));

        verify(identityHubClient, never()).createParticipantContext(any());
    }

    @Test
    void ensureParticipantContextRefusesToProvisionWhenIdentityHubUrlHasNoHost() {
        when(dataspace.getIdentityHubUrl()).thenReturn("identity-hub-placeholder");

        assertThatThrownBy(() -> service.ensureParticipantContext(MEMBER_CONTEXT))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR.code()));

        verify(identityHubClient, never()).createParticipantContext(any());
    }

    @Test
    void ensureParticipantContextReencodesPercentEscapedCtxIdInCredentialServiceUrl() {
        var memberWithPlus = ClientId.Conf.create("TEST", "ORG", "222+A");
        when(dsParticipantRepository.findByMemberIdentifier(memberWithPlus)).thenReturn(Optional.empty());
        var ctxId = ParticipantIdentifierScheme.memberCtxId(memberWithPlus);
        assertThat(ctxId).isEqualTo("TEST:ORG:222%2BA");

        service.ensureParticipantContext(new ParticipantContext(ctxId, ParticipantKind.MEMBER, memberWithPlus));

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(ctxId);
        assertThat(request.credentialServiceUrl()).endsWith("/api/credentials/v1/participants/TEST:ORG:222%252BA");
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
    }

    @Test
    void ensureParticipantContextUsesBoundDidWhenRowExists() {
        var bound = boundParticipant(MEMBER, SS_HOST);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.of(bound));

        service.ensureParticipantContext(MEMBER_CONTEXT);

        var request = capturedIhCreateRequest();
        assertThat(request.did()).isEqualTo(Did.parse(bound.getDid()));
        assertThat(request.memberId()).isEqualTo(slashForm(MEMBER));
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
    }

    @Test
    void ensureParticipantContextThrowsWhenBoundRowNoLongerMatchesDerivation() {
        var bound = boundParticipant(MEMBER, "ih.other.test:7183");
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.of(bound));

        var context = new ParticipantContext(ParticipantIdentifierScheme.memberCtxId(MEMBER), ParticipantKind.MEMBER, MEMBER);
        assertThatThrownBy(() -> service.ensureParticipantContext(context))
                .isInstanceOf(XrdRuntimeException.class);

        verify(identityHubClient, never()).createParticipantContext(any());
    }

    @Test
    void readContextStatusKeepsTheContextAndCredentialReadsWhenTheIdentityAssessmentFails() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenThrow(new IllegalStateException("db down"));

        var status = service.readContextStatus(MEMBER_CONTEXT);

        assertThat(status.contextCreated()).isFalse();
        assertThat(status.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
        assertThat(status.identityStatus()).isEqualTo(IdentityStatus.UNKNOWN);
    }

    @Test
    void readContextStatusKeepsTheObservedContextWhenTheCredentialReadFails() {
        when(identityHubClient.contextDid(MEMBER_CONTEXT.participantId()))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST)));
        when(identityHubClient.getCredentialRequestState(eq(MEMBER_CONTEXT.participantId()), anyString()))
                .thenThrow(new IllegalStateException("identity hub down"));

        var status = service.readContextStatus(MEMBER_CONTEXT);

        assertThat(status.contextCreated()).isTrue();
        assertThat(status.credentialStatus()).isEqualTo(CredentialStatus.UNKNOWN);
        assertThat(status.identityStatus()).isEqualTo(IdentityStatus.UNBOUND);
    }

    @Test
    void readContextStatusReportsUnknownCredentialStatusWhenTheContextDidIsUnreadable() {
        when(identityHubClient.contextDid(MEMBER_CONTEXT.participantId()))
                .thenThrow(new IllegalStateException("identity hub down"));

        var status = service.readContextStatus(MEMBER_CONTEXT);

        assertThat(status.contextCreated()).isFalse();
        assertThat(status.credentialStatus()).isEqualTo(CredentialStatus.UNKNOWN);
        assertThat(status.identityStatus()).isEqualTo(IdentityStatus.UNBOUND);
    }

    @Test
    void ensureParticipantContextThrowsOnHubDidDrift() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        when(identityHubClient.contextDid(memberCtxId))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.memberDid(MEMBER, "ih.other.test:7183")));
        var context = new ParticipantContext(memberCtxId, ParticipantKind.MEMBER, MEMBER);

        assertThatThrownBy(() -> service.ensureParticipantContext(context))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DSP_PARTICIPANT_DID_DRIFT.code()));

        verify(identityHubClient, never()).createParticipantContext(any());
        verify(controlPlaneClient, never()).createParticipantContext(any(), any());
    }

    @Test
    void ensureParticipantContextProceedsWhenHubDidMatchesIntended() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST);
        when(identityHubClient.contextDid(memberCtxId)).thenReturn(Optional.of(expectedDid));

        service.ensureParticipantContext(new ParticipantContext(memberCtxId, ParticipantKind.MEMBER, MEMBER));

        var request = capturedIhCreateRequest();
        assertThat(request.did()).isEqualTo(expectedDid);
        assertThat(request.memberId()).isEqualTo(slashForm(MEMBER));
        assertThat(request.reanchorMemberIdOnConflict()).isFalse();
    }

    @Test
    void ensureParticipantContextLogsCreationWhenHubHadNoContext() {
        var logs = capturedLogs(() -> service.ensureParticipantContext(HOST_CONTEXT));

        assertThat(logs).anyMatch(line -> line.equals(
                "Data space provisioning: participant context " + PARTICIPANT_ID + " created"));
    }

    @Test
    void ensureParticipantContextDoesNotLogCreationWhenHubAlreadyServesTheContext() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        when(identityHubClient.contextDid(memberCtxId))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST)));
        var context = new ParticipantContext(memberCtxId, ParticipantKind.MEMBER, MEMBER);

        var logs = capturedLogs(() -> service.ensureParticipantContext(context));

        assertThat(logs).noneMatch(line -> line.contains("created"));
        verify(identityHubClient).createParticipantContext(any());
    }

    private static List<String> capturedLogs(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(DataspaceProvisioningService.class);
        var previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void ensureControlPlaneContextReappliesContextAndStsConfigWithTheGivenDidOnly() {
        var hubDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST);

        service.ensureControlPlaneContext(MEMBER_CONTEXT, hubDid);

        verify(controlPlaneClient).createParticipantContext(MEMBER_CONTEXT.participantId(), hubDid);
        verify(controlPlaneClient).putParticipantContextConfig(eq(MEMBER_CONTEXT.participantId()), eq(hubDid), any());
        verify(identityHubClient, never()).contextDid(any());
        verify(identityHubClient, never()).createParticipantContext(any());
        verify(dsParticipantRepository, never()).findByMemberIdentifier(any());
    }

    // --- ensureParticipantContext (SYSTEM) ---

    @Test
    void ensureParticipantContextDerivesUnboundSystemDidWhenNoRowExists() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());
        var expectedDid = ParticipantIdentifierScheme.systemDid(SS_HOST);

        service.ensureParticipantContext(SYSTEM_CONTEXT);

        var request = capturedIhCreateRequest();
        assertThat(request.participantContextId()).isEqualTo(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
        assertThat(request.did()).isEqualTo(expectedDid);
        assertThat(request.memberId()).isEqualTo(slashForm(OWNER));
        assertThat(request.reanchorMemberIdOnConflict()).isTrue();
    }

    @Test
    void ensureParticipantContextUsesBoundDidWhenSystemRowExists() {
        var bound = boundSystemParticipant(SS_HOST);
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(bound));

        service.ensureParticipantContext(SYSTEM_CONTEXT);

        var request = capturedIhCreateRequest();
        assertThat(request.did()).isEqualTo(Did.parse(bound.getDid()));
        assertThat(request.memberId()).isEqualTo(slashForm(OWNER));
        assertThat(request.reanchorMemberIdOnConflict()).isTrue();
    }

    @Test
    void ensureParticipantContextThrowsWhenBoundSystemRowNoLongerMatchesDerivation() {
        var bound = boundSystemParticipant("ih.other.test:7183");
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(bound));

        assertThatThrownBy(() -> service.ensureParticipantContext(SYSTEM_CONTEXT))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DSP_PARTICIPANT_IDENTIFIER_MISMATCH.code()));

        verify(identityHubClient, never()).createParticipantContext(any());
    }

    @Test
    void ensureParticipantContextForSystemNeverConsultsMemberIdentifierLookup() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());

        service.ensureParticipantContext(SYSTEM_CONTEXT);

        verify(dsParticipantRepository).findSystemParticipant();
        verify(dsParticipantRepository, never()).findByMemberIdentifier(any());
    }

    // --- ensureParticipantContext credential-issuance-safe signal ---

    @Test
    void ensureParticipantContextReportsConfirmedForHostWhenNoReanchorRequested() {
        when(identityHubClient.createParticipantContext(argThat(request ->
                PARTICIPANT_ID.equals(request.participantContextId()) && !request.reanchorMemberIdOnConflict())))
                .thenReturn(true);

        var anchor = service.ensureParticipantContext(HOST_CONTEXT);

        assertThat(anchor).isTrue();
    }

    @Test
    void ensureParticipantContextReportsUnconfirmedForSystemWhenReanchorUnconfirmed() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());
        when(identityHubClient.createParticipantContext(argThat(request ->
                ParticipantIdentifierScheme.SYSTEM_SEGMENT.equals(request.participantContextId())
                        && request.reanchorMemberIdOnConflict())))
                .thenReturn(false);

        var anchor = service.ensureParticipantContext(SYSTEM_CONTEXT);

        assertThat(anchor).isFalse();
    }

    @Test
    void ensureParticipantContextReportsConfirmedForSystemWhenReanchorConfirmed() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());
        when(identityHubClient.createParticipantContext(argThat(request ->
                ParticipantIdentifierScheme.SYSTEM_SEGMENT.equals(request.participantContextId())
                        && request.reanchorMemberIdOnConflict())))
                .thenReturn(true);

        var anchor = service.ensureParticipantContext(SYSTEM_CONTEXT);

        assertThat(anchor).isTrue();
    }

    // --- ensureMembershipCredential / readCredentialStatus (SYSTEM re-anchor) ---

    @Test
    void ensureMembershipCredentialForSystemParticipantIsIdempotentWhileActiveCredentialExists() {
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OWNER)))
                .thenReturn(CredentialStatus.ISSUED.name());

        assertThat(service.ensureMembershipCredential(SYSTEM_CONTEXT))
                .isEqualTo(CredentialStatus.ISSUED);
        assertThat(service.ensureMembershipCredential(SYSTEM_CONTEXT))
                .isEqualTo(CredentialStatus.ISSUED);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialForSystemSkipsWhenCredentialSubjectMatchesCurrentOwner() {
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OWNER)))
                .thenReturn(CredentialStatus.ISSUED.name());

        var status = service.ensureMembershipCredential(SYSTEM_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.ISSUED);
        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialForSystemSkipsWhenSubjectRequestIsPendingInFlight() {
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OWNER)))
                .thenReturn(CredentialStatus.PENDING.name());

        var status = service.ensureMembershipCredential(SYSTEM_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialForSystemIssuesWhenNoActiveCredentialExists() {
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OWNER)))
                .thenReturn(null);

        var status = service.ensureMembershipCredential(SYSTEM_CONTEXT);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
        verify(identityHubClient).requestMembershipCredential(eq(ParticipantIdentifierScheme.SYSTEM_SEGMENT), any(),
                eq(ownerHolderPidSlot0(OWNER)), anyString(), anyString(), anyString());
    }

    @Test
    void ensureMembershipCredentialForSystemReanchorsAndIssuesOnNewOwnerWhenOwnerChangesWithoutRevokingOldCredential() {
        // Stale credential from the previous owner (OWNER) is left ISSUED at its own base; the new owner's
        // base has never been used, so the mismatch surfaces as "nothing active there yet" and a fresh
        // credential is issued on the new owner (OTHER_MEMBER) — the old one is neither read nor touched.
        lenient().when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OWNER)))
                .thenReturn(CredentialStatus.ISSUED.name());
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OTHER_MEMBER)))
                .thenReturn(null);

        var status = service.ensureMembershipCredential(SYSTEM_CONTEXT_OTHER_OWNER);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
        verify(identityHubClient).requestMembershipCredential(eq(ParticipantIdentifierScheme.SYSTEM_SEGMENT), any(),
                eq(ownerHolderPidSlot0(OTHER_MEMBER)), anyString(), anyString(), anyString());
        verify(identityHubClient, never()).requestMembershipCredential(eq(ParticipantIdentifierScheme.SYSTEM_SEGMENT), any(),
                eq(ownerHolderPidSlot0(OWNER)), anyString(), anyString(), anyString());
    }

    @Test
    void readCredentialStatusForSystemReflectsCredentialAnchoredToCurrentOwner() {
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OWNER)))
                .thenReturn(CredentialStatus.ISSUED.name());
        when(identityHubClient.getCredentialRequestState(ParticipantIdentifierScheme.SYSTEM_SEGMENT, ownerHolderPidSlot0(OTHER_MEMBER)))
                .thenReturn(null);

        assertThat(service.readCredentialStatus(SYSTEM_CONTEXT))
                .isEqualTo(CredentialStatus.ISSUED);
        assertThat(service.readCredentialStatus(SYSTEM_CONTEXT_OTHER_OWNER))
                .isNull();
    }

    @Test
    void ensureMembershipCredentialForNonSystemKindsUsesUnsaltedHolderPidRegardlessOfMemberId() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        var status = service.ensureMembershipCredential(new ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, MEMBER));

        assertThat(status).isEqualTo(CredentialStatus.ISSUED);
    }

    @Test
    void ensureMembershipCredentialForSystemWithUnknownOwnerReturnsUnknownWithoutTouchingHub() {
        var status = service.ensureMembershipCredential(SYSTEM_CONTEXT_NO_OWNER);

        assertThat(status).isEqualTo(CredentialStatus.UNKNOWN);
        verify(identityHubClient, never()).getCredentialRequestState(any(), any());
        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void readCredentialStatusForSystemWithUnknownOwnerReturnsUnknownWithoutTouchingHub() {
        var status = service.readCredentialStatus(SYSTEM_CONTEXT_NO_OWNER);

        assertThat(status).isEqualTo(CredentialStatus.UNKNOWN);
        verify(identityHubClient, never()).getCredentialRequestState(any(), any());
    }

    private static String ownerHolderPidSlot0(ClientId owner) {
        return ParticipantIdentifierScheme.SYSTEM_SEGMENT + "-xroad-membership-credential-request-"
                + ParticipantIdentifierScheme.memberCtxId(owner);
    }

    // --- readIdentityStatus ---

    @Test
    void readIdentityStatusReportsUnboundWhenNoRowExists() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.UNBOUND);
    }

    @Test
    void readIdentityStatusReportsOkWhenBoundRowMatchesDerivation() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER))
                .thenReturn(Optional.of(boundParticipant(MEMBER, SS_HOST)));

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.OK);
    }

    @Test
    void readIdentityStatusReportsMismatchWhenBoundRowDiffersFromDerivation() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER))
                .thenReturn(Optional.of(boundParticipant(MEMBER, "ih.other.test:7183")));

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.MISMATCH);
    }

    @Test
    void readIdentityStatusReportsVersionUnsupportedForUnknownSchemeVersion() {
        var bound = boundParticipant(MEMBER, SS_HOST);
        bound.setSchemeVersion("v0");
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.of(bound));

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.VERSION_UNSUPPORTED);
    }

    @Test
    void readIdentityStatusPropagatesRepositoryFailures() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        assertThatThrownBy(() -> service.readIdentityStatus(MEMBER))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void readIdentityStatusReportsUnboundWithoutRegisteredAddressWhenNoRowExists() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(null);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.UNBOUND);
    }

    @Test
    void readIdentityStatusReportsUnknownWithoutRegisteredAddressWhenRowIsBound() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(null);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER))
                .thenReturn(Optional.of(boundParticipant(MEMBER, SS_HOST)));

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.UNKNOWN);
    }

    // --- registeredAddressKnown ---

    @Test
    void registeredAddressKnownIsFalseWhileGlobalConfIsNotReadable() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID))
                .thenThrow(XrdRuntimeException.systemInternalError("Shared params for instance identifier TEST not found"));

        assertThat(service.registeredAddressKnown()).isFalse();
    }

    @Test
    void registeredAddressKnownIsFalseWhileRegistrationIsNotInGlobalConf() {
        when(globalConfProvider.getSecurityServerAddress(SERVER_ID)).thenReturn(null);

        assertThat(service.registeredAddressKnown()).isFalse();
    }

    @Test
    void registeredAddressKnownIsTrueForRegisteredServer() {
        assertThat(service.registeredAddressKnown()).isTrue();
    }

    private void givenServerConfWithOwner(ClientId owner) {
        var ownerEntity = clientWith(owner);
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(ownerEntity);
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);
    }

    private ClientEntity clientWith(ClientId id) {
        return clientWith(id, Client.STATUS_REGISTERED);
    }

    private ClientEntity clientWith(ClientId id, String clientStatus) {
        var entity = mock(ClientEntity.class);
        lenient().when(entity.getIdentifier()).thenReturn(ClientIdEntityFactory.create(id));
        lenient().when(entity.getClientStatus()).thenReturn(clientStatus);
        return entity;
    }

    private DsParticipantEntity boundParticipant(ClientId member, String ssHost) {
        var participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.MEMBER);
        participant.setMemberIdentifier(ClientIdEntityFactory.create(member));
        participant.setCtxId(ParticipantIdentifierScheme.memberCtxId(member));
        participant.setDid(ParticipantIdentifierScheme.memberDid(member, ssHost).toString());
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

    private DsParticipantEntity boundSystemParticipant(String ssHost) {
        var participant = new DsParticipantEntity();
        participant.setParticipantType(ParticipantType.SYSTEM);
        participant.setCtxId(ParticipantIdentifierScheme.SYSTEM_SEGMENT);
        participant.setDid(ParticipantIdentifierScheme.systemDid(ssHost).toString());
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

    private static String slashForm(ClientId id) {
        return "%s/%s/%s".formatted(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode());
    }

    private CreateParticipantContextRequest capturedIhCreateRequest() {
        var captor = ArgumentCaptor.forClass(CreateParticipantContextRequest.class);
        verify(identityHubClient).createParticipantContext(captor.capture());
        return captor.getValue();
    }
}
