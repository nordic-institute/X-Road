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
package org.niis.xroad.securityserver.restapi.scheduling;

import ee.ria.xroad.common.identifier.ClientId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apicatalog.did.Did;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.DataspaceParticipantBindingService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.CredentialStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.TombstonedParticipant;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspaceParticipantProvisioningWorkerTest {

    private static final ClientId OWNER = ClientId.Conf.create("TEST", "GOV", "1234");
    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "COM", "5678");
    private static final String MEMBER_ID = "TEST:COM:5678";
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("TEST", "COM", "9012");

    private static final String SYSTEM_ID = "system";

    private static final ParticipantContext SYSTEM_CONTEXT = new ParticipantContext(SYSTEM_ID, ParticipantKind.SYSTEM, OWNER);
    private static final ParticipantContext MEMBER_CONTEXT = new ParticipantContext(MEMBER_ID, ParticipantKind.MEMBER, MEMBER);
    private static final ParticipantContext OTHER_MEMBER_CONTEXT =
            new ParticipantContext("TEST:COM:9012", ParticipantKind.MEMBER, OTHER_MEMBER);
    private static final ParticipantContext PRE_OWNER_SYSTEM_CONTEXT = new ParticipantContext(SYSTEM_ID, ParticipantKind.SYSTEM, null);

    @Mock
    private DataspaceProvisioningService dataspaceProvisioningService;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceParticipantBindingService participantBindingService;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspace;

    private static final Did HUB_DID = Did.parse("did:web:ss.example.test%3A7183:xrd-ss0");
    private static final Did DRIFTED_INTENDED_DID = Did.parse("did:web:ss.moved.test%3A7183:xrd-ss0");

    private static final ParticipantContextStatus UNBOUND_CONTEXT =
            statusOf(false, CredentialStatus.ABSENT, IdentityStatus.UNBOUND);

    @InjectMocks
    private DataspaceParticipantProvisioningWorker worker;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger cycleLogger =
            (Logger) LoggerFactory.getLogger(DataspaceParticipantProvisioningWorker.class.getName() + ".cycles");
    private final ListAppender<ILoggingEvent> workerAppender = new ListAppender<>();
    private final Logger workerLogger = (Logger) LoggerFactory.getLogger(DataspaceParticipantProvisioningWorker.class);

    @BeforeEach
    void setUp() {
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        when(dataspace.getProvisioningBatchSize()).thenReturn(100);
        when(dataspaceProvisioningService.registeredAddressKnown()).thenReturn(true);
        when(dataspaceProvisioningService.ensureParticipantContext(any())).thenReturn(true);
        when(dataspaceProvisioningService.readContextStatus(any())).thenReturn(UNBOUND_CONTEXT);
        when(participantBindingService.bindSystemIfAbsent(any())).thenReturn(true);
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        when(participantBindingService.bindMembersIfAbsent(any()))
                .thenAnswer(invocation -> new ArrayList<ClientId>(invocation.<Map<ClientId, Did>>getArgument(0).keySet()));
        appender.start();
        cycleLogger.addAppender(appender);
        cycleLogger.setLevel(Level.DEBUG);
        workerAppender.start();
        workerLogger.addAppender(workerAppender);
        workerLogger.setLevel(Level.WARN);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        cycleLogger.detachAppender(appender);
        appender.stop();
        cycleLogger.setLevel(null);
        workerLogger.detachAppender(workerAppender);
        workerAppender.stop();
        workerLogger.setLevel(null);
    }

    private static ParticipantContextStatus statusOf(boolean contextCreated, CredentialStatus credentialStatus,
            IdentityStatus identityStatus) {
        return new ParticipantContextStatus("irrelevant", ParticipantKind.SYSTEM, contextCreated ? HUB_DID : null, false,
                HUB_DID, credentialStatus, identityStatus);
    }

    @Test
    void scheduledProvisionSwallowsFailures() {
        when(dataspaceProvisioningService.participantContexts()).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> worker.scheduledProvision()).doesNotThrowAnyException();
    }

    @Test
    void provisionParticipantBestEffortSwallowsFailures() {
        when(dataspaceProvisioningService.participantContexts()).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> worker.provisionParticipantBestEffort()).doesNotThrowAnyException();
    }

    @Test
    void provisionParticipantBindsAMemberBeforeItsContextIsProvisioned() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        var order = inOrder(dataspaceProvisioningService, participantBindingService);
        order.verify(participantBindingService).bindMembersIfAbsent(Map.of(MEMBER, HUB_DID));
        order.verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantBindsSystemBeforeItsContextIsProvisioned() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));

        worker.provisionParticipant();

        var order = inOrder(dataspaceProvisioningService, participantBindingService);
        order.verify(participantBindingService).bindSystemIfAbsent(any());
        order.verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        order.verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
    }

    @Test
    void provisionParticipantBindsEachUnboundContextUnderTheDidItsStatusReadChecked() {
        var systemDid = ParticipantIdentifierScheme.systemDid("checked.example.test:7183");
        var memberDid = ParticipantIdentifierScheme.memberDid(MEMBER, "checked.example.test:7183");
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(unboundWithIntendedDid(ParticipantKind.SYSTEM, systemDid));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(unboundWithIntendedDid(ParticipantKind.MEMBER, memberDid));

        worker.provisionParticipant();

        verify(participantBindingService).bindSystemIfAbsent(systemDid);
        verify(participantBindingService).bindMembersIfAbsent(Map.of(MEMBER, memberDid));
    }

    @Test
    void provisionParticipantLeavesAnUnboundContextWhoseStatusReadStatedNoDidUnboundAndUnprovisioned() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(unboundWithIntendedDid(ParticipantKind.SYSTEM, null));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(unboundWithIntendedDid(ParticipantKind.MEMBER, null));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
    }

    private static ParticipantContextStatus unboundWithIntendedDid(ParticipantKind kind, Did intendedDid) {
        return new ParticipantContextStatus("irrelevant", kind, null, false, intendedDid,
                CredentialStatus.ABSENT, IdentityStatus.UNBOUND);
    }

    @Test
    void provisionParticipantBindsAndProvisionsNoSystemUntilTheAuthCertIsRegistered() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantBindsAndProvisionsNoSystemWhileDataspaceIsNotReady() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantDoesNotProvisionASystemContextThatCouldNotBeBound() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(participantBindingService.bindSystemIfAbsent(any())).thenReturn(false);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantReprovisionsABoundSystemContextWithoutBindingAgainWhileNotReadyAndWithoutAuthCert() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, IdentityStatus.OK));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantLeavesADriftedOrUnreadableSystemContextUnboundAndUnprovisioned() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT)).thenReturn(
                new ParticipantContextStatus(SYSTEM_ID, ParticipantKind.SYSTEM, HUB_DID, false, DRIFTED_INTENDED_DID,
                        CredentialStatus.ISSUED, IdentityStatus.DRIFTED),
                statusOf(true, CredentialStatus.ISSUED, IdentityStatus.UNKNOWN));

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(any(), any());
    }

    @Test
    void provisionParticipantBindsAndEnsuresASystemContextConvergedExceptForItsBinding() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.UNBOUND));

        worker.provisionParticipant();

        verify(participantBindingService).bindSystemIfAbsent(any());
        verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(any(), any());
    }

    @Test
    void provisionParticipantDoesNotTreatAContextWithoutAnIdentityStatusAsConverged() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, null));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(any(), any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
    }

    @Test
    void aSecondPassOverAConvergedSystemContextBindsAndProvisionsNothingNew() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT)).thenReturn(
                statusOf(false, CredentialStatus.ABSENT, IdentityStatus.UNBOUND),
                statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK));

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(participantBindingService, times(1)).bindSystemIfAbsent(any());
        verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, times(1)).ensureMembershipCredential(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantProvisionsOnlyTheMembersThatCouldBeBound() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT, OTHER_MEMBER_CONTEXT));
        when(participantBindingService.bindMembersIfAbsent(Map.of(MEMBER, HUB_DID, OTHER_MEMBER, HUB_DID)))
                .thenReturn(List.of(OTHER_MEMBER));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService).ensureParticipantContext(OTHER_MEMBER_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(OTHER_MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantKeepsTheBindingOfAMemberWhoseProvisioningFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        doThrow(new RuntimeException("identity hub unavailable")).when(dataspaceProvisioningService)
                .ensureParticipantContext(MEMBER_CONTEXT);

        worker.provisionParticipant();

        verify(participantBindingService).bindMembersIfAbsent(Map.of(MEMBER, HUB_DID));
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantBindsAndProvisionsNoMemberWhileDataspaceIsNotReady() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantReprovisionsABoundMemberWhileDataspaceIsNotReady() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, IdentityStatus.OK));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantReprovisionsABoundMemberUntilTheAuthCertIsRegisteredButRequestsNoCredential() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, IdentityStatus.OK));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantBindsAndProvisionsNoMemberUntilTheAuthCertIsRegistered() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantStillTearsDownARemovedMemberWhileDataspaceIsNotReady() {
        var tombstone = new TombstonedParticipant(1L, MEMBER_ID);
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.decommissionedParticipants()).thenReturn(List.of(tombstone));
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).teardownParticipant(tombstone);
    }

    @Test
    void aSecondPassOverConvergedMembersBindsAndProvisionsNothingNew() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT)).thenReturn(
                statusOf(false, CredentialStatus.ABSENT, IdentityStatus.UNBOUND),
                statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK));

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(participantBindingService, times(1)).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, times(1)).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantSkipsBindingWhenOwnerNotYetKnown() {
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(PRE_OWNER_SYSTEM_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any());
    }

    @Test
    void provisionParticipantSkipsWhenOwnerNotYetKnown() {
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(PRE_OWNER_SYSTEM_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantBindsAndProvisionsNoUnboundContextWhileTheRegisteredAddressIsUnknown() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.registeredAddressKnown()).thenReturn(false);

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantRecreatesALostBoundContextWhileTheRegisteredAddressIsUnknown() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.registeredAddressKnown()).thenReturn(false);
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, IdentityStatus.OK));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
    }

    @Test
    void provisionParticipantEnsuresCredentialForEachContextWhenAuthCertRegistered() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantEnsuresAndRequestsCredentialForABoundSystemContext() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, IdentityStatus.OK));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantContinuesWithRemainingContextsWhenOneFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        doThrow(new IllegalStateException("bound row mismatch"))
                .when(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantContinuesWithRemainingCredentialsWhenOneCredentialStepFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.ensureMembershipCredential(SYSTEM_CONTEXT))
                .thenThrow(new IllegalStateException("ih down"));

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantSkipsCredentialForSystemContextWhenReanchorUnconfirmed() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.ensureParticipantContext(SYSTEM_CONTEXT)).thenReturn(false);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantIssuesCredentialForSystemContextOnceReanchorConfirmed() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.ensureParticipantContext(SYSTEM_CONTEXT)).thenReturn(true);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantOnlyReappliesControlPlaneRecordsOnAFullyConvergedTick() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        var converged = statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK);
        var convergedMember = statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK);
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT)).thenReturn(converged);
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT)).thenReturn(convergedMember);

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(dataspaceProvisioningService, times(2)).ensureControlPlaneContext(SYSTEM_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, times(2)).ensureControlPlaneContext(MEMBER_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
    }

    @Test
    void provisionParticipantReappliesControlPlaneRecordsOnlyForConvergedContexts() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureControlPlaneContext(SYSTEM_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(eq(MEMBER_CONTEXT), any());
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantContinuesWhenControlPlaneRefreshOfOneConvergedContextFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK));
        doThrow(new RuntimeException("control plane down"))
                .when(dataspaceProvisioningService).ensureControlPlaneContext(SYSTEM_CONTEXT, HUB_DID);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureControlPlaneContext(MEMBER_CONTEXT, HUB_DID);
    }

    @Test
    void provisionParticipantLeavesAContextWhoseHubStateCouldNotBeReadForTheNextTick() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(SYSTEM_CONTEXT)).thenReturn(new ParticipantContextStatus(
                SYSTEM_ID, ParticipantKind.SYSTEM, null, true, HUB_DID, CredentialStatus.UNKNOWN, IdentityStatus.UNBOUND));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(eq(SYSTEM_CONTEXT), any());
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantLeavesAMemberWhoseIdentityCouldNotBeReadUnboundAndUnprovisioned() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.UNKNOWN));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantLeavesADriftedMemberUnboundAndUnprovisioned() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT)).thenReturn(new ParticipantContextStatus(
                MEMBER_ID, ParticipantKind.MEMBER, HUB_DID, false, DRIFTED_INTENDED_DID, CredentialStatus.ISSUED,
                IdentityStatus.DRIFTED));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantBindsAndProvisionsNothingForRejectedContextsOnTwoPasses() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(any()))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, IdentityStatus.REJECTED));

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(participantBindingService, never()).bindSystemIfAbsent(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(any(), any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantWarnsOncePerSkippedIdentityStatusNamingCountAndIds() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var rejected = memberContexts("rejected", 3);
        var drifted = memberContexts("drifted", 2);
        var unsupported = memberContexts("unsupported", 1);
        stubSkippedStatuses(rejected, IdentityStatus.REJECTED);
        stubSkippedStatuses(drifted, IdentityStatus.DRIFTED);
        stubSkippedStatuses(unsupported, IdentityStatus.VERSION_UNSUPPORTED);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(concat(rejected, drifted, unsupported));

        worker.provisionParticipant();

        var warnings = skippedWarnings();
        assertThat(warnings).hasSize(3);
        assertThat(warnings.getFirst()).contains("REJECTED").contains("3 participant")
                .contains("rejected-0", "rejected-1", "rejected-2");
        assertThat(warnings.get(1)).contains("DRIFTED").contains("2 participant").contains("drifted-0", "drifted-1");
        assertThat(warnings.get(2)).contains("VERSION_UNSUPPORTED").contains("1 participant").contains("unsupported-0");
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any());
    }

    @Test
    void provisionParticipantWarnsOncePerStatusAcrossBatches() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspace.getProvisioningBatchSize()).thenReturn(2);
        var rejected = memberContexts("rejected", 5);
        stubSkippedStatuses(rejected, IdentityStatus.REJECTED);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(rejected);

        worker.provisionParticipant();

        assertThat(skippedWarnings()).hasSize(1).first().asString().contains("5 participant");
    }

    @Test
    void provisionParticipantListsOnlyTheCappedIdsOfManySkippedContextsWithTheTotalCount() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var rejected = memberContexts("rejected", 25);
        stubSkippedStatuses(rejected, IdentityStatus.REJECTED);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(rejected);

        worker.provisionParticipant();

        var warnings = skippedWarnings();
        assertThat(warnings).hasSize(1);
        var warning = warnings.getFirst();
        assertThat(warning).contains("25 participant", "rejected-0", "rejected-9")
                .doesNotContain("rejected-10", "rejected-24");
    }

    @Test
    void provisionParticipantWarnsAboutUnchangedSkippedContextsOnlyOnTheFirstPassAndLogsTheRestAtDebug() {
        workerLogger.setLevel(Level.DEBUG);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var rejected = memberContexts("rejected", 2);
        stubSkippedStatuses(rejected, IdentityStatus.REJECTED);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(rejected, rejected.reversed());

        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(skippedLines(Level.WARN)).hasSize(1);
        assertThat(skippedLines(Level.DEBUG)).hasSize(1).first().asString().contains("REJECTED").contains("2 participant");
    }

    @Test
    void provisionParticipantWarnsAgainOnlyForTheStatusWhoseSkippedContextsChanged() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var rejected = memberContexts("rejected", 3);
        var drifted = memberContexts("drifted", 1);
        stubSkippedStatuses(rejected, IdentityStatus.REJECTED);
        stubSkippedStatuses(drifted, IdentityStatus.DRIFTED);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(concat(rejected.subList(0, 2), drifted), concat(rejected, drifted));

        worker.provisionParticipant();
        worker.provisionParticipant();
        worker.provisionParticipant();

        var warnings = skippedWarnings();
        assertThat(warnings).hasSize(3);
        assertThat(warnings.get(2)).contains("REJECTED").contains("3 participant");
    }

    @Test
    void provisionParticipantDoesNotWarnAgainWhenASkippedContextIsUnreadableForOneTick() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var drifted = memberContexts("drifted", 2);
        stubSkippedStatuses(drifted, IdentityStatus.DRIFTED);
        var driftedStatus = statusOf(false, CredentialStatus.ABSENT, IdentityStatus.DRIFTED);
        var unreadable = new ParticipantContextStatus("irrelevant", ParticipantKind.MEMBER, null, true, HUB_DID,
                CredentialStatus.UNKNOWN, IdentityStatus.DRIFTED);
        when(dataspaceProvisioningService.readContextStatus(drifted.get(1))).thenReturn(driftedStatus, unreadable, driftedStatus);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(drifted);

        worker.provisionParticipant();
        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(skippedWarnings()).hasSize(1);
    }

    @Test
    void provisionParticipantRemindsAboutUnchangedSkippedContextsOnceAnHour() {
        var start = Instant.parse("2026-11-03T10:00:00Z");
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var rejected = memberContexts("rejected", 1);
        stubSkippedStatuses(rejected, IdentityStatus.REJECTED);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(rejected);

        worker.setClock(Clock.fixed(start, ZoneOffset.UTC));
        worker.provisionParticipant();
        worker.setClock(Clock.fixed(start.plus(Duration.ofMinutes(59)), ZoneOffset.UTC));
        worker.provisionParticipant();
        worker.setClock(Clock.fixed(start.plus(Duration.ofHours(1)), ZoneOffset.UTC));
        worker.provisionParticipant();

        assertThat(skippedWarnings()).hasSize(2);
    }

    private static List<ParticipantContext> memberContexts(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new ParticipantContext(prefix + "-" + i, ParticipantKind.MEMBER,
                        ClientId.Conf.create("TEST", "COM", prefix + i)))
                .toList();
    }

    @SafeVarargs
    private static List<ParticipantContext> concat(List<ParticipantContext>... lists) {
        return Arrays.stream(lists).flatMap(List::stream).toList();
    }

    private void stubSkippedStatuses(List<ParticipantContext> contexts, IdentityStatus identityStatus) {
        contexts.forEach(context -> when(dataspaceProvisioningService.readContextStatus(context))
                .thenReturn(statusOf(false, CredentialStatus.ABSENT, identityStatus)));
    }

    private List<String> skippedWarnings() {
        return skippedLines(Level.WARN);
    }

    private List<String> skippedLines(Level level) {
        return workerAppender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("participant context(s) with identity status"))
                .toList();
    }

    @Test
    void provisionParticipantBindsAndEnsuresAMemberConvergedExceptForItsIdentity() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.UNBOUND));

        worker.provisionParticipant();

        verify(participantBindingService).bindMembersIfAbsent(Map.of(MEMBER, HUB_DID));
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantAsyncRunsImmediatelyWithoutActiveTransaction() {
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));

        worker.provisionParticipantAsync();

        verify(dataspaceProvisioningService, timeout(1000)).participantContexts();
    }

    @Test
    void provisionParticipantAsyncCoalescesTriggersArrivingWhileARunIsQueued() throws InterruptedException {
        var firstRunStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(dataspaceProvisioningService.participantContexts()).thenAnswer(invocation -> {
            firstRunStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of(SYSTEM_CONTEXT);
        });

        worker.provisionParticipantAsync();
        assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue();
        worker.provisionParticipantAsync();
        worker.provisionParticipantAsync();
        release.countDown();

        verify(dataspaceProvisioningService, after(500).times(2)).participantContexts();
    }

    @Test
    void provisionParticipantAsyncSchedulesASingleRunPerTransaction() {
        TransactionSynchronizationManager.initSynchronization();

        worker.provisionParticipantAsync();
        worker.provisionParticipantAsync();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void provisionParticipantAsyncDefersUntilTransactionCommitsWhenTransactionActive() {
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));
        TransactionSynchronizationManager.initSynchronization();

        worker.provisionParticipantAsync();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(dataspaceProvisioningService, never()).participantContexts();

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        verify(dataspaceProvisioningService, timeout(1000)).participantContexts();
    }

    // --- cycle summary ---

    @Test
    void provisionParticipantLogsExactlyOneCycleSummaryWithTheContextCount() {
        var failingContext = new ParticipantContext("TEST:COM:9999", ParticipantKind.MEMBER,
                ClientId.Conf.create("TEST", "COM", "9999"));
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(SYSTEM_CONTEXT, MEMBER_CONTEXT, failingContext));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK));
        doThrow(new RuntimeException("DID drift"))
                .when(dataspaceProvisioningService).ensureParticipantContext(failingContext);
        when(dataspaceProvisioningService.ensureMembershipCredential(SYSTEM_CONTEXT)).thenReturn(CredentialStatus.PENDING);

        worker.provisionParticipant();

        var summaries = cycleSummaries();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst())
                .containsPattern("cycleStart=\\S+")
                .containsPattern("durationMs=\\d+")
                .contains("contexts=3")
                .doesNotContain("contextsCreated")
                .doesNotContain("credentialsIssued");
    }

    @Test
    void provisionParticipantLogsOneCycleSummaryEvenWhenParticipantContextsFails() {
        when(dataspaceProvisioningService.participantContexts()).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> worker.provisionParticipant()).isInstanceOf(RuntimeException.class);

        var summaries = cycleSummaries();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst()).contains("contexts=-1");
    }

    private List<String> cycleSummaries() {
        var testThread = Thread.currentThread().getName();
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.DEBUG)
                .filter(event -> testThread.equals(event.getThreadName()))
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("cycle summary"))
                .toList();
    }

    // --- batches ---

    @Test
    void twoHundredFiftyUnboundMembersAreBoundAndProvisionedInThreeBatchesWithTheDefaultBatchSize() {
        var members = members(250);
        givenNoSystemAndMembers(members);

        worker.provisionParticipant();

        assertThat(bindBatchSizes()).containsExactly(100, 100, 50);
        members.forEach(member -> verify(dataspaceProvisioningService).ensureParticipantContext(member));
        members.forEach(member -> verify(dataspaceProvisioningService).ensureMembershipCredential(member));
    }

    @Test
    void aCustomBatchSizeChangesTheBatchCount() {
        when(dataspace.getProvisioningBatchSize()).thenReturn(50);
        givenNoSystemAndMembers(members(250));

        worker.provisionParticipant();

        assertThat(bindBatchSizes()).containsExactly(50, 50, 50, 50, 50);
    }

    @Test
    void everyMemberOfABatchIsBoundBeforeAnyMemberOfThatBatchIsProvisioned() {
        when(dataspace.getProvisioningBatchSize()).thenReturn(2);
        var members = members(4);
        givenNoSystemAndMembers(members);
        var events = new ArrayList<String>();
        doAnswer(invocation -> {
            Map<ClientId, Did> ids = invocation.getArgument(0);
            events.add("bind " + ids.keySet().stream().map(ClientId::getMemberCode).toList());
            return new ArrayList<>(ids.keySet());
        }).when(participantBindingService).bindMembersIfAbsent(any());
        when(dataspaceProvisioningService.ensureParticipantContext(any())).thenAnswer(invocation -> {
            events.add("ensure " + ((ParticipantContext) invocation.getArgument(0)).memberId().getMemberCode());
            return true;
        });

        worker.provisionParticipant();

        assertThat(events).containsExactly(
                "bind [0000, 0001]", "ensure 0000", "ensure 0001",
                "bind [0002, 0003]", "ensure 0002", "ensure 0003");
    }

    @Test
    void batchesHoldSystemFirstAndThenMembersInParticipantIdOrderWhateverOrderTheyAreListedIn() {
        when(dataspace.getProvisioningBatchSize()).thenReturn(2);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        var members = members(3);
        when(dataspaceProvisioningService.participantContexts())
                .thenReturn(List.of(members.get(2), members.getFirst(), SYSTEM_CONTEXT, members.get(1)));

        worker.provisionParticipant();

        var order = inOrder(participantBindingService);
        order.verify(participantBindingService).bindMembersIfAbsent(Map.of(members.getFirst().memberId(), HUB_DID));
        order.verify(participantBindingService).bindSystemIfAbsent(any());
        order.verify(participantBindingService)
                .bindMembersIfAbsent(Map.of(members.get(1).memberId(), HUB_DID, members.get(2).memberId(), HUB_DID));
    }

    @Test
    void aMemberWhoseProvisioningFailsIsLeftOutOfItsBatchAndProvisionedOnTheNextPass() {
        var members = members(3);
        var failing = members.get(1);
        givenNoSystemAndMembers(members);
        var tracker = new ConvergenceTracker();
        when(dataspaceProvisioningService.ensureParticipantContext(any())).thenReturn(true);
        doThrow(new RuntimeException("identity hub unavailable")).doReturn(true)
                .when(dataspaceProvisioningService).ensureParticipantContext(failing);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(members.getFirst());
        verify(dataspaceProvisioningService).ensureParticipantContext(members.get(2));
        verify(dataspaceProvisioningService).ensureMembershipCredential(members.getFirst());
        verify(dataspaceProvisioningService).ensureMembershipCredential(members.get(2));
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(failing);
        assertThat(tracker.issued).containsExactlyInAnyOrder(members.getFirst(), members.get(2));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, times(2)).ensureParticipantContext(failing);
        verify(dataspaceProvisioningService).ensureMembershipCredential(failing);
        verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(members.getFirst());
        verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(members.get(2));
        verify(participantBindingService, times(1)).bindMembersIfAbsent(any());
    }

    @Test
    void aPassAfterEveryMemberConvergedBindsAndProvisionsNothing() {
        var members = members(250);
        givenNoSystemAndMembers(members);
        new ConvergenceTracker();

        worker.provisionParticipant();
        worker.provisionParticipant();

        assertThat(bindBatchSizes()).containsExactly(100, 100, 50);
        members.forEach(member -> verify(dataspaceProvisioningService, times(1)).ensureParticipantContext(member));
        members.forEach(member -> verify(dataspaceProvisioningService, times(1)).ensureMembershipCredential(member));
    }

    private static List<ParticipantContext> members(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> {
                    var code = "%04d".formatted(index);
                    return new ParticipantContext("TEST:COM:" + code, ParticipantKind.MEMBER,
                            ClientId.Conf.create("TEST", "COM", code));
                })
                .toList();
    }

    private void givenNoSystemAndMembers(List<ParticipantContext> members) {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts()).thenReturn(members);
    }

    private List<Integer> bindBatchSizes() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<ClientId, Did>> batches = ArgumentCaptor.forClass(Map.class);
        verify(participantBindingService, atLeastOnce()).bindMembersIfAbsent(batches.capture());
        return batches.getAllValues().stream().map(Map::size).toList();
    }

    /**
     * Makes the mocked identity state follow the worker's writes: a member is bound once its bind call
     * returned it, and issued once its credential was requested.
     */
    private final class ConvergenceTracker {
        private final Set<ClientId> bound = new HashSet<>();
        private final Set<ParticipantContext> issued = new HashSet<>();

        private ConvergenceTracker() {
            doAnswer(invocation -> {
                Map<ClientId, Did> ids = invocation.getArgument(0);
                bound.addAll(ids.keySet());
                return new ArrayList<>(ids.keySet());
            }).when(participantBindingService).bindMembersIfAbsent(any());
            when(dataspaceProvisioningService.ensureMembershipCredential(any())).thenAnswer(invocation -> {
                issued.add(invocation.getArgument(0));
                return CredentialStatus.ISSUED;
            });
            when(dataspaceProvisioningService.readContextStatus(any())).thenAnswer(invocation -> {
                ParticipantContext context = invocation.getArgument(0);
                if (issued.contains(context)) {
                    return statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK);
                }
                return bound.contains(context.memberId())
                        ? statusOf(false, CredentialStatus.ABSENT, IdentityStatus.OK)
                        : UNBOUND_CONTEXT;
            });
        }
    }

    // --- teardown ---

    @Test
    void provisionParticipantTearsDownEachDecommissionedParticipant() {
        var tombstoneA = new TombstonedParticipant(1L, "ctx-a");
        var tombstoneB = new TombstonedParticipant(2L, "ctx-b");
        when(dataspaceProvisioningService.decommissionedParticipants()).thenReturn(List.of(tombstoneA, tombstoneB));
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of());

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).teardownParticipant(tombstoneA);
        verify(dataspaceProvisioningService).teardownParticipant(tombstoneB);
    }

    @Test
    void provisionParticipantContinuesRemainingTeardownsWhenOneFails() {
        var tombstoneA = new TombstonedParticipant(1L, "ctx-a");
        var tombstoneB = new TombstonedParticipant(2L, "ctx-b");
        when(dataspaceProvisioningService.decommissionedParticipants()).thenReturn(List.of(tombstoneA, tombstoneB));
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of());
        doThrow(new IllegalStateException("control plane unreachable"))
                .when(dataspaceProvisioningService).teardownParticipant(tombstoneA);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).teardownParticipant(tombstoneA);
        verify(dataspaceProvisioningService).teardownParticipant(tombstoneB);
    }

    @Test
    void provisionParticipantDoesNotAttemptTeardownWhenNoTombstonesExist() {
        when(dataspaceProvisioningService.decommissionedParticipants()).thenReturn(List.of());
        when(dataspaceProvisioningService.participantContexts()).thenReturn(List.of(SYSTEM_CONTEXT));

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService, never()).teardownParticipant(any());
    }

    // --- single-threaded convergence ---

    @Test
    void asyncTriggeredProvisioningNeverRunsConcurrentlyWithScheduledTick() throws InterruptedException {
        var concurrentInvocations = new AtomicInteger(0);
        var maxConcurrentInvocations = new AtomicInteger(0);
        var completedInvocations = new AtomicInteger(0);
        var completionLatch = new CountDownLatch(2);

        when(dataspaceProvisioningService.participantContexts()).thenAnswer(invocation -> {
            int concurrent = concurrentInvocations.incrementAndGet();
            maxConcurrentInvocations.updateAndGet(max -> Math.max(max, concurrent));
            Thread.sleep(100);
            concurrentInvocations.decrementAndGet();
            completedInvocations.incrementAndGet();
            completionLatch.countDown();
            return List.of();
        });

        new Thread(worker::scheduledProvision).start();
        worker.provisionParticipantAsync();

        assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(maxConcurrentInvocations.get()).isEqualTo(1);
        assertThat(completedInvocations.get()).isEqualTo(2);
    }
}
