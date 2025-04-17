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

package org.niis.xroad.common.properties.dbsource.spring;

import org.niis.xroad.common.properties.dbsource.CachedDbConfigSource;
import org.niis.xroad.common.properties.dbsource.DbSourceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import javax.sql.DataSource;

import java.util.HashMap;
import java.util.Map;

import static java.util.Optional.ofNullable;
import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

public class DbPropertySourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final DeferredLog log = new DeferredLog();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        application.addInitializers(ctx -> log.replayTo(DbPropertySourceEnvironmentPostProcessor.class));

        String appName = environment.getProperty("spring.application.name");
        DbSourceConfig config = DbSourceConfig.loadValues(appName);
        if (config.isEnabled() && config.getUrl() != null) {
            log.info("Using DB properties source.");

            DataSource dataSource = initDataSource(config);
            CachedDbConfigSource dbSource = new CachedDbConfigSource(dataSource, config);
            Map<String, String> dbProperties = dbSource.getProperties();

            environment.getPropertySources().addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    new MapPropertySource("db-source", new HashMap<>(dbProperties)));
        } else {
            log.info("DB properties source is disabled.");
        }
    }

    private DataSource initDataSource(DbSourceConfig config) {
        DataSourceBuilder<?> dataSourceBuilder = DataSourceBuilder
                .create()
                .driverClassName("org.postgresql.Driver")
                .url(config.getUrl());
        ofNullable(config.getUsername()).ifPresent(dataSourceBuilder::username);
        ofNullable(config.getPassword()).ifPresent(p -> dataSourceBuilder.password(new String(p)));
        return dataSourceBuilder.build();
    }
}
