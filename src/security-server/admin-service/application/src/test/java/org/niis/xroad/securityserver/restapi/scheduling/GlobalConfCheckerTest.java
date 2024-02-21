/*
 * The MIT License
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

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.globalconf.SharedParameters;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.signer.protocol.dto.AuthKeyInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.config.AbstractFacadeMockingTestContext;
import org.niis.xroad.securityserver.restapi.service.ClientService;
import org.niis.xroad.securityserver.restapi.service.GlobalConfService;
import org.niis.xroad.securityserver.restapi.service.ServerConfService;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.SystemProperties.NODE_TYPE;
import static ee.ria.xroad.common.SystemProperties.NodeType.MASTER;
import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Test GlobalConfChecker
 */
@Slf4j
public class GlobalConfCheckerTest extends AbstractFacadeMockingTestContext {

    @Autowired
    private GlobalConfChecker globalConfChecker;
    @Autowired
    private ServerConfService serverConfService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private GlobalConfService globalConfService;

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
        doAnswer(answer -> null).when(globalConfFacade).verifyValidity();
        doAnswer(answer -> null).when(globalConfFacade).reload();

        List<MemberInfo> globalMemberInfos = new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        null)));
        when(globalConfFacade.getMembers(any())).thenReturn(globalMemberInfos);
        when(globalConfFacade.getMemberName(any())).thenAnswer(invocation -> {
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            Optional<MemberInfo> m = globalMemberInfos.stream()
                    .filter(g -> g.getId().equals(clientId))
                    .findFirst();
            if (m.isPresent()) {
                return m.get().getName();
            } else {
                return null;
            }
        });

        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(managementRequestSenderService.sendClientRegisterRequest(any())).thenReturn(1);

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

        when(signerProxyFacade.getTokens()).thenReturn(new ArrayList<>(tokens.values()));
        when(signerProxyFacade.getAuthKey(any())).thenReturn(new AuthKeyInfo(
                KEY_AUTH_ID, null, null, certificateInfo));
    }

    @Test
    public void updateLocalClientStatus() {
        when(globalConfFacade.isSecurityServerClient(OWNER_MEMBER, SS_ID)).thenReturn(true);
        // Verify initial state
        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerId().toString());
        ClientType owner = clientService.getLocalClient(OWNER_MEMBER);
        log.debug("Owner {}", owner.getIdentifier());
        assertEquals(ClientType.STATUS_REGISTERED, owner.getClientStatus());
        ClientType subsystem = clientService.getLocalClient(SUBSYSTEM);
        assertEquals(ClientType.STATUS_REGISTERED, subsystem.getClientStatus());

        // Update serverconf
        globalConfChecker.checkGlobalConf();
        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerId().toString());
        assertEquals(ClientType.STATUS_REGISTERED, owner.getClientStatus());
        // Subsystem status is changed to "GLOBALERR" since it's not recognized as a Security Server client
        assertEquals(ClientType.STATUS_GLOBALERR, subsystem.getClientStatus());

        // Global conf starts to recognize the subsystem as a Security Server client
        when(globalConfFacade.isSecurityServerClient(SUBSYSTEM, SS_ID)).thenReturn(true);
        // Update serverconf
        globalConfChecker.checkGlobalConf();
        // Subsystem status is changed back to "REGISTERED"
        assertEquals(ClientType.STATUS_REGISTERED, subsystem.getClientStatus());
    }

    @Test
    public void registerMemberAndChangeSecurityServerOwner() throws Exception {
        when(globalConfService.getMemberClassesForThisInstance()).thenReturn(new HashSet<>(MEMBER_CLASSES));

        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerId().toString());

        // Add new member locally
        ClientType clientType = clientService.addLocalClient(NEW_OWNER_MEMBER.getMemberClass(),
                NEW_OWNER_MEMBER.getMemberCode(), NEW_OWNER_MEMBER.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(ClientType.STATUS_SAVED, clientType.getClientStatus());

        // Register new member
        clientService.registerClient(NEW_OWNER_MEMBER);
        assertEquals(ClientType.STATUS_REGINPROG, clientType.getClientStatus());
        when(globalConfFacade.isSecurityServerClient(any(), any())).thenReturn(true);

        // Update serverconf
        globalConfChecker.checkGlobalConf();
        assertEquals(ClientType.STATUS_REGISTERED, clientType.getClientStatus());
        assertEquals(OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerId().toString());

        // Global conf starts to recognize the new member as the Security Server owner
        when(globalConfFacade.getServerOwner(SS_ID)).thenReturn(null);
        when(globalConfFacade.getServerOwner(NEW_SS_ID)).thenReturn(NEW_OWNER_MEMBER);
        when(globalConfFacade.getServerId(any())).thenReturn(NEW_SS_ID);

        // Update serverconf => owner is changed
        globalConfChecker.checkGlobalConf();
        assertEquals(NEW_OWNER_MEMBER.toString(), serverConfService.getSecurityServerOwnerId().toString());
    }

    @Test
    public void testUpdateTimestampServiceUrls() {

        // test with single matching items
        List<SharedParameters.ApprovedTSA> approvedTSATypes =
                Collections.singletonList(TestUtils.createApprovedTsaType("http://example.com:8121", "Foo"));
        List<TspType> tspTypes =
                Collections.singletonList(TestUtils.createTspType("http://example.com:8121", "Foo"));
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes, tspTypes);
        assertEquals(1, approvedTSATypes.size());
        assertEquals(1, tspTypes.size());
        assertEquals(approvedTSATypes.get(0).getName(), tspTypes.get(0).getName());
        assertEquals(approvedTSATypes.get(0).getUrl(), tspTypes.get(0).getUrl());

        // test the normal update case
        // the change in approvedTSAType1 URL should be reflected to tspType1 URL
        List<SharedParameters.ApprovedTSA> approvedTSATypes1 = Arrays.asList(
                TestUtils.createApprovedTsaType("http://example.com:9999", "Foo"),
                TestUtils.createApprovedTsaType("http://example.net", "Bar")
        );
        List<TspType> tspTypes1 = Arrays.asList(
                TestUtils.createTspType("http://example.com:8121", "Foo"),
                TestUtils.createTspType("http://example.net", "Bar")
        );
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes1, tspTypes1);
        assertEquals(2, approvedTSATypes1.size());
        assertEquals(2, tspTypes1.size());
        assertEquals(approvedTSATypes1.get(0).getName(), tspTypes1.get(0).getName());
        assertEquals(approvedTSATypes1.get(0).getUrl(), tspTypes1.get(0).getUrl());
        assertEquals(approvedTSATypes1.get(1).getName(), tspTypes1.get(1).getName());
        assertEquals(approvedTSATypes1.get(1).getUrl(), tspTypes1.get(1).getUrl());

        // test the conflicting update case
        // the change in approvedTSAType3 URL should not be reflected to tspType3 URL because of ambiguous names
        List<SharedParameters.ApprovedTSA> approvedTSATypes2 = Arrays.asList(
                TestUtils.createApprovedTsaType("http://example.com:9898", "Foo"),
                TestUtils.createApprovedTsaType("http://example.net", "Foo"),
                TestUtils.createApprovedTsaType("http://example.org:8080", "Zzz")
        );
        List<TspType> tspTypes2 = Arrays.asList(
                TestUtils.createTspType("http://example.com:8121", "Foo"),
                TestUtils.createTspType("http://example.net", "Foo"),
                TestUtils.createTspType("http://example.org:8080", "Zzz")
        );
        globalConfChecker.updateTimestampServiceUrls(approvedTSATypes2, tspTypes2);
        assertEquals(3, approvedTSATypes2.size());
        assertEquals(3, tspTypes2.size());
        assertEquals(approvedTSATypes2.get(0).getName(), tspTypes2.get(0).getName());
        assertNotEquals(approvedTSATypes2.get(0).getUrl(), tspTypes2.get(0).getUrl());
        assertEquals(approvedTSATypes2.get(1).getName(), tspTypes2.get(1).getName());
        assertEquals(approvedTSATypes2.get(1).getUrl(), tspTypes2.get(1).getUrl());
        assertEquals(approvedTSATypes2.get(2).getName(), tspTypes2.get(2).getName());
        assertEquals(approvedTSATypes2.get(2).getUrl(), tspTypes2.get(2).getUrl());
    }

    @Test
    public void doNotUpdateServerConfOnSlave() {
        System.setProperty(NODE_TYPE, SLAVE.toString());

        globalConfChecker.checkGlobalConf();

        verify(globalConfFacade).reload();
        verify(globalConfFacade).verifyValidity();
        verifyNoMoreInteractions(globalConfFacade);

        System.setProperty(NODE_TYPE, MASTER.toString());
    }
}
