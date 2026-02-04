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

@ConfigMapping(prefix = "xroad.configuration-client")
public interface ConfigurationClientProperties {

    @WithName("update-interval")
    @WithDefault("60")
    int updateInterval();

    @WithName("configuration-anchor-file")
    @WithDefault("/etc/xroad/configuration-anchor.xml")
    String configurationAnchorFile();

    @WithName("global-conf-dir")
    @WithDefault("/etc/xroad/globalconf")
    String globalConfDir();

    @WithName("global-conf-hostname-verification")
    @WithDefault("true")
    boolean globalConfHostnameVerification();

    @WithName("global-conf-tls-cert-verification")
    @WithDefault("true")
    boolean globalConfTlsCertVerification();

    @WithName("configuration-anchor-storage")
    @WithDefault("DB")
    ConfigurationAnchorStorage configurationAnchorStorage();

    @WithName("downloader-connect-timeout")
    @WithDefault("10000")
    int downloaderConnectTimeout();

    @WithName("downloader-read-timeout")
    @WithDefault("30000")
    int downloaderReadTimeout();

    @WithName("allowed-federations")
    @WithDefault("NONE")
    String allowedFederations();

    enum ConfigurationAnchorStorage {
        FILE,
        DB
    }

    /**
     * A constant to describe the X-Road instances this security server federates with.
     * {@link #CUSTOM} means a list of named, comma-separated X-Road instances to allow.
     * {@link #ALL} naturally means all and {@link #NONE} means federation is disabled.
     * The configurations for those instances won't be downloaded.
     */
    enum AllowedFederationMode { ALL, NONE, CUSTOM }
}
