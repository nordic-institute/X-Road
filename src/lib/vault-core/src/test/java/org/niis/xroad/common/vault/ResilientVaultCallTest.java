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
package org.niis.xroad.common.vault;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.resilience4j.core.IntervalFunction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Timeout(10)
class ResilientVaultCallTest {

    private static final IntervalFunction FAST_BACKOFF = IntervalFunction.of(Duration.ofMillis(1));
    private static final Predicate<Throwable> RETRY_ILLEGAL_STATE = IllegalStateException.class::isInstance;
    private static final Logger CALL_LOGGER = (Logger) LoggerFactory.getLogger(ResilientVaultCall.class);
    private static final ListAppender<ILoggingEvent> LOG_EVENTS = new ListAppender<>();

    private final FakeClock clock = new FakeClock();

    @BeforeAll
    static void captureLogs() {
        LOG_EVENTS.start();
        CALL_LOGGER.addAppender(LOG_EVENTS);
    }

    @AfterAll
    static void releaseLogs() {
        CALL_LOGGER.detachAppender(LOG_EVENTS);
        LOG_EVENTS.stop();
    }

    @Test
    void shouldSucceedWhenTheFailureEndsWithinTheBudget() {
        var call = newCall(Duration.ofSeconds(30));
        var attempts = new AtomicInteger();
        var start = clock.instant();

        var result = call.execute("read", "path/failover", () -> {
            attempts.incrementAndGet();
            clock.advance(Duration.ofSeconds(5));
            if (Duration.between(start, clock.instant()).compareTo(Duration.ofSeconds(20)) <= 0) {
                throw new IllegalStateException("leader gone");
            }
            return "value";
        });

        assertThat(result).isEqualTo("value");
        assertThat(attempts).hasValue(5);
    }

    @Test
    void shouldStopRetryingOnceTheBudgetPassesAndReportTheLastFailure() {
        var call = newCall(Duration.ofSeconds(30));
        var attempts = new AtomicInteger();
        var lastFailure = new AtomicReference<IllegalStateException>();

        assertThatThrownBy(() -> call.execute("write", "path/outage", () -> {
            attempts.incrementAndGet();
            clock.advance(Duration.ofSeconds(10));
            lastFailure.set(new IllegalStateException("still down"));
            throw lastFailure.get();
        })).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(ErrorCode.SECRET_STORE_UNAVAILABLE)).isTrue();
            assertThat(e.getDetails()).contains("write", "path/outage", "after 3 attempt(s)", "retry budget 30s",
                    "still down");
            assertThat(e.getCause()).isSameAs(lastFailure.get());
        });

        assertThat(attempts).hasValue(3);
    }

    @Test
    void shouldLogOneWarnLinePerFailedAttempt() {
        var call = newCall(Duration.ofSeconds(30));
        var path = "path/logged";

        assertThatThrownBy(() -> call.execute("list", path, () -> {
            clock.advance(Duration.ofSeconds(10));
            throw new IllegalStateException("down");
        })).isInstanceOf(XrdRuntimeException.class);

        var warnings = LOG_EVENTS.list.stream()
                .filter(event -> event.getFormattedMessage().contains(path))
                .toList();
        assertThat(warnings).hasSize(3).allSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
    }

    @Test
    void shouldMakeASingleAttemptWithAZeroBudgetAndStillWrapARetryableFailure() {
        var call = newCall(Duration.ZERO);
        var attempts = new AtomicInteger();

        assertThatThrownBy(() -> call.execute("read", "path/zero", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("down");
        })).isInstanceOfSatisfying(XrdRuntimeException.class,
                e -> assertThat(e.isCausedBy(ErrorCode.SECRET_STORE_UNAVAILABLE)).isTrue());

        assertThat(attempts).hasValue(1);
    }

    @Test
    void shouldRethrowTheLastFailureAndKeepTheInterruptFlagWhenInterruptedBetweenAttempts() {
        var call = newCall(Duration.ofSeconds(30));
        var attempts = new AtomicInteger();
        var failure = new IllegalStateException("leader gone");

        try {
            assertThatThrownBy(() -> call.execute("read", "path/interrupted", () -> {
                attempts.incrementAndGet();
                Thread.currentThread().interrupt();
                throw failure;
            })).isSameAs(failure);

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(attempts).hasValue(1);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldRunARunnableThroughTheSameRetryLoop() {
        var call = newCall(Duration.ofSeconds(30));
        var attempts = new AtomicInteger();

        call.run("write", "path/runnable", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("leader gone");
            }
        });

        assertThat(attempts).hasValue(3);
    }

    @Test
    void shouldRethrowANonRetryableFailureUnchangedAfterOneAttempt() {
        var call = newCall(Duration.ofSeconds(30));
        var attempts = new AtomicInteger();
        var failure = new IllegalArgumentException("forbidden");

        assertThatThrownBy(() -> call.execute("read", "path/denied", () -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(attempts).hasValue(1);
    }

    private ResilientVaultCall newCall(Duration budget) {
        return new ResilientVaultCall(RETRY_ILLEGAL_STATE, budget, clock, FAST_BACKOFF);
    }

    private static final class FakeClock extends Clock {

        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

        void advance(Duration duration) {
            now.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}
