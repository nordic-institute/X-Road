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

package org.niis.xroad.confclient.core.admin;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.confclient.core.config.ConfClientJobConfig;
import org.niis.xroad.confclient.proto.AdminServiceGrpc;
import org.niis.xroad.confclient.proto.DiagnosticsStatus;
import org.niis.xroad.rpc.common.Empty;

import java.util.function.Supplier;

import static ee.ria.xroad.common.util.TimeUtils.offsetDateTimeToEpochMillis;
import static java.util.Optional.ofNullable;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class AdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final ConfClientJobConfig.ConfigurationClientJobListener listener;

    @Override
    public void getStatus(Empty request, StreamObserver<DiagnosticsStatus> responseObserver) {
        handleRequest(responseObserver, this::handleGetStatus);
    }

    private DiagnosticsStatus handleGetStatus() {
        log.info("handler /status");

        var status = listener.getStatus();

        DiagnosticsStatus.Builder responseBuilder = DiagnosticsStatus.newBuilder();

        responseBuilder.setReturnCode(status.getReturnCode());
        ofNullable(offsetDateTimeToEpochMillis(status.getPrevUpdate())).ifPresent(responseBuilder::setPrevUpdate);
        ofNullable(offsetDateTimeToEpochMillis(status.getNextUpdate())).ifPresent(responseBuilder::setNextUpdate);
        if (StringUtils.isNotBlank(status.getDescription())) {
            responseBuilder.setDescription(status.getDescription());
        }

        return responseBuilder.build();
    }

    private <T> void handleRequest(StreamObserver<T> responseObserver, Supplier<T> handler) {
        try {
            responseObserver.onNext(handler.get());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }
}
