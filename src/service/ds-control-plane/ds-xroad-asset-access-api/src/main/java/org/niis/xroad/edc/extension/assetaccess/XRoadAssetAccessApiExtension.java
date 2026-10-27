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

package org.niis.xroad.edc.extension.assetaccess;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.flow.DataFlowController;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.DataAddressStore;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToDataServiceTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToDatasetTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToDistributionTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.v2025.to.JsonObjectToCatalogTransformer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.niis.xroad.edc.extension.assetaccess.agreement.ReusableAgreementLookup;
import org.niis.xroad.edc.extension.assetaccess.cooldown.CooldownTracker;
import org.niis.xroad.edc.extension.assetaccess.listener.ConsumerDataFlowCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.poller.AssetAccessCompletionPoller;
import org.niis.xroad.edc.extension.assetaccess.poller.ExpiredTransferSweeper;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessStateStore;

import java.time.Clock;
import java.time.Duration;

@Provides({AssetAccessOrchestrator.class})
@Extension(value = XRoadAssetAccessApiExtension.EXTENSION_NAME)
public class XRoadAssetAccessApiExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Asset Access Api Extension";

    @Inject
    private CatalogService catalogService;

    @Inject
    private ContractNegotiationService contractNegotiationService;

    @Inject
    private TransferProcessService transferProcessService;

    @Inject
    private ContractNegotiationStore contractNegotiationStore;

    @Inject
    private TransferProcessStore transferProcessStore;

    @Inject
    private DataAddressStore dataAddressStore;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private Clock clock;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private Monitor monitor;

    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Inject
    private TransferProcessObservable transferProcessObservable;

    @Inject
    private DataFlowController dataFlowController;

    @Setting(key = "xroad.asset-access.negotiation.timeout-seconds",
            description = "Per-stage contract negotiation deadline in seconds",
            defaultValue = "60")
    private long negotiationTimeoutSeconds;

    @Setting(key = "xroad.asset-access.transfer.timeout-seconds",
            description = "Per-stage transfer process deadline in seconds",
            defaultValue = "60")
    private long transferTimeoutSeconds;

    @Setting(key = "xroad.asset-access.cooldown.period-seconds",
            description = "How long, in seconds, a provider Security Server that failed with a transport fault is kept "
                    + "out of acquisition. When every candidate is cooling down, all of them are tried.",
            defaultValue = "180")
    private long cooldownPeriodSeconds;

    @Setting(key = "xroad.asset-access.poll-interval-ms",
            description = "Completion poller tick interval in milliseconds",
            defaultValue = "250")
    private long pollIntervalMillis;

    @Setting(key = "xroad.asset-access.expired-transfer-sweep.interval-seconds",
            description = "Expired transfer sweep tick interval in seconds",
            defaultValue = "30")
    private long expiredTransferSweepIntervalSeconds;

    @Setting(key = "xroad.asset-access.expired-transfer-sweep.grace-seconds",
            description = "How long, in seconds, a STARTED consumer transfer's agreement token may be expired "
                    + "before the transfer is completed. Must comfortably exceed the consumer proxy's cache "
                    + "margin so a transfer is never completed while a request could still be using its token.",
            defaultValue = "30")
    private long expiredTransferSweepGraceSeconds;

    @Setting(key = "xroad.asset-access.expired-transfer-sweep.untokened-lifetime-seconds",
            description = "How long, in seconds, a STARTED consumer transfer whose data address carries no agreement "
                    + "token (the provider's endpoint-only fallback) lives before the transfer is completed. Must be "
                    + "at least the consumer proxy's xroad.proxy.dsp.cache.default-ttl, which is how long the proxy "
                    + "keeps using an endpoint-only address.",
            defaultValue = "300")
    private long expiredTransferSweepUntokenedLifetimeSeconds;

    private AssetAccessOrchestrator assetAccessOrchestrator;
    private AssetAccessCompletionPoller completionPoller;
    private ExpiredTransferSweeper expiredTransferSweeper;
    private ReusableAgreementLookup reusableAgreementLookup;
    private CooldownTracker cooldownTracker;
    private TypeTransformerRegistry assetAccessTransformerRegistry;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("Initializing extension: " + EXTENSION_NAME);

        completionPoller = new AssetAccessCompletionPoller(
                contractNegotiationStore,
                transferProcessStore,
                dataAddressStore,
                transactionContext,
                executorInstrumentation,
                clock,
                monitor,
                Duration.ofMillis(pollIntervalMillis));

        expiredTransferSweeper = new ExpiredTransferSweeper(
                transferProcessStore,
                dataAddressStore,
                transferProcessService,
                transactionContext,
                clock,
                monitor,
                Duration.ofSeconds(expiredTransferSweepIntervalSeconds),
                Duration.ofSeconds(expiredTransferSweepGraceSeconds),
                Duration.ofSeconds(expiredTransferSweepUntokenedLifetimeSeconds));

        reusableAgreementLookup = new ReusableAgreementLookup(contractNegotiationStore, transactionContext);

        cooldownTracker = new CooldownTracker(Duration.ofSeconds(cooldownPeriodSeconds));

        transferProcessObservable.registerListener(new ConsumerDataFlowCompletionListener(dataFlowController, monitor));

        assetAccessTransformerRegistry = transformerRegistry.forContext("xrd-asset-access-api");

        assetAccessTransformerRegistry.register(new JsonObjectToCatalogTransformer());
        assetAccessTransformerRegistry.register(new JsonObjectToDatasetTransformer());
        assetAccessTransformerRegistry.register(new JsonObjectToDataServiceTransformer());
        assetAccessTransformerRegistry.register(new JsonObjectToDistributionTransformer());
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper)
                .forEach(assetAccessTransformerRegistry::register);
    }

    @Override
    public void start() {
        completionPoller.start();
        expiredTransferSweeper.start();
    }

    @Override
    public void shutdown() {
        completionPoller.stop();
        expiredTransferSweeper.stop();
    }

    @Provider
    public AssetAccessOrchestrator assetAccessOrchestrator() {
        if (assetAccessOrchestrator == null) {
            assetAccessOrchestrator = new AssetAccessOrchestrator(
                    new AssetAccessStateStore(),
                    reusableAgreementLookup,
                    catalogService,
                    contractNegotiationService,
                    transferProcessService,
                    completionPoller,
                    cooldownTracker,
                    jsonLd,
                    assetAccessTransformerRegistry,
                    monitor,
                    Duration.ofSeconds(negotiationTimeoutSeconds),
                    Duration.ofSeconds(transferTimeoutSeconds)
            );
        }
        return assetAccessOrchestrator;
    }
}
