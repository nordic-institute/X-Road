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
package org.niis.xroad.common.acme.spring.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.acme.config.AcmeSchedulingProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static java.time.temporal.ChronoUnit.SECONDS;

@Slf4j
public class CertificateRenewalScheduler implements DisposableBean {

    private final AcmeRenewalWorker acmeRenewalWorker;
    private final AcmeSchedulingProperties acmeConfig;
    private final TaskScheduler taskScheduler;
    private final boolean ownsTaskScheduler;
    private ScheduledFuture<?> scheduledFuture;

    private static final Duration RECOVER_FROM_INVALID_GLOBAL_CONF_DELAY = Duration.of(60, SECONDS);
    private static final Duration INITIAL_DELAY = Duration.of(5, SECONDS);
    private boolean retryMode;
    private boolean rescheduledDuringCycle;

    public CertificateRenewalScheduler(AcmeRenewalWorker acmeRenewalWorker, AcmeSchedulingProperties acmeConfig,
                                       TaskScheduler taskScheduler) {
        this(acmeRenewalWorker, acmeConfig, taskScheduler, false);
    }

    private CertificateRenewalScheduler(AcmeRenewalWorker acmeRenewalWorker, AcmeSchedulingProperties acmeConfig,
                                        TaskScheduler taskScheduler, boolean ownsTaskScheduler) {
        this.acmeRenewalWorker = acmeRenewalWorker;
        this.acmeConfig = acmeConfig;
        this.taskScheduler = taskScheduler;
        this.ownsTaskScheduler = ownsTaskScheduler;
    }

    /**
     * Creates a {@link CertificateRenewalScheduler} backed by its own single-thread {@link ThreadPoolTaskScheduler},
     * isolated from any shared {@code TaskScheduler} used elsewhere in the application. The dedicated scheduler is
     * started immediately and is shut down when this instance is {@linkplain #destroy() destroyed}.
     */
    public static CertificateRenewalScheduler withDedicatedScheduler(AcmeRenewalWorker acmeRenewalWorker,
                                                                     AcmeSchedulingProperties acmeConfig,
                                                                     String threadNamePrefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.initialize();
        log.info("Started dedicated ACME renewal scheduler thread '{}'", threadNamePrefix);
        return new CertificateRenewalScheduler(acmeRenewalWorker, acmeConfig, scheduler, true);
    }

    public void init() {
        reschedule(INITIAL_DELAY);
    }

    @Override
    public void destroy() {
        cancelNext();
        if (ownsTaskScheduler) {
            log.info("Shutting down dedicated ACME renewal scheduler thread");
            ((ThreadPoolTaskScheduler) taskScheduler).shutdown();
        }
    }

    private Duration getNextDelay() {
        final int retryDelay = acmeConfig.getAcmeRenewalRetryDelay();
        if (retryMode && retryDelay < acmeConfig.getAcmeRenewalInterval()) {
            return Duration.of(retryDelay, SECONDS);
        }
        return Duration.of(acmeConfig.getAcmeRenewalInterval(), SECONDS);
    }

    public void success() {
        log.info("ACME certificate renewal cycle successfully completed, continuing with normal scheduling");
        retryMode = false;
    }

    public void failure() {
        if (!retryMode) {
            log.info("ACME certificate renewal failed, switching to retry backoff schedule");
            retryMode = true;
            reschedule(getNextDelay());
        } else {
            log.info("ACME certificate renewal retry failed, continuing along backoff schedule");
        }
    }

    public void globalConfInvalidated() {
        log.info("ACME certificate renewal cycle failed due to invalid global configuration, "
                + "switching to global configuration recovery schedule");
        reschedule(RECOVER_FROM_INVALID_GLOBAL_CONF_DELAY);
        retryMode = false;
        rescheduledDuringCycle = true;
    }

    private void runJob() {
        rescheduledDuringCycle = false;
        try {
            acmeRenewalWorker.execute(this);
        } finally {
            // globalConfInvalidated() already scheduled its own recovery delay; scheduling again here with
            // getNextDelay() (which no longer reflects that call's intent) would immediately cancel and replace
            // it with the normal, much longer renewal interval.
            if (!rescheduledDuringCycle) {
                reschedule(getNextDelay());
            }
        }
    }

    private void cancelNext() {
        if (this.scheduledFuture != null) {
            this.scheduledFuture.cancel(false);
        }
    }

    private void reschedule(Duration delay) {
        cancelNext();
        log.trace("Rescheduling job after {}", delay);
        this.scheduledFuture = taskScheduler.schedule(this::runJob, taskScheduler.getClock().instant().plus(delay));
    }
}
