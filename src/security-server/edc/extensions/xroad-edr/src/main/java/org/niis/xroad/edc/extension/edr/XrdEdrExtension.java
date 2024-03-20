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

import org.eclipse.edc.connector.api.management.configuration.ManagementApiConfiguration;
import org.eclipse.edc.connector.spi.callback.CallbackProtocolResolverRegistry;
import org.eclipse.edc.connector.spi.catalog.CatalogService;
import org.eclipse.edc.connector.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.connector.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.message.RemoteMessageDispatcherRegistry;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.spi.WebService;
import org.niis.xroad.edc.extension.edr.callback.ContractNegotiationCallbackHandler;
import org.niis.xroad.edc.extension.edr.callback.LocalCallbackMessageDispatcherImpl;
import org.niis.xroad.edc.extension.edr.callback.LocalCallbackRegistryImpl;
import org.niis.xroad.edc.extension.edr.callback.TransferProcessCallbackHandler;
import org.niis.xroad.edc.extension.edr.service.AssetAuthorizationManager;
import org.niis.xroad.edc.extension.edr.service.AssetInProgressRegistry;
import org.niis.xroad.edc.extension.edr.service.AuthorizedAssetRegistry;
import org.niis.xroad.edc.extension.edr.service.InMemoryAuthorizedAssetRegistry;
import org.niis.xroad.edc.extension.edr.transform.JsonObjectFromEndpointDataReferenceTransformer;
import org.niis.xroad.edc.extension.edr.transform.JsonObjectToNegotiateAssetRequestDtoTransformer;

import static org.niis.xroad.edc.extension.edr.callback.LocalCallbackMessageDispatcherImpl.CALLBACK_EVENT_LOCAL;
import static org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto.XRD_NAMESPACE;
import static org.niis.xroad.edc.extension.edr.dto.NegotiateAssetRequestDto.XRD_PREFIX;

@Extension(value = XrdEdrExtension.NAME)
public class XrdEdrExtension implements ServiceExtension {

    static final String NAME = "X-Road EDR Extension";

    @Inject
    private WebService webService;

    @Inject
    private ManagementApiConfiguration apiConfig;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private JsonLd jsonLdService;

    @Inject
    private CatalogService catalogService;
    @Inject
    private ContractNegotiationService contractNegotiationService;
    @Inject
    private TransferProcessService transferProcessService;

    @Inject
    private Monitor monitor;

    @Inject
    private CallbackProtocolResolverRegistry callbackResolverRegistry;

    @Inject
    private RemoteMessageDispatcherRegistry remoteMessageDispatcherRegistry;

    @Override
    public void initialize(ServiceExtensionContext context) {
        jsonLdService.registerNamespace(XRD_PREFIX, XRD_NAMESPACE);
        transformerRegistry.register(new JsonObjectToNegotiateAssetRequestDtoTransformer());
        transformerRegistry.register(new JsonObjectFromEndpointDataReferenceTransformer());

        AssetInProgressRegistry inProgressRegistry = new AssetInProgressRegistry();
        AuthorizedAssetRegistry authorizedAssetRegistry = new InMemoryAuthorizedAssetRegistry(monitor);
        AssetAuthorizationManager assetAuthorizationManager = new AssetAuthorizationManager(catalogService,
                contractNegotiationService, transformerRegistry, authorizedAssetRegistry, inProgressRegistry);

        LocalCallbackRegistryImpl localCallbackRegistry = new LocalCallbackRegistryImpl();
        localCallbackRegistry.registerHandler(new ContractNegotiationCallbackHandler(transferProcessService,
                inProgressRegistry, monitor));
        localCallbackRegistry.registerHandler(new TransferProcessCallbackHandler(transformerRegistry,
                authorizedAssetRegistry,
                inProgressRegistry, monitor));

        remoteMessageDispatcherRegistry.register(new LocalCallbackMessageDispatcherImpl(localCallbackRegistry));
        callbackResolverRegistry.registerResolver(this::resolveProtocol);

        webService.registerResource(apiConfig.getContextAlias(),
                new XrdEdrController(transformerRegistry, assetAuthorizationManager));
    }

    private String resolveProtocol(String scheme) {
        if (scheme.equalsIgnoreCase("local")) {
            return CALLBACK_EVENT_LOCAL;
        }
        return null;
    }

}
