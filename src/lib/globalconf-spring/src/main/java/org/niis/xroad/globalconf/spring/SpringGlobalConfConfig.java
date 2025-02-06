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
package org.niis.xroad.globalconf.spring;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.impl.GlobalConfRefreshJob;
import org.niis.xroad.globalconf.impl.config.GlobalConfConfig;
import org.niis.xroad.globalconf.impl.config.GlobalConfProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

import static org.niis.xroad.globalconf.impl.config.GlobalConfProperties.MAPPING_PREFIX;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SpringGlobalConfConfig.SpringCommonGlobalConfProperties.class)
public class SpringGlobalConfConfig extends GlobalConfConfig {

    @Bean
    @Override
    public GlobalConfProvider globalConfProvider(GlobalConfSource source) {
        return super.globalConfProvider(source);
    }

    @Bean
    public GlobalConfSource globalConfSource(Optional<ConfClientRpcClient> confClientRpcClient, GlobalConfProperties globalConfProperties) {
        return super.globalConfSource(confClientRpcClient::get, globalConfProperties);
    }

    @Bean(initMethod = "init")
    public GlobalConfRefreshJob globalConfRefreshJob(GlobalConfProperties config, GlobalConfProvider globalConfProvider) {
        return new GlobalConfRefreshJob(config, globalConfProvider);
    }

    @Setter
    @ConfigurationProperties(prefix = MAPPING_PREFIX)
    public static class SpringCommonGlobalConfProperties implements GlobalConfProperties {
        private GlobalConfSource source = GlobalConfSource.valueOf(DEFAULT_SOURCE);

        private int refreshRateSeconds = Integer.parseInt(DEFAULT_RATE_INTERVAL);

        @Override
        public GlobalConfSource source() {
            return source;
        }

        @Override
        public int refreshRateSeconds() {
            return refreshRateSeconds;
        }
    }

}
