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
package org.niis.xroad.signer.client.util;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.experimental.UtilityClass;
import org.niis.xroad.rpc.error.CodedExceptionProto;
import org.niis.xroad.signer.api.exception.SignerException;

import java.util.function.Function;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;

@UtilityClass
public class SignerRpcUtils {

    public static void tryToRun(Action action) throws SignerException {
        try {
            action.run();
        } catch (SignerException e) {
            throw e;
        } catch (CodedException e) {
            throw new SignerException(e);
        } catch (Exception e) {
            throw new SignerException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    public static <R, T> T tryToRun(ActionWithResult<R> action, Function<R, T> mapper) throws SignerException {
        return tryToRun(() -> mapper.apply(action.run()));
    }

    public static <T> T tryToRun(ActionWithResult<T> action) throws SignerException {
        try {
            return action.run();
        } catch (SignerException e) {
            throw e;
        } catch (CodedException e) {
            throw new SignerException(e);
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw SignerException.tr(X_NETWORK_ERROR, "signer_client_timeout",
                                "Signer client timed out. " + error.getStatus().getDescription())
                        .withPrefix(SIGNER_X);
            }
            com.google.rpc.Status status = io.grpc.protobuf.StatusProto.fromThrowable(error);
            if (status != null) {
                handleGenericStatusRuntimeException(status);
            }
            throw error;
        } catch (Exception e) {
            throw new SignerException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    private static void handleGenericStatusRuntimeException(com.google.rpc.Status status) {
        for (Any any : status.getDetailsList()) {
            if (any.is(CodedExceptionProto.class)) {
                try {
                    final CodedExceptionProto ce = any.unpack(CodedExceptionProto.class);
                    throw CodedException.tr(ce.getFaultCode(), ce.getTranslationCode(), ce.getFaultString())
                            .withPrefix(SIGNER_X);
                } catch (InvalidProtocolBufferException e) {
                    throw new RuntimeException("Failed to parse grpc message", e);
                }
            }
        }
    }

    public interface ActionWithResult<T> {
        T run() throws Exception;
    }

    public interface Action {
        void run() throws Exception;
    }
}
