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
package org.niis.xroad.signer.softtoken.sync;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.softtoken.config.KeySyncHealthCheckProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncLivenessCheckTest {

    @Mock
    private SyncHealthState syncHealthState;

    @Mock
    private KeySyncHealthCheckProperties healthCheckProperties;

    @InjectMocks
    private SyncLivenessCheck check;

    @Test
    void returnsUpWhenNoFailures() {
        when(syncHealthState.getConsecutiveFailures()).thenReturn(0);
        when(healthCheckProperties.maxConsecutiveFailures()).thenReturn(3);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("SOFTTOKEN_SYNC_LIVENESS", response.getName());
    }

    @Test
    void returnsUpWhenBelowThreshold() {
        when(syncHealthState.getConsecutiveFailures()).thenReturn(2);
        when(healthCheckProperties.maxConsecutiveFailures()).thenReturn(3);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void returnsDownWhenAtThreshold() {
        when(syncHealthState.getConsecutiveFailures()).thenReturn(3);
        when(healthCheckProperties.maxConsecutiveFailures()).thenReturn(3);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }

    @Test
    void returnsDownWhenAboveThreshold() {
        when(syncHealthState.getConsecutiveFailures()).thenReturn(5);
        when(healthCheckProperties.maxConsecutiveFailures()).thenReturn(3);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }

    @Test
    void respectsCustomThreshold() {
        when(syncHealthState.getConsecutiveFailures()).thenReturn(10);
        when(healthCheckProperties.maxConsecutiveFailures()).thenReturn(15);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void includesDataInResponse() {
        when(syncHealthState.getConsecutiveFailures()).thenReturn(2);
        when(healthCheckProperties.maxConsecutiveFailures()).thenReturn(3);

        var response = check.call();

        assertEquals(2L, response.getData().get().get("consecutive_failures"));
        assertEquals(3L, response.getData().get().get("threshold"));
    }
}
