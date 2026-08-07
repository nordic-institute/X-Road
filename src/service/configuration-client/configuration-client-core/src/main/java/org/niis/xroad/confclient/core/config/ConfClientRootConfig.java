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
package org.niis.xroad.confclient.core.config;

import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Provider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
import org.niis.xroad.common.properties.config.keys.GlobalConfConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.XRoadRpcProperties;
import org.niis.xroad.confclient.common.config.ConfigurationAnchorProvider;
import org.niis.xroad.confclient.common.globalconf.FileBasedProvider;
import org.niis.xroad.confclient.common.repository.GlobalConfSourceLocationRepository;
import org.niis.xroad.confclient.common.repository.GlobalConfSourceLocationRepositoryImpl;
import org.niis.xroad.confclient.common.repository.GlobalConfSourceLocationRepositoryNoopImpl;
import org.niis.xroad.confclient.common.service.ConfigurationClient;
import org.niis.xroad.confclient.common.service.ConfigurationClientService;
import org.niis.xroad.confclient.common.service.ConfigurationDownloader;
import org.niis.xroad.confclient.common.service.HttpUrlConnectionConfigurer;
import org.niis.xroad.confclient.core.globalconf.DBBasedProvider;
import org.niis.xroad.globalconf.util.FSGlobalConfValidator;

import javax.sql.DataSource;

import static org.niis.xroad.common.properties.config.keys.CommonConfigKeys.TEMP_FILES_PATH;

public class ConfClientRootConfig {

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName) {
        return configBuilder()
                .dbOverrides(appName)
                .build();
    }

    public static XRoadConfigBuilder configBuilder() {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(CommonConfigKeys.instance())
                .register(ConfClientConfigKeys.instance())
                .register(HealthCheckConfigKeys.instance())
                .register(GlobalConfConfigKeys.instance())
                .register(OcspVerifierConfigKeys.instance())
                .deploymentMode(deploymentMode());
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
    ConfigurationClientRpcServerProperties configurationClientRpcServerProperties(XRoadConfig xRoadConfig) {
        return new ConfigurationClientRpcServerProperties(xRoadConfig);
    }

    @ApplicationScoped
    ConfigurationClientProperties configurationClientProperties(XRoadConfig xRoadConfig) {
        return new ConfigurationClientProperties(xRoadConfig);
    }

    @ApplicationScoped
    ConfigurationAnchorProvider configurationAnchorProvider(ConfigurationClientProperties configurationClientProperties,
                                                            XRoadConfig xRoadConfig,
                                                            Provider<DataSource> dataSource) {
        return switch (configurationClientProperties.configurationAnchorStorage()) {
            case FILE -> new FileBasedProvider(configurationClientProperties.configurationAnchorFile(),
                    xRoadConfig.value(TEMP_FILES_PATH));
            case DB -> new DBBasedProvider(dataSource.get());
        };
    }

    @ApplicationScoped
    GlobalConfSourceLocationRepository globalConfSourceLocationRepository(Provider<DataSource> dataSource) {
        var dataSourceActive = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.datasource.active", Boolean.class)
                .orElse(true);
        if (dataSourceActive && dataSource.get() != null) {
            return new GlobalConfSourceLocationRepositoryImpl(dataSource.get());
        }
        return new GlobalConfSourceLocationRepositoryNoopImpl();
    }

    @ApplicationScoped
    ConfigurationClient configurationClient(ConfigurationClientProperties configurationClientProperties,
                                            ConfigurationAnchorProvider configurationAnchorProvider,
                                            HttpUrlConnectionConfigurer connectionConfigurer,
                                            GlobalConfSourceLocationRepository globalConfSourceLocationRepository) {
        var downloader = new ConfigurationDownloader(connectionConfigurer, globalConfSourceLocationRepository,
                configurationClientProperties.globalConfDir());
        return new ConfigurationClient(
                configurationAnchorProvider,
                configurationClientProperties.globalConfDir(), downloader, configurationClientProperties.allowedFederations());
    }

    @ApplicationScoped
    FSGlobalConfValidator fsGlobalConfValidator() {
        return new FSGlobalConfValidator();
    }

    @ApplicationScoped
    HttpUrlConnectionConfigurer httpUrlConnectionConfigurer(ConfigurationClientProperties configurationClientProperties) {
        return new HttpUrlConnectionConfigurer(configurationClientProperties);
    }

    @ApplicationScoped
    ConfigurationClientService configurationClientService(HttpUrlConnectionConfigurer httpUrlConnectionConfigurer,
                                                          ConfigurationClientProperties configurationClientProperties,
                                                          XRoadConfig xRoadConfig) {
        return new ConfigurationClientService(
                httpUrlConnectionConfigurer,
                configurationClientProperties,
                () -> xRoadConfig.value(TEMP_FILES_PATH));
    }

}
