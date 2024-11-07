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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.db.DatabaseCtxV2;

import org.junit.BeforeClass;

import java.util.Map;

import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.prepareDatabase;

/**
 * Base class for tests using database.
 */
public class BaseTestUsingDB {

    protected static final Map<String, String> HIBERNATE_PROPERTIES = Map.of(
            "jdbc.batch_size", "100",
            "dialect", "org.hibernate.dialect.HSQLDialect",
            "connection.driver_class", "org.hsqldb.jdbcDriver",
            "connection.url", "jdbc:hsqldb:mem:op-monitor;hsqldb.sqllog=3",
            "connection.username", "opmonitor",
            "connection.password", "opmonitor",
            "hbm2ddl.auto", "create-drop"
    );
    protected static final DatabaseCtxV2 DATABASE_CTX = OpMonitorDaemonDatabaseCtx.create(HIBERNATE_PROPERTIES);
    protected final OperationalDataRecordManager operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX);

    protected BaseTestUsingDB() {
    }

    /**
     * Prepares the testing database.
     *
     * @throws Exception if an error occurs.
     */
    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        prepareDatabase(DATABASE_CTX);
    }
}
