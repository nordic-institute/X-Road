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

package org.niis.xroad.proxy.core.clientproxy;

import org.apache.http.HttpHost;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;

class DataCallFailoverTest {

    private static XrdRuntimeException couldNotConnect(Exception cause) {
        return XrdRuntimeException.systemException(NETWORK_ERROR, cause, "Could not connect to any target host");
    }

    @Test
    void connectionRefusedIsAConnectionFailure() {
        assertThat(DataCallFailover.isConnectionFailure(new ConnectException("Connection refused"))).isTrue();
        assertThat(DataCallFailover.isConnectionFailure(couldNotConnect(new ConnectException("Connection refused")))).isTrue();
        assertThat(DataCallFailover.isConnectionFailure(new HttpHostConnectException(
                new IOException("refused"), new HttpHost("a.example", 443), (InetAddress[]) null))).isTrue();
    }

    @Test
    void connectTimeoutIsAConnectionFailure() {
        assertThat(DataCallFailover.isConnectionFailure(new ConnectTimeoutException("connect timed out"))).isTrue();
        assertThat(DataCallFailover.isConnectionFailure(couldNotConnect(new SocketTimeoutException("Connect timed out")))).isTrue();
    }

    @Test
    void connectPhaseIsToldFromReadPhaseByTypeNotByMessage() {
        assertThat(DataCallFailover.isConnectionFailure(couldNotConnect(new SocketTimeoutException("Read timed out")))).isTrue();
        assertThat(DataCallFailover.isConnectionFailure(new SocketTimeoutException("connect timed out"))).isFalse();
    }

    @Test
    void noRouteAndUnknownHostAreConnectionFailures() {
        assertThat(DataCallFailover.isConnectionFailure(new NoRouteToHostException("No route to host"))).isTrue();
        assertThat(DataCallFailover.isConnectionFailure(couldNotConnect(new UnknownHostException("a.example")))).isTrue();
    }

    @Test
    void aReadTimeoutIsNotAConnectionFailure() {
        assertThat(DataCallFailover.isConnectionFailure(new SocketTimeoutException("Read timed out"))).isFalse();
    }

    @Test
    void tlsErrorsAreNotConnectionFailures() {
        assertThat(DataCallFailover.isConnectionFailure(
                XrdRuntimeException.systemException(SSL_AUTH_FAILED, new SSLHandshakeException("bad cert"), "TLS handshake failed")))
                .isFalse();
        assertThat(DataCallFailover.isConnectionFailure(
                couldNotConnect(new SSLHandshakeException("All target hosts are in failure cooldown")))).isFalse();
    }

    @Test
    void httpErrorResponsesAndOtherFailuresAreNotConnectionFailures() {
        assertThat(DataCallFailover.isConnectionFailure(XrdRuntimeException.systemException(ACCESS_DENIED, "denied"))).isFalse();
        assertThat(DataCallFailover.isConnectionFailure(new IOException("Connection reset"))).isFalse();
        assertThat(DataCallFailover.isConnectionFailure(new IllegalStateException("boom"))).isFalse();
    }
}
