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
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessResp;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessWinner;
import org.niis.xroad.edc.protocol.assetaccess.SecurityServerAuthority;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAccessCacheTest {

    /**
     * Runs the load on the calling thread, so it (and the expiry Caffeine derives from its result) completes
     * before {@link AssetAccessCache#get} returns, letting {@link ManualTicker} advance time deterministically
     * right after.
     */
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

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
        cache = new AssetAccessCache(cacheProperties, ticker, DIRECT_EXECUTOR);
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
        cache = new AssetAccessCache(cacheProperties, ticker, DIRECT_EXECUTOR);
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

    @Test
    void concurrentLookupsOfTheSameKeyShareOneLoadAndReturnTheSameResult() throws Exception {
        // DIRECT_EXECUTOR would run the load inline inside the first caller's cache lookup, so the second
        // caller would block on Caffeine's internal per-key map lock rather than on a real in-flight future.
        // This test needs the production constructor's real load executor, so the first caller's lookup
        // returns (with the load still running on its own thread) before the second caller joins it.
        var cacheUnderTest = new AssetAccessCache(cacheProperties);
        var key = key("asset-1");
        var loaderStarted = new CountDownLatch(1);
        var releaseLoad = new CountDownLatch(1);
        var loaderEntries = new AtomicInteger();
        var results = new AtomicReferenceArray<AssetAccessCache.CachedEntry>(2);

        Function<AssetAccessCache.CacheKey, AssetAccessCache.CachedEntry> blockingLoader = k -> {
            loaderEntries.incrementAndGet();
            loaderStarted.countDown();
            awaitQuietly(releaseLoad);
            return load(60);
        };
        var first = Thread.ofPlatform().start(() -> results.set(0, cacheUnderTest.get(key, blockingLoader)));
        assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).as("first load started").isTrue();
        var second = Thread.ofPlatform().start(() -> results.set(1, cacheUnderTest.get(key, blockingLoader)));
        // The load is still held, so a parked second caller is waiting on the in-flight future,
        // not reading a completed cache entry.
        awaitWaiting(second);

        releaseLoad.countDown();
        first.join(5_000);
        second.join(5_000);

        assertThat(loaderEntries.get()).as("loader invocations for the shared key").isEqualTo(1);
        assertThat(loadCount.get()).isEqualTo(1);
        assertThat(results.get(0)).as("first caller result").isNotNull();
        assertThat(results.get(1)).as("second caller result").isNotNull().isSameAs(results.get(0));
    }

    @Test
    void aFailedLoadIsNotCachedAndTheFailureReachesTheCaller() {
        var key = key("asset-1");
        var failure = new IllegalStateException("control plane unreachable");

        assertThatThrownBy(() -> cache.get(key, k -> {
            loadCount.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        var result = cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(2);
        assertThat(result.response().endpoint()).isEqualTo("http://provider/api/data");
    }

    /**
     * On {@link #DIRECT_EXECUTOR}, the load and its completion run inline on the calling thread, so a failed
     * future is always gone from the cache well before this method returns. Caffeine's own cache clears a
     * failed future from a callback that can instead run on the load executor's thread, after {@link
     * AssetAccessCache#get} has already woken the caller — so only a real, separate-thread load executor can
     * expose a same-key retry that lands in that window. Many fresh keys, with no pause between a key's
     * failing load and its retry, make that window land almost every time it still exists.
     */
    @Test
    void aFailedLoadIsNotVisibleToAnImmediateRetryOnARealExecutor() {
        var cacheUnderTest = new AssetAccessCache(cacheProperties);
        var failure = new IllegalStateException("control plane unreachable");
        int iterations = 1_000;

        for (int i = 0; i < iterations; i++) {
            var key = key("retry-" + i);

            assertThatThrownBy(() -> cacheUnderTest.get(key, k -> {
                throw failure;
            })).isSameAs(failure);

            var retried = new AtomicBoolean();
            var result = cacheUnderTest.get(key, k -> {
                retried.set(true);
                return load(60);
            });

            assertThat(retried).as("iteration %d: the retry right after a failure must call the loader again", i).isTrue();
            assertThat(result.response().endpoint()).isEqualTo("http://provider/api/data");
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitWaiting(Thread thread) {
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> thread.getState() == Thread.State.WAITING);
    }

    @Test
    void invalidatingDropsTheEntryAndTheNextLookupLoadsAfresh() {
        var key = key("asset-1");
        cache.get(key, k -> load(60));

        cache.invalidate("test-participant-ctx", "asset-1", null);
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(2);
    }

    @Test
    void invalidatingAnInFlightLoadDropsItTooSoTheNextLookupLoadsAfresh() throws Exception {
        // A direct executor runs the load before the key is even visible as in-flight (see DIRECT_EXECUTOR),
        // so this test needs the production constructor's real load executor to observe the in-flight window.
        var cacheUnderTest = new AssetAccessCache(cacheProperties);
        var key = key("asset-1");
        var loaderStarted = new CountDownLatch(1);
        var releaseLoad = new CountDownLatch(1);

        var inFlight = Thread.ofPlatform().start(() -> cacheUnderTest.get(key, k -> {
            loaderStarted.countDown();
            awaitQuietly(releaseLoad);
            return load(60);
        }));
        assertThat(loaderStarted.await(5, TimeUnit.SECONDS)).as("in-flight load started").isTrue();

        cacheUnderTest.invalidate("test-participant-ctx", "asset-1", null);
        releaseLoad.countDown();
        inFlight.join(5_000);

        cacheUnderTest.get(key, k -> load(60));

        assertThat(loadCount.get()).as("one load for the invalidated in-flight call, one for the fresh lookup")
                .isEqualTo(2);
    }

    @Test
    void invalidatingDropsEveryProviderCandidateOfTheAssetAndLeavesOthersCached() {
        var candidateA = new AssetAccessCache.CacheKey("test-participant-ctx", "asset-1",
                Set.of(candidate("provider-a")), "http-dsp-profile-2025-1", "DEV:COM:222:A");
        var candidateB = new AssetAccessCache.CacheKey("test-participant-ctx", "asset-1",
                Set.of(candidate("provider-b")), "http-dsp-profile-2025-1", "DEV:COM:222:A");
        var otherClient = new AssetAccessCache.CacheKey("test-participant-ctx", "asset-1",
                Set.of(candidate("provider-a")), "http-dsp-profile-2025-1", "DEV:COM:222:B");
        var otherAsset = key("asset-2");
        Stream.of(candidateA, candidateB, otherClient, otherAsset).forEach(k -> cache.get(k, x -> load(60)));
        loadCount.set(0);

        cache.invalidate("test-participant-ctx", "asset-1", "DEV:COM:222:A");
        Stream.of(candidateA, candidateB, otherClient, otherAsset).forEach(k -> cache.get(k, x -> load(60)));

        assertThat(loadCount.get()).isEqualTo(2);
    }

    @Test
    void theSameCandidatesInADifferentOrderShareOneEntry() {
        var ab = new LinkedHashSet<>(List.of(candidate("provider-a"), candidate("provider-b")));
        var ba = new LinkedHashSet<>(List.of(candidate("provider-b"), candidate("provider-a")));

        cache.get(keyOf(ab), k -> load(60));
        cache.get(keyOf(ba), k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void separatelyBuiltCandidatesEqualInValueShareOneEntry() {
        var firstA = candidate("provider-a");
        var firstB = candidate("provider-b");
        var secondA = freshCandidate("provider-a");
        var secondB = freshCandidate("provider-b");
        var first = new LinkedHashSet<>(List.of(firstA, firstB));
        var second = new LinkedHashSet<>(List.of(secondA, secondB));

        assertThat(secondA).isNotSameAs(firstA);
        assertThat(secondB).isNotSameAs(firstB);
        assertThat(secondA.counterPartyId()).isNotSameAs(firstA.counterPartyId());
        assertThat(secondA.counterPartyAddress()).isNotSameAs(firstA.counterPartyAddress());
        assertThat(secondA).isEqualTo(firstA);
        assertThat(secondB).isEqualTo(firstB);
        assertThat(keyOf(second)).isEqualTo(keyOf(first));

        cache.get(keyOf(first), k -> load(60));
        cache.get(keyOf(second), k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void theSameCounterPartyAtADifferentAddressIsADifferentEntry() {
        var atFirstAddress = Set.of(new AcquisitionCandidate("provider-a", "http://first/dsp"));
        var atSecondAddress = Set.of(new AcquisitionCandidate("provider-a", "http://second/dsp"));

        cache.get(keyOf(atFirstAddress), k -> load(60));
        cache.get(keyOf(atSecondAddress), k -> load(60));

        assertThat(loadCount.get()).isEqualTo(2);
    }

    @Test
    void theEntryRecordsTheWinnerKeyFromTheResponse() {
        var response = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setWinner(AssetAccessWinner.newBuilder()
                        .setCounterPartyId("provider-b")
                        .setCounterPartyAddress("http://SS1.example:8183/dsp"))
                .build();
        var entry = AssetAccessCache.CachedEntry.from(response);
        assertThat(entry.winnerKey())
                .isEqualTo("ss1.example:8183")
                .isEqualTo(SecurityServerAuthority.of("http://SS1.example:8183/dsp"));
        assertThat(entry.response().endpoint()).isEqualTo("http://provider/api/data");
    }

    @Test
    void theResponseOfAnEntryCarriesTheWinnerFromTheResponse() {
        var response = AcquireAssetAccessResp.newBuilder()
                .setEndpoint("http://provider/api/data")
                .setWinner(AssetAccessWinner.newBuilder()
                        .setCounterPartyId("provider-b")
                        .setCounterPartyAddress("http://ss1.example/dsp"))
                .build();

        var result = AssetAccessCache.CachedEntry.from(response).response();

        assertThat(result.winner().counterPartyId()).isEqualTo("provider-b");
        assertThat(result.winner().counterPartyAddress()).isEqualTo("http://ss1.example/dsp");
    }

    @Test
    void removalByWinnerDropsEveryEntryWonByTheServerAndKeepsTheOthers() {
        var wonBySsAElsewhere = new AssetAccessCache.CacheKey("ctx-2", "asset-2",
                Set.of(candidate("provider-a"), candidate("provider-b")),
                "http-dsp-profile-2025-1", "DEV:COM:222:B");
        var wonBySsA = keyWith("ctx-1", "asset-1", "provider-a", "DEV:COM:222:A");
        var wonBySsAOtherPort = keyWith("ctx-1", "asset-3", "provider-a", "DEV:COM:222:A");
        var wonBySsAOtherClient = keyWith("ctx-1", "asset-5", "provider-a", "DEV:COM:222:B");
        var wonBySsB = keyWith("ctx-1", "asset-1", "provider-b", "DEV:COM:222:A");
        var wonByOtherMemberServer = keyWith("ctx-3", "asset-1", "provider-c", "DEV:COM:222:A");
        var wonByDefaultServer = keyWith("ctx-1", "asset-4", "provider-d", "DEV:COM:222:A");
        cache.get(wonBySsA, k -> loadWonBy("provider-a", "https://ss-a.example:8183/api/dsp/DEV:COM:1/p"));
        cache.get(wonBySsAElsewhere, k -> loadWonBy("provider-a", "https://ss-a.example:8183/api/dsp/DEV:COM:2/p"));
        cache.get(wonBySsAOtherPort, k -> loadWonBy("provider-a", "https://ss-a.example:9999/api/dsp/DEV:COM:1/p"));
        cache.get(wonBySsAOtherClient, k -> loadWonBy("provider-a", "https://SS-A.example:8183/api/dsp/DEV:COM:1/p"));
        cache.get(wonBySsB, k -> loadWonBy("provider-b", "https://ss-b.example:8183/api/dsp/DEV:COM:1/p"));
        cache.get(wonByOtherMemberServer, k -> loadWonBy("provider-c", "https://ss-c.example:8183/api/dsp/DEV:COM:3/p"));
        cache.get(wonByDefaultServer, k -> load(60));
        loadCount.set(0);

        cache.removeWonBy("https://ss-a.example:8183/dsp");

        Stream.of(wonBySsA, wonBySsAElsewhere, wonBySsAOtherClient).forEach(k -> cache.get(k, x -> load(60)));
        assertThat(loadCount.get()).isEqualTo(3);
        loadCount.set(0);
        Stream.of(wonBySsAOtherPort, wonBySsB, wonByOtherMemberServer, wonByDefaultServer)
                .forEach(k -> cache.get(k, x -> load(60)));
        assertThat(loadCount.get()).isZero();
    }

    @Test
    void aSecondRemovalForTheSameServerChangesNothingAndDoesNotFail() {
        var wonBySsA = keyWith("ctx-1", "asset-1", "provider-a", "DEV:COM:222:A");
        var wonBySsB = keyWith("ctx-1", "asset-1", "provider-b", "DEV:COM:222:A");
        cache.get(wonBySsA, k -> loadWonBy("provider-a", "https://ss-a.example:8183/p"));
        cache.get(wonBySsB, k -> loadWonBy("provider-b", "https://ss-b.example:8183/p"));
        cache.removeWonBy("https://ss-a.example:8183/dsp");
        cache.get(wonBySsA, k -> loadWonBy("provider-a", "https://ss-a.example:8183/p"));
        cache.removeWonBy("https://ss-a.example:8183/dsp");
        cache.removeWonBy("https://ss-a.example:8183/dsp");
        loadCount.set(0);

        cache.get(wonBySsB, k -> load(60));
        cache.get(wonBySsA, k -> loadWonBy("provider-a", "https://ss-a.example:8183/p"));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void concurrentRemovalsForTheSameServerFailNowhereAndLeaveNoEntryWonByIt() throws Exception {
        var keys = IntStream.range(0, 200)
                .mapToObj(i -> keyWith("ctx-1", "asset-" + i, "provider-a", "DEV:COM:222:A"))
                .toList();
        keys.forEach(k -> cache.get(k, x -> loadWonBy("provider-a", "https://ss-a.example:8183/p")));
        var executor = Executors.newFixedThreadPool(2);
        var startGate = new CountDownLatch(1);
        try {
            var futures = Stream.of(1, 2)
                    .map(_ -> executor.submit(() -> {
                        startGate.await();
                        cache.removeWonBy("https://ss-a.example:8183/dsp");
                        return null;
                    }))
                    .toList();
            startGate.countDown();
            for (var future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        loadCount.set(0);

        keys.forEach(k -> cache.get(k, x -> load(60)));

        assertThat(loadCount.get()).isEqualTo(keys.size());
    }

    @Test
    void anEntryAtAHostWithTheSameNameInADifferentCaseIsDropped() {
        var key = keyWith("ctx-1", "asset-1", "provider-a", null);
        cache.get(key, k -> loadWonBy("provider-a", "https://SS-A.example:8183/p"));
        loadCount.set(0);

        cache.removeWonBy("https://ss-a.example:8183/dsp");
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void anEntryAtAHostWithTheSameNameInLowerCaseIsDroppedByARemovalForUpperCase() {
        var key = keyWith("ctx-1", "asset-1", "provider-a", null);
        cache.get(key, k -> loadWonBy("provider-a", "https://ss-a.example:8183/p"));
        loadCount.set(0);

        cache.removeWonBy("https://SS-A.example:8183/p");
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void anEntryAtADefaultPortIsDroppedByARemovalNamingThePortExplicitly() {
        var key = keyWith("ctx-1", "asset-1", "provider-a", null);
        cache.get(key, k -> loadWonBy("provider-a", "https://ss-a.example/p"));
        loadCount.set(0);

        cache.removeWonBy("https://ss-a.example:443/p");
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void anEntryAtAHostTheJdkUriRejectsIsDroppedByARemovalForTheSameAddress() {
        var address = "https://ss_a:8183/api/dsp";
        var key = keyWith("ctx-1", "asset-1", "provider-a", null);
        cache.get(key, k -> loadWonBy("provider-a", address));
        loadCount.set(0);

        cache.removeWonBy(address);
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    @Test
    void anEntryAtAnUnparsableWinnerAddressIsDroppedByARemovalForTheSameStringAndKeptByAnother() {
        var key = keyWith("ctx-1", "asset-1", "provider-a", null);
        cache.get(key, k -> loadWonBy("provider-a", "not a url"));
        loadCount.set(0);

        cache.removeWonBy("https://ss-a.example:8183/p");
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isZero();

        cache.removeWonBy("not a url");
        cache.get(key, k -> load(60));

        assertThat(loadCount.get()).isEqualTo(1);
    }

    private AssetAccessCache.CachedEntry load(long expiresInSeconds) {
        loadCount.incrementAndGet();
        return AssetAccessCache.CachedEntry.of(
                new AssetAccessResponse("http://provider/api/data", "token",
                        new AcquisitionCandidate("provider-x", "https://ss-x.example:8183/p")),
                Instant.now().getEpochSecond() + expiresInSeconds);
    }

    private AssetAccessCache.CachedEntry loadWonBy(String counterPartyId, String counterPartyAddress) {
        loadCount.incrementAndGet();
        return AssetAccessCache.CachedEntry.of(
                new AssetAccessResponse("http://provider/api/data", "token",
                        new AcquisitionCandidate(counterPartyId, counterPartyAddress)),
                Instant.now().getEpochSecond() + 60);
    }

    private static AssetAccessCache.CacheKey keyWith(String participantContextId, String assetId, String candidateId,
                                                     String consumerClientId) {
        return new AssetAccessCache.CacheKey(participantContextId, assetId, Set.of(candidate(candidateId)),
                "http-dsp-profile-2025-1", consumerClientId);
    }

    private static AssetAccessCache.CacheKey key(String assetId) {
        return keyOf(Set.of(candidate("provider-1")), assetId);
    }

    private static AssetAccessCache.CacheKey keyOf(Set<AcquisitionCandidate> candidates) {
        return keyOf(candidates, "asset-1");
    }

    private static AssetAccessCache.CacheKey keyOf(Set<AcquisitionCandidate> candidates, String assetId) {
        return new AssetAccessCache.CacheKey("test-participant-ctx", assetId, candidates,
                "http-dsp-profile-2025-1", null);
    }

    private static AcquisitionCandidate candidate(String counterPartyId) {
        return new AcquisitionCandidate(counterPartyId, "http://" + counterPartyId + "/dsp");
    }

    private static AcquisitionCandidate freshCandidate(String counterPartyId) {
        return new AcquisitionCandidate(String.valueOf(counterPartyId.toCharArray()),
                String.join("", "http://", counterPartyId, "/dsp"));
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
