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
package org.niis.xroad.cs.admin.core.dataspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.IssuerHostState;
import org.niis.xroad.cs.admin.api.dto.IssuerSetUpResult;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssuerSetUpServiceImplTest {

    private static final String HOST = "issuer.example.org";
    private static final String NODE = "node-a";
    private static final int DEADLINE_AFTER_MS = 60000;

    @Mock
    private IssuerHostService issuerHostService;
    @Mock
    private ConnectorCertificateCheck connectorCertificateCheck;
    @Mock
    private DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    @Mock
    private IssuerSetUpLeaseService issuerSetUpLeaseService;
    @Mock
    private IssuerProvisioningRpcChannelProperties issuerProvisioningRpcChannelProperties;

    private IssuerSetUpServiceImpl issuerSetUpService;

    @BeforeEach
    void setUp() {
        issuerSetUpService = new IssuerSetUpServiceImpl(issuerHostService, connectorCertificateCheck,
                dataspaceIssuerProvisioningService, issuerSetUpLeaseService, issuerProvisioningRpcChannelProperties,
                new HAConfigStatus(NODE, true));
    }

    /** Stubs every check before the lease so a test can start at "no run in progress". */
    private void givenEveryPrerequisiteCheckPasses() {
        when(issuerHostService.getState()).thenReturn(new IssuerHostState(HOST, false));
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
    }

    @Test
    void refusesAlreadySetUpWhenTheIssuerDidIsBound() {
        when(issuerHostService.getState()).thenReturn(new IssuerHostState(HOST, true));

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_SET_UP));
        // No collaborator here can write issuer_dids, so the already-bound refusal leaves it untouched
        // by construction: proving that requires only that neither check ran past the bound check.
        verifyNoInteractions(connectorCertificateCheck, dataspaceIssuerProvisioningService);
    }

    @Test
    void refusesHostNotSetWhenNoHostIsConfigured() {
        when(issuerHostService.getState()).thenReturn(new IssuerHostState(null, false));

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.HOST_NOT_SET));
        verifyNoInteractions(connectorCertificateCheck, dataspaceIssuerProvisioningService);
    }

    @Test
    void refusesCertificateNotReadyWhenTheCertificateCheckFails() {
        when(issuerHostService.getState()).thenReturn(new IssuerHostState(HOST, false));
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(false);

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.CERTIFICATE_NOT_READY));
        verifyNoInteractions(dataspaceIssuerProvisioningService);
    }

    @Test
    void refusesAlreadyRunningWhenAnotherNodeHoldsTheLease() {
        givenEveryPrerequisiteCheckPasses();
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.of("node-b"));

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_RUNNING));
        verifyNoInteractions(dataspaceIssuerProvisioningService);
    }

    @Test
    void refusesAlreadyRunningWhenAcquiringTheFreeLeaseRacesAnotherNode() {
        givenEveryPrerequisiteCheckPasses();
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(issuerSetUpLeaseService.tryAcquire(eq(NODE), any())).thenReturn(false);

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_RUNNING));
        verifyNoInteractions(dataspaceIssuerProvisioningService);
    }

    @Test
    void refusesAlreadySetUpWhenTheIssuerBecomesBoundWhileAcquiringTheLease() {
        when(issuerHostService.getState())
                .thenReturn(new IssuerHostState(HOST, false), new IssuerHostState(HOST, true));
        when(connectorCertificateCheck.isReadyFor(HOST)).thenReturn(true);
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(issuerSetUpLeaseService.tryAcquire(eq(NODE), any())).thenReturn(true);
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_SET_UP));
        verify(issuerSetUpLeaseService).release(NODE);
        verifyNoInteractions(dataspaceIssuerProvisioningService);
    }

    /**
     * The fake run blocks until released, so {@code start()} returning "accepted" is only possible if
     * the provisioning sequence genuinely runs on a background thread rather than inline: an inline
     * call would hang this test on the same latch it asserts against below. {@code provisionIssuer()}
     * returning does not by itself mean the lease has been released, so the release is awaited through
     * the mocked {@code release(NODE)} call itself.
     */
    @Test
    void acceptsAndRunsTodaysProvisioningSequenceInTheBackgroundWhenEveryCheckPasses() throws Exception {
        givenEveryPrerequisiteCheckPasses();
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(issuerSetUpLeaseService.tryAcquire(eq(NODE), any())).thenReturn(true);
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);

        var runStarted = new CountDownLatch(1);
        var releaseRun = new CountDownLatch(1);
        var leaseReleased = new CountDownLatch(1);
        doAnswer(invocation -> {
            runStarted.countDown();
            assertThat(releaseRun.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(dataspaceIssuerProvisioningService).provisionIssuer();
        doAnswer(invocation -> {
            leaseReleased.countDown();
            return null;
        }).when(issuerSetUpLeaseService).release(NODE);

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Accepted());
        assertThat(runStarted.await(2, TimeUnit.SECONDS)).isTrue();
        // The lease stays held while the run waits on the fake gRPC calls above.
        verify(issuerSetUpLeaseService, never()).release(anyString());

        releaseRun.countDown();

        assertThat(leaseReleased.await(2, TimeUnit.SECONDS)).isTrue();
        verify(dataspaceIssuerProvisioningService).provisionIssuer();
        verify(issuerSetUpLeaseService).release(NODE);
    }

    @Test
    void leaseTtlCoversTheWorstCaseRunAtAShortChannelDeadline() {
        assertLeaseTtlCoversTheWorstCaseRun(1000);
    }

    @Test
    void leaseTtlCoversTheWorstCaseRunAtTheDefaultChannelDeadline() {
        assertLeaseTtlCoversTheWorstCaseRun(DEADLINE_AFTER_MS);
    }

    /**
     * Computes the worst-case run time independently of {@code IssuerSetUpServiceImpl.leaseTtl()}: 3 RPC
     * calls (creating the participant context and the two definition calls) each wait out the channel
     * deadline; each of the 2 "can sign" probes retries up to its own fixed per-attempt deadline times the
     * maximum attempt count, waiting the retry interval between attempts.
     */
    private void assertLeaseTtlCoversTheWorstCaseRun(int deadlineAfterMs) {
        givenEveryPrerequisiteCheckPasses();
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(issuerSetUpLeaseService.tryAcquire(eq(NODE), any())).thenReturn(true);
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(deadlineAfterMs);

        issuerSetUpService.start();

        var ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(issuerSetUpLeaseService).tryAcquire(eq(NODE), ttlCaptor.capture());

        var oneProbeWorstCase = Duration.ofMillis(IssuerProvisioningRpcClient.CAN_SIGN_DEADLINE_MILLIS)
                .multipliedBy(DataspaceIssuerProvisioningServiceImpl.CAN_SIGN_MAX_ATTEMPTS)
                .plus(DataspaceIssuerProvisioningServiceImpl.CAN_SIGN_RETRY_INTERVAL
                        .multipliedBy(DataspaceIssuerProvisioningServiceImpl.CAN_SIGN_MAX_ATTEMPTS - 1L));
        var worstCase = Duration.ofMillis(deadlineAfterMs).multipliedBy(3)
                .plus(oneProbeWorstCase.multipliedBy(2));

        assertThat(ttlCaptor.getValue()).isGreaterThanOrEqualTo(worstCase);
    }

    @Test
    void releasesTheLeaseAfterAFailingRun() throws Exception {
        givenEveryPrerequisiteCheckPasses();
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty());
        when(issuerSetUpLeaseService.tryAcquire(eq(NODE), any())).thenReturn(true);
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);

        var runFailed = new CountDownLatch(1);
        doThrow(new IllegalStateException("provisioning failed")).when(dataspaceIssuerProvisioningService).provisionIssuer();
        doAnswer(invocation -> {
            runFailed.countDown();
            return null;
        }).when(issuerSetUpLeaseService).release(NODE);

        var result = issuerSetUpService.start();

        assertThat(result).isEqualTo(new IssuerSetUpResult.Accepted());
        assertThat(runFailed.await(2, TimeUnit.SECONDS)).isTrue();
        verify(issuerSetUpLeaseService).release(NODE);
    }

    /**
     * Two calls at once: the first takes the free lease and is accepted; the second sees the lease
     * already held and is refused.
     */
    @Test
    void secondConcurrentCallIsRefusedAlreadyRunningWhileTheFirstIsStillAccepted() throws Exception {
        givenEveryPrerequisiteCheckPasses();
        when(issuerSetUpLeaseService.currentHolder()).thenReturn(Optional.empty(), Optional.of(NODE));
        when(issuerSetUpLeaseService.tryAcquire(eq(NODE), any())).thenReturn(true);
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);
        var runFinished = new CountDownLatch(1);
        doAnswer(invocation -> {
            runFinished.countDown();
            return null;
        }).when(dataspaceIssuerProvisioningService).provisionIssuer();

        var firstResult = issuerSetUpService.start();
        var secondResult = issuerSetUpService.start();

        assertThat(firstResult).isEqualTo(new IssuerSetUpResult.Accepted());
        assertThat(secondResult).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_RUNNING));
        assertThat(runFinished.await(2, TimeUnit.SECONDS)).isTrue();
    }
}
