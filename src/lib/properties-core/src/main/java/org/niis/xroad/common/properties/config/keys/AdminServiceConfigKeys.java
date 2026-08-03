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
import java.util.stream.Stream;

import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING;
import static org.niis.xroad.common.properties.DefaultTlsProperties.DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING;
import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/**
 * Security Server admin-service keys ({@code xroad.proxy-ui-api.*}, incl. {@code .tls} and the
 * {@code .complementary-user-role-mappings} per-role sub-trees). {@code DataSize} and enum-typed
 * values are modelled as String keys here (converted in the consuming properties class) to keep
 * this module framework-neutral.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class AdminServiceConfigKeys implements ConfigKeyProvider {

    private static final Prefix ADMIN = Prefix.of(Category.PROXY_UI_API, "xroad.proxy-ui-api");
    private static final Prefix TLS_CERT_PROVISIONING = ADMIN.subPrefix("tls").subPrefix("certificate-provisioning");
    private static final Prefix ROLE_MAPPINGS = ADMIN.subPrefix("complementary-user-role-mappings");
    private static final Prefix DATASPACE = ADMIN.subPrefix("dataspace");

    // admin-service config that lives at the top-level xroad.* namespace (not under xroad.proxy-ui-api),
    // stored as a single YAML document per key and parsed by the consuming beans
    private static final Prefix XROAD = Prefix.of(Category.COMMON, "xroad");

    private static final AdminServiceConfigKeys INSTANCE = new AdminServiceConfigKeys();

    // --- rate limiting ---
    /** {@code xroad.proxy-ui-api.rate-limit-enabled}. */
    public static final ConfigKey<Boolean> RATE_LIMIT_ENABLED = ADMIN
            .bool("rate-limit-enabled")
            .withDefaultValue(true)
            .build();
    /** {@code xroad.proxy-ui-api.rate-limit-requests-per-second}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_SECOND = ADMIN
            .integer("rate-limit-requests-per-second")
            .withDefaultValue(20)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.rate-limit-requests-per-minute}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_MINUTE = ADMIN
            .integer("rate-limit-requests-per-minute")
            .withDefaultValue(600)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.rate-limit-cache-size}. */
    public static final ConfigKey<Integer> RATE_LIMIT_CACHE_SIZE = ADMIN
            .integer("rate-limit-cache-size")
            .withDefaultValue(10000)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.rate-limit-expire-after-access-minutes}. */
    public static final ConfigKey<Integer> RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES = ADMIN
            .integer("rate-limit-expire-after-access-minutes")
            .withDefaultValue(5)
            .exposedInUi()
            .build();

    // --- caching ---
    /** {@code xroad.proxy-ui-api.cache-default-ttl}. */
    public static final ConfigKey<Integer> CACHE_DEFAULT_TTL = ADMIN
            .integer("cache-default-ttl")
            .withDefaultValue(60)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.cache-api-key-ttl}. */
    public static final ConfigKey<Integer> CACHE_API_KEY_TTL = ADMIN
            .integer("cache-api-key-ttl")
            .withDefaultValue(60)
            .exposedInUi()
            .build();

    // --- request handling ---
    /** {@code xroad.proxy-ui-api.allowed-hostnames} — no default (any hostname allowed when unset). */
    public static final ConfigKey<String[]> ALLOWED_HOSTNAMES = ADMIN
            .stringArray("allowed-hostnames")
            .build();
    /** {@code xroad.proxy-ui-api.strict-identifier-checks}. */
    public static final ConfigKey<Boolean> STRICT_IDENTIFIER_CHECKS = ADMIN
            .bool("strict-identifier-checks")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.request-size-limit-regular} — Spring {@code DataSize} string. */
    public static final ConfigKey<String> REQUEST_SIZE_LIMIT_REGULAR = ADMIN
            .string("request-size-limit-regular")
            .withDefaultValue("50KB")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.request-size-limit-binary-upload} — Spring {@code DataSize} string. */
    public static final ConfigKey<String> REQUEST_SIZE_LIMIT_BINARY_UPLOAD = ADMIN
            .string("request-size-limit-binary-upload")
            .withDefaultValue("10MB")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.reserved-service-codes} — service codes that cannot be added as user services. */
    public static final ConfigKey<String[]> RESERVED_SERVICE_CODES = ADMIN
            .stringArray("reserved-service-codes")
            .withDefaultValue("listClients,listMethods,allowedMethods,getWsdl,getOpenAPI,"
                    + "getSecurityServerMetrics,getSecurityServerOperationalData,getSecurityServerHealthData")
            .build();

    // --- key algorithms / auth ---
    /** {@code xroad.proxy-ui-api.authentication-key-algorithm} — {@code KeyAlgorithm} name. */
    public static final ConfigKey<String> AUTHENTICATION_KEY_ALGORITHM = ADMIN
            .string("authentication-key-algorithm")
            .withDefaultValue("RSA")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.signing-key-algorithm} — {@code KeyAlgorithm} name. */
    public static final ConfigKey<String> SIGNING_KEY_ALGORITHM = ADMIN
            .string("signing-key-algorithm")
            .withDefaultValue("RSA")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.authentication-provider} — PAM natively, DATABASE in containers. */
    public static final ConfigKey<String> AUTHENTICATION_PROVIDER = ADMIN
            .string("authentication-provider")
            .withDefaultValue("PAM")
            .withContainerDefaultValue("DATABASE")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.enforce-user-password-policy} (off natively, on in containers). */
    public static final ConfigKey<Boolean> ENFORCE_USER_PASSWORD_POLICY = ADMIN
            .bool("enforce-user-password-policy")
            .withDefaultValue(false)
            .withContainerDefaultValue(true)
            .exposedInUi()
            .build();

    // --- certificate management ---
    /** {@code xroad.proxy-ui-api.auto-update-timestamp-service-url}. */
    public static final ConfigKey<Boolean> AUTO_UPDATE_TIMESTAMP_SERVICE_URL = ADMIN
            .bool("auto-update-timestamp-service-url")
            .withDefaultValue(false)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.automatic-activate-auth-certificate}. */
    public static final ConfigKey<Boolean> AUTOMATIC_ACTIVATE_AUTH_CERTIFICATE = ADMIN
            .bool("automatic-activate-auth-certificate")
            .withDefaultValue(false)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.allow-csr-for-key-with-certificate}. */
    public static final ConfigKey<Boolean> ALLOW_CSR_FOR_KEY_WITH_CERTIFICATE = ADMIN
            .bool("allow-csr-for-key-with-certificate")
            .withDefaultValue(false)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.auth-cert-reg-signature-digest-algorithm-id}. */
    public static final ConfigKey<String> AUTH_CERT_REG_SIGNATURE_DIGEST_ALGORITHM_ID = ADMIN
            .string("auth-cert-reg-signature-digest-algorithm-id")
            .withDefaultValue("SHA-512")
            .exposedInUi()
            .build();

    // --- mail notifications ---
    /** {@code xroad.proxy-ui-api.acme-renewal-success-notification-enabled}. */
    public static final ConfigKey<Boolean> ACME_RENEWAL_SUCCESS_NOTIFICATION_ENABLED = ADMIN
            .bool("acme-renewal-success-notification-enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-renewal-failure-notification-enabled}. */
    public static final ConfigKey<Boolean> ACME_RENEWAL_FAILURE_NOTIFICATION_ENABLED = ADMIN
            .bool("acme-renewal-failure-notification-enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.auth-cert-registered-notification-enabled}. */
    public static final ConfigKey<Boolean> AUTH_CERT_REGISTERED_NOTIFICATION_ENABLED = ADMIN
            .bool("auth-cert-registered-notification-enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.cert-auto-activation-notification-enabled}. */
    public static final ConfigKey<Boolean> CERT_AUTO_ACTIVATION_NOTIFICATION_ENABLED = ADMIN
            .bool("cert-auto-activation-notification-enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.cert-auto-activation-failure-notification-enabled}. */
    public static final ConfigKey<Boolean> CERT_AUTO_ACTIVATION_FAILURE_NOTIFICATION_ENABLED = ADMIN
            .bool("cert-auto-activation-failure-notification-enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.mail-notification-locale} — no default. */
    public static final ConfigKey<String> MAIL_NOTIFICATION_LOCALE = ADMIN
            .string("mail-notification-locale")
            .exposedInUi()
            .build();

    // --- ACME ---
    /** {@code xroad.proxy-ui-api.acme-renewal-active}. */
    public static final ConfigKey<Boolean> ACME_RENEWAL_ACTIVE = ADMIN
            .bool("acme-renewal-active")
            .withDefaultValue(true)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-renewal-retry-delay}. */
    public static final ConfigKey<Integer> ACME_RENEWAL_RETRY_DELAY = ADMIN
            .integer("acme-renewal-retry-delay")
            .withDefaultValue(60)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-renewal-interval}. */
    public static final ConfigKey<Integer> ACME_RENEWAL_INTERVAL = ADMIN
            .integer("acme-renewal-interval")
            .withDefaultValue(3600)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-renewal-time-before-expiration-date}. */
    public static final ConfigKey<Integer> ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE = ADMIN
            .integer("acme-renewal-time-before-expiration-date")
            .withDefaultValue(14)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-keypair-renewal-time-before-expiration-date}. */
    public static final ConfigKey<Integer> ACME_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE = ADMIN
            .integer("acme-keypair-renewal-time-before-expiration-date")
            .withDefaultValue(14)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.automatic-activate-acme-sign-certificate}. */
    public static final ConfigKey<Boolean> AUTOMATIC_ACTIVATE_ACME_SIGN_CERTIFICATE = ADMIN
            .bool("automatic-activate-acme-sign-certificate")
            .withDefaultValue(false)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-authorization-wait-attempts}. */
    public static final ConfigKey<Integer> ACME_AUTHORIZATION_WAIT_ATTEMPTS = ADMIN
            .integer("acme-authorization-wait-attempts")
            .withDefaultValue(5)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-authorization-wait-interval}. */
    public static final ConfigKey<Integer> ACME_AUTHORIZATION_WAIT_INTERVAL = ADMIN
            .integer("acme-authorization-wait-interval")
            .withDefaultValue(5)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-certificate-wait-attempts}. */
    public static final ConfigKey<Integer> ACME_CERTIFICATE_WAIT_ATTEMPTS = ADMIN
            .integer("acme-certificate-wait-attempts")
            .withDefaultValue(5)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-certificate-wait-interval}. */
    public static final ConfigKey<Integer> ACME_CERTIFICATE_WAIT_INTERVAL = ADMIN
            .integer("acme-certificate-wait-interval")
            .withDefaultValue(5)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-certificate-account-key-pair-expiration}. */
    public static final ConfigKey<Integer> ACME_CERTIFICATE_ACCOUNT_KEY_PAIR_EXPIRATION = ADMIN
            .integer("acme-certificate-account-key-pair-expiration")
            .withDefaultValue(365)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-challenge-port-enabled}. */
    public static final ConfigKey<Boolean> ACME_CHALLENGE_PORT_ENABLED = ADMIN
            .bool("acme-challenge-port-enabled")
            .withDefaultValue(false)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-challenge-port}. */
    public static final ConfigKey<Integer> ACME_CHALLENGE_PORT = ADMIN
            .integer("acme-challenge-port")
            .withDefaultValue(80)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-key-length}. */
    public static final ConfigKey<Integer> ACME_KEY_LENGTH = ADMIN
            .integer("acme-key-length")
            .withDefaultValue(2048)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-account-keystore-path} — no default. */
    public static final ConfigKey<String> ACME_ACCOUNT_KEYSTORE_PATH = ADMIN
            .string("acme-account-keystore-path")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.acme-challenge-path} — no default. */
    public static final ConfigKey<String> ACME_CHALLENGE_PATH = ADMIN
            .string("acme-challenge-path")
            .exposedInUi()
            .build();

    // --- proxy server (management requests) ---
    /** {@code xroad.proxy-ui-api.proxy-server-url}. */
    public static final ConfigKey<String> PROXY_SERVER_URL = ADMIN
            .string("proxy-server-url")
            .withDefaultValue("https://localhost:8443")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.proxy-server-connect-timeout}. */
    public static final ConfigKey<Integer> PROXY_SERVER_CONNECT_TIMEOUT = ADMIN
            .integer("proxy-server-connect-timeout")
            .withDefaultValue(30000)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.proxy-server-socket-timeout}. */
    public static final ConfigKey<Integer> PROXY_SERVER_SOCKET_TIMEOUT = ADMIN
            .integer("proxy-server-socket-timeout")
            .withDefaultValue(0)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.proxy-server-enable-connection-reuse}. */
    public static final ConfigKey<Boolean> PROXY_SERVER_ENABLE_CONNECTION_REUSE = ADMIN
            .bool("proxy-server-enable-connection-reuse")
            .withDefaultValue(false)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.proxy-tls-protocols}. */
    public static final ConfigKey<String[]> PROXY_TLS_PROTOCOLS = ADMIN
            .stringArray("proxy-tls-protocols")
            .withDefaultValue(DEFAULT_PROXY_CLIENT_TLS_PROTOCOLS_STRING)
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.proxy-tls-cipher-suites}. */
    public static final ConfigKey<String[]> PROXY_TLS_CIPHER_SUITES = ADMIN
            .stringArray("proxy-tls-cipher-suites")
            .withDefaultValue(DEFAULT_PROXY_CLIENT_SSL_CIPHER_SUITES_STRING)
            .exposedInUi()
            .build();

    // --- whitelists / misc ---
    /** {@code xroad.proxy-ui-api.key-management-api-whitelist}. */
    public static final ConfigKey<String> KEY_MANAGEMENT_API_WHITELIST = ADMIN
            .string("key-management-api-whitelist")
            .withDefaultValue("127.0.0.0/8, ::1")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.regular-api-whitelist}. */
    public static final ConfigKey<String> REGULAR_API_WHITELIST = ADMIN
            .string("regular-api-whitelist")
            .withDefaultValue("0.0.0.0/0, ::/0")
            .exposedInUi()
            .build();

    // --- tls.certificate-provisioning ---
    /** {@code xroad.proxy-ui-api.tls.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_ISSUANCE_ROLE_NAME = TLS_CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.tls.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_COMMON_NAME = TLS_CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.tls.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.tls.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("127.0.0.1")
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.tls.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> TLS_CERT_PROVISIONING_TTL = TLS_CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .exposedInUi()
            .build();
    /** {@code xroad.proxy-ui-api.tls.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_SECRET_STORE_PKI_PATH = TLS_CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .exposedInUi()
            .build();

    // --- complementary user-role mappings (one stringArray per Role; empty by default) ---
    /** {@code xroad.proxy-ui-api.complementary-user-role-mappings.xroad-security-officer}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SECURITY_OFFICER = ROLE_MAPPINGS
            .stringArray("xroad-security-officer")
            .withDefaultValue("")
            .build();
    /** {@code xroad.proxy-ui-api.complementary-user-role-mappings.xroad-registration-officer}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_REGISTRATION_OFFICER = ROLE_MAPPINGS
            .stringArray("xroad-registration-officer")
            .withDefaultValue("")
            .build();
    /** {@code xroad.proxy-ui-api.complementary-user-role-mappings.xroad-service-administrator}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SERVICE_ADMINISTRATOR = ROLE_MAPPINGS
            .stringArray("xroad-service-administrator")
            .withDefaultValue("")
            .build();
    /** {@code xroad.proxy-ui-api.complementary-user-role-mappings.xroad-system-administrator}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SYSTEM_ADMINISTRATOR = ROLE_MAPPINGS
            .stringArray("xroad-system-administrator")
            .withDefaultValue("")
            .build();
    /** {@code xroad.proxy-ui-api.complementary-user-role-mappings.xroad-securityserver-observer}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SECURITYSERVER_OBSERVER = ROLE_MAPPINGS
            .stringArray("xroad-securityserver-observer")
            .withDefaultValue("")
            .build();
    /** {@code xroad.proxy-ui-api.complementary-user-role-mappings.xroad-management-service}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_MANAGEMENT_SERVICE = ROLE_MAPPINGS
            .stringArray("xroad-management-service")
            .withDefaultValue("")
            .build();

    // --- top-level YAML-document config (parsed by the consuming beans) ---
    /** {@code xroad.acme} — full ACME configuration as a YAML/JSON document; no default (unset = disabled). */
    public static final ConfigKey<String> ACME = XROAD
            .string("acme")
            .build();
    /** {@code xroad.mail-notification} — full mail-notification configuration as a YAML/JSON document; no default. */
    public static final ConfigKey<String> MAIL_NOTIFICATION = XROAD
            .string("mail-notification")
            .build();

    public static final ConfigKey<Boolean> DATASPACE_ENABLED = DATASPACE
            .bool("enabled")
            .withDefaultValue(false)
            .build();

    public static final ConfigKey<String> DATASPACE_IDENTITY_HUB_URL = DATASPACE
            .string("identity-hub-url")
            .build();

    public static final ConfigKey<String> DATASPACE_CONTROL_PLANE_URL = DATASPACE
            .string("control-plane-url")
            .build();

    public static final ConfigKey<String> DATASPACE_PARTICIPANT_ID = DATASPACE
            .string("participant-id")
            .build();

    public static final ConfigKey<Boolean> DATASPACE_MANAGEMENT_CONTEXT_ENABLED = DATASPACE
            .bool("management-context-enabled")
            .withDefaultValue(false)
            .build();

    public static final ConfigKey<String> DATASPACE_ISSUER_DID = DATASPACE
            .string("issuer-did")
            .build();

    public static final ConfigKey<String> DATASPACE_CREDENTIAL_DEFINITION_ID = DATASPACE
            .string("credential-definition-id")
            .withDefaultValue("xroad-membership-credential-definition")
            .build();

    public static final ConfigKey<String> DATASPACE_IDENTITY_TOKEN = DATASPACE
            .string("identity_token")
            .build();

    public static final ConfigKey<String> DATASPACE_CONTROL_PLANE_TOKEN = DATASPACE
            .string("control-plane-token")
            .build();

    public static final ConfigKey<Integer> DATASPACE_REQUEST_TIMEOUT_MILLIS = DATASPACE
            .integer("request-timeout-millis")
            .withDefaultValue(15000)
            .build();

    public static final ConfigKey<Integer> DATASPACE_POLL_TIMEOUT_MILLIS = DATASPACE
            .integer("poll-timeout-millis")
            .withDefaultValue(30000)
            .build();

    public static final ConfigKey<Integer> DATASPACE_POLL_INTERVAL_MILLIS = DATASPACE
            .integer("poll-interval-millis")
            .withDefaultValue(2000)
            .build();

    public static final ConfigKey<Integer> DATASPACE_MAX_HOLDER_PID_SLOTS = DATASPACE
            .integer("max-holder-pid-slots")
            .withDefaultValue(20)
            .build();

    private AdminServiceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static AdminServiceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Prefix scope() {
        return ADMIN;
    }

    @Override
    public List<ConfigKey<?>> keys() {
        return Stream.concat(ADMIN.keys().stream(), XROAD.keys().stream()).toList();
    }
}
