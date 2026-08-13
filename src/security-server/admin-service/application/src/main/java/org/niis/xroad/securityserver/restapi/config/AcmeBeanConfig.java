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

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.niis.xroad.common.acme.AccountKeystorePasswordProvider;
import org.niis.xroad.common.acme.AcmeConfig;
import org.niis.xroad.common.acme.AcmeProperties;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.spring.SpringConditionConfig;
import org.niis.xroad.securityserver.restapi.scheduling.AcmeClientWorker;
import org.niis.xroad.securityserver.restapi.scheduling.CertificateRenewalScheduler;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.TaskScheduler;

@Slf4j
@Configuration
public class AcmeBeanConfig {

    /**
     * Today's account keystore password provisioning: create-or-fetch always throws, exactly as
     * {@link AcmeProperties#createNewAccountKeystorePassword()} does. When XRDDEV-3070 (DB-backed provisioning)
     * lands, only this bean needs to change.
     */
    @Bean
    public AccountKeystorePasswordProvider accountKeystorePasswordProvider(AcmeProperties acmeProperties) {
        return acmeProperties::createNewAccountKeystorePassword;
    }

    @Bean
    public AcmeService acmeService(AcmeProperties acmeProperties, AcmeConfig acmeConfig,
                                   AccountKeystorePasswordProvider accountKeystorePasswordProvider) {
        return new AcmeService(acmeProperties, acmeConfig, accountKeystorePasswordProvider);
    }

    @Profile("nontest")
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> acmeChallengeCustomizer(AcmeConfig acmeConfig) {
        if (acmeConfig.isAcmeChallengePortEnabled()) {
            return factory -> {
                var connector = new Connector(Http11NioProtocol.class.getName());
                int acmeChallengePort = acmeConfig.getAcmeChallengePort();
                connector.setScheme("http");
                connector.setPort(acmeChallengePort);
                log.info("ACME challenge port enabled, listening on port {}", acmeChallengePort);
                factory.addAdditionalConnectors(connector);
            };
        } else {
            log.info("ACME challenge port is disabled");
            return factory -> {
                // no-op
            };
        }
    }

    @Order(Ordered.LOWEST_PRECEDENCE - 99)
    @Bean
    @Conditional(IsAcmeCertRenewalJobsActive.class)
    @Profile("!test")
    CertificateRenewalScheduler certificateRenewalScheduler(AcmeClientWorker acmeClientWorker, TaskScheduler taskScheduler,
                                                            AcmeConfig acmeConfig) {
        CertificateRenewalScheduler scheduler = new CertificateRenewalScheduler(acmeClientWorker, acmeConfig, taskScheduler);
        scheduler.init();
        return scheduler;
    }

    @Slf4j
    public static class IsAcmeCertRenewalJobsActive implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var config = SpringConditionConfig.resolve(context.getEnvironment(), AdminServiceConfigKeys.instance());
            return schedulingEnabled(config.value(AdminServiceConfigKeys.ACME_RENEWAL_ACTIVE));
        }

        static boolean schedulingEnabled(boolean renewalActive) {
            if (!renewalActive) {
                log.info("ACME certificate renewal configured to be inactive, job auto-scheduling disabled");
            }
            if (NodeProperties.isSecondaryNode()) {
                log.info("This is a secondary cluster node, ACME certificate renewal job auto-scheduling disabled");
                return false;
            }
            return renewalActive;
        }
    }

}
