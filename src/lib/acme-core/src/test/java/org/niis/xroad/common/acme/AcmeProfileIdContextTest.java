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
package org.niis.xroad.common.acme;

import org.junit.jupiter.api.Test;
import org.shredzone.acme4j.exception.AcmeException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AcmeProfileIdConnection} reads the current profile id from a {@link ThreadLocal}; these tests document and
 * verify the two properties that make that safe: the value is visible only on the thread that set it, and it is
 * always cleared afterwards, including when the wrapped action fails.
 */
class AcmeProfileIdContextTest {

    @Test
    void nullProfileIdRunsActionWithoutScoping() throws AcmeException {
        assertThat(AcmeProfileIdContext.runWithProfileId(null, () -> {
            assertThat(AcmeProfileIdContext.current()).isEmpty();
            return "ran";
        })).isEqualTo("ran");
    }

    @Test
    void profileIdIsVisibleWhileActionRuns() throws AcmeException {
        AcmeProfileIdContext.runWithProfileId("xrd-ds-tls", () -> {
            assertThat(AcmeProfileIdContext.current()).contains("xrd-ds-tls");
            return null;
        });
    }

    @Test
    void profileIdIsClearedAfterActionCompletes() throws AcmeException {
        AcmeProfileIdContext.runWithProfileId("xrd-ds-tls", () -> "done");
        assertThat(AcmeProfileIdContext.current()).isEmpty();
    }

    @Test
    void profileIdIsClearedEvenWhenActionThrows() {
        assertThatThrownBy(() -> AcmeProfileIdContext.runWithProfileId("xrd-ds-tls", () -> {
            throw new AcmeException("boom");
        })).isInstanceOf(AcmeException.class);

        assertThat(AcmeProfileIdContext.current()).isEmpty();
    }

    @Test
    void profileIdDoesNotLeakAcrossThreads() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch bothEntered = new CountDownLatch(2);
            AtomicReference<String> seenByThreadA = new AtomicReference<>();
            AtomicReference<String> seenByThreadB = new AtomicReference<>();

            Future<?> taskA = executor.submit(() -> runAndObserve("profile-a", bothEntered, seenByThreadA));
            Future<?> taskB = executor.submit(() -> runAndObserve("profile-b", bothEntered, seenByThreadB));

            taskA.get(5, TimeUnit.SECONDS);
            taskB.get(5, TimeUnit.SECONDS);

            assertThat(seenByThreadA).hasValue("profile-a");
            assertThat(seenByThreadB).hasValue("profile-b");
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runAndObserve(String profileId, CountDownLatch bothEntered, AtomicReference<String> seen) {
        try {
            AcmeProfileIdContext.runWithProfileId(profileId, () -> {
                bothEntered.countDown();
                awaitUninterruptibly(bothEntered);
                seen.set(AcmeProfileIdContext.current().orElse(null));
                return null;
            });
        } catch (AcmeException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
