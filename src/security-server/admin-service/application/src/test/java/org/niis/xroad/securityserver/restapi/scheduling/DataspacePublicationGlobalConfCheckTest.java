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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReadinessPredicates;
import org.niis.xroad.securityserver.restapi.service.DifferenceEvaluator;
import org.niis.xroad.securityserver.restapi.service.DifferenceSignals;
import org.niis.xroad.securityserver.restapi.service.GlobalConfComparison;
import org.niis.xroad.securityserver.restapi.service.SystemValuesComparison;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.ParticipantType;
import org.niis.xroad.serverconf.model.PublicationState;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataspacePublicationGlobalConfCheckTest {

    private static final SecurityServerId SERVER_ID =
            SecurityServerId.Conf.create("DEV", "COM", "222", "SS1");
    private static final OwnAddress.Registered ADDRESS = new OwnAddress.Registered(SERVER_ID, "ss1.example.org");

    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private ClientRepository clientRepository;
    @Mock
    private DifferenceEvaluator differenceEvaluator;
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
    @Mock
    private DifferenceSignals signals;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DataspacePublicationGlobalConfCheck.class);

    private DataspacePublicationGlobalConfCheck check;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        var config = XRoadConfigBuilder.create().register(AdminServiceConfigKeys.instance()).build();
        check = new DataspacePublicationGlobalConfCheck(dsParticipantRepository, clientRepository,
                differenceEvaluator, differenceLog, readinessPredicates, didAuthority, ownSecurityServerResolver,
                new AdminServiceProperties(config), transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(didAuthority.dspBaseUrlIfValid()).thenReturn(Optional.empty());
        when(ownSecurityServerResolver.address(SERVER_ID)).thenReturn(ADDRESS);
        when(differenceEvaluator.signals(SERVER_ID, ADDRESS)).thenReturn(signals);
        when(signals.compareSystemValues(any(), any()))
                .thenReturn(new SystemValuesComparison(GlobalConfComparison.EQUAL, GlobalConfComparison.EQUAL));
        when(signals.compareMemberDid(any())).thenReturn(GlobalConfComparison.EQUAL);
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
    void theOwnAddressIsResolvedAndTheSignalsAreBuiltOncePerCheckWhateverTheNumberOfRows() {
        check.check(SERVER_ID);

        verify(ownSecurityServerResolver, times(1)).address(SERVER_ID);
        verify(differenceEvaluator, times(1)).signals(SERVER_ID, ADDRESS);
        verify(signals, times(1)).compareSystemValues(any(), any());
        verify(signals, times(2)).compareMemberDid(any());
        verify(dsParticipantRepository, times(3)).transitionPublication(anyLong(), any(), any(), any());
    }

    @Test
    void aThrowingResolverIsLoggedAtErrorAndTouchesNoRepository() {
        when(ownSecurityServerResolver.address(SERVER_ID)).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> check.check(SERVER_ID)).doesNotThrowAnyException();

        var errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getFormattedMessage()).contains("own address lookup and signal setup");
        verifyNoInteractions(dsParticipantRepository, clientRepository, differenceEvaluator, differenceLog);
    }

    @Test
    void throwingSignalsAreLoggedAtErrorAndTouchNoRepositoryEvaluationOrLog() {
        when(differenceEvaluator.signals(SERVER_ID, ADDRESS)).thenThrow(new IllegalStateException("globalconf gone"));

        assertThatCode(() -> check.check(SERVER_ID)).doesNotThrowAnyException();

        var errors = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().getFormattedMessage()).contains("signal setup");
        verify(differenceEvaluator, times(1)).signals(SERVER_ID, ADDRESS);
        verifyNoMoreInteractions(differenceEvaluator);
        verifyNoInteractions(dsParticipantRepository, clientRepository, differenceLog);
    }

    @Test
    void theNextCheckAfterAFailedLookupResolvesTheAddressAgain() {
        when(ownSecurityServerResolver.address(SERVER_ID)).thenThrow(new IllegalStateException("database down"))
                .thenReturn(ADDRESS);

        check.check(SERVER_ID);
        check.check(SERVER_ID);

        verify(ownSecurityServerResolver, times(2)).address(SERVER_ID);
        verify(differenceEvaluator, times(1)).signals(SERVER_ID, ADDRESS);
        verify(dsParticipantRepository, times(3)).transitionPublication(anyLong(), any(), any(), any());
    }

    private static DsParticipantEntity row(long id, ParticipantType type) {
        var row = new DsParticipantEntity();
        row.setId(id);
        row.setParticipantType(type);
        row.setPublicationState(PublicationState.SUBMITTED);
        if (type == ParticipantType.MEMBER) {
            row.setMemberIdentifier(ClientIdEntityFactory.create(ClientId.Conf.create("DEV", "COM", "M" + id)));
        }
        return row;
    }
}
