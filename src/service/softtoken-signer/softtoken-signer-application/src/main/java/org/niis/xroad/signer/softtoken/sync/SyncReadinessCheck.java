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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.signer.softtoken.config.KeySyncHealthCheckProperties;

import java.time.Duration;
import java.time.Instant;

/**
 * Readiness health check that reports DOWN when the last successful sync exceeds the configured staleness threshold.
 */
@Slf4j
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class SyncReadinessCheck implements HealthCheck {
    private static final String NAME = "SOFTTOKEN_SYNC_READINESS";

    private final SyncHealthState syncHealthState;
    private final KeySyncHealthCheckProperties healthCheckProperties;

    @Override
    public HealthCheckResponse call() {
        var lastSync = syncHealthState.getLastSuccessfulSync();

        if (lastSync.isEmpty()) {
            return HealthCheckResponse.named(NAME)
                    .up()
                    .withData("status", "AWAITING_FIRST_SYNC")
                    .build();
        }

        var elapsed = Duration.between(lastSync.get(), Instant.now());
        var threshold = healthCheckProperties.maxSyncAge();

        if (elapsed.compareTo(threshold) > 0) {
            log.warn("Sync readiness check DOWN: last success {}s ago (threshold: {}s)",
                    elapsed.toSeconds(), threshold.toSeconds());
            return HealthCheckResponse.named(NAME)
                    .down()
                    .withData("last_successful_sync", lastSync.get().toString())
                    .withData("elapsed_seconds", elapsed.toSeconds())
                    .withData("threshold_seconds", threshold.toSeconds())
                    .build();
        }

        return HealthCheckResponse.named(NAME)
                .up()
                .withData("last_successful_sync", lastSync.get().toString())
                .withData("elapsed_seconds", elapsed.toSeconds())
                .withData("threshold_seconds", threshold.toSeconds())
                .build();
    }
}
