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
package org.niis.xroad.cs.admin.core.config;

import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.XRoadConfigOverrides;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.DataspaceConfigKeys;
import org.niis.xroad.common.properties.config.keys.GlobalConfConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;

/**
 * Wires the {@link XRoadConfig} resolver for the central-server admin-service (DB overrides via the
 * {@code configuration_properties} table + packaged DSL defaults) and the admin-service property beans
 * that resolve through it. Registers the shared RPC channel keys (signer client) and the admin-service keys.
 */
@Configuration(proxyBeanMethods = false)
public class XRoadConfigBeanConfiguration {

    /**
     * Admin-service's deliberate deviations from the shared DSL defaults. Everything else resolves from the
     * packaged defaults (equal to the values in application.yml) or container defaults.
     */
    @Bean
    XRoadConfigOverrides xRoadConfigOverrides(@Value("${XROAD_HOST:localhost}") String rpcCertCommonName) {
        return new XRoadConfigOverrides(Map.of(
                "xroad.admin-service.tls.certificate-provisioning.common-name", rpcCertCommonName));
    }

    @Bean
    XRoadConfig xRoadConfig(@Value("${spring.application.name:centralserver-admin-service}") String appName,
                            Environment environment, XRoadConfigOverrides overrides) {
        var deploymentMode = environment.matchesProfiles("containerized")
                ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(CsAdminServiceConfigKeys.instance())
                .register(OcspVerifierConfigKeys.instance())
                .register(GlobalConfConfigKeys.instance())
                .register(DataspaceConfigKeys.instance())
                .overrides(overrides.values())
                .deploymentMode(deploymentMode)
                .dbOverrides(appName)
                .build();
    }

    @Bean
    AdminServiceProperties adminServiceProperties(XRoadConfig xRoadConfig) {
        return new AdminServiceProperties(xRoadConfig);
    }

    @Bean
    AdminServiceTlsProperties adminServiceTlsProperties(XRoadConfig xRoadConfig) {
        return new AdminServiceTlsProperties(xRoadConfig);
    }

    @Bean
    AdminServiceGlobalConfigProperties adminServiceGlobalConfigProperties(XRoadConfig xRoadConfig) {
        return new AdminServiceGlobalConfigProperties(xRoadConfig);
    }

    @Bean
    ManagementServiceConfigProperties managementServiceConfigProperties(XRoadConfig xRoadConfig) {
        return new ManagementServiceConfigProperties(xRoadConfig);
    }
}
