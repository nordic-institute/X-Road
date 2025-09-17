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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.model.Client;
import org.niis.xroad.serverconf.model.TimestampingService;
import org.niis.xroad.signer.api.dto.AuthKeyInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
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
    @MockitoBean
    private AdminServiceProperties adminServiceProperties;

    private static final ClientId.Conf OWNER_MEMBER =
            TestUtils.getClientId("FI", "GOV", "M1", null);
    private static final ClientId.Conf SUBSYSTEM =
            TestUtils.getClientId("FI", "GOV", "M1", "SS1");
    private static final ClientId.Conf NEW_OWNER_MEMBER =
            TestUtils.getClientId("FI", "GOV", "M2", null);
    private static final SecurityServerId.Conf SS_ID = SecurityServerId.Conf.create(OWNER_MEMBER, "TEST-INMEM-SS");
    private static final SecurityServerId.Conf NEW_SS_ID = SecurityServerId.Conf.create(NEW_OWNER_MEMBER, "TEST-INMEM-SS");
    private static final String KEY_OWNER_ID = "key-owner";
    private static final String CERT_OWNER_HASH = "cert-owner";
    private static final String KEY_NEW_OWNER_ID = "key-new-owner";
    private static final String CERT_NEW_OWNER_HASH = "cert-new-owner";
    private static final String KEY_AUTH_ID = "key-auth";
    private static final String CERT_AUTH_HASH = "cert-auth";
    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

    @Before
    public void setup() throws Exception {
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
        when(globalConfProvider.getServerId(any())).thenReturn(SS_ID);

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
        when(globalConfProvider.getServerId(any())).thenReturn(NEW_SS_ID);

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
    public void testUpdateTimestampServiceUrls() {

        // test with single matching items
        List<SharedParameters.ApprovedTSA> approvedTSATypes =
                Collections.singletonList(TestUtils.createApprovedTsaType("http://example.com:8121", "Foo"));
        List<TimestampingService> timestampingServices =
                Collections.singletonList(TestUtils.createTspType("http://example.com:8121", "Foo"));
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes, timestampingServices);
        assertEquals(1, approvedTSATypes.size());
        assertEquals(1, timestampingServices.size());
        assertEquals(approvedTSATypes.getFirst().getName(), timestampingServices.getFirst().getName());
        assertEquals(approvedTSATypes.getFirst().getUrl(), timestampingServices.getFirst().getUrl());

        // test the normal update case
        // the change in approvedTSAType1 URL should be reflected to tspType1 URL
        List<SharedParameters.ApprovedTSA> approvedTSATypes1 = Arrays.asList(
                TestUtils.createApprovedTsaType("http://example.com:9999", "Foo"),
                TestUtils.createApprovedTsaType("http://example.net", "Bar")
        );
        List<TimestampingService> tspTypes1 = Arrays.asList(
                TestUtils.createTspType("http://example.com:8121", "Foo"),
                TestUtils.createTspType("http://example.net", "Bar")
        );
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes1, tspTypes1);
        assertEquals(2, approvedTSATypes1.size());
        assertEquals(2, tspTypes1.size());
        assertEquals(approvedTSATypes1.getFirst().getName(), tspTypes1.getFirst().getName());
        assertEquals(approvedTSATypes1.getFirst().getUrl(), tspTypes1.getFirst().getUrl());
        assertEquals(approvedTSATypes1.get(1).getName(), tspTypes1.get(1).getName());
        assertEquals(approvedTSATypes1.get(1).getUrl(), tspTypes1.get(1).getUrl());

        // test the conflicting update case
        // the change in approvedTSAType3 URL should not be reflected to tspType3 URL because of ambiguous names
        List<SharedParameters.ApprovedTSA> approvedTSATypes2 = Arrays.asList(
                TestUtils.createApprovedTsaType("http://example.com:9898", "Foo"),
                TestUtils.createApprovedTsaType("http://example.net", "Foo"),
                TestUtils.createApprovedTsaType("http://example.org:8080", "Zzz")
        );
        List<TimestampingService> tspTypes2 = Arrays.asList(
                TestUtils.createTspType("http://example.com:8121", "Foo"),
                TestUtils.createTspType("http://example.net", "Foo"),
                TestUtils.createTspType("http://example.org:8080", "Zzz")
        );
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes2, tspTypes2);
        assertEquals(3, approvedTSATypes2.size());
        assertEquals(3, tspTypes2.size());
        assertEquals(approvedTSATypes2.getFirst().getName(), tspTypes2.getFirst().getName());
        assertNotEquals(approvedTSATypes2.getFirst().getUrl(), tspTypes2.getFirst().getUrl());
        assertEquals(approvedTSATypes2.get(1).getName(), tspTypes2.get(1).getName());
        assertEquals(approvedTSATypes2.get(1).getUrl(), tspTypes2.get(1).getUrl());
        assertEquals(approvedTSATypes2.get(2).getName(), tspTypes2.get(2).getName());
        assertEquals(approvedTSATypes2.get(2).getUrl(), tspTypes2.get(2).getUrl());
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

}
