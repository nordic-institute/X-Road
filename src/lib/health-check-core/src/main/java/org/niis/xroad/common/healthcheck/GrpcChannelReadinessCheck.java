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
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

import java.util.EnumSet;
import java.util.Set;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.ERROR;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATE;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.TARGET;

/**
 * Abstract base class for gRPC channel connectivity readiness checks.
 * Checks the channel connectivity state to determine if the channel is ready to handle requests.
 *
 * <p>The following states are considered acceptable:
 * <ul>
 *   <li>READY - The channel is connected and ready</li>
 *   <li>IDLE - The channel is not actively connected but will connect on demand</li>
 *   <li>CONNECTING - The channel is currently establishing a connection</li>
 * </ul>
 *
 * <p>The following states indicate the channel is not ready:
 * <ul>
 *   <li>TRANSIENT_FAILURE - Connection failed and will retry</li>
 *   <li>SHUTDOWN - Channel has been shut down</li>
 * </ul>
 */
@Slf4j
public abstract class GrpcChannelReadinessCheck implements HealthCheck {


    /**
     * States that indicate the channel is ready or will become ready.
     * IDLE is acceptable because the channel will connect on demand.
     * CONNECTING is acceptable because connection is in progress.
     */
    private static final Set<ConnectivityState> ACCEPTABLE_STATES = EnumSet.of(
            ConnectivityState.READY,
            ConnectivityState.IDLE,
            ConnectivityState.CONNECTING
    );

    /**
     * Get the ManagedChannel to check.
     *
     * @return the ManagedChannel instance
     */
    protected abstract ManagedChannel getChannel();

    /**
     * Get the name for this health check (used in response).
     *
     * @return the health check name
     */
    protected abstract String getCheckName();

    /**
     * Get the target service name for logging and response data.
     *
     * @return the target service name (e.g., "signer", "configuration-client")
     */
    protected abstract String getTargetService();

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder()
                .name(getCheckName());

        ManagedChannel channel = getChannel();
        if (channel == null) {
            log.warn("gRPC channel to {} is not initialized", getTargetService());
            return builder.down()
                    .withData(ERROR, "Channel not initialized")
                    .withData(TARGET, getTargetService())
                    .build();
        }

        // Get current state without triggering connection attempt
        ConnectivityState state = channel.getState(false);

        if (ACCEPTABLE_STATES.contains(state)) {
            return builder.up()
                    .withData(STATE, state.name())
                    .withData(TARGET, getTargetService())
                    .build();
        }

        log.warn("gRPC channel to {} is in state: {}", getTargetService(), state);
        return builder.down()
                .withData(STATE, state.name())
                .withData(TARGET, getTargetService())
                .build();
    }
}
