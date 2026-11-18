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
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

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
 *
 * @param <P> the parsed form of the data
 */
public class LastGoodSource<P> {

    static final Duration SERVE_MARGIN = Duration.ofSeconds(5);

    private final IssuerDataKind<P> kind;
    private final Clock clock;
    private final Duration cacheTime;
    private final Supplier<Result<Duration>> maximumAge;
    private final Monitor monitor;
    private final Cache<String, LastGoodCopy<P>> copies;

    public LastGoodSource(IssuerDataKind<P> kind, Clock clock, Ticker ticker, Executor executor, Duration cacheTime,
                          Supplier<Result<Duration>> maximumAge, Monitor monitor) {
        this.kind = kind;
        this.clock = clock;
        this.cacheTime = cacheTime;
        this.maximumAge = maximumAge;
        this.monitor = monitor;
        this.copies = Caffeine.newBuilder().ticker(ticker).executor(executor).build();
    }

    /**
     * Never throws. A failure carries the download or parse failure when no usable copy exists.
     */
    @Nonnull
    public Result<LastGoodCopy<P>> lookup(String key) {
        var copy = copies.getIfPresent(key);
        if (copy != null && isFresh(copy)) {
            return Result.success(copy);
        }
        var downloaded = download(key);
        if (downloaded.succeeded() || copy == null) {
            return downloaded;
        }
        return usableAfterFailure(key, copy) ? Result.success(copy) : downloaded;
    }

    private boolean isFresh(LastGoodCopy<P> copy) {
        var now = clock.instant();
        return now.isBefore(copy.fetchedAt().plus(cacheTime)) && !pastOwnExpiry(copy, now);
    }

    private boolean pastOwnExpiry(LastGoodCopy<P> copy, Instant now) {
        return kind.ownExpiry(copy.parsed()).map(expiry -> !now.isBefore(expiry)).orElse(false);
    }

    private Result<LastGoodCopy<P>> download(String key) {
        return kind.download(key)
                .compose(payload -> kind.parse(key, payload).map(parsed -> store(key, payload, parsed)));
    }

    private LastGoodCopy<P> store(String key, String payload, P parsed) {
        var fetchedAt = clock.instant();
        var validUntil = fetchedAt.plus(cacheTime).plus(maximumAgeOrZero(key));
        var copy = new LastGoodCopy<>(payload, parsed, fetchedAt, validUntil);
        copies.put(key, copy);
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
}
