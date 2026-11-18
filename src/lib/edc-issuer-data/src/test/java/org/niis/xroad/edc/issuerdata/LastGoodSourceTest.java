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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LastGoodSourceTest {

    private static final String KEY = "https://localhost:7001/issuer/statuslist/1";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(START);
    private final FakeKind kind = new FakeKind();
    private final AtomicReference<Result<Duration>> maximumAge = new AtomicReference<>();
    private final AtomicInteger maximumAgeReads = new AtomicInteger();
    private LastGoodSource<String> source;

    @BeforeEach
    void setUp() {
        maximumAge.set(Result.success(Duration.ofSeconds(60)));
        source = new LastGoodSource<>(kind, clock, clock::millis, Runnable::run, CACHE_TIME, () -> {
            maximumAgeReads.incrementAndGet();
            return maximumAge.get();
        }, mock(Monitor.class));
    }

    @Test
    void shouldServeFreshCopyWithoutDownloadOrMaximumAgeRead() {
        assertThat(source.lookup(KEY).succeeded()).isTrue();
        var readsAfterStore = maximumAgeReads.get();
        clock.advance(CACHE_TIME.minusSeconds(1));

        var result = source.lookup(KEY);

        assertThat(result.succeeded()).isTrue();
        assertThat(kind.downloads.get()).isEqualTo(1);
        assertThat(maximumAgeReads.get()).isEqualTo(readsAfterStore);
    }

    @Test
    void shouldDownloadAgainAfterCacheTime() {
        source.lookup(KEY);
        clock.advance(CACHE_TIME);

        var result = source.lookup(KEY);

        assertThat(kind.downloads.get()).isEqualTo(2);
        assertThat(result.getContent().payload()).isEqualTo("payload-2");
    }

    @Test
    void shouldServeLastGoodCopyBeforeDeadlineMarginWhenIssuerIsDown() {
        source.lookup(KEY);
        kind.down = true;
        clock.set(START.plus(CACHE_TIME).plus(Duration.ofSeconds(54)));

        var result = source.lookup(KEY);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldRefuseLastGoodCopyWithinMarginOfDeadline() {
        source.lookup(KEY);
        kind.down = true;
        clock.set(START.plus(CACHE_TIME).plus(Duration.ofSeconds(55)));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNotUseStaleCopyWhenMaximumAgeIsZero() {
        maximumAge.set(Result.success(Duration.ZERO));
        source.lookup(KEY);
        kind.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldCutCopyOffAtOnceWhenMaximumAgeIsLowered() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        source.lookup(KEY);
        kind.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(30));
        assertThat(source.lookup(KEY).succeeded()).isTrue();

        maximumAge.set(Result.success(Duration.ofSeconds(10)));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNotExtendStoredCopyPastItsValidUntilWhenMaximumAgeIsRaised() {
        source.lookup(KEY);
        kind.down = true;
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        clock.set(START.plus(CACHE_TIME).plusSeconds(61));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldServeCopyAgainWhenMaximumAgeIsLoweredThenRaisedButNeverPastValidUntil() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        source.lookup(KEY);
        kind.down = true;
        clock.set(START.plusSeconds(100));

        maximumAge.set(Result.success(Duration.ZERO));
        assertThat(source.lookup(KEY).failed()).isTrue();

        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        assertThat(source.lookup(KEY).succeeded()).isTrue();

        clock.set(START.plus(CACHE_TIME).plusSeconds(600));
        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldStoreValidUntilAsFetchedAtPlusCacheTimeWhenMaximumAgeIsUnreadableAtWrite() {
        maximumAge.set(Result.failure("globalconf unreadable"));

        var result = source.lookup(KEY);

        assertThat(result.getContent().validUntil()).isEqualTo(START.plus(CACHE_TIME));
    }

    @Test
    void shouldHaveNoUsableCopyWhenMaximumAgeIsUnreadableAtCheck() {
        source.lookup(KEY);
        kind.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));
        maximumAge.set(Result.failure("globalconf unreadable"));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNeverServeCopyPastItsOwnExpiryWithinMaximumAge() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        kind.ownExpiry = Optional.of(START.plusSeconds(90));
        source.lookup(KEY);
        kind.down = true;

        clock.set(START.plusSeconds(80));
        assertThat(source.lookup(KEY).succeeded()).isTrue();

        clock.set(START.plusSeconds(100));
        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNotServeFreshCopyPastItsOwnExpiry() {
        kind.ownExpiry = Optional.of(START.plusSeconds(30));
        source.lookup(KEY);
        clock.set(START.plusSeconds(40));

        source.lookup(KEY);

        assertThat(kind.downloads.get()).isEqualTo(2);
    }

    @Test
    void shouldReturnRetryableFailureWhenNoCopyExistsAndIssuerIsDown() {
        kind.down = true;

        var result = source.lookup(KEY);

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    private static final class FakeKind implements IssuerDataKind<String> {

        private final AtomicInteger downloads = new AtomicInteger();
        private volatile boolean down;
        private volatile Optional<Instant> ownExpiry = Optional.empty();

        @Override
        public Result<String> download(String key) {
            if (down) {
                return Result.retryableFailure("issuer down");
            }
            return Result.success("payload-" + downloads.incrementAndGet());
        }

        @Override
        public Result<String> parse(String key, String payload) {
            return Result.success(payload);
        }

        @Override
        public Optional<Instant> ownExpiry(String parsed) {
            return ownExpiry;
        }
    }
}
