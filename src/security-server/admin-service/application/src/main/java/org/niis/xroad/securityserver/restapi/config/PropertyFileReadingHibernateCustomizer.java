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

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

/**
 * HibernatePropertiesCustomizer that reads db.properties and sets non-datasource-properties.
 * Datasource properties are handled by {@link DatabasePropertiesEnvironmentPostProcessor}
 */
@Slf4j
@Component
@Profile("nontest")
public class PropertyFileReadingHibernateCustomizer implements HibernatePropertiesCustomizer {

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        // called twice since IntelliJ tests load the class twice
        SecurityServerSystemPropertiesInitializer.initialize();
        Properties dbProperties = new Properties();

        try (FileInputStream dbPropertiesStream = new FileInputStream(SystemProperties.getDatabasePropertiesFile())) {
            dbProperties.load(dbPropertiesStream);
        } catch (IOException ioe) {
            log.warn("db.properties file not found", ioe);
        }

        for (String propertyName : dbProperties.stringPropertyNames()) {
            // currently we have just one datasource, and convert
            // e.g. serverconf.hibernate.jdbc.use_streams_for_binary
            // into hibernate.jdbc.use_streams_for_binary
            if (isServerConfProperty(propertyName)
                    && canBeCustomized(propertyName)) {
                hibernateProperties.put(removeServerConfPartFromName(propertyName),
                        dbProperties.getProperty(propertyName));
            }
        }

        // go through system properties, and apply relevant hibernate properties from there
        // (as in old implementation)
        Properties systemProperties = System.getProperties();
        for (String systemProperty : systemProperties.stringPropertyNames()) {
            if (isServerConfProperty(systemProperty) && canBeCustomized(systemProperty)) {
                hibernateProperties.put(removeServerConfPartFromName(systemProperty),
                        systemProperties.getProperty(systemProperty));
            }
        }
    }

    private boolean canBeCustomized(String propertyName) {
        if (propertyName.contains("hibernate.connection") || propertyName.contains("hibernate.hikari")) {
            log.debug("property {} can't be configured with HibernatePropertiesCustomizer, "
                    + "it is handled in datasource configuration instead", propertyName);
            return false;
        }
        if (propertyName.contains("hibernate.dialect")) {
            log.debug("property {} can't be configured with HibernatePropertiesCustomizer, "
                    + "Hibernate dialect is fixed", propertyName);
            return false;
        }
        return true;
    }

    private String removeServerConfPartFromName(String propertyName) {
        return propertyName.substring(SERVER_CONF_PROPERTY_PREFIX.length());
    }

    private static final String SERVER_CONF_PROPERTY_PREFIX = "serverconf.";

    private boolean isServerConfProperty(String propertyName) {
        return propertyName.startsWith(SERVER_CONF_PROPERTY_PREFIX);
    }
}
