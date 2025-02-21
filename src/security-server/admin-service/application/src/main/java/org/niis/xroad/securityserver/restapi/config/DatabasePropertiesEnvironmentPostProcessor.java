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
package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.SystemProperties;

import org.niis.xroad.restapi.config.PropertyFileReadingEnvironmentPostProcessor;
import org.springframework.context.annotation.Profile;

import java.util.Map;

/**
 * Load datasource properties from db.properties file.
 * Non-datasource properties are handled by {@link PropertyFileReadingHibernateCustomizer}
 */
@Profile("nontest")
public class DatabasePropertiesEnvironmentPostProcessor extends PropertyFileReadingEnvironmentPostProcessor {

    private static final Map<String, String> DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES = Map.of(
            "xroad.server-conf.hibernate.connection.username", "spring.datasource.username",
            "xroad.server-conf.hibernate.connection.password", "spring.datasource.password",
            "xroad.server-conf.hibernate.connection.url", "spring.datasource.url",
            "xroad.server-conf.hibernate.hikari.dataSource.currentSchema", "spring.datasource.hikari.data-source-properties.currentSchema"
    );

    @Override
    protected void initialize() {
        super.initialize();
        SecurityServerSystemPropertiesInitializer.initialize();
    }

    @Override
    protected String getPropertySourceName() {
        return "fromDbPropertiesFile";
    }

    @Override
    protected String getPropertyFilePath() {
        return SystemProperties.getDatabasePropertiesFile();
    }

    @Override
    protected boolean isSupported(String propertyName) {
        return DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES.containsKey(propertyName);
    }

    @Override
    protected String mapToSpringPropertyName(String originalPropertyName) {
        return DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES.get(originalPropertyName);
    }
}
