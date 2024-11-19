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
package org.niis.xroad.confclient.config;

import ee.ria.xroad.common.conf.globalconf.ConfigurationClient;
import ee.ria.xroad.common.conf.globalconf.ConfigurationClientValidateActionExecutor;
import ee.ria.xroad.common.conf.globalconf.FSGlobalConfValidator;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcConfig;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.niis.xroad.common.rpc.server.RpcServerConfig;
import org.niis.xroad.confclient.admin.AdminService;
import org.niis.xroad.confclient.globalconf.AnchorService;
import org.niis.xroad.confclient.globalconf.GetGlobalConfRespFactory;
import org.niis.xroad.confclient.globalconf.GlobalConfRpcCache;
import org.niis.xroad.confclient.globalconf.GlobalConfRpcService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Slf4j
@Configuration
@Import({RpcConfig.class, RpcServerConfig.class})
@EnableConfigurationProperties({
        ConfClientRpcConfig.ConfClientRpcServerProperties.class})
@ConditionalOnProperty(name = "xroad.configuration-client.cli-mode", havingValue = "false")
public class ConfClientRpcConfig {

    @Bean
    AdminService adminService(ConfClientJobConfig.ConfigurationClientJobListener listener) {
        return new AdminService(listener);
    }

    @Bean
    AnchorService anchorService(ConfigurationClientProperties confClientProperties,
                                ConfigurationClient configurationClient,
                                ConfigurationClientValidateActionExecutor validateExecutor, GlobalConfRpcCache globalConfRpcCache) {
        return new AnchorService(confClientProperties, configurationClient, validateExecutor, globalConfRpcCache);
    }

    @Bean
    GlobalConfRpcService globalConfRpcService(GlobalConfRpcCache globalConfRpcCache) {
        return new GlobalConfRpcService(globalConfRpcCache);
    }

    @Bean
    FSGlobalConfValidator fsGlobalConfValidator() {
        return new FSGlobalConfValidator();
    }

    @Bean
    GetGlobalConfRespFactory getGlobalConfRespFactory() {
        return new GetGlobalConfRespFactory();
    }

    @Bean
    GlobalConfRpcCache globalConfRpcCache(FSGlobalConfValidator fsGlobalConfValidator,
                                          GetGlobalConfRespFactory getGlobalConfRespFactory) {
        return new GlobalConfRpcCache(fsGlobalConfValidator, getGlobalConfRespFactory);
    }

    @ConfigurationProperties(prefix = "xroad.configuration-client.grpc")
    static class ConfClientRpcServerProperties extends RpcServerProperties {
        ConfClientRpcServerProperties(String listenAddress, int port) {
            super(listenAddress, port);
        }
    }
}
