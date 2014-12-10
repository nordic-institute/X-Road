package ee.cyber.xroad.common.util;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

import com.sun.management.UnixOperatingSystemMXBean;

public class SystemMetrics {

    private static UnixOperatingSystemMXBean stats;

    private static Integer numConnections = new Integer(0);

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
    }

    public static UnixOperatingSystemMXBean getStats() {
        if (stats == null) {
            init();
        }

        return stats;
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
