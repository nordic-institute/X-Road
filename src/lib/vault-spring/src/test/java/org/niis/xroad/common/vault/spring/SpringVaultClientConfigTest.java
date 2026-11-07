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

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.properties.dbsource.InvalidNodeNameException;
import org.niis.xroad.common.properties.dbsource.NodeName;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringVaultClientConfigTest {

    private final VaultKeyValueOperations kvOperations = mock(VaultKeyValueOperations.class);
    private final VaultTemplate vaultTemplate = mock(VaultTemplate.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(VaultTemplate.class, () -> vaultTemplate)
            .withPropertyValues(
                    "spring.cloud.vault.enabled=true",
                    "spring.cloud.vault.kv.backend=xrd-secret",
                    "spring.cloud.vault.kv.backend-version=1");

    SpringVaultClientConfigTest() {
        when(vaultTemplate.opsForKeyValue("xrd-secret", KeyValueBackend.KV_1)).thenReturn(kvOperations);
    }

    @Test
    void shouldReadAdminTlsFromThePlainPathWhenNoNodeIdentityIsProvided() {
        contextRunner.withUserConfiguration(SpringVaultClientConfig.class).run(context -> {
            assertThat(context).doesNotHaveBean(NodeIdentity.class);

            var client = context.getBean(SpringVaultClient.class);
            assertThatThrownBy(client::getAdminServiceTlsCredentials)
                    .isInstanceOf(XrdRuntimeException.class);

            verify(kvOperations).get("tls/admin-service");
        });
    }

    @Test
    void shouldTakeTheNodeNameFromTheEnvironmentVariable() {
        contextRunner.withUserConfiguration(NodeIdentityConfig.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("XROAD_ADMIN_SERVICE_HA_NODE_NAME", "node_env"))))
                .run(context -> assertThat(context.getBean(NodeIdentity.class))
                        .isEqualTo(new NodeIdentity("node_env", true)));
    }

    @Test
    void shouldRejectAnInvalidNodeName() {
        contextRunner.withUserConfiguration(NodeIdentityConfig.class)
                .withPropertyValues("xroad.admin-service.ha-node-name=true")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining(XrdRuntimeException.class.getName())
                        .rootCause()
                        .isInstanceOf(InvalidNodeNameException.class)
                        .hasMessageContaining(NodeName.PROPERTY_KEY));
    }
}
