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
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InitialMaterialWaitTest {

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);
    private static final PeriodicMaterialReloader.Loaded<String> MATERIAL = new PeriodicMaterialReloader.Loaded<>("v1", "v1");

    private final RecordingMonitor monitor = new RecordingMonitor();
    private final List<Duration> sleeps = new ArrayList<>();

    @Test
    void returnsTheMaterialWithoutSleepingWhenTheFirstLoadSucceeds() {
        var wait = new InitialMaterialWait<>("test", () -> MATERIAL, POLL_INTERVAL, monitor, sleeps::add);

        assertThat(wait.awaitMaterial()).isEqualTo(MATERIAL);
        assertThat(sleeps).isEmpty();
        assertThat(monitor.warnings).isEmpty();
    }

    @Test
    void pollsUntilTheLoaderSucceeds() {
        var wait = new InitialMaterialWait<>("test", loaderThat(failing("slot empty"), failing("slot empty"), () -> MATERIAL),
                POLL_INTERVAL, monitor, sleeps::add);

        assertThat(wait.awaitMaterial()).isEqualTo(MATERIAL);
        assertThat(sleeps).containsExactly(POLL_INTERVAL, POLL_INTERVAL);
        assertThat(monitor.infos).anySatisfy(info -> assertThat(info).contains("available after 3 attempts"));
    }

    @Test
    void logsEachDistinctFailureOnceWhileItPersists() {
        var wait = new InitialMaterialWait<>("test",
                loaderThat(failing("slot empty"), failing("slot empty"), failing("vault unreachable"), () -> MATERIAL),
                POLL_INTERVAL, monitor, sleeps::add);

        wait.awaitMaterial();

        assertThat(monitor.warnings).hasSize(2);
        assertThat(monitor.warnings.get(0)).contains("slot empty");
        assertThat(monitor.warnings.get(1)).contains("vault unreachable");
    }

    @Test
    void anInterruptEndsTheWait() {
        var wait = new InitialMaterialWait<>("test", loaderThat(failing("slot empty")), POLL_INTERVAL, monitor, duration -> {
            throw new InterruptedException();
        });

        try {
            assertThatThrownBy(wait::awaitMaterial)
                    .isInstanceOf(EdcException.class)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @SafeVarargs
    private static PeriodicMaterialReloader.MaterialLoader<String> loaderThat(
            Supplier<PeriodicMaterialReloader.Loaded<String>>... outcomes) {
        Iterator<Supplier<PeriodicMaterialReloader.Loaded<String>>> remaining = List.of(outcomes).iterator();
        return () -> remaining.next().get();
    }

    private static Supplier<PeriodicMaterialReloader.Loaded<String>> failing(String message) {
        return () -> {
            throw new IllegalStateException(message);
        };
    }

    private static final class RecordingMonitor implements Monitor {
        private final List<String> warnings = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();

        @Override
        public void warning(String message, Throwable... errors) {
            warnings.add(message);
        }

        @Override
        public void info(String message, Throwable... errors) {
            infos.add(message);
        }
    }
}
