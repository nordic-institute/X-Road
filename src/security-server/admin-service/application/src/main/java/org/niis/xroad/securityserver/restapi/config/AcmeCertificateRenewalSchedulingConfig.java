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
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.spring.SpringConditionConfig;
import org.niis.xroad.securityserver.restapi.scheduling.AcmeCertificateRenewalWorker;
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
public class AcmeCertificateRenewalSchedulingConfig {

    @Bean
    @Profile("!test")
    @Order(Ordered.LOWEST_PRECEDENCE - 99)
    @Conditional(IsAcmeCertRenewalJobsActive.class)
    CertificateRenewalScheduler acmeCertificateRenewalScheduler(AcmeCertificateRenewalWorker acmeCertificateRenewalWorker,
                                                                TaskScheduler taskScheduler, AcmeConfig acmeConfig) {
        var scheduler = new CertificateRenewalScheduler(acmeCertificateRenewalWorker, acmeConfig, taskScheduler);
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
