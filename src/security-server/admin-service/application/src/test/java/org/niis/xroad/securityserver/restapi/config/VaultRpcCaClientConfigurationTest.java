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
package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.RpcProperties.RpcCertificateProvisioningProperties;
import org.niis.xroad.common.vault.VaultCaClient;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VaultRpcCaClientConfigurationTest {

    private static final String RPC_PKI_MOUNT = "rpc-pki";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(VaultRpcCaClientConfiguration.class)
            .withBean(VaultTemplate.class, () -> mock(VaultTemplate.class))
            .withBean(RpcProperties.class, VaultRpcCaClientConfigurationTest::rpcProperties);

    @Test
    void shouldReadTheCaFromTheMountOfTheRpcCertificateProvisioningWhenTheSecretStoreIsEnabled() {
        contextRunner.withPropertyValues("spring.cloud.vault.enabled=true").run(this::assertCaIsReadFromTheRpcMount);
    }

    @Test
    void shouldReadTheCaFromTheMountOfTheRpcCertificateProvisioningWhenTheSecretStoreSettingIsMissing() {
        contextRunner.run(this::assertCaIsReadFromTheRpcMount);
    }

    @Test
    void shouldReadNoCaWhenTheSecretStoreIsDisabled() {
        contextRunner.withPropertyValues("spring.cloud.vault.enabled=false").run(context -> {
            assertThat(context.getBean(VaultCaClient.class).readCaCertificate()).isEmpty();

            verify(context.getBean(VaultTemplate.class), never()).read(anyString());
        });
    }

    private void assertCaIsReadFromTheRpcMount(AssertableApplicationContext context) throws Exception {
        var vaultTemplate = context.getBean(VaultTemplate.class);
        var ca = CertificateTestUtils.getMockTopCaCertificate();
        when(vaultTemplate.read(RPC_PKI_MOUNT + "/cert/ca")).thenReturn(responseWithCertificate(toPem(ca)));

        assertThat(context.getBean(VaultCaClient.class).readCaCertificate()).contains(ca);

        verify(vaultTemplate).read(RPC_PKI_MOUNT + "/cert/ca");
    }

    private static VaultResponse responseWithCertificate(String pem) {
        var response = new VaultResponse();
        response.setData(Map.of("certificate", pem));
        return response;
    }

    private static String toPem(X509Certificate certificate) throws Exception {
        var out = new ByteArrayOutputStream();
        CryptoUtils.writeCertificatePem(certificate.getEncoded(), out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static RpcProperties rpcProperties() {
        var provisioning = mock(RpcCertificateProvisioningProperties.class);
        when(provisioning.secretStorePkiPath()).thenReturn(RPC_PKI_MOUNT);
        var properties = mock(RpcProperties.class);
        when(properties.certificateProvisioning()).thenReturn(provisioning);
        return properties;
    }
}
