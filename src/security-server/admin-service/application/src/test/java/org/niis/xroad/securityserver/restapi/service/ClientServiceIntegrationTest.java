/**
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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.jdbc.JdbcTestUtils;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_DELINPROG;
import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_GLOBALERR;
import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_REGINPROG;
import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_REGISTERED;
import static ee.ria.xroad.common.conf.serverconf.model.ClientType.STATUS_SAVED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * test client service
 */
public class ClientServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    ClientService clientService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private byte[] pemBytes;
    private byte[] derBytes;
    private byte[] sqlFileBytes;

    private ClientId.Conf existingSavedClientId = ClientId.Conf.create("FI", "GOV", "M2", "SS6");
    private ClientId.Conf existingRegisteredClientId = ClientId.Conf.create("FI", "GOV", "M1", "SS1");
    private ClientId.Conf ownerClientId = ClientId.Conf.create("FI", "GOV", "M1", null);
    private ClientId.Conf newOwnerClientId = ClientId.Conf.create("FI", "GOV", "M2", null);

    private static final List<String> MEMBER_CLASSES = Arrays.asList(TestUtils.MEMBER_CLASS_GOV,
            TestUtils.MEMBER_CLASS_PRO);

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
        when(globalConfService.getMemberClassesForThisInstance()).thenReturn(new HashSet<>(MEMBER_CLASSES));
        when(globalConfFacade.isSecurityServerClient(any(), any())).thenAnswer(invocation -> {
            // mock isSecurityServerClient: it is a client, if it exists in DB / getAllLocalClients
            ClientId clientId = (ClientId) invocation.getArguments()[0];
            Optional<ClientType> match = clientService.getAllLocalClients()
                    .stream()
                    .filter(ct -> ct.getIdentifier().equals(clientId))
                    .findFirst();
            return match.isPresent();
        });

        when(managementRequestSenderService.sendClientRegisterRequest(any())).thenReturn(1);
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

    /**
     * - FI:GOV:M1 has a sign cert "cert1" with ocsp status GOOD
     * - FI:GOV:M1 has a sign cert "cert2" with ocsp status REVOKED
     * - FI:GOV:M2 has a sign cert "cert3" with ocsp status UNKNOWN
     */
    private List<CertificateInfo> createSimpleSignCertList() {

        CertificateTestUtils.CertificateInfoBuilder certificateInfoBuilder =
                new CertificateTestUtils.CertificateInfoBuilder();

        // Create cert with good ocsp response status
        // This certificate is valid for all subsystems owned by the member "FI:GOV:M1".
        ClientId.Conf clientId1 = ClientId.Conf.create("FI", "GOV", "M1");
        certificateInfoBuilder.clientId(clientId1);
        CertificateInfo cert1 = certificateInfoBuilder.build();

        // Create cert with revoked ocsp response status
        // N.B. This cert is ignored, and FI:GOV:M1 is considered to have valid sign cert since there's also a valid one
        ClientId.Conf clientId2 = ClientId.Conf.create("FI", "GOV", "M1");
        certificateInfoBuilder.clientId(clientId2).ocspStatus(new RevokedStatus(new Date(), CRLReason.certificateHold));
        CertificateInfo cert2 = certificateInfoBuilder.build();

        // Create cert with unknown ocsp response status
        ClientId.Conf clientId3 = ClientId.Conf.create("FI", "GOV", "M2");
        certificateInfoBuilder.clientId(clientId3).ocspStatus(new UnknownStatus());
        CertificateInfo cert3 = certificateInfoBuilder.build();

        return Arrays.asList(cert2, cert3, cert1);
    }

    /**
     * local sign certificates for local clients:
     * - FI:GOV:M1 has a sign cert "cert1" with ocsp status GOOD
     * - FI:GOV:M1 has a sign cert "cert2" with ocsp status REVOKED
     * ---> FI:GOV:M1 has both GOOD and REVOKED certs
     * - FI:GOV:M2 has a sign cert "cert3" with ocsp status UNKNOWN
     * - FI:DUMMY:M2 has a sign cert "cert4" with ocsp status REVOKED
     * - DUMMY:PRO:M2 does not have any sign certs
     *
     * local sign certificates for global-only clients (not local clients of this SS):
     * - EE:PRO:M1 has a sign cert "cert5" with ocsp status GOOD
     * - EE:PRO:M2 has a sign cert "cert6" with ocsp status REVOKED
     * - EE:PRO:M3 does not have any sign certs
     */
    private List<CertificateInfo> createComplexSignCertList() {

        // FI:GOV:M1 has a sign cert "cert1" with ocsp status GOOD
        ClientId.Conf clientIdFiGovM1 = ClientId.Conf.create("FI", "GOV", "M1");
        CertificateInfo cert1 = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(clientIdFiGovM1)
                .build();

        // FI:GOV:M1 has a sign cert "cert2" with ocsp status REVOKED
        CertificateInfo cert2 = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(clientIdFiGovM1)
                .ocspStatus(new RevokedStatus(new Date(), CRLReason.certificateHold))
                .build();

        // FI:GOV:M2 has a sign cert "cert3" with ocsp status UNKNOWN
        CertificateInfo cert3 = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(ClientId.Conf.create("FI", "GOV", "M2"))
                .ocspStatus(new UnknownStatus())
                .build();

        // FI:DUMMY:M2 has a sign cert "cert4" with ocsp status REVOKED
        CertificateInfo cert4 = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(ClientId.Conf.create("FI", "DUMMY", "M2"))
                .ocspStatus(new RevokedStatus(new Date(), CRLReason.certificateHold))
                .build();

        // DUMMY:PRO:M2 does not have any sign certs

        // EE:PRO:M1 has a sign cert "cert5" with ocsp status GOOD
        CertificateInfo cert5 = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(ClientId.Conf.create("EE", "PRO", "M1"))
                .build();

        // EE:PRO:M2 has a sign cert "cert6" with ocsp status REVOKED
        CertificateInfo cert6 = new CertificateTestUtils.CertificateInfoBuilder()
                .clientId(ClientId.Conf.create("EE", "PRO", "M2"))
                .ocspStatus(new RevokedStatus(new Date(), CRLReason.certificateHold))
                .build();

        // EE:PRO:M3 does not have any sign certs

        return Arrays.asList(cert1, cert2, cert3, cert4, cert5, cert6);
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
    public void deleteLocalClient() throws Exception {
        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // setup: create a new member and a subsystem
        // second member FI:GOV:M3, subsystem FI:GOV:M3:SS-NEW
        ClientId memberId = TestUtils.getClientId("FI:GOV:M3");
        ClientId subsystemId = TestUtils.getClientId("FI:GOV:M3:SS-NEW");
        ClientType addedMember = clientService.addLocalClient(memberId.getMemberClass(), memberId.getMemberCode(),
                memberId.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);

        assertEquals(STATUS_SAVED, addedMember.getClientStatus());
        ClientType addedSubsystem = clientService.addLocalClient(subsystemId.getMemberClass(),
                subsystemId.getMemberCode(), subsystemId.getSubsystemCode(),
                IsAuthentication.SSLAUTH, false);
        assertEquals(STATUS_SAVED, addedSubsystem.getClientStatus());
        assertEquals(startMembers + 1, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());

        // delete unregistered member
        clientService.deleteLocalClient(memberId);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems + 1, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());
        assertNull(clientService.getLocalClient(memberId));
        // subsystems are not affected
        assertNotNull(clientService.getLocalClient(subsystemId));

        // delete unregistered subsystem
        clientService.deleteLocalClient(subsystemId);
        assertEquals(startMembers, countMembers());
        assertEquals(startSubsystems, countSubsystems());
        assertEquals(startIdentifiers + 2, countIdentifiers());
        assertNull(clientService.getLocalClient(memberId));
        assertNull(clientService.getLocalClient(subsystemId));

        // 404 from member
        try {
            clientService.deleteLocalClient(TestUtils.getClientId("FI:GOV:NON-EXISTENT"));
            fail("should throw exception");
        } catch (ClientNotFoundException expected) {
        }

        // 404 from subsystem
        try {
            clientService.deleteLocalClient(TestUtils.getClientId("FI:GOV:NON-EXISTENT:SUBSYSTEM"));
            fail("should throw exception");
        } catch (ClientNotFoundException expected) {
        }
    }

    /**
     * add a new client, set status, attempt to delete
     */
    private void addAndDeleteLocalClient(ClientId clientId, String status) throws ActionNotPossibleException,
            ClientService.CannotDeleteOwnerException, ClientNotFoundException,
            ClientService.AdditionalMemberAlreadyExistsException, UnhandledWarningsException,
            ClientService.ClientAlreadyExistsException, ClientService.InvalidMemberClassException {
        ClientType addedClient = clientService.addLocalClient(clientId.getMemberClass(),
                clientId.getMemberCode(), clientId.getSubsystemCode(),
                IsAuthentication.SSLAUTH, true);
        addedClient.setClientStatus(status);
        clientService.deleteLocalClient(clientId);
    }

    /**
     * Change status to SAVED and then delete
     */
    private void forceDelete(ClientId clientId) throws ActionNotPossibleException,
            ClientService.CannotDeleteOwnerException, ClientNotFoundException {
        ClientType client = clientService.getLocalClient(clientId);
        client.setClientStatus(STATUS_SAVED);
        clientService.deleteLocalClient(clientId);
    }

    @Test
    public void deleteLocalClientNotPossible() throws Exception {
        long startMembers = countMembers();
        long startSubsystems = countSubsystems();
        int startIdentifiers = countIdentifiers();

        // test cases where we delete is not possible due to client status
        /**
         * clients_controller.rb:
         *       :delete_enabled =>
         *         [ClientType::STATUS_SAVED,
         *          ClientType::STATUS_DELINPROG,
         *          ClientType::STATUS_GLOBALERR].include?(client.clientStatus),
         */
        // -> delete not possible with statuses STATUS_REGINPROG and STATUS_REGISTERED

        // iterate all client statuses and test create + delete
        List<String> allStatuses = Arrays.asList(STATUS_SAVED, STATUS_REGINPROG, STATUS_REGISTERED,
                STATUS_DELINPROG, STATUS_GLOBALERR);
        int created = 0;
        for (String status : allStatuses) {
            created++;
            ClientId memberId = TestUtils.getClientId("FI:GOV:UNREGISTERED-NEW-MEMBER" + status);
            ClientId subsystemId = TestUtils.getClientId("FI:GOV:UNREGISTERED-NEW-MEMBER" + status
                    + ":NEW-SUBSYSTEM");

            if (status.equals(STATUS_REGISTERED) || status.equals(STATUS_REGINPROG)) {
                // delete is not possible
                try {
                    addAndDeleteLocalClient(memberId, status);
                    fail("delete should not be been possible");
                } catch (ActionNotPossibleException expected) {
                }
                try {
                    addAndDeleteLocalClient(subsystemId, status);
                    fail("delete should not be been possible");
                } catch (ActionNotPossibleException expected) {
                }
                assertNotNull(clientService.getLocalClient(memberId));
                assertNotNull(clientService.getLocalClient(subsystemId));
                // clean up so that we can continue adding members
                forceDelete(memberId);
                forceDelete(subsystemId);
            } else {
                // delete is possible
                addAndDeleteLocalClient(memberId, status);
                addAndDeleteLocalClient(subsystemId, status);
                assertNull(clientService.getLocalClient(memberId));
                assertNull(clientService.getLocalClient(subsystemId));
            }
            assertEquals(startMembers, countMembers());
            assertEquals(startSubsystems, countSubsystems());
            assertEquals(startIdentifiers + (created * 2), countIdentifiers());
        }
    }

    @Test(expected = ClientService.CannotDeleteOwnerException.class)
    public void deleteOwnerNotPossible() throws Exception {
        clientService.deleteLocalClient(TestUtils.getClientId("FI:GOV:M1"));
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
    public void addLocalClientWithInvalidMemberClass() throws Exception {
        // try member, FI:INVALID:M1
        try {
            ClientId id = TestUtils.getClientId("FI:INVALID:M1");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.InvalidMemberClassException");
        } catch (ClientService.InvalidMemberClassException expected) {
        }

        // and subsystem, FI:INVALID:M1:SS1
        try {
            ClientId id = TestUtils.getClientId("FI:INVALID:M1:SS1");
            clientService.addLocalClient(id.getMemberClass(), id.getMemberCode(), id.getSubsystemCode(),
                    IsAuthentication.SSLAUTH, false);
            fail("should have thrown ClientService.InvalidMemberClassException");
        } catch (ClientService.InvalidMemberClassException expected) {
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
        assertEquals(3, clientType.getLocalGroup().size());

        try {
            clientService.updateConnectionType(id, "FUBAR");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        clientService.updateConnectionType(id, "NOSSL");
        clientType = clientService.getLocalClient(id);
        assertEquals("NOSSL", clientType.getIsAuthentication());
        assertEquals(3, clientType.getLocalGroup().size());

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

    /* Test findClients search */
    @Test
    public void findClientsWithOnlyLocallyMissingClients() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .instance(TestUtils.INSTANCE_FI)
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .showMembers(false)
                .internalSearch(false)
                .excludeLocal(true)
                .build();
        List<ClientType> locallyMissingFiGovClients = clientService.findClients(searchParams);
        assertEquals(1, locallyMissingFiGovClients.size());
    }

    /* Test LOCAL client search */
    @Test
    public void findLocalClientsByNameIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .showMembers(true)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_FI)
                .showMembers(true)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(6, clients.size());
    }

    @Test
    public void findLocalClientsByClassIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .showMembers(true)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(5, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_FI)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .showMembers(true)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .instance(TestUtils.INSTANCE_FI)
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .subsystemCode(TestUtils.SUBSYSTEM1)
                .showMembers(true)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByNameExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .showMembers(false)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_FI)
                .showMembers(false)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(5, clients.size());
    }

    @Test
    public void findLocalClientsByClassExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .showMembers(false)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(4, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_FI)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .showMembers(false)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .instance(TestUtils.INSTANCE_FI)
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .subsystemCode(TestUtils.SUBSYSTEM1)
                .showMembers(false)
                .hasValidLocalSignCert(false).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(1, clients.size());
    }

    @Test
    public void findClientsByHasValidLocalSignCertSimpleScenario() {
        /*
          FI:GOV:M1 has a sign cert "cert1" with ocsp status GOOD
          FI:GOV:M1 has a sign cert "cert2" with ocsp status REVOKED
          FI:GOV:M2 has a sign cert "cert3" with ocsp status UNKNOWN
         */
        when(currentSecurityServerSignCertificates.getSignCertificateInfos()).thenReturn(createSimpleSignCertList());
        // all local clients FI:GOV:M1:* have a valid sign cert
        var searchParams = ClientService.SearchParameters.builder()
                .showMembers(false)
                .hasValidLocalSignCert(true).build();
        List<ClientType> clients = clientService.findLocalClients(searchParams);
        assertEquals(2, clients.size());
        assertTrue("GOV".equals(clients.get(0).getIdentifier().getMemberClass()));
        assertTrue("M1".equals(clients.get(0).getIdentifier().getMemberCode()));
        assertTrue("SS1".equals(clients.get(0).getIdentifier().getSubsystemCode()));
        assertTrue("GOV".equals(clients.get(1).getIdentifier().getMemberClass()));
        assertTrue("M1".equals(clients.get(1).getIdentifier().getMemberCode()));
        assertTrue("SS2".equals(clients.get(1).getIdentifier().getSubsystemCode()));
    }

    /**
     * Test hasValidLocalSignCert parameter in
     * {@link ClientService#findClients(ClientService.SearchParameters)}
     * @throws Exception
     */
    @Test
    public void findClientsByHasValidLocalSignCertComplexScenario() {

        /*
          Test data:
          Clients that match hasValidLocalSignCert parameter
          A = hasValidLocalSignCert = true (must have valid local sign cert)
          B = hasValidLocalSignCert = false (must not have valid local sign cert)
          hasValidLocalSignCert = null = union of A & B

          local clients:
          A -- FI:GOV:M1
          A -- FI:GOV:M1:SS1
          A -- FI:GOV:M1:SS2
          B  -- FI:GOV:M2:SS5
          B  -- FI:GOV:M2:SS6
          B  -- DUMMY:PRO:M2:SS6
          B  -- FI:DUMMY:M2:SS6

          global-only clients:
          B  -- FI:GOV:M2
          B  -- FI:GOV:M3
          B  -- FI:GOV:M3:SS1
          A  -- EE:PRO:M1
          A  -- EE:PRO:M1:SS1
          B  -- EE:PRO:M2
          B  -- EE:PRO:M2:SS3
          B  -- EE:PRO:M3
         */

        Set<ClientId> localGroupAClientIds = createSortedClientIdSet(new HashSet<>());
        localGroupAClientIds.add(TestUtils.getClientId("FI:GOV:M1"));
        localGroupAClientIds.add(TestUtils.getClientId("FI:GOV:M1:SS1"));
        localGroupAClientIds.add(TestUtils.getClientId("FI:GOV:M1:SS2"));

        Set<ClientId> globalGroupAClientIds = createSortedClientIdSet(new HashSet<>());
        globalGroupAClientIds.add(TestUtils.getClientId("EE:PRO:M1"));
        globalGroupAClientIds.add(TestUtils.getClientId("EE:PRO:M1:SS1"));

        Set<ClientId> groupAClientIds = createSortedClientIdSet(new HashSet<>());
        groupAClientIds.addAll(localGroupAClientIds);
        groupAClientIds.addAll(globalGroupAClientIds);

        Set<ClientId> localGroupBClientIds = createSortedClientIdSet(new HashSet<>());
        localGroupBClientIds.add(TestUtils.getClientId("FI:GOV:M2:SS5"));
        localGroupBClientIds.add(TestUtils.getClientId("FI:GOV:M2:SS6"));
        localGroupBClientIds.add(TestUtils.getClientId("DUMMY:PRO:M2:SS6"));
        localGroupBClientIds.add(TestUtils.getClientId("FI:DUMMY:M2:SS6"));

        Set<ClientId> globalGroupBClientIds = createSortedClientIdSet(new HashSet<>());
        globalGroupBClientIds.add(TestUtils.getClientId("FI:GOV:M2"));
        globalGroupBClientIds.add(TestUtils.getClientId("FI:GOV:M3"));
        globalGroupBClientIds.add(TestUtils.getClientId("FI:GOV:M3:SS1"));
        globalGroupBClientIds.add(TestUtils.getClientId("EE:PRO:M2"));
        globalGroupBClientIds.add(TestUtils.getClientId("EE:PRO:M2:SS3"));
        globalGroupBClientIds.add(TestUtils.getClientId("EE:PRO:M3"));

        Set<ClientId> groupBClientIds = createSortedClientIdSet(new HashSet<>());
        groupBClientIds.addAll(localGroupBClientIds);
        groupBClientIds.addAll(globalGroupBClientIds);

        Set<ClientId> allClientIds = createSortedClientIdSet(new HashSet<>());
        allClientIds.addAll(groupAClientIds);
        allClientIds.addAll(groupBClientIds);

        when(currentSecurityServerSignCertificates.getSignCertificateInfos())
                .thenReturn(createComplexSignCertList());

        // (1) test hasValidLocalSignCert in isolation
        // find clients with valid local sign cert
        SortedSet<ClientId> clientIds = findClientIds(true, false, false);
        assertEquals(groupAClientIds, clientIds);

        // find clients without valid local sign cert
        clientIds = findClientIds(false, false, false);
        assertEquals(groupBClientIds, clientIds);

        // find all clients
        clientIds = findClientIds(null, false, false);
        assertEquals(allClientIds, clientIds);

        // (2) combine hasValidLocalSignCert with internalSearch and excludeLocal
        clientIds = findClientIds(true, false, true);
        assertEquals(localGroupAClientIds, clientIds);

        clientIds = findClientIds(true, true, false);
        assertEquals(globalGroupAClientIds, clientIds);

        clientIds = findClientIds(false, false, true);
        assertEquals(localGroupBClientIds, clientIds);

        clientIds = findClientIds(false, true, false);
        assertEquals(globalGroupBClientIds, clientIds);
    }

    /**
     * Convenience wrapper for clientService.findClients which takes only relevant params and returns client ids
     * @param hasValidLocalSignCert see {@link ClientService.SearchParameters#hasValidLocalSignCert}
     * @param excludeLocal          see {@link ClientService.SearchParameters#excludeLocal}
     * @param internalSearch        see {@link ClientService.SearchParameters#internalSearch}
     * @return matching clientIds
     */
    private SortedSet<ClientId> findClientIds(Boolean hasValidLocalSignCert,
            boolean excludeLocal, boolean internalSearch) {
        var searchParams = ClientService.SearchParameters.builder()
                .excludeLocal(excludeLocal)
                .internalSearch(internalSearch)
                .showMembers(true)
                .hasValidLocalSignCert(hasValidLocalSignCert).build();
        List<ClientType> clients = clientService.findClients(searchParams);
        return createSortedClientIdSet(clients.stream()
                .map(ClientType::getIdentifier)
                .collect(Collectors.toSet()));
    }

    /**
     * Convenience sorted set, sorting based on client id short string, for easier debugging
     */
    private SortedSet<ClientId> createSortedClientIdSet(Collection<ClientId> clientIds) {
        SortedSet<ClientId> s = new TreeSet<>(Comparator.comparing(ClientId::toShortString));
        s.addAll(clientIds);
        return s;
    }

    /* Test GLOBAL client search */
    @Test
    public void findGlobalClientsByNameIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .showMembers(true)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_EE)
                .showMembers(true)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(5, clients.size());
    }

    @Test
    public void findGlobalClientsByClassIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .showMembers(true)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(6, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceAndMemberCodeIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_FI)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .showMembers(true)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByAllTermsIncludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .instance(TestUtils.INSTANCE_FI)
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .subsystemCode(TestUtils.SUBSYSTEM1)
                .showMembers(true)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(1, clients.size());
    }

    @Test
    public void findGlobalClientsByNameExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .showMembers(false)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_EE)
                .showMembers(false)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByClassExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .showMembers(false)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceAndMemberCodeExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .instance(TestUtils.INSTANCE_FI)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .showMembers(false)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByAllTermsExcludeMembers() {
        var searchParams = ClientService.SearchParameters.builder()
                .name(TestUtils.NAME_FOR + TestUtils.SUBSYSTEM1)
                .instance(TestUtils.INSTANCE_FI)
                .memberClass(TestUtils.MEMBER_CLASS_GOV)
                .memberCode(TestUtils.MEMBER_CODE_M1)
                .subsystemCode(TestUtils.SUBSYSTEM1)
                .showMembers(false)
                .build();
        List<ClientType> clients = clientService.findGlobalClients(searchParams);
        assertEquals(1, clients.size());
    }

    @Test
    public void getLocalClientMemberIds() {
        Set<ClientId> expected = new HashSet();
        expected.add(ClientId.Conf.create(TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1));
        expected.add(ClientId.Conf.create(TestUtils.INSTANCE_FI,
                TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2));
        expected.add(ClientId.Conf.create("DUMMY", "PRO", "M2"));
        expected.add(ClientId.Conf.create("FI", "DUMMY", "M2"));
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

    @Test(expected = ClientNotFoundException.class)
    public void registerNonExistingClient() throws Exception {
        clientService.registerClient(ClientId.Conf.create("non", "existing", "client", null));
    }

    @Test(expected = ClientService.InvalidInstanceIdentifierException.class)
    public void registerClientWithInvalidInstanceIdentifier() throws Exception {
        clientService.registerClient(ClientId.Conf.create("DUMMY", "PRO", "M2", "SS6"));
    }

    @Test(expected = ClientService.InvalidMemberClassException.class)
    public void registerClientWithInvalidMemberClass() throws Exception {
        clientService.registerClient(ClientId.Conf.create("FI", "DUMMY", "M2", "SS6"));
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
        when(managementRequestSenderService.sendClientUnregisterRequest(any())).thenThrow(CodedException.class);
        clientService.unregisterClient(existingRegisteredClientId);
    }

    @Test(expected = DeviationAwareRuntimeException.class)
    public void unregisterClientRuntimeException() throws Exception {
        when(managementRequestSenderService.sendClientUnregisterRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(new Exception()));
        clientService.unregisterClient(existingRegisteredClientId);
    }

    @Test(expected = ClientNotFoundException.class)
    public void unregisterNonExistingClient() throws Exception {
        clientService.unregisterClient(ClientId.Conf.create("non", "existing", "client", null));
    }

    @Test
    public void changeOwner() {
        try {
            clientService.addLocalClient(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                    null, IsAuthentication.SSLAUTH, false);
            ClientType clientType = clientService.getLocalClient(newOwnerClientId);
            clientType.setClientStatus(STATUS_REGISTERED);
            clientService.changeOwner(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                    newOwnerClientId.getSubsystemCode());
        } catch (Exception e) {
            fail("should have not thrown Exception");
        }
    }

    @Test(expected = ActionNotPossibleException.class)
    public void changeOwnerNewOwnerSubsystem() throws Exception {
        // New owner ("existingClientId") is a subsystem which is not allowed
        clientService.changeOwner(existingRegisteredClientId.getMemberClass(),
                existingRegisteredClientId.getMemberCode(), existingRegisteredClientId.getSubsystemCode());
    }

    @Test(expected = ClientNotFoundException.class)
    public void changeOwnerNonExistingClient() throws Exception {
        clientService.changeOwner("existing", "client", null);
    }

    @Test(expected = ActionNotPossibleException.class)
    public void changeOwnerNewOwnerNotRegistered() throws Exception {
        clientService.addLocalClient(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                null, IsAuthentication.SSLAUTH, false);
        clientService.changeOwner(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                newOwnerClientId.getSubsystemCode());
    }

    @Test(expected = ClientService.MemberAlreadyOwnerException.class)
    public void changeOwnerNewOwnerAlreadyOwner() throws Exception {
        clientService.changeOwner(ownerClientId.getMemberClass(), ownerClientId.getMemberCode(),
                ownerClientId.getSubsystemCode());
    }

    @Test(expected = CodedException.class)
    public void changeOwnerCodedException() throws Exception {
        when(managementRequestSenderService.sendOwnerChangeRequest(any())).thenThrow(CodedException.class);
        clientService.addLocalClient(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                null, IsAuthentication.SSLAUTH, false);
        ClientType clientType = clientService.getLocalClient(newOwnerClientId);
        clientType.setClientStatus(STATUS_REGISTERED);
        clientService.changeOwner(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                newOwnerClientId.getSubsystemCode());
    }

    @Test(expected = DeviationAwareRuntimeException.class)
    public void changeOwnerRuntimeException() throws Exception {
        when(managementRequestSenderService.sendOwnerChangeRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(new Exception()));
        clientService.addLocalClient(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                null, IsAuthentication.SSLAUTH, false);
        ClientType clientType = clientService.getLocalClient(newOwnerClientId);
        clientType.setClientStatus(STATUS_REGISTERED);
        clientService.changeOwner(newOwnerClientId.getMemberClass(), newOwnerClientId.getMemberCode(),
                newOwnerClientId.getSubsystemCode());
    }
}
