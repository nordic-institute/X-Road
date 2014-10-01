package ee.cyber.sdsb.proxy.conf;

import java.util.Date;
import java.util.List;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.cyber.sdsb.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.cyber.sdsb.common.conf.serverconf.dao.AclDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.ClientDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServerConfDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.ServiceDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.dao.WsdlDAOImpl;
import ee.cyber.sdsb.common.conf.serverconf.model.*;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.LocalGroupId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.proxy.conf.TestUtil.*;
import static org.junit.Assert.*;

/**
 * Test cases for different ServerConf releated DAOs.
 */
public class DAOImplTest {

    private Session session;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        prepareDB();
    }

    @Before
    public void beginTransaction() {
        session = ServerConfDatabaseCtx.get().beginTransaction();
    }

    @After
    public void commitTransaction() {
        ServerConfDatabaseCtx.get().commitTransaction();
    }

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

    @Test
    public void getIsCerts() throws Exception {
        ClientId id = createTestClientId(client(1));
        assertEquals(1, ClientDAOImpl.getInstance().getIsCerts(
                session, id).size());
    }

    @Test
    public void getServiceByIdentifier() throws Exception {
        ServiceId id = createTestServiceId(client(1), service(1, 1),
                SERVICE_VERSION);
        ServiceType service = ServiceDAOImpl.getInstance().getService(
                session, id);
        assertNotNull(service);
        assertNotNull(service.getWsdl());
        assertNotNull(service.getWsdl().getClient());
        assertEquals(id, ServiceId.create(
                service.getWsdl().getClient().getIdentifier(),
                service.getServiceCode(), service.getServiceVersion()));

        WsdlType wsdl = WsdlDAOImpl.getInstance().getWsdl(session, id);
        assertNotNull(wsdl);
        assertNotNull(wsdl.getClient());
        assertEquals(id.getClientId(), wsdl.getClient().getIdentifier());
    }

    @Test
    public void getAcl() throws Exception {
        ClientId id = createTestClientId(client(1));
        List<AclType> acl = AclDAOImpl.getInstance().getAcl(session, id);
        assertEquals(1, acl.size());
        assertEquals(4, acl.get(0).getAuthorizedSubject().size());

        List<AuthorizedSubjectType> s = acl.get(0).getAuthorizedSubject();
        assertTrue(s.get(0).getSubjectId() instanceof ClientId);
        assertTrue(s.get(1).getSubjectId() instanceof ClientId);
        assertTrue(s.get(2).getSubjectId() instanceof ServiceId);
        assertTrue(s.get(3).getSubjectId() instanceof LocalGroupId);
    }

    @Test
    public void deleteClient() throws Exception {
        ClientId id = createTestClientId(client(2));
        ClientType client = getClient(id);

        ServerConfType conf = getConf();
        assertTrue(conf.getClient().remove(client));

        session.saveOrUpdate(conf);
        session.delete(client);

        client = ClientDAOImpl.getInstance().findById(session,
                ClientType.class, client.getId());
        assertNull(client);
    }

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

    @Test
    public void deleteGlobalConfDistributor() throws Exception {
        ServerConfType conf = getConf();
        assertEquals(TestUtil.NUM_DISTRIBUTORS,
                conf.getGlobalConfDistributor().size());

        GlobalConfDistributorType t = conf.getGlobalConfDistributor().get(0);
        Long id = t.getId();

        assertTrue(conf.getGlobalConfDistributor().remove(t));
        session.saveOrUpdate(t);
        session.delete(t);

        assertEquals(TestUtil.NUM_DISTRIBUTORS - 1,
                conf.getGlobalConfDistributor().size());
        assertNull(session.get(GlobalConfDistributorType.class, id));
    }

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
        return ServerConfDAOImpl.getInstance().getConf();
    }

    private boolean clientExists(ClientId id, boolean includeSubsystems)
            throws Exception {
        return ClientDAOImpl.getInstance().clientExists(session, id,
                includeSubsystems);
    }

    private ClientType getClient(ClientId id) throws Exception {
        ClientType client = ClientDAOImpl.getInstance().getClient(session, id);
        assertNotNull(client);
        assertEquals(id, client.getIdentifier());

        return client;
    }

    // TODO: more test cases: create-update-delete
}
