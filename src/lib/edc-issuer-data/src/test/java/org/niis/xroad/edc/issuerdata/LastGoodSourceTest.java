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

import com.github.benmanes.caffeine.cache.Ticker;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LastGoodSourceTest {

    private static final String KEY = "https://localhost:7001/issuer/statuslist/1";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final String OTHER_KEY = "https://localhost:7001/issuer/statuslist/2";
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final Duration SHORT_WAIT = Duration.ofMillis(100);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String ISSUER_PATH = "https://ss0.example.org:7183/v1/DEV/COM/222/statuslist/";
    private static final long MAXIMUM_ENTRIES = 32;
    private static final Duration IDLE_EXPIRY = Duration.ofSeconds(120);

    private final MutableClock clock = new MutableClock(START);
    private final Ticker ticker = () -> Duration.between(START, clock.instant()).toNanos();
    private final FakeFetcher fetcher = new FakeFetcher();
    private final IssuerDataStore store = mock(IssuerDataStore.class);
    private final AtomicReference<Result<Duration>> maximumAge = new AtomicReference<>();
    private final AtomicInteger maximumAgeReads = new AtomicInteger();
    private final RecordingMonitor monitor = new RecordingMonitor();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private LastGoodSource<String> source;

    @BeforeEach
    void setUp() {
        maximumAge.set(Result.success(Duration.ofSeconds(60)));
        when(store.write(any(), any(), any())).thenReturn(StoreResult.success());
        when(store.read(any(), any())).thenReturn(StoreResult.notFound("no row"));
        source = newSource(Runnable::run, SHORT_WAIT);
    }

    @AfterEach
    void tearDown() {
        fetcher.gates.values().forEach(CountDownLatch::countDown);
        pool.shutdownNow();
    }

    private LastGoodSource<String> newSource(Executor executor, Duration refreshWait) {
        return newSource(executor, refreshWait, MAXIMUM_ENTRIES);
    }

    private LastGoodSource<String> newSource(Executor executor, Duration refreshWait, long maximumEntries) {
        return newSource(executor, refreshWait, maximumEntries, CACHE_TIME, IDLE_EXPIRY);
    }

    private LastGoodSource<String> newSource(Executor executor, Duration refreshWait, long maximumEntries,
                                             Duration cacheTime, Duration idleExpiry) {
        return new LastGoodSource<>(fetcher, clock, ticker, executor, cacheTime, maximumEntries, idleExpiry, refreshWait, () -> {
            maximumAgeReads.incrementAndGet();
            return maximumAge.get();
        }, store, monitor);
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
        clock.set(START.plusSeconds(70));
        assertThat(source.lookup(KEY).succeeded()).isTrue();
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

        for (var seconds = 200; seconds <= 600; seconds += 100) {
            clock.set(START.plusSeconds(seconds));
            assertThat(source.lookup(KEY).succeeded()).isTrue();
        }

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
    void shouldServeLastGoodCopyAndNeverStoreAnswerWhenIssuerRefusesWithinDeadline() {
        source.lookup(KEY);
        fetcher.refused = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));
        var result = source.lookup(KEY);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        verify(store, times(1)).write(any(), any(), any());
        assertThat(monitor.warnings()).anyMatch(warning -> warning.contains("could not be refreshed"));
    }

    @Test
    void shouldServeLastGoodCopyAndNeverStoreAnswerWhenIssuerAnswersInvalidListWithinDeadline() {
        source.lookup(KEY);
        fetcher.corruptAnswer = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));
        var result = source.lookup(KEY);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        verify(store, times(1)).write(any(), any(), any());
        assertThat(monitor.warnings()).anyMatch(warning -> warning.contains("could not be refreshed"));
    }

    @Test
    void shouldRefuseForGoodWhenIssuerRefusesPastDeadline() {
        source.lookup(KEY);
        fetcher.refused = true;
        clock.set(START.plus(CACHE_TIME).plus(Duration.ofSeconds(55)));
        var result = source.lookup(KEY);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldRefuseForGoodWhenIssuerAnswersInvalidListPastDeadline() {
        source.lookup(KEY);
        fetcher.corruptAnswer = true;
        clock.set(START.plus(CACHE_TIME).plus(Duration.ofSeconds(55)));
        var result = source.lookup(KEY);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
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
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
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
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        assertThat(source.lookup(KEY).failed()).isTrue();
        verify(store, times(2)).read(any(), any());
    }

    @Test
    void shouldNotRefreshOnEveryCheckDuringOutageAndResetBackoffAfterRecovery() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        source.lookup(KEY);
        fetcher.down = true;
        clock.advance(CACHE_TIME);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(2);

        for (var i = 0; i < 10; i++) {
            source.lookup(KEY);
        }
        assertThat(fetcher.attempts.get()).isEqualTo(2);

        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(3);
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(4);

        fetcher.down = false;
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-2");

        fetcher.down = true;
        clock.advance(CACHE_TIME);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(6);
        clock.advance(LastGoodSource.INITIAL_BACKOFF);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(7);
    }

    @Test
    void shouldFollowCopyStoredRowAndModeThroughOutageAndRecovery() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        verify(store, times(1)).write(any(), any(), any());

        fetcher.down = true;
        clock.advance(CACHE_TIME);
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);
        verify(store, times(1)).write(any(), any(), any());

        fetcher.down = false;
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-2");
        verify(store).write(IssuerDataKind.STATUSLIST, KEY, new StoredIssuerData("payload-2", clock.instant(),
                clock.instant().plus(CACHE_TIME).plusSeconds(600)));

        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-2");
        assertThat(fetcher.attempts.get()).isEqualTo(3);
        fetcher.down = true;
        clock.advance(CACHE_TIME);
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-2");
        assertThat(fetcher.attempts.get()).isEqualTo(4);
    }

    @Test
    void shouldFailWithoutThrowingWhenFetcherThrowsAndRetryAfterBackoff() {
        fetcher.throwing = true;

        var result = source.lookup(KEY);

        assertThat(result.failed()).isTrue();
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(1);
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldFailAndRefreshAgainAfterBackoffWhenStoreReadThrowsAfterFailedDownload() {
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        fetcher.down = true;
        when(store.read(any(), any())).thenThrow(new IllegalStateException("database broke"));
        var failed = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(failed.failed()).isTrue();
        var withinBackoff = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(withinBackoff.failed()).isTrue();
        assertThat(fetcher.attempts.get()).isEqualTo(1);
        fetcher.down = false;
        doReturn(StoreResult.notFound("no row")).when(store).read(any(), any());
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        var recovered = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(recovered.getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldNotDelaySecondKeyWhileFirstKeyRefreshIsSlow() {
        var concurrent = newSource(pool, SHORT_WAIT);
        fetcher.gates.put(KEY, new CountDownLatch(1));
        var slow = startCheck(concurrent, KEY);

        var other = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(OTHER_KEY));

        assertThat(other.succeeded()).isTrue();
        assertThat(slow.result()).isNotDone();
        fetcher.gates.get(KEY).countDown();
        assertThat(slow.result().join().succeeded()).isTrue();
    }

    @Test
    void shouldDownloadOnceForConcurrentChecksOfOneKey() {
        var concurrent = newSource(pool, SHORT_WAIT);
        fetcher.gates.put(KEY, new CountDownLatch(1));
        var checks = List.of(startCheck(concurrent, KEY), startCheck(concurrent, KEY), startCheck(concurrent, KEY));
        await().atMost(TIMEOUT).until(() -> checks.stream().allMatch(check -> isBlocked(check.thread())));

        fetcher.gates.get(KEY).countDown();

        checks.forEach(check -> assertThat(check.result().join().getContent().payload()).isEqualTo("payload-1"));
        assertThat(fetcher.attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldServeCopyAtOnceInOutageModeWhileBackgroundAttemptRuns() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        concurrent.lookup(KEY);
        fetcher.down = true;
        clock.advance(CACHE_TIME);
        concurrent.lookup(KEY);
        fetcher.down = false;
        clock.advance(LastGoodSource.MAXIMUM_BACKOFF);
        fetcher.gates.put(KEY, new CountDownLatch(1));

        var result = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));

        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        fetcher.gates.get(KEY).countDown();
        await().atMost(TIMEOUT).untilAsserted(
                () -> assertThat(concurrent.lookup(KEY).getContent().payload()).isEqualTo("payload-2"));
    }

    @Test
    void shouldServeLastGoodCopyWhenRefreshOutlastsWaitInNormalMode() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        var concurrent = newSource(pool, SHORT_WAIT);
        concurrent.lookup(KEY);
        clock.advance(CACHE_TIME);
        fetcher.gates.put(KEY, new CountDownLatch(1));

        var result = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));

        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        fetcher.gates.get(KEY).countDown();
        await().atMost(TIMEOUT).untilAsserted(
                () -> assertThat(concurrent.lookup(KEY).getContent().payload()).isEqualTo("payload-2"));
    }

    @Test
    void shouldServeStoredRowAfterWaitWhenMemoryIsEmptyAndRefreshIsSlow() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        var readers = new CopyOnWriteArrayList<Thread>();
        when(store.read(IssuerDataKind.STATUSLIST, KEY)).thenAnswer(_ -> {
            readers.add(Thread.currentThread());
            return StoreResult.success(new StoredIssuerData("stored-1", START.minusSeconds(100), START.plusSeconds(200)));
        });
        var concurrent = newSource(pool, SHORT_WAIT);
        fetcher.gates.put(KEY, new CountDownLatch(1));
        var checkThread = new AtomicReference<Thread>();

        var result = assertTimeoutPreemptively(TIMEOUT, () -> {
            checkThread.set(Thread.currentThread());
            return concurrent.lookup(KEY);
        });

        assertThat(result.getContent().payload()).isEqualTo("stored-1");
        assertThat(readers).isNotEmpty().doesNotContain(checkThread.get());
        verify(store, never()).write(any(), any(), any());
        fetcher.gates.get(KEY).countDown();
        verify(store, timeout(5000)).write(eq(IssuerDataKind.STATUSLIST), eq(KEY), any());
        assertThat(concurrent.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldWaitForRefreshWhenMemoryIsEmptyAndNoRowIsUsable() {
        var concurrent = newSource(pool, SHORT_WAIT);
        fetcher.gates.put(KEY, new CountDownLatch(1));
        var check = startCheck(concurrent, KEY);
        await().during(SHORT_WAIT.multipliedBy(3)).atMost(TIMEOUT).until(() -> !check.result().isDone());

        fetcher.gates.get(KEY).countDown();

        assertThat(check.result().join().getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldNeverHoldMoreKeysThanTheLimitAcrossManyDistinctKeys() {
        for (var i = 0; i < 10_000; i++) {
            assertThat(source.lookup(statusListKey(i)).succeeded()).isTrue();
            assertThat(source.cachedKeys()).isLessThanOrEqualTo(MAXIMUM_ENTRIES);
        }

        assertThat(fetcher.downloads.get()).isEqualTo(10_000);
    }

    @Test
    void shouldKeepCopyUntilIdleExpiryAndNotLonger() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        source.lookup(KEY);
        storedRow("stored-0", START.minusSeconds(30), START.plusSeconds(1000));
        fetcher.down = true;
        clock.set(START.plus(IDLE_EXPIRY).minusMillis(1));

        var beforeExpiry = source.lookup(KEY);
        clock.advance(IDLE_EXPIRY);
        var afterExpiry = source.lookup(KEY);

        assertThat(beforeExpiry.getContent().payload()).isEqualTo("payload-1");
        assertThat(afterExpiry.getContent().payload()).isEqualTo("stored-0");
    }

    @Test
    void shouldFollowPassedIdleExpiryAndNotTheCacheTime() {
        var longIdle = Duration.ofSeconds(300);
        var idleSource = newSource(Runnable::run, SHORT_WAIT, MAXIMUM_ENTRIES, CACHE_TIME, longIdle);
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        idleSource.lookup(KEY);
        storedRow("stored-0", START.minusSeconds(30), START.plusSeconds(1000));
        fetcher.down = true;
        clock.set(START.plus(longIdle).minusMillis(1));

        var beforeExpiry = idleSource.lookup(KEY);
        clock.advance(longIdle);
        var afterExpiry = idleSource.lookup(KEY);

        assertThat(beforeExpiry.getContent().payload()).isEqualTo("payload-1");
        assertThat(afterExpiry.getContent().payload()).isEqualTo("stored-0");
    }

    @Test
    void shouldServeLastGoodCopyAndHoldRetryDelayDuringOutageWhenCacheTimeIsZero() {
        var noCache = newSource(Runnable::run, SHORT_WAIT, MAXIMUM_ENTRIES, Duration.ZERO, IDLE_EXPIRY);
        maximumAge.set(Result.success(Duration.ofSeconds(60)));
        assertThat(noCache.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        fetcher.down = true;
        clock.advance(Duration.ofSeconds(2));

        var firstDuringOutage = noCache.lookup(KEY);
        var secondDuringOutage = noCache.lookup(KEY);

        assertThat(firstDuringOutage.getContent().payload()).isEqualTo("payload-1");
        assertThat(secondDuringOutage.getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldRestartIdleExpiryWithEachLookup() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        source.lookup(KEY);
        storedRow("stored-0", START.minusSeconds(30), START.plusSeconds(1000));
        fetcher.down = true;
        clock.advance(IDLE_EXPIRY.minusSeconds(1));
        source.lookup(KEY);
        clock.advance(IDLE_EXPIRY.minusSeconds(1));

        var result = source.lookup(KEY);

        assertThat(result.getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldFallBackToRowWithSameDeadlineWhenKeyIsDroppedDuringOutage() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        var validUntil = START.plus(CACHE_TIME).plusSeconds(600);
        assertThat(source.lookup(KEY).getContent().validUntil()).isEqualTo(validUntil);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME));
        assertThat(source.lookup(KEY).succeeded()).isTrue();
        storedRow("stored-1", START, validUntil);
        clearInvocations(store);
        clock.set(START.plus(CACHE_TIME).plus(IDLE_EXPIRY).plusSeconds(10));

        var afterDrop = source.lookup(KEY);

        assertThat(afterDrop.getContent().payload()).isEqualTo("stored-1");
        assertThat(afterDrop.getContent().fetchedAt()).isEqualTo(START);
        assertThat(afterDrop.getContent().validUntil()).isEqualTo(validUntil);
        verify(store, times(1)).read(IssuerDataKind.STATUSLIST, KEY);

        clock.set(validUntil.minus(LastGoodSource.SERVE_MARGIN).minusSeconds(1));
        assertThat(source.lookup(KEY).succeeded()).isTrue();
        clock.set(validUntil.minus(LastGoodSource.SERVE_MARGIN));
        assertThat(source.lookup(KEY).failed()).isTrue();
    }

    @Test
    void shouldStartAtMostOneMoreDownloadWhenKeyIsDroppedWithRefreshInFlight() {
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        fetcher.gates.put(KEY, new CountDownLatch(1));
        var first = startCheck(concurrent, KEY);
        await().atMost(TIMEOUT).until(() -> fetcher.attempts.get() == 1 && isBlocked(first.thread()));
        clock.advance(IDLE_EXPIRY);
        var afterDrop = List.of(startCheck(concurrent, KEY), startCheck(concurrent, KEY), startCheck(concurrent, KEY));
        await().atMost(TIMEOUT).until(() -> fetcher.attempts.get() == 2
                && afterDrop.stream().allMatch(check -> isBlocked(check.thread())));

        fetcher.gates.get(KEY).countDown();

        assertThat(first.result().join().succeeded()).isTrue();
        afterDrop.forEach(check -> assertThat(check.result().join().succeeded()).isTrue());
        assertThat(concurrent.lookup(KEY).succeeded()).isTrue();
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldKeepHotFailingKeyWhileColdKeysFloodTheCache() {
        var small = newSource(Runnable::run, SHORT_WAIT, 8);
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        small.lookup(KEY);
        fetcher.down = true;
        clock.advance(CACHE_TIME);

        for (var i = 0; i < 500; i++) {
            small.lookup(statusListKey(i));
            assertThat(small.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        }

        verify(store, times(1)).read(IssuerDataKind.STATUSLIST, KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(1 + 500 + 1);
    }

    private static String statusListKey(int index) {
        return ISSUER_PATH + index;
    }

    private record Check(Thread thread, CompletableFuture<Result<LastGoodCopy<String>>> result) {
    }

    private static Check startCheck(LastGoodSource<String> source, String key) {
        var result = new CompletableFuture<Result<LastGoodCopy<String>>>();
        var thread = Thread.ofPlatform().daemon().start(() -> result.complete(source.lookup(key)));
        return new Check(thread, result);
    }

    private static boolean isBlocked(Thread thread) {
        return thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING;
    }

    private void storedRow(String payload, Instant fetchedAt, Instant validUntil) {
        when(store.read(IssuerDataKind.STATUSLIST, KEY))
                .thenReturn(StoreResult.success(new StoredIssuerData(payload, fetchedAt, validUntil)));
    }
}
