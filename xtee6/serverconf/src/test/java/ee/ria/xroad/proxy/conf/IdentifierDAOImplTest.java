package ee.ria.xroad.proxy.conf;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.ria.xroad.common.conf.serverconf.ServerConfDatabaseCtx;
import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.identifier.*;

import static org.junit.Assert.assertEquals;

/**
 * Tests identifier DAO implementation -- creating and reading the identifiers.
 */
public class IdentifierDAOImplTest {

    private Session session;

    /**
     * Prepares test database.
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TestUtil.prepareDB();
    }

    /**
     * Begins transaction
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
     * ClientId.
     * @throws Exception if an error occurs
     */
    @Test
    public void clientId() throws Exception {
        assertCreateRead(() -> ClientId.create("EE", "class", "code1"));
        assertCreateRead(() -> ClientId.create("EE", "class", "code2"));
    }

    /**
     * ServiceId.
     * @throws Exception if an error occurs
     */
    @Test
    public void serviceId() throws Exception {
        assertCreateRead(() ->
            ServiceId.create("EE", "cls", "code", null, "service1"));

        assertCreateRead(() ->
            ServiceId.create("EE", "cls", "code", null, "service2"));
    }

    /**
     * CentralServiceId.
     * @throws Exception if an error occurs
     */
    @Test
    public void centralServiceId() throws Exception {
        assertCreateRead(() -> CentralServiceId.create("EE", "central1"));
        assertCreateRead(() -> CentralServiceId.create("EE", "central2"));
    }

    /**
     * GlobalGroupId.
     * @throws Exception if an error occurs
     */
    @Test
    public void globalGroupId() throws Exception {
        assertCreateRead(() -> GlobalGroupId.create("XX", "globalGroup1"));
        assertCreateRead(() -> GlobalGroupId.create("XX", "globalGroup2"));
    }

    /**
     * LocalGroupId.
     * @throws Exception if an error occurs
     */
    @Test
    public void localGroupId() throws Exception {
        assertCreateRead(() -> LocalGroupId.create("localGroup1"));
        assertCreateRead(() -> LocalGroupId.create("localGroup2"));
    }

    /**
     * SecurityCategoryId.
     * @throws Exception if an error occurs
     */
    @Test
    public void securityCategoryId() throws Exception {
        assertCreateRead(() -> SecurityCategoryId.create("XX", "cat1"));
        assertCreateRead(() -> SecurityCategoryId.create("XX", "cat2"));
    }

    /**
     * SecurityServerId.
     * @throws Exception if an error occurs
     */
    @Test
    public void securityServerId() throws Exception {
        assertCreateRead(() ->
            SecurityServerId.create("XX", "class", "code", "srv1"));

        assertCreateRead(() ->
            SecurityServerId.create("XX", "class", "code", "srv2"));
    }

    private <T extends XRoadId> T get(T example) throws Exception {
        return IdentifierDAOImpl.getIdentifier(example);
    }

    private <T> void assertCreateRead(
            IdentifierCallback<? extends XRoadId> callback) throws Exception {
        XRoadId in = callback.create();
        session.save(in);

        XRoadId out = get(callback.create());
        assertEquals(in, out);
    }

    @FunctionalInterface
    private interface IdentifierCallback<T extends XRoadId> {
        T create();
    }
}
