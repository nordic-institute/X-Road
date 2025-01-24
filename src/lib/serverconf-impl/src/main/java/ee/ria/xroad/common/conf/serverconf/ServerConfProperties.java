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

package ee.ria.xroad.common.conf.serverconf;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;
import lombok.Data;

import java.util.Map;

@ConfigMapping(prefix = "xroad.common.serverconf")
//@ConfigurationProperties(prefix = "xroad.common.serverconf")
@Data
public class ServerConfProperties {
    @WithName("cache-period")
    private int cachePeriod;       //xroad.proxy.server-conf-cache-period: 60
    @WithName("client-cache-size")
    private long clientCacheSize;  //xroad.proxy.server-conf-client-cache-size: 100
    @WithName("service-cache-size")
    private long serviceCacheSize; //xroad.proxy.server-conf-service-cache-size: 1000
    @WithName("service-endpoints-cache-size")
    private long serviceEndpointsCacheSize; //xroad.proxy.server-conf-service-endpoints-cache-size: 100_000
    @WithName("acl-cache-size")
    private long aclCacheSize;     //xroad.proxy.server-conf-acl-cache-size: 100_000
    @WithName("hibernate")
    private Map<String, String> hibernate; // serverconf.hibernate.* properties from db-properties file
}
