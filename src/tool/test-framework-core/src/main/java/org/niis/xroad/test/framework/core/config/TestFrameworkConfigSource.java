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
package org.niis.xroad.test.framework.core.config;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.niis.xroad.common.properties.util.DurationConverter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
public class TestFrameworkConfigSource {
    private static final int CONVERTER_PRIORITY = 200;

    private static TestFrameworkConfigSource instance;
    private static String profile = "default";

    private final SmallRyeConfig config;

    public TestFrameworkConfigSource() {
        this.config = buildConfig(TestFrameworkCoreProperties.class, getYamlConfigSource());
    }

    public TestFrameworkCoreProperties getCoreProperties() {
        return config.getConfigMapping(TestFrameworkCoreProperties.class);
    }

    public <T> T buildConfigMapping(Class<T> configMappingClass) {
        return buildConfig(configMappingClass, getYamlConfigSource()).getConfigMapping(configMappingClass);
    }

    private static SmallRyeConfig buildConfig(Class<?> klass, ConfigSource configSource) {
        return new SmallRyeConfigBuilder()
                .withProfile(profile)
                .withSources(configSource)
                .withMapping(klass)
                .withConverter(Duration.class, CONVERTER_PRIORITY, new DurationConverter())
                .addDefaultInterceptors()
                .addDefaultSources()
                .build();
    }

    private YamlConfigSource getYamlConfigSource() {
        URL yamlResource = TestFrameworkConfigSource.class.getClassLoader()
                .getResource("test-automation.yaml");

        if (yamlResource != null) {
            try (InputStream is = yamlResource.openStream()) {
                // Read YAML content as String
                String yamlContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return new YamlConfigSource("test-automation.yaml", yamlContent);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load test-automation.yaml from classpath", e);
            }
        }
        throw new IllegalStateException("test-automation.yaml not found in classpath");
    }

    public static synchronized TestFrameworkConfigSource getInstance() {
        if (instance == null) {
            instance = new TestFrameworkConfigSource();
        }
        return instance;
    }

    public static synchronized void enableCLI() {
        profile = "cli";
        if (instance != null) {
            log.info("Rebuilding TestFrameworkConfigSource for CLI profile");
            instance = new TestFrameworkConfigSource();
        }
    }
}
