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
     * Provider-side participant context identifier used when registering catalog assets.
     *
     * <p><b>Intentional asymmetry with client-sent {@code counterPartyId}:</b> the proxy
     * DSP client sends {@code counterPartyId = ClientId.asEncodedId()} (e.g. {@code "DEV/COM/1234/TestClient"}),
     * which does NOT match the constant {@code "xroad-provider"} used here.
     *
     * <p>This is tolerated today ONLY because the active IAM bundle is {@code edc-iam-mock}
     * (see {@code core/src/gradle/libs.versions.toml} lines 353-361 — {@code edc-iam-dcp-core}
     * is commented out). Under {@code iam-mock}, EDC's {@code catalogService.requestCatalog}
     * does NOT verify {@code counterPartyId} against the provider's own {@code ParticipantContext.participantContextId};
     * see Phase 9 research ({@code .planning/phases/09-core-proxy-wiring/09-RESEARCH.md}, Q1 / D-14).
     *
     * <p><b>DCP migration trigger:</b> uncommenting {@code edc-iam-dcp-core} in
     * {@code libs.versions.toml} makes {@code counterPartyId} identity-significant via DCP
     * holder/issuer claim checks. At that point CFG-01 (per-subsystem configurable
     * {@code participantContextId}) in {@code REQUIREMENTS.md} becomes a hard prerequisite
     * and this constant must be replaced by request-scoped resolution.
     */
    public static final String PARTICIPANT_CONTEXT_ID = "xroad-provider";

    @Inject
    private ServerConfProvider serverConfProvider;

    @Inject
    private GlobalConfProvider globalConfProvider;

    @Override
    public String name() {
        return NAME;
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
        return new ServerConfBackedAssetIndex(serverConfProvider, PARTICIPANT_CONTEXT_ID);
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
        return new ServerConfBackedPolicyDefinitionStore(serverConfProvider, new PolicyMapper(), PARTICIPANT_CONTEXT_ID);
    }

    /**
     * Provides a ContractDefinitionStore backed by ServerConf with disambiguated (asset, subject) IDs.
     */
    @Provider
    public ContractDefinitionStore contractDefinitionStore() {
        log.trace("Providing ContractDefinitionStore backed by ServerConf");
        return new ServerConfBackedContractDefinitionStore(
                serverConfProvider,
                new ContractDefinitionMapper(),
                PARTICIPANT_CONTEXT_ID
        );
    }
}
