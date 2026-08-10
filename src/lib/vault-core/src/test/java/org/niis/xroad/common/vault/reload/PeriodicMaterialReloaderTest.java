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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class PeriodicMaterialReloaderTest {

    @Test
    void loadInitialAppliesMaterialAndPropagatesFailureUnwrapped() throws Exception {
        Queue<String> applied = new ConcurrentLinkedQueue<>();
        var reloader = PeriodicMaterialReloader.builder("test", () -> "v1")
                .onChange(applied::add)
                .build();

        var loaded = reloader.loadInitial();

        assertThat(loaded).isEqualTo("v1");
        assertThat(applied).containsExactly("v1");
    }

    @Test
    void loadInitialDoesNotRetryAndPropagatesFailure() {
        var attempts = new AtomicInteger();
        PeriodicMaterialReloader.MaterialLoader<String> alwaysFails = () -> {
            attempts.incrementAndGet();
            throw new IOException("vault unreachable");
        };
        var reloader = PeriodicMaterialReloader.builder("test", alwaysFails)
                .onChange(m -> { })
                .build();

        assertThatThrownBy(reloader::loadInitial).isInstanceOf(IOException.class);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void periodicReloadSkipsApplyWhenFingerprintUnchanged() throws Exception {
        Queue<String> applied = new ConcurrentLinkedQueue<>();
        var reloader = PeriodicMaterialReloader.builder("test", () -> "same")
                .onChange(applied::add)
                .reloadInterval(Duration.ofMillis(20))
                .build();

        reloader.loadInitial();
        reloader.start();
        try {
            // Give the schedule a few cycles to run; the material never changes.
            Thread.sleep(150);
            assertThat(applied).containsExactly("same");
        } finally {
            reloader.shutdown();
        }
    }

    @Test
    void periodicReloadAppliesNewMaterialOnceItChanges() throws Exception {
        Queue<String> applied = new ConcurrentLinkedQueue<>();
        var currentVersion = new AtomicReference<>("v1");
        var reloader = PeriodicMaterialReloader.builder("test", currentVersion::get)
                .onChange(applied::add)
                .reloadInterval(Duration.ofMillis(20))
                .build();

        reloader.loadInitial();
        reloader.start();
        try {
            currentVersion.set("v2");
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(applied).containsExactly("v1", "v2"));
        } finally {
            reloader.shutdown();
        }
    }

    @Test
    void periodicReloadRetriesWithinACycleThenKeepsOldMaterialOnExhaustion() throws Exception {
        Queue<String> applied = new ConcurrentLinkedQueue<>();
        var loadCount = new AtomicInteger();
        // First call (loadInitial) succeeds; every call after that fails, so the periodic cycle
        // has real "old material" to fall back on.
        PeriodicMaterialReloader.MaterialLoader<String> succeedsOnceThenAlwaysFails = () -> {
            if (loadCount.getAndIncrement() == 0) {
                return "v0";
            }
            throw new IOException("vault unreachable");
        };
        var reloader = PeriodicMaterialReloader.builder("test", succeedsOnceThenAlwaysFails)
                .onChange(applied::add)
                .reloadInterval(Duration.ofMillis(20))
                .maxAttemptsPerCycle(3)
                .initialRetryDelay(Duration.ofMillis(5))
                .retryBackoffMultiplier(1.0)
                .build();

        reloader.loadInitial();
        reloader.start();

        try {
            // Every subsequent load fails: each cycle should retry maxAttemptsPerCycle times
            // (1 initial load + at least 2 full failing cycles of 3 attempts each = 7 calls),
            // and the schedule must keep running further cycles after exhausting retries.
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(loadCount.get()).isGreaterThanOrEqualTo(7));
            assertThat(applied).containsExactly("v0");
        } finally {
            reloader.shutdown();
        }
    }

    @Test
    void neverCancelsScheduleAfterAFailedCycle() throws Exception {
        var cycles = new AtomicInteger();
        PeriodicMaterialReloader.MaterialLoader<String> failsOnlyOnFirstCall = () -> {
            if (cycles.getAndIncrement() == 0) {
                throw new IOException("first cycle fails");
            }
            return "v1";
        };
        var reloader = PeriodicMaterialReloader.builder("test", failsOnlyOnFirstCall)
                .onChange(m -> { })
                .reloadInterval(Duration.ofMillis(20))
                .maxAttemptsPerCycle(1)
                .build();

        reloader.start();

        try {
            await().atMost(Duration.ofSeconds(2)).untilAsserted(() ->
                    assertThat(cycles.get()).isGreaterThanOrEqualTo(2));
        } finally {
            reloader.shutdown();
        }
    }
}
