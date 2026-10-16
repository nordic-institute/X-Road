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
package org.niis.xroad.edc.issuer.didresolution;

import okhttp3.OkHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.extension.rpc.GrpcServiceRegistry;

import java.time.Duration;

/**
 * EDC ServiceExtension that builds the submitted DID resolver once and serves it over the runtime's shared gRPC
 * server.
 */
@Extension(value = DidResolutionExtension.EXTENSION_NAME)
public class DidResolutionExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road DID Resolution gRPC Extension";

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private TypeManager typeManager;

    @Inject
    private GrpcServiceRegistry grpcServiceRegistry;

    @Inject
    private Monitor monitor;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var resolver = SubmittedDidResolver.create(
                okHttpClient, typeManager.getMapper(), fetchTimeout(context, monitor), monitor);
        grpcServiceRegistry.register(new DidResolutionGrpcService(new BatchDidResolver(resolver, monitor), new RpcResponseHandler()));
        monitor.info("Initialized extension: " + EXTENSION_NAME);
    }

    static Duration fetchTimeout(ServiceExtensionContext context, Monitor monitor) {
        var key = DspConfigKeys.DID_RESOLUTION_FETCH_TIMEOUT_MS;
        long defaultMillis = key.convertedDefaultValue();
        long configuredMillis = context.getSetting(key.key(), defaultMillis);
        if (configuredMillis <= 0) {
            monitor.warning("%s: %s must be positive, got %d; using %d ms"
                    .formatted(EXTENSION_NAME, key.key(), configuredMillis, defaultMillis));
            return Duration.ofMillis(defaultMillis);
        }
        return Duration.ofMillis(configuredMillis);
    }
}
