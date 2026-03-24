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

package org.niis.xroad.edc.extension.edr;

import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.observe.ContractNegotiationObservable;
import org.eclipse.edc.connector.controlplane.services.spi.catalog.CatalogService;
import org.eclipse.edc.connector.controlplane.services.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.transfer.spi.observe.TransferProcessObservable;
import org.eclipse.edc.connector.controlplane.transform.odrl.OdrlTransformersFactory;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.participant.spi.ParticipantIdMapper;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.ApiContext;
import org.niis.xroad.edc.extension.edr.listener.NegotiationCompletionListener;
import org.niis.xroad.edc.extension.edr.listener.TransferCompletionListener;
import org.niis.xroad.edc.extension.edr.service.EdrAcquisitionService;
import org.niis.xroad.edc.extension.edr.transform.JsonObjectToCatalogTransformer;
import org.niis.xroad.edc.extension.edr.transform.JsonObjectToDataServiceTransformer;
import org.niis.xroad.edc.extension.edr.transform.JsonObjectToDatasetTransformer;
import org.niis.xroad.edc.extension.edr.transform.JsonObjectToDistributionTransformer;

import static org.eclipse.edc.spi.constants.CoreConstants.JSON_LD;

@Extension(value = XRoadEdrApiExtension.EXTENSION_NAME)
public class XRoadEdrApiExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road EDR Api Extension";

    @Inject
    private WebService webService;

    @Inject
    private CatalogService catalogService;

    @Inject
    private ContractNegotiationService contractNegotiationService;

    @Inject
    private TransferProcessService transferProcessService;

    @Inject
    private EndpointDataReferenceStore edrStore;

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
    private AuthorizationService authorizationService;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private Monitor monitor;

    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor.info("Initializing extension: " + EXTENSION_NAME);
        var edrTransformerRegistry = transformerRegistry.forContext("xrd-edr-api");
        edrTransformerRegistry.register(new JsonObjectToCatalogTransformer());
        edrTransformerRegistry.register(new JsonObjectToDatasetTransformer());
        edrTransformerRegistry.register(new JsonObjectToDataServiceTransformer());
        edrTransformerRegistry.register(new JsonObjectToDistributionTransformer());
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(edrTransformerRegistry::register);

        var negotiationCompletionListener = new NegotiationCompletionListener();
        var transferCompletionListener = new TransferCompletionListener();
        negotiationObservable.registerListener(negotiationCompletionListener);
        transferObservable.registerListener(transferCompletionListener);

        var objectMapper = typeManager.getMapper(JSON_LD);

        var service = new EdrAcquisitionService(
                catalogService,
                contractNegotiationService,
                transferProcessService,
                edrStore,
                negotiationCompletionListener,
                transferCompletionListener,
                jsonLd,
                edrTransformerRegistry,
                objectMapper
        );

        var controller = new XRoadEdrApiController(service, authorizationService, participantContextService);
        webService.registerResource(ApiContext.MANAGEMENT, controller);
    }
}
