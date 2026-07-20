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
import java.util.List;

import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/** Common-RPC keys ({@code xroad.common-rpc.*}): root props, cert-provisioning, and channel sub-trees. */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class CommonRpcConfigKeys implements ConfigKeyProvider {

    private static final Prefix ROOT = Prefix.of(Category.COMMON, "xroad.common-rpc");
    private static final Prefix CERT_PROVISIONING = ROOT.subPrefix("certificate-provisioning");
    private static final Prefix CHANNEL = ROOT.subPrefix("channel");
    private static final Prefix CHANNEL_SIGNER = CHANNEL.subPrefix("signer");
    private static final Prefix CHANNEL_SOFTTOKEN_SIGNER = CHANNEL.subPrefix("softtoken-signer");
    private static final Prefix CHANNEL_OP_MONITOR = CHANNEL.subPrefix("op-monitor");
    private static final Prefix CHANNEL_ENV_MONITOR = CHANNEL.subPrefix("env-monitor");
    private static final Prefix CHANNEL_CONF_CLIENT = CHANNEL.subPrefix("configuration-client");
    private static final Prefix CHANNEL_PROXY = CHANNEL.subPrefix("proxy");
    private static final Prefix CHANNEL_AUXILIARY_SERVICE = CHANNEL.subPrefix("auxiliary-service");
    private static final Prefix CHANNEL_ASSET_ACCESS = CHANNEL.subPrefix("asset-access");

    private static final CommonRpcConfigKeys INSTANCE = new CommonRpcConfigKeys();

    // --- root ---

    /** {@code xroad.common-rpc.use-tls}. */
    public static final ConfigKey<Boolean> USE_TLS = ROOT
            .bool("use-tls")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    // --- certificate-provisioning ---

    /** {@code xroad.common-rpc.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> CERT_PROVISIONING_ISSUANCE_ROLE_NAME = CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> CERT_PROVISIONING_COMMON_NAME = CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> CERT_PROVISIONING_ALT_NAMES = CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("127.0.0.1")
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> CERT_PROVISIONING_TTL = CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> CERT_PROVISIONING_SECRET_STORE_PKI_PATH = CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.refresh-interval}. */
    public static final ConfigKey<Duration> CERT_PROVISIONING_REFRESH_INTERVAL = CERT_PROVISIONING
            .keyDuration("refresh-interval")
            .withDefaultValue(Duration.ofHours(5))
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.retry-delay}. */
    public static final ConfigKey<Duration> CERT_PROVISIONING_RETRY_DELAY = CERT_PROVISIONING
            .keyDuration("retry-delay")
            .withDefaultValue(Duration.ofSeconds(5))
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.retry-exponential-backoff-multiplier}. */
    public static final ConfigKey<String> CERT_PROVISIONING_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER = CERT_PROVISIONING
            .string("retry-exponential-backoff-multiplier")
            .withDefaultValue("1.5")
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.retry-max-attempts}. */
    public static final ConfigKey<Integer> CERT_PROVISIONING_RETRY_MAX_ATTEMPTS = CERT_PROVISIONING
            .integer("retry-max-attempts")
            .withDefaultValue(10)
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.certificate-provisioning.retry-timeout}. */
    public static final ConfigKey<Duration> CERT_PROVISIONING_RETRY_TIMEOUT = CERT_PROVISIONING
            .keyDuration("retry-timeout")
            .withDefaultValue(Duration.ofSeconds(60))
            .exposedInUi()
            .build();

    // --- channel.signer ---

    /** {@code xroad.common-rpc.channel.signer.host}. */
    public static final ConfigKey<String> CHANNEL_SIGNER_HOST = CHANNEL_SIGNER
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("signer")
            .build();

    /** {@code xroad.common-rpc.channel.signer.port}. */
    public static final ConfigKey<Integer> CHANNEL_SIGNER_PORT = CHANNEL_SIGNER
            .integer("port")
            .withDefaultValue(5560)
            .build();

    /** {@code xroad.common-rpc.channel.signer.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_SIGNER_DEADLINE_AFTER = CHANNEL_SIGNER
            .integer("deadline-after")
            .withDefaultValue(60000)
            .build();

    // --- channel.softtoken-signer ---

    /** {@code xroad.common-rpc.channel.softtoken-signer.host}. */
    public static final ConfigKey<String> CHANNEL_SOFTTOKEN_SIGNER_HOST = CHANNEL_SOFTTOKEN_SIGNER
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("softtoken-signer")
            .build();

    /** {@code xroad.common-rpc.channel.softtoken-signer.port}. */
    public static final ConfigKey<Integer> CHANNEL_SOFTTOKEN_SIGNER_PORT = CHANNEL_SOFTTOKEN_SIGNER
            .integer("port")
            .withDefaultValue(5561)
            .build();

    /** {@code xroad.common-rpc.channel.softtoken-signer.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_SOFTTOKEN_SIGNER_DEADLINE_AFTER = CHANNEL_SOFTTOKEN_SIGNER
            .integer("deadline-after")
            .withDefaultValue(60000)
            .build();

    /** {@code xroad.common-rpc.channel.softtoken-signer.enabled}. */
    public static final ConfigKey<Boolean> CHANNEL_SOFTTOKEN_SIGNER_ENABLED = CHANNEL_SOFTTOKEN_SIGNER
            .bool("enabled")
            .withDefaultValue(false)
            .build();

    // --- channel.op-monitor ---

    /** {@code xroad.common-rpc.channel.op-monitor.host}. */
    public static final ConfigKey<String> CHANNEL_OP_MONITOR_HOST = CHANNEL_OP_MONITOR
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("op-monitor")
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.op-monitor.port}. */
    public static final ConfigKey<Integer> CHANNEL_OP_MONITOR_PORT = CHANNEL_OP_MONITOR
            .integer("port")
            .withDefaultValue(2081)
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.op-monitor.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_OP_MONITOR_DEADLINE_AFTER = CHANNEL_OP_MONITOR
            .integer("deadline-after")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    // --- channel.env-monitor ---

    /** {@code xroad.common-rpc.channel.env-monitor.host}. */
    public static final ConfigKey<String> CHANNEL_ENV_MONITOR_HOST = CHANNEL_ENV_MONITOR
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("monitor")
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.env-monitor.port}. */
    public static final ConfigKey<Integer> CHANNEL_ENV_MONITOR_PORT = CHANNEL_ENV_MONITOR
            .integer("port")
            .withDefaultValue(2552)
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.env-monitor.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_ENV_MONITOR_DEADLINE_AFTER = CHANNEL_ENV_MONITOR
            .integer("deadline-after")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    // --- channel.configuration-client ---

    /** {@code xroad.common-rpc.channel.configuration-client.host}. */
    public static final ConfigKey<String> CHANNEL_CONF_CLIENT_HOST = CHANNEL_CONF_CLIENT
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("configuration-client")
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.configuration-client.port}. */
    public static final ConfigKey<Integer> CHANNEL_CONF_CLIENT_PORT = CHANNEL_CONF_CLIENT
            .integer("port")
            .withDefaultValue(5665)
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.configuration-client.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_CONF_CLIENT_DEADLINE_AFTER = CHANNEL_CONF_CLIENT
            .integer("deadline-after")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    // --- channel.proxy ---

    /** {@code xroad.common-rpc.channel.proxy.host}. */
    public static final ConfigKey<String> CHANNEL_PROXY_HOST = CHANNEL_PROXY
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("proxy")
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.proxy.port}. */
    public static final ConfigKey<Integer> CHANNEL_PROXY_PORT = CHANNEL_PROXY
            .integer("port")
            .withDefaultValue(5567)
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.proxy.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_PROXY_DEADLINE_AFTER = CHANNEL_PROXY
            .integer("deadline-after")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    // --- channel.auxiliary-service ---

    /** {@code xroad.common-rpc.channel.auxiliary-service.host}. */
    public static final ConfigKey<String> CHANNEL_AUXILIARY_SERVICE_HOST = CHANNEL_AUXILIARY_SERVICE
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("auxiliary-service")
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.auxiliary-service.port}. */
    public static final ConfigKey<Integer> CHANNEL_AUXILIARY_SERVICE_PORT = CHANNEL_AUXILIARY_SERVICE
            .integer("port")
            .withDefaultValue(7665)
            .exposedInUi()
            .build();

    /** {@code xroad.common-rpc.channel.auxiliary-service.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_AUXILIARY_SERVICE_DEADLINE_AFTER = CHANNEL_AUXILIARY_SERVICE
            .integer("deadline-after")
            .withDefaultValue(60000)
            .exposedInUi()
            .build();

    // --- channel.asset-access ---

    /** {@code xroad.common-rpc.channel.asset-access.host}. */
    public static final ConfigKey<String> CHANNEL_ASSET_ACCESS_HOST = CHANNEL_ASSET_ACCESS
            .string("host")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("ds-control-plane")
            .build();

    /** {@code xroad.common-rpc.channel.asset-access.port}. */
    public static final ConfigKey<Integer> CHANNEL_ASSET_ACCESS_PORT = CHANNEL_ASSET_ACCESS
            .integer("port")
            .withDefaultValue(5461)
            .build();

    /** {@code xroad.common-rpc.channel.asset-access.deadline-after}. */
    public static final ConfigKey<Integer> CHANNEL_ASSET_ACCESS_DEADLINE_AFTER = CHANNEL_ASSET_ACCESS
            .integer("deadline-after")
            .withDefaultValue(60000)
            .build();

    private CommonRpcConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static CommonRpcConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Prefix scope() {
        return ROOT;
    }

    @Override
    public List<ConfigKey<?>> keys() {
        return ROOT.keys();
    }
}
