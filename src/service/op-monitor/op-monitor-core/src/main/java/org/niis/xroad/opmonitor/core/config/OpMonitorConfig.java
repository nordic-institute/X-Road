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
package org.niis.xroad.opmonitor.core.config;

import io.smallrye.config.SmallRyeConfig;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.Source;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.properties.dbsource.DbSourceConfig;
import org.niis.xroad.common.rpc.RpcProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class OpMonitorConfig {

    static final Path SETTINGS_FILE = Path.of("/etc/xroad/conf.d/op-monitor-settings.yaml");

    private static final String OPENBAO = "openbao";
    private static final String FILE = "file";
    private static final String OPENBAO_TOKEN_ENV_VAR = "XROAD_SECRET_STORE_TOKEN";
    private static final String NOT_CONFIGURED_MESSAGE = "op-monitor is not configured: %s is %s (from %s) "
            + "but no OpenBao token is set (%s is missing or blank). "
            + "To run without OpenBao, set %s to %s and set %s, %s and %s in %s, then restart xroad-opmonitor. "
            + "Otherwise provide an OpenBao token.";

    private Path settingsFile = SETTINGS_FILE;

    @ApplicationScoped
    XRoadConfig xRoadConfig(@ConfigProperty(name = "quarkus.application.name") String appName,
                            @ConfigProperty(name = "quarkus.vault.authentication.client-token")
                            Optional<String> openBaoToken) {
        var deploymentMode = deploymentMode();
        var config = buildConfig(appName, deploymentMode);
        requireIdentityConfigured(config, deploymentMode, DbSourceConfig.loadValues(appName).getUrl() != null,
                openBaoToken);
        return config;
    }

    XRoadConfig buildConfig(String appName, DeploymentMode deploymentMode) {
        var builder = XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .register(OpMonitorConfigKeys.instance())
                .register(HealthCheckConfigKeys.instance())
                .deploymentMode(deploymentMode);
        if (deploymentMode == DeploymentMode.NATIVE) {
            builder.fileOverrides(settingsFile, SettingsFileReader.read(settingsFile));
        } else if (Files.exists(settingsFile)) {
            log.warn("Settings file {} is ignored: a containerized op-monitor reads its settings from the "
                    + "configuration database and the container defaults only", settingsFile);
        }
        return builder.dbOverrides(appName).build();
    }

    static void requireIdentityConfigured(XRoadConfig config, DeploymentMode deploymentMode,
                                          boolean configurationDatabaseConfigured, Optional<String> openBaoToken) {
        if (deploymentMode != DeploymentMode.NATIVE || configurationDatabaseConfigured
                || !config.value(CommonRpcConfigKeys.USE_TLS)
                || !OPENBAO.equalsIgnoreCase(config.value(OpMonitorConfigKeys.RPC_IDENTITY_SOURCE))
                || openBaoToken.filter(token -> !token.isBlank()).isPresent()) {
            return;
        }
        var identitySource = config.get(OpMonitorConfigKeys.RPC_IDENTITY_SOURCE);
        throw XrdRuntimeException.systemInternalError(NOT_CONFIGURED_MESSAGE.formatted(
                OpMonitorConfigKeys.RPC_IDENTITY_SOURCE.key(), identitySource.value(),
                sourceDescription(identitySource.source()), OPENBAO_TOKEN_ENV_VAR,
                OpMonitorConfigKeys.RPC_IDENTITY_SOURCE.key(), FILE,
                OpMonitorConfigKeys.RPC_FILE_IDENTITY_PRIVATE_KEY_PATH.key(),
                OpMonitorConfigKeys.RPC_FILE_IDENTITY_CERTIFICATE_PATH.key(),
                OpMonitorConfigKeys.RPC_FILE_IDENTITY_TRUSTED_CA_PATH.key(),
                SETTINGS_FILE));
    }

    private static String sourceDescription(Source source) {
        return switch (source) {
            case FILE -> "the settings file";
            case DB -> "the database";
            default -> "the default";
        };
    }

    private static DeploymentMode deploymentMode() {
        var profiles = ConfigProvider.getConfig().unwrap(SmallRyeConfig.class).getProfiles();
        return profiles.contains("containerized") ? DeploymentMode.CONTAINERIZED : DeploymentMode.NATIVE;
    }

    @ApplicationScoped
    RpcProperties rpcProperties(XRoadConfig xRoadConfig) {
        return new OpMonitorRpcProperties(xRoadConfig);
    }

    @ApplicationScoped
    OpMonitorProperties opMonitorProperties(XRoadConfig xRoadConfig) {
        return new OpMonitorProperties(xRoadConfig);
    }

    @ApplicationScoped
    OpMonitorServerProperties opMonitorServerProperties(XRoadConfig xRoadConfig) {
        return new OpMonitorServerProperties(xRoadConfig);
    }

}
