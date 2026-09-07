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
package org.niis.xroad.cs.management.core.configuration;

import org.niis.xroad.common.api.throttle.IpThrottlingFilterConfig;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.keys.CsManagementServiceConfigKeys;
import org.niis.xroad.cs.admin.client.configuration.AdminServiceClientPropertyProvider;

import java.net.URI;

/**
 * Management service configuration properties ({@code xroad.management-service.*}),
 * resolved through {@link XRoadConfig}.
 */
public class ManagementServiceProperties implements AdminServiceClientPropertyProvider, IpThrottlingFilterConfig {

    private final XRoadConfig config;

    public ManagementServiceProperties(XRoadConfig config) {
        this.config = config;
    }

    @Override
    public boolean isRateLimitEnabled() {
        return config.value(CsManagementServiceConfigKeys.RATE_LIMIT_ENABLED);
    }

    @Override
    public int getRateLimitRequestsPerSecond() {
        return config.value(CsManagementServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_SECOND);
    }

    @Override
    public int getRateLimitRequestsPerMinute() {
        return config.value(CsManagementServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_MINUTE);
    }

    @Override
    public int getRateLimitCacheSize() {
        return config.value(CsManagementServiceConfigKeys.RATE_LIMIT_CACHE_SIZE);
    }

    @Override
    public int getRateLimitExpireAfterAccessMinutes() {
        return config.value(CsManagementServiceConfigKeys.RATE_LIMIT_EXPIRE_AFTER_ACCESS_MINUTES);
    }

    @Override
    public URI getApiBaseUrl() {
        return URI.create(config.value(CsManagementServiceConfigKeys.API_BASE_URL));
    }

    @Override
    public String getApiToken() {
        return config.value(CsManagementServiceConfigKeys.API_TOKEN);
    }

    @Override
    public HttpClientProperties getHttpClientProperties() {
        HttpClientProperties properties = new HttpClientProperties();
        properties.setMaxConnectionsPerRoute(config.value(CsManagementServiceConfigKeys.HTTP_CLIENT_MAX_CONNECTIONS_PER_ROUTE));
        properties.setMaxConnectionsTotal(config.value(CsManagementServiceConfigKeys.HTTP_CLIENT_MAX_CONNECTIONS_TOTAL));
        properties.setConnectionTimeoutSeconds(config.value(CsManagementServiceConfigKeys.HTTP_CLIENT_CONNECTION_TIMEOUT_SECONDS));
        properties.setConnectionRequestTimeoutSeconds(
                config.value(CsManagementServiceConfigKeys.HTTP_CLIENT_CONNECTION_REQUEST_TIMEOUT_SECONDS));
        properties.setResponseTimeoutSeconds(config.value(CsManagementServiceConfigKeys.HTTP_CLIENT_RESPONSE_TIMEOUT_SECONDS));
        return properties;
    }
}
