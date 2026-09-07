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
package org.niis.xroad.common.properties.config.keys;

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.time.Duration;
import java.util.Set;

/**
 * DSL config keys for the {@code xroad.env-monitor} namespace.
 *
 * <p>Not part of {@code ConfigKeyProviders.allProviders()}: that list feeds the Quarkus defaults config
 * source, which would then publish env-monitor defaults into apps that do not map the prefix. The monitor
 * registers it directly; the Security Server admin-service lists it for the system-parameters catalogue.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public final class MonitorConfigKeys implements ConfigKeyProvider {

    private static final Prefix ENV_MONITOR = Prefix.of(Category.MONITOR, "xroad.env-monitor");
    private static final Prefix RPC = ENV_MONITOR.subPrefix("rpc");

    private static final MonitorConfigKeys INSTANCE = new MonitorConfigKeys();

    // --- xroad.env-monitor ------------------------------------------------------

    /** {@code xroad.env-monitor.certificate-info-sensor-interval} */
    public static final ConfigKey<Duration> CERTIFICATE_INFO_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("certificate-info-sensor-interval")
            .withDefaultValue(Duration.ofDays(1))
            .exposedInUi()
            .build();

    /** {@code xroad.env-monitor.disk-space-sensor-interval} */
    public static final ConfigKey<Duration> DISK_SPACE_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("disk-space-sensor-interval")
            .withDefaultValue(Duration.ofSeconds(60))
            .exposedInUi()
            .build();

    /** {@code xroad.env-monitor.exec-listing-sensor-interval} */
    public static final ConfigKey<Duration> EXEC_LISTING_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("exec-listing-sensor-interval")
            .withDefaultValue(Duration.ofSeconds(60))
            .exposedInUi()
            .build();

    /** {@code xroad.env-monitor.system-metrics-sensor-interval} */
    public static final ConfigKey<Duration> SYSTEM_METRICS_SENSOR_INTERVAL = ENV_MONITOR
            .keyDuration("system-metrics-sensor-interval")
            .withDefaultValue(Duration.ofSeconds(5))
            .exposedInUi()
            .build();

    /** {@code xroad.env-monitor.limit-remote-data-set} */
    public static final ConfigKey<Boolean> LIMIT_REMOTE_DATA_SET = ENV_MONITOR
            .bool("limit-remote-data-set")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    // --- xroad.env-monitor.rpc --------------------------------------------------

    /** {@code xroad.env-monitor.rpc.enabled} */
    public static final ConfigKey<Boolean> RPC_ENABLED = RPC
            .bool("enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.env-monitor.rpc.listen-address} */
    public static final ConfigKey<String> RPC_LISTEN_ADDRESS = RPC
            .string("listen-address")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("0.0.0.0")
            .exposedInUi()
            .build();

    /** {@code xroad.env-monitor.rpc.port} */
    public static final ConfigKey<Integer> RPC_PORT = RPC
            .integer("port")
            .withDefaultValue(2552)
            .exposedInUi()
            .build();

    private MonitorConfigKeys() {
    }

    /** @return the provider singleton. */
    public static MonitorConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return ENV_MONITOR.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return ENV_MONITOR.keys();
    }
}
