/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_REGISTERED;
import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_SAVED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * test client service
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@Slf4j
@Transactional
@WithMockUser
public class ClientServiceIntegrationTest {

    @Autowired
    private ClientService clientService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private byte[] pemBytes;
    private byte[] derBytes;
    private byte[] sqlFileBytes;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    private ClientId existingSavedClientId = ClientId.create("FI", "GOV", "M2", "SS6");
    private ClientId existingRegisteredClientId = ClientId.create("FI", "GOV", "M1", "SS1");
    private ClientId ownerClientId = ClientId.create("FI", "GOV", "M1", null);

    @MockBean
    private ManagementRequestSenderService managementRequestSenderService;

    @Before
    public void setup() throws Exception {
        List<MemberInfo> globalMemberInfos = new ArrayList<>(Arrays.asList(
                // exists in serverconf
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                // exists in serverconf
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM2),
                // exists in serverconf
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M3,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M3,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M2,
                        TestUtils.SUBSYSTEM3),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M2,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M3,
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
        when(managementRequestSenderService.sendClientRegisterRequest(any())).thenReturn(1);
        when(globalConfFacade.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfFacade.isSecurityServerClient(any(), any())).thenAnswer(invocation -> {
            // mock isSecurityServerClient: it is a client, if it exists in DB / getAllLocalClients
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            Optional<ClientType> match = clientService.getAllLocalClients()
                    .stream()
                    .filter(ct -> ct.getIdentifier().equals(clientId))
                    .findFirst();
            return match.isPresent();
        });

        pemBytes = IOUtils.toByteArray(this.getClass().getClassLoader().
                getResourceAsStream("google-cert.pem"));
        derBytes = IOUtils.toByteArray(this.getClass().getClassLoader().
                getResourceAsStream("google-cert.der"));
        sqlFileBytes = IOUtils.toByteArray(this.getClass().getClassLoader().
                getResourceAsStream("data.sql"));
        assertTrue(pemBytes.length > 1);
        assertTrue(derBytes.length > 1);
        assertTrue(sqlFileBytes.length > 1);
    }

    private int countIdentifiers() {
        return JdbcTestUtils.countRowsInTable(jdbcTemplate, "identifier");
    }

    private long countMembers() {
        return countByType(false);
    }

    private long countSubsystems() {
        return countByType(true);
    }

    private long countByType(boolean subsystems) {
        List<ClientType> localClients = clientService.getAllLocalClients();
        return localClients.stream()
                .filter(client -> (client.getIdentifier().getSubsystemCode() != null) == subsystems)
                .count();
    }

    @Test
    public void addLocalClientSubsystemToExistingClient() throws Exception {

        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();
        ClientType added;

        // add local subsystem: add SS-NEW to M1
        ClientId id = TestUtils.getClientId("FI:GOV:M1:SS-NEW");
        added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());
        assertEquals(TestUtils.INSTANCE_FI, added.getIdentifier().getXRoadInstance());
        assertEquals(STATUS_SAVED, added.getClientStatus());

        // add global subsystem: add FI:GOV:M3:SS1, which exists in global conf but not serverconf
        id = TestUtils.getClientId("FI:GOV:M3:SS1");
        added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 2, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());
        assertEquals(STATUS_SAVED, added.getClientStatus());
    }

    @Test
    public void addLocalClientSecondMember() throws Exception {

        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // add second member FI:GOV:M2
        ClientId id = TestUtils.getClientId("FI:GOV:M2");
        ClientType added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());
        assertEquals(STATUS_SAVED, added.getClientStatus());

        // add third member FI:GOV:M3 fails
        try {
            id = TestUtils.getClientId("FI:GOV:M3");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.AdditionalMemberAlreadyExistsException");
        } catch (ClientService.AdditionalMemberAlreadyExistsException expected) {
        }
    }

    @Test
    public void addLocalClientDuplicateFails() throws Exception {
        // try member, FI:GOV:M1
        try {
            ClientId id = TestUtils.getClientId("FI:GOV:M1");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.ClientAlreadyExistsException");
        } catch (ClientService.ClientAlreadyExistsException expected) {
        }

        // and subsystem, FI:GOV:M1:SS1
        try {
            ClientId id = TestUtils.getClientId("FI:GOV:M1:SS1");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.ClientAlreadyExistsException");
        } catch (ClientService.ClientAlreadyExistsException expected) {
        }
    }

    @Test
    public void addLocalClientAlreadyLinkedToThisSecurityServer() throws Exception {
        // add clients who global conf says are already linked to this security server
        // this can occur (at least) if admin deletes a client from serverconf, but does not send
        // unregistration request to remove clients from globalconf
        when(globalConfFacade.isSecurityServerClient(any(), any())).thenReturn(true);
        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();
        ClientId id = TestUtils.getClientId("FI:GOV:M3");
        ClientType added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        // these should have status "REGISTERED"
        assertEquals(STATUS_REGISTERED, added.getClientStatus());
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());

        id = TestUtils.getClientId("FI:GOV:M3:SS1");
        added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(STATUS_REGISTERED, added.getClientStatus());
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());
    }

    @Test
    public void addLocalClientUnregisteredMember() throws Exception {
        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // unregistered member without skip warnings
        try {
            ClientId id = TestUtils.getClientId("FI:GOV:UNREGISTERED-MX");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown UnhandledWarningsException");
        } catch (UnhandledWarningsException expected) {
        }

        // unregistered member + subsystem without skip warnings
        try {
            ClientId id = TestUtils.getClientId("FI:GOV:UNREGISTERED-MX:SS1");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown UnhandledWarningsException");
        } catch (UnhandledWarningsException expected) {
        }

        // unregistered member with skip warnings
        ClientId id = TestUtils.getClientId("FI:GOV:UNREGISTERED-MX");
        clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, true);

        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());

        // unregistered members subsystem with skip warnings
        id = TestUtils.getClientId("FI:GOV:UNREGISTERED-MX:SS1");
        clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, true);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());

        // subsystem for a different unregistered member
        id = TestUtils.getClientId("FI:GOV:UNREGISTERED-MY:SS1");
        clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, true);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 2, countSubsystems());
        assertEquals(startIdentifiers + 3, countIdentifiers());
    }

    @Test
    public void addLocalClientWhenIdentifierExists() throws Exception {
        // client identifier may already exists in DB, even though client does
        // not exist in this security server.
        // this can happen (at least) when a client is deleted and then
        // added again. Identifier is kept when client is deleted
        int dataSqlIdentifiers = countIdentifiers();
        jdbcTemplate.execute("INSERT INTO IDENTIFIER"
                + "(id, discriminator, type, x_road_instance, member_class, member_code, subsystem_code)"
                + " values (1000, 'C', 'MEMBER', 'FI', 'GOV', 'M-DELETED', null)");
        jdbcTemplate.execute("INSERT INTO IDENTIFIER"
                + "(id, discriminator, type, x_road_instance, member_class, member_code, subsystem_code)"
                + " values (1001, 'C', 'SUBSYSTEM', 'FI', 'GOV', 'M-DELETED2', 'SS-DELETED')");
        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();
        assertEquals(dataSqlIdentifiers + 2, startIdentifiers);

        // unregistered member with skip warnings
        ClientId id = TestUtils.getClientId("FI:GOV:M-DELETED");
        clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, true);

        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers, countIdentifiers());

        // unregistered member's subsystem with skip warnings
        id = TestUtils.getClientId("FI:GOV:M-DELETED2:SS-DELETED");
        clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, true);

        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers, countIdentifiers());
    }

    @Test
    public void getAllLocalMembers() {
        List<ClientType> localMembers = clientService.getAllLocalMembers();
        assertEquals(1, localMembers.size());
        assertEquals(1, (long) localMembers.iterator().next().getId());
    }

    /**
     * Test how IsAuthentication and also other properties behave when adding a new client,
     * and compare returned ClientType to one fetched separately so that they match
     * @throws Exception
     */
    @Test
    public void addSubsystemIsAuthenticationAndOtherProperties() throws Exception {

        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();
        ClientType added;
        ClientType loadedAdded;

        // add local subsystem
        ClientId id = TestUtils.getClientId("FI:GOV:M1:SS-NEW-SSLAUTH");
        added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());

        assertEquals(IsAuthentication.SSLAUTH.name(), added.getIsAuthentication());
        assertEquals(STATUS_SAVED, added.getClientStatus());
        loadedAdded = clientService.getLocalClient(TestUtils.getClientId(
                "FI:GOV:M1:SS-NEW-SSLAUTH"));
        assertEquals(IsAuthentication.SSLAUTH.name(), loadedAdded.getIsAuthentication());
        assertEquals(STATUS_SAVED, loadedAdded.getClientStatus());

        // add local subsystem
        id = TestUtils.getClientId("FI:GOV:M1:SS-NEW-NOSSL");
        added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.NOSSL, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 2, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());

        assertEquals(IsAuthentication.NOSSL.name(), added.getIsAuthentication());
        loadedAdded = clientService.getLocalClient(TestUtils.getClientId(
                "FI:GOV:M1:SS-NEW-NOSSL"));
        assertEquals(IsAuthentication.NOSSL.name(), loadedAdded.getIsAuthentication());

        // add local subsystem
        id = TestUtils.getClientId("FI:GOV:M1:SS-NEW-SSLNOAUTH");
        added = clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                IsAuthentication.SSLNOAUTH, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 3, countSubsystems());
        assertEquals(startIdentifiers + 3, countIdentifiers());

        assertEquals(IsAuthentication.SSLNOAUTH.name(), added.getIsAuthentication());
        loadedAdded = clientService.getLocalClient(TestUtils.getClientId(
                "FI:GOV:M1:SS-NEW-SSLNOAUTH"));
        assertEquals(IsAuthentication.SSLNOAUTH.name(), loadedAdded.getIsAuthentication());

    }

    @Test
    public void updateConnectionType() throws Exception {
        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getLocalClient(id);
        assertEquals("SSLNOAUTH", clientType.getIsAuthentication());
        assertEquals(2, clientType.getLocalGroup().size());

        try {
            clientService.updateConnectionType(id, "FUBAR");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        clientService.updateConnectionType(id, "NOSSL");
        clientType = clientService.getLocalClient(id);
        assertEquals("NOSSL", clientType.getIsAuthentication());
        assertEquals(2, clientType.getLocalGroup().size());
    }

    @Test
    public void addCertificatePem() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getLocalClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, pemBytes);

        clientType = clientService.getLocalClient(id);
        assertEquals(1, clientType.getIsCert().size());
        assertTrue(Arrays.equals(derBytes, clientType.getIsCert().get(0).getData()));
    }

    @Test
    public void addInvalidCertificate() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getLocalClient(id);
        assertEquals(0, clientType.getIsCert().size());

        try {
            clientService.addTlsCertificate(id, sqlFileBytes);
            fail("should have thrown CertificateException");
        } catch (CertificateException expected) {
        }
    }

    @Test
    public void addCertificateDer() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getLocalClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);

        clientType = clientService.getLocalClient(id);
        assertEquals(1, clientType.getIsCert().size());
        assertTrue(Arrays.equals(derBytes, clientType.getIsCert().get(0).getData()));
    }

    @Test
    public void addDuplicate() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getLocalClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);

        try {
            clientService.addTlsCertificate(id, pemBytes);
            fail("should have thrown CertificateAlreadyExistsException");
        } catch (CertificateAlreadyExistsException expected) {
        }
    }

    @Test
    public void deleteCertificate() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getLocalClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);
        String hash = CryptoUtils.calculateCertHexHash(derBytes);

        try {
            clientService.deleteTlsCertificate(id, "wrong hash");
            fail("should have thrown CertificateNotFoundException");
        } catch (CertificateNotFoundException expected) {
        }
        clientType = clientService.getLocalClient(id);
        assertEquals(1, clientType.getIsCert().size());

        clientService.deleteTlsCertificate(id, hash);
        clientType = clientService.getLocalClient(id);
        assertEquals(0, clientType.getIsCert().size());
    }

    /* Test LOCAL client search */
    @Test
    public void findLocalClientsByNameIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1, null,
                null,
                null, null, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, TestUtils.INSTANCE_FI, null,
                null, null, true);
        assertEquals(5, clients.size());
    }

    @Test
    public void findLocalClientsByClassIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, null, TestUtils.MEMBER_CLASS_GOV,
                null, null, true);
        assertEquals(5, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, TestUtils.INSTANCE_FI, null,
                TestUtils.MEMBER_CODE_M1, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByNameExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1, null,
                null,
                null, null, false);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, TestUtils.INSTANCE_FI, null,
                null, null, false);
        assertEquals(4, clients.size());
    }

    @Test
    public void findLocalClientsByClassExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, null, TestUtils.MEMBER_CLASS_GOV,
                null, null, false);
        assertEquals(4, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, TestUtils.INSTANCE_FI, null,
                TestUtils.MEMBER_CODE_M1, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1, false);
        assertEquals(1, clients.size());
    }

    /* Test GLOBAL client search */
    @Test
    public void findGlobalClientsByNameIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1, null,
                null,
                null, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, TestUtils.INSTANCE_EE, null,
                null, null, true);
        assertEquals(5, clients.size());
    }

    @Test
    public void findGlobalClientsByClassIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, null, TestUtils.MEMBER_CLASS_GOV,
                null, null, true);
        assertEquals(6, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceAndMemberCodeIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, TestUtils.INSTANCE_FI, null,
                TestUtils.MEMBER_CODE_M1, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByAllTermsIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findGlobalClientsByNameExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1, null,
                null,
                null, null, false);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, TestUtils.INSTANCE_EE, null,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByClassExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, null, TestUtils.MEMBER_CLASS_GOV,
                null, null, false);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceAndMemberCodeExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, TestUtils.INSTANCE_FI, null,
                TestUtils.MEMBER_CODE_M1, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByAllTermsExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1,
                TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1, false);
        assertEquals(1, clients.size());
    }

    @Test
    public void getLocalClientMemberIds() {
        Set<ClientId> expected = new HashSet();
        expected.add(ClientId.create(TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1));
        expected.add(ClientId.create(TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2));
        Set<ClientId> result = clientService.getLocalClientMemberIds();
        assertEquals(expected, result);
    }

    @Test
    public void registerClient() throws Exception {
        ClientType clientType = clientService.getLocalClient(existingSavedClientId);
        assertEquals(ClientType.STATUS_SAVED, clientType.getClientStatus());
        clientService.registerClient(existingSavedClientId);
        clientType = clientService.getLocalClient(existingSavedClientId);
        assertEquals(ClientType.STATUS_REGINPROG, clientType.getClientStatus());
    }

    @Test(expected = CodedException.class)
    public void registerClientCodedException() throws Exception {
        when(managementRequestSenderService.sendClientRegisterRequest(any())).thenThrow(CodedException.class);
        clientService.registerClient(existingSavedClientId);
    }

    @Test(expected = DeviationAwareRuntimeException.class)
    public void registerClientRuntimeException() throws Exception {
        when(managementRequestSenderService.sendClientRegisterRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(new Exception()));
        clientService.registerClient(existingSavedClientId);
    }

    @Test
    public void unregisterClient() throws Exception {
        ClientType clientType = clientService.getLocalClient(existingRegisteredClientId);
        assertEquals(ClientType.STATUS_REGISTERED, clientType.getClientStatus());
        clientService.unregisterClient(existingRegisteredClientId);
        clientType = clientService.getLocalClient(existingRegisteredClientId);
        assertEquals(ClientType.STATUS_DELINPROG, clientType.getClientStatus());
    }

    @Test(expected = ActionNotPossibleException.class)
    public void unregisterClientNotPossible() throws Exception {
        ClientType clientType = clientService.getLocalClient(existingSavedClientId);
        assertEquals(ClientType.STATUS_SAVED, clientType.getClientStatus());
        clientService.unregisterClient(existingSavedClientId);
    }

    @Test(expected = ClientService.CannotUnregisterOwnerException.class)
    public void unregisterOwnerClient() throws Exception {
        clientService.unregisterClient(ownerClientId);
    }

    @Test(expected = CodedException.class)
    public void unregisterClientCodedException() throws Exception {
        when(managementRequestSenderService.sendClientRegisterRequest(any())).thenThrow(CodedException.class);
        clientService.registerClient(existingRegisteredClientId);
    }

    @Test(expected = DeviationAwareRuntimeException.class)
    public void unregisterClientRuntimeException() throws Exception {
        when(managementRequestSenderService.sendClientRegisterRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(new Exception()));
        clientService.registerClient(existingRegisteredClientId);
    }
}
