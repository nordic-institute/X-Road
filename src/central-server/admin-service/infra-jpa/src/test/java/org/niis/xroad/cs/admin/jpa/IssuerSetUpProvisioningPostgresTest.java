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
package org.niis.xroad.cs.admin.jpa;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.CanSignCause;
import org.niis.xroad.cs.admin.api.domain.CanSignResult;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.IssuerSetUpResult;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerDidService;
import org.niis.xroad.cs.admin.api.service.IssuerHostService;
import org.niis.xroad.cs.admin.api.service.IssuerSetUpLeaseService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerDidServiceImpl;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProperties;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProvisioningServiceImpl;
import org.niis.xroad.cs.admin.core.dataspace.IssuerProvisioningRpcChannelProperties;
import org.niis.xroad.cs.admin.core.dataspace.IssuerProvisioningRpcClient;
import org.niis.xroad.cs.admin.core.dataspace.IssuerSetUpLeaseServiceImpl;
import org.niis.xroad.cs.admin.core.dataspace.IssuerSetUpServiceImpl;
import org.niis.xroad.cs.admin.core.entity.IssuerDidEntity;
import org.niis.xroad.cs.admin.core.entity.IssuerSetUpLeaseEntity;
import org.niis.xroad.cs.admin.core.repository.IssuerDidRepository;
import org.niis.xroad.cs.admin.core.repository.IssuerSetUpLeaseRepository;
import org.niis.xroad.cs.admin.core.service.IssuerHostServiceImpl;
import org.niis.xroad.cs.admin.core.validation.DnsNameValidator;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Runs the guarded set-up sequence ({@link DataspaceIssuerProvisioningServiceImpl}, {@link IssuerSetUpServiceImpl})
 * against the real database lease and binding record, with the Issuer Service itself stood in by a Mockito test
 * double at {@link IssuerProvisioningRpcClient}. Prior art: {@code IssuerSetUpLeasePostgresTest},
 * {@code IssuerDidPostgresTest}.
 *
 * <p>{@link DataspaceIssuerDidServiceImpl}, {@link IssuerSetUpLeaseServiceImpl} and {@link IssuerHostServiceImpl}
 * are each class-level {@code @Transactional}, so they are wired as Spring beans (see {@link Config}) on top of
 * the real, Spring-managed {@link IssuerDidRepository} and {@link IssuerSetUpLeaseRepository}: only a Spring AOP
 * proxy applies that annotation. {@link DataspaceIssuerProvisioningServiceImpl} and {@link IssuerSetUpServiceImpl}
 * carry no such annotation, so each test builds its own with plain {@code new}.
 */
@SpringBootTest(
        classes = {PostgresTestConfiguration.class, IssuerSetUpProvisioningPostgresTest.Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
                "spring.jpa.properties.hibernate.id.db_structure_naming_strategy=single",
                "spring.jpa.properties.hibernate.dialect=ee.ria.xroad.common.db.Postgres10FixedImplicitSequenceDialect",
                "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                        + "org.niis.xroad.cs.admin.jpa.StatementRecorder",
                "spring.liquibase.enabled=false"})
class IssuerSetUpProvisioningPostgresTest extends AbstractCentralServerPostgresTest {

    private static final int GENERAL_NAME_DNS = 2;
    private static final String HOST = "issuer.example.test";
    private static final int DID_PORT = 7183;
    private static final int ISSUANCE_PORT = 7185;
    private static final int STATUSLIST_PORT = 7187;
    private static final String SCHEMA_URL = "https://example.test/schema/XRoadMembershipCredential.json";
    private static final String EXPECTED_DID = "did:web:" + HOST + "%3A" + DID_PORT + ":issuer";
    private static final String ISSUANCE_URL = "https://" + HOST + ":" + ISSUANCE_PORT + "/api/issuance/v1/participants/issuer";
    private static final String STATUSLIST_BASE_URL = "https://" + HOST + ":" + STATUSLIST_PORT + "/statuslist";
    private static final int DEADLINE_AFTER_MS = 60_000;
    private static final long WAIT_SECONDS = 15;

    @Autowired
    private DataspaceIssuerDidService dataspaceIssuerDidService;
    @Autowired
    private IssuerSetUpLeaseService issuerSetUpLeaseService;
    @Autowired
    private IssuerHostService issuerHostService;
    @Autowired
    private SystemParameterService systemParameterService;
    @Autowired
    private DsTlsCertificateService dsTlsCertificateService;
    @Autowired
    private IssuerProvisioningRpcChannelProperties issuerProvisioningRpcChannelProperties;
    @Autowired
    private DataspaceIssuerProperties properties;

    @BeforeEach
    void clearTablesAndRestubCollaborators() {
        jdbc.update("delete from " + IssuerDidEntity.TABLE_NAME);
        jdbc.update("delete from " + IssuerSetUpLeaseEntity.TABLE_NAME);

        reset(systemParameterService, dsTlsCertificateService, issuerProvisioningRpcChannelProperties, properties);
        when(properties.getHost()).thenReturn(HOST);
        when(properties.getDidPort()).thenReturn(DID_PORT);
        when(properties.getIssuancePort()).thenReturn(ISSUANCE_PORT);
        when(properties.getStatuslistPort()).thenReturn(STATUSLIST_PORT);
        lenient().when(properties.getCredentialJsonSchemaUrl()).thenReturn(SCHEMA_URL);
    }

    @Test
    void crashBetweenKeyCreationAndBindingThenASecondRunBindsWithoutCreatingAKeyAndLogsAWarning() {
        var rpcClient = mock(IssuerProvisioningRpcClient.class);
        when(rpcClient.canSign(any())).thenReturn(
                CanSignResult.absent(), CanSignResult.canSign(EXPECTED_DID),
                CanSignResult.canSign(EXPECTED_DID), CanSignResult.canSign(EXPECTED_DID));
        var didServiceDouble = mock(DataspaceIssuerDidService.class);
        doThrow(new IllegalStateException("crash before bind"))
                .doAnswer(invocation -> {
                    dataspaceIssuerDidService.register(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
                    return null;
                })
                .when(didServiceDouble).register(any(), any(), anyInt(), any(), any());
        var service = new DataspaceIssuerProvisioningServiceImpl(rpcClient, properties, didServiceDouble);

        var logger = (Logger) LoggerFactory.getLogger(DataspaceIssuerProvisioningServiceImpl.class);
        var originalLevel = logger.getLevel();
        var logEvents = new ListAppender<ILoggingEvent>();
        logger.setLevel(Level.WARN);
        logEvents.start();
        logger.addAppender(logEvents);
        try {
            assertThatThrownBy(service::provisionIssuer).isInstanceOf(RuntimeException.class);
            assertThat(countIssuerDids()).isZero();

            service.provisionIssuer();
        } finally {
            logger.detachAppender(logEvents);
            logger.setLevel(originalLevel);
        }

        verify(rpcClient, times(1)).createParticipantContext(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(didServiceDouble, times(2)).register(any(), any(), anyInt(), any(), any());
        assertThat(logEvents.list).anyMatch(event -> event.getLevel() == Level.WARN
                && event.getFormattedMessage().contains("without creating a new key"));
        assertThat(countIssuerDids()).isEqualTo(1);
    }

    @Test
    void anUnboundLeftoverThatCannotSignOrHasAnotherDidLeavesIssuerDidsEmpty() {
        var cannotSignClient = mock(IssuerProvisioningRpcClient.class);
        when(cannotSignClient.canSign(any())).thenReturn(CanSignResult.cannotSign(CanSignCause.NO_ACTIVE_KEY));
        var cannotSignService = new DataspaceIssuerProvisioningServiceImpl(cannotSignClient, properties, dataspaceIssuerDidService);

        assertThatThrownBy(cannotSignService::provisionIssuer).isInstanceOf(RuntimeException.class);

        verify(cannotSignClient, never()).createParticipantContext(any(), any(), any(), any(), any());
        assertThat(countIssuerDids()).isZero();

        var otherDidClient = mock(IssuerProvisioningRpcClient.class);
        when(otherDidClient.canSign(any())).thenReturn(CanSignResult.canSign("did:web:other.example.test%3A7183:issuer"));
        var otherDidService = new DataspaceIssuerProvisioningServiceImpl(otherDidClient, properties, dataspaceIssuerDidService);

        assertThatThrownBy(otherDidService::provisionIssuer).isInstanceOf(RuntimeException.class);

        verify(otherDidClient, never()).createParticipantContext(any(), any(), any(), any(), any());
        assertThat(countIssuerDids()).isZero();
    }

    @Test
    void boundRefusesAlreadySetUpAndLeavesTheBindingAndDidDocumentUnchangedRegardlessOfKeyHealth() throws Exception {
        givenEveryPrerequisiteCheckPasses();
        dataspaceIssuerDidService.register(EXPECTED_DID, HOST, DID_PORT, ISSUANCE_URL, STATUSLIST_BASE_URL);
        var rpcClient = mock(IssuerProvisioningRpcClient.class);
        var provisioningService = new DataspaceIssuerProvisioningServiceImpl(rpcClient, properties, dataspaceIssuerDidService);
        var setUpService = buildSetUpService(provisioningService, "node-a");

        try {
            var result = setUpService.start();

            assertThat(result).isEqualTo(new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_SET_UP));
            verifyNoInteractions(rpcClient);
            assertThat(countIssuerDids()).isEqualTo(1);
        } finally {
            setUpService.destroy();
        }
    }

    @Test
    void aRunStoppedAfterKeyCreationBindsWhenStartIsCalledAgain() throws Exception {
        givenEveryPrerequisiteCheckPasses();
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);
        var rpcClient = mock(IssuerProvisioningRpcClient.class);
        when(rpcClient.canSign(any())).thenReturn(
                CanSignResult.absent(), CanSignResult.canSign(EXPECTED_DID),
                CanSignResult.canSign(EXPECTED_DID), CanSignResult.canSign(EXPECTED_DID));
        var didServiceDouble = mock(DataspaceIssuerDidService.class);
        doThrow(new IllegalStateException("crash before bind"))
                .doAnswer(invocation -> {
                    dataspaceIssuerDidService.register(invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
                    return null;
                })
                .when(didServiceDouble).register(any(), any(), anyInt(), any(), any());
        var provisioningService = new DataspaceIssuerProvisioningServiceImpl(rpcClient, properties, didServiceDouble);
        var setUpService = buildSetUpService(provisioningService, "node-a");

        try {
            assertThat(setUpService.start()).isEqualTo(new IssuerSetUpResult.Accepted());

            Awaitility.await("the crashed run to release the lease")
                    .atMost(Duration.ofSeconds(WAIT_SECONDS))
                    .until(() -> issuerSetUpLeaseService.currentHolder().isEmpty());
            assertThat(countIssuerDids()).isZero();

            assertThat(setUpService.start()).isEqualTo(new IssuerSetUpResult.Accepted());

            Awaitility.await("the second run to finish binding")
                    .atMost(Duration.ofSeconds(WAIT_SECONDS))
                    .until(() -> countIssuerDids() == 1);
            assertThat(jdbc.queryForObject("select did from " + IssuerDidEntity.TABLE_NAME, String.class))
                    .isEqualTo(EXPECTED_DID);
            verify(rpcClient, times(1)).createParticipantContext(anyString(), anyString(), anyString(), anyString(), anyString());
        } finally {
            setUpService.destroy();
        }
    }

    @Test
    void twoConcurrentSetUpCallsOnTwoNodesLeaveOneAcceptedOneRefusedAndOneKeyAndDidDocument() throws Exception {
        givenEveryPrerequisiteCheckPasses();
        when(issuerProvisioningRpcChannelProperties.deadlineAfter()).thenReturn(DEADLINE_AFTER_MS);
        var rpcClient = mock(IssuerProvisioningRpcClient.class);
        when(rpcClient.canSign(any())).thenReturn(CanSignResult.absent(), CanSignResult.canSign(EXPECTED_DID));
        var provisioningService = new DataspaceIssuerProvisioningServiceImpl(rpcClient, properties, dataspaceIssuerDidService);

        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        var nodeA = buildSetUpService(provisioningService, "node-a");
        var nodeB = buildSetUpService(provisioningService, "node-b");
        try {
            Future<IssuerSetUpResult> callA = executor.submit(callStart(nodeA, barrier));
            Future<IssuerSetUpResult> callB = executor.submit(callStart(nodeB, barrier));

            var results = List.of(callA.get(WAIT_SECONDS, TimeUnit.SECONDS), callB.get(WAIT_SECONDS, TimeUnit.SECONDS));

            assertThat(results).containsExactlyInAnyOrder(
                    new IssuerSetUpResult.Accepted(),
                    new IssuerSetUpResult.Refused(IssuerSetUpResult.Cause.ALREADY_RUNNING));

            Awaitility.await("the winning run to finish binding")
                    .atMost(Duration.ofSeconds(WAIT_SECONDS))
                    .until(() -> countIssuerDids() == 1);
            verify(rpcClient, times(1)).createParticipantContext(anyString(), anyString(), anyString(), anyString(), anyString());
        } finally {
            executor.shutdownNow();
            nodeA.destroy();
            nodeB.destroy();
        }
    }

    private void givenEveryPrerequisiteCheckPasses() throws CertificateParsingException {
        when(systemParameterService.getIssuerServiceHost()).thenReturn(HOST);
        var certificate = mock(X509Certificate.class);
        lenient().when(certificate.getSubjectAlternativeNames()).thenReturn(List.of(List.of(GENERAL_NAME_DNS, HOST)));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, certificate));
    }

    private IssuerSetUpServiceImpl buildSetUpService(DataspaceIssuerProvisioningServiceImpl provisioningService, String node) {
        return new IssuerSetUpServiceImpl(issuerHostService, dsTlsCertificateService, provisioningService,
                issuerSetUpLeaseService, issuerProvisioningRpcChannelProperties, new HAConfigStatus(node, true));
    }

    private static Callable<IssuerSetUpResult> callStart(IssuerSetUpServiceImpl service, CyclicBarrier barrier) {
        return () -> {
            barrier.await(10, TimeUnit.SECONDS);
            return service.start();
        };
    }

    private int countIssuerDids() {
        return jdbc.queryForObject("select count(*) from " + IssuerDidEntity.TABLE_NAME, Integer.class);
    }

    /**
     * Supplies {@link DataspaceIssuerDidServiceImpl}, {@link IssuerSetUpLeaseServiceImpl} and
     * {@link IssuerHostServiceImpl} as Spring beans on top of {@link PostgresTestConfiguration}'s repositories, and
     * mocks every collaborator outside the database.
     */
    @TestConfiguration
    static class Config {

        @Bean
        DnsNameValidator dnsNameValidator() {
            return new DnsNameValidator();
        }

        @Bean
        DataspaceIssuerDidServiceImpl dataspaceIssuerDidService(IssuerDidRepository issuerDidRepository) {
            return new DataspaceIssuerDidServiceImpl(issuerDidRepository);
        }

        @Bean
        IssuerSetUpLeaseServiceImpl issuerSetUpLeaseService(IssuerSetUpLeaseRepository issuerSetUpLeaseRepository) {
            return new IssuerSetUpLeaseServiceImpl(issuerSetUpLeaseRepository);
        }

        @Bean
        IssuerHostServiceImpl issuerHostService(SystemParameterService systemParameterService,
                DataspaceIssuerDidService dataspaceIssuerDidService, DnsNameValidator dnsNameValidator) {
            return new IssuerHostServiceImpl(systemParameterService, dataspaceIssuerDidService, dnsNameValidator);
        }

        @Bean
        SystemParameterService systemParameterService() {
            return mock(SystemParameterService.class);
        }

        @Bean
        DsTlsCertificateService dsTlsCertificateService() {
            return mock(DsTlsCertificateService.class);
        }

        @Bean
        IssuerProvisioningRpcChannelProperties issuerProvisioningRpcChannelProperties() {
            return mock(IssuerProvisioningRpcChannelProperties.class);
        }

        @Bean
        DataspaceIssuerProperties dataspaceIssuerProperties() {
            return mock(DataspaceIssuerProperties.class);
        }
    }
}
