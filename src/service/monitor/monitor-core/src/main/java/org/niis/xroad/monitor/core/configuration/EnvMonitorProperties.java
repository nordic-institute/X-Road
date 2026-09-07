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
package org.niis.xroad.monitor.core.configuration;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;

import static org.niis.xroad.common.properties.config.keys.MonitorConfigKeys.CERTIFICATE_INFO_SENSOR_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.MonitorConfigKeys.DISK_SPACE_SENSOR_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.MonitorConfigKeys.EXEC_LISTING_SENSOR_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.MonitorConfigKeys.LIMIT_REMOTE_DATA_SET;
import static org.niis.xroad.common.properties.config.keys.MonitorConfigKeys.SYSTEM_METRICS_SENSOR_INTERVAL;

/** Env-monitor configuration ({@code xroad.env-monitor.*}). */
@RequiredArgsConstructor
public class EnvMonitorProperties {

    private final XRoadConfig xRoadConfig;

    /** @return sensor interval for certificate info collection */
    public Duration certificateInfoSensorInterval() {
        return xRoadConfig.value(CERTIFICATE_INFO_SENSOR_INTERVAL);
    }

    /** @return sensor interval for disk space collection */
    public Duration diskSpaceSensorInterval() {
        return xRoadConfig.value(DISK_SPACE_SENSOR_INTERVAL);
    }

    /** @return sensor interval for executable listing */
    public Duration execListingSensorInterval() {
        return xRoadConfig.value(EXEC_LISTING_SENSOR_INTERVAL);
    }

    /** @return sensor interval for system metrics collection */
    public Duration systemMetricsSensorInterval() {
        return xRoadConfig.value(SYSTEM_METRICS_SENSOR_INTERVAL);
    }

    /** @return whether to limit the remote data set */
    public boolean limitRemoteDataSet() {
        return xRoadConfig.value(LIMIT_REMOTE_DATA_SET);
    }
}
