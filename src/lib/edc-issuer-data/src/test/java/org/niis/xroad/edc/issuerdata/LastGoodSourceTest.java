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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.niis.xroad.test.MutableClock;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.edc.issuerdata.TestSettings.DELAYS;
import static org.niis.xroad.edc.issuerdata.TestSettings.DOWNLOAD_EXPIRE_AFTER;
import static org.niis.xroad.edc.issuerdata.TestSettings.settings;

class LastGoodSourceTest {

    private static final String KEY = "https://localhost:7001/issuer/statuslist/1";
    private static final Instant START = Instant.parse("2026-11-18T10:00:00Z");
    private static final String OTHER_KEY = "https://localhost:7001/issuer/statuslist/2";
    private static final Duration CACHE_TIME = Duration.ofSeconds(60);
    private static final Duration SHORT_WAIT = Duration.ofMillis(100);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String ISSUER_PATH = "https://ss0.example.org:7183/v1/DEV/COM/222/statuslist/";
    private static final long MAXIMUM_ENTRIES = 32;
    private static final long TRACKED_DOWNLOADS_LIMIT = 4;
    private static final Duration FIRST_DELAY = DELAYS.getFirst();

    private final MutableClock clock = new MutableClock(START);
    private final Ticker ticker = () -> Duration.between(START, clock.instant()).toNanos();
    private final FakeFetcher fetcher = new FakeFetcher(clock);
    private final InMemoryIssuerDataStore store = new InMemoryIssuerDataStore();
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final AtomicReference<Result<Duration>> maximumAge = new AtomicReference<>();
    private final AtomicInteger maximumAgeReads = new AtomicInteger();
    private final RecordingMonitor monitor = new RecordingMonitor();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean maximumAgeThrows;
    private LastGoodSource<String> source;

    @BeforeEach
    void setUp() {
        maximumAge.set(Result.success(Duration.ofSeconds(60)));
        source = newSource(Runnable::run, SHORT_WAIT);
    }

    @AfterEach
    void tearDown() {
        fetcher.gates.values().forEach(CountDownLatch::countDown);
        pool.shutdownNow();
    }

    private LastGoodSource<String> newSource(Executor executor, Duration downloadWait) {
        return newSource(executor, downloadWait, MAXIMUM_ENTRIES);
    }

    private LastGoodSource<String> newSource(Executor executor, Duration downloadWait, long maximumEntries) {
        return newSource(executor, downloadWait, maximumEntries, CACHE_TIME);
    }

    private LastGoodSource<String> newSource(Executor executor, Duration downloadWait, long maximumEntries,
                                             Duration cacheTime) {
        return newSource(executor, cacheTime, settings(maximumEntries, downloadWait));
    }

    private LastGoodSource<String> newSource(Executor executor, Duration cacheTime, IssuerDataSettings settings) {
        return new LastGoodSource<>(fetcher, clock, ticker, executor, scheduler, cacheTime, settings, () -> {
            maximumAgeReads.incrementAndGet();
            if (maximumAgeThrows) {
                throw new IllegalStateException("globalconf broke");
            }
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
    void shouldServeStoredRowUntilItsDeadlineWhenIssuerIsDown() {
        source.lookup(KEY);
        fetcher.down = true;
        var deadline = START.plus(CACHE_TIME).plusSeconds(60);

        clock.set(deadline.minusSeconds(1));
        var beforeDeadline = source.lookup(KEY);
        clock.set(deadline);
        var atDeadline = source.lookup(KEY);

        assertThat(beforeDeadline.getContent().payload()).isEqualTo("payload-1");
        assertThat(atDeadline.failed()).isTrue();
    }

    @Test
    void shouldRefuseStoredRowWithinTheGivenMarginOfItsDeadline() {
        source.lookup(KEY);
        fetcher.down = true;
        var margin = Duration.ofSeconds(5);
        var deadline = START.plus(CACHE_TIME).plusSeconds(60);

        clock.set(deadline.minus(margin).minusSeconds(1));
        var beforeMargin = source.lookup(KEY, margin);
        clock.set(deadline.minus(margin));
        var withinMargin = source.lookup(KEY, margin);

        assertThat(beforeMargin.getContent().payload()).isEqualTo("payload-1");
        assertThat(withinMargin.failed()).isTrue();
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
        assertThat(store.writes.get()).isEqualTo(1);
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
        assertThat(store.writes.get()).isEqualTo(1);
        assertThat(monitor.warnings()).anyMatch(warning -> warning.contains("could not be refreshed"));
    }

    @Test
    void shouldRefuseForGoodWhenIssuerRefusesPastDeadline() {
        source.lookup(KEY);
        fetcher.refused = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(60));
        var result = source.lookup(KEY);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldRefuseForGoodWhenIssuerAnswersInvalidListPastDeadline() {
        source.lookup(KEY);
        fetcher.corruptAnswer = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(60));
        var result = source.lookup(KEY);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isFalse();
    }

    @Test
    void shouldWriteEachDownloadWithItsFetchedAtAndValidUntil() {
        source.lookup(KEY);

        assertThat(store.row(IssuerDataKind.STATUSLIST, KEY))
                .isEqualTo(new StoredIssuerData("payload-1", START, START.plus(CACHE_TIME).plusSeconds(60)));
    }

    @Test
    void shouldNotReadStoreWhileMemoryCopyIsFresh() {
        source.lookup(KEY);
        clock.advance(CACHE_TIME.minusSeconds(1));
        source.lookup(KEY);
        assertThat(store.reads.get()).isZero();
    }

    @Test
    void shouldDownloadInsteadOfServingStoredCopyWhenMemoryIsEmpty() {
        storedRow("stored-1", START.minusSeconds(10), START.plusSeconds(100));
        var result = source.lookup(KEY);
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        assertThat(store.reads.get()).isZero();
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
    void shouldNotServeStoredCopyPastItsValidUntilWhenCacheTimeOutlastsIt() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        storedRow("stored-1", START, START.plusSeconds(40));
        fetcher.down = true;
        clock.set(START.plusSeconds(30));
        var beforeValidUntil = source.lookup(KEY);
        clock.set(START.plusSeconds(50));
        var afterValidUntil = source.lookup(KEY);
        assertThat(beforeValidUntil.getContent().payload()).isEqualTo("stored-1");
        assertThat(afterValidUntil.failed()).isTrue();
    }

    @Test
    void shouldReadStoredRowOnEveryOutageCheckAndServeANewerRowFromAnotherNode() {
        source.lookup(KEY);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(store.reads.get()).isEqualTo(1);

        storedRow("stored-2", START.plusSeconds(30), START.plusSeconds(500));

        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("stored-2");
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("stored-2");
        assertThat(store.reads.get()).isEqualTo(3);
    }

    @Test
    void shouldFailRetryablyWhenStoreReadFailsAfterCopyLeftMemory() {
        source.lookup(KEY);
        store.failingReads = true;
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));

        var result = source.lookup(KEY);

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldFailRetryablyWithoutThrowingWhenStoreReadThrowsAfterCopyLeftMemory() {
        source.lookup(KEY);
        store.throwingReads = true;
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));

        var result = source.lookup(KEY);

        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldFailWithoutThrowingWhenStoreReadFailsAndMemoryIsEmpty() {
        store.failingReads = true;
        fetcher.down = true;
        var result = source.lookup(KEY);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @ParameterizedTest(name = "throwing {0}")
    @ValueSource(booleans = {false, true})
    void shouldServeFreshCopyWhenStoreWriteFailsOrThrows(boolean throwing) {
        store.failingWrites = !throwing;
        store.throwingWrites = throwing;
        var result = source.lookup(KEY);
        clock.advance(CACHE_TIME.minusSeconds(1));
        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.downloads.get()).isEqualTo(1);
    }

    @Test
    void shouldNeverServeUnparsableStoredRowWarnOnceAndServeLaterRowFromAnotherNode() {
        storedRow(FakeFetcher.CORRUPT, START, START.plusSeconds(500));
        fetcher.down = true;
        clock.set(START.plusSeconds(10));

        var first = source.lookup(KEY);
        var second = source.lookup(KEY);

        assertThat(first.failed()).isTrue();
        assertThat(first.getFailureDetail()).contains("issuer down");
        assertThat(second.failed()).isTrue();
        assertThat(unusableRowWarnings()).isEqualTo(1);
        assertThat(store.reads.get()).isEqualTo(2);

        storedRow("stored-good", START.plusSeconds(5), START.plusSeconds(500));

        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("stored-good");
    }

    @Test
    void shouldReadAndWarnAgainOnEveryCheckWhenStoredRowFailsRetryably() {
        storedRow(FakeFetcher.CORRUPT, START, START.plusSeconds(500));
        fetcher.parseRetryable = true;
        fetcher.down = true;
        clock.set(START.plusSeconds(10));
        assertThat(source.lookup(KEY).failed()).isTrue();
        clock.advance(FIRST_DELAY);
        assertThat(source.lookup(KEY).failed()).isTrue();
        assertThat(unusableRowWarnings()).isEqualTo(2);
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

        clock.advance(DELAYS.get(0));
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(3);
        clock.advance(DELAYS.get(1));
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(4);

        fetcher.down = false;
        clock.advance(DELAYS.get(2));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-2");

        fetcher.down = true;
        clock.advance(CACHE_TIME);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(6);
        clock.advance(DELAYS.get(0));
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(7);
    }

    @Test
    void shouldFollowCopyStoredRowAndModeThroughOutageAndRecovery() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(store.writes.get()).isEqualTo(1);

        fetcher.down = true;
        clock.advance(CACHE_TIME);
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);
        assertThat(store.writes.get()).isEqualTo(1);

        fetcher.down = false;
        clock.advance(FIRST_DELAY);
        assertThat(source.lookup(KEY).getContent().payload()).isEqualTo("payload-2");
        assertThat(store.row(IssuerDataKind.STATUSLIST, KEY)).isEqualTo(new StoredIssuerData("payload-2",
                clock.instant(), clock.instant().plus(CACHE_TIME).plusSeconds(600)));

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
        clock.advance(FIRST_DELAY);
        source.lookup(KEY);
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldFailAndRefreshAgainAfterBackoffWhenStoreReadThrowsDuringOutage() {
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        fetcher.down = true;
        store.throwingReads = true;
        var failed = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(failed.failed()).isTrue();
        var withinBackoff = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(withinBackoff.failed()).isTrue();
        assertThat(fetcher.attempts.get()).isEqualTo(1);
        fetcher.down = false;
        store.throwingReads = false;
        clock.advance(FIRST_DELAY);
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

    @ParameterizedTest(name = "limit {0}")
    @ValueSource(longs = {MAXIMUM_ENTRIES, 0})
    void shouldDownloadOnceForConcurrentChecksOfOneKey(long maximumEntries) {
        var concurrent = newSource(pool, SHORT_WAIT, maximumEntries);
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
        clock.advance(FIRST_DELAY);
        fetcher.gates.put(KEY, new CountDownLatch(1));

        var result = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));

        assertThat(result.getContent().payload()).isEqualTo("payload-1");
        fetcher.gates.get(KEY).countDown();
        await().atMost(TIMEOUT).untilAsserted(
                () -> assertThat(concurrent.lookup(KEY).getContent().payload()).isEqualTo("payload-2"));
    }

    @Test
    void shouldServeLastGoodCopyWhenRefreshOutlastsDownloadWaitInNormalMode() {
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
    void shouldServeStoredRowAfterDownloadWaitWhenMemoryIsEmptyAndRefreshIsSlow() {
        maximumAge.set(Result.success(Duration.ofSeconds(600)));
        storedRow("stored-1", START.minusSeconds(100), START.plusSeconds(200));
        var concurrent = newSource(pool, SHORT_WAIT);
        fetcher.gates.put(KEY, new CountDownLatch(1));
        var checkThread = new AtomicReference<Thread>();

        var result = assertTimeoutPreemptively(TIMEOUT, () -> {
            checkThread.set(Thread.currentThread());
            return concurrent.lookup(KEY);
        });

        assertThat(result.getContent().payload()).isEqualTo("stored-1");
        assertThat(store.readThreads).isNotEmpty().doesNotContain(checkThread.get());
        assertThat(store.writes.get()).isZero();
        fetcher.gates.get(KEY).countDown();
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 1);
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
        assertThat(source.trackedKeys()).isZero();
    }

    @Test
    void shouldNeverTrackMoreKeysThanTheDownloadLimitWhileTheIssuerIsDown() {
        var limited = newSource(Runnable::run, CACHE_TIME, settings(MAXIMUM_ENTRIES, TRACKED_DOWNLOADS_LIMIT, SHORT_WAIT));
        fetcher.down = true;
        for (var i = 0; i < 10 * TRACKED_DOWNLOADS_LIMIT; i++) {
            assertThat(limited.lookup(statusListKey(i)).isRetryable()).isTrue();
            assertThat(limited.trackedKeys()).isLessThanOrEqualTo(TRACKED_DOWNLOADS_LIMIT);
        }
    }

    @Test
    void shouldAnswerWaitingCheckRetryablyAtOnceWhenDownloadLimitPushesOutItsTrackerEntry() {
        var limited = newSource(pool, CACHE_TIME, settings(MAXIMUM_ENTRIES, TRACKED_DOWNLOADS_LIMIT, TIMEOUT.multipliedBy(6)));
        var running = startDownload(limited, 1);
        fetcher.down = true;
        for (var i = 0; i < 100 * TRACKED_DOWNLOADS_LIMIT && !running.lookup().isDone(); i++) {
            limited.lookup(statusListKey(i));
        }
        var result = assertTimeoutPreemptively(TIMEOUT, running.lookup()::join);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
    }

    @Test
    void shouldServeLastGoodCopyAndHoldRetryDelayDuringOutageWhenCacheTimeIsZero() {
        var noCache = newSource(Runnable::run, SHORT_WAIT, MAXIMUM_ENTRIES, Duration.ZERO);
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
    void shouldGiveAValueFromEveryCheckWhenLimitIsZeroAndIssuerIsUp() {
        var uncached = newSource(Runnable::run, SHORT_WAIT, 0);

        var first = uncached.lookup(KEY);
        var second = uncached.lookup(KEY);

        assertThat(first.getContent().payload()).isEqualTo("payload-1");
        assertThat(second.getContent().payload()).isEqualTo("payload-2");
        assertThat(store.writes.get()).isEqualTo(2);
    }

    @Test
    void shouldServeStoredRowAndHitIssuerOncePerRetryDelayWhenLimitIsZeroAndIssuerIsDown() {
        var uncached = newSource(Runnable::run, SHORT_WAIT, 0);
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        assertThat(uncached.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        fetcher.down = true;
        clock.advance(Duration.ofSeconds(10));

        assertThat(uncached.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(uncached.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);

        clock.advance(FIRST_DELAY.minusSeconds(1));
        assertThat(uncached.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(1));
        assertThat(uncached.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        assertThat(fetcher.attempts.get()).isEqualTo(3);
    }

    @Test
    void shouldKeepRetryStateOfFailingKeyWhileColdKeysFloodTheCopyStore() {
        var small = newSource(Runnable::run, SHORT_WAIT, 8);
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        small.lookup(KEY);
        fetcher.down = true;
        clock.advance(CACHE_TIME);

        for (var i = 0; i < 500; i++) {
            small.lookup(statusListKey(i));
            assertThat(small.lookup(KEY).getContent().payload()).isEqualTo("payload-1");
        }

        assertThat(fetcher.attempts.get()).isEqualTo(1 + 500 + 1);
    }

    @Test
    void shouldWaitTheListedDelayAfterEachFailureAndKeepTheLastOneWhenACheckComesLate() {
        var retrying = newSource(Runnable::run, CACHE_TIME, settings(MAXIMUM_ENTRIES, SHORT_WAIT,
                new DownloadRetry(List.of(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15))),
                DOWNLOAD_EXPIRE_AFTER));
        fetcher.down = true;
        retrying.lookup(KEY);
        var expectedAttempts = 1;

        for (var waitSeconds : List.of(5, 10, 15, 15)) {
            assertThat(attemptsAfterCheckIn(retrying, Duration.ofSeconds(waitSeconds - 1))).isEqualTo(expectedAttempts);
            expectedAttempts++;
            assertThat(attemptsAfterCheckIn(retrying, Duration.ofSeconds(1))).isEqualTo(expectedAttempts);
        }

        assertThat(attemptsAfterCheckIn(retrying, Duration.ofSeconds(20))).isEqualTo(6);
        assertThat(attemptsAfterCheckIn(retrying, Duration.ofSeconds(14))).isEqualTo(6);
        assertThat(attemptsAfterCheckIn(retrying, Duration.ofSeconds(1))).isEqualTo(7);
    }

    @Test
    void shouldForgetFailuresAtDownloadExpiryTimeEvenWhenChecksKeepArriving() {
        var retrying = newSource(Runnable::run, CACHE_TIME, settings(MAXIMUM_ENTRIES, SHORT_WAIT,
                new DownloadRetry(List.of(Duration.ofSeconds(10), Duration.ofSeconds(20), Duration.ofSeconds(100))),
                Duration.ofSeconds(50)));
        fetcher.down = true;

        assertThat(attemptsAfterCheckAt(retrying, 0)).isEqualTo(1);
        assertThat(attemptsAfterCheckAt(retrying, 5)).isEqualTo(1);
        assertThat(attemptsAfterCheckAt(retrying, 10)).isEqualTo(2);
        assertThat(attemptsAfterCheckAt(retrying, 15)).isEqualTo(2);
        assertThat(attemptsAfterCheckAt(retrying, 29)).isEqualTo(2);
        assertThat(attemptsAfterCheckAt(retrying, 30)).isEqualTo(3);
        assertThat(attemptsAfterCheckAt(retrying, 35)).isEqualTo(3);
        assertThat(attemptsAfterCheckAt(retrying, 49)).isEqualTo(3);

        assertThat(attemptsAfterCheckAt(retrying, 51)).isEqualTo(4);
        assertThat(attemptsAfterCheckAt(retrying, 60)).isEqualTo(4);
        assertThat(attemptsAfterCheckAt(retrying, 61)).isEqualTo(5);
    }

    @Test
    void shouldKeepTrackerEntryOfRunningDownloadUntilItsOwnDownloadEnds() {
        maximumAge.set(Result.success(Duration.ofSeconds(3600)));
        storedRow("stored-1", START.minusSeconds(100), START.plusSeconds(10_000));
        var concurrent = newSource(pool, SHORT_WAIT, MAXIMUM_ENTRIES);
        var firstGate = new CountDownLatch(1);
        fetcher.gates.put(KEY, firstGate);
        assertThat(assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY)).getContent().payload())
                .isEqualTo("stored-1");
        assertThat(fetcher.attempts.get()).isEqualTo(1);

        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).minusSeconds(1));
        assertThat(assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY)).getContent().payload())
                .isEqualTo("stored-1");
        assertThat(fetcher.attempts.get()).isEqualTo(1);

        var secondGate = new CountDownLatch(1);
        fetcher.gates.put(KEY, secondGate);
        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusSeconds(1));
        assertThat(assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY)).getContent().payload())
                .isEqualTo("stored-1");
        assertThat(fetcher.attempts.get()).isEqualTo(2);

        firstGate.countDown();
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 1);
        await().during(SHORT_WAIT.multipliedBy(3)).atMost(TIMEOUT).untilAsserted(
                () -> assertThat(concurrent.trackedKeys()).isEqualTo(1));

        secondGate.countDown();
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(concurrent.trackedKeys()).isZero());
    }

    @Test
    void shouldKeepNewerCopyAndAnswerWaitingCheckRetryablyWhenOlderDownloadFinishesLast() {
        var concurrent = newSource(pool, Duration.ofMinutes(15), settings(MAXIMUM_ENTRIES, TIMEOUT.multipliedBy(6)));
        var older = startDownload(concurrent, 1);
        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusSeconds(1));
        var newerSentAt = clock.instant();
        var newer = startDownload(concurrent, 2);

        newer.gate().countDown();
        assertThat(assertTimeoutPreemptively(TIMEOUT, newer.lookup()::join).getContent().fetchedAt()).isEqualTo(newerSentAt);

        older.gate().countDown();
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 2);

        var olderResult = assertTimeoutPreemptively(TIMEOUT, older.lookup()::join);
        assertThat(olderResult.failed()).isTrue();
        assertThat(olderResult.isRetryable()).isTrue();
        assertThat(concurrent.lookup(KEY).getContent().fetchedAt()).isEqualTo(newerSentAt);
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldNotAnswerWaitingCheckWithOlderCopyWhenLimitIsZeroAndOlderDownloadFinishesLast() {
        var uncached = newSource(pool, Duration.ofMinutes(15), settings(0, TIMEOUT.multipliedBy(6)));
        var older = startDownload(uncached, 1);
        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusSeconds(1));
        var newerSentAt = clock.instant();
        var newer = startDownload(uncached, 2);

        newer.gate().countDown();
        assertThat(assertTimeoutPreemptively(TIMEOUT, newer.lookup()::join).getContent().fetchedAt()).isEqualTo(newerSentAt);

        older.gate().countDown();
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 2);

        var olderResult = assertTimeoutPreemptively(TIMEOUT, older.lookup()::join);
        assertThat(olderResult.failed()).isTrue();
        assertThat(olderResult.isRetryable()).isTrue();
    }

    @Test
    void shouldNotAnswerWaitingCheckWithOlderCopyWhenNewerCopyLeftMemoryByCacheTime() {
        var shortLived = newSource(pool, Duration.ofSeconds(30), settings(MAXIMUM_ENTRIES, TIMEOUT.multipliedBy(6)));
        var older = startDownload(shortLived, 1);
        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusSeconds(1));
        var newer = startDownload(shortLived, 2);

        newer.gate().countDown();
        assertThat(assertTimeoutPreemptively(TIMEOUT, newer.lookup()::join).succeeded()).isTrue();

        clock.advance(Duration.ofSeconds(31));

        older.gate().countDown();
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 2);

        var olderResult = assertTimeoutPreemptively(TIMEOUT, older.lookup()::join);
        assertThat(olderResult.failed()).isTrue();
        assertThat(olderResult.isRetryable()).isTrue();

        var later = shortLived.lookup(KEY);
        assertThat(later.getContent().fetchedAt()).isEqualTo(clock.instant());
    }

    @Test
    void shouldAnswerWaitingCheckRetryablyAtOnceWhenTrackerEntryExpiresWhileDownloadRuns() {
        var concurrent = newSource(pool, Duration.ofMinutes(15), settings(MAXIMUM_ENTRIES, TIMEOUT.multipliedBy(6)));
        var running = startDownload(concurrent, 1);

        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusSeconds(1));
        assertThat(concurrent.trackedKeys()).isZero();

        var result = assertTimeoutPreemptively(TIMEOUT, running.lookup()::join);
        assertThat(result.failed()).isTrue();
        assertThat(result.isRetryable()).isTrue();
        assertThat(fetcher.attempts.get()).isEqualTo(1);
    }

    @Test
    void shouldKeepCopyOutOfMemoryAndAnswerRetryablyWhenTrackerEntryExpiredBeforeDownloadEnds() {
        var concurrent = newSource(pool, Duration.ofMinutes(15), settings(MAXIMUM_ENTRIES, TIMEOUT.multipliedBy(6)));
        var running = startDownload(concurrent, 1);
        store.writeGate = new CountDownLatch(1);

        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusSeconds(1));
        running.gate().countDown();

        try {
            var result = assertTimeoutPreemptively(TIMEOUT, running.lookup()::join);
            assertThat(result.failed()).isTrue();
            assertThat(result.isRetryable()).isTrue();
        } finally {
            store.writeGate.countDown();
        }
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 1);
        assertThat(concurrent.cachedKeys()).isZero();
    }

    @Test
    void shouldKeepCopyOutOfMemoryAndAnswerRetryablyWhenTrackerMaintenanceRanInTheTickOfItsExpiry() {
        var concurrent = newSource(pool, Duration.ofMinutes(15), settings(MAXIMUM_ENTRIES, TIMEOUT.multipliedBy(6)));
        var running = startDownload(concurrent, 1);
        store.writeGate = new CountDownLatch(1);

        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).minusMillis(1));
        assertThat(concurrent.trackedKeys()).isOne();
        clock.set(START.plus(DOWNLOAD_EXPIRE_AFTER).plusMillis(1));
        running.gate().countDown();

        try {
            var result = assertTimeoutPreemptively(TIMEOUT, running.lookup()::join);
            assertThat(result.failed()).isTrue();
            assertThat(result.isRetryable()).isTrue();
        } finally {
            store.writeGate.countDown();
        }
        await().atMost(TIMEOUT).until(() -> store.writes.get() == 1);
        assertThat(concurrent.cachedKeys()).isZero();
    }

    @Test
    void shouldFailWithoutHangingAndRetryWhenMaximumAgeSupplierThrowsAfterDownload() {
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        maximumAgeThrows = true;

        var failed = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));

        assertThat(failed.failed()).isTrue();
        maximumAgeThrows = false;
        clock.advance(FIRST_DELAY);
        var recovered = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(recovered.succeeded()).isTrue();
        assertThat(fetcher.attempts.get()).isEqualTo(2);
    }

    @Test
    void shouldWarnOnlyOnceForTheFailedRefreshWhileServingStoredCopyOnLaterChecks() {
        source.lookup(KEY);
        fetcher.down = true;
        clock.set(START.plus(CACHE_TIME).plusSeconds(10));

        var results = List.of(source.lookup(KEY), source.lookup(KEY), source.lookup(KEY));

        assertThat(results).allSatisfy(result -> assertThat(result.getContent().payload()).isEqualTo("payload-1"));
        assertThat(monitor.warnings()).hasSize(1);
    }

    @Test
    void shouldNotDelayWaitingCheckWhileStoreWriteIsBlocked() {
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        store.writeGate = new CountDownLatch(1);
        try {
            var result = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));

            assertThat(result.getContent().payload()).isEqualTo("payload-1");
        } finally {
            store.writeGate.countDown();
        }
    }

    @Test
    void shouldFailRetryablyWhenExecutorRejectsAndRefreshAgainOnceItAccepts() {
        var rejecting = new AtomicBoolean(true);
        var concurrent = newSource(command -> {
            if (rejecting.get()) {
                throw new RejectedExecutionException("shut down");
            }
            pool.execute(command);
        }, SHORT_WAIT);

        var rejected = concurrent.lookup(KEY);

        assertThat(rejected.failed()).isTrue();
        assertThat(rejected.isRetryable()).isTrue();
        rejecting.set(false);
        clock.advance(FIRST_DELAY);
        var recovered = assertTimeoutPreemptively(TIMEOUT, () -> concurrent.lookup(KEY));
        assertThat(recovered.getContent().payload()).isEqualTo("payload-1");
    }

    @Test
    void shouldFailRetryablyAndKeepInterruptFlagWhenWaitIsInterruptedAndNoRowExists() {
        var concurrent = newSource(pool, TIMEOUT.multipliedBy(6));
        fetcher.gates.put(KEY, new CountDownLatch(1));
        Thread.currentThread().interrupt();
        try {
            var result = concurrent.lookup(KEY);
            var interruptKept = Thread.interrupted();

            assertThat(result.failed()).isTrue();
            assertThat(result.isRetryable()).isTrue();
            assertThat(interruptKept).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldNeverParseStoredRowPastItsValidUntil() {
        storedRow("stored-1", START.minusSeconds(100), START.plusSeconds(10));
        fetcher.down = true;
        clock.set(START.plusSeconds(10));

        var results = List.of(source.lookup(KEY), source.lookup(KEY), source.lookup(KEY));

        assertThat(results).allSatisfy(result -> assertThat(result.failed()).isTrue());
        assertThat(fetcher.parses.get()).isZero();
    }

    @Test
    void shouldStampCopyAndRowWithTheSendTimeOfTheDownload() {
        fetcher.sendTime = START.minusSeconds(20);

        var first = source.lookup(KEY);

        assertThat(first.getContent().fetchedAt()).isEqualTo(START.minusSeconds(20));
        assertThat(first.getContent().validUntil()).isEqualTo(START.plusSeconds(100));
        assertThat(store.row(IssuerDataKind.STATUSLIST, KEY))
                .isEqualTo(new StoredIssuerData("payload-1", START.minusSeconds(20), START.plusSeconds(100)));
        clock.set(START.plusSeconds(39));
        source.lookup(KEY);
        assertThat(fetcher.downloads.get()).isEqualTo(1);
        clock.set(START.plusSeconds(40));
        source.lookup(KEY);
        assertThat(fetcher.downloads.get()).isEqualTo(2);
    }

    @Test
    void shouldCountDeadlineOfStoredRowFromTheSendTimeWhenIssuerIsDown() {
        fetcher.sendTime = START.minusSeconds(20);
        source.lookup(KEY);
        fetcher.down = true;

        clock.set(START.plusSeconds(99));
        var beforeDeadline = source.lookup(KEY);
        clock.set(START.plusSeconds(100));
        var atDeadline = source.lookup(KEY);

        assertThat(beforeDeadline.getContent().payload()).isEqualTo("payload-1");
        assertThat(atDeadline.failed()).isTrue();
    }

    private Download startDownload(LastGoodSource<String> checked, int attempt) {
        var gate = new CountDownLatch(1);
        fetcher.gates.put(KEY, gate);
        var lookup = CompletableFuture.supplyAsync(() -> checked.lookup(KEY), pool);
        await().atMost(TIMEOUT).until(() -> fetcher.attempts.get() == attempt);
        return new Download(lookup, gate);
    }

    private int attemptsAfterCheckIn(LastGoodSource<String> checked, Duration after) {
        clock.advance(after);
        checked.lookup(KEY);
        return fetcher.attempts.get();
    }

    private int attemptsAfterCheckAt(LastGoodSource<String> checked, int secondsFromStart) {
        clock.set(START.plusSeconds(secondsFromStart));
        checked.lookup(KEY);
        return fetcher.attempts.get();
    }

    private static String statusListKey(int index) {
        return ISSUER_PATH + index;
    }

    private long unusableRowWarnings() {
        return monitor.warnings().stream().filter(warning -> warning.contains("is not usable")).count();
    }

    private record Download(CompletableFuture<Result<LastGoodCopy<String>>> lookup, CountDownLatch gate) {
    }

    private record Check(Thread thread, CompletableFuture<Result<LastGoodCopy<String>>> result) {
    }

    private static Check startCheck(LastGoodSource<String> source, String key) {
        var result = new CompletableFuture<Result<LastGoodCopy<String>>>();
        var thread = Thread.ofPlatform().daemon().start(() -> result.complete(source.lookup(key)));
        return new Check(thread, result);
    }

    private static boolean isBlocked(Thread thread) {
        return Set.of(Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING).contains(thread.getState());
    }

    private void storedRow(String payload, Instant fetchedAt, Instant validUntil) {
        store.put(IssuerDataKind.STATUSLIST, KEY, new StoredIssuerData(payload, fetchedAt, validUntil));
    }
}
