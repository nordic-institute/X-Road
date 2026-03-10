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
package org.niis.xroad.auxiliaryservice.application.healthcheck;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.auxiliaryservice.application.config.AuxiliaryServiceReadinessCheckProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KubernetesApiReadinessCheckTest {

    @Mock
    private AuxiliaryServiceReadinessCheckProperties healthCheckProperties;

    @Mock
    private AuxiliaryServiceReadinessCheckProperties.KubernetesApiProperties kubernetesProps;

    private KubernetesApiReadinessCheck check;

    @BeforeEach
    void setUp() throws GeneralSecurityException, IOException {
        when(healthCheckProperties.kubernetes()).thenReturn(kubernetesProps);
        when(kubernetesProps.caCertPath()).thenReturn("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt");
        check = new KubernetesApiReadinessCheck(healthCheckProperties);
    }

    @Test
    void notInKubernetesReturnsUpNotRequired() {
        when(kubernetesProps.serviceHost()).thenReturn(Optional.empty());

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("NOT_REQUIRED", response.getData().orElseThrow().get("status"));
    }

    @Test
    void emptyServiceHostReturnsUpNotRequired() {
        when(kubernetesProps.serviceHost()).thenReturn(Optional.of(""));

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("NOT_REQUIRED", response.getData().orElseThrow().get("status"));
    }

    @Test
    void kubernetesEnvWithMissingTokenFileReturnsDown() {
        when(kubernetesProps.serviceHost()).thenReturn(Optional.of("10.0.0.1"));
        when(kubernetesProps.servicePort()).thenReturn(Optional.of("443"));
        when(kubernetesProps.tokenPath()).thenReturn("/nonexistent/kubernetes/token");

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }
}
