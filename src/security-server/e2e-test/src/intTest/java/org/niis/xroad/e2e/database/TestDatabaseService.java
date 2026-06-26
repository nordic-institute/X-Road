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

package org.niis.xroad.e2e.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.e2e.EnvSetup;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestDatabaseService implements DisposableBean {

    private static final long INITIALIZATION_FAIL_TIMEOUT_MS = 5_000;
    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration CONNECT_POLL_INTERVAL = Duration.ofSeconds(2);

    private final Map<String, HikariDataSource> messagelogDataSources = new HashMap<>();
    private final Map<String, NamedParameterJdbcTemplate> messagelogTemplates = new HashMap<>();

    @Autowired
    private EnvSetup envSetup;

    public NamedParameterJdbcTemplate getMessagelogTemplate(String env) {
        var existing = messagelogTemplates.get(env);
        if (existing != null) {
            return existing;
        }
        var dataSource = connectWithRetry(env);
        var template = new NamedParameterJdbcTemplate(dataSource);
        messagelogDataSources.put(env, dataSource);
        messagelogTemplates.put(env, template);
        return template;
    }

    private HikariDataSource connectWithRetry(String env) {
        var ref = new AtomicReference<HikariDataSource>();
        await()
                .atMost(CONNECT_TIMEOUT)
                .pollInterval(CONNECT_POLL_INTERVAL)
                .ignoreExceptions()
                .until(() -> {
                    log.info("Connecting to {} messagelog DB..", env);
                    ref.set(createDataSource(env, EnvSetup.DB_MESSAGELOG, "messagelog", "messagelog"));
                    return true;
                });
        return ref.get();
    }

    private HikariDataSource createDataSource(String env, String service, String database, String username) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getJdbcUrl(env, service, database));
        config.setUsername(username);
        config.setPassword("secret");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        config.setInitializationFailTimeout(INITIALIZATION_FAIL_TIMEOUT_MS);
        return new HikariDataSource(config);
    }

    private String getJdbcUrl(String env, String service, String database) {
        var mapping = envSetup.getContainerMapping(env, service, EnvSetup.Port.DB);
        return String.format("jdbc:postgresql://%s:%d/%s?sslmode=disable",
                mapping.host(),
                mapping.port(),
                database);
    }

    @Override
    public void destroy() {
        messagelogDataSources.values().forEach(HikariDataSource::close);
    }
}
