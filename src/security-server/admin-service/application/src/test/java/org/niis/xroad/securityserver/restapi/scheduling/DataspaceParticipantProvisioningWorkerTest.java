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

import com.apicatalog.did.Did;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.securityserver.restapi.service.DataspaceParticipantBindingService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.CredentialStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.IdentityStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContextStatus;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
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
    private static final String HOST_ID = "xrd-ss0";
    private static final String MGMT_ID = "xrd-ss0-mgmt";
    private static final String MEMBER_ID = "TEST:COM:5678";

    private static final String SYSTEM_ID = "system";

    private static final ParticipantContext HOST_CONTEXT = new ParticipantContext(HOST_ID, ParticipantKind.HOST, OWNER);
    private static final ParticipantContext MGMT_CONTEXT = new ParticipantContext(MGMT_ID, ParticipantKind.MANAGEMENT, OWNER);
    private static final ParticipantContext SYSTEM_CONTEXT = new ParticipantContext(SYSTEM_ID, ParticipantKind.SYSTEM, OWNER);
    private static final ParticipantContext MEMBER_CONTEXT = new ParticipantContext(MEMBER_ID, ParticipantKind.MEMBER, MEMBER);
    private static final ParticipantContext PRE_OWNER_HOST_CONTEXT = new ParticipantContext(HOST_ID, ParticipantKind.HOST, null);

    @Mock
    private DataspaceProvisioningService dataspaceProvisioningService;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceParticipantBindingService participantBindingService;

    private static final Did HUB_DID = Did.parse("did:web:ss.example.test%3A7183:xrd-ss0");

    private static final ParticipantContextStatus NOT_CONVERGED =
            statusOf(false, CredentialStatus.ABSENT, null);

    @InjectMocks
    private DataspaceParticipantProvisioningWorker worker;

    @BeforeEach
    void setUp() {
        when(readinessPredicates.isManagementSubsystemRegistered()).thenReturn(true);
        when(dataspaceProvisioningService.registeredAddressKnown()).thenReturn(true);
        when(dataspaceProvisioningService.ensureParticipantContext(any())).thenReturn(true);
        when(dataspaceProvisioningService.readContextStatus(any())).thenReturn(NOT_CONVERGED);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ParticipantContextStatus statusOf(boolean contextCreated, CredentialStatus credentialStatus,
            IdentityStatus identityStatus) {
        return new ParticipantContextStatus("irrelevant", ParticipantKind.HOST, contextCreated ? HUB_DID : null,
                credentialStatus, identityStatus);
    }

    @Test
    void scheduledProvisionSwallowsFailures() {
        when(dataspaceProvisioningService.participantContexts(true)).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> worker.scheduledProvision()).doesNotThrowAnyException();
    }

    @Test
    void provisionParticipantBestEffortSwallowsFailures() {
        when(dataspaceProvisioningService.participantContexts(true)).thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> worker.provisionParticipantBestEffort()).doesNotThrowAnyException();
    }

    @Test
    void provisionParticipantBindsMemberIdentitiesOnlyAfterTheirContextIsEnsured() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true))
                .thenReturn(List.of(HOST_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        var order = inOrder(dataspaceProvisioningService, participantBindingService);
        order.verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        order.verify(participantBindingService).bindMembersIfAbsent(List.of(MEMBER), true);
    }

    @Test
    void provisionParticipantLeavesADriftedMemberUnbound() {
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));
        doThrow(new RuntimeException("DID drift")).when(dataspaceProvisioningService)
                .ensureParticipantContext(MEMBER_CONTEXT);

        worker.provisionParticipant();

        verify(participantBindingService).bindMembersIfAbsent(List.of(), false);
    }

    @Test
    void provisionParticipantBindsNothingUntilTheAuthCertIsRegistered() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService).bindMembersIfAbsent(List.of(MEMBER), false);
    }

    @Test
    void provisionParticipantSkipsBindingWhenOwnerNotYetKnown() {
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(PRE_OWNER_HOST_CONTEXT));

        worker.provisionParticipant();

        verify(participantBindingService, never()).bindMembersIfAbsent(any(), anyBoolean());
    }

    @Test
    void provisionParticipantSkipsWhenOwnerNotYetKnown() {
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(PRE_OWNER_HOST_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantSkipsWhenRegisteredAddressNotYetKnown() {
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MGMT_CONTEXT));
        when(dataspaceProvisioningService.registeredAddressKnown()).thenReturn(false);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantEnsuresAllContextsAndDefersCredentialUntilAuthCertRegistered() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts(true))
                .thenReturn(List.of(HOST_CONTEXT, SYSTEM_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(HOST_CONTEXT);
        verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureParticipantContext(MGMT_CONTEXT);
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
    }

    @Test
    void provisionParticipantEnsuresCredentialForEachContextWhenAuthCertRegistered() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true))
                .thenReturn(List.of(HOST_CONTEXT, SYSTEM_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureMembershipCredential(HOST_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MGMT_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantEnsuresSystemContextUnconditionallyEvenWithoutManagement() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, SYSTEM_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantContinuesWithRemainingContextsWhenOneFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT, MGMT_CONTEXT));
        doThrow(new IllegalStateException("bound row mismatch"))
                .when(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureParticipantContext(HOST_CONTEXT);
        verify(dataspaceProvisioningService).ensureParticipantContext(MGMT_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(HOST_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MGMT_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantContinuesWithRemainingCredentialsWhenOneCredentialStepFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.ensureMembershipCredential(HOST_CONTEXT))
                .thenThrow(new IllegalStateException("ih down"));

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantSkipsCredentialForSystemContextWhenReanchorUnconfirmed() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true))
                .thenReturn(List.of(HOST_CONTEXT, SYSTEM_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.ensureParticipantContext(SYSTEM_CONTEXT)).thenReturn(false);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(SYSTEM_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(HOST_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MGMT_CONTEXT);
        verify(dataspaceProvisioningService).ensureMembershipCredential(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantIssuesCredentialForSystemContextOnceReanchorConfirmed() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(SYSTEM_CONTEXT));
        when(dataspaceProvisioningService.ensureParticipantContext(SYSTEM_CONTEXT)).thenReturn(true);

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureMembershipCredential(SYSTEM_CONTEXT);
    }

    @Test
    void provisionParticipantQueriesContextsForTheCurrentManagementRegistrationState() {
        when(readinessPredicates.isManagementSubsystemRegistered()).thenReturn(false);
        when(dataspaceProvisioningService.participantContexts(false)).thenReturn(List.of(HOST_CONTEXT));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).participantContexts(false);
    }

    @Test
    void provisionParticipantOnlyReappliesControlPlaneRecordsOnAFullyConvergedTick() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true))
                .thenReturn(List.of(HOST_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));
        var converged = statusOf(true, CredentialStatus.ISSUED, null);
        var convergedMember = statusOf(true, CredentialStatus.ISSUED, IdentityStatus.OK);
        when(dataspaceProvisioningService.readContextStatus(HOST_CONTEXT)).thenReturn(converged);
        when(dataspaceProvisioningService.readContextStatus(MGMT_CONTEXT)).thenReturn(converged);
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT)).thenReturn(convergedMember);

        worker.provisionParticipant();
        worker.provisionParticipant();

        verify(dataspaceProvisioningService, times(2)).ensureControlPlaneContext(HOST_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, times(2)).ensureControlPlaneContext(MGMT_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, times(2)).ensureControlPlaneContext(MEMBER_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(any());
        verify(dataspaceProvisioningService, never()).ensureMembershipCredential(any());
        verify(participantBindingService, never()).bindMembersIfAbsent(any(), anyBoolean());
    }

    @Test
    void provisionParticipantReappliesControlPlaneRecordsOnlyForConvergedContexts() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT, MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(HOST_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, null));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureControlPlaneContext(HOST_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService, never()).ensureControlPlaneContext(eq(MEMBER_CONTEXT), any());
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(dataspaceProvisioningService, never()).ensureParticipantContext(HOST_CONTEXT);
    }

    @Test
    void provisionParticipantContinuesWhenControlPlaneRefreshOfOneConvergedContextFails() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true))
                .thenReturn(List.of(HOST_CONTEXT, MGMT_CONTEXT, MEMBER_CONTEXT));
        var converged = statusOf(true, CredentialStatus.ISSUED, null);
        when(dataspaceProvisioningService.readContextStatus(HOST_CONTEXT)).thenReturn(converged);
        when(dataspaceProvisioningService.readContextStatus(MGMT_CONTEXT)).thenReturn(converged);
        doThrow(new RuntimeException("control plane down"))
                .when(dataspaceProvisioningService).ensureControlPlaneContext(HOST_CONTEXT, HUB_DID);

        assertThatCode(() -> worker.provisionParticipant()).doesNotThrowAnyException();

        verify(dataspaceProvisioningService).ensureControlPlaneContext(MGMT_CONTEXT, HUB_DID);
        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantKeepsRecheckingAMemberWhoseIdentityCouldNotBeRead() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.UNKNOWN));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
    }

    @Test
    void provisionParticipantEnsuresAndBindsAMemberConvergedExceptForItsIdentity() {
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(MEMBER_CONTEXT));
        when(dataspaceProvisioningService.readContextStatus(MEMBER_CONTEXT))
                .thenReturn(statusOf(true, CredentialStatus.ISSUED, IdentityStatus.UNBOUND));

        worker.provisionParticipant();

        verify(dataspaceProvisioningService).ensureParticipantContext(MEMBER_CONTEXT);
        verify(participantBindingService).bindMembersIfAbsent(List.of(MEMBER), true);
    }

    @Test
    void provisionParticipantAsyncRunsImmediatelyWithoutActiveTransaction() {
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT));

        worker.provisionParticipantAsync();

        verify(dataspaceProvisioningService, timeout(1000)).participantContexts(true);
    }

    @Test
    void provisionParticipantAsyncCoalescesTriggersArrivingWhileARunIsQueued() throws InterruptedException {
        var firstRunStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(dataspaceProvisioningService.participantContexts(true)).thenAnswer(invocation -> {
            firstRunStarted.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of(HOST_CONTEXT);
        });

        worker.provisionParticipantAsync();
        assertThat(firstRunStarted.await(5, TimeUnit.SECONDS)).isTrue();
        worker.provisionParticipantAsync();
        worker.provisionParticipantAsync();
        release.countDown();

        verify(dataspaceProvisioningService, after(500).times(2)).participantContexts(true);
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
        when(dataspaceProvisioningService.participantContexts(true)).thenReturn(List.of(HOST_CONTEXT));
        TransactionSynchronizationManager.initSynchronization();

        worker.provisionParticipantAsync();

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        verify(dataspaceProvisioningService, never()).participantContexts(anyBoolean());

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());

        verify(dataspaceProvisioningService, timeout(1000)).participantContexts(true);
    }
}
