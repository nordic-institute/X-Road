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
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LastGoodSourceTest {

    private static final String KEY = "https://localhost:7001/issuer/statuslist/1";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(START);
    private final FakeFetcher fetcher = new FakeFetcher();
    private final IssuerDataStore store = mock(IssuerDataStore.class);
    private final AtomicReference<Result<Duration>> maximumAge = new AtomicReference<>();
    private final AtomicInteger maximumAgeReads = new AtomicInteger();
    private LastGoodSource<String> source;

    @BeforeEach
    void setUp() {
        maximumAge.set(Result.success(Duration.ofSeconds(60)));
        when(store.write(any(), any(), any())).thenReturn(StoreResult.success());
        when(store.read(any(), any())).thenReturn(StoreResult.notFound("no row"));
        source = new LastGoodSource<>(fetcher, clock, clock::millis, Runnable::run, CACHE_TIME, () -> {
            maximumAgeReads.incrementAndGet();
            return maximumAge.get();
        }, store, mock(Monitor.class));
    }

    @Test
    void shouldServeFreshCopyWithoutDownloadOrMaximumAgeRead() {
        assertThat(source.lookup(KEY).succeeded()).isTrue();
        var readsAfterStore = maximumAgeReads.get();
        clock.advance(CACHE_TIME.minusSeconds(1));

        var result = source.lookup(KEY);

        assertThat(result.succeeded()).isTrue();
        assertThat(fetcher.downloads.get()).isEqualTo(1);
        assertThat(maximumAgeReads.get()).isEqualTo(readsAfterStore);
    }

    @Test
    void shouldDownloadAgainAfterCacheTime() {
        source.lookup(KEY);
        clock.advance(CACHE_TIME);

        var result = source.lookup(KEY);

        assertThat(fetcher.downloads.get()).isEqualTo(2);
        assertThat(result.getContent().payload()).isEqualTo("payload-2");
    }

    @Test
    void shouldServeLastGoodCopyBeforeDeadlineMarginWhenIssuerIsDown() {
        source.lookup(KEY);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plus(Duration.ofSeconds(54)));

        var result = source.lookup(KEY);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldRefuseLastGoodCopyWithinMarginOfDeadline() {
        source.lookup(KEY);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plus(Duration.ofSeconds(55)));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNotUseStaleCopyWhenMaximumAgeIsZero() {
        maximumAge.set(Result.success(Duration.ZERO));
        source.lookup(KEY);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldCutCopyOffAtOnceWhenMaximumAgeIsLowered() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        source.lookup(KEY);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(30));
        assertThat(source.lookup(KEY).succeeded()).isTrue();

        maximumAge.set(Result.success(Duration.ofSeconds(10)));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNotExtendStoredCopyPastItsValidUntilWhenMaximumAgeIsRaised() {
        source.lookup(KEY);
        fetcher.down = true;
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        clock.set(START.plus(CACHE_TIME).plusSeconds(61));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldServeCopyAgainWhenMaximumAgeIsLoweredThenRaisedButNeverPastValidUntil() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        source.lookup(KEY);
        fetcher.down = true;
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
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(1));
        maximumAge.set(Result.failure("globalconf unreadable"));

        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNeverServeCopyPastItsOwnExpiryWithinMaximumAge() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        fetcher.ownExpiry = Optional.of(START.plusSeconds(90));
        source.lookup(KEY);
        fetcher.down = true;

        clock.set(START.plusSeconds(80));
        assertThat(source.lookup(KEY).succeeded()).isTrue();

        clock.set(START.plusSeconds(100));
        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldNotServeFreshCopyPastItsOwnExpiry() {
        fetcher.ownExpiry = Optional.of(START.plusSeconds(30));
        source.lookup(KEY);
        clock.set(START.plusSeconds(40));

        source.lookup(KEY);

        assertThat(fetcher.downloads.get()).isEqualTo(2);
    }

    @Test
    void shouldReturnRetryableFailureWhenNoCopyExistsAndIssuerIsDown() {
        fetcher.down = true;

        var result = source.lookup(KEY);

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldWriteEachDownloadWithItsFetchedAtAndValidUntil() {
        source.lookup(KEY);
        verify(store).write(IssuerDataKind.STATUSLIST, KEY,
                new StoredIssuerData("payload-1", START, START.plus(CACHE_TIME).plusSeconds(60)));
    }

    @Test
    void shouldNotReadStoreWhileMemoryCopyIsFresh() {
        source.lookup(KEY);
        clock.advance(CACHE_TIME.minusSeconds(1));
        source.lookup(KEY);
        verify(store, never()).read(any(), any());
    }

    @Test
    void shouldDownloadInsteadOfServingStoredCopyWhenMemoryIsEmpty() {
        storedRow("stored-1", START.minusSeconds(10), START.plusSeconds(100));
        var result = source.lookup(KEY);
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        verify(store, never()).read(any(), any());
    }

    @Test
    void shouldServeStoredCopyWhenMemoryIsEmptyAndDownloadFails() {
        storedRow("stored-1", START, START.plusSeconds(120));
        fetcher.down = true;
        clock.set(START.plusSeconds(30));
        var result = source.lookup(KEY);
        assertThat(result.getContent().payload()).isEqualTo("stored-1");
        assertThat(result.getContent().fetchedAt()).isEqualTo(START);
    }

    @Test
    void shouldRefuseStoredCopyPastItsValidUntilEvenWhenMaximumAgeWouldAllowIt() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        storedRow("stored-1", START, START.plusSeconds(50));
        fetcher.down = true;
        clock.set(START.plusSeconds(60));
        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldServeStoredCopyWhenItIsNewerThanMemoryCopy() {
        source.lookup(KEY);
        storedRow("stored-2", START.plusSeconds(30), START.plusSeconds(500));
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("stored-2");
    }

    @Test
    void shouldServeMemoryCopyWhenStoredCopyIsOlder() {
        source.lookup(KEY);
        storedRow("stored-0", START.minusSeconds(30), START.plusSeconds(500));
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldServeMemoryCopyWhenStoreReadFails() {
        source.lookup(KEY);
        when(store.read(any(), any())).thenReturn(StoreResult.generalError("database down"));
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldFailWithoutThrowingWhenStoreReadFailsAndMemoryIsEmpty() {
        when(store.read(any(), any())).thenReturn(StoreResult.generalError("database down"));
        fetcher.down = true;
        var result = source.lookup(KEY);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldServeFreshCopyWhenStoreWriteFails() {
        when(store.write(any(), any(), any())).thenReturn(StoreResult.generalError("database down"));
        var result = source.lookup(KEY);
        clock.advance(CACHE_TIME.minusSeconds(1));
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldIgnoreCorruptStoredCopyAndStopReadingItUntilNextDownload() {
        storedRow(FakeFetcher.CORRUPT, START, START.plusSeconds(500));
        fetcher.down = true;
        clock.set(START.plusSeconds(10));
        assertThat(source.lookup(KEY).failed()).isTrue();
        assertThat(source.lookup(KEY).failed()).isTrue();
        verify(store, times(1)).read(any(), any());
        fetcher.down = false;
        assertThat(source.lookup(KEY).succeeded()).isTrue();
        fetcher.down = true;
        clock.advance(CACHE_TIME);
        source.lookup(KEY);
        verify(store, times(2)).read(any(), any());
    }

    @Test
    void shouldReadStoredCopyAgainAfterRetryableParseFailure() {
        storedRow(FakeFetcher.CORRUPT, START, START.plusSeconds(500));
        fetcher.parseRetryable = true;
        fetcher.down = true;
        clock.set(START.plusSeconds(10));
        assertThat(source.lookup(KEY).failed()).isTrue();
        assertThat(source.lookup(KEY).failed()).isTrue();
        verify(store, times(2)).read(any(), any());
    }

    private void storedRow(String payload, Instant fetchedAt, Instant validUntil) {
        when(store.read(IssuerDataKind.STATUSLIST, KEY))
                .thenReturn(StoreResult.success(new StoredIssuerData(payload, fetchedAt, validUntil)));
    }
}
