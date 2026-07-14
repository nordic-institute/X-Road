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
package org.niis.xroad.ds.controlplane.application;

import io.quarkus.arc.Unremovable;
import io.quarkus.vault.VaultKVSecretEngine;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.niis.xroad.common.properties.config.keys.ServerConfConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.XRoadRpcProperties;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultClient;
import org.niis.xroad.confclient.rpc.ConfClientRpcChannelProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfCommonProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.XRoadServerConfProperties;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.ServerConfFactory;

@Slf4j
class ControlPlaneBeansConfig {

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(OcspVerifierConfigKeys.instance())
                .register(ServerConfConfigKeys.instance())
                .deploymentMode(deploymentMode())
                .dbOverrides(appName)
                .build();
    }

    @ApplicationScoped
    ServerConfCommonProperties serverConfCommonProperties(XRoadConfig xRoadConfig) {
        return new XRoadServerConfProperties(xRoadConfig);
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
    ConfClientRpcChannelProperties confClientRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new ConfClientRpcChannelProperties(xRoadConfig);
    }

    @Unremovable
    @ApplicationScoped
    VaultClient vaultClient(VaultKVSecretEngine kvSecretEngine) {
        return new QuarkusVaultClient(kvSecretEngine);
    }

    @Unremovable
    @ApplicationScoped
    ServerConfProvider serverConfProvider(ServerConfDatabaseCtx databaseCtx,
                                          ServerConfCommonProperties serverConfProperties,
                                          GlobalConfProvider globalConfProvider,
                                          VaultClient vaultClient) {
        log.debug("Creating ServerConfProvider");
        return ServerConfFactory.create(databaseCtx, globalConfProvider, vaultClient, serverConfProperties);
    }
}
