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
 * ServerConf-backed {@link AssetIndex} that reads members and services live from
 * {@link ServerConfProvider} on each call. Disabled services are excluded.
 * Write operations return {@link StoreResult} failures (read-only store).
 *
 * <p>{@code AssetIndex} extends {@code DataAddressResolver} in EDC 0.16, so this class
 * implements both interfaces via the single {@code AssetIndex} contract.
 */
@Slf4j
class AssetIndexServerConfStore implements AssetIndex {

    private final ServerConfProvider serverConfProvider;
    private final GlobalConfProvider globalConfProvider;
    private final String participantContextId;
    private final String managementParticipantContextId;
    private final BuiltinServiceCatalog builtinServiceCatalog;
    private final QueryEvaluator<Asset> queryEvaluator = new QueryEvaluator<>(Asset::getId, Asset::getParticipantContextId);

    AssetIndexServerConfStore(ServerConfProvider serverConfProvider,
                              GlobalConfProvider globalConfProvider,
                              String participantContextId,
                              String managementParticipantContextId,
                              BuiltinServiceCatalog builtinServiceCatalog) {
        this.serverConfProvider = serverConfProvider;
        this.globalConfProvider = globalConfProvider;
        this.participantContextId = participantContextId;
        this.managementParticipantContextId = managementParticipantContextId;
        this.builtinServiceCatalog = builtinServiceCatalog;
    }

    /**
     * Chooses the participant context ID for a given serviceId.
     * MANAGEMENT subsystem uses a distinct DSP identity to avoid self-negotiation constraint violations.
     */
    private String resolveContextId(ServiceId serviceId) {
        var mgmtService = globalConfProvider.getManagementRequestService();
        return (mgmtService != null && mgmtService.equals(serviceId.getClientId()))
                ? managementParticipantContextId
                : participantContextId;
    }

    private boolean isOwnerOnly(ServiceId serviceId) {
        try {
            return serverConfProvider.getServiceAccessRights(serviceId).isEmpty();
        } catch (Exception e) {
            log.warn("Failed to read access rights for service '{}': {}", serviceId, e.getMessage());
            return false;
        }
    }

    /**
     * Returns one {@link Asset} per enabled service across all members.
     * Disabled services (non-null {@code getDisabledNotice}) are excluded.
     * Results are filtered and paged by {@link QueryEvaluator}.
     */
    @Override
    public Stream<Asset> queryAssets(QuerySpec querySpec) {
        if (log.isTraceEnabled()) {
            log.trace("queryAssets criteria={} offset={} limit={}",
                    querySpec.getFilterExpression(), querySpec.getOffset(), querySpec.getLimit());
        }
        var assets = new ArrayList<Asset>();
        for (var member : serverConfProvider.getMembers()) {
            for (var serviceId : serverConfProvider.getAllServices(member)) {
                assets.add(AssetMapper.toAsset(serviceId, managementParticipantContextId));
                if (serverConfProvider.getDisabledNotice(serviceId) == null) {
                    assets.add(AssetMapper.toAsset(serviceId, resolveContextId(serviceId)));
                }
            }
        }
        for (var serviceId : builtinServiceCatalog.activeServiceIds()) {
            assets.add(AssetMapper.toAsset(serviceId, managementParticipantContextId));
        }
        ManagementServiceCatalog.resolveSyntheticServices(globalConfProvider, serverConfProvider)
                .forEach(serviceId -> assets.add(AssetMapper.toAsset(serviceId, managementParticipantContextId)));
        if (log.isTraceEnabled()) {
            log.trace("queryAssets collected={} assets before filtering", assets.size());
        }
        return queryEvaluator.evaluate(assets.stream(), querySpec);
    }

    /**
     * Decodes the asset ID, verifies the service exists and is enabled, returns the {@link Asset}.
     * Returns {@code null} for malformed IDs, disabled services, or missing services.
     */
    @Override
    @Nullable
    public Asset findById(String assetId) {
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
                : resolveContextId(serviceId);
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

    /**
     * Builds an {@link HttpDataAddress} for the given asset with proxy flags enabled.
     * Returns {@code null} on decode failure, disabled service, or missing address.
     */
    @Override
    @Nullable
    public DataAddress resolveForAsset(String assetId) {
        log.trace("resolveForAsset assetId={}", assetId);
        var builtinServiceId = builtinServiceCatalog.findServiceId(assetId);
        if (builtinServiceId != null) {
            log.trace("resolveForAsset assetId={} matched builtin, baseUrl={}", assetId, builtinServiceCatalog.serverProxyUrl());
            return buildHttpDataAddress(builtinServiceCatalog.serverProxyUrl());
        }
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
        return StoreResult.alreadyExists("Read-only: managed by ServerConf");
    }

    @Override
    public StoreResult<Asset> deleteById(String assetId) {
        log.trace("deleteById assetId={} read-only, returning notFound", assetId);
        return StoreResult.notFound("Read-only: managed by ServerConf");
    }

    @Override
    public StoreResult<Asset> updateAsset(Asset asset) {
        log.trace("updateAsset assetId={} read-only, returning notFound", asset.getId());
        return StoreResult.notFound("Read-only: managed by ServerConf");
    }
}
