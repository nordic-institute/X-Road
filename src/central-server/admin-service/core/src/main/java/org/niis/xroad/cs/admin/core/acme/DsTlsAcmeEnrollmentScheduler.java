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
package org.niis.xroad.cs.admin.core.acme;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Retry/backoff scheduler for {@link DsTlsAcmeEnrollmentWorker}. Unlike the Security Server's equivalent, there is
 * no globalconf-invalidation recovery schedule - the Central Server produces its own globalconf and reads the
 * designated CA straight from its own database.
 */
@Slf4j
@RequiredArgsConstructor
public class DsTlsAcmeEnrollmentScheduler {

    private final DsTlsAcmeEnrollmentWorker dsTlsAcmeEnrollmentWorker;
    private final AcmeConfig acmeConfig;
    private final TaskScheduler taskScheduler;
    private ScheduledFuture<?> scheduledFuture;

    private static final Duration INITIAL_DELAY = Duration.of(5, SECONDS);
    private boolean retryMode;

    public void init() {
        reschedule(INITIAL_DELAY);
    }

    private Duration getNextDelay() {
        final int retryDelay = acmeConfig.getRenewalRetryDelay();
        if (retryMode && retryDelay < acmeConfig.getRenewalInterval()) {
            return Duration.of(retryDelay, SECONDS);
        }
        return Duration.of(acmeConfig.getRenewalInterval(), SECONDS);
    }

    public void success() {
        log.info("Dataspace TLS certificate ACME cycle successfully completed, continuing with normal scheduling");
        retryMode = false;
    }

    public void failure() {
        if (!retryMode) {
            log.info("Dataspace TLS certificate ACME cycle failed, switching to retry backoff schedule");
            retryMode = true;
            reschedule(getNextDelay());
        } else {
            log.info("Dataspace TLS certificate ACME retry failed, continuing along backoff schedule");
        }
    }

    private void runJob() {
        try {
            dsTlsAcmeEnrollmentWorker.execute(this);
        } finally {
            reschedule(getNextDelay());
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
