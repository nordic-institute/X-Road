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
import org.eclipse.edc.connector.controlplane.asset.spi.index.AssetIndex;
import org.eclipse.edc.connector.controlplane.asset.spi.index.DataAddressResolver;
import org.eclipse.edc.connector.controlplane.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.controlplane.policy.spi.store.PolicyDefinitionStore;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.ServerConfProvider;

/**
 * EDC ServiceExtension that overrides default catalog store SPIs with ServerConf-backed implementations.
 * AssetIndex, DataAddressResolver, PolicyDefinitionStore, and ContractDefinitionStore are all backed
 * by live ServerConf reads.
 */
@Slf4j
@Extension(XrdServerConfCatalogExtension.NAME)
public class XrdServerConfCatalogExtension implements ServiceExtension {

    static final String NAME = "X-Road ServerConf Catalog";

    /**
     * EDC config key for the participant context ID used when tagging catalog assets.
     *
     * <p>Each ds-control-plane instance must tag its catalog entities with the
     * {@code participantContextId} that matches the {@code ParticipantContext} registered in the
     * Identity Hub for this Security Server. The registered context is the SS hostname (e.g.
     * {@code xrd-ss0}), so this setting should be set to the SS hostname in
     * {@code local-ds-control-plane.yaml} for native deployments.
     *
     * <p>Default resolves to {@code edc.hostname} (which itself defaults to the {@code HOSTNAME}
     * environment variable or {@code "localhost"}), matching the Identity Hub bootstrap convention.
     *
     * <p><b>Historical note:</b> prior to DCP migration this was hardcoded to {@code "xroad-provider"}.
     * Under {@code edc-iam-mock} the mismatch was silently tolerated. Under {@code edc-iam-dcp-core}
     * EDC's catalog endpoint filters the {@code ContractDefinitionStore} by
     * {@code participantContextId = <routed-context>}, causing all definitions to be excluded and
     * the catalog to return zero datasets (CFG-01).
     */
    static final String SETTING_PARTICIPANT_CONTEXT_ID = "xroad.dsp.participant-context-id";

    /**
     * EDC config key for the participant context ID used when tagging MANAGEMENT-subsystem entities.
     *
     * <p>MANAGEMENT subsystem must own a distinct DSP identity so that self-negotiation (SS hosting
     * MANAGEMENT locally) produces two distinct {@code participantContextId} values — one per side of
     * the contract negotiation — avoiding the {@code edc_contract_negotiation} unique-constraint
     * collision documented in PRD dsp-mgmt-dual-context.
     *
     * <p>Defaults to {@code ${xroad.dsp.participant-context-id}-mgmt}, resolved by Smallrye Config
     * expression chaining.
     */
    static final String SETTING_MANAGEMENT_PARTICIPANT_CONTEXT_ID = "xroad.dsp.management-participant-context-id";

    @Inject
    private ServerConfProvider serverConfProvider;

    @Inject
    private GlobalConfProvider globalConfProvider;

    private String participantContextId;
    private String managementParticipantContextId;

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
    }

    /**
     * Provides an in-memory {@link DataPlaneInstanceStore} that overrides the SQL store on classpath.
     * Starts empty; receives the proxy data plane instance via DPS registerOn() at proxy boot.
     */
    @Provider
    public DataPlaneInstanceStore dataPlaneInstanceStore() {
        log.trace("Providing DataPlaneInstanceStore backed by in-memory store");
        return new ServerConfBackedDataPlaneInstanceStore();
    }

    /**
     * Provides a ServerConf-backed {@link AssetIndex} with live reads from {@link ServerConfProvider}.
     * Also serves as {@link DataAddressResolver} since {@code AssetIndex extends DataAddressResolver} in EDC 0.16.
     */
    @Provider
    public AssetIndex assetIndex() {
        log.trace("Providing AssetIndex backed by ServerConf");
        return new ServerConfBackedAssetIndex(
                serverConfProvider, globalConfProvider, participantContextId, managementParticipantContextId);
    }

    /**
     * Provides a {@link DataAddressResolver} delegating to the same ServerConf-backed {@link AssetIndex}.
     */
    @Provider
    public DataAddressResolver dataAddressResolver() {
        log.trace("Providing DataAddressResolver delegating to ServerConf-backed AssetIndex");
        return assetIndex();
    }

    /**
     * Provides a ServerConf-backed {@link PolicyDefinitionStore} with live ODRL policy generation
     * from {@link ServerConfProvider} access rights data.
     */
    @Provider
    public PolicyDefinitionStore policyDefinitionStore() {
        log.trace("Providing PolicyDefinitionStore backed by ServerConf");
        return new ServerConfBackedPolicyDefinitionStore(
                serverConfProvider, globalConfProvider, new PolicyMapper(),
                participantContextId, managementParticipantContextId);
    }

    /**
     * Provides a ContractDefinitionStore backed by ServerConf with disambiguated (asset, subject) IDs.
     */
    @Provider
    public ContractDefinitionStore contractDefinitionStore() {
        log.trace("Providing ContractDefinitionStore backed by ServerConf");
        return new ServerConfBackedContractDefinitionStore(
                serverConfProvider, globalConfProvider,
                new ContractDefinitionMapper(),
                participantContextId, managementParticipantContextId
        );
    }
}
