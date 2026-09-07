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
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService.DataspaceStatus;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceProvisioningStatusServiceTest {

    private static final String PARTICIPANT_ID = "test-participant";
    private static final String MGMT_PARTICIPANT_ID = PARTICIPANT_ID + "-mgmt";
    private static final String HOLDER_PID_SLOT0 = PARTICIPANT_ID + "-xroad-membership-credential-request";
    private static final String MGMT_HOLDER_PID_SLOT0 = MGMT_PARTICIPANT_ID + "-xroad-membership-credential-request";

    private static final ClientId OWNER = ClientId.Conf.create("TEST", "GOV", "1234");
    private static final String OWNER_CTX_ID = ParticipantIdentifierScheme.memberCtxId(OWNER);

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
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

    private DataspaceProvisioningService provisioningService;
    private DataspaceProvisioningStatusService statusService;

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

        var ownerEntity = mock(ClientEntity.class);
        lenient().when(ownerEntity.getIdentifier()).thenReturn(ClientIdEntityFactory.create(OWNER));
        var serverConf = mock(ServerConfEntity.class);
        lenient().when(serverConf.getOwner()).thenReturn(ownerEntity);
        lenient().when(serverConfRepository.getServerConf()).thenReturn(serverConf);
        lenient().when(clientRepository.getAllLocalClients()).thenReturn(List.of());
        lenient().when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());

        provisioningService = new DataspaceProvisioningService(adminServiceProperties, identityHubClient, controlPlaneClient,
                clientRepository, serverConfRepository, dsParticipantRepository);

        statusService = new DataspaceProvisioningStatusService(
                provisioningService, readinessPredicates);
    }

    @Test
    void readStatusAlwaysReportsHostAndManagementContextsBothIssued() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(PARTICIPANT_ID)).thenReturn(Optional.of("did:web:host"));
        when(identityHubClient.contextDid(MGMT_PARTICIPANT_ID)).thenReturn(Optional.of("did:web:host:mgmt"));
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());
        when(identityHubClient.getCredentialRequestState(MGMT_PARTICIPANT_ID, MGMT_HOLDER_PID_SLOT0))
                .thenReturn(CredentialStatus.ISSUED.name());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.participantContexts()).hasSize(3);
        var host = status.participantContexts().get(0);
        var mgmt = status.participantContexts().get(1);
        assertThat(host.participantId()).isEqualTo(PARTICIPANT_ID);
        assertThat(host.kind()).isEqualTo(ParticipantKind.HOST);
        assertThat(host.contextCreated()).isTrue();
        assertThat(host.credentialStatus()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(mgmt.participantId()).isEqualTo(MGMT_PARTICIPANT_ID);
        assertThat(mgmt.kind()).isEqualTo(ParticipantKind.MANAGEMENT);
        assertThat(mgmt.contextCreated()).isTrue();
        assertThat(mgmt.credentialStatus()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(status.participantContexts().get(2).kind()).isEqualTo(ParticipantKind.MEMBER);
        assertThat(status.participantContexts().get(2).participantId()).isEqualTo(OWNER_CTX_ID);
    }

    @Test
    void readStatusReportsManagementContextEvenWhenHostIssuedAndManagementAbsent() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(PARTICIPANT_ID)).thenReturn(Optional.of("did:web:host"));
        when(identityHubClient.contextDid(MGMT_PARTICIPANT_ID)).thenReturn(Optional.empty());
        when(identityHubClient.contextDid(OWNER_CTX_ID)).thenReturn(Optional.empty());
        when(identityHubClient.getCredentialRequestState(PARTICIPANT_ID, HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(3);
        var host = status.participantContexts().get(0);
        var mgmt = status.participantContexts().get(1);
        assertThat(host.kind()).isEqualTo(ParticipantKind.HOST);
        assertThat(host.contextCreated()).isTrue();
        assertThat(host.credentialStatus()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(mgmt.kind()).isEqualTo(ParticipantKind.MANAGEMENT);
        assertThat(mgmt.contextCreated()).isFalse();
        assertThat(mgmt.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
    }

    @Test
    void readStatusBackendUnreachableReportsUnknownInsteadOf5xx() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString()))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR).build());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.participantContexts()).hasSize(3);
        assertThat(status.participantContexts())
                .allSatisfy(ctx -> {
                    assertThat(ctx.contextCreated()).isFalse();
                    assertThat(ctx.credentialStatus()).isEqualTo(CredentialStatus.UNKNOWN);
                });
    }

    @Test
    void readStatusSsNotInitializedReportsAllContextsAbsent() {
        when(serverConfRepository.getServerConf())
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.MALFORMED_SERVERCONF).build());
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(2);
        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.MANAGEMENT);
        assertThat(status.participantContexts())
                .allSatisfy(ctx -> {
                    assertThat(ctx.contextCreated()).isFalse();
                    assertThat(ctx.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
                });
    }

    @Test
    void readStatusProvisionedContextsReportedWithAllKinds() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(3);
        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.MANAGEMENT, ParticipantKind.MEMBER);
        assertThat(status.participantContexts())
                .allSatisfy(ctx -> {
                    assertThat(ctx.contextCreated()).isFalse();
                    assertThat(ctx.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
                });
    }

    @Test
    void readStatusReportsIdentityStatusOnlyForMemberContexts() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        DataspaceStatus status = statusService.readStatus();

        var host = status.participantContexts().get(0);
        var mgmt = status.participantContexts().get(1);
        var member = status.participantContexts().get(2);
        assertThat(host.identityStatus()).isNull();
        assertThat(mgmt.identityStatus()).isNull();
        assertThat(member.identityStatus()).isEqualTo(IdentityStatus.UNBOUND);
    }

    @Test
    void readStatusReportsIdentityMismatchForDriftedBoundRow() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        var bound = new DsParticipantEntity();
        bound.setParticipantType(ParticipantType.MEMBER);
        bound.setMemberIdentifier(ClientIdEntityFactory.create(OWNER));
        bound.setCtxId(OWNER_CTX_ID);
        bound.setDid(ParticipantIdentifierScheme.memberDid(OWNER, "ih.other.test:7183"));
        bound.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        bound.setState(ParticipantState.ACTIVE);
        when(dsParticipantRepository.findByMemberIdentifier(OWNER)).thenReturn(Optional.of(bound));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts().get(2).identityStatus())
                .isEqualTo(IdentityStatus.MISMATCH);
    }

    @Test
    void readStatusReportsIdentityDriftWhenHubServesDifferentDid() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(identityHubClient.contextDid(OWNER_CTX_ID))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.memberDid(OWNER, "ih.other.test:7183")));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts().get(2).identityStatus())
                .isEqualTo(IdentityStatus.DRIFTED);
    }

    @Test
    void readStatusOmitsMemberContextWhenOwnerNotYetSet() {
        var serverConf = mock(ServerConfEntity.class);
        when(serverConf.getOwner()).thenReturn(null);
        when(serverConfRepository.getServerConf()).thenReturn(serverConf);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(2);
        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::kind)
                .containsExactly(ParticipantKind.HOST, ParticipantKind.MANAGEMENT);
    }
}
