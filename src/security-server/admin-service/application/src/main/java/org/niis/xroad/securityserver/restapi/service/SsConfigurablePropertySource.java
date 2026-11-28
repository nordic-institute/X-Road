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
package org.niis.xroad.securityserver.restapi.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.ActiveCountryProfile;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.DeploymentMode;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.common.properties.config.keys.GlobalConfConfigKeys;
import org.niis.xroad.common.properties.config.keys.HealthCheckConfigKeys;
import org.niis.xroad.common.properties.config.keys.MessageLogArchiverConfigKeys;
import org.niis.xroad.common.properties.config.keys.MonitorConfigKeys;
import org.niis.xroad.common.properties.config.keys.OcspVerifierConfigKeys;
import org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.common.properties.config.keys.ServerConfConfigKeys;
import org.niis.xroad.messagelog.MessageLogEncryptionConfigKeys;
import org.niis.xroad.restapi.service.ConfigurablePropertySource;
import org.niis.xroad.restapi.service.RestartTarget;
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.niis.xroad.signer.common.config.SignerKeyConfigKeys;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * Security Server's {@link ConfigurablePropertySource}: the providers whose exposed keys make up the
 * Security Server's system-parameters catalogue, their category-to-scope mapping and the restart target of a
 * category or key for the deployment mode.
 */
@Component
@RequiredArgsConstructor
public class SsConfigurablePropertySource implements ConfigurablePropertySource {

    private static final List<ConfigKeyProvider> SS_PROVIDERS = List.of(
            CommonConfigKeys.instance(),
            CommonRpcConfigKeys.instance(),
            ProxyConfigKeys.instance(),
            ConfClientConfigKeys.instance(),
            DspConfigKeys.instance(),
            OpMonitorConfigKeys.instance(),
            AuxiliaryServiceConfigKeys.instance(),
            AdminServiceConfigKeys.instance(),
            OcspVerifierConfigKeys.instance(),
            GlobalConfConfigKeys.instance(),
            ServerConfConfigKeys.instance(),
            HealthCheckConfigKeys.instance(),
            SignerConfigKeys.instance(),
            SignerKeyConfigKeys.instance(),
            MonitorConfigKeys.instance(),
            MessageLogArchiverConfigKeys.instance(),
            MessageLogEncryptionConfigKeys.instance());

    private final DeploymentMode deploymentMode;
    private final ActiveCountryProfile countryProfile;

    @Override
    public DeploymentMode deploymentMode() {
        return deploymentMode;
    }

    @Override
    public ActiveCountryProfile countryProfile() {
        return countryProfile;
    }

    @Override
    public List<ConfigKeyProvider> getConfigKeyProviders() {
        return SS_PROVIDERS;
    }

    @Override
    public String categoryToScope(Category category) {
        return switch (category) {
            case PROXY -> "proxy";
            case SIGNER -> "signer";
            case PROXY_UI_API -> "proxy-ui-api";
            case OP_MONITOR_DAEMON -> "op-monitor-daemon";
            case MONITOR -> "monitor";
            case CONFIGURATION_CLIENT -> "configuration-client";
            case AUXILIARY_SERVICE -> "auxiliary-service";
            case MESSAGE_LOG_ARCHIVER -> "message-log-archiver";
            case COMMON -> null;
            default -> throw XrdRuntimeException.systemInternalError(
                    "Unmapped category for configurable properties catalogue: " + category);
        };
    }

    @Override
    public RestartTarget restartTarget(Category category) {
        return switch (category) {
            case PROXY -> RestartTarget.of(serviceName("xroad-proxy", "proxy"));
            case SIGNER -> RestartTarget.of(serviceName("xroad-signer", "signer"));
            case PROXY_UI_API -> RestartTarget.of(serviceName("xroad-proxy-ui-api", "proxy-ui-api"));
            case OP_MONITOR_DAEMON -> RestartTarget.of(serviceName("xroad-opmonitor", "op-monitor"));
            case MONITOR -> RestartTarget.of(serviceName("xroad-monitor", "monitor"));
            case CONFIGURATION_CLIENT -> RestartTarget.of(serviceName("xroad-confclient", "configuration-client"));
            case AUXILIARY_SERVICE -> RestartTarget.of(serviceName("xroad-auxiliary-service", "auxiliary-service"));
            case MESSAGE_LOG_ARCHIVER -> RestartTarget.nextRun();
            case COMMON -> RestartTarget.allServices();
            default -> throw XrdRuntimeException.systemInternalError(
                    "Unmapped category for configurable properties catalogue: " + category);
        };
    }

    @Override
    public RestartTarget restartTarget(ConfigKey<?> key) {
        if (ProxyConfigKeys.CLIENT_PROXY_CLIENT_HTTPS_PORT.key().equals(key.key())) {
            return RestartTarget.of(Stream.of(Category.PROXY, Category.PROXY_UI_API)
                    .flatMap(category -> restartTarget(category).services().stream())
                    .toArray(String[]::new));
        }
        return restartTarget(key.category());
    }

    private String serviceName(String nativeName, String containerizedName) {
        return deploymentMode == DeploymentMode.CONTAINERIZED ? containerizedName : nativeName;
    }
}
