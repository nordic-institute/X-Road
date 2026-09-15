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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.ServerClientRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProvisioningServiceImpl.ISSUER_PARTICIPANT_ID;

@ExtendWith(MockitoExtension.class)
class MemberCredentialRevocationServiceTest {

    private static final String INSTANCE = "TEST";
    private static final String MEMBER_CLASS = "CLASS";
    private static final String MEMBER_CODE = "MEMBER";
    private static final String SERVER_CODE = "SERVER";
    private static final String SS_ADDRESS = "ss1.example.test";
    private static final long CUTOFF = 9_000L;

    private final ClientId memberId = ClientId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);
    private final SecurityServerId securityServerId = SecurityServerId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE, SERVER_CODE);
    private final XRoadMemberEntity owner = new XRoadMemberEntity("owner",
            ClientId.Conf.create(INSTANCE, MEMBER_CLASS, "OWNER"), new MemberClassEntity(MEMBER_CLASS, "description"));
    private final SecurityServerEntity server = new SecurityServerEntity(owner, SERVER_CODE);

    @Mock
    private SecurityServerRepository securityServers;
    @Mock
    private XRoadMemberRepository members;
    @Mock
    private ServerClientRepository serverClients;
    @Mock
    private IssuerProvisioningRpcClient rpcClient;
    @Mock
    private XRoadMemberEntity member;
    @Mock
    private SubsystemEntity subsystem;

    private MemberCredentialRevocationService service;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(MemberCredentialRevocationService.class);

    @BeforeEach
    void setUp() {
        server.setAddress(SS_ADDRESS);
        service = new MemberCredentialRevocationService(securityServers, members, serverClients, rpcClient);

        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDownLogging() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(null);
    }

    @Test
    @DisplayName("Revokes when the member has no remaining client rows and is not the server's owner")
    void revokesWhenNoRemainingClientRows() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(member.getSubsystems()).thenReturn(Set.of());
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(0L);
        when(rpcClient.revokeCredential(eq(ISSUER_PARTICIPANT_ID), anyString(), eq(CUTOFF))).thenReturn(3);

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        ArgumentCaptor<String> didCaptor = ArgumentCaptor.forClass(String.class);
        verify(rpcClient).revokeCredential(eq(ISSUER_PARTICIPANT_ID), didCaptor.capture(), eq(CUTOFF));
        assertThat(didCaptor.getValue()).isEqualTo(ParticipantIdentifierScheme.memberDid(memberId, SS_ADDRESS));
    }

    @Test
    @DisplayName("Does not revoke when the member itself still has a registered client row")
    void doesNotRevokeWhenMemberHasRemainingClientRow() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(1L);

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        verifyNoInteractions(rpcClient);
    }

    @Test
    @DisplayName("Does not revoke when only a subsystem of the member remains registered")
    void doesNotRevokeWhenSubsystemHasRemainingClientRow() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(member.getSubsystems()).thenReturn(Set.of(subsystem));
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(1L);

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        verifyNoInteractions(rpcClient);
    }

    @Test
    @DisplayName("Never revokes the server's current owner")
    void doesNotRevokeServerOwner() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, owner.getIdentifier(), CUTOFF));

        verifyNoInteractions(members, serverClients, rpcClient);
    }

    @Test
    @DisplayName("Does nothing when the security server can no longer be found")
    void doesNothingWhenServerNotFound() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.empty());

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        verifyNoInteractions(members, serverClients, rpcClient);
    }

    @Test
    @DisplayName("Does nothing when the member can no longer be found")
    void doesNothingWhenMemberNotFound() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.empty());

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        verifyNoInteractions(serverClients, rpcClient);
    }

    @Test
    @DisplayName("Swallows and never propagates an issuer revocation failure")
    void swallowsRevocationFailure() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(member.getSubsystems()).thenReturn(Set.of());
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(0L);
        when(rpcClient.revokeCredential(any(), any(), anyLong()))
                .thenThrow(new RuntimeException("issuer unreachable"));

        assertThatCode(() -> service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Logs a successful revocation of zero credentials without throwing")
    void completesNormallyOnZeroRevokedCount() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(member.getSubsystems()).thenReturn(Set.of());
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(0L);
        when(rpcClient.revokeCredential(any(), any(), anyLong())).thenReturn(0);

        assertThatCode(() -> service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF)))
                .doesNotThrowAnyException();

        verify(rpcClient).revokeCredential(eq(ISSUER_PARTICIPANT_ID), anyString(), eq(CUTOFF));
    }

    @Test
    @DisplayName("Logs the revocation attempt with the derived holder DID and issued-before cutoff")
    void logsRevocationAttemptWithHolderDidAndCutoff() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(member.getSubsystems()).thenReturn(Set.of());
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(0L);
        when(rpcClient.revokeCredential(eq(ISSUER_PARTICIPANT_ID), anyString(), eq(CUTOFF))).thenReturn(1);

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        var expectedDid = ParticipantIdentifierScheme.memberDid(memberId, SS_ADDRESS);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getLevel, ILoggingEvent::getFormattedMessage)
                .contains(tuple(Level.INFO,
                        "Dataspace credential revocation: revoking credentials for holder %s issued before %d"
                                .formatted(expectedDid, CUTOFF)));
    }

    @Test
    @DisplayName("Phrases a zero-match outcome to name the searched holder DID")
    void logsZeroMatchOutcomeNamingTheSearchedHolderDid() {
        when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
        when(members.findMember(memberId)).thenReturn(Optional.of(member));
        when(member.getSubsystems()).thenReturn(Set.of());
        when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(0L);
        when(rpcClient.revokeCredential(eq(ISSUER_PARTICIPANT_ID), anyString(), eq(CUTOFF))).thenReturn(0);

        service.onServerClientRemoved(new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF));

        var expectedDid = ParticipantIdentifierScheme.memberDid(memberId, SS_ADDRESS);
        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains("no credentials matched holder " + expectedDid));
    }

    @Nested
    @DisplayName("@TransactionalEventListener(phase = AFTER_COMMIT) wiring")
    class TransactionalWiring {

        @AfterEach
        void tearDown() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }

        @Test
        @DisplayName("Dispatches off the caller's thread via @Async")
        void onServerClientRemovedIsAsync() throws NoSuchMethodException {
            Method listener = MemberCredentialRevocationService.class.getMethod("onServerClientRemoved", ServerClientRemovedEvent.class);

            assertThat(listener.isAnnotationPresent(Async.class)).isTrue();
        }

        @Test
        @DisplayName("Fires the revocation only once the publishing transaction commits")
        void firesOnlyAfterCommit() {
            when(securityServers.findBy(securityServerId)).thenReturn(Optional.of(server));
            when(members.findMember(memberId)).thenReturn(Optional.of(member));
            when(member.getSubsystems()).thenReturn(Set.of());
            when(serverClients.countBySecurityServerAndSecurityServerClientIn(eq(server), any())).thenReturn(0L);

            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(TransactionalEventListenerFactory.class, TransactionalEventListenerFactory::new);
                context.registerBean(AsyncAnnotationBeanPostProcessor.class, () -> {
                    var processor = new AsyncAnnotationBeanPostProcessor();
                    processor.setExecutor(new SyncTaskExecutor());
                    return processor;
                });
                context.registerBean(MemberCredentialRevocationService.class,
                        () -> new MemberCredentialRevocationService(securityServers, members, serverClients, rpcClient));
                context.refresh();

                var event = new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF);

                TransactionSynchronizationManager.setActualTransactionActive(true);
                TransactionSynchronizationManager.initSynchronization();

                context.publishEvent(event);
                verifyNoInteractions(rpcClient);

                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCommit();
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
                }
            }

            verify(rpcClient).revokeCredential(eq(ISSUER_PARTICIPANT_ID), anyString(), eq(CUTOFF));
        }

        @Test
        @DisplayName("Never fires the revocation when the publishing transaction rolls back")
        void neverFiresOnRollback() {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(TransactionalEventListenerFactory.class, TransactionalEventListenerFactory::new);
                context.registerBean(AsyncAnnotationBeanPostProcessor.class, () -> {
                    var processor = new AsyncAnnotationBeanPostProcessor();
                    processor.setExecutor(new SyncTaskExecutor());
                    return processor;
                });
                context.registerBean(MemberCredentialRevocationService.class,
                        () -> new MemberCredentialRevocationService(securityServers, members, serverClients, rpcClient));
                context.refresh();

                var event = new ServerClientRemovedEvent(securityServerId, memberId, CUTOFF);

                TransactionSynchronizationManager.setActualTransactionActive(true);
                TransactionSynchronizationManager.initSynchronization();

                context.publishEvent(event);

                for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                }
            }

            verifyNoInteractions(securityServers, members, serverClients, rpcClient);
        }
    }
}
