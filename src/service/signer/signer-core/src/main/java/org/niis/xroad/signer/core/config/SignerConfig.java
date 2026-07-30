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
package org.niis.xroad.signer.core.config;

import io.quarkus.runtime.Startup;
import io.quarkus.vault.VaultKVSecretEngine;
import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.healthcheck.HealthCheckProperties;
import org.niis.xroad.common.healthcheck.XRoadHealthCheckProperties;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.GlobalConfConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.XRoadRpcProperties;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultClient;
import org.niis.xroad.confclient.rpc.ConfClientRpcChannelProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SoftwareTokenSignerRpcChannelProperties;
import org.niis.xroad.signer.common.config.SignerKeyConfigKeys;
import org.niis.xroad.signer.common.config.SignerKeyProperties;
import org.niis.xroad.signer.core.certmanager.OcspClientWorker;
import org.niis.xroad.signer.core.job.OcspClientExecuteScheduler;
import org.niis.xroad.signer.core.job.OcspClientExecuteSchedulerImpl;

import java.util.List;
import java.util.Map;

@Slf4j
public class SignerConfig {

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(SignerConfigKeys.instance())
                .register(SignerKeyConfigKeys.instance())
                .register(HealthCheckConfigKeys.instance())
                .register(GlobalConfConfigKeys.instance())
                .register(OcspVerifierConfigKeys.instance())
                .deploymentMode(deploymentMode())
                .overrides(profileOverrides())
                .dbOverrides(appName)
                .build();
    }

    /**
     * Profile-scoped defaults the DSL cannot express: a signer serving a Security Server retrieves OCSP
     * responses for its keys, while one serving a configuration proxy has no auth keys and leaves it off.
     * DB overrides still win over these.
     */
    private static Map<String, String> profileOverrides() {
        if (activeProfiles().contains("ss")) {
            return Map.of(SignerConfigKeys.OCSP_RESPONSE_RETRIEVAL_ACTIVE.key(), "true");
        }
        return Map.of();
    }

    @ApplicationScoped
    RpcProperties rpcProperties(XRoadConfig xRoadConfig) {
        return new XRoadRpcProperties(xRoadConfig);
    }

    @ApplicationScoped
    ConfClientRpcChannelProperties confClientRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new ConfClientRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    SignerRpcChannelProperties signerRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new SignerRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    SoftwareTokenSignerRpcChannelProperties softwareTokenSignerRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new SoftwareTokenSignerRpcChannelProperties(xRoadConfig);
    }

    @ApplicationScoped
    SignerRpcServerProperties signerRpcServerProperties(XRoadConfig xRoadConfig) {
        return new SignerRpcServerProperties(xRoadConfig);
    }

    @ApplicationScoped
    HealthCheckProperties healthCheckProperties(XRoadConfig xRoadConfig) {
        return new XRoadHealthCheckProperties(xRoadConfig);
    }

    @ApplicationScoped
    SignerProperties signerProperties(XRoadConfig xRoadConfig) {
        return new SignerProperties(xRoadConfig);
    }

    @ApplicationScoped
    SignerAutologinProperties signerAutologinProperties(XRoadConfig xRoadConfig,
                                                        SignerAutologinProperties.Tokens tokens) {
        return new SignerAutologinProperties(xRoadConfig, tokens);
    }

    @ApplicationScoped
    SignerHwTokenAddonProperties signerHwTokenAddonProperties(XRoadConfig xRoadConfig) {
        return new SignerHwTokenAddonProperties(xRoadConfig);
    }

    @ApplicationScoped
    SoftwarePinHasherProperties softwarePinHasherProperties(XRoadConfig xRoadConfig) {
        return new SoftwarePinHasherProperties(xRoadConfig);
    }

    @ApplicationScoped
    SignerKeyProperties signerKeyProperties(XRoadConfig xRoadConfig) {
        return new SignerKeyProperties(xRoadConfig);
    }

    @ApplicationScoped
    @Startup
    OcspClientExecuteScheduler ocspClientExecuteScheduler(OcspClientWorker ocspClientWorker,
                                                          GlobalConfProvider globalConfProvider,
                                                          SignerProperties signerProperties) {
        if (signerProperties.ocspResponseRetrievalActive()) {
            var scheduler = new OcspClientExecuteSchedulerImpl(ocspClientWorker, globalConfProvider, signerProperties);
            scheduler.init();
            return scheduler;
        } else {
            return new OcspClientExecuteScheduler.NoopScheduler();
        }
    }

    @ApplicationScoped
    VaultClient vaultClient(VaultKVSecretEngine kvSecretEngine) {
        return new QuarkusVaultClient(kvSecretEngine);
    }

    private static DeploymentMode deploymentMode() {
        return activeProfiles().contains("containerized") ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
    }

    private static List<String> activeProfiles() {
        return ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getProfiles();
    }
}
