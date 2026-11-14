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
package org.niis.xroad.signer.jpa;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SignerDatabaseCtxSchemaPostgresTest {
    private static final String SCHEMA = "custom_schema";
    private static final String APPLICATION_NAME = "signer-schema-test";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @BeforeAll
    static void startPostgres() throws SQLException {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        POSTGRES.start();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
    }

    @AfterAll
    static void stopPostgres() {
        POSTGRES.stop();
    }

    @Test
    void centralServerPropertiesUseConfiguredSchema() {
        var springDatasource = Map.of(
                "url", POSTGRES.getJdbcUrl(),
                "username", POSTGRES.getUsername(),
                "password", POSTGRES.getPassword(),
                "driver-class-name", "org.postgresql.Driver",
                "hikari.data-source-properties.currentSchema", SCHEMA + ",public",
                "hikari.data-source-properties.ApplicationName", APPLICATION_NAME);

        var ctx = new SignerDatabaseCtx(Optional.empty(), Optional.empty(), Optional.of(springDatasource));
        try {
            assertThat(currentSchema(ctx)).isEqualTo(SCHEMA);
            assertThat(applicationName(ctx)).isEqualTo(APPLICATION_NAME);
        } finally {
            ctx.destroy();
        }
    }

    @Test
    void securityServerPropertiesUseConfiguredSchema() {
        var serverconf = Map.of(
                "connection.url", POSTGRES.getJdbcUrl(),
                "connection.username", POSTGRES.getUsername(),
                "connection.password", POSTGRES.getPassword(),
                "connection.driver_class", "org.postgresql.Driver",
                "hikari.dataSource.currentSchema", SCHEMA + ",public");

        var ctx = new SignerDatabaseCtx(Optional.of(serverconf), Optional.empty(), Optional.empty());
        try {
            assertThat(currentSchema(ctx)).isEqualTo(SCHEMA);
        } finally {
            ctx.destroy();
        }
    }

    private static String currentSchema(SignerDatabaseCtx ctx) {
        return ctx.doInTransaction(session ->
                session.createNativeQuery("SELECT current_schema()", String.class).getSingleResult());
    }

    private static String applicationName(SignerDatabaseCtx ctx) {
        return ctx.doInTransaction(session ->
                session.createNativeQuery("SELECT current_setting('application_name')", String.class).getSingleResult());
    }
}
