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

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
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
import org.niis.xroad.signer.common.config.SignerConfigKeys;
import org.niis.xroad.signer.common.config.SignerKeyConfigKeys;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Security Server's {@link ConfigurablePropertySource}: the providers whose exposed keys make up the
 * Security Server's system-parameters catalogue, and their category-to-scope mapping.
 */
@Component
public class SsConfigurablePropertySource implements ConfigurablePropertySource {

    private static final List<ConfigKeyProvider> SS_PROVIDERS = List.of(
            CommonConfigKeys.instance(),
            CommonRpcConfigKeys.instance(),
            ProxyConfigKeys.instance(),
            ConfClientConfigKeys.instance(),
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
}
