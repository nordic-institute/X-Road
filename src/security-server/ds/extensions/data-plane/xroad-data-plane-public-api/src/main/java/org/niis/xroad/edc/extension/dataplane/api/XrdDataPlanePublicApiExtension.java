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

package org.niis.xroad.edc.extension.dataplane.api;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.signature.SimpleSigner;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.conf.SigningCtxProvider;
import ee.ria.xroad.signer.SignerRpcClient;

import lombok.SneakyThrows;
import org.eclipse.edc.connector.dataplane.spi.Endpoint;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.iam.PublicEndpointGeneratorService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.Hostname;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.edc.spi.XRoadPublicApiConfiguration;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import java.util.concurrent.Executors;

import static org.niis.xroad.edc.spi.XRoadPublicApiConfiguration.XROAD_PUBLIC_API_CONTEXT;

@SuppressWarnings("checkstyle:MagicNumber") //TODO xroad8
@Extension(value = XrdDataPlanePublicApiExtension.NAME)
public class XrdDataPlanePublicApiExtension implements ServiceExtension {

    public static final String NAME = "X-Road Data Plane Public API";
    private static final int DEFAULT_THREAD_POOL = 10;

    @Setting(description = "Base url of the public API endpoint without the trailing slash."
            + "This should point to the public endpoint configured.",
            required = false,
            key = "edc.dataplane.api.public.baseurl", warnOnMissingConfig = true)
    private String publicBaseUrl;

    @Configuration
    private XRoadPublicApiConfiguration apiConfiguration;
    @Inject
    private PortMappingRegistry portMappingRegistry;

    @Inject
    private PipelineService pipelineService;

    @Inject
    private WebService webService;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeManager typeManager;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private Hostname hostname;

    @Inject
    DataPlaneAuthorizationService authorizationService;

    @Inject(required = false)
    private XRoadMessageLog xRoadMessageLog;

    @Inject
    private PublicEndpointGeneratorService generatorService;

    @Inject
    private GlobalConfProvider globalConfProvider;
    @Inject
    private ServerConfProvider serverConfProvider;
    @Inject
    private KeyConfProvider keyConfProvider;
    @Inject
    private CertChainFactory certChainFactory;

    @Inject
    private SignerRpcClient signerRpcClient;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    @SneakyThrows
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        loadSystemProperties(monitor);

        var needClientAuth = apiConfiguration.needClientAuth();
        var portMapping = new PortMapping(XROAD_PUBLIC_API_CONTEXT, apiConfiguration.port(), apiConfiguration.path());
        portMappingRegistry.register(portMapping);
        var executorService = executorInstrumentation.instrument(
                Executors.newFixedThreadPool(DEFAULT_THREAD_POOL),
                "Data plane proxy transfers"
        );

        if (publicBaseUrl == null) {
            publicBaseUrl = "https://%s:%d%s".formatted(hostname.get(), apiConfiguration.port(), apiConfiguration.path());
            context.getMonitor().warning("The public API endpoint was not explicitly configured, the default '%s' will be used."
                    .formatted(publicBaseUrl));
        }

        monitor.debug("X-Road public endpoint is set to: %s".formatted(publicBaseUrl));

        // todo remove this workaround...
        SigningCtxProvider.setSigner(new SimpleSigner(signerRpcClient));

        var endpoint = Endpoint.url(publicBaseUrl);
        generatorService.addGeneratorFunction("XrdHttpData", dataAddress -> endpoint);
        var signService = new XrdSignatureService(globalConfProvider, null, null);
        var proxyApiController = new XrdDataPlaneProxyApiController(monitor,
                xRoadMessageLog, authorizationService, needClientAuth, globalConfProvider, keyConfProvider,
                serverConfProvider, certChainFactory);
        var lcController = new XrdDataPlanePublicApiController(pipelineService,
                new XrdEdcSignService(signService, monitor), monitor, executorService,
                xRoadMessageLog, authorizationService);

        webService.registerResource(XROAD_PUBLIC_API_CONTEXT, proxyApiController);
        webService.registerResource(XROAD_PUBLIC_API_CONTEXT, lcController);
    }

    private void loadSystemProperties(Monitor monitor) {
        monitor.info("Initializing X-Road System Properties..");
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .load();
    }

}
