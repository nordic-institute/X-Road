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

import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;

import java.time.Duration;

/**
 * Blocks a boot-time load until the material an extension serves exists, for material that is provisioned
 * out of band after the process has started (an operator uploading a certificate through the admin API).
 * A distinct failure is logged once when it first appears, with a periodic progress line while it persists,
 * so a service waiting on provisioning neither floods the log nor goes silent.
 *
 * @param <T> the loaded material's runtime representation
 */
public final class InitialMaterialWait<T> {

    private static final int PROGRESS_EVERY_ATTEMPTS = 12;

    private final String name;
    private final PeriodicMaterialReloader.MaterialLoader<T> loader;
    private final Duration pollInterval;
    private final Monitor monitor;
    private final Sleeper sleeper;

    InitialMaterialWait(String name, PeriodicMaterialReloader.MaterialLoader<T> loader, Duration pollInterval,
                        Monitor monitor, Sleeper sleeper) {
        this.name = name;
        this.loader = loader;
        this.pollInterval = pollInterval;
        this.monitor = monitor;
        this.sleeper = sleeper;
    }

    /**
     * Calls {@code loader} until it succeeds, sleeping {@code pollInterval} between attempts, and returns the
     * first material it produces. Only an interrupt of the calling thread ends the wait early, as an
     * {@link EdcException}.
     */
    public static <T> PeriodicMaterialReloader.Loaded<T> await(String name, PeriodicMaterialReloader.MaterialLoader<T> loader,
                                                             Duration pollInterval, Monitor monitor) {
        return new InitialMaterialWait<>(name, loader, pollInterval, monitor, Thread::sleep).awaitMaterial();
    }

    PeriodicMaterialReloader.Loaded<T> awaitMaterial() {
        var startNanos = System.nanoTime();
        String lastFailure = null;
        for (var attempt = 1; ; attempt++) {
            try {
                var loaded = loader.load();
                if (attempt > 1) {
                    monitor.info("%s: material available after %d attempts (%ds)".formatted(name, attempt, elapsedSeconds(startNanos)));
                }
                return loaded;
            } catch (Exception e) {
                lastFailure = logFailure(attempt, startNanos, lastFailure, e);
                sleepOrAbort();
            }
        }
    }

    private String logFailure(int attempt, long startNanos, String lastFailure, Exception failure) {
        var message = String.valueOf(failure.getMessage());
        if (!message.equals(lastFailure)) {
            monitor.warning("%s: not available yet, waiting: %s".formatted(name, message));
        } else if (attempt % PROGRESS_EVERY_ATTEMPTS == 0) {
            monitor.info("%s: still waiting after %d attempts (%ds): %s".formatted(name, attempt, elapsedSeconds(startNanos), message));
        }
        return message;
    }

    private void sleepOrAbort() {
        try {
            sleeper.sleep(pollInterval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EdcException("%s: interrupted while waiting for the material to become available".formatted(name), e);
        }
    }

    private static long elapsedSeconds(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toSeconds();
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }
}
