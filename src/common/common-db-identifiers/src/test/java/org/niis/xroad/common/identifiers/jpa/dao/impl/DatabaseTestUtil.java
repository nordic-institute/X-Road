/*
 * The MIT License
 *
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
package org.niis.xroad.common.identifiers.jpa.dao.impl;

import ee.ria.xroad.common.db.DatabaseCtx;

import lombok.experimental.UtilityClass;

import java.util.Map;

/**
 * Contains server conf test utility methods.
 */
@UtilityClass
public class DatabaseTestUtil {

    public static Map<String, String> hibernateProperties = Map.of(
            "dialect", "org.hibernate.dialect.HSQLDialect",
            "connection.driver_class", "org.hsqldb.jdbcDriver",
            "connection.url", "jdbc:hsqldb:mem:testdb",
            "connection.username", "testdb",
            "connection.password", "testdb",
            "hbm2ddl.auto", "create-drop"
    );

    /**
     * Creates in-memory test database and fills it with test data.
     *
     * @throws Exception if an error occurs
     */
    public static void prepareDB(DatabaseCtx ctx) throws Exception {
        prepareDB(ctx, true);
    }

    /**
     * Creates in-memory test database and fills it with test data.
     *
     * @param clean if true, database is cleaned
     * @throws Exception if an error occurs
     */
    public static void prepareDB(DatabaseCtx ctx, boolean clean) throws Exception {
        if (clean) {
            cleanDB(ctx);
        }
    }

    static void cleanDB(DatabaseCtx ctx) throws Exception {
        ctx.doInTransaction(session -> {
            var q = session.createNativeMutationQuery(
                    // Since we are using HSQLDB for tests, we can use
                    // special commands to completely wipe out the database
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();
            return null;
        });
    }


}
