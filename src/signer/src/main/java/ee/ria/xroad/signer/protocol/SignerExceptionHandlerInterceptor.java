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
package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.CodedExceptionProto;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;

import static com.google.protobuf.Any.pack;
import static java.util.Optional.ofNullable;

public class SignerExceptionHandlerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers,
                                                                 ServerCallHandler<ReqT, RespT> next) {
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        return new ExceptionHandler<>(delegate, call, headers);
    }

    private static class ExceptionHandler<T, R> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<T> {

        private final ServerCall<T, R> delegate;
        private final Metadata headers;

        ExceptionHandler(ServerCall.Listener<T> listener, ServerCall<T, R> serverCall, Metadata headers) {
            super(listener);
            this.delegate = serverCall;
            this.headers = headers;
        }

        @Override
        public void onHalfClose() {
            try {
                super.onHalfClose();
            } catch (RuntimeException ex) {
                handleException(ex, delegate, headers);
                throw ex;
            }
        }

        private void handleException(RuntimeException exception, ServerCall<T, R> serverCall, Metadata headers) {
            if (exception instanceof CodedException) {
                CodedException codedException = (CodedException) exception;

                com.google.rpc.Status rpcStatus = com.google.rpc.Status.newBuilder()
                        .setCode(Status.Code.INTERNAL.value())
                        .setMessage(codedException.getMessage())
                        .addDetails(pack(toProto(codedException)))
                        .build();

                StatusRuntimeException statusRuntimeException = StatusProto.toStatusRuntimeException(rpcStatus);

                var newStatus = Status.fromThrowable(statusRuntimeException);
                // Get metadata from statusRuntimeException
                Metadata newHeaders = statusRuntimeException.getTrailers();

                serverCall.close(newStatus, newHeaders);
            } else {
                serverCall.close(Status.UNKNOWN, headers);
            }
        }

        private CodedExceptionProto toProto(CodedException codedException) {
            final CodedExceptionProto.Builder codedExceptionBuilder = CodedExceptionProto.newBuilder();

            ofNullable(codedException.getFaultCode()).ifPresent(codedExceptionBuilder::setFaultCode);
            ofNullable(codedException.getFaultActor()).ifPresent(codedExceptionBuilder::setFaultActor);
            ofNullable(codedException.getFaultDetail()).ifPresent(codedExceptionBuilder::setFaultDetail);
            ofNullable(codedException.getFaultString()).ifPresent(codedExceptionBuilder::setFaultString);
            ofNullable(codedException.getTranslationCode()).ifPresent(codedExceptionBuilder::setTranslationCode);

            return codedExceptionBuilder.build();
        }
    }

}
