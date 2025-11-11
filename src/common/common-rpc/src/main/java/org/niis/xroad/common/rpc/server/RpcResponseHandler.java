/*
 * The MIT License
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
package org.niis.xroad.common.rpc.server;

import io.grpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.util.function.Supplier;

import static com.google.protobuf.Any.pack;

@Slf4j
@RequiredArgsConstructor
public class RpcResponseHandler {

    public <T> void handleRequest(StreamObserver<T> responseObserver, Supplier<T> handler) {
        try {
            responseObserver.onNext(handler.get());
            responseObserver.onCompleted();
        } catch (Exception e) {
            handleException(e, responseObserver);
        }
    }

    public <T> void handleException(Exception exception, StreamObserver<T> responseObserver) {
        var xrdRuntimeException = XrdRuntimeException.systemException(exception);

        log.error("Exception was thrown by gRPC handler. Exception will be sent over gRPC.", exception);

        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.INTERNAL.value())
                .setMessage(xrdRuntimeException.getMessage())
                .addDetails(pack(toProto(xrdRuntimeException)))
                .build();

        responseObserver.onError(StatusProto.toStatusRuntimeException(status));
    }

    private XrdRuntimeExceptionProto toProto(XrdRuntimeException exception) {
        final var builder = XrdRuntimeExceptionProto.newBuilder();

        builder.setIdentifier(exception.getIdentifier());
        builder.setErrorCode(exception.getCode());

        if (exception.getErrorCodeMetadata() != null && !exception.getErrorCodeMetadata().isEmpty()) {
            builder.addAllErrorMetadata(exception.getErrorCodeMetadata());
        }
        if (exception.getDetails() != null) {
            builder.setDetails(exception.getDetails());
        }

        exception.getHttpStatus().ifPresent(httpStatus -> builder.setHttpStatus(httpStatus.getCode()));

        return builder.build();
    }

}
