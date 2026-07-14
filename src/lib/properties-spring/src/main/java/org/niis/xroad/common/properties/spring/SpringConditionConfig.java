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

import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.springframework.core.env.Environment;

import java.util.HashMap;

/**
 * Builds a standalone {@link XRoadConfig} for Spring {@code Condition} evaluation, which happens before any
 * bean (including the application's {@code XRoadConfig}) exists. Resolves through the same layers as the
 * application bean — DB overrides + Spring {@code Environment} overrides + packaged DSL defaults — so a
 * bean-registration decision honours DB-sourced config. The underlying DB connection pool is opened and
 * closed within the single read (see {@code CachedDbConfigSource}), so the throwaway config leaks nothing.
 */
public final class SpringConditionConfig {

    private SpringConditionConfig() {
    }

    /**
     * @param environment the condition's environment
     * @param providers   the key providers whose values the condition needs
     * @return an eagerly-resolved {@link XRoadConfig}
     */
    public static XRoadConfig resolve(Environment environment, ConfigKeyProvider... providers) {
        var appName = environment.getProperty("spring.application.name", "");
        var deploymentMode = environment.matchesProfiles("containerized")
                ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
        var builder = XRoadConfigBuilder.create();
        var overrides = new HashMap<String, String>();
        for (var provider : providers) {
            builder.register(provider);
            for (var key : provider.keys()) {
                var value = environment.getProperty(key.key());
                if (value != null) {
                    overrides.put(key.key(), value);
                }
            }
        }
        return builder.overrides(overrides).deploymentMode(deploymentMode).dbOverrides(appName).build();
    }
}
