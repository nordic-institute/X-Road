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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.niis.xroad.edc.extension.assetaccess.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.listener.TransferCompletionListener;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessStateStore;
import org.niis.xroad.edc.extension.assetaccess.transform.JsonObjectToCatalogTransformer;
import org.niis.xroad.edc.extension.assetaccess.transform.JsonObjectToDataServiceTransformer;
import org.niis.xroad.edc.extension.assetaccess.transform.JsonObjectToDatasetTransformer;
import org.niis.xroad.edc.extension.assetaccess.transform.JsonObjectToDistributionTransformer;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

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
    private ContractNegotiationObservable negotiationObservable;

    @Inject
    private TransferProcessObservable transferObservable;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private Monitor monitor;

    @Inject
    private ParticipantIdMapper participantIdMapper;

    private AssetAccessOrchestrator assetAccessOrchestrator;
    private NegotiationCompletionListener negotiationCompletionListener;
    private TransferCompletionListener transferCompletionListener;
    private TypeTransformerRegistry assetAccessTransformerRegistry;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("Initializing extension: " + EXTENSION_NAME);

        negotiationCompletionListener = new NegotiationCompletionListener();
        transferCompletionListener = new TransferCompletionListener();
        assetAccessTransformerRegistry = transformerRegistry.forContext("xrd-asset-access-api");

        assetAccessTransformerRegistry.register(new JsonObjectToCatalogTransformer());
        assetAccessTransformerRegistry.register(new JsonObjectToDatasetTransformer());
        assetAccessTransformerRegistry.register(new JsonObjectToDataServiceTransformer());
        assetAccessTransformerRegistry.register(new JsonObjectToDistributionTransformer());
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper)
                .forEach(assetAccessTransformerRegistry::register);

        negotiationObservable.registerListener(negotiationCompletionListener);
        transferObservable.registerListener(transferCompletionListener);
    }

    @Provider
    public AssetAccessOrchestrator assetAccessOrchestrator() {
        var objectMapper = typeManager.getMapper(JSON_LD);

        assetAccessOrchestrator = new AssetAccessOrchestrator(
                new AssetAccessStateStore(),
                catalogService,
                contractNegotiationService,
                transferProcessService,
                negotiationCompletionListener,
                transferCompletionListener,
                jsonLd,
                assetAccessTransformerRegistry,
                objectMapper,
                monitor
        );
        return assetAccessOrchestrator;
    }
}
