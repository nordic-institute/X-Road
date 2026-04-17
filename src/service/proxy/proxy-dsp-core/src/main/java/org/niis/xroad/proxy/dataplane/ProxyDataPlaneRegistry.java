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

package org.niis.xroad.proxy.dataplane;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.dataplane.Dataplane;
import org.eclipse.dataplane.domain.DataAddress;
import org.eclipse.dataplane.domain.Result;
import org.eclipse.dataplane.domain.dataflow.DataFlow;
import org.eclipse.dataplane.domain.registration.Authorization;
import org.eclipse.dataplane.domain.registration.AuthorizationProfile;
import org.eclipse.dataplane.logic.OnPrepare;
import org.eclipse.dataplane.logic.OnStart;
import org.eclipse.dataplane.logic.OnStarted;
import org.eclipse.dataplane.logic.OnTerminate;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.net.URI;
import java.util.List;

@Slf4j
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public class ProxyDataPlaneRegistry {
    private final ProxyDspProperties dspProperties;
    private final DataPlaneServer dataPlaneServer;

    private Dataplane fullDataplane;

    @PostConstruct
    public void initialize() {
        log.info("Registering data-planes..");
        fullDataplane = Dataplane.newInstance()
                .endpoint(URI.create(dspProperties.dataFlowEndpoint()))
                .transferType("Xrd-PULL")
                .registerAuthorization(new LocalhostPassthroughAuthorization())
                .onPrepare(new DataplaneOnPrepare())
                .onStart(new DataplaneOnStart())
                .onStarted(new DataplaneOnStarted())
                .onCompleted(Result::success)
                .onTerminate(new DataplaneOnTerminate())
                .build();

        dataPlaneServer.registerJaxRsResource("/full/api/", fullDataplane.controller());

        try {
            dataPlaneServer.start();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR, "Failed to start dataplane server", e);
        }

        log.info("Registering X-Road data plane on control plane.");
        fullDataplane.registerOn(dspProperties.controlPlaneEndpoint())
                .orElseThrow(e -> XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR,
                        "Cannot register dataplane on controlplane", e));
    }

    public Dataplane getFullDataplane() {
        return fullDataplane;
    }

    private static final class DataplaneOnPrepare implements OnPrepare {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            return Result.success(dataFlow);
        }
    }

    private final class DataplaneOnStart implements OnStart {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            return switch (dataFlow.getTransferType()) {
                case "Xrd-PULL" -> {
                    var dataAddress = new DataAddress("http",
                            dspProperties.dataFlowEndpoint(), List.of());
                    dataFlow.setDataAddress(dataAddress);
                    yield Result.success(dataFlow);
                }
                default -> Result.failure(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR,
                        "TransferType %s not supported".formatted(dataFlow.getTransferType())));
            };
        }
    }

    private static final class DataplaneOnStarted implements OnStarted {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            log.info("Starting X-Road Sample data plane.");
            return Result.success(dataFlow);
        }
    }

    private static final class DataplaneOnTerminate implements OnTerminate {
        @Override
        public Result<DataFlow> action(DataFlow dataFlow) {
            log.info("Terminating X-Road Sample data plane.");
            return Result.success(dataFlow);
        }
    }

    /**
     * Pass-through authorization for localhost-only signaling.
     * The signaling port (5590) is bound to 127.0.0.1, so no external
     * access is possible. This simply extracts a caller ID from the
     * Authorization header without validation.
     */
    private static final class LocalhostPassthroughAuthorization implements Authorization {
        private static final String TYPE = "passthrough";

        @Override
        public String type() {
            return TYPE;
        }

        @Override
        public Result<String> authorizationHeader(AuthorizationProfile profile) {
            return Result.success("Bearer passthrough");
        }

        @Override
        public Result<String> extractCallerId(String authorizationHeader) {
            // Accept any authorization header from localhost CP
            return Result.success("controlplane");
        }
    }

}
