/**
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
package ee.ria.xroad.common.opmonitoring;

import ee.ria.xroad.common.PortNumbers;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.SystemProperties.PREFIX;
import static ee.ria.xroad.common.SystemProperties.getConfPath;

/**
 * Contains constants for operational monitor system properties.
 */
@Slf4j
public final class OpMonitoringSystemProperties {

    private static final String DEFAULT_OP_MONITOR_MAX_RECORDS_IN_PAYLOAD = "10000";

    // Operational monitoring buffer --------------------------------------- //

    /**
     * Property name of the maximum size of operational monitoring buffer.
     */
    private static final String OP_MONITOR_BUFFER_SIZE =
            PREFIX + "op-monitor-buffer.size";

    /**
     * Property name of the maximum records in message sent by the operational
     * monitoring buffer to the operational monitoring daemon.
     */
    private static final String OP_MONITOR_BUFFER_MAX_RECORDS_IN_MESSAGE =
            PREFIX + "op-monitor-buffer.max-records-in-message";

    /**
     * Property name of the operational monitoring buffer sending interval seconds.
     */
    private static final String OP_MONITOR_BUFFER_SENDING_INTERVAL_SECONDS =
            PREFIX + "op-monitor-buffer.sending-interval-seconds";


    /**
     * Property name of the operational monitoring buffer HTTP client SO_TIMEOUT seconds.
     */
    private static final String OP_MONITOR_BUFFER_SOCKET_TIMEOUT_SECONDS =
            PREFIX + "op-monitor-buffer.socket-timeout-seconds";

    /**
     * Property name of the operational monitoring buffer HTTP client connection timeout seconds.
     */
    private static final String OP_MONITOR_BUFFER_CONNECTION_TIMEOUT_SECONDS =
            PREFIX + "op-monitor-buffer.connection-timeout-seconds";

    // Operational monitoring service ---------------------------------------//

    /**
     * Property name of the operational monitoring service HTTP client SO_TIMEOUT seconds.
     */
    private static final String OP_MONITOR_SERVICE_SOCKET_TIMEOUT_SECONDS =
            PREFIX + "op-monitor-service.socket-timeout-seconds";

    /**
     * Property name of the operational monitoring service HTTP client connection timeout seconds.
     */
    private static final String OP_MONITOR_SERVICE_CONNECTION_TIMEOUT_SECONDS =
            PREFIX + "op-monitor-service.connection-timeout-seconds";

    // Operational monitoring daemon --------------------------------------- //

    /**
     * Property name of the host address that the operational monitoring daemon listens on.
     */
    private static final String OP_MONITOR_HOST =
            PREFIX + "op-monitor.host";

    /**
     * Property name of the URI scheme name which the operational monitoring daemon uses.
     */
    private static final String OP_MONITOR_SCHEME =
            PREFIX + "op-monitor.scheme";

    /**
     * Property name of the port on which the operational monitoring daemon listens for JSON/SOAP requests.
     */
    private static final String OP_MONITOR_PORT =
            PREFIX + "op-monitor.port";

    /**
     * Property name of the path to the location of the operational monitoring daemon TLS certificate.
     */
    private static final String OP_MONITOR_TLS_CERTIFICATE =
            PREFIX + "op-monitor.tls-certificate";

    /**
     * Property name of the path to the location of the TLS certificate used by the HTTP client sending requests to the
     * operational data daemon. Validated by the daemon server and should be the security server internal certificate.
     */
    private static final String OP_MONITOR_CLIENT_TLS_CERTIFICATE =
            PREFIX + "op-monitor.client-tls-certificate";

    /**
     * Property name of the offset seconds used to calculate timestamp to which the operational data records are
     * available. Records with earlier timestamp (monitoringDataTs) than 'currentSeconds - offset' are available.
     */
    private static final String OP_MONITOR_RECORDS_AVAILABLE_TIMESTAMP_OFFSET_SECONDS =
            PREFIX + "op-monitor.records-available-timestamp-offset-seconds";

    /**
     * Property name of the period in seconds for gathering statistics about services.
     */
    private static final String OP_MONITOR_HEALTH_STATISTICS_PERIOD_SECONDS =
            PREFIX + "op-monitor.health-statistics-period-seconds";

    /**
     * Property name of the period in days for keeping operational data records in the database.
     */
    private static final String OP_MONITOR_KEEP_RECORDS_FOR_DAYS =
            PREFIX + "op-monitor.keep-records-for-days";

    /**
     * Property name of the interval for running the operational monitoring data cleanup operation represented as a
     * Cron expression.
     */
    private static final String OP_MONITOR_CLEAN_INTERVAL =
            PREFIX + "op-monitor.clean-interval";

    /**
     * Property name of the maximum records in the get operational data response payload.
     */
    private static final String OP_MONITOR_MAX_RECORDS_IN_PAYLOAD =
            PREFIX + "op-monitor.max-records-in-payload";

    private OpMonitoringSystemProperties() {
    }

    /**
     * @return the size of the operational monitoring buffer, '20000' by default. In case buffer size < 1, operational
     * monitoring data is not stored.
     */
    public static int getOpMonitorBufferSize() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_BUFFER_SIZE, "20000"));
    }

    /**
     * @return max records in message sent to the operational monitoring daemon, '100' by default.
     */
    public static int getOpMonitorBufferMaxRecordsInMessage() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_BUFFER_MAX_RECORDS_IN_MESSAGE, "100"));
    }

    /**
     * @return the interval in seconds at which operational monitoring buffer additionally tries to send records to the
     * operational monitoring daemon, '5' by default.
     */
    public static long getOpMonitorBufferSendingIntervalSeconds() {
        return Long.parseLong(System.getProperty(OP_MONITOR_BUFFER_SENDING_INTERVAL_SECONDS, "5"));
    }

    /**
     * @return the operational monitoring buffer HTTP client SO_TIMEOUT in seconds, '60' by default.
     */
    public static int getOpMonitorBufferSocketTimeoutSeconds() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_BUFFER_SOCKET_TIMEOUT_SECONDS, "60"));
    }

    /**
     * @return the operational monitoring buffer HTTP client connection timeout in seconds, '30' by default.
     */
    public static int getOpMonitorBufferConnectionTimeoutSeconds() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_BUFFER_CONNECTION_TIMEOUT_SECONDS, "30"));
    }

    /**
     * @return the operational monitoring service HTTP client SO_TIMEOUT in seconds, '60' by default.
     */
    public static int getOpMonitorServiceSocketTimeoutSeconds() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_SERVICE_SOCKET_TIMEOUT_SECONDS, "60"));
    }

    /**
     * @return the operational monitoring service HTTP client connection timeout in seconds, '30' by default.
     */
    public static int getOpMonitorServiceConnectionTimeoutSeconds() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_SERVICE_CONNECTION_TIMEOUT_SECONDS, "30"));
    }

    /**
     * @return the host address on which the operational monitoring daemon listens, 'localhost' by default.
     */
    public static String getOpMonitorHost() {
        return System.getProperty(OP_MONITOR_HOST, "localhost");
    }

    /**
     * @return the URI scheme name of the operational monitoring daemon, 'http' by default.
     */
    public static String getOpMonitorDaemonScheme() {
        return System.getProperty(OP_MONITOR_SCHEME, "http");
    }

     /**
     * @return the port number on which the operational monitoring daemon listens.
     */
    public static int getOpMonitorPort() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_PORT,
                Integer.toString(PortNumbers.OP_MONITOR_DAEMON_PORT)));
    }

    /**
     * @return the path to the location of the operational monitoring daemon TLS certificate,
     * '/etc/xroad/ssl/opmonitor.crt' by default.
     */
    public static String getOpMonitorCertificatePath() {
        return System.getProperty(OP_MONITOR_TLS_CERTIFICATE, getConfPath() + "ssl/opmonitor.crt");
    }

    /**
     * @return path to the TLS certificate used by the HTTP client making sending requests to the operational data
     * daemon. validated by the daemon server and should be the security server internal certificate.
     */
    public static String getOpMonitorClientCertificatePath() {
        return System.getProperty(OP_MONITOR_CLIENT_TLS_CERTIFICATE, getConfPath() + "ssl/internal.crt");
    }

    /**
     * @return the period in seconds that is used for gathering health statistics about services, 600 by default.
     */
    public static int getOpMonitorHealthStatisticsPeriodSeconds() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_HEALTH_STATISTICS_PERIOD_SECONDS, "600"));
    }

    /**
     * @return the period in days for keeping operational data records in the database, 7 days by default.
     */
    public static int getOpMonitorKeepRecordsForDays() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_KEEP_RECORDS_FOR_DAYS, "7"));
    }

    /**
     * @return the time interval as a Cron expression for running the operational monitoring data cleanup operation,
     * '0 0 0/12 1/1 * ? *' by default.
     */
    public static String getOpMonitorCleanInterval() {
        return System.getProperty(OP_MONITOR_CLEAN_INTERVAL, "0 0 0/12 1/1 * ? *");
    }

    /**
     * @return the maximum records in the get operational data response payload, 10000 by default.
     */
    public static int getOpMonitorMaxRecordsInPayload() {
        int payload = Integer.parseInt(System.getProperty(OP_MONITOR_MAX_RECORDS_IN_PAYLOAD,
                DEFAULT_OP_MONITOR_MAX_RECORDS_IN_PAYLOAD));

        if (payload < 1) {
            log.warn("Property {} has invalid value, using default '{}'", OP_MONITOR_MAX_RECORDS_IN_PAYLOAD,
                    DEFAULT_OP_MONITOR_MAX_RECORDS_IN_PAYLOAD);

            payload = Integer.parseInt(DEFAULT_OP_MONITOR_MAX_RECORDS_IN_PAYLOAD);
        }

        return payload;
    }

    /**
     * @return the offset seconds used to calculate timestamp to which the operational data records are available,
     * 60 by default.
     */
    public static int getOpMonitorRecordsAvailableTimestampOffsetSeconds() {
        return Integer.parseInt(System.getProperty(OP_MONITOR_RECORDS_AVAILABLE_TIMESTAMP_OFFSET_SECONDS, "60"));
    }
}
