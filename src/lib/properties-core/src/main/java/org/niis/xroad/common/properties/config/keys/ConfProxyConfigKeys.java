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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/**
 * Keys for the {@code xroad.configuration-proxy} scope, including scalars,
 * the JSON-map {@code instances} key, and the TLS cert-provisioning sub-scope.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public final class ConfProxyConfigKeys implements ConfigKeyProvider {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, ConfProxyInstanceConfig>> INSTANCES_TYPE = new TypeReference<>() { };

    private static final Prefix ROOT = Prefix.of(Category.CONFIGURATION_PROXY, "xroad.configuration-proxy");
    private static final Prefix TLS = ROOT.subPrefix("tls");
    private static final Prefix TLS_CERT_PROVISIONING = TLS.subPrefix("certificate-provisioning");

    private static final ConfProxyConfigKeys INSTANCE = new ConfProxyConfigKeys();

    /** {@code xroad.configuration-proxy.minimum-global-configuration-version}. */
    public static final ConfigKey<Integer> MINIMUM_GLOBAL_CONFIGURATION_VERSION = ROOT
            .integer("minimum-global-configuration-version")
            .withDefaultValue(2)
            .build();

    /** {@code xroad.configuration-proxy.address}. */
    public static final ConfigKey<String> ADDRESS = ROOT
            .string("address")
            .withDefaultValue("0.0.0.0")
            .build();

    /** {@code xroad.configuration-proxy.hash-algorithm-uri} — optional, no default. */
    public static final ConfigKey<String> HASH_ALGORITHM_URI = ROOT
            .string("hash-algorithm-uri")
            .build();

    /** {@code xroad.configuration-proxy.signature-digest-algorithm-id} — optional, no default. */
    public static final ConfigKey<String> SIGNATURE_DIGEST_ALGORITHM_ID = ROOT
            .string("signature-digest-algorithm-id")
            .build();

    /** {@code xroad.configuration-proxy.generated-conf-path}. */
    public static final ConfigKey<String> GENERATED_CONF_PATH = ROOT
            .string("generated-conf-path")
            .withDefaultValue("/var/lib/xroad/public")
            .build();

    /** {@code xroad.configuration-proxy.configuration-path}. */
    public static final ConfigKey<String> CONFIGURATION_PATH = ROOT
            .string("configuration-path")
            .withDefaultValue("/etc/xroad/confproxy")
            .build();

    /** {@code xroad.configuration-proxy.update-interval}. */
    public static final ConfigKey<String> UPDATE_INTERVAL = ROOT
            .string("update-interval")
            .withDefaultValue("60s")
            .publishedToFramework()
            .build();

    /** {@code xroad.configuration-proxy.global-conf-download-path}. */
    public static final ConfigKey<String> GLOBAL_CONF_DOWNLOAD_PATH = ROOT
            .string("global-conf-download-path")
            .withDefaultValue("/etc/xroad/globalconf")
            .build();

    /** {@code xroad.configuration-proxy.auto-init-soft-token} — optional, no default. */
    public static final ConfigKey<String> AUTO_INIT_SOFT_TOKEN = ROOT
            .string("auto-init-soft-token")
            .build();

    /**
     * {@code xroad.configuration-proxy.instances}: JSON object mapping instance names to their
     * configuration, e.g. {@code {"EE":{"tokenId":"tok1","sourceAnchorFileUri":"http://cs/anchor.xml"}}}.
     * Default is an empty map (JSON {@code {}}).
     */
    public static final ConfigKey<Map<String, ConfProxyInstanceConfig>> INSTANCES = ROOT
            .key("instances", instancesType())
            .withConverter(ConfProxyConfigKeys::parseInstances)
            .withDefaultValue("{}")
            .build();

    /** {@code xroad.configuration-proxy.tls.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_ISSUANCE_ROLE_NAME = TLS_CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .build();

    /** {@code xroad.configuration-proxy.tls.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_COMMON_NAME = TLS_CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .build();

    /** {@code xroad.configuration-proxy.tls.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .build();

    /** {@code xroad.configuration-proxy.tls.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("127.0.0.1")
            .build();

    /** {@code xroad.configuration-proxy.tls.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> TLS_CERT_PROVISIONING_TTL = TLS_CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .build();

    /** {@code xroad.configuration-proxy.tls.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_SECRET_STORE_PKI_PATH = TLS_CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .build();

    private ConfProxyConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static ConfProxyConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return ROOT.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return ROOT.keys();
    }

    private static Map<String, ConfProxyInstanceConfig> parseInstances(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(raw, INSTANCES_TYPE);
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, ConfProxyInstanceConfig>> instancesType() {
        return (Class<Map<String, ConfProxyInstanceConfig>>) (Class<?>) Map.class;
    }
}
