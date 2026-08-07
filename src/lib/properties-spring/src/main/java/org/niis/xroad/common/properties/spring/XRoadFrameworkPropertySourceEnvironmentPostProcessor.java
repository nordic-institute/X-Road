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

package org.niis.xroad.common.properties.spring;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.FrameworkPublishedConfig;
import org.niis.xroad.common.properties.config.keys.ConfigKeyProviders;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;

import static org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

/**
 * Publishes the stored overrides of the keys marked {@link ConfigKey#publishedToFramework()} into the
 * Spring {@code Environment}, so a framework setting that interpolates one — e.g.
 * {@code spring.servlet.multipart.max-file-size: ${xroad.proxy-ui-api.request-size-limit-binary-upload}}
 * — sees the value an operator stored.
 *
 * <p>Replaces the whole-table {@code db-source} property source: only flagged keys are published, and
 * every other {@code xroad.*} value is read through {@code XRoadConfig}. The property source is added
 * right after the system environment, keeping the precedence the retired one had.
 */
public class XRoadFrameworkPropertySourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "xroad-framework-source";

    private final DeferredLog log = new DeferredLog();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        application.addInitializers(
                ctx -> log.replayTo(XRoadFrameworkPropertySourceEnvironmentPostProcessor.class));

        var appName = environment.getProperty("spring.application.name");
        var storedOverrides = FrameworkPublishedConfig.storedOverrides(appName, ConfigKeyProviders.allProviders());
        if (storedOverrides.isEmpty()) {
            return;
        }

        log.info("Publishing stored override(s) of framework-visible keys: " + storedOverrides.keySet());
        environment.getPropertySources().addAfter(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(SOURCE_NAME, new HashMap<>(storedOverrides)));
    }
}
