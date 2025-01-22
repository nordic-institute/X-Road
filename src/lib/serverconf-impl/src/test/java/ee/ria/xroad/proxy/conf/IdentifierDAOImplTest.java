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
import ee.ria.xroad.common.conf.serverconf.dao.IdentifierDAOImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.hibernate.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests identifier DAO implementation -- creating and reading the identifiers.
 */
public class IdentifierDAOImplTest {

    private final IdentifierDAOImpl identifierDAO = new IdentifierDAOImpl();
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
     */
    @Test
    public void clientId() {
        assertCreateRead(() -> ClientId.Conf.create("EE", "class", "code1"),
                id -> identifierDAO.findClientId(session, id));
        assertCreateRead(() -> ClientId.Conf.create("EE", "class", "code2"),
                id -> identifierDAO.findClientId(session, id));
    }

    /**
     * ServiceId.
     */
    @Test
    public void serviceId() {
        assertCreateRead(() -> ServiceId.Conf.create("EE", "cls", "code", null, "service1"),
                id -> identifierDAO.findServiceId(session, id));

        assertCreateRead(() -> ServiceId.Conf.create("EE", "cls", "code", null, "service2"),
                id -> identifierDAO.findServiceId(session, id));

        assertCreateRead(() -> ServiceId.Conf.create("EE", "cls", "code", null, "service3", "1.0"),
                id -> identifierDAO.findServiceId(session, id));

        assertCreateRead(() -> ServiceId.Conf.create("EE", "cls", "code", null, "service3", "2.0"),
                id -> identifierDAO.findServiceId(session, id));
    }

    /**
     * GlobalGroupId.
     */
    @Test
    public void globalGroupId() {
        assertCreateRead(() -> GlobalGroupId.Conf.create("XX", "globalGroup1"),
                id -> identifierDAO.findGlobalGroupId(session, id));
        assertCreateRead(() -> GlobalGroupId.Conf.create("XX", "globalGroup2"),
                id -> identifierDAO.findGlobalGroupId(session, id));
    }

    /**
     * LocalGroupId.
     */
    @Test
    public void localGroupId() {
        assertCreateRead(() -> LocalGroupId.Conf.create("localGroup1"),
                id -> identifierDAO.findLocalGroupId(session, id));
        assertCreateRead(() -> LocalGroupId.Conf.create("localGroup2"),
                id -> identifierDAO.findLocalGroupId(session, id));
    }

    /**
     * SecurityServerId.
     */
    @Test
    public void securityServerId() {
        assertCreateRead(() -> SecurityServerId.Conf.create("XX", "class", "code", "srv1"),
                id -> identifierDAO.findSecurityServerId(session, id));

        assertCreateRead(() -> SecurityServerId.Conf.create("XX", "class", "code", "srv2"),
                id -> new IdentifierDAOImpl().findSecurityServerId(session, id));
    }

    private <T extends XRoadId> void assertCreateRead(IdentifierCreator<T> creator,
                                                      IdentifierFetcher<T> fetcher) {
        XRoadId in = creator.create();
        session.persist(in);

        XRoadId out = fetcher.get(creator.create());
        assertEquals(in, out);
    }

    @FunctionalInterface
    private interface IdentifierCreator<T extends XRoadId> {
        T create();
    }

    @FunctionalInterface
    private interface IdentifierFetcher<T extends XRoadId> {
        T get(T example);
    }
}
