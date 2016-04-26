/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.conf;

import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.WsdlDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.*;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.proxy.conf.TestUtil.*;
import static org.junit.Assert.*;

/**
 * Test cases for different ServerConf releated DAOs.
 */
public class DAOImplTest {

    private Session session;

    /**
     * Prepares test database.
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        prepareDB();
    }

    /**
     * Begins transaction.
     */
    @Before
    public void beginTransaction() {
        session = ServerConfDatabaseCtx.get().beginTransaction();
    }

    /**
     * Commits transaction.
     */
    @After
    public void commitTransaction() {
        ServerConfDatabaseCtx.get().commitTransaction();
    }

    /**
     * Test getting client by its identifier.
     * @throws Exception if an error occurs
     */
    @Test
    public void getClientByIdentifier() throws Exception {
        ClientId id = createTestClientId(client(1));
        assertTrue(clientExists(id, false));
        getClient(id);

        id = createTestClientId(client(NUM_CLIENTS - 1));
        assertFalse(clientExists(id, false));
        assertTrue(clientExists(id, true));

        id = createTestClientId(client(NUM_CLIENTS - 1), SUBSYSTEM);
        assertTrue(clientExists(id, false));
    }

    /**
     * Test getting IS certificates.
     * @throws Exception if an error occurs
     */
    @Test
    public void getIsCerts() throws Exception {
        ClientId id = createTestClientId(client(1));
        assertEquals(1, new ClientDAOImpl().getIsCerts(session, id).size());
    }

    /**
     * Test getting service by identifier.
     * @throws Exception if an error occurs
     */
    @Test
    public void getServiceByIdentifier() throws Exception {
        ServiceId id = createTestServiceId(client(1), service(1, 1),
                SERVICE_VERSION);
        ServiceType service = new ServiceDAOImpl().getService(session, id);
        assertNotNull(service);
        assertNotNull(service.getWsdl());
        assertNotNull(service.getWsdl().getClient());
        assertEquals(id, ServiceId.create(
                service.getWsdl().getClient().getIdentifier(),
                service.getServiceCode(), service.getServiceVersion()));

        WsdlType wsdl = new WsdlDAOImpl().getWsdl(session, id);
        assertNotNull(wsdl);
        assertNotNull(wsdl.getClient());
        assertEquals(id.getClientId(), wsdl.getClient().getIdentifier());
    }

    /**
     * Test getting ACL.
     * @throws Exception if an error occurs
     */
    @Test
    public void getAcl() throws Exception {
        ClientId id = createTestClientId(client(1));
        List<AccessRightType> acl = getClient(id).getAcl();
        assertEquals(4, acl.size());

        assertTrue(acl.get(0).getSubjectId() instanceof ClientId);
        assertTrue(acl.get(1).getSubjectId() instanceof ClientId);
        assertTrue(acl.get(2).getSubjectId() instanceof ServiceId);
        assertTrue(acl.get(3).getSubjectId() instanceof LocalGroupId);
    }

    /**
     * Test deleting client.
     * @throws Exception if an error occurs
     */
    @Test
    public void deleteClient() throws Exception {
        ClientId id = createTestClientId(client(2));
        ClientType client = getClient(id);

        ServerConfType conf = getConf();
        assertTrue(conf.getClient().remove(client));

        session.saveOrUpdate(conf);
        session.delete(client);

        client = new ClientDAOImpl().findById(session, ClientType.class,
                client.getId());
        assertNull(client);
    }

    /**
     * Test deleting WSDL.
     * @throws Exception if an error occurs
     */
    @Test
    public void deleteWsdl() throws Exception {
        ClientId id = createTestClientId(client(3));
        ClientType client = getClient(id);

        assertEquals(TestUtil.NUM_WSDLS, client.getWsdl().size());

        WsdlType wsdl = client.getWsdl().get(0);
        Long wsdlId = wsdl.getId();

        client.getWsdl().remove(wsdl);
        session.saveOrUpdate(client);
        session.delete(wsdl);

        assertEquals(TestUtil.NUM_WSDLS - 1, client.getWsdl().size());
        assertNull(session.get(WsdlType.class, wsdlId));
    }

    /**
     * Test adding local group member.
     * @throws Exception if an error occurs
     */
    @Test
    public void addLocalGroupMember() throws Exception {
        ClientType client = getClient(createTestClientId(client(1)));
        assertTrue(!client.getLocalGroup().isEmpty());

        LocalGroupType localGroup = client.getLocalGroup().get(0);

        ClientId clientId =
                IdentifierDAOImpl.getIdentifier(createTestClientId(client(3)));
        assertNotNull(clientId);

        GroupMemberType member = new GroupMemberType();
        member.setAdded(new Date());
        member.setGroupMemberId(clientId);
        session.save(member);

        localGroup.getGroupMember().add(member);
    }

    private ServerConfType getConf() throws Exception {
        return new ServerConfDAOImpl().getConf();
    }

    private boolean clientExists(ClientId id, boolean includeSubsystems)
            throws Exception {
        return new ClientDAOImpl().clientExists(session, id, includeSubsystems);
    }

    private ClientType getClient(ClientId id) throws Exception {
        ClientType client = new ClientDAOImpl().getClient(session, id);
        assertNotNull(client);
        assertEquals(id, client.getIdentifier());

        return client;
    }
}
