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
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.result.StoreFailure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Serves issuer data from a copy for the cache time, downloads it again after that, and serves the stored copy while
 * the issuer does not answer, up to a deadline set by the maximum age.
 * <p>
 * The deadline of a copy is the earlier of its stored {@code validUntil} and its send time plus the cache time plus
 * the maximum age read at the moment of the check. A copy is never served past the expiry the data states itself.
 * <p>
 * Copies in memory and running downloads are kept apart. A copy stays in memory for the cache time from its send time,
 * at most the configured number of keys; with the limit 0 no copy stays. Whether a copy is fresh is decided by the
 * clock, not by the memory expiry.
 * <p>
 * A check that finds no fresh copy starts or joins the one download of its key, at limit 0 too, and waits for it at
 * most the download wait. A successful download is put in memory and written to the {@link IssuerDataStore}; a
 * failed write only logs. After a failed or unfinished download the key is in outage mode: checks read the stored row
 * each time and serve it while it is fresh or usable after a failure, and a retry starts only on a check after the
 * next attempt time, with the delay of {@link DownloadRetry}. A row read back is never put in memory. A check with no
 * usable row waits for the running download of its key with no time limit, or, when none runs, gets the failure of
 * the last download. A failed or thrown read of the row gives a retriable failure.
 * <p>
 * A download and its failure state live in a tracker entry that Caffeine removes by the entry's own state: at once
 * when it holds neither, and at the latest the reset time after it was created. A success ends the entry at once.
 * <p>
 * Data is verified when it is downloaded; a stored copy is read back through {@link IssuerDataFetcher#parseStored} as
 * stored. A stored row that fails to parse for good is skipped until another row, with another send time, replaces
 * it. Store calls run only on the executor, never on the thread of a check, so they never join a transaction of the
 * caller.
 *
 * @param <P> the parsed form of the data
 */
public class LastGoodSource<P> {

    private final IssuerDataFetcher<P> fetcher;
    private final Clock clock;
    private final Executor executor;
    private final Duration cacheTime;
    private final IssuerDataSettings settings;
    private final Supplier<Result<Duration>> maximumAge;
    private final IssuerDataStore store;
    private final Monitor monitor;
    private final Cache<String, LastGoodCopy<P>> copies;
    private final Cache<String, Tracking<P>> trackers;

    /**
     * @param executor  runs the downloads and every store call; it must not run tasks on the calling thread in
     *                  production
     * @param scheduler removes expired entries of both caches in good time
     * @param settings  the memory limit, the download wait, the retry delay and the reset time
     */
    public LastGoodSource(IssuerDataFetcher<P> fetcher, Clock clock, Ticker ticker, Executor executor,
                          ScheduledExecutorService scheduler, Duration cacheTime, IssuerDataSettings settings,
                          Supplier<Result<Duration>> maximumAge, IssuerDataStore store, Monitor monitor) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.executor = executor;
        this.cacheTime = cacheTime;
        this.settings = settings;
        this.maximumAge = maximumAge;
        this.store = store;
        this.monitor = monitor;
        this.copies = Caffeine.newBuilder()
                .ticker(ticker)
                .executor(Runnable::run)
                .maximumSize(settings.maximumEntries())
                .scheduler(Scheduler.forScheduledExecutorService(scheduler))
                .expireAfter(Expiry.writing((String _, LastGoodCopy<P> copy) -> memoryLifetime(copy)))
                .build();
        this.trackers = Caffeine.newBuilder()
                .ticker(ticker)
                .executor(Runnable::run)
                .scheduler(Scheduler.forScheduledExecutorService(scheduler))
                .expireAfter(Expiry.writing((String _, Tracking<P> tracking) -> lifetime(tracking)))
                .build();
    }

    long cachedKeys() {
        return copies.estimatedSize();
    }

    long trackedKeys() {
        trackers.cleanUp();
        return trackers.estimatedSize();
    }

    /**
     * Same as {@link #lookup(String, Duration)} with no margin.
     */
    @Nonnull
    public Result<LastGoodCopy<P>> lookup(String key) {
        return lookup(key, Duration.ZERO);
    }

    /**
     * Never throws. A failure carries the download or parse failure when no usable copy exists.
     *
     * @param margin a stored copy that expires within this margin does not count as usable during an outage
     */
    @Nonnull
    public Result<LastGoodCopy<P>> lookup(String key, Duration margin) {
        var copy = copies.getIfPresent(key);
        if (copy != null && isFresh(copy)) {
            return Result.success(copy);
        }
        var tracking = startRefreshIfDue(key);
        return tracking.outage() == null ? checkInNormalMode(key, margin, tracking.refresh())
                : checkInOutage(key, margin, tracking);
    }

    private Tracking<P> startRefreshIfDue(String key) {
        var created = new CompletableFuture<Result<LastGoodCopy<P>>>();
        var now = clock.instant();
        var tracking = trackers.asMap().compute(key, (_, current) -> {
            if (current == null) {
                return Tracking.<P>created(now).withRefresh(created);
            }
            return refreshDue(current, now) ? current.withRefresh(created) : current;
        });
        if (tracking.refresh() == created) {
            executor.execute(() -> runRefresh(key, created));
        }
        return tracking;
    }

    private boolean refreshDue(Tracking<P> tracking, Instant now) {
        return tracking.refresh() == null && (tracking.outage() == null || !now.isBefore(tracking.outage().nextAttempt()));
    }

    private Result<LastGoodCopy<P>> checkInNormalMode(String key, Duration margin,
                                                      CompletableFuture<Result<LastGoodCopy<P>>> refresh) {
        var finished = awaitFinished(refresh);
        if (finished == null) {
            enterOutage(key);
            return rowOr(key, margin, refresh::join);
        }
        return finished.succeeded() ? finished : rowOr(key, margin, () -> finished);
    }

    private Result<LastGoodCopy<P>> checkInOutage(String key, Duration margin, Tracking<P> tracking) {
        var refresh = tracking.refresh();
        return rowOr(key, margin, refresh != null ? refresh::join : () -> tracking.outage().failure().mapFailure());
    }

    private Result<LastGoodCopy<P>> rowOr(String key, Duration margin, Supplier<Result<LastGoodCopy<P>>> otherwise) {
        var row = CompletableFuture.supplyAsync(() -> servableRow(key, margin), executor).join();
        if (row.failed()) {
            return row.mapFailure();
        }
        return row.getContent().map(Result::success).orElseGet(otherwise);
    }

    @Nullable
    private Result<LastGoodCopy<P>> awaitFinished(CompletableFuture<Result<LastGoodCopy<P>>> refresh) {
        try {
            return refresh.get(settings.downloadWait().toNanos(), TimeUnit.NANOSECONDS);
        } catch (TimeoutException _) {
            return null;
        } catch (ExecutionException e) {
            return Result.failure("Issuer data refresh ended unexpectedly: " + e.getCause());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Result.failure("Interrupted while waiting for the issuer data refresh");
        }
    }

    private void enterOutage(String key) {
        var now = clock.instant();
        trackers.asMap().computeIfPresent(key, (_, current) -> {
            if (current.outage() != null || current.refresh() == null) {
                return current;
            }
            return current.withOutage(new Outage(0, now, Result.retryableFailure("Issuer data refresh is still running")));
        });
    }

    private Result<Optional<LastGoodCopy<P>>> servableRow(String key, Duration margin) {
        try {
            var row = store.read(fetcher.kind(), key);
            if (row.failed()) {
                return rowReadFailed(key, row.getFailure().getReason(), row.getFailureDetail());
            }
            return Result.success(servable(key, row.getContent(), margin));
        } catch (RuntimeException e) {
            monitor.warning("Stored issuer data of '%s' could not be read".formatted(key), e);
            return Result.retryableFailure("Stored issuer data could not be read");
        }
    }

    private Result<Optional<LastGoodCopy<P>>> rowReadFailed(String key, StoreFailure.Reason reason, String detail) {
        if (reason == StoreFailure.Reason.NOT_FOUND) {
            return Result.success(Optional.empty());
        }
        monitor.warning("Stored issuer data of '%s' could not be read: %s".formatted(key, detail));
        return Result.retryableFailure("Stored issuer data could not be read");
    }

    private Optional<LastGoodCopy<P>> servable(String key, StoredIssuerData stored, Duration margin) {
        var tracking = trackers.getIfPresent(key);
        if (tracking != null && tracking.storedRow().rejects(stored)) {
            return Optional.empty();
        }
        var parsed = attempt(key, () -> fetcher.parseStored(key, stored.payload()));
        if (parsed.failed()) {
            monitor.warning("Stored issuer data of '%s' is not usable: %s".formatted(key, parsed.getFailureDetail()));
            if (!parsed.isRetryable()) {
                markUnparsable(key, stored.fetchedAt());
            }
            return Optional.empty();
        }
        var copy = new LastGoodCopy<>(stored.payload(), parsed.getContent(), stored.fetchedAt(), stored.validUntil());
        return isFresh(copy) || usableAfterFailure(key, copy, margin) ? Optional.of(copy) : Optional.empty();
    }

    private void markUnparsable(String key, Instant fetchedAt) {
        trackers.asMap().computeIfPresent(key, (_, current) -> current.withStoredRow(new StoredRowState.Unparsable(fetchedAt)));
    }

    private void runRefresh(String key, CompletableFuture<Result<LastGoodCopy<P>>> refresh) {
        Result<LastGoodCopy<P>> outcome = Result.failure("Issuer data refresh did not finish");
        try {
            outcome = download(key);
            if (outcome.succeeded()) {
                recordSuccess(key, refresh, outcome.getContent());
            } else {
                monitor.warning("Issuer data of '%s' could not be refreshed: %s".formatted(key, outcome.getFailureDetail()));
                recordFailure(key, refresh, outcome);
            }
        } catch (RuntimeException e) {
            monitor.warning("Issuer data of '%s' could not be refreshed".formatted(key), e);
            outcome = Result.failure("Unexpected " + e.getClass().getSimpleName());
            recordFailure(key, refresh, outcome);
        } finally {
            refresh.complete(outcome);
        }
    }

    private Result<LastGoodCopy<P>> download(String key) {
        return attempt(key, () -> fetcher.download(key))
                .compose(download -> attempt(key, () -> fetcher.parse(key, download.payload()))
                        .map(parsed -> newCopy(key, download, parsed)));
    }

    private <T> Result<T> attempt(String key, Supplier<Result<T>> step) {
        try {
            return step.get();
        } catch (RuntimeException e) {
            monitor.warning("Issuer data of '%s' could not be processed".formatted(key), e);
            return Result.failure("Unexpected " + e.getClass().getSimpleName());
        }
    }

    private LastGoodCopy<P> newCopy(String key, IssuerDataFetcher.Download download, P parsed) {
        var fetchedAt = download.requestSentAt();
        var validUntil = fetchedAt.plus(cacheTime).plus(maximumAgeOrZero(key));
        return new LastGoodCopy<>(download.payload(), parsed, fetchedAt, validUntil);
    }

    private void recordSuccess(String key, CompletableFuture<Result<LastGoodCopy<P>>> refresh, LastGoodCopy<P> copy) {
        copies.put(key, copy);
        writeRow(key, copy);
        trackers.asMap().computeIfPresent(key, (_, current) -> current.refresh() == refresh ? null : current);
    }

    private void writeRow(String key, LastGoodCopy<P> copy) {
        try {
            var written = store.write(fetcher.kind(), key, new StoredIssuerData(copy.payload(), copy.fetchedAt(), copy.validUntil()));
            if (written.failed()) {
                monitor.warning("Issuer data of '%s' could not be stored: %s".formatted(key, written.getFailureDetail()));
            }
        } catch (RuntimeException e) {
            monitor.warning("Issuer data of '%s' could not be stored".formatted(key), e);
        }
    }

    private void recordFailure(String key, CompletableFuture<Result<LastGoodCopy<P>>> refresh, Result<?> failure) {
        var failedAt = clock.instant();
        var downloadRetry = settings.downloadRetry();
        trackers.asMap().computeIfPresent(key, (_, current) ->
                current.refresh() == refresh ? current.withFailure(failedAt, failure, downloadRetry) : current);
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

    private Duration memoryLifetime(LastGoodCopy<P> copy) {
        return cacheTime.minus(Duration.between(copy.fetchedAt(), clock.instant()));
    }

    private Duration lifetime(Tracking<P> tracking) {
        if (tracking.refresh() == null && tracking.outage() == null) {
            return Duration.ZERO;
        }
        var age = Duration.between(tracking.createdAt(), clock.instant());
        return age.isNegative() ? settings.resetAfter() : settings.resetAfter().minus(age);
    }

    private boolean isFresh(LastGoodCopy<P> copy) {
        var now = clock.instant();
        return now.isBefore(copy.fetchedAt().plus(cacheTime)) && now.isBefore(copy.validUntil()) && !pastOwnExpiry(copy, now);
    }

    private boolean pastOwnExpiry(LastGoodCopy<P> copy, Instant now) {
        return fetcher.ownExpiry(copy.parsed()).map(expiry -> !now.isBefore(expiry)).orElse(false);
    }

    private boolean usableAfterFailure(String key, LastGoodCopy<P> copy, Duration margin) {
        var age = maximumAge.get();
        if (age.failed()) {
            return false;
        }
        var now = clock.instant();
        var ageDeadline = copy.fetchedAt().plus(cacheTime).plus(age.getContent());
        var deadline = copy.validUntil().isBefore(ageDeadline) ? copy.validUntil() : ageDeadline;
        var usable = now.plus(margin).isBefore(deadline) && !pastOwnExpiry(copy, now);
        if (usable) {
            monitor.warning("Issuer data of '%s' not refreshed, serving the copy downloaded at %s"
                    .formatted(key, copy.fetchedAt()));
        }
        return usable;
    }

    /**
     * What is known about the download of one key. Changed only through {@code compute} and {@code computeIfPresent}
     * on the tracker cache, with no I/O in the function.
     *
     * @param refresh   the download in flight, if any
     * @param outage    set after a failed or unfinished download, until a download succeeds
     * @param storedRow what is known about the stored row of the key
     * @param createdAt when the entry was created; the entry never outlives it by more than the reset time
     */
    private record Tracking<P>(@Nullable CompletableFuture<Result<LastGoodCopy<P>>> refresh, @Nullable Outage outage,
                               StoredRowState storedRow, Instant createdAt) {

        static <P> Tracking<P> created(Instant now) {
            return new Tracking<>(null, null, StoredRowState.Unchecked.INSTANCE, now);
        }

        Tracking<P> withRefresh(CompletableFuture<Result<LastGoodCopy<P>>> started) {
            return new Tracking<>(started, outage, storedRow, createdAt);
        }

        Tracking<P> withFailure(Instant failedAt, Result<?> failure, DownloadRetry downloadRetry) {
            var failures = (outage == null ? 0 : outage.failures()) + 1;
            var nextAttempt = failedAt.plus(downloadRetry.after(failures));
            return new Tracking<>(null, new Outage(failures, nextAttempt, failure), storedRow, createdAt);
        }

        Tracking<P> withOutage(Outage entered) {
            return new Tracking<>(refresh, entered, storedRow, createdAt);
        }

        Tracking<P> withStoredRow(StoredRowState state) {
            return new Tracking<>(refresh, outage, state, createdAt);
        }
    }

    /**
     * @param failures    the failed downloads in a row
     * @param nextAttempt the earliest time of the next download
     * @param failure     the last failed download, returned while no copy is usable and the backoff runs
     */
    private record Outage(int failures, Instant nextAttempt, Result<?> failure) {
    }

    private sealed interface StoredRowState {

        boolean rejects(StoredIssuerData stored);

        record Unchecked() implements StoredRowState {

            static final Unchecked INSTANCE = new Unchecked();

            @Override
            public boolean rejects(StoredIssuerData stored) {
                return false;
            }
        }

        /**
         * A stored row with this send time did not parse and is skipped.
         */
        record Unparsable(Instant fetchedAt) implements StoredRowState {

            @Override
            public boolean rejects(StoredIssuerData stored) {
                return stored.fetchedAt().equals(fetchedAt);
            }
        }
    }
}
