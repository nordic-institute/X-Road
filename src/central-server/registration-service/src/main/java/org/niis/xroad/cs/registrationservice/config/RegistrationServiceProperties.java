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
package org.niis.xroad.cs.registrationservice.config;

import lombok.Getter;
import lombok.Setter;
import org.niis.xroad.common.api.throttle.IpThrottlingFilterConfig;
import org.niis.xroad.cs.admin.client.configuration.AdminServiceClientPropertyProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.nio.file.Path;

/**
 * Registration service configuration properties.
 * <p>
 * Can be defined in local.ini, e.g.:
 * <pre>
 * [registration-service]
 * rate-limit-enabled = true
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@ConfigurationProperties(prefix = "xroad.registration-service")
@Getter
@Setter
public class RegistrationServiceProperties implements AdminServiceClientPropertyProvider, IpThrottlingFilterConfig {

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
     * Controls how many requests from an IP address are allowed per minute.
     * Normally security servers should have a unique address and send second
     * one registration request, so this value can be low.
     * To disable this feature, set this value to -1.
     */
    private int rateLimitRequestsPerSecond;

    /**
     * Controls how many requests from an IP address are allowed per minute.
     * Normally security servers should have a unique address and send just
     * one registration request, so this value can be low.
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
     * Path to a trust store containing certificates for the central server admin API
     */
    private Path apiTrustStore;

    /**
     * Password for the trust store
     */
    private String apiTrustStorePassword;

    /**
     * Central server admin api base URL
     */
    private URI apiBaseUrl;

    /**
     * API token for the central server API (required)
     * The token needs to have the MANAGEMENT_SERVICE role (and for security, no other roles).
     */
    private String apiToken;

    /**
     * HTTP client configuration.
     */
    private HttpClientProperties httpClientProperties = new HttpClientProperties();
}
