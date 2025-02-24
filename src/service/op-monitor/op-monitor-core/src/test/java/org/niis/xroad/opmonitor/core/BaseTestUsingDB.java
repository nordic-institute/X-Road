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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.db.DatabaseCtx;

import org.junit.BeforeClass;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.opmonitor.core.config.OpMonitorDaemonDatabaseConfig;
import org.niis.xroad.opmonitor.core.config.OpMonitorDbProperties;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;

import java.util.Map;

import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.prepareDatabase;

/**
 * Base class for tests using database.
 */
public class BaseTestUsingDB {
    protected static final Map<String, String> HIBERNATE_PROPERTIES = Map.of(
            "xroad.db.opmonitor.hibernate.jdbc.batch_size", "100",
            "xroad.db.opmonitor.hibernate.dialect", "org.hibernate.dialect.HSQLDialect",
            "xroad.db.opmonitor.hibernate.connection.driver_class", "org.hsqldb.jdbcDriver",
            "xroad.db.opmonitor.hibernate.connection.url", "jdbc:hsqldb:mem:op-monitor;hsqldb.sqllog=3",
            "xroad.db.opmonitor.hibernate.connection.username", "opmonitor",
            "xroad.db.opmonitor.hibernate.connection.password", "opmonitor",
            "xroad.db.opmonitor.hibernate.hbm2ddl.auto", "create-drop"
    );

    private static final OpMonitorDbProperties OP_MONITOR_PROPERTIES = ConfigUtils.initConfiguration(OpMonitorDbProperties.class,
            HIBERNATE_PROPERTIES);
    protected static final DatabaseCtx DATABASE_CTX = new OpMonitorDaemonDatabaseConfig().serverConfCtx(OP_MONITOR_PROPERTIES);

    protected OperationalDataRecordManager operationalDataRecordManager =
            new OperationalDataRecordManager(DATABASE_CTX, Integer.parseInt(OpMonitorProperties.DEFAULT_MAX_RECORDS_IN_PAYLOAD));

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
