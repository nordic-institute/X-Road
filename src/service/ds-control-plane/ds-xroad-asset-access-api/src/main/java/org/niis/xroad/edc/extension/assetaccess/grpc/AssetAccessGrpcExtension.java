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
package org.niis.xroad.edc.extension.assetaccess.grpc;

import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.extension.assetaccess.service.AssetAccessOrchestrator;
import org.niis.xroad.edc.extension.rpc.GrpcServiceRegistry;

import java.time.Duration;

/**
 * EDC ServiceExtension that registers the AssetAccessService onto the runtime's shared gRPC server.
 */
@Extension(value = AssetAccessGrpcExtension.EXTENSION_NAME)
public class AssetAccessGrpcExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road Asset Access gRPC Extension";

    @Setting(key = "xroad.asset-access.acquisition.timeout-seconds",
            description = "Overall asset acquisition deadline in seconds (gRPC await for catalog → negotiation → transfer)",
            defaultValue = "60")
    private long acquisitionTimeoutSeconds;

    @Inject
    private AssetAccessOrchestrator assetAccessOrchestrator;

    @Inject
    private ParticipantContextService participantContextService;

    @Inject
    private GrpcServiceRegistry grpcServiceRegistry;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext extensionContext) {
        var responseHandler = new RpcResponseHandler();
        var grpcService = new AssetAccessGrpcService(assetAccessOrchestrator, participantContextService, responseHandler,
                Duration.ofSeconds(acquisitionTimeoutSeconds));
        grpcServiceRegistry.register(grpcService);
        monitor.info("Initialized extension: " + EXTENSION_NAME);
    }
}
