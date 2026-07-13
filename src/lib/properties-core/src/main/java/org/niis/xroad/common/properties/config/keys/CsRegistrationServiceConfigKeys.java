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

import java.time.Duration;

/**
 * Central Server registration-service keys ({@code xroad.registration-service.*}, incl.
 * {@code .http-client-properties} and {@code .vault-retry} sub-trees). The exponential backoff multiplier
 * is modelled as a String key (parsed to {@code double} in the consuming properties class), since the DSL
 * has no double-typed key builder.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class CsRegistrationServiceConfigKeys implements ConfigKeyProvider {

    private static final Scope REGISTRATION = Scope.of("xroad.registration-service");
    private static final Scope HTTP_CLIENT = REGISTRATION.child("http-client-properties");
    private static final Scope VAULT_RETRY = REGISTRATION.child("vault-retry");

    private static final CsRegistrationServiceConfigKeys INSTANCE = new CsRegistrationServiceConfigKeys();

    // --- rate limiting ---
    /** {@code xroad.registration-service.rate-limit-enabled}. */
    public static final ConfigKey<Boolean> RATE_LIMIT_ENABLED = REGISTRATION
            .bool("rate-limit-enabled")
            .withDefaultValue(true)
            .build();
    /** {@code xroad.registration-service.rate-limit-requests-per-second}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_SECOND = REGISTRATION
            .integer("rate-limit-requests-per-second")
            .withDefaultValue(-1)
            .build();
    /** {@code xroad.registration-service.rate-limit-requests-per-minute}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_MINUTE = REGISTRATION
            .integer("rate-limit-requests-per-minute")
            .withDefaultValue(10)
            .build();
    /** {@code xroad.registration-service.rate-limit-cache-size}. */
    public static final ConfigKey<Integer> RATE_LIMIT_CACHE_SIZE = REGISTRATION
            .integer("rate-limit-cache-size")
            .withDefaultValue(10000)
            .build();
    /** {@code xroad.registration-service.rate-limit-expire-after-access-minutes}. */
    public static final ConfigKey<Integer> RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES = REGISTRATION
            .integer("rate-limit-expire-after-access-minutes")
            .withDefaultValue(2)
            .build();

    // --- admin API client ---
    /** {@code xroad.registration-service.api-base-url}. */
    public static final ConfigKey<String> API_BASE_URL = REGISTRATION
            .string("api-base-url")
            .withDefaultValue("https://127.0.0.1:4000/api/v1")
            .build();
    /** {@code xroad.registration-service.api-token} — required; no default. */
    public static final ConfigKey<String> API_TOKEN = REGISTRATION
            .string("api-token")
            .build();

    // --- http-client-properties ---
    /** {@code xroad.registration-service.http-client-properties.max-connections-per-route}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_MAX_CONNECTIONS_PER_ROUTE = HTTP_CLIENT
            .integer("max-connections-per-route")
            .withDefaultValue(50)
            .build();
    /** {@code xroad.registration-service.http-client-properties.max-connections-total}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_MAX_CONNECTIONS_TOTAL = HTTP_CLIENT
            .integer("max-connections-total")
            .withDefaultValue(50)
            .build();
    /** {@code xroad.registration-service.http-client-properties.connection-timeout-seconds}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_CONNECTION_TIMEOUT_SECONDS = HTTP_CLIENT
            .integer("connection-timeout-seconds")
            .withDefaultValue(5)
            .build();
    /** {@code xroad.registration-service.http-client-properties.connection-request-timeout-seconds}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_CONNECTION_REQUEST_TIMEOUT_SECONDS = HTTP_CLIENT
            .integer("connection-request-timeout-seconds")
            .withDefaultValue(10)
            .build();
    /** {@code xroad.registration-service.http-client-properties.response-timeout-seconds}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_RESPONSE_TIMEOUT_SECONDS = HTTP_CLIENT
            .integer("response-timeout-seconds")
            .withDefaultValue(5)
            .build();

    // --- vault-retry ---
    /** {@code xroad.registration-service.vault-retry.retry-max-attempts}. */
    public static final ConfigKey<Integer> VAULT_RETRY_RETRY_MAX_ATTEMPTS = VAULT_RETRY
            .integer("retry-max-attempts")
            .withDefaultValue(5)
            .build();
    /** {@code xroad.registration-service.vault-retry.retry-delay}. */
    public static final ConfigKey<Duration> VAULT_RETRY_RETRY_DELAY = VAULT_RETRY
            .keyDuration("retry-delay")
            .withDefaultValue(Duration.ofSeconds(2))
            .build();
    /** {@code xroad.registration-service.vault-retry.retry-exponential-backoff-multiplier} — parsed to double. */
    public static final ConfigKey<String> VAULT_RETRY_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER = VAULT_RETRY
            .string("retry-exponential-backoff-multiplier")
            .withDefaultValue("2.0")
            .build();

    private CsRegistrationServiceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static CsRegistrationServiceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return REGISTRATION;
    }
}
