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
package org.niis.xroad.common.rpc.client;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.rpc.error.XrdRuntimeExceptionProto;

import java.util.concurrent.Callable;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AbstractRpcClientTest {

    private final AbstractRpcClient client = clientMapping(null);

    @Test
    void deadlineExceededIsANetworkError() {
        assertThatThrownBy(() -> failing(client, Status.DEADLINE_EXCEEDED.asRuntimeException()))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.NETWORK_ERROR)).isTrue();
                    assertThat(e.originatesFrom(ErrorOrigin.SIGNER)).isTrue();
                    assertThat(e.getDetails()).isEqualTo("gRPC client timed out.");
                });
    }

    @Test
    void aStatusCarryingAnErrorDetailMapsToTheServerErrorCodeIdentifierAndDetails() {
        var status = com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.INTERNAL.value())
                .addDetails(Any.pack(XrdRuntimeExceptionProto.newBuilder()
                        .setIdentifier("server-failure-1")
                        .setErrorCode(ErrorCode.DATABASE_ERROR.code())
                        .setDetails("server side detail")
                        .build()))
                .build();

        assertThatThrownBy(() -> failing(client, StatusProto.toStatusRuntimeException(status)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.DATABASE_ERROR)).isTrue();
                    assertThat(e.getIdentifier()).isEqualTo("server-failure-1");
                    assertThat(e.getDetails()).isEqualTo("server side detail");
                    assertThat(e.originatesFrom(ErrorOrigin.SIGNER)).isTrue();
                });
    }

    @Test
    void aStatusWithoutAnErrorDetailIsRethrownRaw() {
        var raw = Status.UNAVAILABLE.asRuntimeException();

        assertThatThrownBy(() -> failing(client, raw)).isSameAs(raw);
    }

    @Test
    void aNonGrpcExceptionIsWrappedAsAnInternalError() {
        assertThatThrownBy(() -> failing(client, new IllegalStateException("boom")))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue();
                    assertThat(e.originatesFrom(ErrorOrigin.SIGNER)).isTrue();
                });
    }

    @Test
    void aSubclassOverridingTheStatusMappingReplacesTheDefault() {
        var overriding = clientMapping(error ->
                XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR).cause(error).build());

        assertThatThrownBy(() -> failing(overriding, Status.UNAVAILABLE.asRuntimeException()))
                .isInstanceOfSatisfying(XrdRuntimeException.class,
                        e -> assertThat(e.isCausedBy(ErrorCode.NETWORK_ERROR)).isTrue());
    }

    @Test
    void aStatusCarryingAnUnparsableErrorDetailMapsToAnInternalErrorWithTheRpcOrigin() {
        var status = com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.INTERNAL.value())
                .addDetails(Any.newBuilder()
                        .setTypeUrl(Any.pack(XrdRuntimeExceptionProto.getDefaultInstance()).getTypeUrl())
                        .setValue(ByteString.copyFrom(new byte[] {0x0A, 0x7F}))
                        .build())
                .build();

        assertThatThrownBy(() -> failing(client, StatusProto.toStatusRuntimeException(status)))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
                    assertThat(e.isCausedBy(ErrorCode.INTERNAL_ERROR)).isTrue();
                    assertThat(e.originatesFrom(ErrorOrigin.SIGNER)).isTrue();
                });
    }

    private static AbstractRpcClient clientMapping(Function<StatusRuntimeException, RuntimeException> mapping) {
        return new AbstractRpcClient() {
            @Override
            public ManagedChannel getChannel() {
                return null;
            }

            @Override
            public ErrorOrigin getRpcOrigin() {
                return ErrorOrigin.SIGNER;
            }

            @Override
            public void close() {
                // nothing to release
            }

            @Override
            protected RuntimeException mapFailedStatus(StatusRuntimeException error) {
                return mapping == null ? super.mapFailedStatus(error) : mapping.apply(error);
            }
        };
    }

    private static Object failing(AbstractRpcClient rpcClient, RuntimeException failure) {
        return rpcClient.exec((Callable<Object>) () -> {
            throw failure;
        });
    }
}
