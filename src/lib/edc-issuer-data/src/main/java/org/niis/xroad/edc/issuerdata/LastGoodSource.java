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
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Serves issuer data from a copy for the cache time, downloads it again after that, and falls back to the last good
 * copy while the issuer does not answer, up to a deadline set by the maximum age.
 * <p>
 * The deadline of a copy is the earlier of its stored {@code validUntil} and its download time plus the cache time
 * plus the maximum age read at the moment of the check. A copy is never served past the expiry the data states itself.
 * <p>
 * Every successful download is also written to the {@link IssuerDataStore}. The store is read only when a download
 * fails: the later of the memory copy and the stored copy is then the candidate. A stored copy is read again through
 * {@link IssuerDataFetcher#parse} like a fresh download, and it is never served without a download attempt first.
 *
 * @param <P> the parsed form of the data
 */
public class LastGoodSource<P> {

    static final Duration SERVE_MARGIN = Duration.ofSeconds(5);

    private final IssuerDataFetcher<P> fetcher;
    private final Clock clock;
    private final Duration cacheTime;
    private final Supplier<Result<Duration>> maximumAge;
    private final IssuerDataStore store;
    private final Monitor monitor;
    private final Cache<String, Entry<P>> entries;

    public LastGoodSource(IssuerDataFetcher<P> fetcher, Clock clock, Ticker ticker, Executor executor, Duration cacheTime,
                          Supplier<Result<Duration>> maximumAge, IssuerDataStore store, Monitor monitor) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.cacheTime = cacheTime;
        this.maximumAge = maximumAge;
        this.store = store;
        this.monitor = monitor;
        this.entries = Caffeine.newBuilder().ticker(ticker).executor(executor).build();
    }

    /**
     * Never throws. A failure carries the download or parse failure when no usable copy exists.
     */
    @Nonnull
    public Result<LastGoodCopy<P>> lookup(String key) {
        var entry = entries.getIfPresent(key);
        var copy = entry == null ? null : entry.copy();
        if (copy != null && isFresh(copy)) {
            return Result.success(copy);
        }
        var downloaded = download(key);
        if (downloaded.succeeded()) {
            return downloaded;
        }
        var stored = entry != null && entry.storedCopyInvalid() ? null : storedCopy(key, copy);
        var candidate = stored != null ? stored : copy;
        return candidate != null && usableAfterFailure(key, candidate) ? Result.success(candidate) : downloaded;
    }

    private boolean isFresh(LastGoodCopy<P> copy) {
        var now = clock.instant();
        return now.isBefore(copy.fetchedAt().plus(cacheTime)) && !pastOwnExpiry(copy, now);
    }

    private boolean pastOwnExpiry(LastGoodCopy<P> copy, Instant now) {
        return fetcher.ownExpiry(copy.parsed()).map(expiry -> !now.isBefore(expiry)).orElse(false);
    }

    private Result<LastGoodCopy<P>> download(String key) {
        return fetcher.download(key)
                .compose(payload -> fetcher.parse(key, payload).map(parsed -> keep(key, payload, parsed)));
    }

    private LastGoodCopy<P> keep(String key, String payload, P parsed) {
        var fetchedAt = clock.instant();
        var validUntil = fetchedAt.plus(cacheTime).plus(maximumAgeOrZero(key));
        var copy = new LastGoodCopy<>(payload, parsed, fetchedAt, validUntil);
        entries.put(key, new Entry<>(copy, false));
        var written = store.write(fetcher.kind(), key, new StoredIssuerData(payload, fetchedAt, validUntil));
        if (written.failed()) {
            monitor.warning("Issuer data of '%s' could not be stored: %s".formatted(key, written.getFailureDetail()));
        }
        return copy;
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

    @Nullable
    private LastGoodCopy<P> storedCopy(String key, @Nullable LastGoodCopy<P> memoryCopy) {
        var row = store.read(fetcher.kind(), key);
        if (row.failed()) {
            if (row.getFailure().getReason() != StoreFailure.Reason.NOT_FOUND) {
                monitor.warning("Stored issuer data of '%s' could not be read: %s".formatted(key, row.getFailureDetail()));
            }
            return null;
        }
        var stored = row.getContent();
        if (memoryCopy != null && !stored.fetchedAt().isAfter(memoryCopy.fetchedAt())) {
            return null;
        }
        var parsed = fetcher.parse(key, stored.payload());
        if (parsed.failed()) {
            monitor.warning("Stored issuer data of '%s' is not usable: %s".formatted(key, parsed.getFailureDetail()));
            if (!parsed.isRetryable()) {
                entries.put(key, new Entry<>(memoryCopy, true));
            }
            return null;
        }
        return new LastGoodCopy<>(stored.payload(), parsed.getContent(), stored.fetchedAt(), stored.validUntil());
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

    /**
     * @param copy              the last good download held in memory, if any
     * @param storedCopyInvalid true when the stored copy failed to parse; it stays ignored until the next successful
     *                          download
     */
    private record Entry<P>(@Nullable LastGoodCopy<P> copy, boolean storedCopyInvalid) {
    }
}
