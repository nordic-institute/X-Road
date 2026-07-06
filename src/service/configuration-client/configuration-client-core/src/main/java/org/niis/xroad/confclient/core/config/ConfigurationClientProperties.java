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
package org.niis.xroad.confclient.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.niis.xroad.confclient.common.config.ConfigurationClientConfig;

@ConfigMapping(prefix = "xroad.configuration-client")
public interface ConfigurationClientProperties extends ConfigurationClientConfig {

    @WithName(ConfigurationClientConfig.ALLOWED_FEDERATIONS)
    @WithDefault(ConfigurationClientConfig.ALLOWED_FEDERATIONS_DEFAULT)
    String allowedFederations();

    @WithName(ConfigurationClientConfig.GLOBAL_CONF_HOSTNAME_VERIFICATION)
    @WithDefault(ConfigurationClientConfig.GLOBAL_CONF_HOSTNAME_VERIFICATION_DEFAULT)
    boolean globalConfHostnameVerification();

    @WithName(ConfigurationClientConfig.GLOBAL_CONF_TLS_CERT_VERIFICATION)
    @WithDefault(ConfigurationClientConfig.GLOBAL_CONF_TLS_CERT_VERIFICATION_DEFAULT)
    boolean globalConfTlsCertVerification();

    @WithName(ConfigurationClientConfig.DOWNLOADER_CONNECT_TIMEOUT)
    @WithDefault(ConfigurationClientConfig.DOWNLOADER_CONNECT_TIMEOUT_DEFAULT)
    int downloaderConnectTimeout();

    @WithName(ConfigurationClientConfig.DOWNLOADER_READ_TIMEOUT)
    @WithDefault(ConfigurationClientConfig.DOWNLOADER_READ_TIMEOUT_DEFAULT)
    int downloaderReadTimeout();

    @WithName(ConfigurationClientConfig.GLOBAL_CONF_DIR)
    @WithDefault(ConfigurationClientConfig.GLOBAL_CONF_DIR_DEFAULT)
    String globalConfDir();

    @WithName("update-interval")
    @WithDefault("60")
    int updateInterval();

    @WithName("configuration-anchor-file")
    @WithDefault("/etc/xroad/configuration-anchor.xml")
    String configurationAnchorFile();

    @WithName("configuration-anchor-storage")
    @WithDefault("DB")
    ConfigurationAnchorStorage configurationAnchorStorage();

    enum ConfigurationAnchorStorage {
        FILE,
        DB
    }

}
