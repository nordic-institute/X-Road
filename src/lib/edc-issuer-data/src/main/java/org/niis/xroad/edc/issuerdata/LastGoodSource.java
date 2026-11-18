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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreFailure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Serves issuer data from a copy for the cache time, refreshes it in the background after that, and falls back to the
 * last good copy while the issuer does not answer, up to a deadline set by the maximum age.
 * <p>
 * The deadline of a copy is the earlier of its stored {@code validUntil} and its download time plus the cache time
 * plus the maximum age read at the moment of the check. A copy is never served past the expiry the data states itself.
 * <p>
 * Each key has at most one refresh at a time, run on the executor with no lock held. While the last refresh of a key
 * succeeded, a check that finds the copy past the cache time starts or joins the refresh and waits for it at most the
 * refresh wait. After a failed refresh the key is in outage mode: checks never wait, they get the last good copy at
 * once, and the executor retries the download with a backoff that doubles up to a maximum. A success ends the outage
 * and resets the backoff.
 * <p>
 * Every successful download is also written to the {@link IssuerDataStore}. The store is read when a refresh fails
 * and when a key has no copy in memory and its refresh is not finished within the refresh wait: the later of the
 * memory copy and the stored copy is then the candidate. A stored copy is read again through
 * {@link IssuerDataFetcher#parse} like a fresh download. Store calls run only on the executor, never on the thread of
 * a check, so they never join a transaction of the caller.
 *
 * @param <P> the parsed form of the data
 */
public class LastGoodSource<P> {

    static final Duration SERVE_MARGIN = Duration.ofSeconds(5);
    static final Duration REFRESH_WAIT = Duration.ofSeconds(2);
    static final Duration INITIAL_BACKOFF = Duration.ofSeconds(5);
    static final Duration MAXIMUM_BACKOFF = Duration.ofSeconds(60);
    private static final int MAXIMUM_BACKOFF_SHIFT = 16;

    private final IssuerDataFetcher<P> fetcher;
    private final Clock clock;
    private final Executor executor;
    private final Duration cacheTime;
    private final Duration refreshWait;
    private final Supplier<Result<Duration>> maximumAge;
    private final IssuerDataStore store;
    private final Monitor monitor;
    private final Cache<String, KeyState<P>> entries;

    /**
     * @param executor runs the refreshes and every store call; it must not run tasks on the calling thread in
     *                 production
     */
    public LastGoodSource(IssuerDataFetcher<P> fetcher, Clock clock, Ticker ticker, Executor executor, Duration cacheTime,
                          Duration refreshWait, Supplier<Result<Duration>> maximumAge, IssuerDataStore store,
                          Monitor monitor) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.executor = executor;
        this.cacheTime = cacheTime;
        this.refreshWait = refreshWait;
        this.maximumAge = maximumAge;
        this.store = store;
        this.monitor = monitor;
        this.entries = Caffeine.newBuilder().ticker(ticker).executor(Runnable::run).build();
    }

    /**
     * Never throws. A failure carries the download or parse failure when no usable copy exists.
     */
    @Nonnull
    public Result<LastGoodCopy<P>> lookup(String key) {
        var current = KeyState.<P>orEmpty(entries.getIfPresent(key));
        if (current.copy() != null && isFresh(current.copy())) {
            return Result.success(current.copy());
        }
        var state = startRefreshIfDue(key);
        if (state.refresh() == null && state.outage() == null) {
            return Result.success(state.copy());
        }
        return state.outage() == null ? checkInNormalMode(key, state) : checkInOutage(key, state);
    }

    private KeyState<P> startRefreshIfDue(String key) {
        var created = new CompletableFuture<Result<LastGoodCopy<P>>>();
        var now = clock.instant();
        var state = entries.asMap().compute(key, (_, current) -> {
            var existing = KeyState.<P>orEmpty(current);
            return refreshDue(existing, now) ? existing.withRefresh(created) : existing;
        });
        if (state.refresh() == created) {
            executor.execute(() -> runRefresh(key, created));
        }
        return state;
    }

    private boolean refreshDue(KeyState<P> state, Instant now) {
        if (state.refresh() != null || state.copy() != null && isFresh(state.copy(), now)) {
            return false;
        }
        return state.outage() == null || !now.isBefore(state.outage().nextAttempt());
    }

    private Result<LastGoodCopy<P>> checkInNormalMode(String key, KeyState<P> state) {
        var finished = awaitFinished(state.refresh());
        if (finished != null) {
            return resolve(key, finished);
        }
        if (state.copy() == null) {
            enterOutage(key);
            runOnExecutor(() -> adoptStoredCopy(key));
        }
        var copy = servableCopy(key);
        return copy != null ? Result.success(copy) : resolve(key, state.refresh().join());
    }

    private Result<LastGoodCopy<P>> checkInOutage(String key, KeyState<P> state) {
        var copy = servableCopy(key);
        if (copy != null) {
            return Result.success(copy);
        }
        if (state.refresh() != null) {
            return resolve(key, state.refresh().join());
        }
        return state.outage().failure().mapFailure();
    }

    private Result<LastGoodCopy<P>> resolve(String key, Result<LastGoodCopy<P>> outcome) {
        if (outcome.succeeded()) {
            return outcome;
        }
        var copy = servableCopy(key);
        return copy != null ? Result.success(copy) : outcome;
    }

    @Nullable
    private Result<LastGoodCopy<P>> awaitFinished(CompletableFuture<Result<LastGoodCopy<P>>> refresh) {
        try {
            return refresh.get(refreshWait.toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException _) {
            return null;
        } catch (ExecutionException e) {
            return Result.failure("Issuer data refresh ended unexpectedly: " + e.getCause());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Result.failure("Interrupted while waiting for the issuer data refresh");
        }
    }

    private void runOnExecutor(Runnable task) {
        CompletableFuture.runAsync(task, executor).join();
    }

    private void enterOutage(String key) {
        var now = clock.instant();
        entries.asMap().compute(key, (_, current) -> {
            var existing = KeyState.<P>orEmpty(current);
            if (existing.outage() != null || existing.refresh() == null) {
                return existing;
            }
            return existing.withOutage(new Outage(0, now, Result.retryableFailure("Issuer data refresh is still running")));
        });
    }

    @Nullable
    private LastGoodCopy<P> servableCopy(String key) {
        var copy = KeyState.<P>orEmpty(entries.getIfPresent(key)).copy();
        if (copy == null) {
            return null;
        }
        return isFresh(copy) || usableAfterFailure(key, copy) ? copy : null;
    }

    private void runRefresh(String key, CompletableFuture<Result<LastGoodCopy<P>>> refresh) {
        var outcome = download(key);
        try {
            if (outcome.succeeded()) {
                recordSuccess(key, outcome.getContent());
            } else {
                recordFailure(key, outcome);
            }
        } catch (RuntimeException e) {
            monitor.warning("Issuer data of '%s' could not be recorded after the refresh".formatted(key), e);
            outcome = Result.failure("Unexpected " + e.getClass().getSimpleName());
            endRefreshWithFailure(key, refresh, outcome);
        } finally {
            refresh.complete(outcome);
        }
    }

    private void endRefreshWithFailure(String key, CompletableFuture<Result<LastGoodCopy<P>>> refresh, Result<?> failure) {
        var failedAt = clock.instant();
        entries.asMap().compute(key, (_, current) -> {
            var existing = KeyState.<P>orEmpty(current);
            return existing.refresh() == refresh ? existing.withFailure(failedAt, failure) : existing;
        });
    }

    private Result<LastGoodCopy<P>> download(String key) {
        return attempt(key, () -> fetcher.download(key))
                .compose(payload -> attempt(key, () -> fetcher.parse(key, payload))
                        .map(parsed -> newCopy(key, payload, parsed)));
    }

    private <T> Result<T> attempt(String key, Supplier<Result<T>> step) {
        try {
            return step.get();
        } catch (RuntimeException e) {
            monitor.warning("Issuer data of '%s' could not be processed".formatted(key), e);
            return Result.failure("Unexpected " + e.getClass().getSimpleName());
        }
    }

    private LastGoodCopy<P> newCopy(String key, String payload, P parsed) {
        var fetchedAt = clock.instant();
        var validUntil = fetchedAt.plus(cacheTime).plus(maximumAgeOrZero(key));
        return new LastGoodCopy<>(payload, parsed, fetchedAt, validUntil);
    }

    private void recordSuccess(String key, LastGoodCopy<P> copy) {
        entries.asMap().compute(key, (_, current) -> KeyState.<P>orEmpty(current).withDownloaded(copy));
        var written = store.write(fetcher.kind(), key, new StoredIssuerData(copy.payload(), copy.fetchedAt(), copy.validUntil()));
        if (written.failed()) {
            monitor.warning("Issuer data of '%s' could not be stored: %s".formatted(key, written.getFailureDetail()));
        }
    }

    private void recordFailure(String key, Result<LastGoodCopy<P>> failure) {
        monitor.warning("Issuer data of '%s' could not be refreshed: %s".formatted(key, failure.getFailureDetail()));
        adoptStoredCopy(key);
        var failedAt = clock.instant();
        entries.asMap().compute(key, (_, current) -> KeyState.<P>orEmpty(current).withFailure(failedAt, failure));
    }

    private Duration maximumAgeOrZero(String key) {
        var age = maximumAge.get();
        if (age.failed()) {
            monitor.warning("Maximum age of issuer data unreadable, no stale use of the copy of '%s': %s"
                    .formatted(key, age.getFailureDetail()));
            return Duration.ZERO;
        }
        return age.getContent();
    }

    private void adoptStoredCopy(String key) {
        var state = KeyState.<P>orEmpty(entries.getIfPresent(key));
        if (state.storedCopyInvalid()) {
            return;
        }
        var row = store.read(fetcher.kind(), key);
        if (row.failed()) {
            if (row.getFailure().getReason() != StoreFailure.Reason.NOT_FOUND) {
                monitor.warning("Stored issuer data of '%s' could not be read: %s".formatted(key, row.getFailureDetail()));
            }
            return;
        }
        var stored = row.getContent();
        if (state.copy() != null && !stored.fetchedAt().isAfter(state.copy().fetchedAt())) {
            return;
        }
        var parsed = attempt(key, () -> fetcher.parse(key, stored.payload()));
        if (parsed.failed()) {
            monitor.warning("Stored issuer data of '%s' is not usable: %s".formatted(key, parsed.getFailureDetail()));
            if (!parsed.isRetryable()) {
                entries.asMap().compute(key, (_, current) -> KeyState.<P>orEmpty(current).withStoredCopyInvalid());
            }
            return;
        }
        var adopted = new LastGoodCopy<>(stored.payload(), parsed.getContent(), stored.fetchedAt(), stored.validUntil());
        entries.asMap().compute(key, (_, current) -> KeyState.<P>orEmpty(current).withNewerCopy(adopted));
    }

    private boolean isFresh(LastGoodCopy<P> copy) {
        return isFresh(copy, clock.instant());
    }

    private boolean isFresh(LastGoodCopy<P> copy, Instant now) {
        return now.isBefore(copy.fetchedAt().plus(cacheTime)) && !pastOwnExpiry(copy, now);
    }

    private boolean pastOwnExpiry(LastGoodCopy<P> copy, Instant now) {
        return fetcher.ownExpiry(copy.parsed()).map(expiry -> !now.isBefore(expiry)).orElse(false);
    }

    private boolean usableAfterFailure(String key, LastGoodCopy<P> copy) {
        var age = maximumAge.get();
        if (age.failed()) {
            return false;
        }
        var now = clock.instant();
        var ageDeadline = copy.fetchedAt().plus(cacheTime).plus(age.getContent());
        var deadline = copy.validUntil().isBefore(ageDeadline) ? copy.validUntil() : ageDeadline;
        var usable = now.plus(SERVE_MARGIN).isBefore(deadline) && !pastOwnExpiry(copy, now);
        if (usable) {
            monitor.warning("Issuer data of '%s' not refreshed, serving the copy downloaded at %s"
                    .formatted(key, copy.fetchedAt()));
        }
        return usable;
    }

    static Duration backoff(int failures) {
        var shift = Math.min(failures - 1, MAXIMUM_BACKOFF_SHIFT);
        var backoff = INITIAL_BACKOFF.multipliedBy(1L << shift);
        return backoff.compareTo(MAXIMUM_BACKOFF) > 0 ? MAXIMUM_BACKOFF : backoff;
    }

    /**
     * What is known about one key. Changed only through {@code compute} on the cache, with no I/O in the function.
     *
     * @param copy              the last good download held in memory, if any
     * @param storedCopyInvalid true when the stored copy failed to parse; it stays ignored until the next successful
     *                          download
     * @param outage            set after a failed refresh, until a refresh succeeds
     * @param refresh           the refresh in flight, if any
     */
    private record KeyState<P>(@Nullable LastGoodCopy<P> copy, boolean storedCopyInvalid, @Nullable Outage outage,
                               @Nullable CompletableFuture<Result<LastGoodCopy<P>>> refresh) {

        static <P> KeyState<P> orEmpty(@Nullable KeyState<P> state) {
            return state != null ? state : new KeyState<>(null, false, null, null);
        }

        KeyState<P> withRefresh(CompletableFuture<Result<LastGoodCopy<P>>> started) {
            return new KeyState<>(copy, storedCopyInvalid, outage, started);
        }

        KeyState<P> withDownloaded(LastGoodCopy<P> downloaded) {
            return new KeyState<>(downloaded, false, null, null);
        }

        KeyState<P> withFailure(Instant failedAt, Result<?> failure) {
            var failures = (outage == null ? 0 : outage.failures()) + 1;
            return new KeyState<>(copy, storedCopyInvalid, new Outage(failures, failedAt.plus(backoff(failures)), failure), null);
        }

        KeyState<P> withOutage(Outage entered) {
            return new KeyState<>(copy, storedCopyInvalid, entered, refresh);
        }

        KeyState<P> withNewerCopy(LastGoodCopy<P> candidate) {
            if (copy != null && !candidate.fetchedAt().isAfter(copy.fetchedAt())) {
                return this;
            }
            return new KeyState<>(candidate, storedCopyInvalid, outage, refresh);
        }

        KeyState<P> withStoredCopyInvalid() {
            return new KeyState<>(copy, true, outage, refresh);
        }
    }

    /**
     * @param failures    the failed refreshes in a row
     * @param nextAttempt the earliest time of the next background refresh
     * @param failure     the last failed refresh, returned while no copy is usable and the backoff runs
     */
    private record Outage(int failures, Instant nextAttempt, Result<?> failure) {
    }
}
