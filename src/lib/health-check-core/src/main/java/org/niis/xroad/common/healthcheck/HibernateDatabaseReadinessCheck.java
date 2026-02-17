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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.ERROR;

@Slf4j
public abstract class HibernateDatabaseReadinessCheck implements HealthCheck {

    private static final String DATABASE = "database";

    /**
     * Get the DatabaseCtx to check connectivity for.
     *
     * @return the DatabaseCtx instance
     */
    protected abstract DatabaseCtx getDatabaseCtx();

    /**
     * Get the name for this health check (used in response).
     *
     * @return the health check name
     */
    protected abstract String getCheckName();

    /**
     * Get the database name for logging and response data.
     *
     * @return the database name
     */
    protected abstract String getDatabaseName();

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name(getCheckName());

        DatabaseCtx databaseCtx = getDatabaseCtx();
        if (databaseCtx == null) {
            log.warn("DatabaseCtx for {} is not configured", getDatabaseName());
            return builder.down()
                    .withData(ERROR, "DatabaseCtx not configured")
                    .withData(DATABASE, getDatabaseName())
                    .build();
        }

        try {
            // Execute a simple query to verify database connectivity
            databaseCtx.doInTransaction(session -> {
                session.createNativeQuery("SELECT 1", Integer.class).getSingleResult();
                return null;
            });
            return builder.up()
                    .withData(DATABASE, getDatabaseName())
                    .build();
        } catch (Exception e) {
            log.warn("Database connectivity check failed for {}: {}", getDatabaseName(), e.getMessage());
            return builder.down()
                    .withData(ERROR, e.getMessage())
                    .withData(DATABASE, getDatabaseName())
                    .build();
        }
    }
}
