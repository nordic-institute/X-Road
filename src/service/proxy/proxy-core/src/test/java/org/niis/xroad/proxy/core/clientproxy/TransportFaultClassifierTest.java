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
import org.apache.http.NoHttpResponseException;
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
import javax.net.ssl.SSLPeerUnverifiedException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;

class TransportFaultClassifierTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("failures")
    void classifiesFailure(String name, Throwable failure, boolean expectedNothingWasSent) {
        assertThat(TransportFaultClassifier.nothingWasSent(failure)).isEqualTo(expectedNothingWasSent);
    }

    @Test
    void nullMeansTheRequestWasSent() {
        assertThat(TransportFaultClassifier.nothingWasSent(null)).isFalse();
    }

    static Stream<Arguments> failures() {
        return Stream.of(nothingSent(), factoryFailures(), mixedChains(), sent()).flatMap(rows -> rows);
    }

    private static Stream<Arguments> nothingSent() {
        var hostConnectException = new HttpHostConnectException(new ConnectException("refused"),
                new HttpHost("127.0.0.1", 1));
        return Stream.of(
                        new NamedFailure("connection refused", new ConnectException("refused")),
                        new NamedFailure("apache host connect failure", hostConnectException),
                        new NamedFailure("no route to host", new NoRouteToHostException("unreachable")),
                        new NamedFailure("unresolvable host", new UnknownHostException("no.such.host")),
                        new NamedFailure("connect timeout", new ConnectTimeoutException("connect timed out")),
                        new NamedFailure("tls handshake failure", new SSLHandshakeException("handshake_failure")))
                .flatMap(fault -> Stream.of(
                        Arguments.of(fault.name(), fault.failure(), true),
                        Arguments.of(fault.name() + ", wrapped by the sender",
                                XrdRuntimeException.systemException(IO_ERROR, fault.failure()), true),
                        Arguments.of(fault.name() + ", wrapped twice",
                                new IllegalStateException(XrdRuntimeException.systemException(IO_ERROR, fault.failure())),
                                true)));
    }

    private static Stream<Arguments> factoryFailures() {
        return Stream.of(
                Arguments.of("socket factory could not connect, network unreachable",
                        networkError(new SocketException("Network is unreachable")), true),
                Arguments.of("socket factory connect timed out",
                        networkError(new SocketTimeoutException("connect timed out")), true),
                Arguments.of("socket factory network error, wrapped by the sender",
                        XrdRuntimeException.systemException(IO_ERROR,
                                networkError(new SocketException("Network is unreachable"))), true),
                Arguments.of("socket factory handshake timed out",
                        sslAuthFailed(new SocketTimeoutException("Read timed out")), true),
                Arguments.of("socket factory handshake reset",
                        sslAuthFailed(new SocketException("Connection reset")), true),
                Arguments.of("socket factory handshake rejected the provider certificate",
                        sslAuthFailed(new SSLPeerUnverifiedException("peer not verified")), true),
                Arguments.of("socket factory handshake failure, wrapped twice",
                        new IllegalStateException(XrdRuntimeException.systemException(IO_ERROR,
                                sslAuthFailed(new SocketException("Connection reset")))), true),
                Arguments.of("provider certificate check without an io cause",
                        XrdRuntimeException.systemException(SSL_AUTH_FAILED, "Certificate is not trusted"), false),
                Arguments.of("provider certificate check with a non-io cause",
                        XrdRuntimeException.systemException(SSL_AUTH_FAILED, new IllegalStateException("no ocsp"),
                                "Certificate is not trusted"), false));
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

    private static Stream<Arguments> sent() {
        return Stream.of(
                Arguments.of("read timeout", new SocketTimeoutException("Read timed out"), false),
                Arguments.of("read timeout, wrapped by the sender",
                        XrdRuntimeException.systemException(IO_ERROR, new SocketTimeoutException("Read timed out")),
                        false),
                Arguments.of("no http response", new NoHttpResponseException("failed to respond"), false),
                Arguments.of("connection reset", new SocketException("Connection reset"), false),
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

    private static XrdRuntimeException networkError(Throwable cause) {
        return XrdRuntimeException.systemException(NETWORK_ERROR, cause, "Could not connect to any target host");
    }

    private static XrdRuntimeException sslAuthFailed(IOException cause) {
        return XrdRuntimeException.systemException(SSL_AUTH_FAILED, cause, "TLS handshake failed");
    }

    private record NamedFailure(String name, Throwable failure) {
    }
}
