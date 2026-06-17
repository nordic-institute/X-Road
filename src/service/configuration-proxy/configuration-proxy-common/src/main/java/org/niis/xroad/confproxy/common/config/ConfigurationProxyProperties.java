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

package org.niis.xroad.confproxy.common.config;

import ee.ria.xroad.common.DefaultFilepaths;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "xroad.configuration-proxy")
public interface ConfigurationProxyProperties {
    String DEFAULT_CONNECTOR_HOST = "0.0.0.0";
    String DEFAULT_MINIMUM_GLOBAL_CONFIGURATION_VERSION = "2";
    String DEFAULT_CONFIGURATION_PATH = "/etc/xroad/confproxy";
    String DEFAULT_GLOBAL_CONF_DOWNLOAD_PATH = "/etc/xroad/globalconf";

    @WithName("minimum-global-configuration-version")
    @WithDefault(DEFAULT_MINIMUM_GLOBAL_CONFIGURATION_VERSION)
    int minimumGlobalConfigurationVersion();

    @WithName("address")
    @WithDefault(DEFAULT_CONNECTOR_HOST)
    String address();

    @WithName("hash-algorithm-uri")
    Optional<String> hashAlgorithmUri();

    @WithName("signature-digest-algorithm-id")
    Optional<String> signatureDigestAlgorithmId();

    @WithName("generated-conf-path")
    @WithDefault(DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH)
    String generatedConfPath();

    @WithName("configuration-path")
    @WithDefault(DEFAULT_CONFIGURATION_PATH)
    String configurationPath();

    @WithName("update-interval")
    @WithDefault("60s")
    String updateInterval();

    @WithName("instances")
    Map<String, Instance> instances();

    @WithName("global-conf-download-path")
    @WithDefault(DEFAULT_GLOBAL_CONF_DOWNLOAD_PATH)
    String globalConfDownloadPath();

    @WithName("auto-init-soft-token")
    Optional<String> autoInitSoftToken();

    default DigestAlgorithm getSignatureDigestAlgorithmId() {
        return signatureDigestAlgorithmId()
                .filter(StringUtils::isNotEmpty)
                .map(DigestAlgorithm::ofUri)
                .orElse(DigestAlgorithm.SHA512);
    }

    default DigestAlgorithm getHashAlgorithmUri() {
        return hashAlgorithmUri()
                .filter(StringUtils::isNotEmpty)
                .map(DigestAlgorithm::ofUri)
                .orElse(DigestAlgorithm.SHA512);
    }

    interface Instance {
        @WithName("token-id")
        Optional<String> tokenId();

        @WithName("signing-key-id")
        Optional<String> signingKeyId();

        @WithName("key-algorithm")
        @WithDefault("RSA")
        KeyAlgorithm keyAlgorithm();

        @WithName("source-anchor-file-uri")
        String sourceAnchorFileUri();

        @WithName("validity-interval")
        @WithDefault("600")
        int validityInterval();
    }
}
