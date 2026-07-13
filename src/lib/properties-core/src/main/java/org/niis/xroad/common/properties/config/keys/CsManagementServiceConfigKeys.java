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

import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/**
 * Central Server management-service keys ({@code xroad.management-service.*}, incl.
 * {@code .tls.certificate-provisioning} and {@code .http-client-properties} sub-trees).
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class CsManagementServiceConfigKeys implements ConfigKeyProvider {

    private static final Scope MANAGEMENT = Scope.of("xroad.management-service");
    private static final Scope TLS_CERT_PROVISIONING = MANAGEMENT.child("tls").child("certificate-provisioning");
    private static final Scope HTTP_CLIENT = MANAGEMENT.child("http-client-properties");

    private static final CsManagementServiceConfigKeys INSTANCE = new CsManagementServiceConfigKeys();

    // --- rate limiting ---
    /** {@code xroad.management-service.rate-limit-enabled}. */
    public static final ConfigKey<Boolean> RATE_LIMIT_ENABLED = MANAGEMENT
            .bool("rate-limit-enabled")
            .withDefaultValue(true)
            .build();
    /** {@code xroad.management-service.rate-limit-requests-per-second}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_SECOND = MANAGEMENT
            .integer("rate-limit-requests-per-second")
            .withDefaultValue(-1)
            .build();
    /** {@code xroad.management-service.rate-limit-requests-per-minute}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_MINUTE = MANAGEMENT
            .integer("rate-limit-requests-per-minute")
            .withDefaultValue(10)
            .build();
    /** {@code xroad.management-service.rate-limit-cache-size}. */
    public static final ConfigKey<Integer> RATE_LIMIT_CACHE_SIZE = MANAGEMENT
            .integer("rate-limit-cache-size")
            .withDefaultValue(10000)
            .build();
    /** {@code xroad.management-service.rate-limit-expire-after-access-minutes}. */
    public static final ConfigKey<Integer> RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES = MANAGEMENT
            .integer("rate-limit-expire-after-access-minutes")
            .withDefaultValue(2)
            .build();

    // --- admin API client ---
    /** {@code xroad.management-service.api-base-url}. */
    public static final ConfigKey<String> API_BASE_URL = MANAGEMENT
            .string("api-base-url")
            .withDefaultValue("https://127.0.0.1:4000/api/v1")
            .build();
    /** {@code xroad.management-service.api-token} — required; no default. */
    public static final ConfigKey<String> API_TOKEN = MANAGEMENT
            .string("api-token")
            .build();

    // --- http-client-properties ---
    /** {@code xroad.management-service.http-client-properties.max-connections-per-route}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_MAX_CONNECTIONS_PER_ROUTE = HTTP_CLIENT
            .integer("max-connections-per-route")
            .withDefaultValue(50)
            .build();
    /** {@code xroad.management-service.http-client-properties.max-connections-total}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_MAX_CONNECTIONS_TOTAL = HTTP_CLIENT
            .integer("max-connections-total")
            .withDefaultValue(50)
            .build();
    /** {@code xroad.management-service.http-client-properties.connection-timeout-seconds}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_CONNECTION_TIMEOUT_SECONDS = HTTP_CLIENT
            .integer("connection-timeout-seconds")
            .withDefaultValue(5)
            .build();
    /** {@code xroad.management-service.http-client-properties.connection-request-timeout-seconds}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_CONNECTION_REQUEST_TIMEOUT_SECONDS = HTTP_CLIENT
            .integer("connection-request-timeout-seconds")
            .withDefaultValue(10)
            .build();
    /** {@code xroad.management-service.http-client-properties.response-timeout-seconds}. */
    public static final ConfigKey<Integer> HTTP_CLIENT_RESPONSE_TIMEOUT_SECONDS = HTTP_CLIENT
            .integer("response-timeout-seconds")
            .withDefaultValue(5)
            .build();

    // --- tls.certificate-provisioning ---
    /** {@code xroad.management-service.tls.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_ISSUANCE_ROLE_NAME = TLS_CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .build();
    /** {@code xroad.management-service.tls.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_COMMON_NAME = TLS_CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .build();
    /** {@code xroad.management-service.tls.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .build();
    /** {@code xroad.management-service.tls.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("127.0.0.1")
            .build();
    /** {@code xroad.management-service.tls.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> TLS_CERT_PROVISIONING_TTL = TLS_CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .build();
    /** {@code xroad.management-service.tls.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_SECRET_STORE_PKI_PATH = TLS_CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .build();

    private CsManagementServiceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static CsManagementServiceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return MANAGEMENT;
    }
}
