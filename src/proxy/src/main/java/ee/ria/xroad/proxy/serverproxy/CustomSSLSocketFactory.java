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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;

@Slf4j
class CustomSSLSocketFactory extends SSLConnectionSocketFactory {

    CustomSSLSocketFactory(SSLContext sslContext,
            String[] supportedCipherSuites,
            HostnameVerifier hostNameVerifier) {
        super(sslContext, null, supportedCipherSuites, hostNameVerifier);
    }

    CustomSSLSocketFactory(SSLContext sslContext,
                           String[] supportedProtocols,
                           String[] supportedCipherSuites,
                           HostnameVerifier hostNameVerifier) {
        super(sslContext, supportedProtocols, supportedCipherSuites, hostNameVerifier);
    }

    @Override
    public Socket connectSocket(int timeout, Socket socket, HttpHost host,
                                InetSocketAddress remoteAddress, InetSocketAddress localAddress,
                                HttpContext context) throws IOException {
        Socket connected = super.connectSocket(timeout, socket, host,
                remoteAddress, localAddress, context);
        try {
            if (!(connected instanceof SSLSocket)) {
                throw new Exception("Failed to create SSL socket");
            }

            X509Certificate cert = getPeerCertificate((SSLSocket) connected);
            log.trace("Peer certificate: {}", cert);

            checkServerTrusted(getServiceId(context), cert);
        } catch (Exception e) {
            IOUtils.closeQuietly(connected);
            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }

        return connected;
    }

    private static void checkServerTrusted(ServiceId service,
            X509Certificate cert) throws Exception {
        if (!ServerConf.isSslAuthentication(service)) {
            return;
        }

        log.trace("Verifying service TLS certificate...");

        ClientId client = service.getClientId();
        List<X509Certificate> isCerts = ServerConf.getIsCerts(client);
        if (isCerts.isEmpty()) {
            throw new Exception(String.format(
                    "Client '%s' has no IS certificates", client));
        }

        if (isCerts.contains(cert)) {
            log.trace("Found matching IS certificate");
            return;
        }

        log.error("Could not find matching IS certificate for client '{}'",
                client);
        throw new Exception("IS certificate is not trusted");
    }

    private static ServiceId getServiceId(HttpContext context)
            throws Exception {
        Object attribute = context.getAttribute(ServiceId.class.getName());
        if (attribute == null || !(attribute instanceof ServiceId)) {
            throw new Exception("Cannot get ServiceId from HttpContext");
        }

        return (ServiceId) attribute;
    }

    private static X509Certificate getPeerCertificate(SSLSocket sslsock)
            throws Exception {
        Certificate[] certs = sslsock.getSession().getPeerCertificates();
        if (certs.length == 0) {
            throw new Exception("Could not get peer certificates");
        }

        return (X509Certificate) certs[0];
    }
}
