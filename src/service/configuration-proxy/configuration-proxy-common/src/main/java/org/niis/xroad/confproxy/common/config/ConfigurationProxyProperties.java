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

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.ConfProxyInstanceConfig;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.ADDRESS;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.AUTO_INIT_SOFT_TOKEN;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.CONFIGURATION_PATH;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.GENERATED_CONF_PATH;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.GLOBAL_CONF_DOWNLOAD_PATH;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.HASH_ALGORITHM_URI;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.INSTANCES;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.MINIMUM_GLOBAL_CONFIGURATION_VERSION;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.SIGNATURE_DIGEST_ALGORITHM_ID;
import static org.niis.xroad.common.properties.config.keys.ConfProxyConfigKeys.UPDATE_INTERVAL;

/**
 * {@link XRoadConfig}-backed configuration properties for the configuration proxy service.
 */
@RequiredArgsConstructor
public class ConfigurationProxyProperties {

    public static final String DEFAULT_CONNECTOR_HOST = "0.0.0.0";

    private final XRoadConfig config;

    public int minimumGlobalConfigurationVersion() {
        return config.value(MINIMUM_GLOBAL_CONFIGURATION_VERSION);
    }

    public String address() {
        return config.value(ADDRESS);
    }

    public Optional<String> hashAlgorithmUri() {
        var raw = config.value(HASH_ALGORITHM_URI);
        return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(raw);
    }

    public Optional<String> signatureDigestAlgorithmId() {
        var raw = config.value(SIGNATURE_DIGEST_ALGORITHM_ID);
        return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(raw);
    }

    public String generatedConfPath() {
        return config.value(GENERATED_CONF_PATH);
    }

    public String configurationPath() {
        return config.value(CONFIGURATION_PATH);
    }

    public String updateInterval() {
        return config.value(UPDATE_INTERVAL);
    }

    public Map<String, Instance> instances() {
        return config.value(INSTANCES).entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> new Instance(e.getValue())));
    }

    public String globalConfDownloadPath() {
        return config.value(GLOBAL_CONF_DOWNLOAD_PATH);
    }

    public Optional<String> autoInitSoftToken() {
        var raw = config.value(AUTO_INIT_SOFT_TOKEN);
        return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(raw);
    }

    public DigestAlgorithm getSignatureDigestAlgorithmId() {
        return signatureDigestAlgorithmId()
                .filter(StringUtils::isNotEmpty)
                .map(DigestAlgorithm::ofUri)
                .orElse(DigestAlgorithm.SHA512);
    }

    public DigestAlgorithm getHashAlgorithmUri() {
        return hashAlgorithmUri()
                .filter(StringUtils::isNotEmpty)
                .map(DigestAlgorithm::ofUri)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * View of a single configuration-proxy instance entry.
     */
    @RequiredArgsConstructor
    public static class Instance {

        private final ConfProxyInstanceConfig delegate;

        public Optional<String> tokenId() {
            return delegate.tokenId();
        }

        public Optional<String> signingKeyId() {
            return delegate.signingKeyId();
        }

        public KeyAlgorithm keyAlgorithm() {
            return KeyAlgorithm.valueOf(delegate.keyAlgorithm());
        }

        public String sourceAnchorFileUri() {
            return delegate.sourceAnchorFileUri();
        }

        public int validityInterval() {
            return delegate.validityInterval();
        }
    }
}
