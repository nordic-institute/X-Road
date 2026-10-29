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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PeriodicMaterialReloaderTest {

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(20);
    private static final Duration RETRY_DELAY = Duration.ofMillis(5);

    @Mock
    private Monitor monitor;

    @Test
    void appliesNewMaterialOnlyWhenFingerprintChanges() {
        List<String> versions = List.of("v1", "v1", "v2", "v2", "v2");
        var index = new AtomicInteger();
        Queue<String> applied = new ConcurrentLinkedQueue<>();

        var reloader = PeriodicMaterialReloader.schedule("test", new PeriodicMaterialReloader.Loaded<>("v0", "v0"),
                CHECK_INTERVAL, 3, RETRY_DELAY,
                () -> {
                    var version = versions.get(Math.min(index.getAndIncrement(), versions.size() - 1));
                    return new PeriodicMaterialReloader.Loaded<>(version, version);
                },
                applied::add, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> applied.contains("v2"));
            assertThat(applied).containsExactly("v1", "v2");
        } finally {
            reloader.close();
        }
    }

    @Test
    void retriesWithinACycleAndKeepsOldMaterialWhenExhausted() {
        var attempts = new AtomicInteger();
        var applied = new ConcurrentLinkedQueue<String>();

        var reloader = PeriodicMaterialReloader.<String>schedule("test", new PeriodicMaterialReloader.Loaded<>("v0", "v0"),
                CHECK_INTERVAL, 3, Duration.ofMillis(1),
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("vault unreachable");
                },
                applied::add, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> attempts.get() >= 3);
        } finally {
            reloader.close();
        }

        assertThat(applied).isEmpty();
    }

    @Test
    void aLoaderThatAlwaysThrowsNeverCancelsTheSchedule() {
        var cycles = new AtomicInteger();

        var reloader = PeriodicMaterialReloader.<String>schedule("test", new PeriodicMaterialReloader.Loaded<>("v0", "v0"),
                CHECK_INTERVAL, 1, RETRY_DELAY,
                () -> {
                    cycles.incrementAndGet();
                    throw new IllegalStateException("vault unreachable");
                },
                material -> { }, monitor);
        try {
            var firstCount = cycles.get();
            await().atMost(Duration.ofSeconds(2)).until(() -> cycles.get() > firstCount + 2);
        } finally {
            reloader.close();
        }
    }

    @Test
    void anApplyFailureIsTreatedAsAnAttemptFailureAndDoesNotAdvanceTheFingerprint() {
        var appliedCount = new AtomicInteger();

        var reloader = PeriodicMaterialReloader.schedule("test", new PeriodicMaterialReloader.Loaded<>("v0", "v0"),
                CHECK_INTERVAL, 3, RETRY_DELAY,
                () -> new PeriodicMaterialReloader.Loaded<>("v1", "v1"),
                material -> {
                    appliedCount.incrementAndGet();
                    throw new IllegalStateException("apply failed");
                }, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> appliedCount.get() >= 3);
        } finally {
            reloader.close();
        }
    }

    @Test
    void withNoInitialMaterialTheFirstSuccessfulLoadFiresOnChangeOnceAndAnUnchangedFingerprintDoesNot() {
        var loads = new AtomicInteger();
        var applied = new ConcurrentLinkedQueue<String>();

        var reloader = PeriodicMaterialReloader.scheduleUntilFirstLoad("test", CHECK_INTERVAL, CHECK_INTERVAL, 3, RETRY_DELAY,
                () -> {
                    loads.incrementAndGet();
                    return new PeriodicMaterialReloader.Loaded<>("v1", "v1");
                },
                applied::add, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> loads.get() >= 4);
        } finally {
            reloader.close();
        }

        assertThat(applied).containsExactly("v1");
    }

    @Test
    void withNoInitialMaterialRepeatedLoaderFailuresKeepItPendingAndRetrying() {
        var attempts = new AtomicInteger();
        var applied = new ConcurrentLinkedQueue<String>();

        var reloader = PeriodicMaterialReloader.scheduleUntilFirstLoad("test", CHECK_INTERVAL, Duration.ofHours(1), 3, RETRY_DELAY,
                () -> {
                    if (attempts.incrementAndGet() <= 5) {
                        throw new IllegalStateException("slot is empty");
                    }
                    return new PeriodicMaterialReloader.Loaded<>("v1", "v1");
                },
                applied::add, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> !applied.isEmpty());
        } finally {
            reloader.close();
        }

        assertThat(applied).containsExactly("v1");
        assertThat(attempts).hasValueGreaterThan(5);
    }

    @Test
    void afterTheFirstLoadItSwitchesFromThePendingToTheNormalInterval() throws InterruptedException {
        var loads = new AtomicInteger();
        var applied = new ConcurrentLinkedQueue<String>();

        var reloader = PeriodicMaterialReloader.scheduleUntilFirstLoad("test", CHECK_INTERVAL, Duration.ofHours(1), 3, RETRY_DELAY,
                () -> {
                    loads.incrementAndGet();
                    return new PeriodicMaterialReloader.Loaded<>("v1", "v1");
                },
                applied::add, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> !applied.isEmpty());
            var loadsWhenServed = loads.get();
            Thread.sleep(CHECK_INTERVAL.toMillis() * 5);
            assertThat(loads).hasValue(loadsWhenServed);
        } finally {
            reloader.close();
        }
    }

    @Test
    void whilePendingAFailureIsLoggedWhenItsMessageDiffersFromThePreviousFailure() {
        var attempts = new AtomicInteger();
        var failures = List.of("slot is empty", "slot is empty", "no certificate yet", "no certificate yet", "slot is empty");
        var reloader = PeriodicMaterialReloader.<String>scheduleUntilFirstLoad("test", CHECK_INTERVAL, Duration.ofHours(1), 3,
                RETRY_DELAY,
                () -> {
                    throw new IllegalStateException(failures.get(Math.min(attempts.getAndIncrement(), failures.size() - 1)));
                },
                material -> { }, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> attempts.get() > failures.size());
        } finally {
            reloader.close();
        }
        verify(monitor, times(1)).warning("test: material not available yet: no certificate yet");
        verify(monitor, times(2)).warning("test: material not available yet: slot is empty");
    }

    @Test
    void whilePendingTheProgressLineIsLoggedEveryTwelfthPoll() {
        var attempts = new AtomicInteger();
        var reloader = PeriodicMaterialReloader.<String>scheduleUntilFirstLoad("test", CHECK_INTERVAL, Duration.ofHours(1), 3,
                RETRY_DELAY,
                () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("slot is empty");
                },
                material -> { }, monitor);
        try {
            await().atMost(Duration.ofSeconds(5)).until(() -> attempts.get() >= 12);
        } finally {
            reloader.close();
        }
        verify(monitor, timeout(1000)).info("test: still waiting for material after 12 polls: slot is empty");
        verify(monitor, times(1)).warning("test: material not available yet: slot is empty");
    }

    @Test
    void aLoaderReturningANullFingerprintIsRejectedWithoutFiringOnChange() {
        var attempts = new AtomicInteger();
        var applied = new ConcurrentLinkedQueue<String>();

        var reloader = PeriodicMaterialReloader.scheduleUntilFirstLoad("test", CHECK_INTERVAL, Duration.ofHours(1), 3, RETRY_DELAY,
                () -> {
                    attempts.incrementAndGet();
                    return new PeriodicMaterialReloader.Loaded<>("v1", null);
                },
                applied::add, monitor);
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> attempts.get() >= 4);
        } finally {
            reloader.close();
        }

        assertThat(applied).isEmpty();
        verify(monitor, times(1)).warning("test: material not available yet: Loaded material must carry a fingerprint");
    }

    @Test
    void nullFingerprintMaterialCannotBeConstructed() {
        assertThatThrownBy(() -> new PeriodicMaterialReloader.Loaded<>("v1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fingerprint");
    }

    @Test
    void aPendingFailureTheCallerAlreadyReportedIsNotLoggedAgainButADifferentOneIs() {
        var attempts = new AtomicInteger();
        var reloader = PeriodicMaterialReloader.<String>scheduleUntilFirstLoad("test", CHECK_INTERVAL, Duration.ofHours(1), 3,
                RETRY_DELAY,
                () -> {
                    throw new IllegalStateException(attempts.incrementAndGet() <= 3 ? "slot is empty" : "no certificate yet");
                },
                material -> { }, monitor, "slot is empty");
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> attempts.get() >= 5);
        } finally {
            reloader.close();
        }

        verify(monitor, never()).warning("test: material not available yet: slot is empty");
        verify(monitor, times(1)).warning("test: material not available yet: no certificate yet");
    }
}
