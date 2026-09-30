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

package org.niis.xroad.proxy.core.configuration;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Drives {@link AgreementTokenKeyMaterial#retryOrRefresh()} off the request path, on a programmatic
 * {@link Scheduler} job whose interval comes from {@link ProxyAgreementTokenProperties#keyRefreshInterval()}.
 */
@Startup
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class AgreementTokenKeyRefreshJob {

    private final Scheduler scheduler;
    private final AgreementTokenKeyMaterial keyMaterial;
    private final ProxyAgreementTokenProperties properties;
    private final Scheduled.ApplicationNotRunning applicationNotRunning;

    @PostConstruct
    public void init() {
        var interval = properties.keyRefreshInterval();
        log.info("Scheduling agreement-token signing key refresh every {}", interval);
        scheduler.newJob(getClass().getSimpleName())
                .setInterval(interval.toString())
                .setTask(_ -> keyMaterial.retryOrRefresh())
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setSkipPredicate(applicationNotRunning)
                .schedule();
    }
}
