/*
 * The MIT License
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
package org.niis.xroad.proxy.core.util;

import com.sun.management.UnixOperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contains methods for gathering and retrieving system metrics information.
 */
@Slf4j
public final class SystemMetrics {

    private static final UnixOperatingSystemMXBean STATS;

    private static final MemoryMXBean MEMORY_STATS;

    private static final AtomicInteger NUM_CONNECTIONS = new AtomicInteger(0);

    static {
        OperatingSystemMXBean osStatsBean = ManagementFactory.getOperatingSystemMXBean();
        if (osStatsBean instanceof UnixOperatingSystemMXBean) {
            STATS = ((UnixOperatingSystemMXBean) osStatsBean);
        } else {
            log.warn("Unexpected OperatingSystemMXBean {}", osStatsBean.getName());
            STATS = null;
        }
        MEMORY_STATS = ManagementFactory.getMemoryMXBean();
    }

    private SystemMetrics() {
    }

    /**
     * @return UNIX operating system stats object
     */
    public static UnixOperatingSystemMXBean getStats() {
        if (STATS == null) {
            throw new IllegalStateException("Operating system statistics are not available.");
        }
        return STATS;
    }

    /**
     * @return the current ratio of heap usage on the current JVM.
     */
    public static double getHeapUsage() {
        try {
            long max = MEMORY_STATS.getHeapMemoryUsage().getMax();
            long used = MEMORY_STATS.getHeapMemoryUsage().getUsed();
            return ((double) used) / max;
        } catch (InternalError err) {
            log.error("Error getting heap usage", err);
            return -1;
        }
    }

    /**
     * Informs system metrics of a connection accepted event.
     */
    public static void connectionAccepted() {
        NUM_CONNECTIONS.getAndIncrement();
    }

    /**
     * Informs system metrics of a connection closed event.
     */
    public static void connectionClosed() {
        NUM_CONNECTIONS.getAndDecrement();
    }

    /**
     * @return the number of free file handles, or -1 if
     * the system operation failed (i.e. out of file handles)
     */
    public static long getFreeFileDescriptorCount() {
        try {
            long max = getStats().getMaxFileDescriptorCount();
            long open = getStats().getOpenFileDescriptorCount();
            return max - open;
        } catch (InternalError probablyOutOfFileHandles) {
            log.error("Error getting free file descriptor count",
                    probablyOutOfFileHandles);
            return -1;
        }
    }
}
