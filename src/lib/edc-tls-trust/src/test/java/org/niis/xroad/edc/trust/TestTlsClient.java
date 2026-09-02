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
package org.niis.xroad.edc.trust;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;

import java.security.SecureRandom;

/**
 * Drives a real TLS handshake against {@link TestTlsServer} with a given trust manager under test, connecting by
 * an explicit host string so the socket-based {@code checkServerTrusted} overload sees a real, client-chosen
 * peer host and port — precisely what {@link DsTlsCompositeTrustManager}'s vault-endpoint routing keys off.
 */
final class TestTlsClient {

    private TestTlsClient() {
    }

    /**
     * @return {@code true} if the handshake succeeded (the trust manager accepted the server's certificate),
     * {@code false} if it was rejected.
     */
    static boolean handshakeSucceeds(String host, int port, X509ExtendedTrustManager trustManager) throws Exception {
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new X509ExtendedTrustManager[] {trustManager}, new SecureRandom());
        try (var socket = (SSLSocket) sslContext.getSocketFactory().createSocket(host, port)) {
            socket.startHandshake();
            return true;
        } catch (SSLHandshakeException e) {
            return false;
        }
    }
}
