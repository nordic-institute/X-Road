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

package ee.ria.xroad.proxy;

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.opmonitoring.AbstractOpMonitoringBuffer;
import ee.ria.xroad.proxy.clientproxy.AbstractClientProxyHandler;
import ee.ria.xroad.proxy.clientproxy.AsicContainerHandler;
import ee.ria.xroad.proxy.clientproxy.MetadataHandler;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.messagelog.LogManager;
import ee.ria.xroad.proxy.opmonitoring.OpMonitoringBuffer;
import ee.ria.xroad.proxy.serverproxy.MetadataServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.OpMonitoringServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.ProxyMonitorServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.RestMetadataServiceHandlerImpl;
import ee.ria.xroad.proxy.serverproxy.RestServiceHandler;
import ee.ria.xroad.proxy.serverproxy.ServiceHandler;

import org.apache.http.client.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;

@Configuration
class ProxyAddonsConfig {

    @Configuration
    @ConditionalOnProperty(name = "xroad.proxy.addon.metaservices.enabled", havingValue = "true")
    static class MetaServicesAddonConfig {
        @Bean
        @Order(100)
        AbstractClientProxyHandler metadataHandler(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                                   ServerConfProvider serverConfProvider, CertChainFactory certChainFactory,
                                                   @Qualifier("proxyHttpClient") HttpClient httpClient) {
            return new MetadataHandler(globalConfProvider, keyConfProvider,
                    serverConfProvider, certChainFactory, httpClient);
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        ServiceHandler metadataServiceHandler(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
            return new MetadataServiceHandlerImpl(serverConfProvider, globalConfProvider);
        }

        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        RestServiceHandler restMetadataServiceHandler(ServerConfProvider serverConfProvider) {
            return new RestMetadataServiceHandlerImpl(serverConfProvider);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "xroad.proxy.addon.messagelog.enabled", havingValue = "true")
    static class MessageLogAddonConfig {
        @Bean
        @Order(200)
        AbstractClientProxyHandler asicContainerHandler(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider,
                                                        ServerConfProvider serverConfProvider, CertChainFactory certChainFactory,
                                                        @Qualifier("proxyHttpClient") HttpClient client) {
            return new AsicContainerHandler(globalConfProvider, keyConfProvider,
                    serverConfProvider, certChainFactory, client);
        }

        @Bean
        @Primary
        AbstractLogManager logManager(GlobalConfProvider globalConfProvider,
                                      ServerConfProvider serverConfProvider,
                                      @Autowired(required = false) @Qualifier("messagelogDatabaseCtx")
                                      DatabaseCtxV2 messagelogDatabaseCtx) {
            return new LogManager("proxy", globalConfProvider, serverConfProvider, messagelogDatabaseCtx);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "xroad.proxy.addon.proxymonitor.enabled", havingValue = "true")
    static class ProxyMonitorAddonConfig {
        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        ServiceHandler proxyMonitorServiceHandler(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
            return new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = "xroad.proxy.addon.op-monitor.enabled", havingValue = "true")
    static class OpMonitorAddonConfig {
        @Bean
        @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
        ServiceHandler opMonitoringServiceHandler(ServerConfProvider serverConfProvider, GlobalConfProvider globalConfProvider) {
            return new OpMonitoringServiceHandlerImpl(serverConfProvider, globalConfProvider);
        }

        @Bean
        @Primary
        AbstractOpMonitoringBuffer opMonitoringBuffer(ServerConfProvider serverConfProvider) throws Exception {
            return new OpMonitoringBuffer(serverConfProvider);
        }
    }

}
