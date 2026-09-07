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
package org.niis.xroad.common.acme.spring.dstls;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.config.AcmeSchedulingConfig;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;

/**
 * Wires the DS TLS certificate's own {@link CertificateRenewalScheduler} instance, entirely separate from any
 * product's member auth/sign scheduler bean. Whether it actually runs is decided by
 * {@link DsTlsAcmeHostContext#isSchedulingActive()}, evaluated once when this bean is created — enabling DS TLS
 * ACME while admin-service is already running does not start this scheduler until the process restarts.
 * {@link DsTlsAcmeCertificateRenewalWorker} still resolves the public hostname live on every tick regardless,
 * so a not-currently-enabled state is still handled as "skip this cycle" rather than relying solely on this gate.
 */
@Slf4j
@Configuration
public class DsTlsAcmeCertificateRenewalSchedulingConfig {

    @Bean
    @Profile("!test")
    @Order(Ordered.LOWEST_PRECEDENCE - 98)
    CertificateRenewalScheduler dsTlsAcmeCertificateRenewalScheduler(DsTlsAcmeCertificateRenewalWorker dsTlsAcmeCertificateRenewalWorker,
                                                                      TaskScheduler taskScheduler,
                                                                      AcmeSchedulingConfig acmeConfig,
                                                                      DsTlsAcmeHostContext hostContext) {
        if (!hostContext.isSchedulingActive()) {
            log.info("DS TLS ACME certificate renewal job auto-scheduling disabled");
            return null;
        }
        var scheduler = new CertificateRenewalScheduler(dsTlsAcmeCertificateRenewalWorker, acmeConfig, taskScheduler);
        scheduler.init();
        return scheduler;
    }
}
