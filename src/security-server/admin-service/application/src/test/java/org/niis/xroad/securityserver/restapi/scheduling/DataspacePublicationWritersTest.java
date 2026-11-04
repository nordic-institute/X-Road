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
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.apicatalog.did.Did;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
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
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the reconciler's publication stage and the globalconf check against one SYSTEM row, with the row
 * transitions behaving like the real conditional update: a transition applies only if the row is still in the
 * expected state.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspacePublicationWritersTest {

    private static final Did SYSTEM_DID = Did.parse("did:web:ss.example.test%3A7183:xrd-ss0");
    private static final String DSP_BASE_URL = "https://ss.example.test:7181/dsp";
    private static final SecurityServerId SERVER_ID =
            SecurityServerId.Conf.create(ClientId.Conf.create("DEV", "COM", "222"), "SS0");
    private static final ServerSystemValues CARRIED = new ServerSystemValues(SYSTEM_DID.toString(), DSP_BASE_URL);
    private static final ServerSystemValues OTHER =
            new ServerSystemValues(SYSTEM_DID.toString(), "https://old.example.test:7181/dsp");
    private static final ParticipantContext SYSTEM_CONTEXT = new ParticipantContext("xrd-ss0", ParticipantKind.SYSTEM, null);
    private static final long ROW_ID = 17L;

    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceDidAuthority didAuthority;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private DidRegistrationSender didRegistrationSender;
    @Mock
    private GlobalConfProvider globalConfProvider;

    private DataspacePublicationStage stage;
    private DataspacePublicationGlobalConfCheck check;
    private DsParticipantEntity row;

    @BeforeEach
    void setUp() {
        row = new DsParticipantEntity();
        row.setId(ROW_ID);
        row.setDid(SYSTEM_DID.toString());
        row.setState(ParticipantState.ACTIVE);
        row.setPublicationState(PublicationState.NOT_SENT);

        when(readinessPredicates.isDataspaceReady()).thenReturn(true);
        when(readinessPredicates.hasRegisteredAuthCert()).thenReturn(true);
        when(readinessPredicates.registeredAuthCertBytes()).thenReturn(Optional.of(new byte[] {1, 2, 3}));
        when(didAuthority.dspBaseUrlIfValid()).thenReturn(Optional.of(DSP_BASE_URL));
        when(globalConfProvider.getServerSystemValues(SERVER_ID)).thenReturn(Optional.empty());
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any()))
                .thenReturn(SystemDidRegistrationSendOutcome.stored(1));
        when(dsParticipantRepository.findActiveSystemParticipant()).thenAnswer(_ -> Optional.of(snapshot()));
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenAnswer(call -> {
            PublicationState expected = call.getArgument(1);
            if (row.getPublicationState() != expected) {
                return false;
            }
            row.setPublicationState(call.getArgument(2));
            row.setPublicationReason(call.getArgument(3));
            return true;
        });

        stage = new DataspacePublicationStage(readinessPredicates, didAuthority, dsParticipantRepository,
                didRegistrationSender);
        check = new DataspacePublicationGlobalConfCheck(dsParticipantRepository, globalConfProvider,
                readinessPredicates, didAuthority, mock(PlatformTransactionManager.class));
    }

    private DsParticipantEntity snapshot() {
        var copy = new DsParticipantEntity();
        copy.setId(row.getId());
        copy.setDid(row.getDid());
        copy.setState(row.getState());
        copy.setPublicationState(row.getPublicationState());
        copy.setPublicationReason(row.getPublicationReason());
        return copy;
    }

    private void reconcilerPass() {
        stage.publish(Set.of(SYSTEM_CONTEXT));
    }

    private void globalconfCheck() {
        check.check(SERVER_ID);
    }

    private void carry(ServerSystemValues values) {
        when(globalConfProvider.getServerSystemValues(SERVER_ID)).thenReturn(Optional.ofNullable(values));
    }

    @Test
    void theReconcilerSendsAndTheCheckPublishes() {
        reconcilerPass();
        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);

        globalconfCheck();
        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);

        carry(CARRIED);
        globalconfCheck();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        verify(didRegistrationSender, times(1)).sendSystemDidRegisterRequest(any(), any(), any());
    }

    @Test
    void theCheckResetsAndTheReconcilerSendsAgain() {
        row.setPublicationState(PublicationState.PUBLISHED);

        globalconfCheck();
        assertThat(row.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);

        reconcilerPass();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        verify(didRegistrationSender, times(1)).sendSystemDidRegisterRequest(any(), any(), any());
    }

    @Test
    void aReconcilerPassSendsAgainAfterTheResetOnlyOnce() {
        row.setPublicationState(PublicationState.PUBLISHED);
        globalconfCheck();

        reconcilerPass();
        reconcilerPass();
        globalconfCheck();

        verify(didRegistrationSender, times(1)).sendSystemDidRegisterRequest(any(), any(), any());
        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @Test
    void aNotSentValueIsSentWhileGlobalconfCarriesADifferentValueAndTheCheckThenChangesNothing() {
        carry(OTHER);

        reconcilerPass();
        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        globalconfCheck();
        globalconfCheck();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
        verify(didRegistrationSender, times(1)).sendSystemDidRegisterRequest(any(), any(), any());
    }

    @Test
    void aPublishedValueWithADifferentValueInGlobalconfIsNeverSentAgain() {
        row.setPublicationState(PublicationState.PUBLISHED);
        carry(OTHER);

        globalconfCheck();
        globalconfCheck();
        globalconfCheck();
        reconcilerPass();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        verify(didRegistrationSender, times(0)).sendSystemDidRegisterRequest(any(), any(), any());
    }

    @Test
    void theCheckLosesToAWriterThatMovedTheRowFirstAndChangesNothing() {
        row.setPublicationState(PublicationState.PUBLISHED);
        when(readinessPredicates.hasRegisteredAuthCert()).thenAnswer(_ -> {
            row.setPublicationState(PublicationState.SUBMITTED);
            return true;
        });

        globalconfCheck();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.SUBMITTED);
    }

    @Test
    void theReconcilerLosesToAWriterThatMovedTheRowFirstAndChangesNothing() {
        when(didRegistrationSender.sendSystemDidRegisterRequest(any(), any(), any())).thenAnswer(_ -> {
            row.setPublicationState(PublicationState.PUBLISHED);
            return SystemDidRegistrationSendOutcome.stored(1);
        });

        reconcilerPass();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
    }

    @Test
    void aSecondCheckWithUnchangedGlobalconfChangesNothing() {
        row.setPublicationState(PublicationState.SUBMITTED);
        carry(CARRIED);
        globalconfCheck();
        var afterFirst = row.getPublicationState();

        globalconfCheck();

        assertThat(afterFirst).isEqualTo(PublicationState.PUBLISHED);
        assertThat(row.getPublicationState()).isEqualTo(afterFirst);
        verify(dsParticipantRepository, times(1)).transitionPublication(anyLong(), any(), any(), any());
    }

    @Test
    void aResendThatFailsIsTriedAgainByTheNextCheck() {
        row.setPublicationState(PublicationState.PUBLISHED);
        doThrow(new IllegalStateException("database unavailable")).when(dsParticipantRepository)
                .transitionPublication(anyLong(), any(), any(), any());
        globalconfCheck();
        assertThat(row.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        doAnswer(call -> {
            row.setPublicationState(call.getArgument(2));
            return true;
        }).when(dsParticipantRepository).transitionPublication(anyLong(), any(), any(), any());

        globalconfCheck();

        assertThat(row.getPublicationState()).isEqualTo(PublicationState.NOT_SENT);
    }

    @Test
    void theDspBaseUrlIsNotBuiltForNotSentRejectedAndPublishedRows() {
        for (var state : new PublicationState[] {PublicationState.NOT_SENT, PublicationState.REJECTED,
                PublicationState.PUBLISHED}) {
            row.setPublicationState(state);
            carry(CARRIED);
            globalconfCheck();
            carry(null);
            globalconfCheck();
        }

        verify(didAuthority, never()).dspBaseUrlIfValid();
    }
}
