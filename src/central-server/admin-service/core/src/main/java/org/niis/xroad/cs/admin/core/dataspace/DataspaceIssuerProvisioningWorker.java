/*
 * The MIT License
 *
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
package org.niis.xroad.cs.admin.core.dataspace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

/**
 * Level-triggered worker that provisions the co-located dataspace issuer once the Central Server
 * is initialized. The issuer service cannot start before its DS TLS certificate is provisioned,
 * and that certificate is obtained after initialization (ACME enrollment or the manual CSR
 * upload), so initialization must not depend on a live issuer; this worker retries until the
 * issuer accepts the provisioning calls, which are idempotent on the issuer side. While
 * preconditions are unmet it rechecks on a fixed cadence; once an attempt has actually been made
 * and failed, further attempts back off exponentially so a persistent fault doesn't spam the log
 * or the issuer with a call every few seconds.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataspaceIssuerProvisioningWorker implements InitializingBean, DisposableBean {

    private static final Duration INITIAL_DELAY = Duration.ofSeconds(30);
    private static final Duration RECHECK_INTERVAL = Duration.ofSeconds(30);

    private static final Duration BACKOFF_INITIAL_INTERVAL = Duration.ofSeconds(30);
    private static final double BACKOFF_MULTIPLIER = 2.0;
    private static final Duration BACKOFF_MAX_INTERVAL = Duration.ofMinutes(5);

    private static BackOffExecution initBackOffExecution() {
        var backOff = new ExponentialBackOff(BACKOFF_INITIAL_INTERVAL.toMillis(), BACKOFF_MULTIPLIER);
        backOff.setMaxInterval(BACKOFF_MAX_INTERVAL.toMillis());
        return backOff.start();
    }

    private final DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    private final SystemParameterService systemParameterService;
    private final DataspaceIssuerProperties dataspaceIssuerProperties;
    private final TaskScheduler taskScheduler;

    @Getter
    private volatile ProvisioningState state = new ProvisioningState(Status.WAITING_FOR_CONFIGURATION, null, null);

    private BackOffExecution backOffExecution;
    private ScheduledFuture<?> scheduledFuture;

    @Override
    public void afterPropertiesSet() {
        reschedule(INITIAL_DELAY);
    }

    @Override
    public void destroy() {
        cancelNext();
    }

    /**
     * Non-blocking eager trigger for the initialization request path: runs one best-effort
     * provisioning attempt on a background thread; the scheduled tick remains the convergence
     * guarantee.
     */
    public void provisionAsync() {
        CompletableFuture.runAsync(this::provisionBestEffort);
    }

    private void scheduledProvision() {
        if (state.status() == Status.PROVISIONED) {
            return;
        }
        if (!preconditionsMet()) {
            log.debug("Dataspace issuer provisioning: preconditions not met, skipping");
            reschedule(RECHECK_INTERVAL);
            return;
        }
        provisionBestEffort();
        if (state.status() == Status.FAILING) {
            reschedule(nextBackoffDelay());
        }
    }

    private synchronized void provisionBestEffort() {
        if (state.status() == Status.PROVISIONED || !preconditionsMet()) {
            return;
        }
        try {
            dataspaceIssuerProvisioningService.provisionIssuer();
            state = new ProvisioningState(Status.PROVISIONED, null, Instant.now());
            log.info("Dataspace issuer provisioned");
        } catch (Exception e) {
            state = new ProvisioningState(Status.FAILING, e, Instant.now());
            log.warn("Dataspace issuer provisioning failed: {}. Dataspace features stay unavailable until "
                    + "provisioned; check the issuer service is running and its DS TLS certificate is ready.", e.getMessage(), e);
        }
    }

    private boolean preconditionsMet() {
        return !systemParameterService.getInstanceIdentifier().isEmpty()
                && !systemParameterService.getCentralServerAddress().isEmpty()
                && dataspaceIssuerProperties.isHostConfigured();
    }

    private Duration nextBackoffDelay() {
        if (backOffExecution == null) {
            backOffExecution = initBackOffExecution();
        }
        return Duration.ofMillis(backOffExecution.nextBackOff());
    }

    private void reschedule(Duration delay) {
        cancelNext();
        log.trace("Rescheduling dataspace issuer provisioning in {}", delay);
        this.scheduledFuture = taskScheduler.schedule(this::scheduledProvision, taskScheduler.getClock().instant().plus(delay));
    }

    private void cancelNext() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    public enum Status {
        WAITING_FOR_CONFIGURATION,
        FAILING,
        PROVISIONED
    }

    public record ProvisioningState(Status status, Exception lastError, Instant lastAttemptAt) {
    }
}
