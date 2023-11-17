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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.dao.ClientDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.ria.xroad.common.conf.serverconf.dao.ServiceDescriptionDAOImpl;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.GroupMemberType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static ee.ria.xroad.proxy.conf.TestUtil.NUM_CLIENTS;
import static ee.ria.xroad.proxy.conf.TestUtil.SERVICE_VERSION;
import static ee.ria.xroad.proxy.conf.TestUtil.SUBSYSTEM;
import static ee.ria.xroad.proxy.conf.TestUtil.client;
import static ee.ria.xroad.proxy.conf.TestUtil.createTestClientId;
import static ee.ria.xroad.proxy.conf.TestUtil.createTestServiceId;
import static ee.ria.xroad.proxy.conf.TestUtil.prepareDB;
import static ee.ria.xroad.proxy.conf.TestUtil.service;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test cases for different ServerConf releated DAOs.
 */
public class DAOImplTest {

    private Session session;
    private IdentifierDAOImpl identifierDAO = new IdentifierDAOImpl();

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
        ServiceId.Conf id = createTestServiceId(client(1), service(1, 1),
                SERVICE_VERSION);
        ServiceType service = new ServiceDAOImpl().getService(session, id);
        assertNotNull(service);
        assertNotNull(service.getServiceDescription());
        assertNotNull(service.getServiceDescription().getClient());
        assertEquals(id, ServiceId.Conf.create(
                service.getServiceDescription().getClient().getIdentifier(),
                service.getServiceCode(), service.getServiceVersion()));

        ServiceDescriptionType serviceDescription = new ServiceDescriptionDAOImpl().getServiceDescription(session, id);
        assertNotNull(serviceDescription);
        assertNotNull(serviceDescription.getClient());
        assertEquals(id.getClientId(), serviceDescription.getClient().getIdentifier());
    }

    /**
     * Test getting ACL.
     * @throws Exception if an error occurs
     */
    @Test
    public void getAcl() throws Exception {
        ClientId id = createTestClientId(client(1));
        List<AccessRightType> acl = getClient(id).getAcl();
        assertEquals(6, acl.size());

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
     * Test deleting service description.
     * @throws Exception if an error occurs
     */
    @Test
    public void deleteServiceDescription() throws Exception {
        ClientId id = createTestClientId(client(3));
        ClientType client = getClient(id);

        assertEquals(TestUtil.NUM_SERVICEDESCRIPTIONS, client.getServiceDescription().size());

        ServiceDescriptionType serviceDescription = client.getServiceDescription().get(0);
        Long serviceDescriptionId = serviceDescription.getId();

        client.getServiceDescription().remove(serviceDescription);
        session.saveOrUpdate(client);
        session.delete(serviceDescription);

        assertEquals(TestUtil.NUM_SERVICEDESCRIPTIONS - 1, client.getServiceDescription().size());
        assertNull(session.get(ServiceDescriptionType.class, serviceDescriptionId));
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
                identifierDAO.findClientId(session, createTestClientId(client(3)));
        assertNotNull(clientId);

        GroupMemberType member = new GroupMemberType();
        member.setAdded(new Date());
        member.setGroupMemberId(clientId);
        session.save(member);

        localGroup.getGroupMember().add(member);
    }

    private ServerConfType getConf() throws Exception {
        return new ServerConfDAOImpl().getConf(session);
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
