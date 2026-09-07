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
package org.niis.xroad.securityserver.restapi.dstls;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.config.AcmeConfig;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.properties.config.keys.AdminServiceConfigKeys;
import org.niis.xroad.common.properties.spring.SpringConditionConfig;
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

/**
 * Wires the DS TLS certificate's own {@link CertificateRenewalScheduler} instance, entirely separate from the
 * auth/sign scheduler bean in {@code AcmeCertificateRenewalSchedulingConfig}. Gated on secondary-node status and
 * on the DataSpace feature flag ({@code AdminServiceConfigKeys#DATASPACE_ENABLED}) — there is no independent
 * DS-TLS-specific kill-switch, so disabling DataSpace is what disables this scheduler too.
 * <p>
 * Like any {@link Conditional} bean, this is evaluated once at context startup: enabling DataSpace while
 * admin-service is already running does not start this scheduler until the process restarts, same restart
 * requirement {@code AcmeCertificateRenewalSchedulingConfig}'s own {@code acme-renewal-active} flag already has.
 * {@link DsTlsAcmeCertificateRenewalWorker} still resolves the public hostname live on every tick regardless,
 * so a blank hostname is still handled as "skip this cycle" rather than relying solely on this gate.
 */
@Slf4j
@Configuration
public class DsTlsAcmeCertificateRenewalSchedulingConfig {

    @Bean
    @Profile("!test")
    @Order(Ordered.LOWEST_PRECEDENCE - 98)
    @Conditional(IsDsTlsAcmeSchedulingActive.class)
    CertificateRenewalScheduler dsTlsAcmeCertificateRenewalScheduler(DsTlsAcmeCertificateRenewalWorker dsTlsAcmeCertificateRenewalWorker,
                                                                     TaskScheduler taskScheduler, AcmeConfig acmeConfig) {
        var scheduler = new CertificateRenewalScheduler(dsTlsAcmeCertificateRenewalWorker, acmeConfig, taskScheduler);
        scheduler.init();
        return scheduler;
    }

    @Slf4j
    public static class IsDsTlsAcmeSchedulingActive implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var config = SpringConditionConfig.resolve(context.getEnvironment(), AdminServiceConfigKeys.instance());
            return schedulingEnabled(config.value(AdminServiceConfigKeys.DATASPACE_ENABLED));
        }

        static boolean schedulingEnabled(boolean dataspaceEnabled) {
            if (!dataspaceEnabled) {
                log.info("DataSpace feature is not enabled, DS TLS ACME certificate renewal job auto-scheduling disabled");
            }
            if (NodeProperties.isSecondaryNode()) {
                log.info("This is a secondary cluster node, DS TLS ACME certificate renewal job auto-scheduling disabled");
                return false;
            }
            return dataspaceEnabled;
        }
    }

}
