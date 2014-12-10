package ee.cyber.sdsb.common.util;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;

import com.sun.management.UnixOperatingSystemMXBean;

import ee.cyber.sdsb.common.SystemProperties;

@Slf4j
public class SystemMetrics {
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

    public static UnixOperatingSystemMXBean getStats() {
        if (stats == null) {
            init();
        }

        return stats;
    }

    public static double getHeapUsage() {
        if (memoryStats == null) {
            init();
        }
        long max = memoryStats.getHeapMemoryUsage().getMax();
        long used = memoryStats.getHeapMemoryUsage().getUsed();
        return ((double) used) / max;
    }

    public static void connectionAccepted() {
        synchronized (numConnections) {
            numConnections++;
        }
    }

    public static void connectionClosed() {
        synchronized (numConnections) {
            numConnections--;
        }
    }

    public static int getNumConnections() {
        synchronized (numConnections) {
            return numConnections;
        }
    }

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
                    bytesReceived += Long.parseLong(parts[1]);
                    bytesTransmitted += Long.parseLong(parts[9]);
                }
            }

            return new NetStats(bytesReceived, bytesTransmitted);
        } catch (IOException e) {
            log.error("Did not manage to collect network statistics:", e);
            return null;
        }
    }

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
            return -1;
        }
    }
}
