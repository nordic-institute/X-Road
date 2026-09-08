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

package org.niis.xroad.opmonitor.core.config;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.CLEAN_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.HEALTH_STATISTICS_PERIOD_SECONDS;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.KEEP_RECORDS_FOR_DAYS;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.LISTEN_ADDRESS;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.MAX_RECORDS_IN_PAYLOAD;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.PORT;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.RECORDS_AVAILABLE_TIMESTAMP_OFFSET_SECONDS;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.SCHEME;
import static org.niis.xroad.common.properties.config.keys.OpMonitorConfigKeys.XROAD_TLS_CIPHERS;

@RequiredArgsConstructor
public class OpMonitorProperties {
    private static final String DEFAULT_MAX_RECORDS_IN_PAYLOAD = "10000";

    private final XRoadConfig xRoadConfig;

    public String listenAddress() {
        return xRoadConfig.value(LISTEN_ADDRESS);
    }

    public int port() {
        return xRoadConfig.value(PORT);
    }

    public String scheme() {
        return xRoadConfig.value(SCHEME);
    }

    public String[] xroadTlsCiphers() {
        return xRoadConfig.value(XROAD_TLS_CIPHERS);
    }

    /**
     * The period in days for keeping operational data records in the database.
     *
     * @return number of days
     */
    public int keepRecordsForDays() {
        return xRoadConfig.value(KEEP_RECORDS_FOR_DAYS);
    }

    /**
     * The time interval as a Cron expression for running the data cleanup operation.
     *
     * @return cron expression
     */
    public String cleanInterval() {
        return xRoadConfig.value(CLEAN_INTERVAL);
    }

    /**
     * The maximum records in the get operational data response payload.
     *
     * @return max records count
     */
    public int maxRecordsInPayload() {
        return xRoadConfig.value(MAX_RECORDS_IN_PAYLOAD);
    }

    public int getMaxRecordsInPayload() {
        int maxRecords = maxRecordsInPayload();
        if (maxRecords < 1) {
            return Integer.parseInt(DEFAULT_MAX_RECORDS_IN_PAYLOAD);
        }
        return maxRecords;
    }

    /**
     * The offset seconds used to calculate timestamp to which the operational data records are available.
     *
     * @return offset in seconds
     */
    public int recordsAvailableTimestampOffsetSeconds() {
        return xRoadConfig.value(RECORDS_AVAILABLE_TIMESTAMP_OFFSET_SECONDS);
    }

    /**
     * The period in seconds that is used for gathering health statistics about services.
     *
     * @return period in seconds
     */
    public int healthStatisticsPeriodSeconds() {
        return xRoadConfig.value(HEALTH_STATISTICS_PERIOD_SECONDS);
    }

}
