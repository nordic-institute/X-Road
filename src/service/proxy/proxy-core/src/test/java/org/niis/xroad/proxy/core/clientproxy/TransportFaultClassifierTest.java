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
package org.niis.xroad.proxy.core.clientproxy;

import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionPoolTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;

class TransportFaultClassifierTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    void classifiesFailure(String name, Throwable failure, boolean expectedTransportFault) {
        assertThat(TransportFaultClassifier.isTransportFault(failure)).isEqualTo(expectedTransportFault);
    }

    @Test
    void nullIsNotATransportFault() {
        assertThat(TransportFaultClassifier.isTransportFault(null)).isFalse();
    }

    static Stream<Arguments> failures() {
        return Stream.of(transportFaults(), mixedChains(), otherFailures()).flatMap(rows -> rows);
    }

    private static Stream<Arguments> transportFaults() {
        var hostConnectException = new HttpHostConnectException(new ConnectException("refused"),
                new HttpHost("127.0.0.1", 1));
        return Stream.of(
                        new NamedFailure("connection refused", new ConnectException("refused")),
                        new NamedFailure("apache host connect failure", hostConnectException),
                        new NamedFailure("no route to host", new NoRouteToHostException("unreachable")),
                        new NamedFailure("unresolvable host", new UnknownHostException("no.such.host")),
                        new NamedFailure("connect timeout", new ConnectTimeoutException("connect timed out")),
                        new NamedFailure("tls handshake failure", new SSLHandshakeException("handshake_failure")),
                        new NamedFailure("read timeout", new SocketTimeoutException("Read timed out")))
                .flatMap(fault -> Stream.of(
                        Arguments.of(fault.name(), fault.failure(), true),
                        Arguments.of(fault.name() + ", wrapped by the sender",
                                XrdRuntimeException.systemException(IO_ERROR, fault.failure()), true),
                        Arguments.of(fault.name() + ", wrapped twice",
                                new IllegalStateException(XrdRuntimeException.systemException(IO_ERROR, fault.failure())),
                                true)));
    }

    private static Stream<Arguments> mixedChains() {
        return Stream.of(
                Arguments.of("connection pool timeout caused by a refused connection",
                        new ConnectionPoolTimeoutException("pool exhausted").initCause(new ConnectException("refused")),
                        true),
                Arguments.of("refused connection caused by a connection pool timeout",
                        new ConnectException("refused").initCause(new ConnectionPoolTimeoutException("pool exhausted")),
                        true));
    }

    private static Stream<Arguments> otherFailures() {
        return Stream.of(
                Arguments.of("connection pool timeout", new ConnectionPoolTimeoutException("pool exhausted"), false),
                Arguments.of("connection pool timeout, wrapped by the sender",
                        XrdRuntimeException.systemException(IO_ERROR,
                                new ConnectionPoolTimeoutException("pool exhausted")), false),
                Arguments.of("connection pool timeout, wrapped twice",
                        new IllegalStateException(XrdRuntimeException.systemException(IO_ERROR,
                                new ConnectionPoolTimeoutException("pool exhausted"))), false),
                Arguments.of("http error raised inside the send after a response arrived",
                        XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s: %s", 503,
                                "Service Unavailable"), false),
                Arguments.of("http error carrying a client error status",
                        XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s: %s", 404,
                                "Not Found"), false),
                Arguments.of("coded fault", XrdRuntimeException.systemException(SERVICE_FAILED, "service failed"),
                        false),
                Arguments.of("agreement token rejection",
                        XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "token rejected"), false),
                Arguments.of("plain io exception", new IOException("broken"), false),
                Arguments.of("plain io exception, wrapped",
                        XrdRuntimeException.systemException(IO_ERROR, new IOException("broken")), false),
                Arguments.of("tls failure that is not a handshake failure", new SSLException("bad record mac"), false),
                Arguments.of("unrelated runtime exception", new IllegalArgumentException("bad argument"), false));
    }

    private record NamedFailure(String name, Throwable failure) {
    }
}
