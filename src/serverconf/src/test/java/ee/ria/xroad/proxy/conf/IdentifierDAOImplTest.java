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
        assertCreateRead(() -> ClientId.Conf.create("EE", "class", "code1"));
        assertCreateRead(() -> ClientId.Conf.create("EE", "class", "code2"));
    }

    /**
     * ServiceId.
     * @throws Exception if an error occurs
     */
    @Test
    public void serviceId() throws Exception {
        assertCreateRead(() ->
                ServiceId.Conf.create("EE", "cls", "code", null, "service1"));

        assertCreateRead(() ->
                ServiceId.Conf.create("EE", "cls", "code", null, "service2"));
    }

    /**
     * GlobalGroupId.
     * @throws Exception if an error occurs
     */
    @Test
    public void globalGroupId() throws Exception {
        assertCreateRead(() -> GlobalGroupId.Conf.create("XX", "globalGroup1"));
        assertCreateRead(() -> GlobalGroupId.Conf.create("XX", "globalGroup2"));
    }

    /**
     * LocalGroupId.
     * @throws Exception if an error occurs
     */
    @Test
    public void localGroupId() throws Exception {
        assertCreateRead(() -> LocalGroupId.Conf.create("localGroup1"));
        assertCreateRead(() -> LocalGroupId.Conf.create("localGroup2"));
    }

    /**
     * SecurityServerId.
     * @throws Exception if an error occurs
     */
    @Test
    public void securityServerId() throws Exception {
        assertCreateRead(() ->
                SecurityServerId.Conf.create("XX", "class", "code", "srv1"));

        assertCreateRead(() ->
                SecurityServerId.Conf.create("XX", "class", "code", "srv2"));
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
