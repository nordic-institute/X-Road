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

package org.niis.xroad.common.properties.dbsource.quarkus;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalConnectionFactoryConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalConnectionPoolConfigurationSupplier;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.niis.xroad.common.properties.dbsource.DbSourceConfig;

import javax.sql.DataSource;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Collections;

import static java.util.Optional.ofNullable;

@Slf4j
public class DbConfigSourceFactory implements ConfigSourceFactory {

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        DbSourceConfig config = DbSourceConfig.loadValues(context.getValue("quarkus.application.name").getValue());
        if (config.isEnabled()) {
            log.info("Using DB config source.");
            try {
                DataSource dataSource = initDataSource(config);
                return Collections.singletonList(new DbConfigSource(dataSource, config));
            } catch (SQLException e) {
                log.error("Failed to initialize DB configuration source", e);
                // todo: fail?
            }
        } else {
            log.info("DB config source is disabled.");
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private DataSource initDataSource(DbSourceConfig config) throws SQLException {
        AgroalDataSourceConfigurationSupplier dataSourceConfiguration = new AgroalDataSourceConfigurationSupplier();
        AgroalConnectionPoolConfigurationSupplier poolConfiguration = dataSourceConfiguration.connectionPoolConfiguration();
        AgroalConnectionFactoryConfigurationSupplier connectionFactoryConfiguration = poolConfiguration.connectionFactoryConfiguration();

        poolConfiguration
                .initialSize(1)
                .minSize(1)
                .maxSize(10)
                .acquisitionTimeout(Duration.ofSeconds(5));

        connectionFactoryConfiguration.jdbcUrl(config.getUrl());

        ofNullable(config.getUsername())
                .ifPresent(username -> connectionFactoryConfiguration.credential(new NamePrincipal(config.getUsername())));

        ofNullable(config.getPassword())
                .ifPresent(password -> connectionFactoryConfiguration.credential(new SimplePassword(password)));

        return AgroalDataSource.from(dataSourceConfiguration.get());
    }

}
