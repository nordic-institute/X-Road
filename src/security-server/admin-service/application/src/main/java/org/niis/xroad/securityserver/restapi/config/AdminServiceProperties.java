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
package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import lombok.Getter;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.api.throttle.IpThrottlingFilterConfig;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.restapi.auth.AllowListConfig;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.config.IdentifierValidationConfiguration;
import org.niis.xroad.restapi.config.LimitRequestSizesFilter;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserRoleConfig;
import org.niis.xroad.restapi.domain.Role;
import org.niis.xroad.securityserver.restapi.mail.NotificationConfig;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Stream;

import static org.niis.xroad.restapi.domain.Role.XROAD_REGISTRATION_OFFICER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SECURITYSERVER_OBSERVER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SECURITY_OFFICER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SERVICE_ADMINISTRATOR;
import static org.niis.xroad.restapi.domain.Role.XROAD_SYSTEM_ADMINISTRATOR;

/**
 * Admin service configuration properties, resolved through {@link XRoadConfig}
 * (DB overrides + packaged DSL defaults) via {@link AdminServiceConfigKeys}.
 */
public class AdminServiceProperties implements IpThrottlingFilterConfig,
        AllowedHostnamesConfig,
        ApiCachingConfiguration.Config,
        LimitRequestSizesFilter.Config,
        IdentifierValidationConfiguration.Config,
        UserRoleConfig,
        KeyAlgorithmConfig,
        NotificationConfig,
        AcmeConfig,
        AllowListConfig,
        UserAuthenticationConfig {

    private final XRoadConfig config;

    @Getter
    private Dataspace dataspace = new Dataspace();

    public AdminServiceProperties(XRoadConfig config) {
        this.config = config;
    }

    @Override
    public boolean isRateLimitEnabled() {
        return config.value(AdminServiceConfigKeys.RATE_LIMIT_ENABLED);
    }

    @Override
    public int getRateLimitRequestsPerSecond() {
        return config.value(AdminServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_SECOND);
    }

    @Override
    public int getRateLimitRequestsPerMinute() {
        return config.value(AdminServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_MINUTE);
    }

    @Override
    public int getRateLimitCacheSize() {
        return config.value(AdminServiceConfigKeys.RATE_LIMIT_CACHE_SIZE);
    }

    @Override
    public int getRateLimitExpireAfterAccessMinutes() {
        return config.value(AdminServiceConfigKeys.RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES);
    }

    @Override
    public List<String> getAllowedHostnames() {
        return optionalList(config.value(AdminServiceConfigKeys.ALLOWED_HOSTNAMES));
    }

    public List<String> getReservedServiceCodes() {
        return optionalList(config.value(AdminServiceConfigKeys.RESERVED_SERVICE_CODES));
    }

    @Override
    public int getCacheDefaultTtl() {
        return config.value(AdminServiceConfigKeys.CACHE_DEFAULT_TTL);
    }

    @Override
    public int getCacheApiKeyTtl() {
        return config.value(AdminServiceConfigKeys.CACHE_API_KEY_TTL);
    }

    @Override
    public boolean isStrictIdentifierChecks() {
        return config.value(AdminServiceConfigKeys.STRICT_IDENTIFIER_CHECKS);
    }

    @Override
    public DataSize getRequestSizeLimitRegular() {
        return DataSize.parse(config.value(AdminServiceConfigKeys.REQUEST_SIZE_LIMIT_REGULAR));
    }

    @Override
    public DataSize getRequestSizeLimitBinaryUpload() {
        return DataSize.parse(config.value(AdminServiceConfigKeys.REQUEST_SIZE_LIMIT_BINARY_UPLOAD));
    }

    @Override
    public KeyAlgorithm getAuthenticationKeyAlgorithm() {
        return KeyAlgorithm.valueOf(config.value(AdminServiceConfigKeys.AUTHENTICATION_KEY_ALGORITHM));
    }

    @Override
    public KeyAlgorithm getSigningKeyAlgorithm() {
        return KeyAlgorithm.valueOf(config.value(AdminServiceConfigKeys.SIGNING_KEY_ALGORITHM));
    }

    @Override
    public AuthenticationProviderType getAuthenticationProvider() {
        return AuthenticationProviderType.valueOf(config.value(AdminServiceConfigKeys.AUTHENTICATION_PROVIDER));
    }

    @Override
    public boolean isEnforceUserPasswordPolicy() {
        return config.value(AdminServiceConfigKeys.ENFORCE_USER_PASSWORD_POLICY);
    }

    public boolean isAutoUpdateTimestampServiceUrl() {
        return config.value(AdminServiceConfigKeys.AUTO_UPDATE_TIMESTAMP_SERVICE_URL);
    }

    public boolean isAutomaticActivateAuthCertificate() {
        return config.value(AdminServiceConfigKeys.AUTOMATIC_ACTIVATE_AUTH_CERTIFICATE);
    }

    @Override
    public boolean isAcmeRenewalSuccessNotificationEnabled() {
        return config.value(AdminServiceConfigKeys.ACME_RENEWAL_SUCCESS_NOTIFICATION_ENABLED);
    }

    @Override
    public boolean isAcmeRenewalFailureNotificationEnabled() {
        return config.value(AdminServiceConfigKeys.ACME_RENEWAL_FAILURE_NOTIFICATION_ENABLED);
    }

    @Override
    public boolean isAuthCertRegisteredNotificationEnabled() {
        return config.value(AdminServiceConfigKeys.AUTH_CERT_REGISTERED_NOTIFICATION_ENABLED);
    }

    @Override
    public boolean isCertAutoActivationNotificationEnabled() {
        return config.value(AdminServiceConfigKeys.CERT_AUTO_ACTIVATION_NOTIFICATION_ENABLED);
    }

    @Override
    public boolean isCertAutoActivationFailureNotificationEnabled() {
        return config.value(AdminServiceConfigKeys.CERT_AUTO_ACTIVATION_FAILURE_NOTIFICATION_ENABLED);
    }

    @Override
    public String getMailNotificationLocale() {
        return config.value(AdminServiceConfigKeys.MAIL_NOTIFICATION_LOCALE);
    }

    @Override
    public boolean isAcmeRenewalActive() {
        return config.value(AdminServiceConfigKeys.ACME_RENEWAL_ACTIVE);
    }

    @Override
    public int getAcmeRenewalRetryDelay() {
        return config.value(AdminServiceConfigKeys.ACME_RENEWAL_RETRY_DELAY);
    }

    @Override
    public int getAcmeRenewalInterval() {
        return config.value(AdminServiceConfigKeys.ACME_RENEWAL_INTERVAL);
    }

    @Override
    public int getAcmeRenewalTimeBeforeExpirationDate() {
        return config.value(AdminServiceConfigKeys.ACME_RENEWAL_TIME_BEFORE_EXPIRATION_DATE);
    }

    @Override
    public int getAcmeKeypairRenewalTimeBeforeExpirationDate() {
        return config.value(AdminServiceConfigKeys.ACME_KEYPAIR_RENEWAL_TIME_BEFORE_EXPIRATION_DATE);
    }

    @Override
    public boolean isAutomaticActivateAcmeSignCertificate() {
        return config.value(AdminServiceConfigKeys.AUTOMATIC_ACTIVATE_ACME_SIGN_CERTIFICATE);
    }

    @Override
    public int getAcmeAuthorizationWaitAttempts() {
        return config.value(AdminServiceConfigKeys.ACME_AUTHORIZATION_WAIT_ATTEMPTS);
    }

    @Override
    public int getAcmeAuthorizationWaitInterval() {
        return config.value(AdminServiceConfigKeys.ACME_AUTHORIZATION_WAIT_INTERVAL);
    }

    @Override
    public int getAcmeCertificateWaitAttempts() {
        return config.value(AdminServiceConfigKeys.ACME_CERTIFICATE_WAIT_ATTEMPTS);
    }

    @Override
    public int getAcmeCertificateWaitInterval() {
        return config.value(AdminServiceConfigKeys.ACME_CERTIFICATE_WAIT_INTERVAL);
    }

    @Override
    public int getAcmeCertificateAccountKeyPairExpiration() {
        return config.value(AdminServiceConfigKeys.ACME_CERTIFICATE_ACCOUNT_KEY_PAIR_EXPIRATION);
    }

    @Override
    public boolean isAcmeChallengePortEnabled() {
        return config.value(AdminServiceConfigKeys.ACME_CHALLENGE_PORT_ENABLED);
    }

    @Override
    public int getAcmeChallengePort() {
        return config.value(AdminServiceConfigKeys.ACME_CHALLENGE_PORT);
    }

    @Override
    public int getAcmeKeyLength() {
        return config.value(AdminServiceConfigKeys.ACME_KEY_LENGTH);
    }

    @Override
    public String getAcmeChallengePath() {
        return config.value(AdminServiceConfigKeys.ACME_CHALLENGE_PATH);
    }

    public boolean isAllowCsrForKeyWithCertificate() {
        return config.value(AdminServiceConfigKeys.ALLOW_CSR_FOR_KEY_WITH_CERTIFICATE);
    }

    public String getAuthCertRegSignatureDigestAlgorithmId() {
        return config.value(AdminServiceConfigKeys.AUTH_CERT_REG_SIGNATURE_DIGEST_ALGORITHM_ID);
    }

    public String getProxyServerUrl() {
        return config.value(AdminServiceConfigKeys.PROXY_SERVER_URL);
    }

    public int getProxyServerConnectTimeout() {
        return config.value(AdminServiceConfigKeys.PROXY_SERVER_CONNECT_TIMEOUT);
    }

    public int getProxyServerSocketTimeout() {
        return config.value(AdminServiceConfigKeys.PROXY_SERVER_SOCKET_TIMEOUT);
    }

    public boolean isProxyServerEnableConnectionReuse() {
        return config.value(AdminServiceConfigKeys.PROXY_SERVER_ENABLE_CONNECTION_REUSE);
    }

    public String[] getProxyTlsProtocols() {
        return config.value(AdminServiceConfigKeys.PROXY_TLS_PROTOCOLS);
    }

    public String[] getProxyTlsCipherSuites() {
        return config.value(AdminServiceConfigKeys.PROXY_TLS_CIPHER_SUITES);
    }

    @Override
    public String getKeyManagementApiWhitelist() {
        return config.value(AdminServiceConfigKeys.KEY_MANAGEMENT_API_WHITELIST);
    }

    @Override
    public String getRegularApiWhitelist() {
        return config.value(AdminServiceConfigKeys.REGULAR_API_WHITELIST);
    }

    @Override
    public EnumMap<Role, List<String>> getUserRoleMappings() {
        EnumMap<Role, List<String>> userRoleMappings = new EnumMap<>(Role.class);
        userRoleMappings.put(XROAD_SECURITY_OFFICER, List.of("xroad-security-officer"));
        userRoleMappings.put(XROAD_REGISTRATION_OFFICER, List.of("xroad-registration-officer"));
        userRoleMappings.put(XROAD_SERVICE_ADMINISTRATOR, List.of("xroad-service-administrator"));
        userRoleMappings.put(XROAD_SYSTEM_ADMINISTRATOR, List.of("xroad-system-administrator"));
        userRoleMappings.put(XROAD_SECURITYSERVER_OBSERVER, List.of("xroad-securityserver-observer"));

        complementaryMappings(config).forEach((role, groups) -> userRoleMappings.merge(role, groups,
                (a, b) -> Stream.concat(a.stream(), b.stream()).toList()));

        return userRoleMappings;
    }

    private static EnumMap<Role, List<String>> complementaryMappings(XRoadConfig config) {
        EnumMap<Role, List<String>> mappings = new EnumMap<>(Role.class);
        putIfNotEmpty(mappings, XROAD_SECURITY_OFFICER, config.value(AdminServiceConfigKeys.COMPLEMENTARY_ROLE_SECURITY_OFFICER));
        putIfNotEmpty(mappings, XROAD_REGISTRATION_OFFICER, config.value(AdminServiceConfigKeys.COMPLEMENTARY_ROLE_REGISTRATION_OFFICER));
        putIfNotEmpty(mappings, XROAD_SERVICE_ADMINISTRATOR, config.value(AdminServiceConfigKeys.COMPLEMENTARY_ROLE_SERVICE_ADMINISTRATOR));
        putIfNotEmpty(mappings, XROAD_SYSTEM_ADMINISTRATOR, config.value(AdminServiceConfigKeys.COMPLEMENTARY_ROLE_SYSTEM_ADMINISTRATOR));
        putIfNotEmpty(mappings, XROAD_SECURITYSERVER_OBSERVER,
                config.value(AdminServiceConfigKeys.COMPLEMENTARY_ROLE_SECURITYSERVER_OBSERVER));
        putIfNotEmpty(mappings, Role.XROAD_MANAGEMENT_SERVICE, config.value(AdminServiceConfigKeys.COMPLEMENTARY_ROLE_MANAGEMENT_SERVICE));
        return mappings;
    }

    private static void putIfNotEmpty(EnumMap<Role, List<String>> mappings, Role role, String[] groups) {
        List<String> values = Arrays.stream(groups).filter(group -> !group.isBlank()).toList();
        if (!values.isEmpty()) {
            mappings.put(role, values);
        }
    }

    private static List<String> optionalList(String[] values) {
        return values == null ? null : Arrays.stream(values).filter(value -> !value.isBlank()).toList();
    }

    /**
     * Data space (EDC) membership credential provisioning configuration.
     */
    public class Dataspace {
        public boolean isEnabled() {
            return config.value(AdminServiceConfigKeys.DATASPACE_ENABLED);
        }

        public String getIdentityHubUrl() {
            return config.value(AdminServiceConfigKeys.DATASPACE_IDENTITY_HUB_URL);
        }

        public String getParticipantId() {
            return config.value(AdminServiceConfigKeys.DATASPACE_PARTICIPANT_ID);
        }

        public String getIssuerDid() {
            return config.value(AdminServiceConfigKeys.DATASPACE_ISSUER_DID);
        }

        public String getCredentialDefinitionId() {
            return config.value(AdminServiceConfigKeys.DATASPACE_CREDENTIAL_DEFINITION_ID);
        }

        public int getMaxHolderPidSlots() {
            return config.value(AdminServiceConfigKeys.DATASPACE_MAX_HOLDER_PID_SLOTS);
        }
    }

}
