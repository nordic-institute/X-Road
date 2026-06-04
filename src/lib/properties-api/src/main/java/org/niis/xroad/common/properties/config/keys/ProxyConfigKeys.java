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

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;


import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING;
import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING;
import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING;

/**
 * Keys for the {@code xroad.proxy} scope, mirroring the nested structure of the legacy
 * {@code ProxyProperties} {@code @ConfigMapping}. Sub-scopes (client-proxy, server,
 * ocsp-responder, addon, …) are created via {@link Scope#child(String)} so every key
 * still registers with the root {@code proxy} scope.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class ProxyConfigKeys implements ConfigKeyProvider {

    private static final Scope PROXY = Scope.of("xroad.proxy");
    private static final Scope CLIENT_PROXY = PROXY.child("client-proxy");
    private static final Scope SERVER = PROXY.child("server");
    private static final Scope OCSP_RESPONDER = PROXY.child("ocsp-responder");
    private static final Scope ADDON = PROXY.child("addon");
    private static final Scope ADDON_PROXY_MONITOR = ADDON.child("proxy-monitor");
    private static final Scope ADDON_META_SERVICES = ADDON.child("meta-services");
    private static final Scope ADDON_OP_MONITOR = ADDON.child("op-monitor");
    private static final Scope ADDON_OP_MONITOR_BUFFER = ADDON_OP_MONITOR.child("buffer");
    private static final Scope ADDON_OP_MONITOR_CONNECTION = ADDON_OP_MONITOR.child("connection");

    private static final ProxyConfigKeys INSTANCE = new ProxyConfigKeys();

    // --- xroad.proxy ---------------------------------------------------------

    /** {@code xroad.proxy.admin-port}. */
    public static final ConfigKey<Integer> ADMIN_PORT = PROXY
            .integer("admin-port").withDefaultValue(5566).build();

    /** {@code xroad.proxy.ssl-enabled}. */
    public static final ConfigKey<Boolean> SSL_ENABLED = PROXY
            .bool("ssl-enabled").withDefaultValue(true).build();

    /** {@code xroad.proxy.dsp-enabled}. */
    public static final ConfigKey<Boolean> DSP_ENABLED = PROXY
            .bool("dsp-enabled").withDefaultValue(true).build();

    /** {@code xroad.proxy.health-check-enabled}. */
    public static final ConfigKey<Boolean> HEALTH_CHECK_ENABLED = PROXY
            .bool("health-check-enabled").withDefaultValue(false).build();

    /** {@code xroad.proxy.health-check-port}. */
    public static final ConfigKey<Integer> HEALTH_CHECK_PORT = PROXY
            .integer("health-check-port").withDefaultValue(5588).build();

    /** {@code xroad.proxy.health-check-interface}. */
    public static final ConfigKey<String> HEALTH_CHECK_INTERFACE = PROXY
            .string("health-check-interface").withDefaultValue("0.0.0.0").build();

    /** {@code xroad.proxy.hsm-health-check-enabled}. */
    public static final ConfigKey<Boolean> HSM_HEALTH_CHECK_ENABLED = PROXY
            .bool("hsm-health-check-enabled").withDefaultValue(false).build();

    /** {@code xroad.proxy.memory-usage-threshold} — no default (optional). */
    public static final ConfigKey<Integer> MEMORY_USAGE_THRESHOLD = PROXY
            .integer("memory-usage-threshold").build();

    /** {@code xroad.proxy.message-sign-digest-name}. */
    public static final ConfigKey<String> MESSAGE_SIGN_DIGEST_NAME = PROXY
            .string("message-sign-digest-name").withDefaultValue("SHA-512").build();

    /** {@code xroad.proxy.verify-client-cert}. */
    public static final ConfigKey<Boolean> VERIFY_CLIENT_CERT = PROXY
            .bool("verify-client-cert").withDefaultValue(true).build();

    /** {@code xroad.proxy.log-client-cert}. */
    public static final ConfigKey<Boolean> LOG_CLIENT_CERT = PROXY
            .bool("log-client-cert").withDefaultValue(false).build();

    /** {@code xroad.proxy.enforce-client-is-cert-validity-period-check}. */
    public static final ConfigKey<Boolean> ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK = PROXY
            .bool("enforce-client-is-cert-validity-period-check").withDefaultValue(false).build();

    /** {@code xroad.proxy.server-port}. */
    public static final ConfigKey<Integer> SERVER_PORT = PROXY
            .integer("server-port").withDefaultValue(5500).build();

    /** {@code xroad.proxy.xroad-tls-ciphers}. */
    public static final ConfigKey<String[]> XROAD_TLS_CIPHERS = PROXY
            .stringArray("xroad-tls-ciphers").withDefaultValue(DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING).build();

    /** {@code xroad.proxy.batch-signing-enabled}. */
    public static final ConfigKey<Boolean> BATCH_SIGNING_ENABLED = PROXY
            .bool("batch-signing-enabled").withDefaultValue(false).build();

    /** {@code xroad.proxy.strict-identifier-checks}. */
    public static final ConfigKey<Boolean> STRICT_IDENTIFIER_CHECKS = PROXY
            .bool("strict-identifier-checks").withDefaultValue(true).build();

    // --- xroad.proxy.client-proxy --------------------------------------------

    /** {@code xroad.proxy.client-proxy.connector-host}. */
    public static final ConfigKey<String> CLIENT_PROXY_CONNECTOR_HOST = CLIENT_PROXY
            .string("connector-host").withDefaultValue("0.0.0.0").build();

    /** {@code xroad.proxy.client-proxy.client-http-port}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTP_PORT = CLIENT_PROXY
            .integer("client-http-port").withDefaultValue(8080).build();

    /** {@code xroad.proxy.client-proxy.client-https-port}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTPS_PORT = CLIENT_PROXY
            .integer("client-https-port").withDefaultValue(8443).build();

    /** {@code xroad.proxy.client-proxy.jetty-configuration-file}. */
    public static final ConfigKey<String> CLIENT_PROXY_JETTY_CONFIGURATION_FILE = CLIENT_PROXY
            .string("jetty-configuration-file").withDefaultValue("classpath:jetty/clientproxy.xml").build();

    /** {@code xroad.proxy.client-proxy.client-connector-initial-idle-time}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_CONNECTOR_INITIAL_IDLE_TIME = CLIENT_PROXY
            .integer("client-connector-initial-idle-time").withDefaultValue(30000).build();

    /** {@code xroad.proxy.client-proxy.client-timeout}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_TIMEOUT = CLIENT_PROXY
            .integer("client-timeout").withDefaultValue(30000).build();

    /** {@code xroad.proxy.client-proxy.client-httpclient-so-linger}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTPCLIENT_SO_LINGER = CLIENT_PROXY
            .integer("client-httpclient-so-linger").withDefaultValue(-1).build();

    /** {@code xroad.proxy.client-proxy.client-httpclient-timeout}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTPCLIENT_TIMEOUT = CLIENT_PROXY
            .integer("client-httpclient-timeout").withDefaultValue(0).build();

    /** {@code xroad.proxy.client-proxy.pool-total-max-connections}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_POOL_TOTAL_MAX_CONNECTIONS = CLIENT_PROXY
            .integer("pool-total-max-connections").withDefaultValue(10000).build();

    /** {@code xroad.proxy.client-proxy.pool-total-default-max-connections-per-route}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_POOL_TOTAL_DEFAULT_MAX_CONNECTIONS_PER_ROUTE = CLIENT_PROXY
            .integer("pool-total-default-max-connections-per-route").withDefaultValue(2500).build();

    /** {@code xroad.proxy.client-proxy.pool-validate-connections-after-inactivity-of-millis}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MILLIS = CLIENT_PROXY
            .integer("pool-validate-connections-after-inactivity-of-millis").withDefaultValue(2000).build();

    /** {@code xroad.proxy.client-proxy.client-idle-connection-monitor-interval}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_INTERVAL = CLIENT_PROXY
            .integer("client-idle-connection-monitor-interval").withDefaultValue(30000).build();

    /** {@code xroad.proxy.client-proxy.client-idle-connection-monitor-timeout}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_TIMEOUT = CLIENT_PROXY
            .integer("client-idle-connection-monitor-timeout").withDefaultValue(60000).build();

    /** {@code xroad.proxy.client-proxy.client-use-idle-connection-monitor}. */
    public static final ConfigKey<Boolean> CLIENT_PROXY_CLIENT_USE_IDLE_CONNECTION_MONITOR = CLIENT_PROXY
            .bool("client-use-idle-connection-monitor").withDefaultValue(true).build();

    /** {@code xroad.proxy.client-proxy.fastest-connecting-ssl-uri-cache-period}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD = CLIENT_PROXY
            .integer("fastest-connecting-ssl-uri-cache-period").withDefaultValue(3600).build();

    /** {@code xroad.proxy.client-proxy.use-fastest-connecting-ssl-socket-autoclose}. */
    public static final ConfigKey<Boolean> CLIENT_PROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE = CLIENT_PROXY
            .bool("use-fastest-connecting-ssl-socket-autoclose").withDefaultValue(true).build();

    /** {@code xroad.proxy.client-proxy.client-tls-protocols}. */
    public static final ConfigKey<String[]> CLIENT_PROXY_CLIENT_TLS_PROTOCOLS = CLIENT_PROXY
            .stringArray("client-tls-protocols").withDefaultValue(DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING).build();

    /** {@code xroad.proxy.client-proxy.client-tls-ciphers}. */
    public static final ConfigKey<String[]> CLIENT_PROXY_CLIENT_TLS_CIPHERS = CLIENT_PROXY
            .stringArray("client-tls-ciphers").withDefaultValue(DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING).build();

    /** {@code xroad.proxy.client-proxy.pool-enable-connection-reuse}. */
    public static final ConfigKey<Boolean> CLIENT_PROXY_POOL_ENABLE_CONNECTION_REUSE = CLIENT_PROXY
            .bool("pool-enable-connection-reuse").withDefaultValue(false).build();

    // --- xroad.proxy.server --------------------------------------------------

    /** {@code xroad.proxy.server.listen-address}. */
    public static final ConfigKey<String> SERVER_LISTEN_ADDRESS = SERVER
            .string("listen-address").withDefaultValue("0.0.0.0").build();

    /** {@code xroad.proxy.server.listen-port}. */
    public static final ConfigKey<Integer> SERVER_LISTEN_PORT = SERVER
            .integer("listen-port").withDefaultValue(5500).build();

    /** {@code xroad.proxy.server.connector-initial-idle-time}. */
    public static final ConfigKey<Integer> SERVER_CONNECTOR_INITIAL_IDLE_TIME = SERVER
            .integer("connector-initial-idle-time").withDefaultValue(30000).build();

    /** {@code xroad.proxy.server.jetty-configuration-file}. */
    public static final ConfigKey<String> SERVER_JETTY_CONFIGURATION_FILE = SERVER
            .string("jetty-configuration-file").withDefaultValue("classpath:jetty/serverproxy.xml").build();

    /** {@code xroad.proxy.server.support-clients-pooled-connections}. */
    public static final ConfigKey<Boolean> SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS = SERVER
            .bool("support-clients-pooled-connections").withDefaultValue(false).build();

    /** {@code xroad.proxy.server.min-supported-client-version} — no default (optional). */
    public static final ConfigKey<String> SERVER_MIN_SUPPORTED_CLIENT_VERSION = SERVER
            .string("min-supported-client-version").build();

    // --- xroad.proxy.ocsp-responder ------------------------------------------

    /** {@code xroad.proxy.ocsp-responder.listen-address}. */
    public static final ConfigKey<String> OCSP_RESPONDER_LISTEN_ADDRESS = OCSP_RESPONDER
            .string("listen-address").withDefaultValue("0.0.0.0").build();

    /** {@code xroad.proxy.ocsp-responder.port}. */
    public static final ConfigKey<Integer> OCSP_RESPONDER_PORT = OCSP_RESPONDER
            .integer("port").withDefaultValue(5577).build();

    /** {@code xroad.proxy.ocsp-responder.client-connect-timeout}. */
    public static final ConfigKey<Integer> OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT = OCSP_RESPONDER
            .integer("client-connect-timeout").withDefaultValue(20000).build();

    /** {@code xroad.proxy.ocsp-responder.client-read-timeout}. */
    public static final ConfigKey<Integer> OCSP_RESPONDER_CLIENT_READ_TIMEOUT = OCSP_RESPONDER
            .integer("client-read-timeout").withDefaultValue(30000).build();

    /** {@code xroad.proxy.ocsp-responder.jetty-configuration-file}. */
    public static final ConfigKey<String> OCSP_RESPONDER_JETTY_CONFIGURATION_FILE = OCSP_RESPONDER
            .string("jetty-configuration-file").withDefaultValue("classpath:jetty/ocsp-responder.xml").build();

    // --- xroad.proxy.addon ---------------------------------------------------

    /** {@code xroad.proxy.addon.proxy-monitor.enabled}. */
    public static final ConfigKey<Boolean> ADDON_PROXY_MONITOR_ENABLED = ADDON_PROXY_MONITOR
            .bool("enabled").withDefaultValue(true).build();

    /** {@code xroad.proxy.addon.meta-services.enabled}. */
    public static final ConfigKey<Boolean> ADDON_META_SERVICES_ENABLED = ADDON_META_SERVICES
            .bool("enabled").withDefaultValue(true).build();

    /** {@code xroad.proxy.addon.op-monitor.enabled}. */
    public static final ConfigKey<Boolean> ADDON_OP_MONITOR_ENABLED = ADDON_OP_MONITOR
            .bool("enabled").withDefaultValue(false).build();

    // --- xroad.proxy.addon.op-monitor.buffer ---------------------------------

    /** {@code xroad.proxy.addon.op-monitor.buffer.size}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_SIZE = ADDON_OP_MONITOR_BUFFER
            .integer("size").withDefaultValue(20000).build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.max-records-in-message}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_MAX_RECORDS_IN_MESSAGE = ADDON_OP_MONITOR_BUFFER
            .integer("max-records-in-message").withDefaultValue(100).build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.sending-interval-seconds}. */
    public static final ConfigKey<Long> ADDON_OP_MONITOR_BUFFER_SENDING_INTERVAL_SECONDS = ADDON_OP_MONITOR_BUFFER
            .longValue("sending-interval-seconds").withDefaultValue(5L).build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.socket-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_SOCKET_TIMEOUT_SECONDS = ADDON_OP_MONITOR_BUFFER
            .integer("socket-timeout-seconds").withDefaultValue(60).build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.connection-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_CONNECTION_TIMEOUT_SECONDS = ADDON_OP_MONITOR_BUFFER
            .integer("connection-timeout-seconds").withDefaultValue(50).build();

    // --- xroad.proxy.addon.op-monitor.connection -----------------------------

    /** {@code xroad.proxy.addon.op-monitor.connection.host}. */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_HOST = ADDON_OP_MONITOR_CONNECTION
            .string("host").withDefaultValue("localhost").build();

    /** {@code xroad.proxy.addon.op-monitor.connection.port}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_CONNECTION_PORT = ADDON_OP_MONITOR_CONNECTION
            .integer("port").withDefaultValue(2080).build();

    /** {@code xroad.proxy.addon.op-monitor.connection.scheme}. */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_SCHEME = ADDON_OP_MONITOR_CONNECTION
            .string("scheme").withDefaultValue("http").build();

    /** {@code xroad.proxy.addon.op-monitor.connection.client-tls-certificate} — no default (optional, deprecated). */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_CLIENT_TLS_CERTIFICATE = ADDON_OP_MONITOR_CONNECTION
            .string("client-tls-certificate").build();

    /** {@code xroad.proxy.addon.op-monitor.connection.tls-certificate} — no default (optional, deprecated). */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_TLS_CERTIFICATE = ADDON_OP_MONITOR_CONNECTION
            .string("tls-certificate").build();

    /** {@code xroad.proxy.addon.op-monitor.connection.socket-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_CONNECTION_SOCKET_TIMEOUT_SECONDS = ADDON_OP_MONITOR_CONNECTION
            .integer("socket-timeout-seconds").withDefaultValue(60).build();

    /** {@code xroad.proxy.addon.op-monitor.connection.connection-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_CONNECTION_CONNECTION_TIMEOUT_SECONDS = ADDON_OP_MONITOR_CONNECTION
            .integer("connection-timeout-seconds").withDefaultValue(30).build();

    /** {@code xroad.proxy.addon.op-monitor.connection.xroad-tls-ciphers}. */
    public static final ConfigKey<String[]> ADDON_OP_MONITOR_CONNECTION_XROAD_TLS_CIPHERS = ADDON_OP_MONITOR_CONNECTION
            .stringArray("xroad-tls-ciphers").withDefaultValue(DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING).build();

    private ProxyConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static ProxyConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return PROXY;
    }
}
