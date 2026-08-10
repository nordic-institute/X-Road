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
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.securityserver.restapi.acme.AcmeConfig;
import org.niis.xroad.securityserver.restapi.scheduling.DsTlsAcmeEnrollmentScheduler;
import org.niis.xroad.securityserver.restapi.scheduling.DsTlsAcmeEnrollmentWorker;
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
 * Wires the dataspace TLS certificate's ACME enrollment scheduler, parallel to (and separate from) the auth/sign
 * scheduler wired in {@link AcmeBeanConfig} - that class is not modified by this feature.
 */
@Slf4j
@Configuration
public class DsTlsAcmeBeanConfig {

    @Order(Ordered.LOWEST_PRECEDENCE - 99)
    @Bean
    @Conditional(IsDsTlsAcmeEnrollmentActive.class)
    @Profile("!test")
    DsTlsAcmeEnrollmentScheduler dsTlsAcmeEnrollmentScheduler(DsTlsAcmeEnrollmentWorker dsTlsAcmeEnrollmentWorker,
                                                              TaskScheduler taskScheduler, AcmeConfig acmeConfig) {
        DsTlsAcmeEnrollmentScheduler scheduler = new DsTlsAcmeEnrollmentScheduler(dsTlsAcmeEnrollmentWorker, acmeConfig, taskScheduler);
        scheduler.init();
        return scheduler;
    }

    /**
     * Gates the dataspace TLS ACME enrollment scheduler on secondary-node status only.
     * <p>
     * Deliberately does <b>not</b> reuse {@code xroad.proxy-ui-api.acme-renewal-active}
     * ({@link AcmeBeanConfig.IsAcmeCertRenewalJobsActive}): that toggle is historically auth/sign-scoped, and per
     * the PRD's asymmetric design (XRDADR-42 / XRDDEV-3285) the Security Server gets no independent DS TLS
     * kill-switch of its own - disabling the DataSpace feature (leaving the IdentityHub URL blank) is the only
     * off switch here. The Central Server, by contrast, gains an explicit renewal kill-switch as a new capability
     * on a more conservative component (separate issue). If a dedicated SS-side switch is wanted later, it should
     * be a new, independently-named property rather than widening the auth/sign one, so disabling member-cert
     * ACME renewal can never silently disable dataspace TLS renewal or vice versa.
     */
    @Slf4j
    public static class IsDsTlsAcmeEnrollmentActive implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            if (NodeProperties.isSecondaryNode()) {
                log.info("This is a secondary cluster node, dataspace TLS ACME enrollment job auto-scheduling disabled");
                return false;
            }
            return true;
        }
    }

}
