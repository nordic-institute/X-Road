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
package org.niis.xroad.proxy.core.healthcheck.readiness;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.proxy.core.configuration.ProxyHealthCheckProperties;
import org.niis.xroad.signer.client.SignerRpcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HsmOperationalReadinessCheckTest {

    private static final String CHECK_NAME = "PROXY_HSM_READINESS_CHECK";

    @Mock
    private SignerRpcClient signerRpcClient;

    private HsmOperationalReadinessCheck check;

    @BeforeEach
    void setUp() {
        ProxyHealthCheckProperties props = ConfigUtils.defaultConfiguration(ProxyHealthCheckProperties.class);
        this.check = new HsmOperationalReadinessCheck(signerRpcClient, props);
    }

    @Test
    void returnsUpWhenHsmOperational() {
        when(signerRpcClient.isHSMOperational()).thenReturn(true);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get()).containsEntry("status", "OK");
    }

    @Test
    void returnsDownWhenHsmNotOperational() {
        when(signerRpcClient.isHSMOperational()).thenReturn(false);

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getData().get()).containsEntry("status", "HSM_NON_OPERATIONAL");
    }

    @Test
    void returnsDownWhenHsmCallThrows() {
        when(signerRpcClient.isHSMOperational()).thenThrow(new RuntimeException("boom"));

        HealthCheckResponse response = check.call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData().get())
                .containsEntry("status", "HSM_CHECK_FAILED")
                .containsEntry("error", "RuntimeException");
    }
}
