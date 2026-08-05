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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING;
import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING;
import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING;
import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/**
 * Keys for the {@code xroad.proxy} scope, mirroring the nested structure of the legacy
 * {@code ProxyProperties} {@code @ConfigMapping}. Sub-scopes (client-proxy, server,
 * ocsp-responder, addon, …) are created via {@link Prefix#subPrefix(String)} so every key
 * still registers with the root {@code proxy} prefix.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class ProxyConfigKeys implements ConfigKeyProvider {

    private static final Prefix PROXY = Prefix.of(Category.PROXY, "xroad.proxy");
    private static final Prefix CLIENT_PROXY = PROXY.subPrefix("client-proxy");
    private static final Prefix SERVER = PROXY.subPrefix("server");
    private static final Prefix OCSP_RESPONDER = PROXY.subPrefix("ocsp-responder");
    private static final Prefix ADDON = PROXY.subPrefix("addon");
    private static final Prefix ADDON_PROXY_MONITOR = ADDON.subPrefix("proxy-monitor");
    private static final Prefix ADDON_META_SERVICES = ADDON.subPrefix("meta-services");
    private static final Prefix ADDON_OP_MONITOR = ADDON.subPrefix("op-monitor");
    private static final Prefix ADDON_OP_MONITOR_BUFFER = ADDON_OP_MONITOR.subPrefix("buffer");
    private static final Prefix ADDON_OP_MONITOR_CONNECTION = ADDON_OP_MONITOR.subPrefix("connection");
    private static final Prefix TLS = PROXY.subPrefix("tls");
    private static final Prefix TLS_CERT_PROVISIONING = TLS.subPrefix("certificate-provisioning");
    private static final Prefix ANTI_DOS = Prefix.of(Category.PROXY, "xroad.anti-dos");
    private static final Prefix HEALTH_CHECK = PROXY.subPrefix("health-check");
    private static final Prefix HEALTH_CHECK_AUTH_KEY = HEALTH_CHECK.subPrefix("auth-key");
    private static final Prefix HEALTH_CHECK_HSM = HEALTH_CHECK.subPrefix("hsm");
    private static final Prefix MESSAGE_LOG = PROXY.subPrefix("message-log");
    private static final Prefix MESSAGE_LOG_TIMESTAMPER = MESSAGE_LOG.subPrefix("timestamper");
    private static final Prefix RPC = PROXY.subPrefix("rpc");
    private static final Prefix DSP = PROXY.subPrefix("dsp");
    private static final Prefix DSP_CACHE = DSP.subPrefix("cache");

    private static final String ENABLED = "enabled";
    private static final String LISTEN_ADDRESS = "listen-address";
    private static final String JETTY_CONFIGURATION_FILE = "jetty-configuration-file";
    private static final String ANY_ADDRESS = "0.0.0.0";

    private static final ProxyConfigKeys INSTANCE = new ProxyConfigKeys();

    // --- xroad.proxy ---------------------------------------------------------

    /** {@code xroad.proxy.admin-port}. */
    public static final ConfigKey<Integer> ADMIN_PORT = PROXY
            .integer("admin-port")
            .withDefaultValue(5566)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.ssl-enabled}. */
    public static final ConfigKey<Boolean> SSL_ENABLED = PROXY
            .bool("ssl-enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.dsp-enabled}. */
    public static final ConfigKey<Boolean> DSP_ENABLED = PROXY
            .bool("dsp-enabled")
            .withDefaultValue(true)
            .build();

    /** {@code xroad.proxy.health-check-enabled}. */
    public static final ConfigKey<Boolean> HEALTH_CHECK_ENABLED = PROXY
            .bool("health-check-enabled")
            .withDefaultValue(false)
            .exposedInUi()
            .publishedToFramework()
            .build();

    /** {@code xroad.proxy.health-check-port}. */
    public static final ConfigKey<Integer> HEALTH_CHECK_PORT = PROXY
            .integer("health-check-port")
            .withDefaultValue(5588)
            .exposedInUi()
            .publishedToFramework()
            .build();

    /** {@code xroad.proxy.health-check-interface}. */
    public static final ConfigKey<String> HEALTH_CHECK_INTERFACE = PROXY
            .string("health-check-interface")
            .withDefaultValue(ANY_ADDRESS)
            .exposedInUi()
            .publishedToFramework()
            .build();

    /** {@code xroad.proxy.hsm-health-check-enabled}. */
    public static final ConfigKey<Boolean> HSM_HEALTH_CHECK_ENABLED = PROXY
            .bool("hsm-health-check-enabled")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.memory-usage-threshold} — no default (optional). */
    public static final ConfigKey<Integer> MEMORY_USAGE_THRESHOLD = PROXY
            .integer("memory-usage-threshold")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-sign-digest-name}. */
    public static final ConfigKey<String> MESSAGE_SIGN_DIGEST_NAME = PROXY
            .string("message-sign-digest-name")
            .withDefaultValue("SHA-512")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.verify-client-cert}. */
    public static final ConfigKey<Boolean> VERIFY_CLIENT_CERT = PROXY
            .bool("verify-client-cert")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.log-client-cert}. */
    public static final ConfigKey<Boolean> LOG_CLIENT_CERT = PROXY
            .bool("log-client-cert")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.enforce-client-is-cert-validity-period-check}. */
    public static final ConfigKey<Boolean> ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK = PROXY
            .bool("enforce-client-is-cert-validity-period-check")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.server-port}. */
    public static final ConfigKey<Integer> SERVER_PORT = PROXY
            .integer("server-port")
            .withDefaultValue(5500)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.xroad-tls-ciphers}. */
    public static final ConfigKey<String[]> XROAD_TLS_CIPHERS = PROXY
            .stringArray("xroad-tls-ciphers")
            .withDefaultValue(DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.batch-signing-enabled}. */
    public static final ConfigKey<Boolean> BATCH_SIGNING_ENABLED = PROXY
            .bool("batch-signing-enabled")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.strict-identifier-checks}. */
    public static final ConfigKey<Boolean> STRICT_IDENTIFIER_CHECKS = PROXY
            .bool("strict-identifier-checks")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    // --- xroad.proxy.client-proxy --------------------------------------------

    /** {@code xroad.proxy.client-proxy.connector-host}. */
    public static final ConfigKey<String> CLIENT_PROXY_CONNECTOR_HOST = CLIENT_PROXY
            .string("connector-host")
            .withDefaultValue(ANY_ADDRESS)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-http-port}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTP_PORT = CLIENT_PROXY
            .integer("client-http-port")
            .withDefaultValue(8080)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-https-port}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTPS_PORT = CLIENT_PROXY
            .integer("client-https-port")
            .withDefaultValue(8443)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.jetty-configuration-file}. */
    public static final ConfigKey<String> CLIENT_PROXY_JETTY_CONFIGURATION_FILE = CLIENT_PROXY
            .string(JETTY_CONFIGURATION_FILE)
            .withDefaultValue("classpath:jetty/clientproxy.xml")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-connector-initial-idle-time}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_CONNECTOR_INITIAL_IDLE_TIME = CLIENT_PROXY
            .integer("client-connector-initial-idle-time")
            .withDefaultValue(30000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-timeout}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_TIMEOUT = CLIENT_PROXY
            .integer("client-timeout")
            .withDefaultValue(30000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-httpclient-so-linger}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTPCLIENT_SO_LINGER = CLIENT_PROXY
            .integer("client-httpclient-so-linger")
            .withDefaultValue(-1)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-httpclient-timeout}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_HTTPCLIENT_TIMEOUT = CLIENT_PROXY
            .integer("client-httpclient-timeout")
            .withDefaultValue(0)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.pool-total-max-connections}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_POOL_TOTAL_MAX_CONNECTIONS = CLIENT_PROXY
            .integer("pool-total-max-connections")
            .withDefaultValue(10000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.pool-total-default-max-connections-per-route}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_POOL_TOTAL_DEFAULT_MAX_CONNECTIONS_PER_ROUTE = CLIENT_PROXY
            .integer("pool-total-default-max-connections-per-route")
            .withDefaultValue(2500)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.pool-validate-connections-after-inactivity-of-millis}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MILLIS = CLIENT_PROXY
            .integer("pool-validate-connections-after-inactivity-of-millis")
            .withDefaultValue(2000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-idle-connection-monitor-interval}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_INTERVAL = CLIENT_PROXY
            .integer("client-idle-connection-monitor-interval")
            .withDefaultValue(30000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-idle-connection-monitor-timeout}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_TIMEOUT = CLIENT_PROXY
            .integer("client-idle-connection-monitor-timeout")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-use-idle-connection-monitor}. */
    public static final ConfigKey<Boolean> CLIENT_PROXY_CLIENT_USE_IDLE_CONNECTION_MONITOR = CLIENT_PROXY
            .bool("client-use-idle-connection-monitor")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.fastest-connecting-ssl-uri-cache-period}. */
    public static final ConfigKey<Integer> CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD = CLIENT_PROXY
            .integer("fastest-connecting-ssl-uri-cache-period")
            .withDefaultValue(3600)
            .exposedInUi()
            .build();

    public static final ConfigKey<Duration> CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_UNUSABLE_PERIOD = CLIENT_PROXY
            .keyDuration("fastest-connecting-ssl-uri-unusable-period")
            .withDefaultValue(Duration.ofSeconds(180))
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.use-fastest-connecting-ssl-socket-autoclose}. */
    public static final ConfigKey<Boolean> CLIENT_PROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE = CLIENT_PROXY
            .bool("use-fastest-connecting-ssl-socket-autoclose")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-tls-protocols}. */
    public static final ConfigKey<String[]> CLIENT_PROXY_CLIENT_TLS_PROTOCOLS = CLIENT_PROXY
            .stringArray("client-tls-protocols")
            .withDefaultValue(DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.client-tls-ciphers}. */
    public static final ConfigKey<String[]> CLIENT_PROXY_CLIENT_TLS_CIPHERS = CLIENT_PROXY
            .stringArray("client-tls-ciphers")
            .withDefaultValue(DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.client-proxy.pool-enable-connection-reuse}. */
    public static final ConfigKey<Boolean> CLIENT_PROXY_POOL_ENABLE_CONNECTION_REUSE = CLIENT_PROXY
            .bool("pool-enable-connection-reuse")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    public static final ConfigKey<Boolean> CLIENT_PROXY_ENABLE_REQUEST_RETRY = CLIENT_PROXY
            .bool("enable-request-retry")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    // --- xroad.proxy.server --------------------------------------------------

    /** {@code xroad.proxy.server.listen-address}. */
    public static final ConfigKey<String> SERVER_LISTEN_ADDRESS = SERVER
            .string(LISTEN_ADDRESS)
            .withDefaultValue(ANY_ADDRESS)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.server.listen-port}. */
    public static final ConfigKey<Integer> SERVER_LISTEN_PORT = SERVER
            .integer("listen-port")
            .withDefaultValue(5500)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.server.connector-initial-idle-time}. */
    public static final ConfigKey<Integer> SERVER_CONNECTOR_INITIAL_IDLE_TIME = SERVER
            .integer("connector-initial-idle-time")
            .withDefaultValue(30000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.server.jetty-configuration-file}. */
    public static final ConfigKey<String> SERVER_JETTY_CONFIGURATION_FILE = SERVER
            .string(JETTY_CONFIGURATION_FILE)
            .withDefaultValue("classpath:jetty/serverproxy.xml")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.server.support-clients-pooled-connections}. */
    public static final ConfigKey<Boolean> SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS = SERVER
            .bool("support-clients-pooled-connections")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.server.min-supported-client-version} — no default (optional). */
    public static final ConfigKey<String> SERVER_MIN_SUPPORTED_CLIENT_VERSION = SERVER
            .string("min-supported-client-version")
            .exposedInUi()
            .build();

    // --- xroad.proxy.ocsp-responder ------------------------------------------

    /** {@code xroad.proxy.ocsp-responder.listen-address}. */
    public static final ConfigKey<String> OCSP_RESPONDER_LISTEN_ADDRESS = OCSP_RESPONDER
            .string(LISTEN_ADDRESS)
            .withDefaultValue(ANY_ADDRESS)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.ocsp-responder.port}. */
    public static final ConfigKey<Integer> OCSP_RESPONDER_PORT = OCSP_RESPONDER
            .integer("port")
            .withDefaultValue(5577)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.ocsp-responder.client-connect-timeout}. */
    public static final ConfigKey<Integer> OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT = OCSP_RESPONDER
            .integer("client-connect-timeout")
            .withDefaultValue(20000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.ocsp-responder.client-read-timeout}. */
    public static final ConfigKey<Integer> OCSP_RESPONDER_CLIENT_READ_TIMEOUT = OCSP_RESPONDER
            .integer("client-read-timeout")
            .withDefaultValue(30000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.ocsp-responder.jetty-configuration-file}. */
    public static final ConfigKey<String> OCSP_RESPONDER_JETTY_CONFIGURATION_FILE = OCSP_RESPONDER
            .string(JETTY_CONFIGURATION_FILE)
            .withDefaultValue("classpath:jetty/ocsp-responder.xml")
            .exposedInUi()
            .build();

    // --- xroad.proxy.addon ---------------------------------------------------

    /** {@code xroad.proxy.addon.proxy-monitor.enabled}. */
    public static final ConfigKey<Boolean> ADDON_PROXY_MONITOR_ENABLED = ADDON_PROXY_MONITOR
            .bool(ENABLED)
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.meta-services.enabled}. */
    public static final ConfigKey<Boolean> ADDON_META_SERVICES_ENABLED = ADDON_META_SERVICES
            .bool(ENABLED)
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.enabled}. */
    public static final ConfigKey<Boolean> ADDON_OP_MONITOR_ENABLED = ADDON_OP_MONITOR
            .bool(ENABLED)
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    // --- xroad.proxy.addon.op-monitor.buffer ---------------------------------

    /** {@code xroad.proxy.addon.op-monitor.buffer.size}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_SIZE = ADDON_OP_MONITOR_BUFFER
            .integer("size")
            .withDefaultValue(20000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.max-records-in-message}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_MAX_RECORDS_IN_MESSAGE = ADDON_OP_MONITOR_BUFFER
            .integer("max-records-in-message")
            .withDefaultValue(100)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.sending-interval-seconds}. */
    public static final ConfigKey<Long> ADDON_OP_MONITOR_BUFFER_SENDING_INTERVAL_SECONDS = ADDON_OP_MONITOR_BUFFER
            .longValue("sending-interval-seconds")
            .withDefaultValue(5L)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.socket-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_SOCKET_TIMEOUT_SECONDS = ADDON_OP_MONITOR_BUFFER
            .integer("socket-timeout-seconds")
            .withDefaultValue(60)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.buffer.connection-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_BUFFER_CONNECTION_TIMEOUT_SECONDS = ADDON_OP_MONITOR_BUFFER
            .integer("connection-timeout-seconds")
            .withDefaultValue(50)
            .exposedInUi()
            .build();

    // --- xroad.proxy.addon.op-monitor.connection -----------------------------

    /** {@code xroad.proxy.addon.op-monitor.connection.host}. */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_HOST = ADDON_OP_MONITOR_CONNECTION
            .string("host")
            .withDefaultValue("localhost")
            .withContainerDefaultValue("op-monitor")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.port}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_CONNECTION_PORT = ADDON_OP_MONITOR_CONNECTION
            .integer("port")
            .withDefaultValue(2080)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.scheme}. */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_SCHEME = ADDON_OP_MONITOR_CONNECTION
            .string("scheme")
            .withDefaultValue("http")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.client-tls-certificate} — no default (optional, deprecated). */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_CLIENT_TLS_CERTIFICATE = ADDON_OP_MONITOR_CONNECTION
            .string("client-tls-certificate")
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.tls-certificate} — no default (optional, deprecated). */
    public static final ConfigKey<String> ADDON_OP_MONITOR_CONNECTION_TLS_CERTIFICATE = ADDON_OP_MONITOR_CONNECTION
            .string("tls-certificate")
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.socket-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_CONNECTION_SOCKET_TIMEOUT_SECONDS = ADDON_OP_MONITOR_CONNECTION
            .integer("socket-timeout-seconds")
            .withDefaultValue(60)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.connection-timeout-seconds}. */
    public static final ConfigKey<Integer> ADDON_OP_MONITOR_CONNECTION_CONNECTION_TIMEOUT_SECONDS = ADDON_OP_MONITOR_CONNECTION
            .integer("connection-timeout-seconds")
            .withDefaultValue(30)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.addon.op-monitor.connection.xroad-tls-ciphers}. */
    public static final ConfigKey<String[]> ADDON_OP_MONITOR_CONNECTION_XROAD_TLS_CIPHERS = ADDON_OP_MONITOR_CONNECTION
            .stringArray("xroad-tls-ciphers")
            .withDefaultValue(DEFAULT_XROAD_SSL_CIPHER_SUITES_STRING)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.tls.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_ISSUANCE_ROLE_NAME = TLS_CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.tls.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_COMMON_NAME = TLS_CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.tls.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.tls.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.tls.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> TLS_CERT_PROVISIONING_TTL = TLS_CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.tls.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_SECRET_STORE_PKI_PATH = TLS_CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .exposedInUi()
            .build();

    // --- xroad.anti-dos ---------------------------------------------------------

    /** {@code xroad.anti-dos.max-parallel-connections}. */
    public static final ConfigKey<Integer> ANTI_DOS_MAX_PARALLEL_CONNECTIONS = ANTI_DOS
            .integer("max-parallel-connections")
            .withDefaultValue(5000)
            .exposedInUi()
            .build();

    /** {@code xroad.anti-dos.min-free-file-handles}. */
    public static final ConfigKey<Integer> ANTI_DOS_MIN_FREE_FILE_HANDLES = ANTI_DOS
            .integer("min-free-file-handles")
            .withDefaultValue(100)
            .exposedInUi()
            .build();

    /** {@code xroad.anti-dos.max-cpu-load}. */
    public static final ConfigKey<Double> ANTI_DOS_MAX_CPU_LOAD = ANTI_DOS
            .key("max-cpu-load", Double.class)
            .withConverter(Double::parseDouble)
            .withDefaultValue("1.1")
            .exposedInUi()
            .build();

    /** {@code xroad.anti-dos.max-heap-usage}. */
    public static final ConfigKey<Double> ANTI_DOS_MAX_HEAP_USAGE = ANTI_DOS
            .key("max-heap-usage", Double.class)
            .withConverter(Double::parseDouble)
            .withDefaultValue("1.1")
            .exposedInUi()
            .build();

    /** {@code xroad.anti-dos.enabled}. */
    public static final ConfigKey<Boolean> ANTI_DOS_ENABLED = ANTI_DOS
            .bool(ENABLED)
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    // --- xroad.proxy.health-check.auth-key / .hsm --------------------------------

    /** {@code xroad.proxy.health-check.auth-key.success-ttl}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_AUTH_KEY_SUCCESS_TTL = HEALTH_CHECK_AUTH_KEY
            .keyDuration("success-ttl")
            .withDefaultValue(Duration.parse("PT2S"))
            .build();

    /** {@code xroad.proxy.health-check.auth-key.error-ttl}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_AUTH_KEY_ERROR_TTL = HEALTH_CHECK_AUTH_KEY
            .keyDuration("error-ttl")
            .withDefaultValue(Duration.parse("PT5S"))
            .build();

    /** {@code xroad.proxy.health-check.auth-key.max-error-ttl}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_AUTH_KEY_MAX_ERROR_TTL = HEALTH_CHECK_AUTH_KEY
            .keyDuration("max-error-ttl")
            .withDefaultValue(Duration.parse("PT30S"))
            .build();

    /** {@code xroad.proxy.health-check.auth-key.backoff-multiplier}. */
    public static final ConfigKey<Integer> HEALTH_CHECK_AUTH_KEY_BACKOFF_MULTIPLIER = HEALTH_CHECK_AUTH_KEY
            .integer("backoff-multiplier")
            .withDefaultValue(2)
            .build();

    /** {@code xroad.proxy.health-check.auth-key.timeout}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_AUTH_KEY_TIMEOUT = HEALTH_CHECK_AUTH_KEY
            .keyDuration("timeout")
            .withDefaultValue(Duration.parse("PT5S"))
            .build();

    /** {@code xroad.proxy.health-check.hsm.success-ttl}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_HSM_SUCCESS_TTL = HEALTH_CHECK_HSM
            .keyDuration("success-ttl")
            .withDefaultValue(Duration.parse("PT2S"))
            .build();

    /** {@code xroad.proxy.health-check.hsm.error-ttl}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_HSM_ERROR_TTL = HEALTH_CHECK_HSM
            .keyDuration("error-ttl")
            .withDefaultValue(Duration.parse("PT5S"))
            .build();

    /** {@code xroad.proxy.health-check.hsm.max-error-ttl}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_HSM_MAX_ERROR_TTL = HEALTH_CHECK_HSM
            .keyDuration("max-error-ttl")
            .withDefaultValue(Duration.parse("PT30S"))
            .build();

    /** {@code xroad.proxy.health-check.hsm.backoff-multiplier}. */
    public static final ConfigKey<Integer> HEALTH_CHECK_HSM_BACKOFF_MULTIPLIER = HEALTH_CHECK_HSM
            .integer("backoff-multiplier")
            .withDefaultValue(2)
            .build();

    /** {@code xroad.proxy.health-check.hsm.timeout}. */
    public static final ConfigKey<Duration> HEALTH_CHECK_HSM_TIMEOUT = HEALTH_CHECK_HSM
            .keyDuration("timeout")
            .withDefaultValue(Duration.parse("PT5S"))
            .build();

    // --- xroad.proxy.message-log ------------------------------------------------

    /** {@code xroad.proxy.message-log.enabled}. */
    public static final ConfigKey<Boolean> MESSAGE_LOG_ENABLED = MESSAGE_LOG
            .bool(ENABLED)
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.message-body-logging}. */
    public static final ConfigKey<Boolean> MESSAGE_LOG_MESSAGE_BODY_LOGGING = MESSAGE_LOG
            .bool("message-body-logging")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.max-loggable-message-body-size}. */
    public static final ConfigKey<Long> MESSAGE_LOG_MAX_LOGGABLE_MESSAGE_BODY_SIZE = MESSAGE_LOG
            .longValue("max-loggable-message-body-size")
            .withDefaultValue(10485760L)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.truncated-body-allowed}. */
    public static final ConfigKey<Boolean> MESSAGE_LOG_TRUNCATED_BODY_ALLOWED = MESSAGE_LOG
            .bool("truncated-body-allowed")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.hash-algo-id}. */
    public static final ConfigKey<String> MESSAGE_LOG_HASH_ALGO_ID = MESSAGE_LOG
            .string("hash-algo-id")
            .withDefaultValue("SHA-512")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamping-prioritization-strategy}. */
    public static final ConfigKey<String> MESSAGE_LOG_TIMESTAMPING_PRIORITIZATION_STRATEGY = MESSAGE_LOG
            .string("timestamping-prioritization-strategy")
            .withDefaultValue("NONE")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.enabled-body-logging-local-producer-subsystems} — optional. */
    public static final ConfigKey<String> MESSAGE_LOG_ENABLED_BODY_LOGGING_LOCAL_PRODUCER_SUBSYSTEMS = MESSAGE_LOG
            .string("enabled-body-logging-local-producer-subsystems")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.enabled-body-logging-remote-producer-subsystems} — optional. */
    public static final ConfigKey<String> MESSAGE_LOG_ENABLED_BODY_LOGGING_REMOTE_PRODUCER_SUBSYSTEMS = MESSAGE_LOG
            .string("enabled-body-logging-remote-producer-subsystems")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.disabled-body-logging-local-producer-subsystems} — optional. */
    public static final ConfigKey<String> MESSAGE_LOG_DISABLED_BODY_LOGGING_LOCAL_PRODUCER_SUBSYSTEMS = MESSAGE_LOG
            .string("disabled-body-logging-local-producer-subsystems")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.disabled-body-logging-remote-producer-subsystems} — optional. */
    public static final ConfigKey<String> MESSAGE_LOG_DISABLED_BODY_LOGGING_REMOTE_PRODUCER_SUBSYSTEMS = MESSAGE_LOG
            .string("disabled-body-logging-remote-producer-subsystems")
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamper.client-connect-timeout}. */
    public static final ConfigKey<Integer> MESSAGE_LOG_TIMESTAMPER_CLIENT_CONNECT_TIMEOUT = MESSAGE_LOG_TIMESTAMPER
            .integer("client-connect-timeout")
            .withDefaultValue(20000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamper.client-read-timeout}. */
    public static final ConfigKey<Integer> MESSAGE_LOG_TIMESTAMPER_CLIENT_READ_TIMEOUT = MESSAGE_LOG_TIMESTAMPER
            .integer("client-read-timeout")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamper.timestamp-immediately}. */
    public static final ConfigKey<Boolean> MESSAGE_LOG_TIMESTAMPER_TIMESTAMP_IMMEDIATELY = MESSAGE_LOG_TIMESTAMPER
            .bool("timestamp-immediately")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamper.records-limit}. */
    public static final ConfigKey<Integer> MESSAGE_LOG_TIMESTAMPER_RECORDS_LIMIT = MESSAGE_LOG_TIMESTAMPER
            .integer("records-limit")
            .withDefaultValue(10000)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamper.retry-delay}. */
    public static final ConfigKey<Integer> MESSAGE_LOG_TIMESTAMPER_RETRY_DELAY = MESSAGE_LOG_TIMESTAMPER
            .integer("retry-delay")
            .withDefaultValue(60)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.message-log.timestamper.acceptable-timestamp-failure-period}. */
    public static final ConfigKey<Integer> MESSAGE_LOG_TIMESTAMPER_ACCEPTABLE_TIMESTAMP_FAILURE_PERIOD = MESSAGE_LOG_TIMESTAMPER
            .integer("acceptable-timestamp-failure-period")
            .withDefaultValue(14400)
            .exposedInUi()
            .build();

    // --- xroad.proxy.rpc --------------------------------------------------------

    /** {@code xroad.proxy.rpc.enabled}. */
    public static final ConfigKey<Boolean> RPC_ENABLED = RPC
            .bool(ENABLED)
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.rpc.listen-address}. */
    public static final ConfigKey<String> RPC_LISTEN_ADDRESS = RPC
            .string(LISTEN_ADDRESS)
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue(ANY_ADDRESS)
            .exposedInUi()
            .build();

    /** {@code xroad.proxy.rpc.port}. */
    public static final ConfigKey<Integer> RPC_PORT = RPC
            .integer("port")
            .withDefaultValue(5567)
            .exposedInUi()
            .build();

    // --- xroad.proxy.dsp --------------------------------------------------------

    /** {@code xroad.proxy.dsp.participant-context-id} — no default (must be set per SS). */
    public static final ConfigKey<String> DSP_PARTICIPANT_CONTEXT_ID = DSP
            .string("participant-context-id")
            .build();

    /** {@code xroad.proxy.dsp.protocol}. */
    public static final ConfigKey<String> DSP_PROTOCOL = DSP
            .string("protocol")
            .withDefaultValue("http-dsp-profile-2025-1")
            .build();

    /** {@code xroad.proxy.dsp.listen-address}. */
    public static final ConfigKey<String> DSP_LISTEN_ADDRESS = DSP
            .string(LISTEN_ADDRESS)
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue(ANY_ADDRESS)
            .build();

    /** {@code xroad.proxy.dsp.listen-port}. */
    public static final ConfigKey<Integer> DSP_LISTEN_PORT = DSP
            .integer("listen-port")
            .withDefaultValue(5590)
            .build();

    /** {@code xroad.proxy.dsp.thread-pool-min}. */
    public static final ConfigKey<Integer> DSP_THREAD_POOL_MIN = DSP
            .integer("thread-pool-min")
            .withDefaultValue(10)
            .build();

    /** {@code xroad.proxy.dsp.thread-pool-max}. */
    public static final ConfigKey<Integer> DSP_THREAD_POOL_MAX = DSP
            .integer("thread-pool-max")
            .withDefaultValue(200)
            .build();

    /** {@code xroad.proxy.dsp.thread-pool-idle-timeout}. */
    public static final ConfigKey<Integer> DSP_THREAD_POOL_IDLE_TIMEOUT = DSP
            .integer("thread-pool-idle-timeout")
            .withDefaultValue(60000)
            .build();

    /** {@code xroad.proxy.dsp.serverproxy-endpoint}. */
    public static final ConfigKey<String> DSP_SERVERPROXY_ENDPOINT = DSP
            .string("serverproxy-endpoint")
            .withDefaultValue("https://localhost:5500")
            .build();

    /** {@code xroad.proxy.dsp.cache.enabled}. */
    public static final ConfigKey<Boolean> DSP_CACHE_ENABLED = DSP_CACHE
            .bool(ENABLED)
            .withDefaultValue(true)
            .build();

    /** {@code xroad.proxy.dsp.cache.default-ttl}. */
    public static final ConfigKey<Duration> DSP_CACHE_DEFAULT_TTL = DSP_CACHE
            .keyDuration("default-ttl")
            .withDefaultValue(Duration.parse("PT5M"))
            .build();

    /** {@code xroad.proxy.dsp.cache.maximum-size}. */
    public static final ConfigKey<Long> DSP_CACHE_MAXIMUM_SIZE = DSP_CACHE
            .longValue("maximum-size")
            .withDefaultValue(10000L)
            .build();

    private ProxyConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static ProxyConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return PROXY.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return Stream.concat(PROXY.keys().stream(), ANTI_DOS.keys().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
