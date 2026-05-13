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
package org.niis.xroad.proxy.controlplane;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.proxy.core.clientproxy.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.clientproxy.dsp.ControlPlaneNegotiationService;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-based implementation of {@link ControlPlaneNegotiationService} that calls
 * the ds-control-plane AssetAccessService to acquire asset access responses.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class AssetAccessRpcClient extends AbstractRpcClient implements ControlPlaneNegotiationService {

    private final RpcChannelFactory rpcChannelFactory;
    private final AssetAccessRpcChannelProperties channelProperties;
    private final AssetAccessClientProperties clientProperties;

    private ManagedChannel channel;
    private AssetAccessServiceGrpc.AssetAccessServiceBlockingStub accessServiceBlockingStub;

    // Null when caching is disabled via configuration; acquireAssetAccess() bypasses to a direct gRPC call.
    private Cache<CacheKey, CachedEntry> cache;

    /**
     * Cache key for acquired asset access responses.
     *
     * <p>All five fields are included even though {@code participantContextId},
     * {@code counterPartyAddress}, and {@code protocol} are effectively constant
     * today. Rationale (Phase 8 decisions D-05..D-08):
     * <ul>
     *   <li>{@code participantContextId} — currently a hardcoded constant from
     *       {@link AssetAccessClientProperties#participantContextId()};
     *       requirement CFG-01 (future) will make it per-request-configurable.</li>
     *   <li>{@code counterPartyAddress} — deterministic per {@code counterPartyId}
     *       today; Phase 9 PROXY-03 will resolve it from GlobalConf. Included
     *       for defence-in-depth at zero cost.</li>
     *   <li>{@code protocol} — one DSP protocol today; multiple versions
     *       foreseeable.</li>
     * </ul>
     * <p>General rule: a cache must key on every field that affects the response.
     */
    private record CacheKey(String participantContextId, String assetId, String counterPartyId,
                            String counterPartyAddress, String protocol) { }

    private record CachedEntry(AssetAccessResponse response, long expiresAtEpochSeconds) { }

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.PROXY;
    }

    @Override
    public ManagedChannel getChannel() {
        return channel;
    }

    /**
     * Initializes the gRPC channel and blocking stub for the asset access service.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(),
                channelProperties.host(), channelProperties.port());
        channel = rpcChannelFactory.createChannel(channelProperties);
        // Deadline is applied at channel level by RpcChannelFactory#timeoutInterceptor;
        // per-stub interceptors are therefore unnecessary here.
        accessServiceBlockingStub = AssetAccessServiceGrpc.newBlockingStub(channel).withWaitForReady();
        cache = buildCache();
    }

    private Cache<CacheKey, CachedEntry> buildCache() {
        var cacheProps = clientProperties.cache();
        if (!cacheProps.enabled()) {
            log.info("AssetAccess cache disabled by configuration");
            return null;
        }
        long defaultTtlNanos = cacheProps.defaultTtl().toNanos();
        return Caffeine.newBuilder()
                .maximumSize(cacheProps.maximumSize())
                .expireAfter(new Expiry<CacheKey, CachedEntry>() {
                    @Override
                    public long expireAfterCreate(CacheKey key, CachedEntry value, long currentTime) {
                        if (value.expiresAtEpochSeconds() <= 0) {
                            return defaultTtlNanos;
                        }
                        long ttlSeconds = value.expiresAtEpochSeconds() - Instant.now().getEpochSecond();
                        return TimeUnit.SECONDS.toNanos(Math.max(ttlSeconds, 0));
                    }

                    @Override
                    public long expireAfterUpdate(CacheKey key, CachedEntry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    @Override
                    public long expireAfterRead(CacheKey key, CachedEntry value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    public AssetAccessResponse acquireAssetAccess(String assetId, String counterPartyId, String counterPartyAddress) {
        var cacheKey = new CacheKey(
                clientProperties.participantContextId(),
                assetId,
                counterPartyId,
                counterPartyAddress,
                clientProperties.protocol());
        if (cache == null) {
            return loadAssetAccess(cacheKey).response();
        }
        // cache.get(key, loader) serializes concurrent loads on the same key inside Caffeine,
        // collapsing N parallel proxy threads into a single gRPC round-trip per cache miss.
        return cache.get(cacheKey, this::loadAssetAccess).response();
    }

    private CachedEntry loadAssetAccess(CacheKey cacheKey) {
        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId(cacheKey.participantContextId())
                .setAssetId(cacheKey.assetId())
                .setCounterPartyId(cacheKey.counterPartyId())
                .setCounterPartyAddress(cacheKey.counterPartyAddress())
                .setProtocol(cacheKey.protocol())
                .build();
        var response = exec(() -> accessServiceBlockingStub.acquire(request));
        long expiresAt = response.hasExpiresAtEpochSeconds() ? response.getExpiresAtEpochSeconds() : 0;
        var assetAccessResponse = new AssetAccessResponse(
                response.getEndpoint(),
                response.hasAuthorization() ? response.getAuthorization() : null);
        return new CachedEntry(assetAccessResponse, expiresAt);
    }
}
