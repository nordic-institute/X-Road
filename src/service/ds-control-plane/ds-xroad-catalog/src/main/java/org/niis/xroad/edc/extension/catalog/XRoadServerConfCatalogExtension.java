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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.asset.spi.domain.Asset;
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.PolicyDefinition;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;

/**
 * Overrides EDC's default catalog SPIs (AssetIndex, DataAddressResolver,
 * PolicyDefinitionStore, ContractDefinitionStore) with ServerConf-backed read-only stores.
 *
 * <p>Also exposes a {@link CatalogCacheInvalidator} hook so other extensions can invalidate the
 * store-enumeration caches when a change (e.g. a new participant context) must be visible immediately.</p>
 */
@Slf4j
@Provides(CatalogCacheInvalidator.class)
@Extension(XRoadServerConfCatalogExtension.NAME)
public class XRoadServerConfCatalogExtension implements ServiceExtension {

    static final String NAME = "X-Road ServerConf Catalog";

    /**
     * Tag for catalog entities owned by this SS. Must match the ParticipantContext registered
     * in the Identity Hub (SS hostname) — EDC's catalog endpoint filters by it under
     * edc-iam-dcp-core. Defaults to {@code edc.hostname}.
     */
    static final String SETTING_PARTICIPANT_CONTEXT_ID = "xroad.dsp.participant-context-id";

    /**
     * Distinct DSP identity for MANAGEMENT-subsystem entities. Required so that self-negotiation
     * (SS hosting MANAGEMENT locally) avoids the {@code edc_contract_negotiation} unique-constraint
     * collision. Defaults to {@code ${xroad.dsp.participant-context-id}-mgmt}.
     */
    static final String SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID = "xroad.dsp.management-participant-context-id";

    static final String SETTING_CACHE_ENABLED = "xroad.dsp.catalog.cache.enabled";
    static final String SETTING_CACHE_TTL_SECONDS = "xroad.dsp.catalog.cache.ttl-seconds";
    static final String SETTING_CACHE_FIND_BY_ID_MAX_SIZE = "xroad.dsp.catalog.cache.find-by-id-max-size";

    private static final long DEFAULT_CACHE_TTL_SECONDS = 60L;
    private static final int DEFAULT_CACHE_FIND_BY_ID_MAX_SIZE = 10000;

    @Inject
    private ServerConfProvider serverConfProvider;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private WebService webService;

    private String participantContextId;
    private String managementParticipantContextId;
    private BuiltinServiceCatalog builtinServiceCatalog;
    private StoreCacheConfig cacheConfig;
    private ServiceContextResolver serviceContextResolver;
    private DspParticipantContextHolder dspParticipantContextHolder;
    private AssetIndexServerConfStore assetIndexStore;
    private StoreEnumerationCache<PolicyDefinition> policyDefinitionCache;
    private StoreEnumerationCache<ContractDefinition> contractDefinitionCache;
    private CatalogCacheInvalidator catalogCacheInvalidator;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var defaultContextId = context.getSetting("edc.hostname", "localhost");
        participantContextId = context.getSetting(SETTING_PARTICIPANT_CONTEXT_ID, defaultContextId);
        managementParticipantContextId = context.getSetting(
                SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID, participantContextId + "-mgmt");
        log.info("Participant context ID for catalog assets: {}", participantContextId);
        log.info("Management participant context ID for catalog assets: {}", managementParticipantContextId);

        var proxyMonitorEnabled = context.getSetting(BuiltinServiceCatalog.SETTING_PROXY_MONITOR_ENABLED, true);
        var opMonitorEnabled = context.getSetting(BuiltinServiceCatalog.SETTING_OP_MONITOR_ENABLED, true);
        var metaservicesEnabled = context.getSetting(BuiltinServiceCatalog.SETTING_METASERVICES_ENABLED, true);
        var serverProxyUrl = context.getSetting(BuiltinServiceCatalog.SETTING_SERVER_PROXY_URL,
                BuiltinServiceCatalog.DEFAULT_SERVER_PROXY_URL);

        builtinServiceCatalog = new BuiltinServiceCatalog(
                serverConfProvider, proxyMonitorEnabled, opMonitorEnabled, metaservicesEnabled, serverProxyUrl);
        log.info("Built-in service catalog active entries: {}", builtinServiceCatalog.activeServiceIds().size());

        var cacheEnabled = context.getSetting(SETTING_CACHE_ENABLED, true);
        var cacheTtlSeconds = context.getSetting(SETTING_CACHE_TTL_SECONDS, DEFAULT_CACHE_TTL_SECONDS);
        var cacheFindByIdMaxSize = context.getSetting(SETTING_CACHE_FIND_BY_ID_MAX_SIZE, DEFAULT_CACHE_FIND_BY_ID_MAX_SIZE);
        cacheConfig = new StoreCacheConfig(cacheEnabled, cacheTtlSeconds, cacheFindByIdMaxSize);
        log.info("Store cache enabled={} ttlSeconds={} findByIdMaxSize={}",
                cacheEnabled, cacheTtlSeconds, cacheFindByIdMaxSize);

        serviceContextResolver = new ServiceContextResolver(
                participantContextService, globalConfProvider, participantContextId, managementParticipantContextId);
        dspParticipantContextHolder = new DspParticipantContextHolder();
        webService.registerResource(ApiContext.PROTOCOL, new DspParticipantContextRequestFilter(dspParticipantContextHolder));

        StoreEnumerationCache<Asset> assetCache = new StoreEnumerationCache<>(cacheConfig.enabled(), cacheConfig.ttlSeconds(),
                cacheConfig.findByIdMaxSize(), "AssetIndex");
        policyDefinitionCache = new StoreEnumerationCache<>(cacheConfig.enabled(), cacheConfig.ttlSeconds(),
                cacheConfig.findByIdMaxSize(), "PolicyDefinition");
        contractDefinitionCache = new StoreEnumerationCache<>(cacheConfig.enabled(), cacheConfig.ttlSeconds(),
                cacheConfig.findByIdMaxSize(), "ContractDefinition");

        assetIndexStore = new AssetIndexServerConfStore(
                serverConfProvider, globalConfProvider, participantContextId, managementParticipantContextId,
                builtinServiceCatalog, assetCache, serviceContextResolver, dspParticipantContextHolder);

        catalogCacheInvalidator = new DefaultCatalogCacheInvalidator(List.of(assetCache, policyDefinitionCache, contractDefinitionCache));
    }

    @Provider
    public AssetIndex assetIndex() {
        log.trace("Providing AssetIndex backed by ServerConf");
        return assetIndexStore;
    }

    @Provider
    public DataAddressResolver dataAddressResolver() {
        log.trace("Providing DataAddressResolver delegating to ServerConf-backed AssetIndex");
        return assetIndexStore;
    }

    @Provider
    public PolicyDefinitionStore policyDefinitionStore() {
        log.trace("Providing PolicyDefinitionStore backed by ServerConf");
        return new PolicyDefinitionServerConfStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(),
                participantContextId, managementParticipantContextId, builtinServiceCatalog,
                policyDefinitionCache,
                serviceContextResolver, dspParticipantContextHolder);
    }

    @Provider
    public ContractDefinitionStore contractDefinitionStore() {
        log.trace("Providing ContractDefinitionStore backed by ServerConf");
        return new ContractDefinitionServerConfStore(
                serverConfProvider, globalConfProvider,
                participantContextId, managementParticipantContextId,
                builtinServiceCatalog,
                contractDefinitionCache,
                serviceContextResolver, dspParticipantContextHolder);
    }

    /**
     * Exposes the hook other extensions call to invalidate the catalog store-enumeration caches
     * when a change (e.g. a genuine participant-context creation) must be visible immediately.
     */
    @Provider
    public CatalogCacheInvalidator catalogCacheInvalidator() {
        return catalogCacheInvalidator;
    }
}
