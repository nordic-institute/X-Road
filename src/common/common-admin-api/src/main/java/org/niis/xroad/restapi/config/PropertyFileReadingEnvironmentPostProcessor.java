/*
 * The MIT License
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
package org.niis.xroad.restapi.config;

import ee.ria.xroad.common.SystemProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * EnvironmentPostProcessor which reads the properties from a file.
 * This customization is needed since we want to read Spring properties
 * from a file (such as db.properties), whose location may be customized
 * in {@link SystemProperties}
 */
@Profile("nontest")
public abstract class PropertyFileReadingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private final DeferredLog log = new DeferredLog();

    protected abstract String getPropertySourceName();

    protected abstract String getPropertyFilePath();

    protected abstract boolean isSupported(String propertyName);

    protected abstract String mapToSpringPropertyName(String originalPropertyName);

    /**
     * Is property file mandatory - if it is, throws exception if file cannot be read
     *
     * @return
     */
    protected boolean isPropertyFileMandatory() {
        return true;
    }

    protected void initialize() {
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment,
                                       SpringApplication application) {
        application.addInitializers(ctx -> log.replayTo(PropertyFileReadingEnvironmentPostProcessor.class));
        // we read properties from file only if not testing
        if (environment.acceptsProfiles(Profiles.of("nontest"))) {
            // called twice since IntelliJ tests load the class twice
            initialize();
            try {
                Properties originalProperties = new Properties();

                try (FileInputStream originalPropertiesStream = new FileInputStream(getPropertyFilePath())) {
                    originalProperties.load(originalPropertiesStream);
                } catch (IOException e) {
                    if (isPropertyFileMandatory()) {
                        throw e;
                    } else {
                        log.info(String.format("Property file %s not found", getPropertyFilePath()));
                    }
                }

                Map<String, Object> springPropertiesMap = new HashMap<>();
                for (String originalPropertyName : originalProperties.stringPropertyNames()) {
                    if (isSupported(originalPropertyName)) {
                        String value = originalProperties.getProperty(originalPropertyName);
                        springPropertiesMap.put(
                                mapToSpringPropertyName(originalPropertyName),
                                value);
                    }
                }
                environment.getPropertySources().addFirst(new MapPropertySource(
                        getPropertySourceName(), springPropertiesMap));
            } catch (Exception e) {
                log.error("Failed to process properties file: " + getPropertyFilePath(), e);
            }
        }
    }
}
