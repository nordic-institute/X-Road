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

import org.junit.jupiter.api.Test;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssetAccessCacheConcurrencyTest {

    private static final int INDEPENDENT_KEYS = 32;

    @Test
    void loadsOfIndependentKeysRunAtTheSameTime() throws Exception {
        var cacheProperties = mock(AssetAccessClientProperties.Cache.class);
        when(cacheProperties.maximumSize()).thenReturn(10_000L);
        when(cacheProperties.defaultTtl()).thenReturn(Duration.ofMinutes(5));
        when(cacheProperties.margin()).thenReturn(Duration.ofSeconds(5));
        var cache = new AssetAccessCache(cacheProperties);

        var loading = new AtomicInteger();
        var allLoading = new CountDownLatch(INDEPENDENT_KEYS);
        var releaseLoads = new CountDownLatch(1);
        var callers = new ArrayList<Thread>();
        for (int i = 0; i < INDEPENDENT_KEYS; i++) {
            var key = new AssetAccessCache.CacheKey("participant-" + i, "asset-" + i, "provider-1",
                    "http://provider/dsp", "http-dsp-profile-2025-1", "DEV:COM:4321:client-" + i);
            callers.add(Thread.ofPlatform().start(() -> cache.get(key, k -> {
                loading.incrementAndGet();
                allLoading.countDown();
                awaitQuietly(releaseLoads);
                return new AssetAccessCache.CachedEntry(new AssetAccessResponse("http://provider/api", "token"),
                        Instant.now().getEpochSecond() + 300);
            })));
        }
        try {
            allLoading.await(5, TimeUnit.SECONDS);
            assertThat(loading.get())
                    .as("loads in progress while every load blocks, out of %d independent keys", INDEPENDENT_KEYS)
                    .isEqualTo(INDEPENDENT_KEYS);
        } finally {
            releaseLoads.countDown();
            for (var caller : callers) {
                caller.join(5_000);
            }
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
