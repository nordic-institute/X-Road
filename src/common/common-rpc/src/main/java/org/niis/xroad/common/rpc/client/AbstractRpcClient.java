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

import ee.ria.xroad.common.CodedException;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.niis.xroad.rpc.error.CodedExceptionProto;

import java.util.concurrent.Callable;

public abstract class AbstractRpcClient implements AutoCloseable {

    public <V> V exec(Callable<V> grpcCall) throws Exception {
        try {
            return grpcCall.call();
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw error; //TODO or handle?
            }
            com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(error);
            if (status != null) {
                handleGenericStatusRuntimeException(status);
            }
            throw error;
        }
    }

    private void handleGenericStatusRuntimeException(com.google.rpc.Status status) {
        for (Any any : status.getDetailsList()) {
            if (any.is(CodedExceptionProto.class)) {
                try {
                    final CodedExceptionProto ce = any.unpack(CodedExceptionProto.class);

                    throw CodedException.tr(ce.getFaultCode(), ce.getTranslationCode(), ce.getFaultString());
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException("Failed to parse grpc message", e);
                }
            }
        }
    }

/*     void handleTimeout() {
        var codedException = new CodedException(X_INTERNAL_ERROR, "rpc_client_timeout",
                "RPC client timed out.");
       codedException.withPrefix("[%s] ".formatted(getClass().getSimpleName()));
        throw codedException;
    }

    private String getErrorPrefix() {
        return "prefix";
    }*/
}
