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
package org.niis.xroad.common.healthcheck;

import ee.ria.xroad.common.HeapMemoryStatus;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapMemoryLivenessCheckTest {

    private static final String CHECK_NAME = "HEAP_MEMORY_CHECK";

    @Mock
    private HeapMemoryStatusService heapMemoryStatusService;

    @Test
    void callReturnsUpWhenUsedBelowThreshold() {
        when(heapMemoryStatusService.getMemoryStatus())
                .thenReturn(new HeapMemoryStatus(200L, 100L, 1000L, 100L, 80, 50L));

        HealthCheckResponse response = new HeapMemoryLivenessCheck(heapMemoryStatusService).call();

        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        Map<String, Object> data = response.getData().orElseThrow();
        assertThat(data).containsEntry("threshold_percent", 80L);
        assertThat(data).doesNotContainKey("threshold_configured");
    }

    @Test
    void callReturnsDownWhenUsedAboveThreshold() {
        when(heapMemoryStatusService.getMemoryStatus())
                .thenReturn(new HeapMemoryStatus(900L, 50L, 1000L, 850L, 80, 95L));

        HealthCheckResponse response = new HeapMemoryLivenessCheck(heapMemoryStatusService).call();

        assertThat(response.getName()).isEqualTo(CHECK_NAME);
        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.DOWN);
        assertThat(response.getData().orElseThrow()).containsEntry("threshold_percent", 80L);
    }

    @Test
    void callReturnsUpWhenThresholdNotConfigured() {
        when(heapMemoryStatusService.getMemoryStatus())
                .thenReturn(new HeapMemoryStatus(990L, 10L, 1000L, 990L, null, 99L));

        HealthCheckResponse response = new HeapMemoryLivenessCheck(heapMemoryStatusService).call();

        assertThat(response.getStatus()).isEqualTo(HealthCheckResponse.Status.UP);
        Map<String, Object> data = response.getData().orElseThrow();
        assertThat(data).containsEntry("threshold_configured", false);
        assertThat(data).doesNotContainKey("threshold_percent");
    }

    @Test
    void callIncludesAllMemoryDataFields() {
        when(heapMemoryStatusService.getMemoryStatus())
                .thenReturn(new HeapMemoryStatus(500L, 200L, 1000L, 300L, 80, 30L));

        HealthCheckResponse response = new HeapMemoryLivenessCheck(heapMemoryStatusService).call();

        assertThat(response.getData().orElseThrow())
                .containsEntry("total_memory", 500L)
                .containsEntry("free_memory", 200L)
                .containsEntry("used_percent", 30L)
                .containsEntry("used_bytes", 300L)
                .containsEntry("max_bytes", 1000L);
    }
}
