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

import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * test client service
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ClientServiceIntegrationTest {

    private static final String INSTANCE_FI = "FI";
    private static final String INSTANCE_EE = "EE";
    private static final String MEMBER_CLASS_GOV = "GOV";
    private static final String MEMBER_CLASS_PRO = "PRO";
    private static final String MEMBER_CODE_M1 = "M1";
    private static final String MEMBER_CODE_M2 = "M2";
    private static final String SUBSYSTEM1 = "SS1";
    private static final String SUBSYSTEM2 = "SS2";
    private static final String SUBSYSTEM3 = "SS3";
    private static final String NAME_APPENDIX = "-name";

    @Autowired
    private ClientRepository clientRepository;
    private ClientService clientService;

    private byte[] pemBytes;
    private byte[] derBytes;
    private byte[] sqlFileBytes;

    @Before
    public void setup() throws Exception {
        GlobalConfFacade globalConfFacade = new GlobalConfFacade() {
            @Override
            public List<MemberInfo> getMembers(String... instanceIdentifiers) {
                return new ArrayList<>(Arrays.asList(
                        TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1),
                        TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM2),
                        TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                        TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M2, SUBSYSTEM3),
                        TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M1, null),
                        TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M1, SUBSYSTEM1),
                        TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M2, null))
                );
            }

            @Override
            public String getMemberName(ClientId identifier) {
                return identifier.getSubsystemCode() != null ? identifier.getSubsystemCode() + NAME_APPENDIX
                        : "test-member" + NAME_APPENDIX;
            }
        };
        clientService = new ClientService(clientRepository, globalConfFacade);
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

    @Test
    @WithMockUser(authorities = { "EDIT_CLIENT_INTERNAL_CONNECTION_TYPE",
            "VIEW_CLIENT_DETAILS" })
    public void updateConnectionType() {
        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals("SSLNOAUTH", clientType.getIsAuthentication());
        assertEquals(2, clientType.getLocalGroup().size());

        try {
            clientService.updateConnectionType(id, "FUBAR");
            fail("should throw IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }

        clientService.updateConnectionType(id, "NOSSL");
        clientType = clientService.getClient(id);
        assertEquals("NOSSL", clientType.getIsAuthentication());
        assertEquals(2, clientType.getLocalGroup().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addCertificatePem() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, pemBytes);

        clientType = clientService.getClient(id);
        assertEquals(1, clientType.getIsCert().size());
        assertTrue(Arrays.equals(derBytes, clientType.getIsCert().get(0).getData()));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addInvalidCertificate() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        try {
            clientService.addTlsCertificate(id, sqlFileBytes);
            fail("should have thrown CertificateException");
        } catch (CertificateException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addCertificateDer() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);

        clientType = clientService.getClient(id);
        assertEquals(1, clientType.getIsCert().size());
        assertTrue(Arrays.equals(derBytes, clientType.getIsCert().get(0).getData()));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT" })
    public void addDuplicate() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);

        try {
            clientService.addTlsCertificate(id, pemBytes);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "ADD_CLIENT_INTERNAL_CERT",
            "DELETE_CLIENT_INTERNAL_CERT" })
    public void deleteCertificate() throws Exception {

        ClientId id = TestUtils.getM1Ss1ClientId();
        ClientType clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());

        clientService.addTlsCertificate(id, derBytes);
        String hash = CryptoUtils.calculateCertHexHash(derBytes);

        try {
            clientService.deleteTlsCertificate(id, "wrong hash");
            fail("should have thrown NotFoundException");
        } catch (NotFoundException expected) {
        }
        clientType = clientService.getClient(id);
        assertEquals(1, clientType.getIsCert().size());

        clientService.deleteTlsCertificate(id, hash);
        clientType = clientService.getClient(id);
        assertEquals(0, clientType.getIsCert().size());
    }

    /* Test LOCAL client search */
    @Test
    public void findLocalClientsByNameIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, null, null,
                null, null, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, INSTANCE_FI, null,
                null, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByClassIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, null, MEMBER_CLASS_GOV,
                null, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, INSTANCE_FI, null,
                MEMBER_CODE_M1, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsIncludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, INSTANCE_FI,
                MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByNameExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, null, null,
                null, null, false);
        assertEquals(1, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, INSTANCE_FI, null,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByClassExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, null, MEMBER_CLASS_GOV,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByInstanceAndMemberCodeExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(null, INSTANCE_FI, null,
                MEMBER_CODE_M1, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findLocalClientsByAllTermsExcludeMembers() {
        List<ClientType> clients = clientService.findLocalClients(SUBSYSTEM1 + NAME_APPENDIX, INSTANCE_FI,
                MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1, false);
        assertEquals(1, clients.size());
    }

    /* Test GLOBAL client search */
    @Test
    public void findGlobalClientsByNameIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(SUBSYSTEM1 + NAME_APPENDIX, null, null,
                null, null, true);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, INSTANCE_EE, null,
                null, null, true);
        assertEquals(4, clients.size());
    }

    @Test
    public void findGlobalClientsByClassIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, null, MEMBER_CLASS_GOV,
                null, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceAndMemberCodeIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, INSTANCE_FI, null,
                MEMBER_CODE_M1, null, true);
        assertEquals(3, clients.size());
    }

    @Test
    public void findGlobalClientsByAllTermsIncludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(SUBSYSTEM1 + NAME_APPENDIX, INSTANCE_FI,
                MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1, true);
        assertEquals(1, clients.size());
    }

    @Test
    public void findGlobalClientsByNameExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(SUBSYSTEM1 + NAME_APPENDIX, null, null,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, INSTANCE_EE, null,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByClassExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, null, MEMBER_CLASS_GOV,
                null, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByInstanceAndMemberCodeExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(null, INSTANCE_FI, null,
                MEMBER_CODE_M1, null, false);
        assertEquals(2, clients.size());
    }

    @Test
    public void findGlobalClientsByAllTermsExcludeMembers() {
        List<ClientType> clients = clientService.findGlobalClients(SUBSYSTEM1 + NAME_APPENDIX, INSTANCE_FI,
                MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1, false);
        assertEquals(1, clients.size());
    }
}
