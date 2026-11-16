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

package org.niis.xroad.common.vault.spring;

import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.vault.NoopVaultClient;
import org.niis.xroad.common.vault.ResilientVaultCall;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.vault.core.VaultKeyValueOperationsSupport;
import org.springframework.vault.core.VaultTemplate;

import java.time.Duration;

@Configuration
@EnableScheduling
public class SpringVaultClientConfig {

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
    SpringVaultClient springVaultClient(VaultTemplate vaultTemplate,
                                        @Value("${spring.cloud.vault.kv.backend}") String rootPath,
                                        @Value("${spring.cloud.vault.kv.backend-version}") String version,
                                        ObjectProvider<NodeIdentity> nodeIdentity,
                                        ObjectProvider<XRoadConfig> xRoadConfig) {
        var vaultKvClient = vaultTemplate.opsForKeyValue(rootPath, getKeyValueBackendVersion(version));
        return new SpringVaultClient(vaultKvClient, nodeIdentity.getIfAvailable(), retryBudget(xRoadConfig));
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "true")
    SpringVaultTokenKeepalive springVaultTokenKeepalive(VaultTemplate vaultTemplate,
                                                        ObjectProvider<XRoadConfig> xRoadConfig) {
        return new SpringVaultTokenKeepalive(vaultTemplate, retryBudget(xRoadConfig));
    }

    private static Duration retryBudget(ObjectProvider<XRoadConfig> xRoadConfig) {
        var config = xRoadConfig.getIfAvailable();
        return config == null ? ResilientVaultCall.DEFAULT_BUDGET : config.value(CommonConfigKeys.VAULT_RETRY_BUDGET);
    }

    private VaultKeyValueOperationsSupport.KeyValueBackend getKeyValueBackendVersion(String version) {
        return switch (version) {
            case "1" -> VaultKeyValueOperationsSupport.KeyValueBackend.KV_1;
            case "2" -> VaultKeyValueOperationsSupport.KeyValueBackend.KV_2;
            default -> throw new IllegalArgumentException("Unsupported secrets version: " + version);
        };
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cloud.vault.enabled", havingValue = "false", matchIfMissing = true)
    NoopVaultClient noopVaultClient() {
        return new NoopVaultClient();
    }

}
