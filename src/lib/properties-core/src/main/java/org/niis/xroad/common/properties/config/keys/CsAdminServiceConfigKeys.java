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

import static org.niis.xroad.common.properties.EnvProperties.xroadHost;

/**
 * Central Server admin-service keys ({@code xroad.admin-service.*}, incl. {@code .tls.certificate-provisioning},
 * {@code .complementary-user-role-mappings}, {@code .global-conf-generator} and {@code .management-requests}
 * sub-trees). {@code DataSize} and enum-typed values are modelled as String keys here (converted in the
 * consuming properties classes) to keep this module framework-neutral.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class CsAdminServiceConfigKeys implements ConfigKeyProvider {

    private static final Prefix ADMIN = Prefix.of(Category.ADMIN_SERVICE, "xroad.admin-service");
    private static final Prefix TLS_CERT_PROVISIONING = ADMIN.subPrefix("tls").subPrefix("certificate-provisioning");
    private static final Prefix ROLE_MAPPINGS = ADMIN.subPrefix("complementary-user-role-mappings");
    private static final Prefix GLOBAL_CONF_GENERATOR = ADMIN.subPrefix("global-conf-generator");
    private static final Prefix MANAGEMENT_REQUESTS = ADMIN.subPrefix("management-requests");

    private static final CsAdminServiceConfigKeys INSTANCE = new CsAdminServiceConfigKeys();

    // --- global configuration generation ---
    /** {@code xroad.admin-service.global-configuration-generation-rate-in-seconds}. */
    public static final ConfigKey<Integer> GLOBAL_CONFIGURATION_GENERATION_RATE_IN_SECONDS = ADMIN
            .integer("global-configuration-generation-rate-in-seconds")
            .withDefaultValue(60)
            .publishedToFramework()
            .build();

    // --- rate limiting ---
    /** {@code xroad.admin-service.rate-limit-enabled}. */
    public static final ConfigKey<Boolean> RATE_LIMIT_ENABLED = ADMIN
            .bool("rate-limit-enabled")
            .withDefaultValue(true)
            .build();
    /** {@code xroad.admin-service.rate-limit-requests-per-second}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_SECOND = ADMIN
            .integer("rate-limit-requests-per-second")
            .withDefaultValue(20)
            .build();
    /** {@code xroad.admin-service.rate-limit-requests-per-minute}. */
    public static final ConfigKey<Integer> RATE_LIMIT_REQUESTS_PER_MINUTE = ADMIN
            .integer("rate-limit-requests-per-minute")
            .withDefaultValue(600)
            .build();
    /** {@code xroad.admin-service.rate-limit-cache-size}. */
    public static final ConfigKey<Integer> RATE_LIMIT_CACHE_SIZE = ADMIN
            .integer("rate-limit-cache-size")
            .withDefaultValue(10000)
            .build();
    /** {@code xroad.admin-service.rate-limit-expire-after-access-minutes}. */
    public static final ConfigKey<Integer> RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES = ADMIN
            .integer("rate-limit-expire-after-access-minutes")
            .withDefaultValue(5)
            .build();

    // --- caching ---
    /** {@code xroad.admin-service.cache-default-ttl}. */
    public static final ConfigKey<Integer> CACHE_DEFAULT_TTL = ADMIN
            .integer("cache-default-ttl")
            .withDefaultValue(60)
            .build();
    /** {@code xroad.admin-service.cache-api-key-ttl}. */
    public static final ConfigKey<Integer> CACHE_API_KEY_TTL = ADMIN
            .integer("cache-api-key-ttl")
            .withDefaultValue(60)
            .build();

    // --- request handling ---
    /** {@code xroad.admin-service.allowed-hostnames} — no default (any hostname allowed when unset). */
    public static final ConfigKey<String[]> ALLOWED_HOSTNAMES = ADMIN
            .stringArray("allowed-hostnames")
            .build();
    /** {@code xroad.admin-service.strict-identifier-checks}. */
    public static final ConfigKey<Boolean> STRICT_IDENTIFIER_CHECKS = ADMIN
            .bool("strict-identifier-checks")
            .withDefaultValue(true)
            .build();
    /** {@code xroad.admin-service.request-size-limit-regular} — Spring {@code DataSize} string. */
    public static final ConfigKey<String> REQUEST_SIZE_LIMIT_REGULAR = ADMIN
            .string("request-size-limit-regular")
            .withDefaultValue("50KB")
            .build();
    /** {@code xroad.admin-service.request-size-limit-binary-upload} — Spring {@code DataSize} string. */
    public static final ConfigKey<String> REQUEST_SIZE_LIMIT_BINARY_UPLOAD = ADMIN
            .string("request-size-limit-binary-upload")
            .withDefaultValue("10MB")
            .publishedToFramework()
            .build();

    // --- allowed files (Set<String>, modelled as string arrays) ---
    /** {@code xroad.admin-service.backup-allowed-content-types}. */
    public static final ConfigKey<String[]> BACKUP_ALLOWED_CONTENT_TYPES = ADMIN
            .stringArray("backup-allowed-content-types")
            .withDefaultValue("application/pgp-encrypted")
            .build();
    /** {@code xroad.admin-service.xml-allowed-extensions}. */
    public static final ConfigKey<String[]> XML_ALLOWED_EXTENSIONS = ADMIN
            .stringArray("xml-allowed-extensions")
            .withDefaultValue("xml")
            .build();
    /** {@code xroad.admin-service.xml-allowed-content-types}. */
    public static final ConfigKey<String[]> XML_ALLOWED_CONTENT_TYPES = ADMIN
            .stringArray("xml-allowed-content-types")
            .withDefaultValue("text/xml,application/xml")
            .build();
    /** {@code xroad.admin-service.certificate-allowed-extensions}. */
    public static final ConfigKey<String[]> CERTIFICATE_ALLOWED_EXTENSIONS = ADMIN
            .stringArray("certificate-allowed-extensions")
            .withDefaultValue("der,crt,pem,cer")
            .build();
    /** {@code xroad.admin-service.certificate-allowed-content-types}. */
    public static final ConfigKey<String[]> CERTIFICATE_ALLOWED_CONTENT_TYPES = ADMIN
            .stringArray("certificate-allowed-content-types")
            .withDefaultValue("application/x-x509-cert,text/plain")
            .build();

    // --- key algorithms / auth ---
    /** {@code xroad.admin-service.external-key-algorithm} — {@code KeyAlgorithm} name; no default. */
    public static final ConfigKey<String> EXTERNAL_KEY_ALGORITHM = ADMIN
            .string("external-key-algorithm")
            .build();
    /** {@code xroad.admin-service.internal-key-algorithm} — {@code KeyAlgorithm} name; no default. */
    public static final ConfigKey<String> INTERNAL_KEY_ALGORITHM = ADMIN
            .string("internal-key-algorithm")
            .build();
    /** {@code xroad.admin-service.authentication-provider}. */
    public static final ConfigKey<String> AUTHENTICATION_PROVIDER = ADMIN
            .string("authentication-provider")
            .withDefaultValue("PAM")
            .build();
    /** {@code xroad.admin-service.enforce-user-password-policy}. */
    public static final ConfigKey<Boolean> ENFORCE_USER_PASSWORD_POLICY = ADMIN
            .bool("enforce-user-password-policy")
            .withDefaultValue(false)
            .build();

    // --- misc admin flags ---
    /** {@code xroad.admin-service.trusted-anchors-allowed}. */
    public static final ConfigKey<Boolean> TRUSTED_ANCHORS_ALLOWED = ADMIN
            .bool("trusted-anchors-allowed")
            .withDefaultValue(true)
            .build();
    /** {@code xroad.admin-service.conf-backup-path}. */
    public static final ConfigKey<String> CONF_BACKUP_PATH = ADMIN
            .string("conf-backup-path")
            .withDefaultValue("/var/lib/xroad/backup/")
            .build();
    /**
     * {@code xroad.admin-service.app-log-path} — directory holding the global-conf generation status
     * file. Not exposed in the UI: a filesystem path fixed by packaging.
     */
    public static final ConfigKey<String> APP_LOG_PATH = ADMIN
            .string("app-log-path")
            .withDefaultValue("/var/log/xroad/")
            .build();
    /** {@code xroad.admin-service.backup-format-version-file-path}. */
    public static final ConfigKey<String> BACKUP_FORMAT_VERSION_FILE_PATH = ADMIN
            .string("backup-format-version-file-path")
            .withDefaultValue("/usr/share/xroad/scripts/_backup_format_version")
            .build();
    /** {@code xroad.admin-service.create-backup-metadata-path}. */
    public static final ConfigKey<String> CREATE_BACKUP_METADATA_PATH = ADMIN
            .string("create-backup-metadata-path")
            .withDefaultValue("/usr/share/xroad/scripts/_create_backup_metadata.sh")
            .build();
    /** {@code xroad.admin-service.key-management-api-whitelist}. */
    public static final ConfigKey<String> KEY_MANAGEMENT_API_WHITELIST = ADMIN
            .string("key-management-api-whitelist")
            .withDefaultValue("127.0.0.0/8, ::1")
            .build();
    /** {@code xroad.admin-service.regular-api-whitelist}. */
    public static final ConfigKey<String> REGULAR_API_WHITELIST = ADMIN
            .string("regular-api-whitelist")
            .withDefaultValue("0.0.0.0/0, ::/0")
            .build();

    // --- tls.certificate-provisioning ---
    /** {@code xroad.admin-service.tls.certificate-provisioning.issuance-role-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_ISSUANCE_ROLE_NAME = TLS_CERT_PROVISIONING
            .string("issuance-role-name")
            .withDefaultValue("xrd-internal")
            .build();
    /** {@code xroad.admin-service.tls.certificate-provisioning.common-name}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_COMMON_NAME = TLS_CERT_PROVISIONING
            .string("common-name")
            .withDefaultValue(xroadHost("localhost"))
            .build();
    /** {@code xroad.admin-service.tls.certificate-provisioning.alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("alt-names")
            .withDefaultValue("")
            .build();
    /** {@code xroad.admin-service.tls.certificate-provisioning.ip-subject-alt-names}. */
    public static final ConfigKey<String[]> TLS_CERT_PROVISIONING_IP_SUBJECT_ALT_NAMES = TLS_CERT_PROVISIONING
            .stringArray("ip-subject-alt-names")
            .withDefaultValue("127.0.0.1")
            .build();
    /** {@code xroad.admin-service.tls.certificate-provisioning.ttl}. */
    public static final ConfigKey<Duration> TLS_CERT_PROVISIONING_TTL = TLS_CERT_PROVISIONING
            .keyDuration("ttl")
            .withDefaultValue(Duration.ofDays(3650))
            .build();
    /** {@code xroad.admin-service.tls.certificate-provisioning.secret-store-pki-path}. */
    public static final ConfigKey<String> TLS_CERT_PROVISIONING_SECRET_STORE_PKI_PATH = TLS_CERT_PROVISIONING
            .string("secret-store-pki-path")
            .withDefaultValue("xrd-pki")
            .build();

    // --- complementary user-role mappings (one stringArray per Role; empty by default) ---
    /** {@code xroad.admin-service.complementary-user-role-mappings.xroad-security-officer}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SECURITY_OFFICER = ROLE_MAPPINGS
            .stringArray("xroad-security-officer")
            .withDefaultValue("")
            .build();
    /** {@code xroad.admin-service.complementary-user-role-mappings.xroad-registration-officer}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_REGISTRATION_OFFICER = ROLE_MAPPINGS
            .stringArray("xroad-registration-officer")
            .withDefaultValue("")
            .build();
    /** {@code xroad.admin-service.complementary-user-role-mappings.xroad-service-administrator}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SERVICE_ADMINISTRATOR = ROLE_MAPPINGS
            .stringArray("xroad-service-administrator")
            .withDefaultValue("")
            .build();
    /** {@code xroad.admin-service.complementary-user-role-mappings.xroad-system-administrator}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SYSTEM_ADMINISTRATOR = ROLE_MAPPINGS
            .stringArray("xroad-system-administrator")
            .withDefaultValue("")
            .build();
    /** {@code xroad.admin-service.complementary-user-role-mappings.xroad-securityserver-observer}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_SECURITYSERVER_OBSERVER = ROLE_MAPPINGS
            .stringArray("xroad-securityserver-observer")
            .withDefaultValue("")
            .build();
    /** {@code xroad.admin-service.complementary-user-role-mappings.xroad-management-service}. */
    public static final ConfigKey<String[]> COMPLEMENTARY_ROLE_MANAGEMENT_SERVICE = ROLE_MAPPINGS
            .stringArray("xroad-management-service")
            .withDefaultValue("")
            .build();

    // --- global-conf-generator ---
    /** {@code xroad.admin-service.global-conf-generator.internal-directory}. */
    public static final ConfigKey<String> GLOBAL_CONF_GENERATOR_INTERNAL_DIRECTORY = GLOBAL_CONF_GENERATOR
            .string("internal-directory")
            .withDefaultValue("internalconf")
            .build();
    /** {@code xroad.admin-service.global-conf-generator.external-directory}. */
    public static final ConfigKey<String> GLOBAL_CONF_GENERATOR_EXTERNAL_DIRECTORY = GLOBAL_CONF_GENERATOR
            .string("external-directory")
            .withDefaultValue("externalconf")
            .build();
    /** {@code xroad.admin-service.global-conf-generator.generated-conf-dir}. */
    public static final ConfigKey<String> GLOBAL_CONF_GENERATOR_GENERATED_CONF_DIR = GLOBAL_CONF_GENERATOR
            .string("generated-conf-dir")
            .withDefaultValue("/var/lib/xroad/public")
            .build();
    /** {@code xroad.admin-service.global-conf-generator.minimum-global-configuration-version}. */
    public static final ConfigKey<Integer> GLOBAL_CONF_GENERATOR_MINIMUM_GLOBAL_CONFIGURATION_VERSION = GLOBAL_CONF_GENERATOR
            .integer("minimum-global-configuration-version")
            .withDefaultValue(2)
            .build();

    // --- management-requests (auto-approval toggles) ---
    /** {@code xroad.admin-service.management-requests.auto-approve-auth-cert-reg-requests}. */
    public static final ConfigKey<Boolean> MANAGEMENT_REQUESTS_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS = MANAGEMENT_REQUESTS
            .bool("auto-approve-auth-cert-reg-requests")
            .withDefaultValue(false)
            .build();
    /** {@code xroad.admin-service.management-requests.auto-approve-client-reg-requests}. */
    public static final ConfigKey<Boolean> MANAGEMENT_REQUESTS_AUTO_APPROVE_CLIENT_REG_REQUESTS = MANAGEMENT_REQUESTS
            .bool("auto-approve-client-reg-requests")
            .withDefaultValue(false)
            .build();
    /** {@code xroad.admin-service.management-requests.auto-approve-owner-change-requests}. */
    public static final ConfigKey<Boolean> MANAGEMENT_REQUESTS_AUTO_APPROVE_OWNER_CHANGE_REQUESTS = MANAGEMENT_REQUESTS
            .bool("auto-approve-owner-change-requests")
            .withDefaultValue(false)
            .build();

    private CsAdminServiceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static CsAdminServiceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return ADMIN.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return ADMIN.keys();
    }
}
