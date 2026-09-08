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
package org.niis.xroad.signer.softtoken.sync;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe health state tracker for the software token key synchronization job.
 * <p>
 * Tracks consecutive failure count, last successful sync timestamp, and last failure message.
 * This state is intended to be read by health probes (liveness, readiness) in a later phase
 * to detect sustained sync failures and stale key data.
 * <p>
 * All fields use atomic types to ensure thread safety between the scheduler thread
 * (which writes) and health probe threads (which read).
 */
@Slf4j
@ApplicationScoped
public class SyncHealthState {

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> lastSuccessfulSync = new AtomicReference<>();
    private final AtomicReference<String> lastFailureMessage = new AtomicReference<>();

    /**
     * Records a successful synchronization. Resets consecutive failure count to zero,
     * updates last successful sync timestamp, and clears last failure message.
     */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        lastSuccessfulSync.set(Instant.now());
        lastFailureMessage.set(null);
    }

    /**
     * Records a failed synchronization. Increments the consecutive failure count
     * and stores the error message. Does not modify the last successful sync timestamp.
     *
     * @param errorMessage the error message describing the failure
     */
    public void recordFailure(String errorMessage) {
        consecutiveFailures.incrementAndGet();
        lastFailureMessage.set(errorMessage);
    }

    /**
     * Returns the number of consecutive sync failures since the last success.
     *
     * @return consecutive failure count, 0 if last sync was successful or no syncs attempted
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * Returns the timestamp of the last successful synchronization, if any.
     *
     * @return the last successful sync instant, or empty if no successful sync has occurred
     */
    public Optional<Instant> getLastSuccessfulSync() {
        return Optional.ofNullable(lastSuccessfulSync.get());
    }

    /**
     * Returns the error message from the last failed synchronization, if any.
     *
     * @return the last failure message, or empty if no failure has occurred or last sync was successful
     */
    public Optional<String> getLastFailureMessage() {
        return Optional.ofNullable(lastFailureMessage.get());
    }
}
