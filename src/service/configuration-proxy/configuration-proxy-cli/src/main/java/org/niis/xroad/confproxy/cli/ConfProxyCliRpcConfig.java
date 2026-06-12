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
package org.niis.xroad.confproxy.cli;

import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.XRoadRpcProperties;
import org.niis.xroad.confclient.rpc.ConfClientRpcChannelProperties;
import org.niis.xroad.confclient.rpc.XRoadConfClientRpcChannelProperties;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SoftwareTokenSignerRpcChannelProperties;
import org.niis.xroad.signer.client.XRoadSignerRpcChannelProperties;
import org.niis.xroad.signer.client.XRoadSoftwareTokenSignerRpcChannelProperties;

import java.util.HashMap;
import java.util.Map;

class ConfProxyCliRpcConfig {

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName) {
        return XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .register(CommonRpcConfigKeys.instance())
                .register(ConfClientConfigKeys.instance())
                .register(ConfProxyConfigKeys.instance())
                .deploymentMode(deploymentMode())
                .overrides(smallryeOverrides())
                .dbOverrides(appName)
                .build();
    }

    private static DeploymentMode deploymentMode() {
        var profiles = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getProfiles();
        return profiles.contains("containerized") ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
    }

    @ApplicationScoped
    RpcProperties rpcProperties(XRoadConfig xRoadConfig) {
        return new XRoadRpcProperties(xRoadConfig);
    }

    @ApplicationScoped
    CommonProperties commonProperties(XRoadConfig xRoadConfig) {
        return new XRoadConfigCommonProperties(xRoadConfig);
    }

    @ApplicationScoped
    ConfigurationProxyProperties configurationProxyProperties(XRoadConfig xRoadConfig) {
        return new ConfigurationProxyProperties(xRoadConfig);
    }

    @ApplicationScoped
    ConfClientRpcChannelProperties confClientRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new XRoadConfClientRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    SignerRpcChannelProperties signerRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new XRoadSignerRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    SoftwareTokenSignerRpcChannelProperties softwareTokenSignerRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new XRoadSoftwareTokenSignerRpcChannelProperties(xRoadConfig);
    }

    private static Map<String, String> smallryeOverrides() {
        var overrides = new HashMap<String, String>();
        ConfigProvider.getConfig()
                .getOptionalValue("xroad.common-rpc.use-tls", String.class)
                .ifPresent(v -> overrides.put("xroad.common-rpc.use-tls", v));
        return overrides;
    }

}
