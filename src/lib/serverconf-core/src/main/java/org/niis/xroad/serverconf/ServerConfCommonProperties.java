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

package org.niis.xroad.serverconf;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;


@ConfigMapping(prefix = ServerConfCommonProperties.PREFIX)
public interface ServerConfCommonProperties {
    String PREFIX = "xroad.common.server-conf";
    String DEFAULT_CACHE_PERIOD = "60";
    String DEFAULT_CLIENT_CACHE_SIZE = "100";
    String DEFAULT_SERVICE_CACHE_SIZE = "1000";
    String DEFAULT_SERVICE_ENDPOINTS_CACHE_SIZE = "100000";
    String DEFAULT_ACL_CACHE_SIZE = "100000";

    @WithName("cache-period")
    @WithDefault(DEFAULT_CACHE_PERIOD)
    int cachePeriod();

    @WithName("client-cache-size")
    @WithDefault(DEFAULT_CLIENT_CACHE_SIZE)
    long clientCacheSize();

    @WithName("service-cache-size")
    @WithDefault(DEFAULT_SERVICE_CACHE_SIZE)
    long serviceCacheSize();

    @WithName("service-endpoints-cache-size")
    @WithDefault(DEFAULT_SERVICE_ENDPOINTS_CACHE_SIZE)
    long serviceEndpointsCacheSize();

    @WithName("acl-cache-size")
    @WithDefault(DEFAULT_ACL_CACHE_SIZE)
    long aclCacheSize();

}
