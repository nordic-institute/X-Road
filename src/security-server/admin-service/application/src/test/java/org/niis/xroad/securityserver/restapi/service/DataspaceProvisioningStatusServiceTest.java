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

import com.apicatalog.did.Did;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningStatusService.DataspaceStatus;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.entity.ServerConfEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceProvisioningStatusServiceTest {

    private static final String SYSTEM_PARTICIPANT_ID = ParticipantIdentifierScheme.SYSTEM_SEGMENT;

    private static final ClientId OWNER = ClientId.Conf.create("TEST", "GOV", "1234");
    private static final String OWNER_CTX_ID = ParticipantIdentifierScheme.memberCtxId(OWNER);
    private static final String SYSTEM_HOLDER_PID_SLOT0 =
            SYSTEM_PARTICIPANT_ID + "-" + OWNER_CTX_ID + ":SS0-xroad-membership-credential-request";
    private static final String MEMBER_HOLDER_PID_SLOT0 =
            OWNER_CTX_ID + "-" + OWNER_CTX_ID + ":SS0-xroad-membership-credential-request";

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
    @Mock
    private GlobalConfProvider globalConfProvider;

    private DataspaceProvisioningService provisioningService;
    private DataspaceProvisioningStatusService statusService;

    @BeforeEach
    void setUp() {
        lenient().when(dataspace.getIdentityHubUrl()).thenReturn("https://ih.example.test");
        lenient().when(dataspace.getCredentialDefinitionId()).thenReturn("xroad-membership-credential-definition");
        lenient().when(dataspace.getMaxHolderPidSlots()).thenReturn(20);
        lenient().when(dataspace.getDidPort()).thenReturn(7183);
        lenient().when(dataspace.getDspPort()).thenReturn(7181);
        lenient().when(dataspace.getIdentityHubStsPort()).thenReturn(7184);
        lenient().when(dataspace.getIdentityHubCredentialsPort()).thenReturn(7185);
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);

        var ownerEntity = mock(ClientEntity.class);
        lenient().when(ownerEntity.getIdentifier()).thenReturn(ClientIdEntityFactory.create(OWNER));
        lenient().when(ownerEntity.getClientStatus()).thenReturn(Client.STATUS_REGISTERED);
        var serverConf = mock(ServerConfEntity.class);
        lenient().when(serverConf.getOwner()).thenReturn(ownerEntity);
        lenient().when(serverConf.getServerCode()).thenReturn("SS0");
        lenient().when(serverConfRepository.getServerConf()).thenReturn(serverConf);
        lenient().when(clientRepository.getAllLocalClients()).thenReturn(List.of(ownerEntity));
        lenient().when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());

        lenient().when(globalConfProvider.getSecurityServerAddress(any())).thenReturn("ss.example.test");

        var ownSecurityServerResolver = new OwnSecurityServerResolver(
                new ServerConfRepositoryIdentitySource(serverConfRepository), globalConfProvider);
        provisioningService = new DataspaceProvisioningService(adminServiceProperties, identityHubClient, controlPlaneClient,
                clientRepository, ownSecurityServerResolver, dsParticipantRepository, globalConfProvider,
                new DataspaceDidAuthority(ownSecurityServerResolver, adminServiceProperties));

        statusService = new DataspaceProvisioningStatusService(provisioningService, readinessPredicates,
                new DataspaceDidAuthority(ownSecurityServerResolver, adminServiceProperties), dsParticipantRepository);
    }

    @Test
    void readStatusReportsServerLevelPublicationOfTheSystemValues() {
        var systemRow = new DsParticipantEntity();
        systemRow.setState(ParticipantState.ACTIVE);
        systemRow.setPublicationState(PublicationState.REJECTED);
        systemRow.setPublicationReason("conflict-reason");
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(systemRow));
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);

        var server = statusService.readStatus().server();

        assertThat(server.dataspaceReady()).isTrue();
        assertThat(server.dspBaseUrl()).contains("ss.example.test").contains("7181");
        assertThat(server.publicationState()).isEqualTo(PublicationState.REJECTED);
        assertThat(server.publicationReason()).isEqualTo("conflict-reason");
    }

    @Test
    void readStatusReportsNotSentWhenTheSystemRowHasNoPublicationState() {
        var systemRow = new DsParticipantEntity();
        systemRow.setState(ParticipantState.ACTIVE);
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(systemRow));

        var server = statusService.readStatus().server();

        assertThat(server.publicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(server.publicationReason()).isNull();
    }

    @Test
    void readStatusIgnoresThePublicationOfADecommissionedSystemRow() {
        var systemRow = new DsParticipantEntity();
        systemRow.setState(ParticipantState.DECOMMISSIONED);
        systemRow.setPublicationState(PublicationState.REJECTED);
        systemRow.setPublicationReason("conflict-reason");
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(systemRow));

        var server = statusService.readStatus().server();

        assertThat(server.publicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(server.publicationReason()).isNull();
    }

    @Test
    void readStatusReportsThePublicationOfAMemberRowAndNoneForTheSystemContext() {
        var memberRow = new DsParticipantEntity();
        memberRow.setState(ParticipantState.ACTIVE);
        memberRow.setPublicationState(PublicationState.REJECTED);
        memberRow.setPublicationReason("conflict-reason");
        when(dsParticipantRepository.findByMemberIdentifier(OWNER)).thenReturn(Optional.of(memberRow));
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        var status = statusService.readStatus();

        assertThat(status.memberPublications()).containsOnlyKeys(OWNER_CTX_ID);
        var publication = status.memberPublications().get(OWNER_CTX_ID);
        assertThat(publication.publicationState()).isEqualTo(PublicationState.REJECTED);
        assertThat(publication.publicationReason()).isEqualTo("conflict-reason");
    }

    @Test
    void readStatusIgnoresThePublicationOfADecommissionedMemberRow() {
        var activeRow = new DsParticipantEntity();
        activeRow.setState(ParticipantState.ACTIVE);
        var decommissionedRow = new DsParticipantEntity();
        decommissionedRow.setState(ParticipantState.DECOMMISSIONED);
        decommissionedRow.setPublicationState(PublicationState.REJECTED);
        decommissionedRow.setPublicationReason("conflict-reason");
        var reads = new AtomicInteger();
        when(dsParticipantRepository.findByMemberIdentifier(OWNER))
                .thenAnswer(_ -> Optional.of(reads.getAndIncrement() == 0 ? activeRow : decommissionedRow));
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        var publication = statusService.readStatus().memberPublications().get(OWNER_CTX_ID);

        assertThat(publication.publicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(publication.publicationReason()).isNull();
    }

    @Test
    void readStatusReportsNotSentForAMemberWithoutAPublicationStateOrRow() {
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        var publication = statusService.readStatus().memberPublications().get(OWNER_CTX_ID);

        assertThat(publication.publicationState()).isEqualTo(PublicationState.NOT_SENT);
        assertThat(publication.publicationReason()).isNull();
    }

    @Test
    void readStatusReportsSystemAndMemberContextsWithCredentialStatus() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(SYSTEM_PARTICIPANT_ID)).thenReturn(Optional.of(Did.parse("did:web:host:v1:system")));
        when(identityHubClient.contextDid(OWNER_CTX_ID)).thenReturn(Optional.of(Did.parse("did:web:host:v1:TEST:GOV:1234")));
        when(identityHubClient.getCredentialRequestState(SYSTEM_PARTICIPANT_ID, SYSTEM_HOLDER_PID_SLOT0))
                .thenReturn(CredentialStatus.ISSUED.name());
        when(identityHubClient.getCredentialRequestState(OWNER_CTX_ID, MEMBER_HOLDER_PID_SLOT0)).thenReturn(CredentialStatus.ISSUED.name());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.participantContexts()).hasSize(2);
        var system = status.participantContexts().get(0);
        var member = status.participantContexts().get(1);
        assertThat(system.participantId()).isEqualTo(SYSTEM_PARTICIPANT_ID);
        assertThat(system.kind()).isEqualTo(ParticipantKind.SYSTEM);
        assertThat(system.contextCreated()).isTrue();
        assertThat(system.credentialStatus()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(member.participantId()).isEqualTo(OWNER_CTX_ID);
        assertThat(member.kind()).isEqualTo(ParticipantKind.MEMBER);
        assertThat(member.contextCreated()).isTrue();
        assertThat(member.credentialStatus()).isEqualTo(CredentialStatus.ISSUED);
    }

    @Test
    void readStatusReportsMemberContextEvenWhenSystemIssuedAndMemberAbsent() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(SYSTEM_PARTICIPANT_ID)).thenReturn(Optional.of(Did.parse("did:web:host:v1:system")));
        when(identityHubClient.contextDid(OWNER_CTX_ID)).thenReturn(Optional.empty());
        when(identityHubClient.getCredentialRequestState(SYSTEM_PARTICIPANT_ID, SYSTEM_HOLDER_PID_SLOT0))
                .thenReturn(CredentialStatus.ISSUED.name());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(2);
        var system = status.participantContexts().get(0);
        var member = status.participantContexts().get(1);
        assertThat(system.kind()).isEqualTo(ParticipantKind.SYSTEM);
        assertThat(system.contextCreated()).isTrue();
        assertThat(system.credentialStatus()).isEqualTo(CredentialStatus.ISSUED);
        assertThat(member.kind()).isEqualTo(ParticipantKind.MEMBER);
        assertThat(member.contextCreated()).isFalse();
        assertThat(member.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
    }

    @Test
    void readStatusBackendUnreachableReportsUnknownInsteadOf5xx() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString()))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR).build());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.enabled()).isTrue();
        assertThat(status.participantContexts()).hasSize(2);
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

        assertThat(status.participantContexts()).hasSize(1);
        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::kind)
                .containsExactly(ParticipantKind.SYSTEM);
        assertThat(status.participantContexts())
                .allSatisfy(ctx -> {
                    assertThat(ctx.contextCreated()).isFalse();
                    assertThat(ctx.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
                });
    }

    @Test
    void readStatusProvisionedContextsReportedForSystemAndMember() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).hasSize(2);
        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::kind)
                .containsExactly(ParticipantKind.SYSTEM, ParticipantKind.MEMBER);
        assertThat(status.participantContexts())
                .allSatisfy(ctx -> {
                    assertThat(ctx.contextCreated()).isFalse();
                    assertThat(ctx.credentialStatus()).isEqualTo(CredentialStatus.ABSENT);
                });
    }

    @Test
    void readStatusReportsUnboundIdentityForSystemAndMemberWithoutBindingRows() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());

        DataspaceStatus status = statusService.readStatus();

        var system = status.participantContexts().get(0);
        var member = status.participantContexts().get(1);
        assertThat(system.identityStatus()).isEqualTo(IdentityStatus.UNBOUND);
        assertThat(member.identityStatus()).isEqualTo(IdentityStatus.UNBOUND);
    }

    @Test
    void readStatusReportsRejectedIdentityForSystemAndMemberWhenTheAddressCannotCarryADid() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(globalConfProvider.getSecurityServerAddress(any())).thenReturn("b\u00fccher.example");

        DataspaceStatus status = statusService.readStatus();

        var system = status.participantContexts().getFirst();
        var member = status.participantContexts().getLast();
        assertThat(system.identityStatus()).isEqualTo(IdentityStatus.REJECTED);
        assertThat(system.intendedDid()).isNull();
        assertThat(member.identityStatus()).isEqualTo(IdentityStatus.REJECTED);
        assertThat(member.intendedDid()).isNull();
        assertThat(status.server().dspBaseUrl()).isNull();
    }

    @Test
    void readStatusReportsSystemIdentityOkOnceItsBindingRowExists() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(dsParticipantRepository.findSystemParticipant())
                .thenReturn(Optional.of(systemRow(ParticipantIdentifierScheme.systemDid("ss.example.test:7183"))));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts().getFirst().identityStatus()).isEqualTo(IdentityStatus.OK);
    }

    @Test
    void readStatusReportsSystemIdentityDriftWhenHubServesDifferentDid() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(identityHubClient.contextDid(SYSTEM_PARTICIPANT_ID))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.systemDid("ih.other.test:7183")));
        when(dsParticipantRepository.findSystemParticipant())
                .thenReturn(Optional.of(systemRow(ParticipantIdentifierScheme.systemDid("ss.example.test:7183"))));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts().getFirst().identityStatus()).isEqualTo(IdentityStatus.DRIFTED);
    }

    @Test
    void readStatusKeepsSystemBindingOkWhenRegisteredAddressDiffersFromBoundDid() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(dsParticipantRepository.findSystemParticipant())
                .thenReturn(Optional.of(systemRow(ParticipantIdentifierScheme.systemDid("ih.other.test:7183"))));

        DataspaceStatus status = statusService.readStatus();

        var system = status.participantContexts().getFirst();
        assertThat(system.identityStatus()).isEqualTo(IdentityStatus.OK);
        assertThat(system.intendedDid()).isEqualTo(ParticipantIdentifierScheme.systemDid("ih.other.test:7183"));
    }

    @Test
    void readStatusKeepsBindingsOkWhenRegisteredAddressBecomesNonAscii() {
        lenient().when(globalConfProvider.getSecurityServerAddress(any())).thenReturn("b\u00fccher.example");
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(dsParticipantRepository.findSystemParticipant())
                .thenReturn(Optional.of(systemRow(ParticipantIdentifierScheme.systemDid("ss.example.test:7183"))));
        when(dsParticipantRepository.findByMemberIdentifier(OWNER))
                .thenReturn(Optional.of(memberRow(ParticipantIdentifierScheme.memberDid(OWNER, "ss.example.test:7183"))));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::identityStatus)
                .containsExactly(IdentityStatus.OK, IdentityStatus.OK);
    }

    private static DsParticipantEntity systemRow(Did did) {
        var row = new DsParticipantEntity();
        row.setParticipantType(ParticipantType.SYSTEM);
        row.setCtxId(SYSTEM_PARTICIPANT_ID);
        row.setDid(did.toString());
        row.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        row.setState(ParticipantState.ACTIVE);
        return row;
    }

    @Test
    void readStatusKeepsMemberBindingOkWhenRegisteredAddressDiffersFromBoundDid() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        var bound = memberRow(ParticipantIdentifierScheme.memberDid(OWNER, "ih.other.test:7183"));
        when(dsParticipantRepository.findByMemberIdentifier(OWNER)).thenReturn(Optional.of(bound));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts().get(1).identityStatus())
                .isEqualTo(IdentityStatus.OK);
    }

    private static DsParticipantEntity memberRow(Did did) {
        var row = new DsParticipantEntity();
        row.setParticipantType(ParticipantType.MEMBER);
        row.setMemberIdentifier(ClientIdEntityFactory.create(OWNER));
        row.setCtxId(OWNER_CTX_ID);
        row.setDid(did.toString());
        row.setSchemeVersion(ParticipantIdentifierScheme.SCHEME_VERSION);
        row.setState(ParticipantState.ACTIVE);
        return row;
    }

    @Test
    void readStatusReportsIdentityDriftWhenHubServesDifferentDid() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(identityHubClient.contextDid(anyString())).thenReturn(Optional.empty());
        when(identityHubClient.contextDid(OWNER_CTX_ID))
                .thenReturn(Optional.of(ParticipantIdentifierScheme.memberDid(OWNER, "ih.other.test:7183")));

        DataspaceStatus status = statusService.readStatus();

        assertThat(status.participantContexts().get(1).identityStatus())
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

        assertThat(status.participantContexts()).hasSize(1);
        assertThat(status.participantContexts()).extracting(DataspaceProvisioningService.ParticipantContextStatus::kind)
                .containsExactly(ParticipantKind.SYSTEM);
    }
}
