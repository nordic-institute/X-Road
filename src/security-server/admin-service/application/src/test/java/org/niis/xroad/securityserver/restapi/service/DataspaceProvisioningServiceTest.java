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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.CredentialStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;
import java.util.Optional;

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

    private static final String PARTICIPANT_ID = "test-participant";
    private static final String HOLDER_PID_SLOT0 = PARTICIPANT_ID + "-xroad-membership-credential-request";
    private static final String HOLDER_PID_SLOT1 = PARTICIPANT_ID + "-xroad-membership-credential-request-1";
    private static final String HOLDER_PID_SLOT2 = PARTICIPANT_ID + "-xroad-membership-credential-request-2";

    private static final ClientId OWNER = ClientId.Conf.create("TEST", "ORG", "OWNER");
    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "ORG", "MEMBER");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("TEST", "ORG", "OTHER");
    private static final String SS_HOST = "ih.example.test:7183";

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

    private DataspaceProvisioningService service;

    @BeforeEach
    void setUp() {
        lenient().when(dataspace.getParticipantId()).thenReturn(PARTICIPANT_ID);
        lenient().when(dataspace.getIdentityHubUrl()).thenReturn("https://ih.example.test");
        lenient().when(dataspace.getIssuerDid()).thenReturn("did:web:issuer.example.test");
        lenient().when(dataspace.getCredentialDefinitionId()).thenReturn("xroad-membership-credential-definition");
        lenient().when(dataspace.getMaxHolderPidSlots()).thenReturn(20);
        lenient().when(dataspace.getIdentityHubDidPort()).thenReturn(7183);
        lenient().when(dataspace.getIdentityHubStsPort()).thenReturn(7184);
        lenient().when(dataspace.getIdentityHubCredentialsPort()).thenReturn(7185);
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        lenient().when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        service = new DataspaceProvisioningService(adminServiceProperties, identityHubClient, controlPlaneClient,
                clientRepository, serverConfRepository, dsParticipantRepository);
    }

    // --- ensureMembershipCredential ---

    @Test
    void ensureMembershipCredentialSubmitsIntoSlot0WhenNoExistingRequest() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(null);

        assertThat(service.ensureMembershipCredential(PARTICIPANT_ID)).isEqualTo(CredentialStatus.PENDING);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT0),
                anyString(), anyString(), anyString());
    }

    @Test
    void ensureMembershipCredentialNoOpWhenSlot0IsPending() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.PENDING.name());

        assertThat(service.ensureMembershipCredential(PARTICIPANT_ID)).isEqualTo(CredentialStatus.PENDING);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialNoOpWhenSlot0IsIssued() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        assertThat(service.ensureMembershipCredential(PARTICIPANT_ID)).isEqualTo(CredentialStatus.ISSUED);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureMembershipCredentialAdvancesPastErrorSlot() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(null);

        service.ensureMembershipCredential(PARTICIPANT_ID);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT1),
                anyString(), anyString(), anyString());
    }

    @Test
    void ensureMembershipCredentialAdvancesPastMultipleErrorSlots() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT2)).thenReturn(null);

        service.ensureMembershipCredential(PARTICIPANT_ID);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT2),
                anyString(), anyString(), anyString());
    }

    @Test
    void ensureMembershipCredentialNoSubmitWhenAllSlotsExhausted() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ERROR.name());

        assertThat(service.ensureMembershipCredential(PARTICIPANT_ID)).isEqualTo(CredentialStatus.ERROR);

        verify(identityHubClient, never()).requestMembershipCredential(any(), any(), any(), any(), any(), any());
    }

    // --- readCredentialStatus ---

    @Test
    void readCredentialStatusReturnsNullWhenNoRequests() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(eq(PARTICIPANT_ID), anyString())).thenReturn(null);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isNull();
    }

    @Test
    void readCredentialStatusReturnsIssuedWhenSlot0Issued() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(CredentialStatus.ISSUED);
    }

    @Test
    void readCredentialStatusReturnsPendingWhenSlot0Pending() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.PENDING.name());

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
    }

    @Test
    void readCredentialStatusSkipsErrorSlotAndReturnsNextActiveStatus() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.PENDING.name());

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(CredentialStatus.PENDING);
    }

    @Test
    void readCredentialStatusReturnsErrorWhenAllSlotsError() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ERROR.name());

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(CredentialStatus.ERROR);
    }

    @Test
    void readCredentialStatusReturnsNullWhenAllSlotsAbsent() {
        when(dataspace.getMaxHolderPidSlots()).thenReturn(2);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(null);
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(null);

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isNull();
    }

    @Test
    void readCredentialStatusStopsAtFirstActiveSlotAfterErrors() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ERROR.name());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(CredentialStatus.ISSUED.name());

        var status = service.readCredentialStatus(PARTICIPANT_ID);

        assertThat(status).isEqualTo(CredentialStatus.ISSUED);
        // slot2..N are not queried
        verify(identityHubClient, times(2)).getCredentialRequestState(eq(PARTICIPANT_ID), anyString());
    }

    @Test
    void readCredentialStatusMapsUnrecognizedHubStateToUnknown() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn("APPROVED");

        assertThat(service.readCredentialStatus(PARTICIPANT_ID)).isEqualTo(CredentialStatus.UNKNOWN);
    }

    @Test
    void ensureMembershipCredentialAdvancesPastUnrecognizedHubState() {
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn("APPROVED");
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT1)).thenReturn(null);

        assertThat(service.ensureMembershipCredential(PARTICIPANT_ID)).isEqualTo(CredentialStatus.PENDING);

        verify(identityHubClient).requestMembershipCredential(eq(PARTICIPANT_ID), anyString(), eq(HOLDER_PID_SLOT1),
                anyString(), anyString(), anyString());
    }

    // --- participantContexts ---

    @Test
    void participantContextsReturnsOnlyHostAndOwnerWhenManagementNotRegisteredAndNoOtherClients() {
        givenServerConfWithOwner(OWNER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, OWNER),
                new DataspaceProvisioningService.ParticipantContext(ParticipantIdentifierScheme.memberCtxId(OWNER),
                        ParticipantKind.MEMBER, OWNER));
    }

    @Test
    void participantContextsIncludesManagementContextWhenManagementRegistered() {
        givenServerConfWithOwner(OWNER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of());

        var contexts = service.participantContexts(true);

        assertThat(contexts).extracting(DataspaceProvisioningService.ParticipantContext::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.MANAGEMENT, ParticipantKind.MEMBER);
        assertThat(contexts.get(1).participantId()).isEqualTo(PARTICIPANT_ID + "-mgmt");
    }

    @Test
    void participantContextsAddsOneMemberContextPerHostedMember() {
        givenServerConfWithOwner(OWNER);
        var memberClient = clientWith(MEMBER);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(memberClient));

        var contexts = service.participantContexts(false);

        assertThat(contexts).extracting(DataspaceProvisioningService.ParticipantContext::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.MEMBER, ParticipantKind.MEMBER);
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

        // owner + MEMBER once, even though a subsystem of MEMBER is also a local client
        assertThat(contexts).hasSize(3);
        assertThat(contexts).filteredOn(ctx -> ctx.kind() == ParticipantKind.MEMBER)
                .extracting(DataspaceProvisioningService.ParticipantContext::participantId)
                .containsExactlyInAnyOrder(ParticipantIdentifierScheme.memberCtxId(OWNER), ParticipantIdentifierScheme.memberCtxId(MEMBER));
    }

    @Test
    void participantContextsSkipsMembersWithoutAnyRegisteredClient() {
        givenServerConfWithOwner(OWNER);
        var savedClient = clientWith(MEMBER, Client.STATUS_SAVED);
        var registeredOther = clientWith(OTHER_MEMBER, Client.STATUS_REGISTERED);
        when(clientRepository.getAllLocalClients()).thenReturn(List.of(savedClient, registeredOther));

        var contexts = service.participantContexts(false);

        assertThat(contexts).filteredOn(ctx -> ctx.kind() == ParticipantKind.MEMBER)
                .extracting(DataspaceProvisioningService.ParticipantContext::participantId)
                .containsExactlyInAnyOrder(
                        ParticipantIdentifierScheme.memberCtxId(OWNER), ParticipantIdentifierScheme.memberCtxId(OTHER_MEMBER));
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
                .containsExactlyInAnyOrder(
                        ParticipantIdentifierScheme.memberCtxId(OWNER), ParticipantIdentifierScheme.memberCtxId(MEMBER));
    }

    @Test
    void participantContextsReturnsOnlyHostWhenOwnerNotYetSet() {
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(null);
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, null));
    }

    @Test
    void participantContextsReturnsOnlyHostWhenServerConfNotInitialized() {
        when(serverConfRepository.getServerConf())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.MALFORMED_SERVERCONF).build());

        var contexts = service.participantContexts(false);

        assertThat(contexts).containsExactly(
                new DataspaceProvisioningService.ParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, null));
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
        service.ensureParticipantContext(PARTICIPANT_ID, ParticipantKind.HOST, OWNER);

        verify(identityHubClient).createParticipantContext(eq(PARTICIPANT_ID), any(), eq(slashForm(OWNER)), any(), any(), any());
        verify(controlPlaneClient).createParticipantContext(eq(PARTICIPANT_ID), any());
        verify(controlPlaneClient).putParticipantContextConfig(eq(PARTICIPANT_ID), any(), any());
        verify(dsParticipantRepository, never()).findByMemberIdentifier(any());
    }

    @Test
    void ensureParticipantContextUsesMgmtDidSuffixForManagementParticipant() {
        var mgmtId = PARTICIPANT_ID + "-mgmt";

        service.ensureParticipantContext(mgmtId, ParticipantKind.MANAGEMENT, OWNER);

        verify(identityHubClient).createParticipantContext(eq(mgmtId), argThatEndsWith(":mgmt"), eq(slashForm(OWNER)), any(), any(), any());
    }

    // --- ensureParticipantContext (MEMBER) ---

    @Test
    void ensureParticipantContextDerivesUnboundDidWhenNoRowExists() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST);

        service.ensureParticipantContext(ParticipantIdentifierScheme.memberCtxId(MEMBER), ParticipantKind.MEMBER, MEMBER);

        verify(identityHubClient).createParticipantContext(any(), eq(expectedDid), eq(slashForm(MEMBER)), any(), any(), any());
    }

    @Test
    void ensureParticipantContextUsesConfiguredIdentityHubPorts() {
        when(dataspace.getIdentityHubDidPort()).thenReturn(8183);
        when(dataspace.getIdentityHubStsPort()).thenReturn(8184);
        when(dataspace.getIdentityHubCredentialsPort()).thenReturn(8185);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var ctxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, "ih.example.test:8183");

        service.ensureParticipantContext(ctxId, ParticipantKind.MEMBER, MEMBER);

        verify(identityHubClient).createParticipantContext(eq(ctxId), eq(expectedDid), any(),
                argThat(url -> url.startsWith("https://ih.example.test:8185/api/credentials/")), any(), any());
        verify(controlPlaneClient).putParticipantContextConfig(eq(ctxId), eq(expectedDid),
                eq("https://ih.example.test:8184/api/sts/token"));
    }

    @Test
    void ensureParticipantContextRefusesToProvisionWhenIdentityHubUrlHasNoHost() {
        when(dataspace.getIdentityHubUrl()).thenReturn("identity-hub-placeholder");

        assertThatThrownBy(() -> service.ensureParticipantContext(
                ParticipantIdentifierScheme.memberCtxId(MEMBER), ParticipantKind.MEMBER, MEMBER))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR.code()));

        verify(identityHubClient, never()).createParticipantContext(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureParticipantContextReencodesPercentEscapedCtxIdInCredentialServiceUrl() {
        var memberWithPlus = ClientId.Conf.create("TEST", "ORG", "222+A");
        when(dsParticipantRepository.findByMemberIdentifier(memberWithPlus)).thenReturn(Optional.empty());
        var ctxId = ParticipantIdentifierScheme.memberCtxId(memberWithPlus);
        assertThat(ctxId).isEqualTo("TEST:ORG:222%2BA");

        service.ensureParticipantContext(ctxId, ParticipantKind.MEMBER, memberWithPlus);

        verify(identityHubClient).createParticipantContext(eq(ctxId), any(), any(),
                argThat(url -> url.endsWith("/api/credentials/v1/participants/TEST:ORG:222%252BA")), any(), any());
    }

    @Test
    void ensureParticipantContextUsesBoundDidWhenRowExists() {
        var bound = boundParticipant(MEMBER, SS_HOST);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.of(bound));

        service.ensureParticipantContext(ParticipantIdentifierScheme.memberCtxId(MEMBER), ParticipantKind.MEMBER, MEMBER);

        verify(identityHubClient).createParticipantContext(any(), eq(bound.getDid()), eq(slashForm(MEMBER)), any(), any(), any());
    }

    @Test
    void ensureParticipantContextThrowsWhenBoundRowNoLongerMatchesDerivation() {
        var bound = boundParticipant(MEMBER, "ih.other.test:7183");
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.of(bound));

        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        assertThatThrownBy(() ->
                service.ensureParticipantContext(memberCtxId, ParticipantKind.MEMBER, MEMBER))
                .isInstanceOf(XrdRuntimeException.class);

        verify(identityHubClient, never()).createParticipantContext(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureParticipantContextThrowsOnHubDidDrift() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        when(identityHubClient.contextDid(memberCtxId))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.memberDid(MEMBER, "ih.other.test:7183")));

        assertThatThrownBy(() -> service.ensureParticipantContext(memberCtxId, ParticipantKind.MEMBER, MEMBER))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DSP_PARTICIPANT_DID_DRIFT.code()));

        verify(identityHubClient, never()).createParticipantContext(any(), any(), any(), any(), any(), any());
        verify(controlPlaneClient, never()).createParticipantContext(any(), any());
    }

    @Test
    void ensureParticipantContextProceedsWhenHubDidMatchesIntended() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.empty());
        var memberCtxId = ParticipantIdentifierScheme.memberCtxId(MEMBER);
        var expectedDid = ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST);
        when(identityHubClient.contextDid(memberCtxId)).thenReturn(Optional.of(expectedDid));

        service.ensureParticipantContext(memberCtxId, ParticipantKind.MEMBER, MEMBER);

        verify(identityHubClient).createParticipantContext(any(), eq(expectedDid), eq(slashForm(MEMBER)), any(), any(), any());
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
    void readIdentityStatusReportsUnknownWhenRepositoryFails() {
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        assertThat(service.readIdentityStatus(MEMBER)).isEqualTo(IdentityStatus.UNKNOWN);
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
        participant.setDid(ParticipantIdentifierScheme.memberDid(member, ssHost));
        participant.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        participant.setState(ParticipantState.ACTIVE);
        return participant;
    }

    private static String slashForm(ClientId id) {
        return "%s/%s/%s".formatted(id.getXRoadInstance(), id.getMemberClass(), id.getMemberCode());
    }

    private static String argThatEndsWith(String suffix) {
        return argThat(value -> value != null && value.endsWith(suffix));
    }
}
