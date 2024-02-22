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

package org.niis.xroad.edc.extension.signer;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.signer.protocol.RpcSignerClient;

import org.eclipse.edc.connector.dataplane.api.validation.ConsumerPullTransferDataAddressResolver;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;
import org.niis.xroad.edc.sig.XrdSignatureService;

@SuppressWarnings("checkstyle:MagicNumber") //TODO xroad8
//@Provides({ DataPlaneManager.class, PipelineService.class, DataTransferExecutorServiceContainer.class, TransferServiceRegistry.class })
@Extension(value = XrdPayloadSignerExtension.NAME)
public class XrdPayloadSignerExtension implements ServiceExtension {

    public static final String NAME = "X-Road Data Plane Public API";
    private static final int DEFAULT_PUBLIC_PORT = 9293;
    private static final String PUBLIC_API_CONFIG = "web.http.xroad.public";
    private static final String PUBLIC_CONTEXT_ALIAS = "xroad";
    private static final String PUBLIC_CONTEXT_PATH = "/xroad/public";

    @Setting
    private static final String CONTROL_PLANE_VALIDATION_ENDPOINT = "edc.dataplane.token.validation.endpoint";

    private static final WebServiceSettings PUBLIC_SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey(PUBLIC_API_CONFIG)
            .contextAlias(PUBLIC_CONTEXT_ALIAS)
            .defaultPath(PUBLIC_CONTEXT_PATH)
            .defaultPort(DEFAULT_PUBLIC_PORT)
            .name(NAME)
            .build();

    @Inject
    private WebServer webServer;

    @Inject
    private WebServiceConfigurer webServiceConfigurer;

    @Inject
    private PipelineService pipelineService;

    @Inject
    private WebService webService;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeManager typeManager;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();

        loadSystemProperties(monitor);


        var validationEndpoint = context.getConfig().getString(CONTROL_PLANE_VALIDATION_ENDPOINT);
        var dataAddressResolver = new ConsumerPullTransferDataAddressResolver(httpClient, validationEndpoint, typeManager.getMapper());
        var configuration = webServiceConfigurer.configure(context, webServer, PUBLIC_SETTINGS);

        var signService = new XrdSignatureService();
        var publicApiController = new XrdDataPlanePublicApiController(pipelineService, dataAddressResolver,
                new XrdEdcSignService(signService, monitor), monitor);

        //TODO xroad8 this added port mapping is added due to a strange behavior ir edc jersey registry. Consider refactor.
        webServer.addPortMapping(configuration.getContextAlias(), configuration.getPort(), configuration.getPath());
        webService.registerResource(configuration.getContextAlias(), publicApiController);
        initSignerClient(monitor);
    }

    @Override
    public void shutdown() {
        RpcSignerClient.shutdown();
    }

    private void loadSystemProperties(Monitor monitor) {
        monitor.info("Initializing X-Road System Properties..");
        SystemPropertiesLoader.create()
                .withCommonAndLocal()
                .load();
    }

    private void initSignerClient(Monitor monitor) {
        monitor.info("Hello from '%s' extension".formatted(NAME));
        try {
            RpcSignerClient.init("localhost", 5560, 10000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}