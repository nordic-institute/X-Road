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
package org.niis.xroad.serverconf.impl;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.serverconf.entity.AccessRightTypeEntity;
import org.niis.xroad.serverconf.entity.ClientIdConfEntity;
import org.niis.xroad.serverconf.entity.ClientTypeEntity;
import org.niis.xroad.serverconf.entity.GroupMemberTypeEntity;
import org.niis.xroad.serverconf.entity.LocalGroupTypeEntity;
import org.niis.xroad.serverconf.entity.ServerConfTypeEntity;
import org.niis.xroad.serverconf.entity.ServiceDescriptionTypeEntity;
import org.niis.xroad.serverconf.entity.ServiceIdConfEntity;
import org.niis.xroad.serverconf.entity.ServiceTypeEntity;
import org.niis.xroad.serverconf.impl.dao.ClientDAOImpl;
import org.niis.xroad.serverconf.impl.dao.IdentifierDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServerConfDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServiceDAOImpl;
import org.niis.xroad.serverconf.impl.dao.ServiceDescriptionDAOImpl;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.serverconf.impl.TestUtil.NUM_CLIENTS;
import static org.niis.xroad.serverconf.impl.TestUtil.SERVICE_VERSION;
import static org.niis.xroad.serverconf.impl.TestUtil.SUBSYSTEM;
import static org.niis.xroad.serverconf.impl.TestUtil.client;
import static org.niis.xroad.serverconf.impl.TestUtil.createTestClientId;
import static org.niis.xroad.serverconf.impl.TestUtil.createTestServiceIdEntity;
import static org.niis.xroad.serverconf.impl.TestUtil.prepareDB;
import static org.niis.xroad.serverconf.impl.TestUtil.service;

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
     */
    @Test
    public void getClientByIdentifier() {
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
     */
    @Test
    public void getIsCerts() {
        ClientId id = createTestClientId(client(1));
        assertEquals(1, new ClientDAOImpl().getIsCerts(session, id).size());
    }

    /**
     * Test getting service by identifier.
     */
    @Test
    public void getServiceByIdentifier() {
        ServiceIdConfEntity id = createTestServiceIdEntity(client(1), service(1, 1),
                SERVICE_VERSION);
        ServiceTypeEntity service = new ServiceDAOImpl().getService(session, id);
        assertNotNull(service);
        assertNotNull(service.getServiceDescription());
        assertNotNull(service.getServiceDescription().getClient());
        assertEquals(id, ServiceIdConfEntity.create(
                service.getServiceDescription().getClient().getIdentifier(),
                service.getServiceCode(), service.getServiceVersion()));

        ServiceDescriptionTypeEntity serviceDescription = new ServiceDescriptionDAOImpl().getServiceDescription(session, id);
        assertNotNull(serviceDescription);
        assertNotNull(serviceDescription.getClient());
        assertEquals(id.getClientId(), serviceDescription.getClient().getIdentifier());
    }

    /**
     * Test getting ACL.
     */
    @Test
    public void getAcl(){
        ClientId id = createTestClientId(client(1));
        List<AccessRightTypeEntity> acl = getClient(id).getAcl();
        assertEquals(6, acl.size());

        assertTrue(acl.get(0).getSubjectId() instanceof ClientId);
        assertTrue(acl.get(1).getSubjectId() instanceof ClientId);
        assertTrue(acl.get(2).getSubjectId() instanceof ServiceId);
        assertTrue(acl.get(3).getSubjectId() instanceof LocalGroupId);
    }

    /**
     * Test deleting client.
     */
    @Test
    public void deleteClient() {
        ClientId id = createTestClientId(client(2));
        ClientTypeEntity client = getClient(id);

        ServerConfTypeEntity conf = getConf();
        assertTrue(conf.getClient().remove(client));

        session.merge(conf);
        session.remove(client);

        client = new ClientDAOImpl().findById(session, ClientTypeEntity.class,
                client.getId());
        assertNull(client);
    }

    /**
     * Test deleting service description.
     */
    @Test
    public void deleteServiceDescription() {
        ClientId id = createTestClientId(client(3));
        ClientTypeEntity client = getClient(id);

        assertEquals(TestUtil.NUM_SERVICEDESCRIPTIONS, client.getServiceDescription().size());

        ServiceDescriptionTypeEntity serviceDescription = client.getServiceDescription().get(0);
        Long serviceDescriptionId = serviceDescription.getId();

        client.getServiceDescription().remove(serviceDescription);
        session.merge(client);
        session.remove(serviceDescription);

        assertEquals(TestUtil.NUM_SERVICEDESCRIPTIONS - 1, client.getServiceDescription().size());
        assertNull(session.get(ServiceDescriptionTypeEntity.class, serviceDescriptionId));
    }

    /**
     * Test adding local group member.
     */
    @Test
    public void addLocalGroupMember() {
        ClientTypeEntity client = getClient(createTestClientId(client(1)));
        assertTrue(!client.getLocalGroup().isEmpty());

        LocalGroupTypeEntity localGroup = client.getLocalGroup().get(0);

        ClientIdConfEntity clientId =
                identifierDAO.findClientId(session, createTestClientId(client(3)));
        assertNotNull(clientId);

        GroupMemberTypeEntity member = new GroupMemberTypeEntity();
        member.setAdded(new Date());
        member.setGroupMemberId(clientId);
        session.persist(member);

        localGroup.getGroupMember().add(member);
    }

    private ServerConfTypeEntity getConf() {
        return new ServerConfDAOImpl().getConf(session);
    }

    private boolean clientExists(ClientId id, boolean includeSubsystems) {
        return new ClientDAOImpl().clientExists(session, id, includeSubsystems);
    }

    private ClientTypeEntity getClient(ClientId id) {
        ClientTypeEntity client = new ClientDAOImpl().getClient(session, id);
        assertNotNull(client);
        assertEquals(id, client.getIdentifier());

        return client;
    }
}
