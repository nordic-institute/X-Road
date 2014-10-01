package ee.cyber.sdsb.proxy.serverproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpHost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.protocol.HttpContext;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.X_SSL_AUTH_FAILED;

@Slf4j
class CustomSSLSocketFactory extends SSLConnectionSocketFactory {

    CustomSSLSocketFactory(SSLContext sslContext,
            String[] supportedCipherSuites,
            X509HostnameVerifier hostNameVerifier) {
        super(sslContext, null, supportedCipherSuites, hostNameVerifier);
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
            try {
                connected.close();
            } catch (Exception ignore) {
            }

            throw new CodedException(X_SSL_AUTH_FAILED, e);
        }

        return connected;
    }

    private static void checkServerTrusted(ServiceId service,
            X509Certificate cert) throws Exception {
        if (!ServerConf.isSslAuthentication(service)) {
            return;
        }

        log.trace("Verifying service SSL certificate...");

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
        throw new Exception("Server certificate is not trusted");
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
