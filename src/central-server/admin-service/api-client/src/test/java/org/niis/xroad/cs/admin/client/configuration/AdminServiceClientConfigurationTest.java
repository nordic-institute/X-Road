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
package org.niis.xroad.cs.admin.client.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.spring.SpringVaultClient;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.core.VaultKeyValueOperationsSupport.KeyValueBackend;
import org.springframework.vault.core.VaultTemplate;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SystemStubsExtension.class)
class AdminServiceClientConfigurationTest {

    @SystemStub
    private final EnvironmentVariables variables = new EnvironmentVariables();

    private final VaultKeyValueOperations kvOperations = mock(VaultKeyValueOperations.class);
    private final VaultTemplate vaultTemplate = mock(VaultTemplate.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
            .withUserConfiguration(AdminServiceClientConfiguration.class)
            .withBean(VaultTemplate.class, () -> vaultTemplate)
            .withPropertyValues(
                    "spring.cloud.vault.enabled=true",
                    "spring.cloud.vault.kv.backend=xrd-secret",
                    "spring.cloud.vault.kv.backend-version=1");

    AdminServiceClientConfigurationTest() {
        when(vaultTemplate.opsForKeyValue("xrd-secret", KeyValueBackend.KV_1)).thenReturn(kvOperations);
    }

    @Test
    void shouldReadAdminAndManagementTlsFromTheNodePaths() {
        variables.set("XROAD_NODE_NAME", "node_a");
        contextRunner.run(context -> {
            var client = context.getBean(SpringVaultClient.class);

            assertThatThrownBy(client::getAdminServiceTlsCredentials).isInstanceOf(XrdRuntimeException.class);
            assertThatThrownBy(client::getManagementServicesTlsCredentials).isInstanceOf(XrdRuntimeException.class);

            verify(kvOperations).get("tls/admin-service/node_a");
            verify(kvOperations).get("tls/management-service/node_a");
        });
    }
}
