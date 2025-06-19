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

package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.SystemProperties;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class CustomClientTlsSSLSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory internalFactory;
    private final String[] enabledProtocols;
    private final String[] supportedCipherSuites;

    public CustomClientTlsSSLSocketFactory(SSLSocketFactory internalFactory) {
        this.internalFactory = internalFactory;
        this.enabledProtocols = SystemProperties.getProxyClientTLSProtocols();
        this.supportedCipherSuites = SystemProperties.getProxyClientTLSCipherSuites();
    }

    private Socket restrict(Socket socket) {
        if (socket instanceof SSLSocket sslSocket) {
            sslSocket.setEnabledProtocols(enabledProtocols);
            sslSocket.setEnabledCipherSuites(supportedCipherSuites);
        }
        return socket;
    }

    @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return restrict(internalFactory.createSocket(s, host, port, autoClose));
    }

    @Override public Socket createSocket(String host, int port) throws IOException {
        return restrict(internalFactory.createSocket(host, port));
    }

    @Override public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return restrict(internalFactory.createSocket(host, port, localHost, localPort));
    }

    @Override public Socket createSocket(InetAddress host, int port) throws IOException {
        return restrict(internalFactory.createSocket(host, port));
    }

    @Override public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return restrict(internalFactory.createSocket(address, port, localAddress, localPort));
    }

    @Override public String[] getDefaultCipherSuites() {
        return internalFactory.getDefaultCipherSuites();
    }

    @Override public String[] getSupportedCipherSuites() {
        return supportedCipherSuites;
    }
}
