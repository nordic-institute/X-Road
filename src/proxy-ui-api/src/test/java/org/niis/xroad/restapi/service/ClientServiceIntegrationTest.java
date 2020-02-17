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

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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
import java.util.Set;

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
@AutoConfigureTestDatabase
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

    @Before
    public void setup() throws Exception {
        when(globalConfFacade.getMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM2),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
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
                        null))
        ));
        when(globalConfFacade.getMemberName(any())).thenAnswer(invocation -> {
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            return clientId.getSubsystemCode() != null ? TestUtils.NAME_FOR + clientId.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
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
        return countByType(false);
    }
    private long countByType(boolean subsystem) {
        List<ClientType> localClients = clientService.getAllLocalClients();
        return localClients.stream()
                .filter(client -> (client.getIdentifier().getSubsystemCode() == null) == subsystem)
                .count();
    }

    @Test
    public void addSubsystemToExistingClient() throws Exception {

        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // add local subsystem: add SS-NEW to M1
        clientService.addLocalClient(TestUtils.getClientId("FI:GOV:M1:SS-NEW"),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());

        // add global subsystem: add FI:GOV:M2:SS3, which exists in global conf but not serverconf
        clientService.addLocalClient(TestUtils.getClientId("FI:GOV:M2:SS3"),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers, countMembers());
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 2, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());
    }

    @Test
    public void addSecondMember() throws Exception {

        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // add second member FI:GOV:M2
        clientService.addLocalClient(TestUtils.getClientId("FI:GOV:M2"),
                IsAuthentication.SSLAUTH, false);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());

        // add third member FI:GOV:M3 fails
        try {
            clientService.addLocalClient(TestUtils.getClientId("FI:GOV:M3"),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.AdditionalMemberAlreadyExistsException");
        } catch (ClientService.AdditionalMemberAlreadyExistsException expected) {
        }
    }

    @Test
    public void addExistingClientFails() throws Exception {
        // try member, FI:GOV:M1
        try {
            clientService.addLocalClient(TestUtils.getClientId("FI:GOV:M1"),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.ClientAlreadyExistsException");
        } catch (ClientService.ClientAlreadyExistsException expected) {
        }

        // and subsystem, FI:GOV:M1:SS1
        try {
            clientService.addLocalClient(TestUtils.getClientId("FI:GOV:M1:SS1"),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.ClientAlreadyExistsException");
        } catch (ClientService.ClientAlreadyExistsException expected) {
        }
    }

    @Test
    public void addUnregisteredMemberClients() throws Exception {
        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // unregistered member without skip warnings
        // add third member FI:GOV:M3 fails
        try {
            clientService.addLocalClient(TestUtils.getClientId("FI:GOV:UNREGISTERED-MX"),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown UnhandledWarningsException");
        } catch (UnhandledWarningsException expected) {
        }
        // unregistered member + subsystem without skip warnings
        try {
            clientService.addLocalClient(TestUtils.getClientId("FI:GOV:UNREGISTERED-MX:SS1"),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown UnhandledWarningsException");
        } catch (UnhandledWarningsException expected) {
        }
        // unregistered member with skip warnings
        clientService.addLocalClient(TestUtils.getClientId("FI:GOV:UNREGISTERED-MX"),
                IsAuthentication.SSLAUTH, true);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers + 1, countIdentifiers());

        // unregistered members subsystem with skip warnings
        clientService.addLocalClient(TestUtils.getClientId("FI:GOV:UNREGISTERED-MX:SS1"),
                IsAuthentication.SSLAUTH, true);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());

        // subsystem for a different unregistered member
        clientService.addLocalClient(TestUtils.getClientId("FI:GOV:UNREGISTERED-MY:SS1"),
                IsAuthentication.SSLAUTH, true);
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 2, countSubsystems());
        assertEquals(startIdentifiers + 3, countIdentifiers());
    }

    @Test
    public void getAllLocalMembers() {
        List<ClientType> localMembers = clientService.getAllLocalMembers();
        assertEquals(1, localMembers.size());
        assertEquals(1, (long)localMembers.iterator().next().getId());
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
        assertEquals(2, clients.size());
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
        assertEquals(3, clients.size());
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
        assertEquals(2, clients.size());
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
        assertEquals(2, clients.size());
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
}
