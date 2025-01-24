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
package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfFactory;
import ee.ria.xroad.common.conf.serverconf.ServerConfProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import jakarta.servlet.Filter;
import org.niis.xroad.common.api.throttle.IpThrottlingFilter;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.client.RpcChannelProperties;
import org.niis.xroad.restapi.config.AddCorrelationIdFilter;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.util.CaffeineCacheBuilder;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticCollector;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticReportService;
import org.niis.xroad.securityserver.restapi.service.diagnostic.MonitorClient;
import org.niis.xroad.securityserver.restapi.service.diagnostic.OsVersionCollector;
import org.niis.xroad.securityserver.restapi.service.diagnostic.XrdPackagesCollector;
import org.niis.xroad.securityserver.restapi.service.diagnostic.XrdProcessesCollector;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

import java.util.List;

import static org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService.GET_CERTIFICATE_AUTHORITIES_CACHE;

/**
 * A generic, configuration class for bean initialization.
 */
@Configuration
@EnableConfigurationProperties({
        ServerConfProperties.class,
        SecurityServerConfiguration.EnvMonitorRpcChannelProperties.class,
})
public class SecurityServerConfiguration {

    @Bean
    public ExternalProcessRunner externalProcessRunner() {
        return new ExternalProcessRunner();
    }

    @Bean
    @Order(AddCorrelationIdFilter.CORRELATION_ID_FILTER_ORDER + 3)
    @Profile("nontest")
    public Filter ipThrottlingFilter(AdminServiceProperties properties) {
        return new IpThrottlingFilter(properties);
    }

    @Bean
    public CaffeineCacheBuilder.ConfiguredCache cacheGetCertAuthorities(ApiCachingConfiguration.Config cachingProperties) {
        return CaffeineCacheBuilder.newExpireAfterWriteCache(GET_CERTIFICATE_AUTHORITIES_CACHE, cachingProperties.getCacheDefaultTtl());
    }

    @Bean
    public DiagnosticReportService diagnosticReportService(List<DiagnosticCollector<?>> diagnosticCollectors) {
        return new DiagnosticReportService(diagnosticCollectors);
    }

    @Bean
    @Profile("nontest")
        // todo: should be moved to monitor-rpc-client
    MonitorClient monitorClient(RpcChannelFactory proxyRpcChannelFactory,
                                EnvMonitorRpcChannelProperties rpcChannelProperties) throws Exception {
        return new MonitorClient(proxyRpcChannelFactory, rpcChannelProperties);
    }

    @ConfigurationProperties(prefix = "xroad.common.rpc.channel.env-monitor")
    // todo: should be moved to monitor-rpc-client
    static class EnvMonitorRpcChannelProperties extends RpcChannelProperties {

        public EnvMonitorRpcChannelProperties(String host, int port, int deadlineAfter) {
            super(host, port, deadlineAfter);
        }
    }

    @Bean
    @Profile("nontest")
    @Order(DiagnosticCollector.ORDER_GROUP1)
    public OsVersionCollector osVersionCollector(MonitorClient monitorClient) {
        return new OsVersionCollector(monitorClient);
    }

    @Bean
    @Profile("nontest")
    @Order(DiagnosticCollector.ORDER_GROUP5)
    public XrdPackagesCollector xrdPackagesCollector(MonitorClient monitorClient) {
        return new XrdPackagesCollector(monitorClient);
    }

    @Bean
    @Profile("nontest")
    @Order(DiagnosticCollector.ORDER_GROUP5)
    public XrdProcessesCollector xrdProcessesCollector(MonitorClient monitorClient) {
        return new XrdProcessesCollector(monitorClient);
    }

    @Bean
    public ServerConfProvider serverConfProvider(GlobalConfProvider globalConfProvider, ServerConfProperties serverConfProperties,
                                                 @Qualifier("serverConfDatabaseCtx") DatabaseCtxV2 databaseCtx) {
        return ServerConfFactory.create(serverConfProperties, globalConfProvider, databaseCtx);
    }

    @Bean("serverConfDatabaseCtx")
    DatabaseCtxV2 serverConfDatabaseCtx(ServerConfProperties serverConfProperties) {
        return new DatabaseCtxV2("serverconf", serverConfProperties.hibernate());
    }

}
