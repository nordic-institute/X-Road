/**
 * The MIT License
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
package org.niis.xroad.restapi.config;

import ee.ria.xroad.common.SystemProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Load datasource properties from db.properties file.
 * Non-datasource properties are handled by {@link PropertyFileReadingHibernateCustomizer}
 */
@Slf4j
@Profile("!test")
public class DatabasePropertiesEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Map<String, String> DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES =
            new HashMap<>();
    static {
        DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES
                .put("serverconf.hibernate.connection.username", "spring.datasource.username");
        DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES
                .put("serverconf.hibernate.connection.password", "spring.datasource.password");
        DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES
                .put("serverconf.hibernate.connection.url", "spring.datasource.url");
    }


    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        // we read db.properties only if not testing
        if (environment.acceptsProfiles(Profiles.of("!test"))) {
            // called twice since IntelliJ tests load the class twice
            SystemPropertiesInitializer.initialize();
            try {
                List<PropertySource<?>> sources = new PropertiesPropertySourceLoader().load(
                        "xroad-db-properties",
                        new FileSystemResource(SystemProperties.getDatabasePropertiesFile()));
                if (sources.size() > 1) {
                    throw new IllegalStateException("expected max 1 db properties file source, "
                            + sources.size());
                }
                PropertySource<?> source = sources.get(0);
                Map<String, Object> springDatasourcePropertiesMap = new HashMap<>();
                Properties systemProperties = System.getProperties();
                for (String dbPropertyName: DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES.keySet()) {
                    String springPropertyName = DB_PROPERTY_NAMES_TO_SPRING_PROPERTIES.get(dbPropertyName);
                    Object propertyValue = source.getProperty(dbPropertyName);
                    log.debug("mapping db property {} to spring property {}",
                            dbPropertyName, springPropertyName);
                    if (systemProperties.contains(dbPropertyName)) {
                        log.debug("overriding db property {} with value from system property",
                                dbPropertyName);
                        propertyValue = systemProperties.getProperty(dbPropertyName);
                    }
                    springDatasourcePropertiesMap.put(springPropertyName, propertyValue);
                }
                environment.getPropertySources().addLast(new MapPropertySource(
                        "fromDbPropertiesFile", springDatasourcePropertiesMap));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
