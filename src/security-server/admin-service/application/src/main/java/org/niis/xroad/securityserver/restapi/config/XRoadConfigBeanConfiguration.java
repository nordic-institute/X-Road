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
package org.niis.xroad.securityserver.restapi.config;

import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wires the {@link XRoadConfig} resolver and the admin-service property beans that resolve through it
 * (DB overrides via the {@code configuration_properties} table + packaged DSL defaults).
 */
@Configuration(proxyBeanMethods = false)
public class XRoadConfigBeanConfiguration {

    @Bean
    XRoadConfig xRoadConfig(@Value("${spring.application.name}") String appName, Environment environment) {
        var deploymentMode = environment.matchesProfiles("containerized")
                ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
        var providers = List.<ConfigKeyProvider>of(
                CommonRpcConfigKeys.instance(),
                CommonConfigKeys.instance(),
                AdminServiceConfigKeys.instance(),
                OcspVerifierConfigKeys.instance());
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(CommonConfigKeys.instance())
                .register(AdminServiceConfigKeys.instance())
                .register(OcspVerifierConfigKeys.instance())
                .overrides(springEnvironmentOverrides(providers, environment))
                .deploymentMode(deploymentMode)
                .dbOverrides(appName)
                .build();
    }

    private static Map<String, String> springEnvironmentOverrides(List<ConfigKeyProvider> providers, Environment environment) {
        var result = new HashMap<String, String>();
        for (var provider : providers) {
            for (var key : provider.keys()) {
                var value = environment.getProperty(key.key());
                if (value != null) {
                    result.put(key.key(), value);
                }
            }
        }
        return result;
    }

    @Bean
    AdminServiceProperties adminServiceProperties(XRoadConfig xRoadConfig) {
        return new AdminServiceProperties(xRoadConfig);
    }

    @Bean
    AdminServiceTlsProperties adminServiceTlsProperties(XRoadConfig xRoadConfig) {
        return new AdminServiceTlsProperties(xRoadConfig);
    }
}
