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
package org.niis.xroad.cs.admin.core.config;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import org.niis.xroad.common.api.throttle.IpThrottlingFilterConfig;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CsAdminServiceConfigKeys;
import org.niis.xroad.restapi.auth.AllowListConfig;
import org.niis.xroad.restapi.config.AllowedFilesConfig;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.config.IdentifierValidationConfiguration;
import org.niis.xroad.restapi.config.LimitRequestSizesFilter;
import org.niis.xroad.restapi.config.UserAuthenticationConfig;
import org.niis.xroad.restapi.config.UserRoleConfig;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.restapi.domain.Role.XROAD_REGISTRATION_OFFICER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SECURITY_OFFICER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SYSTEM_ADMINISTRATOR;

/**
 * Admin service configuration properties, resolved through {@link XRoadConfig}
 * (DB overrides + packaged DSL defaults) via {@link CsAdminServiceConfigKeys}.
 */
public class AdminServiceProperties implements IpThrottlingFilterConfig,
        AllowedHostnamesConfig,
        ApiCachingConfiguration.Config,
        LimitRequestSizesFilter.Config,
        IdentifierValidationConfiguration.Config,
        AllowedFilesConfig,
        UserRoleConfig,
        KeyAlgorithmConfig,
        UserAuthenticationConfig,
        FederationConfig,
        BackupConfig,
        AllowListConfig {

    private final XRoadConfig config;

    public AdminServiceProperties(XRoadConfig config) {
        this.config = config;
    }

    public int getGlobalConfigurationGenerationRateInSeconds() {
        return config.value(CsAdminServiceConfigKeys.GLOBAL_CONFIGURATION_GENERATION_RATE_IN_SECONDS);
    }

    @Override
    public boolean isRateLimitEnabled() {
        return config.value(CsAdminServiceConfigKeys.RATE_LIMIT_ENABLED);
    }

    @Override
    public int getRateLimitRequestsPerSecond() {
        return config.value(CsAdminServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_SECOND);
    }

    @Override
    public int getRateLimitRequestsPerMinute() {
        return config.value(CsAdminServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_MINUTE);
    }

    @Override
    public int getRateLimitCacheSize() {
        return config.value(CsAdminServiceConfigKeys.RATE_LIMIT_CACHE_SIZE);
    }

    @Override
    public int getRateLimitExpireAfterAccessMinutes() {
        return config.value(CsAdminServiceConfigKeys.RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES);
    }

    @Override
    public List<String> getAllowedHostnames() {
        return optionalList(config.value(CsAdminServiceConfigKeys.ALLOWED_HOSTNAMES));
    }

    @Override
    public int getCacheDefaultTtl() {
        return config.value(CsAdminServiceConfigKeys.CACHE_DEFAULT_TTL);
    }

    @Override
    public int getCacheApiKeyTtl() {
        return config.value(CsAdminServiceConfigKeys.CACHE_API_KEY_TTL);
    }

    @Override
    public Set<String> getBackupAllowedContentTypes() {
        return asSet(config.value(CsAdminServiceConfigKeys.BACKUP_ALLOWED_CONTENT_TYPES));
    }

    @Override
    public Set<String> getXmlAllowedExtensions() {
        return asSet(config.value(CsAdminServiceConfigKeys.XML_ALLOWED_EXTENSIONS));
    }

    @Override
    public Set<String> getXmlAllowedContentTypes() {
        return asSet(config.value(CsAdminServiceConfigKeys.XML_ALLOWED_CONTENT_TYPES));
    }

    @Override
    public Set<String> getCertificateAllowedExtensions() {
        return asSet(config.value(CsAdminServiceConfigKeys.CERTIFICATE_ALLOWED_EXTENSIONS));
    }

    @Override
    public Set<String> getCertificateAllowedContentTypes() {
        return asSet(config.value(CsAdminServiceConfigKeys.CERTIFICATE_ALLOWED_CONTENT_TYPES));
    }

    @Override
    public boolean isStrictIdentifierChecks() {
        return config.value(CsAdminServiceConfigKeys.STRICT_IDENTIFIER_CHECKS);
    }

    @Override
    public DataSize getRequestSizeLimitRegular() {
        return DataSize.parse(config.value(CsAdminServiceConfigKeys.REQUEST_SIZE_LIMIT_REGULAR));
    }

    @Override
    public DataSize getRequestSizeLimitBinaryUpload() {
        return DataSize.parse(config.value(CsAdminServiceConfigKeys.REQUEST_SIZE_LIMIT_BINARY_UPLOAD));
    }

    @Override
    public KeyAlgorithm getExternalKeyAlgorithm() {
        return keyAlgorithm(config.value(CsAdminServiceConfigKeys.EXTERNAL_KEY_ALGORITHM));
    }

    @Override
    public KeyAlgorithm getInternalKeyAlgorithm() {
        return keyAlgorithm(config.value(CsAdminServiceConfigKeys.INTERNAL_KEY_ALGORITHM));
    }

    @Override
    public AuthenticationProviderType getAuthenticationProvider() {
        return AuthenticationProviderType.valueOf(config.value(CsAdminServiceConfigKeys.AUTHENTICATION_PROVIDER));
    }

    @Override
    public boolean isEnforceUserPasswordPolicy() {
        return config.value(CsAdminServiceConfigKeys.ENFORCE_USER_PASSWORD_POLICY);
    }

    @Override
    public boolean isTrustedAnchorsAllowed() {
        return config.value(CsAdminServiceConfigKeys.TRUSTED_ANCHORS_ALLOWED);
    }

    @Override
    public String getConfBackupPath() {
        return config.value(CsAdminServiceConfigKeys.CONF_BACKUP_PATH);
    }

    @Override
    public String getKeyManagementApiWhitelist() {
        return config.value(CsAdminServiceConfigKeys.KEY_MANAGEMENT_API_WHITELIST);
    }

    @Override
    public String getRegularApiWhitelist() {
        return config.value(CsAdminServiceConfigKeys.REGULAR_API_WHITELIST);
    }

    /**
     * Path to the file containing the current backup format version, used to determine backup compatibility.
     * '/usr/share/xroad/scripts/_backup_format_version' by default.
     */
    private String backupFormatVersionFilePath;

    /**
     * Path to the script that parses an uploaded backup's tar label and writes its ".metadata" file.
     * '/usr/share/xroad/scripts/_create_backup_metadata.sh' by default.
     */
    private String createBackupMetadataPath;

    @Override
    public EnumMap<Role, List<String>> getUserRoleMappings() {
        EnumMap<Role, List<String>> userRoleMappings = new EnumMap<>(Role.class);
        userRoleMappings.put(XROAD_SECURITY_OFFICER, List.of("xroad-security-officer"));
        userRoleMappings.put(XROAD_REGISTRATION_OFFICER, List.of("xroad-registration-officer"));
        userRoleMappings.put(XROAD_SYSTEM_ADMINISTRATOR, List.of("xroad-system-administrator"));

        complementaryMappings(config).forEach((role, groups) -> userRoleMappings.merge(role, groups,
                (a, b) -> Stream.concat(a.stream(), b.stream()).collect(toList())));

        return userRoleMappings;
    }

    private static EnumMap<Role, List<String>> complementaryMappings(XRoadConfig config) {
        EnumMap<Role, List<String>> mappings = new EnumMap<>(Role.class);
        putIfNotEmpty(mappings, XROAD_SECURITY_OFFICER, config.value(CsAdminServiceConfigKeys.COMPLEMENTARY_ROLE_SECURITY_OFFICER));
        putIfNotEmpty(mappings, XROAD_REGISTRATION_OFFICER,
                config.value(CsAdminServiceConfigKeys.COMPLEMENTARY_ROLE_REGISTRATION_OFFICER));
        putIfNotEmpty(mappings, Role.XROAD_SERVICE_ADMINISTRATOR,
                config.value(CsAdminServiceConfigKeys.COMPLEMENTARY_ROLE_SERVICE_ADMINISTRATOR));
        putIfNotEmpty(mappings, XROAD_SYSTEM_ADMINISTRATOR,
                config.value(CsAdminServiceConfigKeys.COMPLEMENTARY_ROLE_SYSTEM_ADMINISTRATOR));
        putIfNotEmpty(mappings, Role.XROAD_SECURITYSERVER_OBSERVER,
                config.value(CsAdminServiceConfigKeys.COMPLEMENTARY_ROLE_SECURITYSERVER_OBSERVER));
        putIfNotEmpty(mappings, Role.XROAD_MANAGEMENT_SERVICE,
                config.value(CsAdminServiceConfigKeys.COMPLEMENTARY_ROLE_MANAGEMENT_SERVICE));
        return mappings;
    }

    private static void putIfNotEmpty(EnumMap<Role, List<String>> mappings, Role role, String[] groups) {
        List<String> values = Arrays.stream(groups).filter(group -> !group.isBlank()).toList();
        if (!values.isEmpty()) {
            mappings.put(role, values);
        }
    }

    private static KeyAlgorithm keyAlgorithm(String value) {
        return value == null ? null : KeyAlgorithm.valueOf(value);
    }

    private static List<String> optionalList(String[] values) {
        return values == null ? null : Arrays.stream(values).filter(value -> !value.isBlank()).toList();
    }

    private static Set<String> asSet(String[] values) {
        return values == null ? null : Arrays.stream(values).filter(value -> !value.isBlank()).collect(toSet());
    }
}
