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

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

/**
 * Computes the current heap-memory snapshot, combining live JVM signals with the
 * shared {@code xroad.health-check.memory.threshold-percent} configuration.
 */
@ApplicationScoped
@RequiredArgsConstructor
public class HeapMemoryStatusService {

    private final HealthCheckProperties healthCheckProperties;

    public HeapMemoryStatus getMemoryStatus() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long maxMemory = heap.getMax();
        long totalMemory = heap.getCommitted();
        long usedMemory = heap.getUsed();
        long freeMemory = totalMemory - usedMemory;
        Integer threshold = healthCheckProperties.memory().thresholdPercent().orElse(null);
        long usedPercent = maxMemory > 0 ? (usedMemory * 100) / maxMemory : 0;
        return new HeapMemoryStatus(totalMemory, freeMemory, maxMemory, usedMemory, threshold, usedPercent);
    }

}
