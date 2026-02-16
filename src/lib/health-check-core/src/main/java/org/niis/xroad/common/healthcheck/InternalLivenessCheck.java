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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

/**
 * Liveness check that detects internal failures that a container restart would fix:
 * - Thread deadlocks
 * - Excessive memory pressure (heap usage above threshold)
 */
@Slf4j
@Liveness
@ApplicationScoped
public class InternalLivenessCheck implements HealthCheck {

    private static final String NAME = "internal-health";
    private static final double DEFAULT_HEAP_USAGE_THRESHOLD = 0.95;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.builder().name(NAME);

        // Check for deadlocked threads
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedThreads = threadBean.findDeadlockedThreads();
        if (deadlockedThreads != null && deadlockedThreads.length > 0) {
            log.error("Detected {} deadlocked threads, reporting liveness as DOWN", deadlockedThreads.length);
            return builder.down()
                    .withData("deadlockedThreads", deadlockedThreads.length)
                    .build();
        }

        // Check memory pressure
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double usageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        if (usageRatio > DEFAULT_HEAP_USAGE_THRESHOLD) {
            log.warn("Heap memory usage at {:.1f}%, above threshold of {:.1f}%, reporting liveness as DOWN",
                    usageRatio * 100, DEFAULT_HEAP_USAGE_THRESHOLD * 100);
            return builder.down()
                    .withData("heapUsage", String.format("%.1f%%", usageRatio * 100))
                    .withData("threshold", String.format("%.1f%%", DEFAULT_HEAP_USAGE_THRESHOLD * 100))
                    .build();
        }

        return builder.up()
                .withData("heapUsage", String.format("%.1f%%", usageRatio * 100))
                .build();
    }
}
