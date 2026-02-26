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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.model.GlobalConfInitState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalConfReadinessCheckTest {

    @Mock
    private GlobalConfSource globalConfSource;

    @InjectMocks
    private GlobalConfReadinessCheck check;

    @Test
    void initializedAndNotExpiredReturnsUp() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.INITIALIZED);
        when(globalConfSource.isExpired()).thenReturn(false);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("OK", response.getData().orElseThrow().get("status"));
    }

    @Test
    void initializedButExpiredReturnsDown() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.INITIALIZED);
        when(globalConfSource.isExpired()).thenReturn(true);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("EXPIRED", response.getData().orElseThrow().get("status"));
    }

    @Test
    void unknownStateReturnsUp() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.UNKNOWN);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("UNKNOWN", response.getData().orElseThrow().get("status"));
    }

    @Test
    void uninitializedStateReturnsUp() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.UNINITIALIZED);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("UNINITIALIZED", response.getData().orElseThrow().get("status"));
    }

    @Test
    void readyToInitStateReturnsUp() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.READY_TO_INIT);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void failureMissingAnchorStateReturnsUp() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.FAILURE_MISSING_ANCHOR);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void failureMissingInstanceIdentifierStateReturnsUp() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.FAILURE_MISSING_INSTANCE_IDENTIFIER);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
    }

    @Test
    void failureUnexpectedStateReturnsDown() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.FAILURE_UNEXPECTED);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("FAILURE_UNEXPECTED", response.getData().orElseThrow().get("status"));
    }

    @Test
    void failureMalformedStateReturnsDown() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.FAILURE_MALFORMED);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }

    @Test
    void failureConfigurationErrorStateReturnsDown() {
        when(globalConfSource.getReadinessState()).thenReturn(GlobalConfInitState.FAILURE_CONFIGURATION_ERROR);

        var response = check.call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
    }
}
