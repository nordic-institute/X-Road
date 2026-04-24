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
package org.niis.xroad.proxy.core.healthcheck.liveness;

import ee.ria.xroad.common.ProxyMemory;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.proxy.core.admin.ProxyMemoryStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyHeapMemoryLivenessCheckTest {

    private static final String CHECK_NAME = "PROXY_HEAP_MEMORY_CHECK";

    @Mock
    private ProxyMemoryStatusService proxyMemoryStatusService;

    @Test
    void returnsUpWhenThresholdNotConfigured() {
        when(proxyMemoryStatusService.getMemoryStatus())
                .thenReturn(memory(null, 42L));
        var check = new ProxyHeapMemoryLivenessCheck(proxyMemoryStatusService);

        var response = check.call();

        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get()).containsEntry("threshold_configured", false);
    }

    @Test
    void returnsUpWhenUsedPercentAtOrBelowThreshold() {
        when(proxyMemoryStatusService.getMemoryStatus())
                .thenReturn(memory(80L, 80L));
        var check = new ProxyHeapMemoryLivenessCheck(proxyMemoryStatusService);

        var response = check.call();

        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get())
                .containsKeys("used_percent", "threshold_percent", "used_bytes", "max_bytes");
        assertThat(response.getData().get().get("threshold_percent")).isEqualTo(80L);
    }

    @Test
    void returnsDownWhenUsedPercentAboveThreshold() {
        when(proxyMemoryStatusService.getMemoryStatus())
                .thenReturn(memory(80L, 95L));
        var check = new ProxyHeapMemoryLivenessCheck(proxyMemoryStatusService);

        var response = check.call();

        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData()).isPresent();
        assertThat(response.getData().get())
                .containsKeys("used_percent", "threshold_percent", "used_bytes", "max_bytes");
        assertThat(response.getData().get().get("threshold_percent")).isEqualTo(80L);
        assertThat(response.getData().get().get("used_percent")).isEqualTo(95L);
    }

    private static ProxyMemory memory(Long threshold, long usedPercent) {
        long maxBytes = 1_000_000L;
        long usedBytes = maxBytes * usedPercent / 100;
        long freeBytes = maxBytes - usedBytes;
        return new ProxyMemory(maxBytes, freeBytes, maxBytes, usedBytes, threshold, usedPercent);
    }
}
