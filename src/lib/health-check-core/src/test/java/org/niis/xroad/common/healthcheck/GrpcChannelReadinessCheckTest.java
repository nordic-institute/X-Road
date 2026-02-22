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

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrpcChannelReadinessCheckTest {

    private static final String CHECK_NAME = "TEST_GRPC_CHECK";
    private static final String TARGET_SERVICE = "test-service";

    @Mock
    private ManagedChannel channel;

    private GrpcChannelReadinessCheck createCheck(ManagedChannel ch) {
        return new GrpcChannelReadinessCheck() {
            @Override
            protected ManagedChannel getChannel() {
                return ch;
            }

            @Override
            protected String getCheckName() {
                return CHECK_NAME;
            }

            @Override
            protected String getTargetService() {
                return TARGET_SERVICE;
            }
        };
    }

    @Test
    void nullChannelReturnsDown() {
        var response = createCheck(null).call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals(CHECK_NAME, response.getName());
        assertEquals("Channel not initialized", response.getData().orElseThrow().get("error"));
    }

    @Test
    void readyStateReturnsUp() {
        when(channel.getState(eq(false))).thenReturn(ConnectivityState.READY);

        var response = createCheck(channel).call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals(CHECK_NAME, response.getName());
        assertEquals("READY", response.getData().orElseThrow().get("state"));
    }

    @Test
    void idleStateReturnsUp() {
        when(channel.getState(eq(false))).thenReturn(ConnectivityState.IDLE);

        var response = createCheck(channel).call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("IDLE", response.getData().orElseThrow().get("state"));
    }

    @Test
    void connectingStateReturnsUp() {
        when(channel.getState(eq(false))).thenReturn(ConnectivityState.CONNECTING);

        var response = createCheck(channel).call();

        assertEquals(HealthCheckResponse.Status.UP, response.getStatus());
        assertEquals("CONNECTING", response.getData().orElseThrow().get("state"));
    }

    @Test
    void transientFailureStateReturnsDown() {
        when(channel.getState(eq(false))).thenReturn(ConnectivityState.TRANSIENT_FAILURE);

        var response = createCheck(channel).call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("TRANSIENT_FAILURE", response.getData().orElseThrow().get("state"));
    }

    @Test
    void shutdownStateReturnsDown() {
        when(channel.getState(eq(false))).thenReturn(ConnectivityState.SHUTDOWN);

        var response = createCheck(channel).call();

        assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus());
        assertEquals("SHUTDOWN", response.getData().orElseThrow().get("state"));
    }
}
