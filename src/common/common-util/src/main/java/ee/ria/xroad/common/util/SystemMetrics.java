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
package ee.ria.xroad.common.util;

import ee.ria.xroad.common.SystemProperties;

import com.sun.management.UnixOperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contains methods for gathering and retrieving system metrics information.
 */
@Slf4j
public final class SystemMetrics {
    private static final int BYTES_RECEIVED_IDX = 1;
    private static final int BYTES_TRANSMITTED_IDX = 9;

    private static final int INDEX_PROC_USER = 1;
    private static final int INDEX_PROC_NICE = 2;
    private static final int INDEX_PROC_SYSTEM = 3;
    private static final int INDEX_PROC_IDLE = 4;
    private static final int INDEX_PROC_IOWAIT = 5;
    private static final int INDEX_PROC_IRQ = 6;
    private static final int INDEX_PROC_SOFTIRQ = 7;
    private static final int INDEX_PROC_STEAL = 8;

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
     * @return the current number of open connection.
     */
    public static int getNumConnections() {
        return NUM_CONNECTIONS.get();
    }

    /**
     * @return a snapshot of current network statistics
     */
    public static NetStats getNetStats() {
        long bytesReceived = 0;
        long bytesTransmitted = 0;

        try {
            List<String> lines = FileUtils.readLines(
                    new File(SystemProperties.getNetStatsFile()),
                    StandardCharsets.UTF_8);

            for (String eachLine : lines) {
                String trimmedLine = eachLine.trim();
                Pattern pattern = Pattern.compile("^eth[01]:[\\s\\d]*$");
                Matcher matcher = pattern.matcher(trimmedLine);

                if (matcher.find()) {
                    String[] parts = trimmedLine.split("\\s+");

                    // Indices according to format of /proc/net/dev
                    bytesReceived += Long.parseLong(parts[BYTES_RECEIVED_IDX]);
                    bytesTransmitted +=
                            Long.parseLong(parts[BYTES_TRANSMITTED_IDX]);
                }
            }

            return new NetStats(bytesReceived, bytesTransmitted);
        } catch (IOException e) {
            log.error("Did not manage to collect network statistics", e);
            return null;
        }
    }

    /**
     * @return a snapshot of current CPU statistics
     */
    public static CpuStats getCpuStats() {
        try {
            List<String> lines = FileUtils.readLines(
                    new File("/proc/stat"),
                    StandardCharsets.UTF_8);
            for (String each : lines) {
                if (each.startsWith("cpu ")) {
                    String[] rawStats = each.trim().split("\\s+");
                    return new CpuStats(
                            Double.parseDouble(rawStats[INDEX_PROC_USER]),
                            Double.parseDouble(rawStats[INDEX_PROC_NICE]),
                            Double.parseDouble(rawStats[INDEX_PROC_SYSTEM]),
                            Double.parseDouble(rawStats[INDEX_PROC_IDLE]),
                            Double.parseDouble(rawStats[INDEX_PROC_IOWAIT]),
                            Double.parseDouble(rawStats[INDEX_PROC_IRQ]),
                            Double.parseDouble(rawStats[INDEX_PROC_SOFTIRQ]),
                            Double.parseDouble(rawStats[INDEX_PROC_STEAL]));
                }
            }

            return null;
        } catch (IOException e) {
            log.error("Did not manage to collect CPU statistics", e);
            return null;
        }
    }

    /**
     * @return the number of free file handles, or -1 if
     *         the system operation failed (i.e. out of file handles)
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
