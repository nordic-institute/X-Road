/**
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
package org.niis.xroad.cs.admin.core.config;

import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.api.throttle.IpThrottlingFilterConfig;
import org.niis.xroad.restapi.config.AllowedFilesConfig;
import org.niis.xroad.restapi.config.AllowedHostnamesConfig;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.config.IdentifierValidationConfiguration;
import org.niis.xroad.restapi.config.LimitRequestSizesFilter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Set;

/**
 * Admin service configuration properties.
 * <p>
 * Can be defined in local.ini, e.g.:
 * <pre>
 * [admin-service]
 * rate-limit-enabled = true
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "xroad.admin-service")
@Getter
@Setter
public class AdminServiceProperties implements IpThrottlingFilterConfig,
        AllowedHostnamesConfig,
        ApiCachingConfiguration.Config,
        LimitRequestSizesFilter.Config,
        IdentifierValidationConfiguration.Config,
        AllowedFilesConfig {

    /**
     * Controls the rate of global configuration generation in seconds.
     */
    private int globalConfigurationGenerationRateInSeconds;

    /**
     * Controls whether the built-in rate limiting is enabled.
     * <p>
     * Note. If the service is behind a reverse proxy (default), the proxy needs to forward the real IP address for the
     * rate-limiting to work correctly. Therefore, by default, using forward headers is enabled.
     * <p>
     * If the service is exposed directly, it must not use forwarded headers (can be spoofed by clients), and the
     * corresponding configuration (server.forward-headers-strategy) needs to be disabled.
     */
    private boolean rateLimitEnabled;

    /**
     * Controls how many requests from an IP address are allowed per second.
     * Normally security servers should have a unique address and send just
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

    /** Determines which file content types are allowed for backup file. Any content type is allowed when left unspecified. */
    private Set<String> backupAllowedContentTypes;

    /** Determines which file extensions are allowed for XML file. Any extension is allowed when left unspecified. */
    private Set<String> xmlAllowedExtensions;
    /** Determines which file content types are allowed for XML files. Any content type is allowed when left unspecified. */
    private Set<String> xmlAllowedContentTypes;

    /** Determines which file extensions are allowed for certificate file. Any extension is allowed when left unspecified. */
    private Set<String> certificateAllowedExtensions;
    /** Determines which file content types are allowed for certificate files. Any content type is allowed when left unspecified. */
    private Set<String> certificateAllowedContentTypes;

    /**
     * Restrict identifiers (member code, subsystem code etc.) to match <code>^[a-zA-Z0-9'()+,-.=?]*</code>.
     * Setting value to false enables legacy compatibility mode, that logs a warning when entity is created with
     * incompatible identifier.
     */
    private boolean strictIdentifierChecks;
    /** Configures Api regular request size limit.  */
    private DataSize requestSizeLimitRegular;

    /** Configures Api file upload request size limit. */
    private DataSize requestSizeLimitBinaryUpload;
}
