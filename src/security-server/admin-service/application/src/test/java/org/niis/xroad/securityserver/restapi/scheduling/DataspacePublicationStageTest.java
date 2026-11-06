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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apicatalog.did.Did;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantContext;
import org.niis.xroad.securityserver.restapi.service.DataspaceProvisioningService.ParticipantKind;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DidRegistrationSender;
import org.niis.xroad.securityserver.restapi.service.SystemDidRegistrationSendOutcome;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.niis.xroad.serverconf.model.PublicationState;
import org.niis.xroad.test.MutableClock;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspacePublicationStageTest {

    private static final Did SYSTEM_DID = Did.parse("did:web:ss.example.test%3A7183:xrd-ss0");
    private static final String DSP_BASE_URL = "https://ss.example.test:7181/dsp";
    private static final byte[] AUTH_CERT = {1, 2, 3};
    private static final long ROW_ID = 17L;
    private static final ParticipantContext SYSTEM_CONTEXT =
            new ParticipantContext("xrd-ss0", ParticipantKind.SYSTEM, null);

    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceDidAuthority didAuthority;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private DidRegistrationSender didRegistrationSender;
    @Mock
    private AdminServiceProperties adminServiceProperties;

    @InjectMocks
    private DataspacePublicationStage stage;

    private DsParticipantEntity systemRow;
    private MutableClock clock;
    private PublicationBackoff backoff;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
        backoff = new PublicationBackoff(clock, () -> 1.0);
        stage.setBackoff(backoff);
        systemRow = row(null, null);
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        when(readinessPredicates.registeredAuthCertBytes()).thenReturn(Optional.of(AUTH_CERT));
        when(didAuthority.dspBaseUrlIfValid()).thenReturn(Optional.of(DSP_BASE_URL));
        when(dsParticipantRepository.findActiveSystemParticipant())
                .thenAnswer(_ -> Optional.of(systemRow).filter(row -> row.getState() == ParticipantState.ACTIVE));
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenReturn(true);
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.stored(1));
    }

    private static DsParticipantEntity row(PublicationState state, String reason) {
        var row = new DsParticipantEntity();
        row.setId(ROW_ID);
        row.setDid(SYSTEM_DID.toString());
        row.setState(ParticipantState.ACTIVE);
        row.setPublicationState(state);
        row.setPublicationReason(reason);
        return row;
    }

    private void runPass() {
        stage.publish(Set.of(SYSTEM_CONTEXT), false);
    }

    private List<ILoggingEvent> logsOfPass() {
        var logger = (Logger) LoggerFactory.getLogger(DataspacePublicationStage.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            runPass();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }
        return appender.list;
    }

    private void verifySent() {
        verify(didRegistrationSender).sendSystemDidRegisterRequest(SYSTEM_DID.toString(), DSP_BASE_URL, AUTH_CERT);
    }

    private void verifyNothingSent() {
        verify(didRegistrationSender, never()).sendSystemDidRegisterRequest(any(), any(), any());
        verify(dsParticipantRepository, never()).transitionPublication(anyLong(), any(), any(), any());
    }

    @Test
    void sendsTheSystemValuesOnceAndMarksTheRowSubmitted() {
        systemRow = row(PublicationState.NOT_SENT, null);

        runPass();

        verifySent();
        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.SUBMITTED, null);
    }

    @Test
    void aRowBoundBeforePublicationStateExistedIsSentOnTheNextPass() {
        systemRow = row(null, null);

        runPass();

        verifySent();
        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.SUBMITTED, null);
    }

    @Test
    void sendsNothingWhileTheDataspaceIsNotReady() {
        when(readinessPredicates.isDataspaceReady()).thenReturn(false);

        runPass();

        verifyNothingSent();
    }

    @Test
    void sendsNothingWhileTheAuthCertRegistrationIsPending() {
        when(readinessPredicates.registeredAuthCertBytes()).thenReturn(Optional.empty());

        runPass();

        verifyNothingSent();
    }

    @Test
    void sendsNothingWhileTheRegisteredAddressIsUnknown() {
        when(didAuthority.dspBaseUrlIfValid()).thenReturn(Optional.empty());

        runPass();

        verifyNothingSent();
    }

    @Test
    void withoutANotSentSystemRowNeitherTheSignerNorGlobalconfIsAsked() {
        systemRow.setPublicationState(PublicationState.SUBMITTED);

        runPass();

        verifyNoInteractions(readinessPredicates, didAuthority, didRegistrationSender);
    }

    @Test
    void sendsNothingWhileTheSystemContextIsNotProvisioned() {
        stage.publish(Set.of(), false);

        verifyNothingSent();
    }

    @Test
    void readsTheGatesBeforeSendingAndStoresTheResultAfter() {
        runPass();

        InOrder order = inOrder(readinessPredicates, didAuthority, didRegistrationSender, dsParticipantRepository);
        order.verify(readinessPredicates).isDataspaceReady();
        order.verify(didRegistrationSender).sendSystemDidRegisterRequest(any(), any(), any());
        order.verify(dsParticipantRepository).transitionPublication(eq(ROW_ID), any(), any(), any());
    }

    @Test
    void sendsNothingWithoutAnActiveSystemRow() {
        systemRow.setState(ParticipantState.DECOMMISSIONED);

        runPass();

        verifyNothingSent();
    }

    @Test
    void sendsNothingWithoutASystemRow() {
        when(dsParticipantRepository.findActiveSystemParticipant()).thenReturn(Optional.empty());

        runPass();

        verifyNothingSent();
    }

    @ParameterizedTest
    @EnumSource(value = PublicationState.class, names = {"SUBMITTED", "PUBLISHED", "REJECTED"})
    void neverSendsAValueThatIsPastNotSent(PublicationState state) {
        systemRow = row(state, state == PublicationState.REJECTED ? "SOME_REASON" : null);

        runPass();

        verifyNothingSent();
    }

    @Test
    void aSecondPassAfterSubmittedSendsNothingMore() {
        systemRow = row(PublicationState.NOT_SENT, null);
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenAnswer(call -> {
            systemRow.setPublicationState(call.getArgument(2));
            return true;
        });

        runPass();
        runPass();

        verifySent();
        verify(dsParticipantRepository).transitionPublication(any(), any(), any(), any());
    }

    @Test
    void aFinalConflictRejectsTheValueWithItsReason() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.finalConflict("DID_TAKEN"));

        runPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.REJECTED, "DID_TAKEN");
    }

    @Test
    void aFixableConflictKeepsTheValueNotSentWithItsReason() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.fixableConflict("ADDRESS_MISMATCH"));

        var logs = logsOfPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "ADDRESS_MISMATCH");
        assertThat(logs).filteredOn(event -> event.getLevel() == Level.INFO)
                .anyMatch(event -> event.getFormattedMessage().contains("FIXABLE_CONFLICT"));
    }

    @Test
    void aRepeatedFixableConflictNamesItsBackoffClassInTheUnchangedLine() {
        systemRow = row(PublicationState.NOT_SENT, "ADDRESS_MISMATCH");
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.fixableConflict("ADDRESS_MISMATCH"));

        var logs = logsOfPass();

        assertThat(logs).filteredOn(event -> event.getLevel() == Level.DEBUG)
                .anyMatch(event -> event.getFormattedMessage().contains("FIXABLE_CONFLICT"));
    }

    @Test
    void aSendFailureKeepsTheValueNotSentAndNamesItsCause() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenThrow(new IllegalStateException("connection refused"));

        var logs = logsOfPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "SEND_FAILURE: IllegalStateException");
        assertThat(logs).anyMatch(event -> event.getLevel() == Level.WARN && event.getThrowableProxy() != null);
    }

    @Test
    void anOutdatedGlobalConfFromTheSenderKeepsTheValueNotSentAsASendFailure() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_OUTDATED).build());

        var logs = logsOfPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "SEND_FAILURE: " + ErrorCode.GLOBAL_CONF_OUTDATED.code());
        assertThat(logs).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                .anyMatch(event -> event.getLevel() == Level.DEBUG
                && event.getFormattedMessage().contains("global configuration is outdated")
                && event.getThrowableProxy() == null);
        verify(dsParticipantRepository, never()).transitionPublication(anyLong(), any(),
                eq(PublicationState.REJECTED), any());
    }

    @Test
    void repeatingTheSameFailureWritesNothing() {
        systemRow = row(PublicationState.NOT_SENT, "SEND_FAILURE: IllegalStateException");
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenThrow(new IllegalStateException("connection refused"));

        runPass();

        verifySent();
        verify(dsParticipantRepository, never()).transitionPublication(anyLong(), any(), any(), any());
    }

    @Test
    void aLostRaceChangesNothingAndDoesNotThrow() {
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenReturn(false);

        runPass();

        verifySent();
        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.SUBMITTED, null);
    }

    @Test
    void aFailingGateReadDoesNotPropagate() {
        when(readinessPredicates.isDataspaceReady()).thenThrow(new IllegalStateException("globalconf unreadable"));

        runPass();

        verifyNoInteractions(didRegistrationSender);
    }

    private void failSendsWith(RuntimeException failure) {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any())).thenThrow(failure);
    }

    private void verifySends(int count) {
        verify(didRegistrationSender, times(count)).sendSystemDidRegisterRequest(any(), any(), any());
    }

    @Test
    void afterASendFailureThePassesInsideThePauseSendNothingAndTheFirstPassAfterItSends() {
        failSendsWith(new IllegalStateException("connection refused"));

        runPass();
        clock.advance(Duration.ofSeconds(29));
        runPass();
        verifySends(1);

        clock.advance(Duration.ofSeconds(1));
        runPass();
        verifySends(2);
    }

    @Test
    void thePauseGrowsWithEachFailedSend() {
        failSendsWith(new IllegalStateException("connection refused"));

        runPass();
        clock.advance(Duration.ofSeconds(30));
        runPass();
        clock.advance(Duration.ofSeconds(59));
        runPass();
        verifySends(2);

        clock.advance(Duration.ofSeconds(1));
        runPass();
        verifySends(3);
    }

    @Test
    void aStoredResultEndsThePause() {
        failSendsWith(new IllegalStateException("connection refused"));
        runPass();
        clock.advance(Duration.ofSeconds(30));
        doReturn(SystemDidRegistrationSendOutcome.stored(1)).when(didRegistrationSender)
                .sendSystemDidRegisterRequest(any(), any(), any());
        runPass();

        runPass();

        verifySends(3);
    }

    @ParameterizedTest
    @EnumSource(value = SystemDidRegistrationSendOutcome.Status.class,
            names = {"FIXABLE_CONFLICT", "NOT_RESOLVED"})
    void aFixableConflictAndANotResolvedResultAreRetriedAfterAPause(SystemDidRegistrationSendOutcome.Status status) {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any())).thenReturn(
                status == SystemDidRegistrationSendOutcome.Status.FIXABLE_CONFLICT
                        ? SystemDidRegistrationSendOutcome.fixableConflict("ADDRESS_MISMATCH")
                        : SystemDidRegistrationSendOutcome.notResolved("DID_UNRESOLVABLE"));

        runPass();
        clock.advance(Duration.ofSeconds(29));
        runPass();
        verifySends(1);

        clock.advance(Duration.ofSeconds(1));
        runPass();
        verifySends(2);
    }

    @Test
    void aFinalConflictIsNeverRetried() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.finalConflict("DID_TAKEN"));
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenAnswer(call -> {
            systemRow.setPublicationState(call.getArgument(2));
            systemRow.setPublicationReason(call.getArgument(3));
            return true;
        });

        runPass();
        clock.advance(Duration.ofHours(1));
        runPass();

        verifySends(1);
        assertThat(systemRow.getPublicationState()).isEqualTo(PublicationState.REJECTED);
    }

    @Test
    void aSendFailurePausesTheRequestKindAndAConflictOnlyItsValue() {
        var kind = PublicationBackoff.Key.ofKind(PublicationBackoff.RequestKind.SYSTEM);
        var value = PublicationBackoff.Key.ofValue(PublicationBackoff.RequestKind.SYSTEM, SYSTEM_DID.toString());

        failSendsWith(new IllegalStateException("connection refused"));
        runPass();
        assertThat(backoff.mayAttempt(kind)).isFalse();
        assertThat(backoff.mayAttempt(value)).isTrue();

        clock.advance(Duration.ofSeconds(30));
        doReturn(SystemDidRegistrationSendOutcome.fixableConflict("ADDRESS_MISMATCH")).when(didRegistrationSender)
                .sendSystemDidRegisterRequest(any(), any(), any());
        runPass();
        assertThat(backoff.mayAttempt(kind)).isTrue();
        assertThat(backoff.mayAttempt(value)).isFalse();
    }

    @Test
    void aRestartForgetsThePauseAndSendsAtOnce() {
        failSendsWith(new IllegalStateException("connection refused"));
        runPass();
        verifySends(1);

        var restarted = new DataspacePublicationStage(readinessPredicates, didAuthority, dsParticipantRepository,
                didRegistrationSender, adminServiceProperties);
        restarted.publish(Set.of(SYSTEM_CONTEXT), false);

        verifySends(2);
    }

    @Test
    void aSendThatHangsIsCutOffAndTheValueStaysNotSentWithATimeoutReason() throws InterruptedException {
        var interrupted = new CountDownLatch(1);
        var neverReleased = new CountDownLatch(1);
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any())).thenAnswer(_ -> {
            try {
                neverReleased.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return SystemDidRegistrationSendOutcome.stored(1);
        });
        stage.setSendTimeout(Duration.ofMillis(200));

        var started = System.nanoTime();
        runPass();
        var elapsed = Duration.ofNanos(System.nanoTime() - started);

        assertThat(elapsed).isLessThan(Duration.ofSeconds(10));
        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "SEND_FAILURE: timeout");
        assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(backoff.mayAttempt(PublicationBackoff.Key.ofKind(PublicationBackoff.RequestKind.SYSTEM))).isFalse();
    }

    @Test
    void anErrorFromTheSenderEndsAsATimeoutBecauseOnlyExceptionsCompleteTheSend() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenThrow(new AssertionError("sender broke"));
        stage.setSendTimeout(Duration.ofMillis(200));

        runPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "SEND_FAILURE: timeout");
        assertThat(backoff.mayAttempt(PublicationBackoff.Key.ofKind(PublicationBackoff.RequestKind.SYSTEM))).isFalse();
    }

    @Test
    void anInterruptOfThePassWhileItWaitsForTheSendIsNotAFailure() throws InterruptedException {
        var sending = new CountDownLatch(1);
        var senderInterrupted = new CountDownLatch(1);
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any())).thenAnswer(_ -> {
            sending.countDown();
            try {
                new CountDownLatch(1).await(30, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                senderInterrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return SystemDidRegistrationSendOutcome.stored(1);
        });
        stage.setSendTimeout(Duration.ofSeconds(30));
        var kindKey = PublicationBackoff.Key.ofKind(PublicationBackoff.RequestKind.SYSTEM);
        var valueKey = PublicationBackoff.Key.ofValue(PublicationBackoff.RequestKind.SYSTEM, SYSTEM_DID.toString());
        var memberKey = PublicationBackoff.Key.ofKind(PublicationBackoff.RequestKind.MEMBER);
        backoff.recordFailure(kindKey);
        backoff.recordFailure(valueKey);
        clock.advance(Duration.ofSeconds(30));
        backoff.recordFailure(memberKey);
        var logger = (Logger) LoggerFactory.getLogger(DataspacePublicationStage.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        var flagAfterPass = new AtomicBoolean();
        var passDone = new CountDownLatch(1);
        var passThread = new Thread(() -> {
            runPass();
            flagAfterPass.set(Thread.currentThread().isInterrupted());
            passDone.countDown();
        });
        try {
            passThread.start();
            assertThat(sending.await(10, TimeUnit.SECONDS)).isTrue();

            passThread.interrupt();

            assertThat(passDone.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }
        assertThat(flagAfterPass.get()).isTrue();
        assertThat(senderInterrupted.await(10, TimeUnit.SECONDS)).isTrue();
        verify(dsParticipantRepository, never()).transitionPublication(anyLong(), any(), any(), any());
        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));

        assertThat(backoff.mayAttempt(kindKey)).isTrue();
        assertThat(backoff.mayAttempt(valueKey)).isTrue();
        assertThat(backoff.mayAttempt(memberKey)).isFalse();
        backoff.recordFailure(kindKey);
        backoff.recordFailure(valueKey);
        clock.advance(Duration.ofSeconds(31));
        assertThat(backoff.mayAttempt(kindKey)).isFalse();
        assertThat(backoff.mayAttempt(valueKey)).isFalse();
        clock.advance(Duration.ofSeconds(29));
        assertThat(backoff.mayAttempt(kindKey)).isTrue();
        assertThat(backoff.mayAttempt(valueKey)).isTrue();
    }
}
