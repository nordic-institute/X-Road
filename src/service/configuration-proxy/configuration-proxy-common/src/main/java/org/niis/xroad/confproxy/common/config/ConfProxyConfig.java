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
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.ConfClientConfigKeys;
import org.niis.xroad.confclient.common.config.ConfigurationClientConfig;
import org.niis.xroad.confclient.common.service.ConfigurationClientService;
import org.niis.xroad.confclient.common.service.HttpUrlConnectionConfigurer;

public class ConfProxyConfig {

    @ApplicationScoped
    ConfigurationClientConfig configurationClientConfig(XRoadConfig xRoadConfig) {
        return new ConfigurationClientConfig() {
            @Override
            public String allowedFederations() {
                return xRoadConfig.value(ConfClientConfigKeys.ALLOWED_FEDERATIONS);
            }

            @Override
            public boolean globalConfHostnameVerification() {
                return xRoadConfig.value(ConfClientConfigKeys.GLOBAL_CONF_HOSTNAME_VERIFICATION);
            }

            @Override
            public boolean globalConfTlsCertVerification() {
                return xRoadConfig.value(ConfClientConfigKeys.GLOBAL_CONF_TLS_CERT_VERIFICATION);
            }

            @Override
            public int downloaderConnectTimeout() {
                return xRoadConfig.value(ConfClientConfigKeys.DOWNLOADER_CONNECT_TIMEOUT);
            }

            @Override
            public int downloaderReadTimeout() {
                return xRoadConfig.value(ConfClientConfigKeys.DOWNLOADER_READ_TIMEOUT);
            }

            @Override
            public String globalConfDir() {
                return xRoadConfig.value(ConfClientConfigKeys.GLOBAL_CONF_DIR);
            }
        };
    }

    @ApplicationScoped
    HttpUrlConnectionConfigurer httpUrlConnectionConfigurer(ConfigurationClientConfig confClientConfig) {
        return new HttpUrlConnectionConfigurer(confClientConfig);
    }

    @ApplicationScoped
    ConfigurationClientService configurationClientService(HttpUrlConnectionConfigurer httpUrlConnectionConfigurer,
                                                          ConfigurationClientConfig confClientConfig,
                                                          CommonProperties commonProperties) {
        return new ConfigurationClientService(httpUrlConnectionConfigurer, confClientConfig, commonProperties::tempFilesPath);
    }
}
