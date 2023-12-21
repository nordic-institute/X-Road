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
package org.niis.xroad.cs.test.container.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.exception.DatabaseException;
import liquibase.integration.spring.SpringLiquibase;
import liquibase.logging.core.AbstractLogService;
import liquibase.ui.LoggerUIService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * An extension of {@link  SpringLiquibase} which allows manual liquibase execution.
 */
@Slf4j
@Component
public class LiquibaseExecutor extends SpringLiquibase {
    private static final Logger LIQUIBASE_LOGGER = LoggerFactory.getLogger("liquibase");
    private final PostgresContextualContainer postgresContextualContainer;

    public LiquibaseExecutor(PostgresContextualContainer postgresContextualContainer) {
        super();
        setShouldRun(false);
        setDropFirst(true);

        setChangeLog("classpath:test-data/centerui-int-test-changelog.xml");
        setContexts("int-test");

        this.postgresContextualContainer = postgresContextualContainer;
    }

    /**
     * Executes changesets.
     * Will drop any existing data in database.
     */
    @SneakyThrows
    public void executeChangesets() {
        var stopWatch = StopWatch.createStarted();

        if (getDataSource() == null) {
            setDataSource(createDataSource());
        }

        Scope.child(createLiquibaseLoggableScope(), () -> {
            executeUpdate();
            log.info("Liquibase schema initialized in {} ms.", stopWatch.getTime(TimeUnit.MILLISECONDS));
        });
    }

    @SneakyThrows
    private void executeUpdate() {
        try (var c = getDataSource().getConnection(); Liquibase liquibase = createLiquibase(c)) {
            performUpdate(liquibase);
        } catch (SQLException e) {
            throw new DatabaseException(e);
        }
    }

    private DataSource createDataSource() {
        var config = new HikariConfig();
        config.setMaximumPoolSize(1);
        config.setJdbcUrl(postgresContextualContainer.getExternalJdbcUrl());
        config.setUsername(postgresContextualContainer.getTestContainer().getUsername());
        config.setPassword(postgresContextualContainer.getTestContainer().getPassword());

        return new HikariDataSource(config);
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
