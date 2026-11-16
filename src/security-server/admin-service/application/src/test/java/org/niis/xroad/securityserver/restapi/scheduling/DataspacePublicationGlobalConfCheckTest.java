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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals;
import org.niis.xroad.securityserver.restapi.service.HostStatus;
import org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.memberDid;
import static org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils.systemDid;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspacePublicationGlobalConfCheckTest {

    private static final SecurityServerId SERVER_ID =
            SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
    private static final OwnAddress.Registered ADDRESS = new OwnAddress.Registered(SERVER_ID, "ss1.example.org");
    private static final String SYSTEM_DID = systemDid("ss1.example.org:7183");
    private static final String DSP_BASE_URL = "https://ss1.example.org:7181/dsp";

    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private DataspaceDifferenceLog differenceLog;
    @Mock
    private DataspaceReadinessPredicates readinessPredicates;
    @Mock
    private DataspaceDidAuthority didAuthority;
    @Mock
    private OwnSecurityServerResolver ownSecurityServerResolver;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DataspacePublicationGlobalConfCheck.class);

    private DataspacePublicationGlobalConfCheck check;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        var config = XRoadConfigBuilder.create().register(AdminServiceConfigKeys.instance()).build();
        check = PublicationCheckTestFactory.create(dsParticipantRepository, clientRepository,
                globalConfProvider, differenceLog, readinessPredicates, didAuthority, ownSecurityServerResolver,
                new AdminServiceProperties(config), transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(ownSecurityServerResolver.address(SERVER_ID)).thenReturn(ADDRESS);
        when(didAuthority.dspBaseUrlIfValid(ADDRESS)).thenReturn(Optional.of(DSP_BASE_URL));
        when(globalConfProvider.getServerSystemValues(SERVER_ID))
                .thenReturn(Optional.of(new ServerSystemValues(SYSTEM_DID, DSP_BASE_URL)));
        when(globalConfProvider.getMemberDid(any(), any())).thenAnswer(
                invocation -> Optional.of(memberDidOfTheOwnHost((ClientId) invocation.getArgument(0))));
        when(dsParticipantRepository.findActiveSystemParticipant())
                .thenReturn(Optional.of(row(1, ParticipantType.SYSTEM)));
        when(dsParticipantRepository.findActiveMembers(any())).thenReturn(List.of(row(2, ParticipantType.MEMBER),
                row(3, ParticipantType.MEMBER)));
        when(dsParticipantRepository.transitionPublication(anyLong(), any(), any(), any())).thenReturn(true);
    }

    @AfterEach
    void releaseLog() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    @Test
    void everySubmittedRowThatGlobalconfCarriesUnchangedIsPublished() {
        check.check(SERVER_ID);

        verify(dsParticipantRepository).transitionPublication(1L, PublicationState.SUBMITTED, PublicationState.PUBLISHED, null);
        verify(dsParticipantRepository).transitionPublication(2L, PublicationState.SUBMITTED, PublicationState.PUBLISHED, null);
        verify(dsParticipantRepository).transitionPublication(3L, PublicationState.SUBMITTED, PublicationState.PUBLISHED, null);
    }

    @Test
    void theLocalClientsAreNotLoadedWhileGlobalconfCarriesEverySentMemberRow() {
        check.check(SERVER_ID);

        verify(clientRepository, never()).getAllLocalClients();
    }

    @Test
    void theEvaluatedDifferencesAreHandedToTheDifferenceLog() {
        var differingMember = ClientId.Conf.create("DEV", "COM", "M3");
        doAnswer(invocation -> Optional.of(
                invocation.getArgument(0, ClientId.class).asEncodedId().equals(differingMember.asEncodedId())
                        ? memberDid(differingMember, "ss1.example.org:8443")
                        : memberDidOfTheOwnHost(invocation.getArgument(0))))
                .when(globalConfProvider).getMemberDid(any(), any());

        check.check(SERVER_ID);

        verify(dsParticipantRepository).findActiveMembers(EnumSet.allOf(PublicationState.class));
        var differences = ArgumentCaptor.forClass(DifferenceSignals.Differences.class);
        verify(differenceLog).record(differences.capture());
        assertThat(differences.getValue().hostStatus()).isEqualTo(HostStatus.OK);
        assertThat(differences.getValue().memberMismatches()).extracting(ClientId::asEncodedId)
                .containsExactly(differingMember.asEncodedId());
        assertThat(differences.getValue().systemMismatch().systemDid()).isFalse();
        assertThat(differences.getValue().systemMismatch().dspBaseUrl()).isFalse();
    }

    @Test
    void anAddressThatIsNotRegisteredReadsNoRowsForTheDifferencesAndRecordsUnknown() {
        when(ownSecurityServerResolver.address(SERVER_ID)).thenReturn(new OwnAddress.NotRegistered(SERVER_ID));

        check.check(SERVER_ID);

        verify(dsParticipantRepository, never()).findActiveMembers(EnumSet.allOf(PublicationState.class));
        verify(differenceLog).record(DifferenceSignals.Differences.UNKNOWN);
    }

    @ParameterizedTest
    @MethodSource("failingSetups")
    void aFailingSetupIsLoggedOnceAtErrorAndTouchesNoRepository(FailingSetup failingSetup) {
        switch (failingSetup) {
            case ADDRESS_LOOKUP -> when(ownSecurityServerResolver.address(SERVER_ID))
                    .thenThrow(new IllegalStateException("database down"));
            case SIGNAL_SETUP -> when(didAuthority.dspBaseUrlIfValid(ADDRESS))
                    .thenThrow(new IllegalStateException("authority gone"));
            default -> throw new IllegalArgumentException(failingSetup.name());
        }

        assertThatCode(() -> check.check(SERVER_ID)).doesNotThrowAnyException();

        assertThat(appender.list).filteredOn(event -> event.getLevel() == Level.ERROR).hasSize(1);
        verifyNoInteractions(dsParticipantRepository, clientRepository, differenceLog);
    }

    private static Stream<Arguments> failingSetups() {
        return Stream.of(
                argumentSet("the own address lookup throws", FailingSetup.ADDRESS_LOOKUP),
                argumentSet("the signal setup throws", FailingSetup.SIGNAL_SETUP));
    }

    private enum FailingSetup {
        ADDRESS_LOOKUP,
        SIGNAL_SETUP
    }

    private static String memberDidOfTheOwnHost(ClientId member) {
        return memberDid(member, "ss1.example.org:7183");
    }

    private static DsParticipantEntity row(long id, ParticipantType type) {
        var row = type == ParticipantType.MEMBER
                ? ParticipantRowTestUtils.memberEntity(ClientId.Conf.create("DEV", "COM", "M" + id), null,
                        PublicationState.SUBMITTED)
                : ParticipantRowTestUtils.systemEntity(SYSTEM_DID, PublicationState.SUBMITTED);
        row.setId(id);
        if (type == ParticipantType.MEMBER) {
            row.setDid(memberDidOfTheOwnHost(row.getMemberIdentifier()));
        }
        return row;
    }
}
