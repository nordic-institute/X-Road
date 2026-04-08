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
import org.eclipse.microprofile.health.Liveness;
import org.niis.xroad.signer.softtoken.config.KeySyncHealthCheckProperties;

/**
 * Liveness health check that reports DOWN after consecutive sync failures exceed the configured threshold.
 */
@Slf4j
@Liveness
@ApplicationScoped
@RequiredArgsConstructor
public class SyncLivenessCheck implements HealthCheck {
    private static final String NAME = "SOFTTOKEN_SYNC_LIVENESS";

    private final SyncHealthState syncHealthState;
    private final KeySyncHealthCheckProperties healthCheckProperties;

    @Override
    public HealthCheckResponse call() {
        var failures = syncHealthState.getConsecutiveFailures();
        var threshold = healthCheckProperties.maxConsecutiveFailures();

        if (failures >= threshold) {
            log.warn("Sync liveness check DOWN: {} consecutive failures (threshold: {})", failures, threshold);
            return HealthCheckResponse.named(NAME)
                    .down()
                    .withData("consecutive_failures", (long) failures)
                    .withData("threshold", (long) threshold)
                    .build();
        }

        return HealthCheckResponse.named(NAME)
                .up()
                .withData("consecutive_failures", (long) failures)
                .withData("threshold", (long) threshold)
                .build();
    }
}
