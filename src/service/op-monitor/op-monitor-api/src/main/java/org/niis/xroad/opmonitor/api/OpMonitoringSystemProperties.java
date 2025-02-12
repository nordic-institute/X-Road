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
package org.niis.xroad.opmonitor.api;

import lombok.extern.slf4j.Slf4j;

import static ee.ria.xroad.common.SystemProperties.PREFIX;

/**
 * Contains constants for operational monitor system properties.
 */
@Slf4j
@Deprecated(forRemoval = true)
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

}
