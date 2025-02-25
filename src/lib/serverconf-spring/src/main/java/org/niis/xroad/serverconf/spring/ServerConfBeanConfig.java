/*
 * The MIT License
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
package org.niis.xroad.serverconf.spring;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.db.DatabaseCtx;

import lombok.Setter;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfDbProperties;
import org.niis.xroad.serverconf.ServerConfProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseConfig;
import org.niis.xroad.serverconf.impl.ServerConfFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.niis.xroad.serverconf.impl.ServerConfDatabaseConfig.SERVER_CONF_DB_CTX;

@Configuration
@EnableConfigurationProperties({
        ServerConfBeanConfig.SpringServerConfProperties.class,
        ServerConfBeanConfig.SpringServerConfDbProperties.class})
public class ServerConfBeanConfig {

    @Bean(name = SERVER_CONF_DB_CTX, destroyMethod = "destroy")
    DatabaseCtx serverConfCtx(ServerConfDbProperties dbProperties) {
        return ServerConfDatabaseConfig.createServerConfDbCtx(dbProperties);
    }

    @Bean
    public ServerConfProvider serverConfProvider(@Qualifier(SERVER_CONF_DB_CTX) DatabaseCtx databaseCtx,
                                                 GlobalConfProvider globalConfProvider) {
        return ServerConfFactory.create(databaseCtx, globalConfProvider, SystemProperties.getServerConfCachePeriod());
    }


    @Setter
    @ConfigurationProperties(prefix = "xroad.server-conf")
    public static class SpringServerConfProperties implements ServerConfProperties {
        private int cachePeriod = Integer.parseInt(DEFAULT_CACHE_PERIOD);
        private long clientCacheSize = Long.parseLong(DEFAULT_CLIENT_CACHE_SIZE);
        private long serviceCacheSize = Long.parseLong(DEFAULT_SERVICE_CACHE_SIZE);
        private long serviceEndpointsCacheSize = Long.parseLong(DEFAULT_SERVICE_ENDPOINTS_CACHE_SIZE);
        private long aclCacheSize = Long.parseLong(DEFAULT_ACL_CACHE_SIZE);
        private Map<String, String> hibernate = Map.of();

        @Override
        public int cachePeriod() {
            return cachePeriod;
        }

        @Override
        public long clientCacheSize() {
            return clientCacheSize;
        }

        @Override
        public long serviceCacheSize() {
            return serviceCacheSize;
        }

        @Override
        public long serviceEndpointsCacheSize() {
            return serviceEndpointsCacheSize;
        }

        @Override
        public long aclCacheSize() {
            return aclCacheSize;
        }

    }

    @Setter
    @ConfigurationProperties(prefix = "xroad.db.serverconf")
    public static class SpringServerConfDbProperties implements ServerConfDbProperties {
        private Map<String, String> hibernate = Map.of();

        @Override
        public Map<String, String> hibernate() {
            return hibernate;
        }
    }
}
