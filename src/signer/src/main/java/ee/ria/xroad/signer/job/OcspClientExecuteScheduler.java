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

package ee.ria.xroad.signer.job;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.conf.globalconfextension.OcspFetchInterval;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;

import static java.time.temporal.ChronoUnit.SECONDS;

@Slf4j
@RequiredArgsConstructor
public class OcspClientExecuteScheduler {
    private static final Duration RECOVER_FROM_INVALID_GLOBALCONF_DELAY = Duration.of(60, SECONDS);
    private static final Duration INITIAL_DELAY = Duration.of(5, SECONDS);

    private final OcspClientWorker ocspClientWorker;
    private final TaskScheduler taskScheduler;
    private final GlobalConfProvider globalConfProvider;

    private ScheduledFuture<?> scheduledFuture;
    private boolean retryMode;

    public void init() {
        reschedule(INITIAL_DELAY);
    }

    private Duration getNextDelay() {
        final int retryDelay = SystemProperties.getOcspResponseRetryDelay();
        int nextOcspFetchIntervalSeconds = getNextOcspFetchIntervalSeconds();
        if (retryMode && retryDelay < nextOcspFetchIntervalSeconds) {
            return Duration.of(retryDelay, SECONDS);
        }
        return Duration.of(nextOcspFetchIntervalSeconds, SECONDS);
    }

    public void success() {
        log.info("OCSP-response refresh cycle successfully completed, continuing with normal scheduling");
        retryMode = false;
    }

    public void failure() {
        if (!retryMode) {
            log.info("OCSP-response refresh cycle failed, switching to retry backoff schedule");
            retryMode = true;
            reschedule(getNextDelay());
        } else {
            log.info("OCSP-response refresh retry failed, continuing along backoff schedule");
        }
    }

    public void globalConfInvalidated() {
        log.info("OCSP-response refresh cycle failed due to invalid global configuration, "
                + "switching to global configuration recovery schedule");
        // attempted to execute OCSP refresh, but global conf was
        // invalid at that time -> reschedule
        reschedule(RECOVER_FROM_INVALID_GLOBALCONF_DELAY);
        retryMode = false;
    }

    public void execute() {
        reschedule(Duration.ZERO);
    }

    private void runJob() {
        try {
            ocspClientWorker.execute(this);
        } finally {
            reschedule(getNextDelay());
        }
    }

    public void reschedule() {
        log.info("OCSP-response refresh cycle rescheduling");
        this.reschedule(getNextDelay());
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

    /**
     * @return the next ocsp freshness time in seconds
     */
    private int getNextOcspFetchIntervalSeconds() {
        int interval = GlobalConfExtensions.getInstance(globalConfProvider).getOcspFetchInterval();

        if (interval < OcspFetchInterval.OCSP_FETCH_INTERVAL_MIN) {
            interval = OcspFetchInterval.OCSP_FETCH_INTERVAL_MIN;
        }

        return interval;
    }

}
