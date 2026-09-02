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
package org.niis.xroad.edc.extension.catalog;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.dataplane.http.spi.HttpDataAddress;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Read-only {@link AssetIndex} backed by live {@link ServerConfProvider} reads. Also serves
 * as {@code DataAddressResolver} since EDC 0.16 folds it into the {@code AssetIndex} contract.
 */
@Slf4j
@RequiredArgsConstructor
class AssetIndexServerConfStore implements AssetIndex {

    private static final String READ_ONLY_MESSAGE = "Read-only: managed by ServerConf";

    private final ServerConfProvider serverConfProvider;
    private final GlobalConfProvider globalConfProvider;
    private final String managementParticipantContextId;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final StoreEnumerationCache<Asset> cache;
    private final ServiceContextResolver serviceContextResolver;
    private final DspParticipantContextHolder dspParticipantContextHolder;
    private final QueryEvaluator<Asset> queryEvaluator = new QueryEvaluator<>(Asset::getId, Asset::getParticipantContextId);

    private boolean isOwnerOnly(ServiceId serviceId) {
        try {
            return serverConfProvider.getServiceAccessRights(serviceId).isEmpty();
        } catch (Exception e) {
            log.warn("Failed to read access rights for service '{}': {}", serviceId, e.getMessage());
            return false;
        }
    }

    @Override
    public Stream<Asset> queryAssets(QuerySpec querySpec) {
        if (log.isTraceEnabled()) {
            log.trace("queryAssets criteria={} offset={} limit={}",
                    querySpec.getFilterExpression(), querySpec.getOffset(), querySpec.getLimit());
        }
        var snapshot = cache.getEnumeration(this::buildAssetList);
        if (log.isTraceEnabled()) {
            log.trace("queryAssets collected={} assets before filtering", snapshot.size());
        }
        return queryEvaluator.evaluate(snapshot.stream(), querySpec);
    }

    private List<Asset> buildAssetList() {
        var assets = new ArrayList<Asset>();
        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                assets.add(AssetMapper.toAsset(serviceId, managementParticipantContextId));
                if (serverConfProvider.getDisabledNotice(serviceId) == null) {
                    for (var ctxId : serviceContextResolver.resolveContexts(serviceId)) {
                        assets.add(AssetMapper.toAsset(serviceId, ctxId));
                    }
                }
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            assets.add(AssetMapper.toAsset(serviceId, managementParticipantContextId));
        }
        ManagementServiceCatalog.resolveSyntheticServices(globalConfProvider, serverConfProvider)
                .forEach(serviceId -> assets.add(AssetMapper.toAsset(serviceId, managementParticipantContextId)));
        return assets;
    }

    @Override
    @Nullable
    public Asset findById(String assetId) {
        return cache.findById(assetId, dspParticipantContextHolder.get(), () -> findByIdInternal(assetId));
    }

    @Nullable
    private Asset findByIdInternal(String assetId) {
        log.trace("findById assetId={}", assetId);
        var builtinServiceId = builtinServiceCatalog.findServiceId(assetId);
        if (builtinServiceId != null) {
            log.trace("findById assetId={} matched builtin", assetId);
            return AssetMapper.toAsset(builtinServiceId, managementParticipantContextId);
        }
        var serviceId = AssetMapper.decodeAssetId(assetId);
        if (serviceId == null) {
            log.trace("findById assetId={} decode failed, returning null", assetId);
            return null;
        }
        if (log.isTraceEnabled()) {
            log.trace("findById decoded serviceId={}", serviceId.asEncodedId());
        }
        if (!serverConfProvider.serviceExists(serviceId)) {
            if (isLocallyRegisteredSubsystem(serviceId.getClientId())) {
                log.trace("findById assetId={} synthesizing owner-only asset for locally registered subsystem", assetId);
                return AssetMapper.toAsset(serviceId, managementParticipantContextId);
            }
            log.trace("findById assetId={} service does not exist, returning null", assetId);
            return null;
        }
        var ctxId = serverConfProvider.getDisabledNotice(serviceId) != null
                ? managementParticipantContextId
                : ServiceContextResolver.selectContext(
                        serviceContextResolver.resolveContexts(serviceId), dspParticipantContextHolder.get());
        return AssetMapper.toAsset(serviceId, ctxId);
    }

    private boolean isLocallyRegisteredSubsystem(ClientId clientId) {
        if (clientId == null || clientId.getSubsystemCode() == null) {
            return false;
        }
        try {
            var thisServer = serverConfProvider.getIdentifier();
            return thisServer != null && globalConfProvider.isSecurityServerClient(clientId, thisServer);
        } catch (Exception e) {
            log.warn("Failed to read global-conf for synthetic asset check '{}': {}", clientId, e.getMessage());
            return false;
        }
    }

    @Override
    @Nullable
    public DataAddress resolveForAsset(String assetId) {
        log.trace("resolveForAsset assetId={}", assetId);
        var builtinServiceId = builtinServiceCatalog.findServiceId(assetId);
        if (builtinServiceId != null) {
            log.trace("resolveForAsset assetId={} matched builtin, baseUrl={}", assetId, builtinServiceCatalog.serverProxyUrl());
            return buildHttpDataAddress(builtinServiceCatalog.serverProxyUrl());
        }
        var cached = cache.getDataAddress(assetId);
        if (cached != null) {
            return cached;
        }
        var result = resolveForAssetInternal(assetId);
        cache.cacheDataAddress(assetId, result);
        return result;
    }

    @Nullable
    private DataAddress resolveForAssetInternal(String assetId) {
        var serviceId = AssetMapper.decodeAssetId(assetId);
        if (serviceId == null) {
            log.trace("resolveForAsset assetId={} decode failed, returning null", assetId);
            return null;
        }
        if (serverConfProvider.getDisabledNotice(serviceId) != null) {
            log.trace("resolveForAsset assetId={} service disabled, returning null", assetId);
            return null;
        }
        String serviceAddress;
        try {
            serviceAddress = serverConfProvider.getServiceAddress(serviceId);
        } catch (Exception e) {
            log.warn("Failed to resolve service address for asset '{}': {}", assetId, e.getMessage());
            return null;
        }
        if (serviceAddress == null) {
            log.trace("resolveForAsset assetId={} no address found, returning null", assetId);
            return null;
        }
        log.trace("resolveForAsset assetId={} resolved baseUrl={}", assetId, serviceAddress);
        return buildHttpDataAddress(serviceAddress);
    }

    @SuppressWarnings("deprecation")
    private static DataAddress buildHttpDataAddress(String baseUrl) {
        return HttpDataAddress.Builder.newInstance()
                .baseUrl(baseUrl)
                .proxyPath("true")
                .proxyMethod("true")
                .proxyBody("true")
                .proxyQueryParams("true")
                .build();
    }

    @Override
    public long countAssets(List<Criterion> criteria) {
        if (log.isTraceEnabled()) {
            log.trace("countAssets criteria count={}", criteria.size());
        }
        var spec = QuerySpec.Builder.newInstance()
                .filter(criteria)
                .limit(Integer.MAX_VALUE)
                .build();
        var count = queryAssets(spec).count();
        log.trace("countAssets result={}", count);
        return count;
    }

    @Override
    public StoreResult<Void> create(Asset asset) {
        log.trace("create assetId={} read-only, returning alreadyExists", asset.getId());
        return StoreResult.alreadyExists(READ_ONLY_MESSAGE);
    }

    @Override
    public StoreResult<Asset> deleteById(String assetId) {
        log.trace("deleteById assetId={} read-only, returning notFound", assetId);
        return StoreResult.notFound(READ_ONLY_MESSAGE);
    }

    @Override
    public StoreResult<Asset> updateAsset(Asset asset) {
        log.trace("updateAsset assetId={} read-only, returning notFound", asset.getId());
        return StoreResult.notFound(READ_ONLY_MESSAGE);
    }
}
