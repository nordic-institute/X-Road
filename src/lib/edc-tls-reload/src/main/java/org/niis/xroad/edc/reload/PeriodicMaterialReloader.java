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
package org.niis.xroad.edc.reload;

import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Polls a vault-backed secret on a fixed schedule and applies it when it changes, for material an
 * EDC extension serves from an eagerly-built, in-process object (a keystore, a trust manager) rather than
 * re-reading the vault on every use. A failed poll is retried a bounded number of times within the same
 * cycle; if every attempt in a cycle fails, the previously applied material keeps serving and the next
 * cycle tries again — the schedule itself is never cancelled, even by an exception the loader or the
 * apply callback did not anticipate.
 *
 * @param <T> the loaded material's runtime representation (e.g. a {@link java.security.KeyStore})
 */
public final class PeriodicMaterialReloader<T> implements AutoCloseable {

    private final String name;
    private final MaterialLoader<T> loader;
    private final Consumer<T> onChange;
    private final Monitor monitor;
    private final int maxAttemptsPerCycle;
    private final Duration retryDelay;
    private final ScheduledExecutorService executor;

    private volatile String appliedFingerprint;

    private PeriodicMaterialReloader(String name, MaterialLoader<T> loader, Consumer<T> onChange, Monitor monitor,
                                      int maxAttemptsPerCycle, Duration retryDelay, String initialFingerprint) {
        this.name = name;
        this.loader = loader;
        this.onChange = onChange;
        this.monitor = monitor;
        this.maxAttemptsPerCycle = maxAttemptsPerCycle;
        this.retryDelay = retryDelay;
        this.appliedFingerprint = initialFingerprint;
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "xrd-edc-reload-" + name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts polling {@code loader} every {@code checkInterval}, beginning one interval after this call
     * returns. {@code initial} is the material already applied by the caller's own eager, fail-fast boot-time
     * load — the schedule only calls {@code onChange} once the loader reports a fingerprint different from it.
     */
    public static <T> PeriodicMaterialReloader<T> schedule(String name, Loaded<T> initial, Duration checkInterval,
                                                            int maxAttemptsPerCycle, Duration retryDelay,
                                                            MaterialLoader<T> loader, Consumer<T> onChange, Monitor monitor) {
        var reloader = new PeriodicMaterialReloader<>(name, loader, onChange, monitor, maxAttemptsPerCycle, retryDelay,
                initial.fingerprint());
        reloader.executor.scheduleWithFixedDelay(reloader::runCycleSafely,
                checkInterval.toMillis(), checkInterval.toMillis(), TimeUnit.MILLISECONDS);
        return reloader;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    private void runCycleSafely() {
        try {
            runCycle();
        } catch (Throwable t) {
            // A scheduled task that escapes with an exception is silently cancelled by
            // ScheduledExecutorService — swallowing here is what keeps future cycles running.
            monitor.severe("%s: reload cycle failed unexpectedly; the schedule continues".formatted(name), t);
        }
    }

    private void runCycle() {
        for (var attempt = 1; attempt <= maxAttemptsPerCycle; attempt++) {
            try {
                var loaded = loader.load();
                if (!Objects.equals(loaded.fingerprint(), appliedFingerprint)) {
                    onChange.accept(loaded.material());
                    appliedFingerprint = loaded.fingerprint();
                    monitor.info("%s: reloaded material (fingerprint changed)".formatted(name));
                }
                return;
            } catch (Exception e) {
                monitor.warning("%s: reload attempt %d/%d failed: %s".formatted(name, attempt, maxAttemptsPerCycle, e.getMessage()));
                if (attempt < maxAttemptsPerCycle) {
                    sleepUninterruptibly(retryDelay);
                }
            }
        }
        monitor.severe("%s: exhausted %d reload attempts this cycle; continuing to serve the previously loaded material"
                .formatted(name, maxAttemptsPerCycle));
    }

    private static void sleepUninterruptibly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    public interface MaterialLoader<T> {
        Loaded<T> load();
    }

    public record Loaded<T>(T material, String fingerprint) {
    }
}
