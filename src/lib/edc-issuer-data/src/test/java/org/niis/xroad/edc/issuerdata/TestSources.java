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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

/**
 * Builds the {@link LastGoodSource} of tests that drive it with a test clock. The send time of each download is the time
 * of the clock, the cache has no ticker and the maintenance scheduler is a mock.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestSources {

    private static final long MAXIMUM_ENTRIES = 128;

    static <P> LastGoodSource<P> source(IssuerDataFetcher<P> fetcher, Clock clock, Executor refreshExecutor,
                                        Duration cacheTime, Duration downloadWait, Supplier<Result<Duration>> maximumAge,
                                        IssuerDataStore store) {
        return new LastGoodSource<>(new ClockStampingFetcher<>(fetcher, clock), clock, Ticker.disabledTicker(),
                refreshExecutor, mock(ScheduledExecutorService.class), cacheTime,
                TestSettings.settings(MAXIMUM_ENTRIES, downloadWait), maximumAge, store, mock(Monitor.class));
    }
}
