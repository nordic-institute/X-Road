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
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private DataspacePublicationStage stage;

    private DsParticipantEntity systemRow;

    @BeforeEach
    void setUp() {
        systemRow = row(null, null);
        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        when(readinessPredicates.registeredAuthCertBytes()).thenReturn(Optional.of(AUTH_CERT));
        when(didAuthority.dspBaseUrlIfValid()).thenReturn(Optional.of(DSP_BASE_URL));
        when(dsParticipantRepository.findSystemParticipant()).thenAnswer(_ -> Optional.of(systemRow));
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
        stage.publish(Set.of(SYSTEM_CONTEXT));
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
        stage.publish(Set.of());

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
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());

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
    void aSendFailureKeepsTheValueNotSentWithAConstantReason() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenThrow(new IllegalStateException("connection refused"));

        var logs = logsOfPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "SEND_FAILURE");
        assertThat(logs).anyMatch(event -> event.getLevel() == Level.WARN && event.getThrowableProxy() != null);
    }

    @Test
    void anOutdatedGlobalConfFromTheSenderKeepsTheValueNotSentAsASendFailure() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.GLOBAL_CONF_OUTDATED).build());

        var logs = logsOfPass();

        verify(dsParticipantRepository).transitionPublication(ROW_ID, PublicationState.NOT_SENT,
                PublicationState.NOT_SENT, "SEND_FAILURE");
        assertThat(logs).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
        assertThat(logs).anyMatch(event -> event.getLevel() == Level.DEBUG
                && event.getFormattedMessage().contains("global configuration is outdated")
                && event.getThrowableProxy() == null);
        verify(dsParticipantRepository, never()).transitionPublication(anyLong(), any(),
                eq(PublicationState.REJECTED), any());
    }

    @Test
    void repeatingTheSameFailureWritesNothing() {
        systemRow = row(PublicationState.NOT_SENT, "SEND_FAILURE");
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
}
