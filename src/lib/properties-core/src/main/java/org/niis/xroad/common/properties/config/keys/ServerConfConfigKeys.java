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

import java.util.Set;

/**
 * Shared server configuration cache keys ({@code xroad.common-server-conf.*}), consumed by both Quarkus and
 * Spring products.
 */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class ServerConfConfigKeys implements ConfigKeyProvider {

    private static final Prefix SERVER_CONF = Prefix.of(Category.COMMON, "xroad.common-server-conf");

    private static final ServerConfConfigKeys INSTANCE = new ServerConfConfigKeys();

    /** {@code xroad.common-server-conf.cache-period}. */
    public static final ConfigKey<Integer> CACHE_PERIOD = SERVER_CONF
            .integer("cache-period")
            .withDefaultValue(60)
            .exposedInUi()
            .build();
    /** {@code xroad.common-server-conf.client-cache-size}. */
    public static final ConfigKey<Long> CLIENT_CACHE_SIZE = SERVER_CONF
            .longValue("client-cache-size")
            .withDefaultValue(100)
            .exposedInUi()
            .build();
    /** {@code xroad.common-server-conf.service-cache-size}. */
    public static final ConfigKey<Long> SERVICE_CACHE_SIZE = SERVER_CONF
            .longValue("service-cache-size")
            .withDefaultValue(1000)
            .exposedInUi()
            .build();
    /** {@code xroad.common-server-conf.service-endpoints-cache-size}. */
    public static final ConfigKey<Long> SERVICE_ENDPOINTS_CACHE_SIZE = SERVER_CONF
            .longValue("service-endpoints-cache-size")
            .withDefaultValue(100000)
            .exposedInUi()
            .build();
    /** {@code xroad.common-server-conf.acl-cache-size}. */
    public static final ConfigKey<Long> ACL_CACHE_SIZE = SERVER_CONF
            .longValue("acl-cache-size")
            .withDefaultValue(100000)
            .exposedInUi()
            .build();

    private ServerConfConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static ServerConfConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public String rootPath() {
        return SERVER_CONF.rootPath();
    }

    @Override
    public Set<ConfigKey<?>> keys() {
        return SERVER_CONF.keys();
    }
}
