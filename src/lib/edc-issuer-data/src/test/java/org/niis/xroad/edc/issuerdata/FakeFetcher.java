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

import lombok.RequiredArgsConstructor;
import org.eclipse.edc.spi.result.Result;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A fetcher whose downloads the test switches on and off, makes refuse with a final failure or answer with a payload
 * that cannot be parsed, holds back per key and makes throw. Payloads are numbered, and the payload {@link #CORRUPT}
 * cannot be parsed. The send time of a download is the time of the clock when the download starts, unless
 * {@link #sendTime} is set.
 */
@RequiredArgsConstructor
final class FakeFetcher implements IssuerDataFetcher<String> {

    static final String CORRUPT = "corrupt";

    final AtomicInteger downloads = new AtomicInteger();
    final AtomicInteger attempts = new AtomicInteger();
    final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    volatile boolean down;
    volatile boolean refused;
    volatile boolean corruptAnswer;
    volatile boolean throwing;
    volatile boolean parseRetryable;
    volatile Optional<Instant> ownExpiry = Optional.empty();
    volatile Instant sendTime;
    private final Clock clock;

    @Override
    public IssuerDataKind kind() {
        return IssuerDataKind.STATUSLIST;
    }

    @Override
    public Result<Download> download(String key) {
        attempts.incrementAndGet();
        var sentAt = sendTime != null ? sendTime : clock.instant();
        awaitGate(key);
        if (throwing) {
            throw new IllegalStateException("fetcher broke");
        }
        if (down) {
            return Result.retryableFailure("issuer down");
        }
        if (refused) {
            return Result.failure("issuer refused the request");
        }
        if (corruptAnswer) {
            return Result.success(new Download(CORRUPT, sentAt));
        }
        return Result.success(new Download("payload-" + downloads.incrementAndGet(), sentAt));
    }

    private void awaitGate(String key) {
        var gate = gates.get(key);
        if (gate == null) {
            return;
        }
        try {
            gate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public Result<String> parse(String key, String payload) {
        if (CORRUPT.equals(payload)) {
            return parseRetryable ? Result.retryableFailure("issuer did not answer") : Result.failure("not a status list");
        }
        return Result.success(payload);
    }

    @Override
    public Optional<Instant> ownExpiry(String parsed) {
        return ownExpiry;
    }
}
