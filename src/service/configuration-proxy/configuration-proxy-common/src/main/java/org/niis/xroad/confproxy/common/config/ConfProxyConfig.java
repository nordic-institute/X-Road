/*
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
package org.niis.xroad.confproxy.common.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.confclient.common.service.ConfigurationClientService;
import org.niis.xroad.confclient.common.service.HttpUrlConnectionConfigurer;

import static org.niis.xroad.common.properties.config.keys.CommonConfigKeys.TEMP_FILES_PATH;

public class ConfProxyConfig {

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName) {
        return XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .dbOverrides(appName)
                .build();
    }

    @ApplicationScoped
    HttpUrlConnectionConfigurer httpUrlConnectionConfigurer(ConfClientProperties confClientProperties) {
        return new HttpUrlConnectionConfigurer(confClientProperties);
    }

    @ApplicationScoped
    ConfigurationClientService configurationClientService(HttpUrlConnectionConfigurer httpUrlConnectionConfigurer,
                                                          ConfClientProperties confClientProperties,
                                                          XRoadConfig xRoadConfig) {
        return new ConfigurationClientService(httpUrlConnectionConfigurer, confClientProperties,
                () -> xRoadConfig.value(TEMP_FILES_PATH));
    }
}
