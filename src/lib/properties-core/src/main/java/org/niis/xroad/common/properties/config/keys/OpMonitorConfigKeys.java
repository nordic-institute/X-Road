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

import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING;
import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/** Op-monitor keys ({@code xroad.op-monitor.*}, incl. {@code .rpc} and {@code .tls} sub-trees). */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class OpMonitorConfigKeys implements ConfigKeyProvider {

    private static final Prefix OP_MONITOR = Prefix.of(Category.OP_MONITOR_DAEMON, "xroad.op-monitor");
    private static final Prefix RPC = OP_MONITOR.subPrefix("rpc");
    private static final Prefix TLS = OP_MONITOR.subPrefix("tls");
    private static final Prefix CERT_PROVISIONING = TLS.subPrefix("certificate-provisioning");

    private static final OpMonitorConfigKeys INSTANCE = new OpMonitorConfigKeys();

    /** {@code xroad.op-monitor.listen-address}. */
    public static final ConfigKey<String> LISTEN_ADDRESS = OP_MONITOR
            .string("listen-address")
            .withDefaultValue("localhost")
            .withContainerDefaultValue("0.0.0.0")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.port}. */
    public static final ConfigKey<Integer> PORT = OP_MONITOR
            .integer("port")
            .withDefaultValue(2080)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.scheme}. */
    public static final ConfigKey<String> SCHEME = OP_MONITOR
            .string("scheme")
            .withDefaultValue("http")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.xroad-tls-ciphers}. */
    public static final ConfigKey<String[]> XROAD_TLS_CIPHERS = OP_MONITOR
            .stringArray("xroad-tls-ciphers")
            .withDefaultValue(DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.keep-records-for-days}. */
    public static final ConfigKey<Integer> KEEP_RECORDS_FOR_DAYS = OP_MONITOR
            .integer("keep-records-for-days")
            .withDefaultValue(7)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.clean-interval}. */
    public static final ConfigKey<String> CLEAN_INTERVAL = OP_MONITOR
            .string("clean-interval")
            .withDefaultValue("0 0 0/12 1/1 * ? *")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.max-records-in-payload}. */
    public static final ConfigKey<Integer> MAX_RECORDS_IN_PAYLOAD = OP_MONITOR
            .integer("max-records-in-payload")
            .withDefaultValue(10000)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.records-available-timestamp-offset-seconds}. */
    public static final ConfigKey<Integer> RECORDS_AVAILABLE_TIMESTAMP_OFFSET_SECONDS = OP_MONITOR
            .integer("records-available-timestamp-offset-seconds")
            .withDefaultValue(60)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.health-statistics-period-seconds}. */
    public static final ConfigKey<Integer> HEALTH_STATISTICS_PERIOD_SECONDS = OP_MONITOR
            .integer("health-statistics-period-seconds")
            .withDefaultValue(600)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.rpc.enabled}. */
    public static final ConfigKey<Boolean> RPC_ENABLED = RPC
            .bool("enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.rpc.listen-address}. */
    public static final ConfigKey<String> RPC_LISTEN_ADDRESS = RPC
            .string("listen-address")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("0.0.0.0")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.rpc.port}. */
    public static final ConfigKey<Integer> RPC_PORT = RPC
            .integer("port")
            .withDefaultValue(2081)
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.client-certificate-refresh-interval}. */
    public static final ConfigKey<Duration> TLS_CLIENT_CERTIFICATE_REFRESH_INTERVAL = TLS
            .keyDuration("client-certificate-refresh-interval")
            .withDefaultValue(Duration.ofSeconds(0))
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> CERT_PROVISIONING_ISSUANCE_ROLE_NAME = CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> CERT_PROVISIONING_COMMON_NAME = CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> CERT_PROVISIONING_ALT_NAMES = CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("")
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> CERT_PROVISIONING_TTL = CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .exposedInUi()
            .build();

    /** {@code xroad.op-monitor.tls.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> CERT_PROVISIONING_SECRET_STORE_PKI_PATH = CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .exposedInUi()
            .build();

    private OpMonitorConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static OpMonitorConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return OP_MONITOR.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return OP_MONITOR.keys();
    }
}
