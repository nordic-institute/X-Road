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
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.niis.xroad.common.CostType;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.globalconf.model.ServerSystemValues;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.restapi.common.backup.service.BackupRestoreEvent;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.exceptions.ErrorMessage;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.securityserver.restapi.repository.PendingAddressChangeRepository;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.service.DataspaceDidAuthority;
import org.niis.xroad.securityserver.restapi.service.DataspaceReIdentificationService;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.securityserver.restapi.util.ParticipantRowTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.TimestampingServiceEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.impl.ownserver.OwnerNotInitialised;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.PublicationState;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.test.MutableClock;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;

/**
 * Test GlobalConfChecker
 */
@Slf4j
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class GlobalConfCheckerTest extends AbstractFacadeMockingTestContext {

    @Autowired
    private GlobalConfChecker globalConfChecker;
    @Autowired
    private ServerConfService serverConfService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private GlobalConfService globalConfService;
    @MockitoBean
    private MailNotificationHelper mailNotificationHelper;
    @MockitoSpyBean
    private AdminServiceProperties adminServiceProperties;
    @MockitoSpyBean
    private OwnSecurityServerResolver ownSecurityServerResolver;
    @Autowired
    private PendingAddressChangeRepository pendingAddressChangeRepository;
    @MockitoBean
    private DataspaceParticipantProvisioningWorker dataspaceParticipantProvisioningWorker;
    @MockitoBean
    private DataspaceDidAuthority dataspaceDidAuthority;
    @MockitoBean
    private DataspaceReIdentificationService reIdentificationService;
    @Autowired
    private PendingAddressChangeGlobalConfCheck pendingAddressChangeGlobalConfCheck;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    private DsParticipantRepository dsParticipantRepository;

    private final Map<Long, String> clientStatusSnapshot = new HashMap<>();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-20T10:00:00Z"));
    private final ListAppender<ILoggingEvent> pendingAddressChangeLog = new ListAppender<>();
    private final Logger pendingAddressChangeLogger =
            (Logger) LoggerFactory.getLogger(PendingAddressChangeGlobalConfCheck.class);
    private Level pendingAddressChangeLoggerLevel;
    private ParticipantRowTestUtils participantRows;

    private static final ClientId.Conf OWNER_MEMBER =
            TestUtils.getClientId("FI", "GOV", "M1", null);
    private static final ClientId.Conf SUBSYSTEM =
            TestUtils.getClientId("FI", "GOV", "M1", "SS1");
    private static final ClientId.Conf NEW_OWNER_MEMBER =
            TestUtils.getClientId("FI", "GOV", "M2", null);
    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create(OWNER_MEMBER, "TEST-INMEM-SS");
    private static final SecurityServerId.Conf NEW_SS_ID = SecurityServerId.Conf.create(NEW_OWNER_MEMBER, "TEST-INMEM-SS");
    private static final String NEW_ADDRESS = "new.example.org";
    private static final Duration EXPIRY = Duration.ofMinutes(7);
    private static final String KEY_OWNER_ID = "key-owner";
    private static final String CERT_OWNER_HASH = "cert-owner";
    private static final String KEY_NEW_OWNER_ID = "key-new-owner";
    private static final String CERT_NEW_OWNER_HASH = "cert-new-owner";
    private static final String KEY_AUTH_ID = "key-auth";
    private static final String CERT_AUTH_HASH = "cert-auth";
    private static final long SYSTEM_ROW_ID = 9002L;
    private static final long MEMBER_ROW_ID = 9003L;
    private static final String MEMBER_DID = "did:web:ss.example.test%3A7183:m1";
    private static final String SYSTEM_DID = "did:web:ss.example.test%3A7183:system";
    private static final String DSP_BASE_URL = "https://ss.example.test:7181/dsp";
    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

    @Before
    public void setup() throws Exception {
        participantRows = new ParticipantRowTestUtils(jdbcTemplate, transactionManager);
        clientStatusSnapshot.clear();
        jdbcTemplate.query("SELECT id, clientstatus FROM client",
                (RowCallbackHandler) rs -> clientStatusSnapshot.put(rs.getLong("id"), rs.getString("clientstatus")));
        pendingAddressChangeRepository.delete();
        doReturn(EXPIRY).when(adminServiceProperties).getAddressChangeExpiry();
        pendingAddressChangeGlobalConfCheck.setClock(clock);
        pendingAddressChangeLog.start();
        pendingAddressChangeLogger.addAppender(pendingAddressChangeLog);
        pendingAddressChangeLoggerLevel = pendingAddressChangeLogger.getLevel();
        pendingAddressChangeLogger.setLevel(Level.INFO);
        doAnswer(answer -> null).when(globalConfProvider).verifyValidity();
        doAnswer(answer -> null).when(globalConfProvider).reload();

        List<MemberInfo> globalMemberInfos = Arrays.asList(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        null));
        when(globalConfProvider.getMembers(any())).thenReturn(globalMemberInfos);
        when(globalConfProvider.getMemberName(any())).thenAnswer(invocation -> {
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            Optional<MemberInfo> m = globalMemberInfos.stream()
                    .filter(g -> g.id().equals(clientId))
                    .findFirst();
            return m.map(MemberInfo::name).orElse(null);
        });

        when(globalConfProvider.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(managementRequestSenderService.sendClientRegisterRequest(any(), anyString())).thenReturn(1);
        when(globalConfProvider.getServerIdOrThrow(any())).thenReturn(SS_ID);

        KeyInfo ownerSignKey = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_OWNER_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(OWNER_MEMBER)
                        .id(CERT_OWNER_HASH)
                        .build())
                .build();

        KeyInfo newOwnerSignKey = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_NEW_OWNER_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(NEW_OWNER_MEMBER)
                        .id(CERT_NEW_OWNER_HASH)
                        .build())
                .build();
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .id(CERT_AUTH_HASH)
                .build();
        KeyInfo authKey = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_AUTH_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(certificateInfo)
                .build();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("fubar")
                .key(ownerSignKey)
                .key(newOwnerSignKey)
                .key(authKey)
                .build();
        Map<String, TokenInfo> tokens = new HashMap<>();
        tokens.put(tokenInfo.getId(), tokenInfo);

        when(signerRpcClient.getTokens()).thenReturn(new ArrayList<>(tokens.values()));
        when(signerRpcClient.getAuthKey(any())).thenReturn(new AuthKeyInfo(
                KEY_AUTH_ID, null, certificateInfo));
    }

    @Test
    public void updateLocalClientStatus() {
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        // Verify initial state
        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerIdEntity().toString());
        ClientEntity owner = clientService.getLocalClientEntity(OWNER_MEMBER);
        log.debug("Owner {}", owner.getIdentifier());
        assertEquals(Client.STATUS_REGISTERED, owner.getClientStatus());
        ClientEntity subsystem = clientService.getLocalClientEntity(SUBSYSTEM);
        assertEquals(Client.STATUS_REGISTERED, subsystem.getClientStatus());

        // Update serverconf
        globalConfChecker.checkGlobalConf();
        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerIdEntity().toString());
        assertEquals(Client.STATUS_REGISTERED, owner.getClientStatus());
        // Subsystem status is changed to "GLOBALERR" since it's not recognized as a Security Server client
        assertEquals(Client.STATUS_GLOBALERR, subsystem.getClientStatus());

        // Global conf starts to recognize the subsystem as a Security Server client
        when(globalConfProvider.isSecurityServerClient(SUBSYSTEM, SS_ID)).thenReturn(true);
        // Update serverconf
        globalConfChecker.checkGlobalConf();
        // Subsystem status is changed back to "REGISTERED"
        assertEquals(Client.STATUS_REGISTERED, subsystem.getClientStatus());
    }

    @Test
    public void updateClientStatusesNudgesProvisioningWhenClientStatusesChange() {
        globalConfChecker.checkGlobalConf();

        verify(dataspaceParticipantProvisioningWorker, atLeastOnce()).provisionParticipantAsync();
    }

    @Test
    public void updateClientStatusesDoesNotNudgeProvisioningWhenNoClientStatusChanges() {
        ClientId.Conf ss2 = TestUtils.getClientId(TestUtils.CLIENT_ID_SS2);
        ClientId.Conf ss5 = TestUtils.getClientId(TestUtils.CLIENT_ID_SS5);
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        when(globalConfProvider.isSecurityServerClient(SUBSYSTEM, SS_ID)).thenReturn(true);
        when(globalConfProvider.isSecurityServerClient(ss2, SS_ID)).thenReturn(true);
        when(globalConfProvider.isSecurityServerClient(ss5, SS_ID)).thenReturn(true);

        globalConfChecker.checkGlobalConf();

        verify(dataspaceParticipantProvisioningWorker, never()).provisionParticipantAsync();
    }

    @Test
    public void updateRenameStatus() {
        var originalName = "originalName";
        var newName = "newName";
        when(globalConfProvider.getSubsystemName(SUBSYSTEM)).thenReturn(originalName);
        subsystemNameStatus.submit(SUBSYSTEM, null, newName);

        when(globalConfProvider.getSubsystemName(SUBSYSTEM)).thenReturn(newName);
        globalConfChecker.checkGlobalConf();

        assertFalse(subsystemNameStatus.getRename(SUBSYSTEM).isPresent());
    }

    @Test
    public void registerMemberAndChangeSecurityServerOwner() throws Exception {
        when(globalConfService.getMemberClassesForThisInstance()).thenReturn(new HashSet<>(MEMBER_CLASSES));

        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerIdEntity().toString());

        // Add new member locally
        ClientEntity clientEntity = clientService.addLocalClientEntity(NEW_OWNER_MEMBER.getMemberClass(),
                NEW_OWNER_MEMBER.getMemberCode(), NEW_OWNER_MEMBER.getSubsystemCode(), null,
                IsAuthentication.SSLAUTH, false);
        assertEquals(Client.STATUS_SAVED, clientEntity.getClientStatus());

        // Register new member
        clientService.registerClient(NEW_OWNER_MEMBER);
        assertEquals(Client.STATUS_REGINPROG, clientEntity.getClientStatus());
        when(globalConfProvider.isSecurityServerClient(any(), any())).thenReturn(true);

        // Update serverconf
        globalConfChecker.checkGlobalConf();
        assertEquals(Client.STATUS_REGISTERED, clientEntity.getClientStatus());
        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerIdEntity().toString());

        // Global conf starts to recognize the new member as the Security Server owner
        when(globalConfProvider.getServerOwner(SS_ID)).thenReturn(null);
        when(globalConfProvider.getServerOwner(NEW_SS_ID)).thenReturn(NEW_OWNER_MEMBER);
        when(globalConfProvider.getServerIdOrThrow(any())).thenReturn(NEW_SS_ID);

        // Update serverconf => owner is changed
        globalConfChecker.checkGlobalConf();
        assertEquals(NEW_OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerIdEntity().toString());
    }

    @Test
    public void updateCertStatuses() {
        CertificateInfo regInProgCertificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .id("regInProgCert")
                .certificateStatus(CertificateInfo.STATUS_REGINPROG)
                .build();
        KeyInfo regInProgAuthKey = new TokenTestUtils.KeyInfoBuilder()
                .id("regInProgKey")
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(regInProgCertificateInfo)
                .build();
        CertificateInfo savedCertificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .id("savedCert")
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .build();
        KeyInfo savedAuthKey = new TokenTestUtils.KeyInfoBuilder()
                .id("savedKey")
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(savedCertificateInfo)
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("token")
                .key(regInProgAuthKey)
                .key(savedAuthKey)
                .build();
        Map<String, TokenInfo> tokens = new HashMap<>();
        tokens.put(tokenInfo.getId(), tokenInfo);
        when(signerRpcClient.getTokens()).thenReturn(new ArrayList<>(tokens.values()));

        globalConfChecker.checkGlobalConf();

        verify(signerRpcClient, times(2)).setCertStatus(any(), any());
        verify(mailNotificationHelper).sendAuthCertRegisteredNotification(any(), any());
        verify(signerRpcClient, times(0)).activateCert(any());
        verify(mailNotificationHelper, times(0)).sendCertActivatedNotification(any(), any(), any(), any());
    }

    @Test
    public void updateCertStatusesActivateCert() {
        CertificateInfo regInProgCertificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .id("regInProgCert")
                .certificateStatus(CertificateInfo.STATUS_REGINPROG)
                .build();
        KeyInfo regInProgAuthKey = new TokenTestUtils.KeyInfoBuilder()
                .id("regInProgKey")
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(regInProgCertificateInfo)
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("token")
                .key(regInProgAuthKey)
                .build();
        Map<String, TokenInfo> tokens = new HashMap<>();
        tokens.put(tokenInfo.getId(), tokenInfo);
        when(signerRpcClient.getTokens()).thenReturn(new ArrayList<>(tokens.values()));

        when(adminServiceProperties.isAutomaticActivateAuthCertificate()).thenReturn(true);
        globalConfChecker.checkGlobalConf();

        verify(signerRpcClient).setCertStatus(any(), any());
        verify(mailNotificationHelper).sendAuthCertRegisteredNotification(any(), any());
        verify(signerRpcClient).activateCert(any());
        verify(mailNotificationHelper).sendCertActivatedNotification(any(), any(), any(), any());
    }

    @Test
    public void testUpdateTimestampServiceUrlsAndCostTypes() {

        // test with single matching items
        List<SharedParameters.ApprovedTSA> approvedTSATypes =
                Collections.singletonList(TestUtils.createApprovedTsaType("http://example.com:8121", "Foo", CostType.FREE));
        List<TimestampingServiceEntity> timestampingServices =
                Collections.singletonList(TestUtils.createTspTypeEntity("http://example.com:8121", "Foo", CostType.FREE.name()));
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes, timestampingServices);
        assertEquals(1, approvedTSATypes.size());
        assertEquals(1, timestampingServices.size());
        assertEquals(approvedTSATypes.getFirst().getName(), timestampingServices.getFirst().getName());
        assertEquals(approvedTSATypes.getFirst().getUrl(), timestampingServices.getFirst().getUrl());
        assertEquals(approvedTSATypes.getFirst().getCostType().name(), timestampingServices.get(0).getCostType());

        // test the normal update case
        // the change in approvedTSAType1 URL should be reflected to tspType1 URL
        List<SharedParameters.ApprovedTSA> approvedTSATypes1 = Arrays.asList(
                TestUtils.createApprovedTsaType("http://example.com:9999", "Foo", CostType.FREE),
                TestUtils.createApprovedTsaType("http://example.net", "Bar", CostType.PAID)
        );
        List<TimestampingServiceEntity> tspTypes1 = Arrays.asList(
                TestUtils.createTspTypeEntity("http://example.com:8121", "Foo", CostType.FREE.name()),
                TestUtils.createTspTypeEntity("http://example.net", "Bar", CostType.FREE.name())
        );
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes1, tspTypes1);
        globalConfChecker.updateTimestampServiceCostTypes(approvedTSATypes1, tspTypes1);
        assertEquals(2, approvedTSATypes1.size());
        assertEquals(2, tspTypes1.size());
        assertEquals(approvedTSATypes1.getFirst().getName(), tspTypes1.getFirst().getName());
        assertEquals(approvedTSATypes1.getFirst().getUrl(), tspTypes1.getFirst().getUrl());
        assertEquals(approvedTSATypes1.getFirst().getCostType().name(), tspTypes1.get(0).getCostType());
        assertEquals(approvedTSATypes1.get(1).getName(), tspTypes1.get(1).getName());
        assertEquals(approvedTSATypes1.get(1).getUrl(), tspTypes1.get(1).getUrl());
        assertEquals(approvedTSATypes1.get(1).getCostType().name(), tspTypes1.get(1).getCostType());

        // test the conflicting update case
        // the change in approvedTSAType2 URL should not be reflected to tspType3 URL because of ambiguous names
        List<SharedParameters.ApprovedTSA> approvedTSATypes2 = Arrays.asList(
                TestUtils.createApprovedTsaType("http://example.com:9898", "Foo", CostType.FREE),
                TestUtils.createApprovedTsaType("http://example.net", "Foo", CostType.PAID),
                TestUtils.createApprovedTsaType("http://example.org:8080", "Zzz", CostType.UNDEFINED)
        );
        List<TimestampingServiceEntity> tspTypes2 = Arrays.asList(
                TestUtils.createTspTypeEntity("http://example.com:8121", "Foo", CostType.PAID.name()),
                TestUtils.createTspTypeEntity("http://example.net", "Foo", CostType.PAID.name()),
                TestUtils.createTspTypeEntity("http://example.org:8080", "Zzz", CostType.UNDEFINED.name())
        );
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes2, tspTypes2);
        globalConfChecker.updateTimestampServiceCostTypes(approvedTSATypes2, tspTypes2);
        assertEquals(3, approvedTSATypes2.size());
        assertEquals(3, tspTypes2.size());
        assertEquals(approvedTSATypes2.getFirst().getName(), tspTypes2.getFirst().getName());
        assertNotEquals(approvedTSATypes2.getFirst().getUrl(), tspTypes2.getFirst().getUrl());
        assertNotEquals(approvedTSATypes2.get(0).getCostType().name(), tspTypes2.get(0).getCostType());
        assertEquals(approvedTSATypes2.get(1).getName(), tspTypes2.get(1).getName());
        assertEquals(approvedTSATypes2.get(1).getUrl(), tspTypes2.get(1).getUrl());
        assertEquals(approvedTSATypes2.get(1).getCostType().name(), tspTypes2.get(1).getCostType());
        assertEquals(approvedTSATypes2.get(2).getName(), tspTypes2.get(2).getName());
        assertEquals(approvedTSATypes2.get(2).getUrl(), tspTypes2.get(2).getUrl());
        assertEquals(approvedTSATypes2.get(2).getCostType().name(), tspTypes2.get(2).getCostType());
    }

    @Test
    public void testUpdateTimestampServiceCostTypesNullGlobalCostType() {
        List<SharedParameters.ApprovedTSA> globalTsps =
                Collections.singletonList(TestUtils.createApprovedTsaType("http://example.com:8121", "Foo", null));
        List<TimestampingServiceEntity> localTsps =
                Collections.singletonList(TestUtils.createTspTypeEntity("http://example.com:8121", "Foo", CostType.UNDEFINED.name()));

        globalConfChecker.updateTimestampServiceCostTypes(globalTsps, localTsps);

        assertEquals(CostType.UNDEFINED.name(), localTsps.getFirst().getCostType());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registeredWithMatchingAddressReIdentifiesAndClearsPendingAddressChange() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify()).thenReturn(3);
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registeredWithMatchingAddressAfterTheExpiryStillReIdentifies() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        pendingAddressChangeRepository.save(NEW_ADDRESS, requestedAgo(EXPIRY.plusSeconds(1)));

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void expiredPendingAddressChangeIsDeletedWithoutReIdentifying() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn("old.example.org");
        pendingAddressChangeRepository.save(NEW_ADDRESS, requestedAgo(EXPIRY));

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, never()).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void theNormalExpiryIsLoggedAtInfo() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn("old.example.org");
        pendingAddressChangeRepository.save(NEW_ADDRESS, requestedAgo(EXPIRY));

        globalConfChecker.checkGlobalConf();

        assertEquals(List.of(Level.INFO), droppedRequestLogLevels());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void registeredWithDifferentAddressKeepsPendingAddressChange() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn("old.example.org");
        pendingAddressChangeRepository.save(NEW_ADDRESS, requestedAgo(EXPIRY.minusSeconds(1)));

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, never()).reIdentify();
        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notRegisteredKeepsPendingAddressChangeAndSyncContinues() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(null);
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, never()).reIdentify();
        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
        verifySyncRan();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void globalConfUnavailableKeepsPendingAddressChangeAndSyncContinues() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.MALFORMED_GLOBALCONF, "unreadable"));
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, never()).reIdentify();
        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
        verifySyncRan();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aPendingAddressChangeReadAfterARestartStillLeadsToTheReIdentification() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify()).thenReturn(3);
        jdbcTemplate.update("INSERT INTO pending_address_change (id, requested_address, requested_at) VALUES (1, ?, ?)",
                NEW_ADDRESS, LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aSecondCheckAfterTheReIdentificationChangesNothing() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify()).thenReturn(3);
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();
        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, times(1)).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aRefusedReIdentificationStillDeletesThePendingAddressChange() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify())
                .thenThrow(new ConflictException(ErrorMessage.RE_IDENTIFICATION_CANNOT_BIND.build()));
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, times(1)).reIdentify();
        assertFalse(pendingAddress().isPresent());
        verifySyncRan();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void anUnexpectedReIdentificationFailureKeepsThePendingAddressChangeForTheNextCheck() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify())
                .thenThrow(new IllegalStateException("boom"))
                .thenReturn(3);
        pendingAddressChangeRepository.save(NEW_ADDRESS, requestedAgo(EXPIRY.minusSeconds(1)));

        globalConfChecker.checkGlobalConf();

        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
        verifySyncRan();

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, times(2)).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void anUnexpectedReIdentificationFailureDropsThePendingAddressChangeOnceTheExpiryHasPassed() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify()).thenThrow(new IllegalStateException("boom"));
        pendingAddressChangeRepository.save(NEW_ADDRESS, requestedAgo(EXPIRY.minusSeconds(1)));

        globalConfChecker.checkGlobalConf();

        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());

        clock.advance(Duration.ofSeconds(1));
        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService, times(2)).reIdentify();
        assertFalse(pendingAddress().isPresent());
        assertEquals(List.of(Level.WARN), droppedRequestLogLevels());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void anEarlierServerConfStepFailingStillLetsTheRegisteredAddressReIdentify() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify()).thenReturn(3);
        when(signerRpcClient.getTokens()).thenThrow(new IllegalStateException("signer unavailable"));
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aRolledBackServerConfTransactionStillLetsTheRegisteredAddressReIdentify() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        when(reIdentificationService.reIdentify()).thenReturn(3);
        when(signerRpcClient.getTokens()).thenAnswer(answer -> {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            throw new IllegalStateException("signer unavailable");
        });
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        globalConfChecker.checkGlobalConf();

        verify(reIdentificationService).reIdentify();
        assertFalse(pendingAddress().isPresent());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void secondaryNodeLeavesThePendingAddressChangeAlone() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(NEW_ADDRESS);
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());

        try (MockedStatic<NodeProperties> nodePropertiesMock = mockStatic(NodeProperties.class)) {
            nodePropertiesMock.when(NodeProperties::getServerNodeType).thenReturn(SECONDARY);
            globalConfChecker.checkGlobalConf();
        }

        verify(reIdentificationService, never()).reIdentify();
        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void secondCycleWhileNotRegisteredChangesNothingMore() {
        when(globalConfProvider.getSecurityServerAddress(SS_ID)).thenReturn(null);
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());
        globalConfChecker.checkGlobalConf();
        var subsystemStatusAfterFirstCycle = clientStatus(SUBSYSTEM);
        globalConfChecker.checkGlobalConf();
        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
        assertEquals(subsystemStatusAfterFirstCycle, clientStatus(SUBSYSTEM));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void ownerNotInitialisedSkipsServerConfUpdate() {
        doReturn(new OwnerNotInitialised()).when(ownSecurityServerResolver).identity();
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());
        globalConfChecker.checkGlobalConf();
        verifySyncSkipped();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void secondCycleWhileOwnerNotInitialisedChangesNothingMore() {
        doReturn(new OwnerNotInitialised()).when(ownSecurityServerResolver).identity();
        pendingAddressChangeRepository.save(NEW_ADDRESS, clock.instant());
        globalConfChecker.checkGlobalConf();
        globalConfChecker.checkGlobalConf();
        verifySyncSkipped();
    }

    private List<Level> droppedRequestLogLevels() {
        return pendingAddressChangeLog.list.stream()
                .filter(event -> event.getFormattedMessage().contains("request dropped"))
                .map(ILoggingEvent::getLevel)
                .toList();
    }

    private Optional<String> pendingAddress() {
        return pendingAddressChangeRepository.findRequestedAddress();
    }

    private Instant requestedAgo(Duration age) {
        return clock.instant().minus(age);
    }

    private void verifySyncRan() {
        assertEquals(Client.STATUS_GLOBALERR, clientStatus(SUBSYSTEM));
        verify(signerRpcClient).getTokens();
        verify(globalConfProvider).getApprovedTsps(any());
    }

    private void verifySyncSkipped() {
        assertEquals(Optional.of(NEW_ADDRESS), pendingAddress());
        assertEquals(Client.STATUS_REGISTERED, clientStatus(SUBSYSTEM));
        verify(ownSecurityServerResolver, never()).address(any(SecurityServerId.class));
        verify(signerRpcClient, never()).getTokens();
        verify(globalConfProvider, never()).getApprovedTsps(any());
        verify(globalConfProvider, never()).getServerOwner(any());
        verify(reIdentificationService, never()).reIdentify();
    }

    @Test
    public void doNotUpdateServerConfOnSecondary() {
        try (MockedStatic<NodeProperties> nodePropertiesMock = mockStatic(NodeProperties.class)) {
            nodePropertiesMock.when(NodeProperties::getServerNodeType).thenReturn(SECONDARY);
            globalConfChecker.checkGlobalConf();

            verify(globalConfProvider).reload();
            verify(globalConfProvider).verifyValidity();
            verifyNoMoreInteractions(globalConfProvider);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submittedSystemValuesBecomePublishedWhenGlobalConfCarriesThem() {
        participantRows.systemRow(SYSTEM_ROW_ID, SYSTEM_DID, PublicationState.SUBMITTED);
        carrySystemValues(SYSTEM_DID, DSP_BASE_URL);

        globalConfChecker.checkGlobalConf();

        assertEquals(PublicationState.PUBLISHED.name(), systemPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submittedSystemValuesStaySubmittedWhenGlobalConfCarriesDifferentValues() {
        participantRows.systemRow(SYSTEM_ROW_ID, SYSTEM_DID, PublicationState.SUBMITTED);
        carrySystemValues(SYSTEM_DID, "https://old.example.test:7181/dsp");

        globalConfChecker.checkGlobalConf();

        assertEquals(PublicationState.SUBMITTED.name(), systemPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submittedSystemValuesStaySubmittedOnSecondary() {
        participantRows.systemRow(SYSTEM_ROW_ID, SYSTEM_DID, PublicationState.SUBMITTED);
        carrySystemValues(SYSTEM_DID, DSP_BASE_URL);

        try (MockedStatic<NodeProperties> nodePropertiesMock = mockStatic(NodeProperties.class)) {
            nodePropertiesMock.when(NodeProperties::getServerNodeType).thenReturn(SECONDARY);
            globalConfChecker.checkGlobalConf();
        }

        assertEquals(PublicationState.SUBMITTED.name(), systemPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submittedSystemValuesStaySubmittedDuringBackupRestore() {
        participantRows.systemRow(SYSTEM_ROW_ID, SYSTEM_DID, PublicationState.SUBMITTED);
        carrySystemValues(SYSTEM_DID, DSP_BASE_URL);

        eventPublisher.publishEvent(BackupRestoreEvent.START);
        try {
            globalConfChecker.checkGlobalConf();
            assertEquals(PublicationState.SUBMITTED.name(), systemPublicationState());
        } finally {
            eventPublisher.publishEvent(BackupRestoreEvent.END);
        }

        globalConfChecker.checkGlobalConf();
        assertEquals(PublicationState.PUBLISHED.name(), systemPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aFailingPublicationStepDoesNotUndoTheCommittedClientStatusUpdates() {
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        doThrow(new IllegalStateException("database unavailable")).when(dsParticipantRepository)
                .findActiveSystemParticipant();

        globalConfChecker.checkGlobalConf();

        assertEquals(Client.STATUS_GLOBALERR, clientStatus(SUBSYSTEM));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submittedMemberDidBecomesPublishedWhenGlobalConfCarriesIt() {
        participantRows.memberRow(MEMBER_ROW_ID, OWNER_MEMBER, MEMBER_DID, PublicationState.SUBMITTED);
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        when(globalConfProvider.getMemberDid(OWNER_MEMBER, SS_ID)).thenReturn(Optional.of(MEMBER_DID));

        globalConfChecker.checkGlobalConf();

        assertEquals(PublicationState.PUBLISHED.name(), memberPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submittedMemberDidStaysSubmittedWhenGlobalConfCarriesADifferentDid() {
        participantRows.memberRow(MEMBER_ROW_ID, OWNER_MEMBER, MEMBER_DID, PublicationState.SUBMITTED);
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        when(globalConfProvider.getMemberDid(OWNER_MEMBER, SS_ID)).thenReturn(Optional.of(MEMBER_DID + "-old"));

        globalConfChecker.checkGlobalConf();

        assertEquals(PublicationState.SUBMITTED.name(), memberPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishedMemberDidGoesBackToNotSentWhenGlobalConfLosesItWhileTheMemberIsHostedHere() {
        participantRows.memberRow(MEMBER_ROW_ID, OWNER_MEMBER, MEMBER_DID, PublicationState.PUBLISHED);
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        when(globalConfProvider.getMemberDid(OWNER_MEMBER, SS_ID)).thenReturn(Optional.empty());

        globalConfChecker.checkGlobalConf();

        assertEquals(PublicationState.NOT_SENT.name(), memberPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishedMemberDidStaysPublishedWhenTheSameCheckDropsTheClientRegistrationAndTheDid() {
        participantRows.memberRow(MEMBER_ROW_ID, OWNER_MEMBER, MEMBER_DID, PublicationState.PUBLISHED);
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(false);
        when(globalConfProvider.getMemberDid(OWNER_MEMBER, SS_ID)).thenReturn(Optional.empty());

        globalConfChecker.checkGlobalConf();

        assertEquals(Client.STATUS_GLOBALERR, clientStatus(OWNER_MEMBER));
        assertEquals(PublicationState.PUBLISHED.name(), memberPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishedMemberDidGoesBackToNotSentWhenTheMemberIsHostedOnlyThroughARegisteredSubsystem() {
        participantRows.memberRow(MEMBER_ROW_ID, OWNER_MEMBER, MEMBER_DID, PublicationState.PUBLISHED);
        when(globalConfProvider.isSecurityServerClient(SUBSYSTEM, SS_ID)).thenReturn(true);
        when(globalConfProvider.getMemberDid(OWNER_MEMBER, SS_ID)).thenReturn(Optional.empty());

        globalConfChecker.checkGlobalConf();

        assertEquals(Client.STATUS_GLOBALERR, clientStatus(OWNER_MEMBER));
        assertEquals(Client.STATUS_REGISTERED, clientStatus(SUBSYSTEM));
        assertEquals(PublicationState.NOT_SENT.name(), memberPublicationState());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void aGlobalConfCheckThatRollsBackCommitsNoPublicationTransition() {
        participantRows.memberRow(MEMBER_ROW_ID, OWNER_MEMBER, MEMBER_DID, PublicationState.PUBLISHED);
        when(globalConfProvider.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        when(globalConfProvider.getMemberDid(OWNER_MEMBER, SS_ID)).thenReturn(Optional.empty());
        when(globalConfProvider.getApprovedTsps(any())).thenAnswer(_ -> {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return List.of();
        });

        globalConfChecker.checkGlobalConf();

        assertEquals(Client.STATUS_REGISTERED, clientStatus(SUBSYSTEM));
        assertEquals(PublicationState.PUBLISHED.name(), memberPublicationState());
    }

    @After
    public void deleteSystemRow() {
        pendingAddressChangeLogger.detachAppender(pendingAddressChangeLog);
        pendingAddressChangeLogger.setLevel(pendingAddressChangeLoggerLevel);
        pendingAddressChangeLog.stop();
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback();
            TestTransaction.end();
        }
        participantRows.inNewTransaction(() -> {
            jdbcTemplate.update("DELETE FROM ds_participant WHERE id IN (?, ?)", SYSTEM_ROW_ID, MEMBER_ROW_ID);
            jdbcTemplate.update("DELETE FROM pending_address_change");
            clientStatusSnapshot.forEach((id, status) ->
                    jdbcTemplate.update("UPDATE client SET clientstatus = ? WHERE id = ?", status, id));
        });
    }

    private void carrySystemValues(String did, String dspBaseUrl) {
        when(dataspaceDidAuthority.dspBaseUrlIfValid(any(OwnAddress.class))).thenReturn(Optional.of(DSP_BASE_URL));
        when(globalConfProvider.getServerSystemValues(SS_ID)).thenReturn(Optional.of(new ServerSystemValues(did, dspBaseUrl)));
    }

    private String memberPublicationState() {
        return jdbcTemplate.queryForObject(
                "SELECT publication_state FROM ds_participant WHERE id = ?", String.class, MEMBER_ROW_ID);
    }

    private String clientStatus(ClientId clientId) {
        var query = "SELECT c.clientstatus FROM client c JOIN identifier i ON c.identifier = i.id"
                + " WHERE i.xroad_instance = ? AND i.member_class = ? AND i.member_code = ?";
        return clientId.getSubsystemCode() == null
                ? jdbcTemplate.queryForObject(query + " AND i.subsystem_code IS NULL", String.class,
                        clientId.getXRoadInstance(), clientId.getMemberClass(), clientId.getMemberCode())
                : jdbcTemplate.queryForObject(query + " AND i.subsystem_code = ?", String.class,
                        clientId.getXRoadInstance(), clientId.getMemberClass(), clientId.getMemberCode(),
                        clientId.getSubsystemCode());
    }

    private String systemPublicationState() {
        return jdbcTemplate.queryForObject(
                "SELECT publication_state FROM ds_participant WHERE participant_type = 'SYSTEM'", String.class);
    }

}
