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
package org.niis.xroad.common.rpc.client;

import ee.ria.xroad.common.HttpStatus;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.util.concurrent.Callable;
import java.util.function.Function;

import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

public abstract class AbstractRpcClient implements AutoCloseable {

    public void exec(Runnable action) {
        try {
            action.run();
        } catch (XrdRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    public <R, T> T exec(Callable<R> action, Function<R, T> mapper) {
        return exec(() -> mapper.apply(action.call()));
    }

    public <V> V exec(Callable<V> grpcCall) {
        try {
            return grpcCall.call();
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw XrdRuntimeException.systemException(NETWORK_ERROR)
                        .origin(getRpcOrigin())
                        .details("gRPC client timed out.")
                        .build();
            }
            com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(error);
            if (status != null) {
                handleGenericStatusRuntimeException(status);
            }
            throw error;
        } catch (Exception e) {
            if (e instanceof XrdRuntimeException xrdRuntimeException) {
                throw xrdRuntimeException;
            } else {
                throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR)
                        .cause(e)
                        .details("gRPC client call failed")
                        .origin(getRpcOrigin())
                        .build();
            }
        }
    }


    private void handleGenericStatusRuntimeException(com.google.rpc.Status status) {
        for (Any any : status.getDetailsList()) {
            if (any.is(XrdRuntimeExceptionProto.class)) {
                try {
                    final var ce = any.unpack(XrdRuntimeExceptionProto.class);

                    var errorDeviation = ErrorCode.withCode(ce.getErrorCode());
                    var exceptionBuilder = XrdRuntimeException.systemException(errorDeviation)
                            .origin(getRpcOrigin())
                            .identifier(ce.getIdentifier())
                            .details(ce.getDetails())
                            .httpStatus(ce.getHttpStatus() > 0 ? HttpStatus.fromCode(ce.getHttpStatus()) : null);

                    if (!ce.getErrorMetadataList().isEmpty()) {
                        exceptionBuilder.metadataItems(ce.getErrorMetadataList());
                    }
                    throw exceptionBuilder.build();
                } catch (InvalidProtocolBufferException e) {
                    throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR)
                            .cause(e)
                            .details("Failed to parse XrdRuntimeExceptionProto from gRPC status details")
                            .build();
                }
            }
        }
    }

    /**
     * Get rpc origin that will be populated for exceptions
     */
    public abstract ErrorOrigin getRpcOrigin();

}
