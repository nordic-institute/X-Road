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
import java.util.Optional;

/**
 * Wraps a real fetcher in a test that uses real HTTP and a test clock, and replaces the send time of each download
 * with the time of the clock. It holds no check logic.
 *
 * @param <P> the parsed form of the data
 */
@RequiredArgsConstructor
final class ClockStampingFetcher<P> implements IssuerDataFetcher<P> {

    private final IssuerDataFetcher<P> delegate;
    private final Clock clock;

    @Override
    public IssuerDataKind kind() {
        return delegate.kind();
    }

    @Override
    public Result<Download> download(String key) {
        return delegate.download(key).map(download -> new Download(download.payload(), clock.instant()));
    }

    @Override
    public Result<P> parse(String key, String payload) {
        return delegate.parse(key, payload);
    }

    @Override
    public Result<P> parseStored(String key, String payload) {
        return delegate.parseStored(key, payload);
    }

    @Override
    public Optional<Instant> ownExpiry(P parsed) {
        return delegate.ownExpiry(parsed);
    }
}
