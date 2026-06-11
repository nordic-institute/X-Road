/*
 * The MIT License
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
package org.niis.xroad.signer.core.config;

import ee.ria.xroad.common.ServicePrioritizationStrategy;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

/**
 * DSL config keys for the {@code xroad.signer} namespace.
 *
 * <p>Lives in signer-core (not properties-api) because it references domain types
 * ({@link ServicePrioritizationStrategy}, {@link SignerModuleConfig}) and uses Jackson for the
 * modules document converter. Register via {@code XRoadConfigBuilder.register(SignerConfigKeys.instance())};
 * do <em>not</em> add to {@code ConfigKeyProviders.allProviders()}.
 */
@SuppressWarnings("checkstyle:MagicNumber")
public final class SignerConfigKeys implements ConfigKeyProvider {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)
            .build();
    private static final TypeReference<Map<String, SignerModuleConfig>> MODULES_TYPE = new TypeReference<>() { };

    private static final Scope SIGNER = Scope.of("xroad.signer", "signer");
    private static final Scope AUTOLOGIN = SIGNER.child("autologin");
    private static final Scope AUTOLOGIN_RETRY = AUTOLOGIN.child("retry");
    private static final Scope HWTOKEN = SIGNER.child("addon").child("hwtoken");
    private static final Scope PIN_HASHER = SIGNER.child("pin-hasher");

    private static final SignerConfigKeys INSTANCE = new SignerConfigKeys();

    /** {@code xroad.signer.selfsigned-cert-digest-algorithm} */
    public static final ConfigKey<String> SELFSIGNED_CERT_DIGEST_ALGORITHM = SIGNER
            .string("selfsigned-cert-digest-algorithm")
            .withDefaultValue("SHA-512")
            .build();

    /** {@code xroad.signer.csr-signature-digest-algorithm} */
    public static final ConfigKey<String> CSR_SIGNATURE_DIGEST_ALGORITHM = SIGNER
            .string("csr-signature-digest-algorithm")
            .withDefaultValue("SHA-256")
            .build();

    /** {@code xroad.signer.enforce-token-pin-policy} */
    public static final ConfigKey<Boolean> ENFORCE_TOKEN_PIN_POLICY = SIGNER
            .bool("enforce-token-pin-policy")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.signer.ocsp-response-retrieval-active} */
    public static final ConfigKey<Boolean> OCSP_RESPONSE_RETRIEVAL_ACTIVE = SIGNER
            .bool("ocsp-response-retrieval-active")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.signer.ocsp-retry-delay} */
    public static final ConfigKey<Integer> OCSP_RETRY_DELAY = SIGNER
            .integer("ocsp-retry-delay")
            .withDefaultValue(60)
            .build();

    /** {@code xroad.signer.ocsp-cache-path} */
    public static final ConfigKey<String> OCSP_CACHE_PATH = SIGNER
            .string("ocsp-cache-path")
            .withDefaultValue("/var/cache/xroad/")
            .build();

    /** {@code xroad.signer.ocsp-prioritization-strategy} */
    public static final ConfigKey<ServicePrioritizationStrategy> OCSP_PRIORITIZATION_STRATEGY = SIGNER
            .keyEnum("ocsp-prioritization-strategy", ServicePrioritizationStrategy.class)
            .defaultValue(ServicePrioritizationStrategy.NONE)
            .build();

    /** {@code xroad.signer.module-manager-update-interval} */
    public static final ConfigKey<Integer> MODULE_MANAGER_UPDATE_INTERVAL = SIGNER
            .integer("module-manager-update-interval")
            .withDefaultValue(60)
            .build();

    /** {@code xroad.signer.soft-token-rsa-sign-mechanism} */
    public static final ConfigKey<String> SOFT_TOKEN_RSA_SIGN_MECHANISM = SIGNER
            .string("soft-token-rsa-sign-mechanism")
            .withDefaultValue("CKM_RSA_PKCS")
            .build();

    /** {@code xroad.signer.soft-token-ec-sign-mechanism} */
    public static final ConfigKey<String> SOFT_TOKEN_EC_SIGN_MECHANISM = SIGNER
            .string("soft-token-ec-sign-mechanism")
            .withDefaultValue("CKM_ECDSA")
            .build();

    /** {@code xroad.signer.soft-token-pin-keystore-algorithm} */
    public static final ConfigKey<String> SOFT_TOKEN_PIN_KEYSTORE_ALGORITHM = SIGNER
            .string("soft-token-pin-keystore-algorithm")
            .withDefaultValue("RSA")
            .build();

    /** {@code xroad.signer.key-length} */
    public static final ConfigKey<Integer> KEY_LENGTH = SIGNER
            .integer("key-length")
            .withDefaultValue(2048)
            .build();

    /** {@code xroad.signer.key-named-curve} */
    public static final ConfigKey<String> KEY_NAMED_CURVE = SIGNER
            .string("key-named-curve")
            .withDefaultValue("secp256r1")
            .build();

    /**
     * {@code xroad.signer.modules}: the entire hardware-module map as a single JSON/YAML document.
     * An empty or blank value is treated as an empty map.
     */
    public static final ConfigKey<Map<String, SignerModuleConfig>> MODULES = SIGNER
            .key("modules", modulesType())
            .withConverter(SignerConfigKeys::parseModules)
            .withDefaultValue("")
            .build();

    /** {@code xroad.signer.autologin.enabled} */
    public static final ConfigKey<Boolean> AUTOLOGIN_ENABLED = AUTOLOGIN
            .bool("enabled")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.signer.autologin.retry.retry-delay} */
    public static final ConfigKey<Duration> AUTOLOGIN_RETRY_DELAY = AUTOLOGIN_RETRY
            .keyDuration("retry-delay")
            .withDefaultValue(Duration.parse("PT3S"))
            .build();

    /** {@code xroad.signer.autologin.retry.retry-exponential-backoff-multiplier} — stored as string, parsed at use */
    public static final ConfigKey<String> AUTOLOGIN_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER = AUTOLOGIN_RETRY
            .string("retry-exponential-backoff-multiplier")
            .withDefaultValue("1.0")
            .build();

    /** {@code xroad.signer.autologin.retry.retry-max-attempts} */
    public static final ConfigKey<Integer> AUTOLOGIN_RETRY_MAX_ATTEMPTS = AUTOLOGIN_RETRY
            .integer("retry-max-attempts")
            .withDefaultValue(20)
            .build();

    /** {@code xroad.signer.autologin.retry.retry-timeout} */
    public static final ConfigKey<Duration> AUTOLOGIN_RETRY_TIMEOUT = AUTOLOGIN_RETRY
            .keyDuration("retry-timeout")
            .withDefaultValue(Duration.parse("PT60S"))
            .build();

    /** {@code xroad.signer.addon.hwtoken.enabled} */
    public static final ConfigKey<Boolean> HWTOKEN_ENABLED = HWTOKEN
            .bool("enabled")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.signer.addon.hwtoken.session-pool-enabled} */
    public static final ConfigKey<Boolean> HWTOKEN_POOL_ENABLED = HWTOKEN
            .bool("session-pool-enabled")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.signer.addon.hwtoken.session-pool-max-total} */
    public static final ConfigKey<Integer> HWTOKEN_POOL_MAX_TOTAL = HWTOKEN
            .integer("session-pool-max-total")
            .withDefaultValue(10)
            .build();

    /** {@code xroad.signer.addon.hwtoken.session-pool-min-idle} */
    public static final ConfigKey<Integer> HWTOKEN_POOL_MIN_IDLE = HWTOKEN
            .integer("session-pool-min-idle")
            .withDefaultValue(2)
            .build();

    /** {@code xroad.signer.addon.hwtoken.session-pool-max-idle} */
    public static final ConfigKey<Integer> HWTOKEN_POOL_MAX_IDLE = HWTOKEN
            .integer("session-pool-max-idle")
            .withDefaultValue(5)
            .build();

    /** {@code xroad.signer.addon.hwtoken.session-acquire-timeout} */
    public static final ConfigKey<Duration> HWTOKEN_SESSION_ACQUIRE_TIMEOUT = HWTOKEN
            .keyDuration("session-acquire-timeout")
            .withDefaultValue(Duration.parse("PT15S"))
            .build();

    /** {@code xroad.signer.pin-hasher.iterations} */
    public static final ConfigKey<Integer> PIN_HASHER_ITERATIONS = PIN_HASHER
            .integer("iterations")
            .withDefaultValue(4)
            .build();

    /** {@code xroad.signer.pin-hasher.memory-kb} */
    public static final ConfigKey<Integer> PIN_HASHER_MEMORY_KB = PIN_HASHER
            .integer("memory-kb")
            .withDefaultValue(19456)
            .build();

    /** {@code xroad.signer.pin-hasher.parallelism} */
    public static final ConfigKey<Integer> PIN_HASHER_PARALLELISM = PIN_HASHER
            .integer("parallelism")
            .withDefaultValue(4)
            .build();

    /** {@code xroad.signer.pin-hasher.hash-length} */
    public static final ConfigKey<Integer> PIN_HASHER_HASH_LENGTH = PIN_HASHER
            .integer("hash-length")
            .withDefaultValue(32)
            .build();

    private SignerConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static SignerConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return SIGNER;
    }

    private static Map<String, SignerModuleConfig> parseModules(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return MAPPER.readValue(raw, MODULES_TYPE);
    }

    @SuppressWarnings("unchecked")
    private static Class<Map<String, SignerModuleConfig>> modulesType() {
        return (Class<Map<String, SignerModuleConfig>>) (Class<?>) Map.class;
    }
}
