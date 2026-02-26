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
package org.niis.xroad.common.healthcheck;

import ee.ria.xroad.common.db.DatabaseCtx;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HibernateDatabaseReadinessCheckTest {

    private static final String CHECK_NAME = "TEST_DB_CHECK";
    private static final String DB_NAME = "testdb";

    @Mock
    private DatabaseCtx databaseCtx;

    private HibernateDatabaseReadinessCheck createCheck(DatabaseCtx ctx) {
        return new HibernateDatabaseReadinessCheck() {
            @Override
            protected DatabaseCtx getDatabaseCtx() {
                return ctx;
            }

            @Override
            protected String getCheckName() {
                return CHECK_NAME;
            }

            @Override
            protected String getDatabaseName() {
                return DB_NAME;
            }
        };
    }

    @Test
    void nullDatabaseCtxReturnsDown() {
        var response = createCheck(null).call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals(CHECK_NAME, response.getName());
        assertEquals("DatabaseCtx not configured", response.getData().orElseThrow().get("error"));
        assertEquals(DB_NAME, response.getData().orElseThrow().get("database"));
    }

    @Test
    void successfulQueryReturnsUp() {
        when(databaseCtx.doInTransaction(any())).thenReturn(null);

        var response = createCheck(databaseCtx).call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(CHECK_NAME, response.getName());
        assertEquals(DB_NAME, response.getData().orElseThrow().get("database"));
    }

    @Test
    void queryThrowsExceptionReturnsDown() {
        doThrow(new RuntimeException("Connection refused")).when(databaseCtx).doInTransaction(any());

        var response = createCheck(databaseCtx).call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("Connection refused", response.getData().orElseThrow().get("error"));
    }
}
