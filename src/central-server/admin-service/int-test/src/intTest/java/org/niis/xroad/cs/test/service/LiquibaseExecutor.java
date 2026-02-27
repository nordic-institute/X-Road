/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.test.service;

import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.UpdateSummaryEnum;
import liquibase.UpdateSummaryOutputEnum;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.logging.core.AbstractLogService;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import liquibase.ui.LoggerUIService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes Liquibase changesets manually without SpringLiquibase wrapper.
 * Uses Liquibase directly to avoid any internal state accumulation.
 */
@Slf4j
@Component
public class LiquibaseExecutor {
    private static final Logger LIQUIBASE_LOGGER = LoggerFactory.getLogger("liquibase");
    private static final String CHANGELOG_FILE = "test-data/centerui-int-test-changelog.xml";
    private static final String CONTEXTS = "int-test";

    private final ContainerDatabaseProvider containerDatabaseProvider;
    private final Map<String, Object> liquibaseScope;

    public LiquibaseExecutor(ContainerDatabaseProvider containerDatabaseProvider) {
        this.containerDatabaseProvider = containerDatabaseProvider;
        this.liquibaseScope = createLiquibaseLoggableScope();
    }

    /**
     * Executes changesets.
     * Will drop any existing data in database.
     */
    @SneakyThrows
    public void executeChangesets() {
        Scope.child(liquibaseScope, this::executeUpdate);
    }

    @SneakyThrows
    private void executeUpdate() {
        var stopWatch = StopWatch.createStarted();

        String jdbcUrl = containerDatabaseProvider.getJdbcUrl();
        String username = containerDatabaseProvider.getAdminUsername();
        String password = containerDatabaseProvider.getAdminPassword();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Drop all tables in the schema first
            dropAllTablesInSchema(connection);
            long dropTime = stopWatch.getTime(TimeUnit.MILLISECONDS);

            // Create Liquibase instance directly (no SpringLiquibase wrapper)
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();

            // Set change log parameters
            Map<String, String> changeLogParameters = new HashMap<>();
            changeLogParameters.put("db_user", containerDatabaseProvider.getUsername());
            changeLogParameters.put("db_password", containerDatabaseProvider.getPassword());

            try (Liquibase liquibase = new Liquibase(CHANGELOG_FILE, resourceAccessor, database)) {
                changeLogParameters.forEach(liquibase::setChangeLogParameter);
                liquibase.setShowSummary(UpdateSummaryEnum.OFF);
                liquibase.setShowSummaryOutput(UpdateSummaryOutputEnum.LOG);
                liquibase.update(CONTEXTS);
            }

            long liquibaseTime = stopWatch.getTime(TimeUnit.MILLISECONDS) - dropTime;


            long totalTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
            log.info("Liquibase schema initialized in {} ms (drop: {} ms, liquibase: {} ms)",
                    totalTime, dropTime, liquibaseTime);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    @SneakyThrows
    private void dropAllTablesInSchema(java.sql.Connection connection) {
        try (var stmt = connection.createStatement()) {
            var dropTablesSql = """
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                      FOR r IN (
                        SELECT c.relname
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = current_schema()
                          AND c.relkind = 'r'
                          AND c.relname NOT LIKE 'pg_%'
                          AND c.relname NOT LIKE '_pg_%'
                      ) LOOP
                        EXECUTE 'DROP TABLE IF EXISTS ' || quote_ident(r.relname) || ' CASCADE';
                      END LOOP;
                    END $$;
                    """;
            stmt.execute(dropTablesSql);

            var dropSequencesSql = """
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                      FOR r IN (
                        SELECT c.relname
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = current_schema()
                          AND c.relkind = 'S'
                      ) LOOP
                        EXECUTE 'DROP SEQUENCE IF EXISTS ' || quote_ident(r.relname) || ' CASCADE';
                      END LOOP;
                    END $$;
                    """;
            stmt.execute(dropSequencesSql);

            log.debug("Dropped all tables and sequences in current schema");
        } catch (SQLException e) {
            log.debug("Could not drop tables in schema (may not exist): {}", e.getMessage());
        }
    }

    private Map<String, Object> createLiquibaseLoggableScope() {
        final Map<String, Object> scopeValues = new HashMap<>();
        scopeValues.put(Scope.Attr.ui.name(), new LoggerUIService());
        scopeValues.put(Scope.Attr.logService.name(), new AbstractLogService() {
            private final LiquibaseSlf4jLogger logger = new LiquibaseSlf4jLogger(LIQUIBASE_LOGGER);

            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public liquibase.logging.Logger getLog(Class clazz) {
                return logger;
            }
        });
        return scopeValues;
    }
}
