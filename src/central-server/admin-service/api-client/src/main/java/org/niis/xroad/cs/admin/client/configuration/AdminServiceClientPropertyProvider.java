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
package org.niis.xroad.cs.admin.client.configuration;

import lombok.Getter;
import lombok.Setter;

import java.net.URI;
import java.nio.file.Path;

/**
 * Admin service API client property provider.
 */
public interface AdminServiceClientPropertyProvider {

    /**
     * Path to a trust store containing certificates for the central server admin API
     */
    Path getApiTrustStore();

    /**
     * Password for the trust store
     */
    String getApiTrustStorePassword();

    /**
     * API token for the central server API (required)
     * The token needs to have the MANAGEMENT_SERVICE role (and for security, no other roles).
     */
    String getApiToken();

    /**
     * Central server admin api base URL
     */
    URI getApiBaseUrl();

    /**
     * Get prefixed api token header value.
     *
     * @return header value
     */
    default String getHeaderValue() {
        return "X-ROAD-APIKEY TOKEN=" + getApiToken();
    }

    /**
     * Get user agent for client operations.
     *
     * @return user agent value
     */
    default String getUserAgent() {
        return "X-Road Api-Client/7";
    }

    /**
     * Get HTTP client properties.
     *
     * @return http client properties
     */
    HttpClientProperties getHttpClientProperties();

    @Getter
    @Setter
    @SuppressWarnings("checkstyle:MagicNumber")
    class HttpClientProperties {
        private Integer maxConnectionsPerRoute = 50;
        private Integer maxConnectionsTotal = 50;

        private Integer connectionTimeoutSeconds = 5;
        private Integer connectionRequestTimeoutSeconds = 10;
        private Integer responseTimeoutSeconds = 5;
    }
}
