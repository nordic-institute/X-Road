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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Test TokenCertificateService
 */
public class OrphanRemovalServiceTest extends AbstractServiceTestContext {

    @Autowired
    OrphanRemovalService orphanRemovalService;

    private static final ClientId.Conf NON_DELETED_CLIENT_ID_O1 =
            TestUtils.getClientId("FI:GOV:O_1:SS1");
    private static final ClientId.Conf DELETED_CLIENT_ID_WITH_SIBLINGS_O3 =
            TestUtils.getClientId("FI:GOV:O_3:SS1");
    private static final ClientId.Conf SIBLING_CLIENT_ID_O3 =
            TestUtils.getClientId("FI:GOV:O_3:SS2");
    private static final ClientId.Conf DELETED_CLIENT_ID_WITHOUT_ORPHAN_ITEMS_O4 =
            TestUtils.getClientId("FI:GOV:O_4:SS1");
    private static final ClientId.Conf DELETED_CLIENT_ID_WITH_ORPHAN_CSR_O5 =
            TestUtils.getClientId("FI:GOV:O_5:SS1");
    private static final ClientId.Conf DELETED_CLIENT_ID_WITH_ORPHAN_CERT_O6 =
            TestUtils.getClientId("FI:GOV:O_6:SS1");
    private static final ClientId.Conf DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07 =
            TestUtils.getClientId("FI:GOV:O_7:SS1");
    private static final ClientId.Conf KEY_SHARING_CLIENT_07_08 =
            TestUtils.getClientId("FI:GOV:O_8:SS1");

    private static final List<ClientId.Conf> ALL_LOCAL_CLIENTS = Arrays.asList(
            NON_DELETED_CLIENT_ID_O1, SIBLING_CLIENT_ID_O3, KEY_SHARING_CLIENT_07_08
    );

    private static final String KEY_01_ID = "key1";
    private static final String ORPHAN_CSR_01_ID = "csr01-orphan";
    private static final String KEY_05_ID = "key5";
    private static final String ORPHAN_CSR_05_ID = "csr05-orphan";
    private static final String KEY_06_ID = "key6";
    private static final String ORPHAN_CERT_06_HASH = "csr06-orphan";
    private static final String KEY_07_SIGN_ORPHAN_1_ID = "key7-sign-orphan-1";
    private static final String KEY_07_SIGN_ORPHAN_2_ID = "key7-sign-orphan-2";
    private static final String KEY_07_SIGN_SHARED_ID = "key7-sign-shared";
    private static final String KEY_07_AUTH_ID = "key7-auth";
    private static final String ORPHAN_CERT_07_1_HASH = "cert07-1-orphan";
    private static final String ORPHAN_CERT_07_2_HASH = "cert07-2-orphan";
    private static final String ORPHAN_CSR_07_2_ID = "csr07-2-orphan";
    private static final String SHARED_KEY_CSR_07_ID = "csr07-shared-key";
    private static final String SHARED_KEY_CSR_08_ID = "csr08-shared-key";
    private static final String SHARED_KEY_CERT_07_1_HASH = "cert07-1-shared-key";
    private static final String SHARED_KEY_CERT_07_2_HASH = "cert07-2-shared-key";
    private static final String AUTH_CERT_07_HASH = "cert7-auth";

    @Before
    public void setup() throws Exception {
        KeyInfo key01 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_01_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(NON_DELETED_CLIENT_ID_O1)
                        .id(ORPHAN_CSR_01_ID)
                        .build())
                .build();

        KeyInfo key05 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_05_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_ORPHAN_CSR_O5)
                        .id(ORPHAN_CSR_05_ID)
                        .build())
                .build();

        KeyInfo key06 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_06_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .cert(new CertificateTestUtils.CertificateInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_ORPHAN_CERT_O6)
                        .id(ORPHAN_CERT_06_HASH)
                        .build())
                .build();

        KeyInfo key071 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_07_SIGN_ORPHAN_1_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .cert(new CertificateTestUtils.CertificateInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07)
                        .id(ORPHAN_CERT_07_1_HASH)
                        .build())
                .build();
        KeyInfo key072 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_07_SIGN_ORPHAN_2_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .cert(new CertificateTestUtils.CertificateInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07)
                        .id(ORPHAN_CERT_07_2_HASH)
                        .build())
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07)
                        .id(ORPHAN_CSR_07_2_ID)
                        .build())
                .build();
        KeyInfo key073 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_07_SIGN_SHARED_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .cert(new CertificateTestUtils.CertificateInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07)
                        .id(SHARED_KEY_CERT_07_1_HASH)
                        .build())
                .cert(new CertificateTestUtils.CertificateInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07)
                        .id(SHARED_KEY_CERT_07_2_HASH)
                        .build())
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07)
                        .id(SHARED_KEY_CSR_07_ID)
                        .build())
                // this is the only item stopping whole key from being deleted
                .csr(new CertificateTestUtils.CertRequestInfoBuilder()
                        .clientId(KEY_SHARING_CLIENT_07_08)
                        .id(SHARED_KEY_CSR_08_ID)
                        .build())
                .build();
        KeyInfo key074 = new TokenTestUtils.KeyInfoBuilder()
                .id(KEY_07_AUTH_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .cert(new CertificateTestUtils.CertificateInfoBuilder()
                        .id(AUTH_CERT_07_HASH)
                        .build())
                .build();

        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("fubar")
                .key(key01)
                .key(key05)
                .key(key06)
                .key(key071)
                .key(key072)
                .key(key073)
                .key(key074)
                .build();
        Map<String, KeyInfo> certCsrIdentifierToKey = new HashMap<>();
        // certs and csrs should not have duplicate ids/hashes
        tokenInfo.getKeyInfo().forEach(key -> key.getCerts().forEach(
                cert -> {
                    if (certCsrIdentifierToKey.containsKey(cert.getId())) throw new RuntimeException("duplicate");
                    certCsrIdentifierToKey.put(cert.getId(), key);
                }));
        tokenInfo.getKeyInfo().forEach(key -> key.getCertRequests().forEach(
                csr -> {
                    if (certCsrIdentifierToKey.containsKey(csr.getId())) throw new RuntimeException("duplicate");
                    certCsrIdentifierToKey.put(csr.getId(), key);
                }));

        doReturn(Collections.singletonList(tokenInfo)).when(signerProxyFacade).getTokens();
        Map<ClientId, ClientType> localClients = new HashMap<>();
        ALL_LOCAL_CLIENTS.forEach(id -> {
            ClientType clientType = new ClientType();
            clientType.setIdentifier(id);
            localClients.put(id, clientType);
        });
        doReturn(new ArrayList(localClients.values())).when(clientRepository).getAllLocalClients();
        doAnswer(invocation -> {
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            return localClients.get(clientId);
        }).when(clientRepository).getClient(any());
        doReturn(tokenInfo).when(signerProxyFacade).getTokenForKeyId(any());
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            return new TokenInfoAndKeyId(tokenInfo,
                    certCsrIdentifierToKey.get(certHash).getId());
        }).when(signerProxyFacade).getTokenAndKeyIdForCertHash(any());
        doAnswer(invocation -> {
            String csrId = (String) invocation.getArguments()[0];
            return new TokenInfoAndKeyId(tokenInfo,
                    certCsrIdentifierToKey.get(csrId).getId());
        }).when(signerProxyFacade).getTokenAndKeyIdForCertRequestId(any());
    }

    @Test
    public void isOrphanKey() {
        ClientId.Conf orphanMember = TestUtils.getClientId("FI:GOV:ORPHAN");
        ClientId.Conf orphanSubsystemDeleted = TestUtils.getClientId("FI:GOV:ORPHAN:DELETED");
        ClientId.Conf orphanSubsystemAlive = TestUtils.getClientId("FI:GOV:ORPHAN:ALIVE");
        ClientId.Conf aliveMember = TestUtils.getClientId("FI:GOV:ALIVE");

        CertificateInfo orphanMemberCert =
                new CertificateTestUtils.CertificateInfoBuilder().clientId(orphanMember).build();
        CertRequestInfo orphanMemberCsr =
                new CertificateTestUtils.CertRequestInfoBuilder().clientId(orphanMember).build();
        CertificateInfo orphanSubstemAliveCert =
                new CertificateTestUtils.CertificateInfoBuilder().clientId(orphanSubsystemAlive).build();
        CertificateInfo aliveMemberCert =
                new CertificateTestUtils.CertificateInfoBuilder().clientId(aliveMember).build();

        assertTrue(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder().cert(orphanMemberCert).build(),
                orphanSubsystemDeleted));
        assertFalse(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder().cert(orphanMemberCert).build(),
                aliveMember));
        assertTrue(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder().csr(orphanMemberCsr).build(),
                orphanSubsystemDeleted));
        assertFalse(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder().build(),
                orphanSubsystemDeleted));
        assertTrue(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder()
                        .cert(orphanMemberCert)
                        .cert(orphanSubstemAliveCert)
                        .csr(orphanMemberCsr)
                        .build(),
                orphanSubsystemDeleted));

        assertFalse(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder()
                        .cert(orphanMemberCert)
                        .cert(orphanSubstemAliveCert)
                        .cert(aliveMemberCert)
                        .csr(orphanMemberCsr)
                        .build(),
                orphanSubsystemDeleted));
        assertFalse(orphanRemovalService.isOrphanKey(
                new TokenTestUtils.KeyInfoBuilder()
                        .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                        .cert(new CertificateTestUtils.CertificateInfoBuilder().build())
                        .build(),
                orphanSubsystemDeleted));
    }

    @Test
    public void orphansDontExistsIfClientExists() {
        // trying to find orphans for client which still exists
        assertFalse(orphanRemovalService.orphansExist(NON_DELETED_CLIENT_ID_O1));
    }

    @Test
    public void orphansDontExistsIfSiblings() {
        // if there are other clients with same memberclass + code
        assertFalse(orphanRemovalService.orphansExist(DELETED_CLIENT_ID_WITH_SIBLINGS_O3));
    }

    @Test
    public void orphansDontExistIfNoOrphanItems() {
        // there are no orphan keys, certs or csrs
        assertFalse(orphanRemovalService.orphansExist(DELETED_CLIENT_ID_WITHOUT_ORPHAN_ITEMS_O4));
    }

    @Test
    public void orphansExistIfCsr() {
        assertTrue(orphanRemovalService.orphansExist(DELETED_CLIENT_ID_WITH_ORPHAN_CSR_O5));
    }

    @Test
    public void orphansExistIfCert() {
        assertTrue(orphanRemovalService.orphansExist(DELETED_CLIENT_ID_WITH_ORPHAN_CERT_O6));
    }

    @Test
    public void orphansForClientWithMultipleKeys() {
        // client 7 has:
        // KEY_07_SIGN_ORPHAN_1_ID has only orphans for this client
        // KEY_07_SIGN_ORPHAN_2_ID has only orphans for this client
        // KEY_07_SIGN_SHARED_ID has items for this client and another
        // -> key is not orphan, but contains orphan items
        // KEY_07_AUTH_ID is auth key and items are not linked to any clients
        assertTrue(orphanRemovalService.orphansExist(DELETED_CLIENT_ID_WITH_ORPHAN_CERT_O6));
        assertFalse(orphanRemovalService.orphansExist(KEY_SHARING_CLIENT_07_08));
        OrphanRemovalService.Orphans orphans = orphanRemovalService
                .findOrphans(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07);
        KeyInfo orphanKey1 = findKey(KEY_07_SIGN_ORPHAN_1_ID, orphans.getKeys());
        KeyInfo orphanKey2 = findKey(KEY_07_SIGN_ORPHAN_2_ID, orphans.getKeys());
        KeyInfo orphanKey3 = findKey(KEY_07_SIGN_SHARED_ID, orphans.getKeys());
        KeyInfo orphanKey4 = findKey(KEY_07_AUTH_ID, orphans.getKeys());
        assertNotNull(orphanKey1);
        assertNotNull(orphanKey2);
        assertNull(orphanKey3);
        assertNull(orphanKey4);
        assertNotNull(findCert(SHARED_KEY_CERT_07_1_HASH, orphans.getCerts()));
        assertNotNull(findCert(SHARED_KEY_CERT_07_2_HASH, orphans.getCerts()));
        assertNotNull(findCsr(SHARED_KEY_CSR_07_ID, orphans.getCsrs()));
        assertEquals(2, orphans.getKeys().size());
        assertEquals(2, orphans.getCerts().size());
        assertEquals(1, orphans.getCsrs().size());
    }

    private KeyInfo findKey(String id, List<KeyInfo> keys) {
        return keys.stream().filter(key -> key.getId().equals(id))
                .findFirst().orElse(null);
    }

    private CertificateInfo findCert(String id, List<CertificateInfo> certs) {
        return certs.stream().filter(cert -> cert.getId().equals(id))
                .findFirst().orElse(null);
    }

    private CertRequestInfo findCsr(String id, List<CertRequestInfo> csrs) {
        return csrs.stream().filter(csr -> csr.getId().equals(id))
                .findFirst().orElse(null);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY"})
    public void cantDeleteOrphansForDifferentReasons() throws Exception {
        // client exists
        try {
            orphanRemovalService.deleteOrphans(NON_DELETED_CLIENT_ID_O1);
            fail("should throw exception");
        } catch (OrphanRemovalService.OrphansNotFoundException expected) {
        }

        // siblings exist
        try {
            orphanRemovalService.deleteOrphans(DELETED_CLIENT_ID_WITH_SIBLINGS_O3);
            fail("should throw exception");
        } catch (OrphanRemovalService.OrphansNotFoundException expected) {
        }

        // siblings dont exist, but there is no orphan data
        // siblings exist
        try {
            orphanRemovalService.deleteOrphans(DELETED_CLIENT_ID_WITHOUT_ORPHAN_ITEMS_O4);
            fail("should throw exception");
        } catch (OrphanRemovalService.OrphansNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = {"DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY"})
    public void deleteOrphanCsrKey() throws Exception {
        // single orphan csr -> key is deleted
        orphanRemovalService.deleteOrphans(DELETED_CLIENT_ID_WITH_ORPHAN_CSR_O5);
        // allow some needed getters
        verify(signerProxyFacade, atLeast(0)).getTokens();
        verify(signerProxyFacade, atLeast(0)).getTokenForKeyId(any());
        // updates: delete key (twice)
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_05_ID, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_05_ID, false);
        // no more interactions
        verifyNoMoreInteractions(signerProxyFacade);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY"})
    public void deleteOrphanCertKey() throws Exception {
        // single orphan cert -> key is deleted
        orphanRemovalService.deleteOrphans(DELETED_CLIENT_ID_WITH_ORPHAN_CERT_O6);
        // allow some needed getters
        verify(signerProxyFacade, atLeast(0)).getTokens();
        verify(signerProxyFacade, atLeast(0)).getTokenForKeyId(any());
        // updates: delete key (twice)
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_06_ID, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_06_ID, false);
        // no more interactions
        verifyNoMoreInteractions(signerProxyFacade);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_AUTH_KEY", "DELETE_SIGN_KEY", "DELETE_KEY", "DELETE_SIGN_CERT"})
    public void deleteComplexOrphanSetup() throws Exception {
        // combination of orphan keys and shared keys
        orphanRemovalService.deleteOrphans(DELETED_CLIENT_ID_WITH_MULTIPLE_KEYS_07);
        // allow some needed getters
        verify(signerProxyFacade, atLeast(0)).getTokens();
        verify(signerProxyFacade, atLeast(0)).getTokenForKeyId(any());
        verify(signerProxyFacade, atLeast(0)).getTokenAndKeyIdForCertRequestId(any());

        // verify updates (deletes)
        // keys KEY_07_SIGN_ORPHAN_1_ID and KEY_07_SIGN_ORPHAN_2_ID only contain this
        // client's orphans, so the full keys will be deleted
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_07_SIGN_ORPHAN_1_ID, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_07_SIGN_ORPHAN_1_ID, false);
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_07_SIGN_ORPHAN_2_ID, true);
        verify(signerProxyFacade, times(1))
                .deleteKey(KEY_07_SIGN_ORPHAN_2_ID, false);
        // key KEY_07_SIGN_SHARED_ID has orphan csr for this client,
        // orphan certs for this client, and a csr for other client
        verify(signerProxyFacade, times(1)).deleteCert(SHARED_KEY_CERT_07_1_HASH);
        verify(signerProxyFacade, times(1)).deleteCert(SHARED_KEY_CERT_07_2_HASH);
        verify(signerProxyFacade, times(1)).deleteCertRequest(SHARED_KEY_CSR_07_ID);

        verifyNoMoreInteractions(signerProxyFacade);
    }
}
