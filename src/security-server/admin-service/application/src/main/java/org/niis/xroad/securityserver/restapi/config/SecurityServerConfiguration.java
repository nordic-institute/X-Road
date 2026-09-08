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

import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.niis.xroad.common.api.throttle.IpThrottlingFilter;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.spring.SpringConditionConfig;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.restapi.config.AddCorrelationIdFilter;
import org.niis.xroad.restapi.config.ApiCachingConfiguration;
import org.niis.xroad.restapi.util.CaffeineCacheBuilder;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticCollector;
import org.niis.xroad.securityserver.restapi.service.diagnostic.DiagnosticReportService;
import org.niis.xroad.securityserver.restapi.service.diagnostic.OsVersionCollector;
import org.niis.xroad.securityserver.restapi.service.diagnostic.XrdPackagesCollector;
import org.niis.xroad.securityserver.restapi.service.diagnostic.XrdProcessesCollector;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.List;

import static org.niis.xroad.securityserver.restapi.service.CertificateAuthorityService.GET_CERTIFICATE_AUTHORITIES_CACHE;

/**
 * A generic, configuration class for bean initialization.
 */
@Configuration
public class SecurityServerConfiguration {

    private static final int IP_THROTTLING_FILTER_ORDER = AddCorrelationIdFilter.CORRELATION_ID_FILTER_ORDER + 3;

    @Bean
    public ExternalProcessRunner externalProcessRunner() {
        return new ExternalProcessRunner();
    }

    @Bean
    @Conditional(RateLimitEnabledCondition.class)
    @Profile("nontest")
    public FilterRegistrationBean<IpThrottlingFilter> ipThrottlingFilter(AdminServiceProperties properties) {
        var filter = new IpThrottlingFilter(properties);
        var bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(IP_THROTTLING_FILTER_ORDER);
        bean.addUrlPatterns(IpThrottlingFilter.ADMIN_UI_PATTERNS);
        return bean;
    }

    static class RateLimitEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var config = SpringConditionConfig.resolve(context.getEnvironment(), AdminServiceConfigKeys.instance());
            if (!config.value(AdminServiceConfigKeys.RATE_LIMIT_ENABLED)) {
                return false;
            }
            return config.value(AdminServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_SECOND) > 0
                    || config.value(AdminServiceConfigKeys.RATE_LIMIT_REQUESTS_PER_MINUTE) > 0;
        }
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
    @Order(DiagnosticCollector.ORDER_GROUP1)
    public OsVersionCollector osVersionCollector(MonitorRpcClient monitorClient) {
        return new OsVersionCollector(monitorClient);
    }

    @Bean
    @Profile("nontest")
    @Order(DiagnosticCollector.ORDER_GROUP5)
    public XrdPackagesCollector xrdPackagesCollector(MonitorRpcClient monitorClient) {
        return new XrdPackagesCollector(monitorClient);
    }

    @Bean
    @Profile("nontest")
    @Order(DiagnosticCollector.ORDER_GROUP5)
    public XrdProcessesCollector xrdProcessesCollector(MonitorRpcClient monitorClient) {
        return new XrdProcessesCollector(monitorClient);
    }
}
