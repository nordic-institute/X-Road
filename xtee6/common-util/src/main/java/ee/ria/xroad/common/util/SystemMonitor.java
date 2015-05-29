package ee.ria.xroad.common.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.sun.management.UnixOperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.SystemProperties;

/**
 * A simple system monitor that logs system metrics to a file
 * with a certain interval.
 */
@Slf4j
public final class SystemMonitor implements StartStop {

    private static final Logger LOG =
            LoggerFactory.getLogger(SystemMonitor.class);

    private static final String LOG_FILE = "system-monitor.log";
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS");
    private static final String ROW_FORMAT = "%-12s | ";

    private static final int QUERY_INTERVAL = 5000;

    private final MonitorThread monitorThread = new MonitorThread();

    @Override
    public void start() throws Exception {
        monitorThread.init();

        monitorThread.isRunning = true;
        monitorThread.start();
    }

    @Override
    public void stop() throws Exception {
        monitorThread.isRunning = false;
    }

    @Override
    public void join() throws InterruptedException {
        monitorThread.join();
    }

    private abstract class LogColumn {
        String name;
        LogColumn(String name) {
            this.name = name;
        }
        abstract String getValue();
    }

    private class MonitorThread extends Thread {

        private static final double HUNDRED = 100.0;

        private UnixOperatingSystemMXBean osStats;
        private Writer logFile;
        private List<LogColumn> columns = new ArrayList<>();
        boolean isRunning;

        @Override
        public void run() {
            try {
                while (isRunning) {
                    queryInformation();
                    try {
                        sleep(QUERY_INTERVAL);
                    } catch (InterruptedException ex) {
                        //ignore
                        log.warn("System monitor thread was interrupted");
                    }
                }
            } finally {
                IOUtils.closeQuietly(logFile);
            }
        }

        public void init() throws Exception {
            osStats = SystemMetrics.getStats();

            createColumns();

            logFile = new FileWriter(getLogDirPath() + LOG_FILE);
            logSystemStats();
            logHeaders();
        }

        private void logSystemStats() throws Exception {
            writeToLog("System stats:");

            writeToLog("\tMax open files: "
                    + osStats.getMaxFileDescriptorCount());
            writeToLog("\tProcessors: " + osStats.getAvailableProcessors());
            writeToLog("\tTotal memory (physical): "
                    + osStats.getTotalPhysicalMemorySize());

            writeToLog("");
        }

        private void createColumns() {
            columns.add(new LogColumn("Time") {
                @Override
                String getValue() {
                    return DATE_FORMAT.format(new Date());
                }
            });

            columns.add(new LogColumn("CPU load %") {
                @Override
                String getValue() {
                    double load = osStats.getProcessCpuLoad();
                    // percentage might be easier to grasp
                    return String.format("%.2f", load * HUNDRED);
                }
            });

            columns.add(new LogColumn("Open files") {
                @Override
                String getValue() {
                    return Long.toString(osStats.getOpenFileDescriptorCount());
                }
            });

            columns.add(new LogColumn("Free memory") {
                @Override
                String getValue() {
                    return Long.toString(osStats.getFreePhysicalMemorySize());
                }
            });

            columns.add(new LogColumn("Threads") {
                @Override
                String getValue() {
                    return Integer.toString(Thread.activeCount());
                }
            });

            columns.add(new LogColumn("Connections") {
                @Override
                String getValue() {
                    return Integer.toString(SystemMetrics.getNumConnections());
                }
            });

            // Add other metrics here...
        }

        private void logHeaders() throws IOException {
            StringBuilder header = new StringBuilder(" ");
            for (LogColumn col : columns) {
                header.append(String.format(ROW_FORMAT, col.name));
            }
            writeToLog(header.toString());
            writeToLog(StringUtils.repeat('-', header.length()));
        }

        private void queryInformation() {
            try {
                StringBuilder line = new StringBuilder(" ");
                for (LogColumn col : columns) {
                    line.append(String.format(ROW_FORMAT, col.getValue()));
                }
                writeToLog(line.toString());
            } catch (Exception e) {
                LOG.error("Error writing to log", e);
            }
        }

        private void writeToLog(String line) throws IOException {
            logFile.write(line);
            logFile.write(System.lineSeparator());
            logFile.flush();
        }
    }

    private static String getLogDirPath() {
        return SystemProperties.getLogPath() + File.separator;
    }
}
