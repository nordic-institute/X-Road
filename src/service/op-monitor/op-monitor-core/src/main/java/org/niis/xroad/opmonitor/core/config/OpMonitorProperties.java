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

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "xroad.op-monitor")
public interface OpMonitorProperties {
    String DEFAULT_MAX_RECORDS_IN_PAYLOAD = "10000";

    @WithName("listen-address")
    @WithDefault("localhost")
    String listenAddress();

    /**
     * The period in days for keeping operational data records in the database.
     *
     * @return number of days
     */
    @WithName("keep-records-for-days")
    @WithDefault("7")
    int keepRecordsForDays();

    /**
     * The time interval as a Cron expression for running the data cleanup operation.
     *
     * @return cron expression
     */
    @WithName("clean-interval")
    @WithDefault("0 0 0/12 1/1 * ? *")
    String cleanInterval();

    /**
     * The maximum records in the get operational data response payload.
     *
     * @return max records count
     */
    @WithName("max-records-in-payload")
    @WithDefault(DEFAULT_MAX_RECORDS_IN_PAYLOAD)
    int maxRecordsInPayload();

    default int getMaxRecordsInPayload() {
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
    @WithName("records-available-timestamp-offset-seconds")
    @WithDefault("60")
    int recordsAvailableTimestampOffsetSeconds();

    /**
     * The period in seconds that is used for gathering health statistics about services.
     *
     * @return period in seconds
     */
    @WithName("health-statistics-period-seconds")
    @WithDefault("600")
    int healthStatisticsPeriodSeconds();

}
