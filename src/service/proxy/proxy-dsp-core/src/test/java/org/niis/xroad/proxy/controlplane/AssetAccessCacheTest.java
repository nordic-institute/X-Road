/*
 * The MIT License
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

package org.niis.xroad.proxy.controlplane;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessCacheTest {

    @Mock
    private AssetAccessClientProperties.Cache cacheProperties;

    private final ManualTicker ticker = new ManualTicker();
    private AssetAccessCache cache;
    private AtomicInteger loadCount;

    @BeforeEach
    void setUp() {
        when(cacheProperties.maximumSize()).thenReturn(10_000L);
        when(cacheProperties.defaultTtl()).thenReturn(Duration.ofMinutes(5));
        when(cacheProperties.margin()).thenReturn(Duration.ofSeconds(5));
        cache = new AssetAccessCache(cacheProperties, ticker);
        loadCount = new AtomicInteger();
    }

    @ParameterizedTest(name = "a lookup {0}s into a 60s entry with a 5s margin loads {1} time(s)")
    @CsvSource({
            "30, 1",
            "50, 1",
            "56, 2",
            "61, 2",
    })
    void reloadsOnlyOnceTheMarginAheadOfExpiryIsReached(long secondsElapsed, int expectedLoads) {
        var key = key("asset-1");
        cache.get(key, k -> load(60));

        ticker.advance(Duration.ofSeconds(secondsElapsed));
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(expectedLoads);
    }

    @Test
    void aMarginThatWouldOutliveTheEntryIsClampedToAOneSecondLifetime() {
        when(cacheProperties.margin()).thenReturn(Duration.ofSeconds(10));
        cache = new AssetAccessCache(cacheProperties, ticker);
        var key = key("asset-1");
        cache.get(key, k -> load(5));

        ticker.advance(Duration.ofMillis(1_500));
        cache.get(key, k -> load(5));

        assertThat(loadCount.get()).isEqualTo(2);
    }

    @Test
    void anEntryAlreadyPastItsExpiryIsNotRetained() {
        var key = key("asset-1");
        cache.get(key, k -> load(-10));

        ticker.advance(Duration.ofMillis(10));
        cache.get(key, k -> load(-10));

        assertThat(loadCount.get()).isEqualTo(2);
    }

    private AssetAccessCache.CachedEntry load(long expiresInSeconds) {
        loadCount.incrementAndGet();
        return new AssetAccessCache.CachedEntry(
                new AssetAccessResponse("http://provider/api/data", "token"),
                Instant.now().getEpochSecond() + expiresInSeconds);
    }

    private static AssetAccessCache.CacheKey key(String assetId) {
        return new AssetAccessCache.CacheKey("test-participant-ctx", assetId, "provider-1",
                "http://provider/dsp", "http-dsp-profile-2025-1", null);
    }

    private static final class ManualTicker implements Ticker {
        private long nanos;

        @Override
        public long read() {
            return nanos;
        }

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }
    }
}
