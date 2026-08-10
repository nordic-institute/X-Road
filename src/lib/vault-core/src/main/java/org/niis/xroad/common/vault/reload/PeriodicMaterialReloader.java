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
package org.niis.xroad.common.vault.reload;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Periodically reloads material (a keystore, a trust store, ...) from a slow or unreliable source -
 * typically a secret store - and applies it only when it actually changed.
 *
 * <p>{@link #loadInitial()} is synchronous and unforgiving: exactly one attempt, failure propagates to
 * the caller unwrapped. It is meant for a boot-time load, where an empty or unreachable source has no
 * previously-applied material to fall back to and must fail the boot outright.
 *
 * <p>Once {@link #start()} has been called, every scheduled cycle retries with backoff a bounded number
 * of times. If every attempt in a cycle fails, the previously applied material is kept and the failure
 * is only logged - the schedule itself is never cancelled by a failed cycle, the next cycle always runs.
 *
 * <p>Applying is skipped whenever the freshly loaded material's fingerprint matches the one last
 * applied, so a source that is polled far more often than it actually changes does not repeatedly
 * re-apply identical material.
 *
 * @param <T> the material type (e.g. an {@code InternalSSLKey} or a trust anchor set)
 */
@Slf4j
public final class PeriodicMaterialReloader<T> {

    @FunctionalInterface
    public interface MaterialLoader<T> {
        T load() throws IOException, GeneralSecurityException;
    }

    @FunctionalInterface
    public interface MaterialConsumer<T> {
        void accept(T material) throws IOException, GeneralSecurityException;
    }

    private final String name;
    private final MaterialLoader<T> loader;
    private final Function<T, String> fingerprintOf;
    private final MaterialConsumer<T> onChange;
    private final Duration reloadInterval;
    private final int maxAttemptsPerCycle;
    private final Duration initialRetryDelay;
    private final double retryBackoffMultiplier;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());

    private volatile String appliedFingerprint;

    private PeriodicMaterialReloader(Builder<T> builder) {
        this.name = builder.name;
        this.loader = builder.loader;
        this.fingerprintOf = builder.fingerprintOf;
        this.onChange = builder.onChange;
        this.reloadInterval = builder.reloadInterval;
        this.maxAttemptsPerCycle = builder.maxAttemptsPerCycle;
        this.initialRetryDelay = builder.initialRetryDelay;
        this.retryBackoffMultiplier = builder.retryBackoffMultiplier;
    }

    public static <T> Builder<T> builder(String name, MaterialLoader<T> loader) {
        return new Builder<>(name, loader);
    }

    /**
     * Loads and applies material once, synchronously, with no retry. Intended for the boot-time load:
     * an empty or unreachable source must fail startup outright, not be masked by the periodic cycle's
     * tolerance for transient errors.
     */
    public T loadInitial() throws IOException, GeneralSecurityException {
        var material = loader.load();
        apply(material);
        return material;
    }

    /** Starts the periodic reload cycle. Call once, after {@link #loadInitial()} has succeeded. */
    public void start() {
        scheduleNext();
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    private void scheduleNext() {
        scheduler.schedule(this::runCycle, reloadInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void runCycle() {
        try {
            attemptWithRetry();
        } finally {
            // Never cancel the schedule, regardless of the outcome of this cycle.
            scheduleNext();
        }
    }

    private void attemptWithRetry() {
        var delay = initialRetryDelay;
        for (int attempt = 1; attempt <= maxAttemptsPerCycle; attempt++) {
            try {
                apply(loader.load());
                return;
            } catch (IOException | GeneralSecurityException e) {
                if (attempt == maxAttemptsPerCycle) {
                    log.error("[{}] reload failed after {} attempt(s); keeping previously applied material",
                            name, maxAttemptsPerCycle, e);
                    return;
                }
                log.warn("[{}] reload attempt {}/{} failed: {}", name, attempt, maxAttemptsPerCycle, e.getMessage());
                sleep(delay);
                delay = Duration.ofMillis((long) (delay.toMillis() * retryBackoffMultiplier));
            }
        }
    }

    private void apply(T material) throws IOException, GeneralSecurityException {
        var fingerprint = fingerprintOf.apply(material);
        if (!fingerprint.equals(appliedFingerprint)) {
            onChange.accept(material);
            appliedFingerprint = fingerprint;
            log.info("[{}] applied new material (fingerprint {})", name, fingerprint);
        } else {
            log.debug("[{}] material unchanged (fingerprint {})", name, fingerprint);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw XrdRuntimeException.systemInternalError("Interrupted while waiting to retry a material reload", e);
        }
    }

    public static final class Builder<T> {
        private static final int DEFAULT_MAX_ATTEMPTS_PER_CYCLE = 3;
        private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

        private final String name;
        private final MaterialLoader<T> loader;
        private Function<T, String> fingerprintOf = Object::toString;
        private MaterialConsumer<T> onChange;
        private Duration reloadInterval = Duration.ofMinutes(1);
        private int maxAttemptsPerCycle = DEFAULT_MAX_ATTEMPTS_PER_CYCLE;
        private Duration initialRetryDelay = Duration.ofSeconds(2);
        private double retryBackoffMultiplier = DEFAULT_RETRY_BACKOFF_MULTIPLIER;

        private Builder(String name, MaterialLoader<T> loader) {
            this.name = name;
            this.loader = loader;
        }

        public Builder<T> fingerprint(Function<T, String> value) {
            this.fingerprintOf = value;
            return this;
        }

        public Builder<T> onChange(MaterialConsumer<T> value) {
            this.onChange = value;
            return this;
        }

        public Builder<T> reloadInterval(Duration value) {
            this.reloadInterval = value;
            return this;
        }

        public Builder<T> maxAttemptsPerCycle(int value) {
            this.maxAttemptsPerCycle = value;
            return this;
        }

        public Builder<T> initialRetryDelay(Duration value) {
            this.initialRetryDelay = value;
            return this;
        }

        public Builder<T> retryBackoffMultiplier(double value) {
            this.retryBackoffMultiplier = value;
            return this;
        }

        public PeriodicMaterialReloader<T> build() {
            if (onChange == null) {
                throw new IllegalStateException("onChange consumer must be set");
            }
            return new PeriodicMaterialReloader<>(this);
        }
    }
}
