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
package org.niis.xroad.ds.issuance.identity;

import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BoundIssuerIdentityHolderTest {

    private static final Duration CHECK_INTERVAL = Duration.ofMillis(20);

    private final Monitor monitor = mock();

    @Test
    void stopsPollingAfterTheFirstBoundLoadAndALaterReaderCallChangesNothing() {
        var bound = identity("DID-STOP");
        var reads = new AtomicInteger();
        BoundIssuerIdentityReader reader = () -> {
            reads.incrementAndGet();
            return Optional.of(bound);
        };
        var holder = new BoundIssuerIdentityHolder(() -> reader, monitor, CHECK_INTERVAL);

        holder.start();
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> holder.identity().isPresent());
            var readsWhenBound = reads.get();

            Thread.sleep(CHECK_INTERVAL.toMillis() * 5);

            assertThat(reads).hasValue(readsWhenBound);
            assertThat(holder.identity()).contains(bound);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            holder.close();
        }
    }

    @Test
    void sequenceUnboundThenBoundThenAFurtherPollAttemptLeavesTheBoundIdentityUnchanged() {
        var bound = identity("DID-SEQUENCE");
        var reads = new AtomicInteger();
        BoundIssuerIdentityReader reader = () -> reads.incrementAndGet() <= 2 ? Optional.empty() : Optional.of(bound);
        var holder = new BoundIssuerIdentityHolder(() -> reader, monitor, CHECK_INTERVAL);

        holder.start();
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> holder.identity().isPresent());
            assertThat(reads.get()).isGreaterThanOrEqualTo(3);
            var readsWhenBound = reads.get();

            Thread.sleep(CHECK_INTERVAL.toMillis() * 5);

            assertThat(holder.identity()).contains(bound);
            assertThat(reads).hasValue(readsWhenBound);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            holder.close();
        }
    }

    @Test
    void aReaderThatFailsToConstructIsTreatedAsUnboundAndRetriedAtThePendingInterval() {
        var bound = identity("DID-CONSTRUCTION-FAILURE");
        var attempts = new AtomicInteger();
        var holder = new BoundIssuerIdentityHolder(() -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new IllegalStateException("the Central Server database is unreachable");
            }
            return () -> Optional.of(bound);
        }, monitor, CHECK_INTERVAL);

        holder.start();
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> holder.identity().isPresent());
            assertThat(holder.identity()).contains(bound);
            assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        } finally {
            holder.close();
        }
    }

    @Test
    void aReaderThatFailsToReadIsTreatedAsUnboundAndRetriedAtThePendingInterval() {
        var bound = identity("DID-READ-FAILURE");
        var attempts = new AtomicInteger();
        BoundIssuerIdentityReader reader = () -> {
            if (attempts.incrementAndGet() <= 2) {
                throw new IllegalStateException("could not read the bound issuer identity");
            }
            return Optional.of(bound);
        };
        var holder = new BoundIssuerIdentityHolder(() -> reader, monitor, CHECK_INTERVAL);

        holder.start();
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> holder.identity().isPresent());
            assertThat(holder.identity()).contains(bound);
        } finally {
            holder.close();
        }
    }

    /**
     * {@link org.niis.xroad.edc.reload.PeriodicMaterialReloader#scheduleUntilFirstLoad} logs a pending-cycle
     * failure only when its message differs from the previous one; a reader that is still unbound on every
     * poll reports the same message each time, so only the first of several polls should log a warning.
     */
    @Test
    void repeatedUnboundPollsLogOnePendingFailureWarningNotOnePerPoll() throws InterruptedException {
        var polls = new CountDownLatch(5);
        var recordingMonitor = new RecordingMonitor();
        BoundIssuerIdentityReader reader = () -> {
            polls.countDown();
            return Optional.empty();
        };
        var holder = new BoundIssuerIdentityHolder(() -> reader, recordingMonitor, Duration.ofMillis(5));

        holder.start();
        try {
            assertThat(polls.await(2, TimeUnit.SECONDS)).isTrue();

            var pendingFailureWarnings = recordingMonitor.warnings.stream()
                    .filter(message -> message.contains("material not available yet"))
                    .toList();
            assertThat(pendingFailureWarnings).hasSize(1);
        } finally {
            holder.close();
        }
    }

    private static final class RecordingMonitor implements Monitor {
        private final List<String> warnings = new CopyOnWriteArrayList<>();

        @Override
        public void warning(Supplier<String> supplier, Throwable... errors) {
            warnings.add(supplier.get());
        }
    }

    /**
     * {@link BoundIssuerIdentityHolder#start()} closes the reloader it just scheduled only after the
     * assignment that stores it; the reloader's own completion callback also closes it, through that same
     * late-bound reference, from the scheduler's own thread. The first read fails, so {@link
     * BoundIssuerIdentityHolder#start()} falls back to scheduling a reloader instead of resolving
     * synchronously; a zero pending interval then lets the scheduled cycle run as soon as possible, maximising
     * the chance the two race, so a design that only worked by virtue of the production default (several
     * seconds) would show up here as a reader called more than twice.
     */
    @Test
    void closingFromWithinTheOnChangeCallbackIsSafeEvenWhenItRacesTheFieldAssignment() {
        var bound = identity("DID-RACE");
        var reads = new AtomicInteger();
        BoundIssuerIdentityReader reader = () -> reads.incrementAndGet() == 1 ? Optional.empty() : Optional.of(bound);
        var holder = new BoundIssuerIdentityHolder(() -> reader, monitor, Duration.ZERO);

        holder.start();
        try {
            await().atMost(Duration.ofSeconds(2)).until(() -> holder.identity().isPresent());
            var readsWhenBound = reads.get();

            Thread.sleep(50);

            assertThat(reads).hasValue(readsWhenBound);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            holder.close();
        }
    }

    /**
     * A reader built while the holder is already closing must not be kept: it is closed immediately once its
     * construction finishes, instead of leaking a connection pool that nothing closes afterwards.
     */
    @Test
    void aReaderBuiltWhileClosingIsClosedRightAwayAndNotKept() throws InterruptedException {
        var factoryEntered = new CountDownLatch(1);
        var releaseFactory = new CountDownLatch(1);
        var readerClosed = new CountDownLatch(1);
        var holder = new BoundIssuerIdentityHolder(() -> {
            factoryEntered.countDown();
            try {
                releaseFactory.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new ClosingReader(readerClosed);
        }, monitor, CHECK_INTERVAL);

        var starter = new Thread(holder::start, "holder-start-under-test");
        starter.start();
        try {
            assertThat(factoryEntered.await(2, TimeUnit.SECONDS)).isTrue();

            holder.close();
            releaseFactory.countDown();

            assertThat(readerClosed.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(holder.identity()).isEmpty();
        } finally {
            starter.join(2000);
            holder.close();
        }
    }

    private record ClosingReader(CountDownLatch closed) implements BoundIssuerIdentityReader, AutoCloseable {
        @Override
        public Optional<BoundIssuerIdentity> read() {
            return Optional.empty();
        }

        @Override
        public void close() {
            closed.countDown();
        }
    }

    @Test
    void identityIsEmptyBeforeAnyBindingLoads() {
        var holder = new BoundIssuerIdentityHolder(() -> Optional::empty, monitor, CHECK_INTERVAL);

        assertThat(holder.identity()).isEmpty();

        holder.close();
    }

    @Test
    void theHolderReturnsTheBoundIdentityAsSoonAsStartReturnsWhenABindingAlreadyExists() {
        var bound = identity("DID-SYNCHRONOUS");
        var holder = new BoundIssuerIdentityHolder(() -> () -> Optional.of(bound), monitor, CHECK_INTERVAL);

        holder.start();
        try {
            assertThat(holder.identity()).contains(bound);
        } finally {
            holder.close();
        }
    }

    @Test
    void startLogsAndReturnsUnboundWithoutThrowingWhenTheDatabaseIsUnreachable() {
        var holder = new BoundIssuerIdentityHolder(() -> {
            throw new IllegalStateException("the Central Server database is unreachable");
        }, monitor, CHECK_INTERVAL);

        try {
            assertThat(holder.identity()).isEmpty();
            holder.start();
            assertThat(holder.identity()).isEmpty();
            verify(monitor).warning(anyString(), any(Throwable.class));
        } finally {
            holder.close();
        }
    }

    private static BoundIssuerIdentity identity(String did) {
        return new BoundIssuerIdentity(did, "issuer.example", 7183, "https://issuer.example/issuance", "https://issuer.example/statuslist");
    }
}
