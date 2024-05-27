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

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.dataplane.api.validation.ConsumerPullTransferDataAddressResolver;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.http.spi.EdcHttpClient;
import org.eclipse.edc.policy.engine.spi.PolicyEngine;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import java.util.concurrent.Executors;

@SuppressWarnings("checkstyle:MagicNumber") //TODO xroad8
//@Provides({ DataPlaneManager.class, PipelineService.class, DataTransferExecutorServiceContainer.class, TransferServiceRegistry.class })
@Extension(value = XrdPayloadSignerExtension.NAME)
public class XrdPayloadSignerExtension implements ServiceExtension {

    public static final String NAME = "X-Road Data Plane Public API";
    private static final int DEFAULT_PUBLIC_PORT = 9293;
    private static final String PUBLIC_API_CONFIG = "web.http.xroad.public";
    private static final String PUBLIC_CONTEXT_ALIAS = "xroad";
    private static final String PUBLIC_CONTEXT_PATH = "/xroad/public";
    private static final int DEFAULT_THREAD_POOL = 10;

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
    private ContractNegotiationStore contractNegotiationStore;

    @Inject
    private PolicyEngine policyEngine;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeManager typeManager;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject(required = false)
    private XRoadMessageLog xRoadMessageLog;

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
        var executorService = executorInstrumentation.instrument(
                Executors.newFixedThreadPool(DEFAULT_THREAD_POOL),
                "Data plane proxy transfers"
        );

        var signService = new XrdSignatureService();
        var publicApiController = new XrdDataPlanePublicApiController(pipelineService, dataAddressResolver,
                new XrdEdcSignService(signService, monitor), monitor, executorService,
                contractNegotiationStore, policyEngine, xRoadMessageLog);

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
        monitor.info("Initializing Signer client");
        try {
            RpcSignerClient.init("localhost", 5560, 10000);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
