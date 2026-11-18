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

import ee.ria.xroad.common.identifier.ClientId;

import com.github.benmanes.caffeine.cache.Ticker;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.edc.assetaccess.proto.AcquireAssetAccessReq;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessCandidate;
import org.niis.xroad.edc.assetaccess.proto.AssetAccessServiceGrpc;
import org.niis.xroad.edc.assetaccess.proto.ReportCounterPartyFailureReq;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.rpc.common.Empty;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-based implementation of {@link AssetAccessAcquisitionService} that calls
 * the ds-control-plane AssetAccessService to acquire asset access responses.
 *
 * <p>A negotiation refusal is remembered by {@link RefusalMemory} under the cache key for a few seconds: a repeat of
 * the call within that time is refused again with the code of the first refusal, without contacting the control plane.
 */
@Slf4j
@ApplicationScoped
public class AssetAccessRpcClient extends AbstractRpcClient implements AssetAccessAcquisitionService {

    private static final Duration FAILURE_REPORT_DEADLINE = Duration.ofSeconds(3);

    private final RpcChannelFactory rpcChannelFactory;
    private final ControlPlaneRpcChannelProperties channelProperties;
    private final AssetAccessClientProperties clientProperties;
    private final RefusalMemory refusalMemory;

    private ManagedChannel channel;
    private AssetAccessServiceGrpc.AssetAccessServiceBlockingStub accessServiceBlockingStub;
    private AssetAccessServiceGrpc.AssetAccessServiceStub failureReportStub;

    private AssetAccessCache cache;

    @Inject
    public AssetAccessRpcClient(RpcChannelFactory rpcChannelFactory, ControlPlaneRpcChannelProperties channelProperties,
                                AssetAccessClientProperties clientProperties) {
        this(rpcChannelFactory, channelProperties, clientProperties, Ticker.systemTicker());
    }

    AssetAccessRpcClient(RpcChannelFactory rpcChannelFactory, ControlPlaneRpcChannelProperties channelProperties,
                         AssetAccessClientProperties clientProperties, Ticker refusalTicker) {
        this.rpcChannelFactory = rpcChannelFactory;
        this.channelProperties = channelProperties;
        this.clientProperties = clientProperties;
        this.refusalMemory = new RefusalMemory(refusalTicker);
    }

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
        accessServiceBlockingStub = AssetAccessServiceGrpc.newBlockingStub(channel).withWaitForReady();
        failureReportStub = AssetAccessServiceGrpc.newStub(channel);
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
    public AssetAccessResponse acquireAssetAccess(String participantContextId, String assetId,
                                                  List<AcquisitionCandidate> candidates,
                                                  @Nullable ClientId consumerClientId,
                                                  Duration timeout) {
        var cacheKey = new AssetAccessCache.CacheKey(
                participantContextId,
                assetId,
                Set.copyOf(candidates),
                clientProperties.protocol(),
                consumerClientId != null ? consumerClientId.asEncodedId() : null);
        var remembered = refusalMemory.recall(cacheKey);
        if (remembered.isPresent()) {
            log.debug("Asset {} was refused for this call a moment ago, not asking again", assetId);
            throw remembered.get();
        }
        try {
            if (cache == null) {
                return loadAssetAccess(cacheKey, candidates, timeout).response();
            }
            return cache.get(cacheKey, key -> loadAssetAccess(key, candidates, timeout)).response();
        } catch (RuntimeException e) {
            refusalMemory.rememberIfRefusal(cacheKey, e);
            throw e;
        }
    }

    @Override
    public void invalidateAssetAccess(String participantContextId, String assetId, @Nullable ClientId consumerClientId) {
        if (cache != null) {
            cache.invalidate(participantContextId, assetId, consumerClientId != null ? consumerClientId.asEncodedId() : null);
        }
    }

    @Override
    public void invalidateAssetAccessWonBy(String counterPartyAddress) {
        if (cache != null) {
            cache.removeWonBy(counterPartyAddress);
        }
    }

    @Override
    public void reportCounterPartyFailure(String counterPartyId, String counterPartyAddress) {
        var request = ReportCounterPartyFailureReq.newBuilder()
                .setCounterPartyId(counterPartyId)
                .setCounterPartyAddress(counterPartyAddress)
                .build();
        try {
            failureReportStub.withDeadlineAfter(FAILURE_REPORT_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                    .reportCounterPartyFailure(request, new FailureReportObserver(counterPartyId, counterPartyAddress));
        } catch (RuntimeException e) {
            log.warn("Failed to report counter-party failure: counterPartyId={} counterPartyAddress={}",
                    counterPartyId, counterPartyAddress, e);
        }
    }

    private AssetAccessCache.CachedEntry loadAssetAccess(AssetAccessCache.CacheKey cacheKey,
                                                         List<AcquisitionCandidate> candidates,
                                                         Duration timeout) {
        var request = AcquireAssetAccessReq.newBuilder()
                .setParticipantContextId(cacheKey.participantContextId())
                .setAssetId(cacheKey.assetId())
                .setProtocol(cacheKey.protocol())
                .setTimeoutMillis(timeout.toMillis());
        candidates.forEach(candidate -> request.addCandidates(
                AssetAccessCandidate.newBuilder()
                        .setCounterPartyId(candidate.counterPartyId())
                        .setCounterPartyAddress(candidate.counterPartyAddress())));
        if (cacheKey.consumerClientId() != null) {
            request.setClientId(cacheKey.consumerClientId());
        }
        var waitMillis = timeout.plus(clientProperties.acquisitionDeadlineMargin()).toMillis();
        return AssetAccessCache.CachedEntry.from(exec(() -> accessServiceBlockingStub
                .withDeadlineAfter(waitMillis, TimeUnit.MILLISECONDS)
                .acquire(request.build())));
    }

    private record FailureReportObserver(String counterPartyId, String counterPartyAddress) implements StreamObserver<Empty> {

        @Override
        public void onNext(Empty value) {
            // The report has no response content.
        }

        @Override
        public void onError(Throwable t) {
            log.warn("Failed to report counter-party failure: counterPartyId={} counterPartyAddress={}",
                    counterPartyId, counterPartyAddress, t);
        }

        @Override
        public void onCompleted() {
            // Nothing to do once the control plane has acknowledged the report.
        }
    }
}
