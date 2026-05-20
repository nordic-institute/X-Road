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
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;

/**
 * gRPC-based implementation of {@link AssetAccessAcquisitionService} that calls
 * the ds-control-plane AssetAccessService to acquire asset access responses.
 */
@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class AssetAccessRpcClient extends AbstractRpcClient implements AssetAccessAcquisitionService {

    private final RpcChannelFactory rpcChannelFactory;
    private final AssetAccessRpcChannelProperties channelProperties;
    private final AssetAccessClientProperties clientProperties;

    private ManagedChannel channel;
    private AssetAccessServiceGrpc.AssetAccessServiceBlockingStub accessServiceBlockingStub;

    // Null when caching is disabled via configuration; acquireAssetAccess() bypasses to a direct gRPC call.
    private AssetAccessCache cache;

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

    private AssetAccessCache buildCache() {
        var cacheProps = clientProperties.cache();
        if (!cacheProps.enabled()) {
            log.info("AssetAccess cache disabled by configuration");
            return null;
        }
        return new AssetAccessCache(cacheProps);
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
        var cacheKey = new AssetAccessCache.CacheKey(
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

    private AssetAccessCache.CachedEntry loadAssetAccess(AssetAccessCache.CacheKey cacheKey) {
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
        return new AssetAccessCache.CachedEntry(assetAccessResponse, expiresAt);
    }
}
