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

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;

import java.time.Duration;

/** DSL config keys for the {@code xroad.env-monitor} namespace. */
@SuppressWarnings("checkstyle:MagicNumber")
final class MonitorConfigKeys implements ConfigKeyProvider {

    private static final Scope ENV_MONITOR = Scope.of("xroad.env-monitor", "monitor");
    private static final Scope RPC = ENV_MONITOR.child("rpc");

    private static final MonitorConfigKeys INSTANCE = new MonitorConfigKeys();

    // --- xroad.env-monitor ------------------------------------------------------

    /** {@code xroad.env-monitor.certificate-info-sensor-interval} */
    static final ConfigKey<Duration> CERTIFICATE_INFO_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("certificate-info-sensor-interval")
            .withDefaultValue(Duration.ofDays(1))
            .build();

    /** {@code xroad.env-monitor.disk-space-sensor-interval} */
    static final ConfigKey<Duration> DISK_SPACE_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("disk-space-sensor-interval")
            .withDefaultValue(Duration.ofSeconds(60))
            .build();

    /** {@code xroad.env-monitor.exec-listing-sensor-interval} */
    static final ConfigKey<Duration> EXEC_LISTING_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("exec-listing-sensor-interval")
            .withDefaultValue(Duration.ofSeconds(60))
            .build();

    /** {@code xroad.env-monitor.system-metrics-sensor-interval} */
    static final ConfigKey<Duration> SYSTEM_METRICS_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("system-metrics-sensor-interval")
            .withDefaultValue(Duration.ofSeconds(5))
            .build();

    /** {@code xroad.env-monitor.limit-remote-data-set} */
    static final ConfigKey<Boolean> LIMIT_REMOTE_DATA_SET = ENV_MONITOR
            .bool("limit-remote-data-set")
            .withDefaultValue(false)
            .build();

    // --- xroad.env-monitor.rpc --------------------------------------------------

    /** {@code xroad.env-monitor.rpc.enabled} */
    static final ConfigKey<Boolean> RPC_ENABLED = RPC
            .bool("enabled")
            .withDefaultValue(true)
            .build();

    /** {@code xroad.env-monitor.rpc.listen-address} */
    static final ConfigKey<String> RPC_LISTEN_ADDRESS = RPC
            .string("listen-address")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("0.0.0.0")
            .build();

    /** {@code xroad.env-monitor.rpc.port} */
    static final ConfigKey<Integer> RPC_PORT = RPC
            .integer("port")
            .withDefaultValue(2552)
            .build();

    private MonitorConfigKeys() {
    }

    /** @return the provider singleton. */
    static MonitorConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return ENV_MONITOR;
    }
}
