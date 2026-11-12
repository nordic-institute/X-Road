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
package org.niis.xroad.cs.admin.core.service;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.NoopVaultKeyClient;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.common.vault.config.CertificateProvisioningProperties;
import org.niis.xroad.common.vault.spring.SpringVaultKeyClient;
import org.niis.xroad.cs.admin.core.config.AdminServiceTlsProperties;
import org.niis.xroad.cs.admin.core.config.BootstrapConfiguration;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.LazyInitializationBeanFactoryPostProcessor;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.vault.core.VaultTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementServiceVaultKeyClientWiringTest {

    private final CertificateProvisioningProperties adminProvisioning = mock(CertificateProvisioningProperties.class);
    private final CertificateProvisioningProperties managementProvisioning = mock(CertificateProvisioningProperties.class);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor()))
            .withUserConfiguration(BootstrapConfiguration.class, ManagementServiceTlsCertificateServiceImpl.class, AdminConsumer.class)
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class))
            .withBean(AdminServiceTlsProperties.class, this::tlsProperties)
            .withBean(VaultClient.class, () -> mock(VaultClient.class))
            .withBean(CertificateConverter.class, () -> mock(CertificateConverter.class))
            .withBean(AuditDataHelper.class, () -> mock(AuditDataHelper.class))
            .withPropertyValues("spring.profiles.active=nontest");

    @Test
    void shouldInjectTheManagementServiceKeyClientWhenOpenBaoIsEnabled() {
        contextRunner.withPropertyValues("spring.cloud.vault.enabled=true").run(context -> {
            var admin = context.getBean(BootstrapConfiguration.ADMIN_SERVICE_VAULT_KEY_CLIENT, VaultKeyClient.class);
            var management = context.getBean(BootstrapConfiguration.MANAGEMENT_SERVICE_VAULT_KEY_CLIENT, VaultKeyClient.class);
            assertThat(admin).isInstanceOf(SpringVaultKeyClient.class).isNotSameAs(management);
            assertThat(management).isInstanceOf(SpringVaultKeyClient.class);
            assertThat(managementInjected(context)).isSameAs(management);
            assertThat(context.getBean(AdminConsumer.class).vaultKeyClient).isSameAs(admin);
            assertThat(provisioningOf(managementInjected(context))).isSameAs(managementProvisioning);
            assertThat(provisioningOf(context.getBean(AdminConsumer.class).vaultKeyClient)).isSameAs(adminProvisioning);
        });
    }

    @Test
    void shouldInjectTheNamedNoopKeyClientsWhenOpenBaoIsDisabled() {
        contextRunner.withPropertyValues("spring.cloud.vault.enabled=false").run(context -> {
            var admin = context.getBean(BootstrapConfiguration.ADMIN_SERVICE_VAULT_KEY_CLIENT, VaultKeyClient.class);
            var management = context.getBean(BootstrapConfiguration.MANAGEMENT_SERVICE_VAULT_KEY_CLIENT, VaultKeyClient.class);
            assertThat(admin).isInstanceOf(NoopVaultKeyClient.class).isNotSameAs(management);
            assertThat(managementInjected(context)).isSameAs(management);
            assertThat(context.getBean(AdminConsumer.class).vaultKeyClient).isSameAs(admin);
        });
    }

    private AdminServiceTlsProperties tlsProperties() {
        var properties = mock(AdminServiceTlsProperties.class);
        when(properties.getCertificateProvisioning()).thenReturn(adminProvisioning);
        when(properties.getManagementServiceCertificateProvisioning()).thenReturn(managementProvisioning);
        return properties;
    }

    private static CertificateProvisioningProperties provisioningOf(VaultKeyClient client) {
        return (CertificateProvisioningProperties) ReflectionTestUtils.getField(client, "properties");
    }

    private static VaultKeyClient managementInjected(AssertableApplicationContext context) {
        return (VaultKeyClient) ReflectionTestUtils.getField(
                context.getBean(ManagementServiceTlsCertificateServiceImpl.class), "vaultKeyClient");
    }

    @Test
    void shouldNotResolveAnUnqualifiedKeyClientInjection() {
        contextRunner.withUserConfiguration(UnqualifiedConsumer.class)
                .withPropertyValues("spring.cloud.vault.enabled=true")
                .run(context -> assertThatThrownBy(() -> context.getBean(UnqualifiedConsumer.class))
                        .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class));
    }

    @Component
    static class AdminConsumer {
        private final VaultKeyClient vaultKeyClient;
        AdminConsumer(@Qualifier(BootstrapConfiguration.ADMIN_SERVICE_VAULT_KEY_CLIENT) VaultKeyClient vaultKeyClient) {
            this.vaultKeyClient = vaultKeyClient;
        }
    }

    @Component
    static class UnqualifiedConsumer {
        UnqualifiedConsumer(VaultKeyClient vaultKeyClient) {
        }
    }
}
