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
package org.niis.xroad.edc.extension.bridge;

import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.health.HealthCheckResult;
import org.eclipse.edc.spi.system.health.HealthCheckService;
import org.eclipse.edc.spi.system.health.HealthStatus;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EdcReadinessCheckTest {

    private static final String COMPONENT = "connector-tls";

    @Test
    void edcReadinessDownMapsToDownWithFailureMessages() {
        var check = new EdcReadinessCheck(runtimeReporting(unhealthy()));
        var response = check.call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getName()).isEqualTo(EdcReadinessCheck.NAME);
        assertThat(response.getData()).hasValueSatisfying(data -> assertThat(data).containsEntry(COMPONENT, "not served"));
    }
    @Test
    void edcReadinessUpMapsToUpCarryingNoData() {
        var response = new EdcReadinessCheck(runtimeReporting(healthy())).call();
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isEmpty();
    }
    @Test
    void runtimeThatHasNotBootedIsNotReady() {
        assertThat(new EdcRuntime(null, null).readiness().isHealthy()).isFalse();
    }

    private static EdcRuntime runtimeReporting(HealthStatus status) {
        var service = mock(HealthCheckService.class);
        when(service.isReady()).thenReturn(status);
        var edcContext = mock(ServiceExtensionContext.class);
        when(edcContext.hasService(HealthCheckService.class)).thenReturn(true);
        when(edcContext.getService(HealthCheckService.class)).thenReturn(service);
        return new EdcRuntime(null, null) {
            {
                this.context = edcContext;
            }
        };
    }

    private static HealthStatus healthy() {
        return new HealthStatus(HealthCheckResult.Builder.newInstance().component(COMPONENT).success().build());
    }

    private static HealthStatus unhealthy() {
        return new HealthStatus(HealthCheckResult.Builder.newInstance().component(COMPONENT).failure("not served").build());
    }
}
