/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.config;

import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.api.throttle.IpThrottlingFilterConfig;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.config.IdentifierValidationConfiguration;
import org.niis.xroad.restapi.config.LimitRequestSizesFilter;
import org.niis.xroad.restapi.config.UserRoleConfig;
import org.niis.xroad.restapi.domain.Role;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.util.EnumMap;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.niis.xroad.restapi.domain.Role.XROAD_REGISTRATION_OFFICER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SECURITYSERVER_OBSERVER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SECURITY_OFFICER;
import static org.niis.xroad.restapi.domain.Role.XROAD_SERVICE_ADMINISTRATOR;
import static org.niis.xroad.restapi.domain.Role.XROAD_SYSTEM_ADMINISTRATOR;

/**
 * Admin service configuration properties.
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "xroad.proxy-ui-api")
@Getter
@Setter
@SuppressWarnings("checkstyle:MagicNumber")
public class AdminServiceProperties implements IpThrottlingFilterConfig,
        AllowedHostnamesConfig,
        ApiCachingConfiguration.Config,
        LimitRequestSizesFilter.Config,
        IdentifierValidationConfiguration.Config,
        UserRoleConfig {

    /**
     * Controls how many requests from an IP address are allowed per minute.
     * Normally security servers should have a unique address and send second
     * one management request, so this value can be low.
     * To disable this feature, set this value to -1.
     */
    private int rateLimitRequestsPerSecond;

    /**
     * Controls how many requests from an IP address are allowed per minute.
     * Normally security servers should have a unique address and send just
     * one management request, so this value can be low.
     * To disable this feature, set this value to -1.
     */
    private int rateLimitRequestsPerMinute;

    /**
     * Controls how many IP addresses can be remembered in the rate-limit cache
     * Tradeoff between memory usage and protection from a large attack.
     */
    private int rateLimitCacheSize;

    /**
     * Controls how long the rate-limit cache entries are valid.
     */
    private int rateLimitExpireAfterAccessMinutes;

    /**
     * Determines which hostnames are allowed. Any hostname is allowed when left unspecified.
     */
    private List<String> allowedHostnames;

    /**
     * Configures default cache expiration in seconds. Can be used by various api services.
     * Setting the value to -1 disables the cache.
     */
    private int cacheDefaultTtl;

    /**
     * Configures Api key cache expiration in seconds. Cache is hit during authentication requests.
     * Setting the value to -1 disables the cache.
     */
    private int cacheApiKeyTtl;

    /**
     * Restrict identifiers (member code, subsystem code etc.) to match <code>^[a-zA-Z0-9'()+,-.=?]*</code>.
     * Setting value to false enables legacy compatibility mode, that logs a warning when entity is created with
     * incompatible identifier.
     */
    private boolean strictIdentifierChecks;
    /** Configures Api regular request size limit. */
    private DataSize requestSizeLimitRegular;

    /** Configures Api file upload request size limit. */
    private DataSize requestSizeLimitBinaryUpload;

    /**
     * Configures additional UNIX groups mapped to X-Road user roles.
     */
    private EnumMap<Role, List<String>> complementaryUserRoleMappings;

    @Override
    public EnumMap<Role, List<String>> getUserRoleMappings() {
        EnumMap<Role, List<String>> userRoleMappings = new EnumMap<>(Role.class);
        userRoleMappings.put(XROAD_SECURITY_OFFICER, List.of("xroad-security-officer"));
        userRoleMappings.put(XROAD_REGISTRATION_OFFICER, List.of("xroad-registration-officer"));
        userRoleMappings.put(XROAD_SERVICE_ADMINISTRATOR, List.of("xroad-service-administrator"));
        userRoleMappings.put(XROAD_SYSTEM_ADMINISTRATOR, List.of("xroad-system-administrator"));
        userRoleMappings.put(XROAD_SECURITYSERVER_OBSERVER, List.of("xroad-securityserver-observer"));

        if (complementaryUserRoleMappings != null) {
            complementaryUserRoleMappings.forEach((role, groups) -> userRoleMappings.merge(role, groups,
                    (a, b) -> Stream.concat(a.stream(), b.stream()).collect(toList())));
        }

        return userRoleMappings;
    }
}

