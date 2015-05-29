package ee.ria.xroad.common.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.management.UnixOperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import ee.ria.xroad.common.SystemProperties;

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

    private static UnixOperatingSystemMXBean stats;

    private static MemoryMXBean memoryStats;

    private static Integer numConnections = 0;

    private SystemMetrics() {
    }

    /**
     * Initializes system metrics internals.
     */
    public static void init() {
        OperatingSystemMXBean osStatsBean =
                ManagementFactory.getOperatingSystemMXBean();
        if (osStatsBean instanceof UnixOperatingSystemMXBean) {
            stats = ((UnixOperatingSystemMXBean) osStatsBean);
        } else {
            throw new RuntimeException(
                    "Unexpected OperatingSystemMXBean " + osStatsBean);
        }
        memoryStats = ManagementFactory.getMemoryMXBean();
    }

    /**
     * @return UNIX operating system stats object
     */
    public static UnixOperatingSystemMXBean getStats() {
        if (stats == null) {
            init();
        }

        return stats;
    }

    /**
     * @return the current ratio of heap usage on the current JVM.
     */
    public static double getHeapUsage() {
        if (memoryStats == null) {
            init();
        }
        try {
            long max = memoryStats.getHeapMemoryUsage().getMax();
            long used = memoryStats.getHeapMemoryUsage().getUsed();
            return ((double) used) / max;
        } catch (InternalError err) {
            log.error("Error getting heap usage: {}", err.getMessage());
            return -1;
        }
    }

    /**
     * Informs system metrics of a connection accepted event.
     */
    public static void connectionAccepted() {
        synchronized (numConnections) {
            numConnections++;
        }
    }

    /**
     * Informs system metrics of a connection closed event.
     */
    public static void connectionClosed() {
        synchronized (numConnections) {
            numConnections--;
        }
    }

    /**
     * @return the current number of open connection.
     */
    public static int getNumConnections() {
        synchronized (numConnections) {
            return numConnections;
        }
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
            log.error("Did not manage to collect network statistics:", e);
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
            log.error("Did not manage to collect CPU statistics:", e);
            return null;
        }
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
            log.error("Error getting free file descriptor count: {}",
                    probablyOutOfFileHandles.getMessage());
            return -1;
        }
    }
}
