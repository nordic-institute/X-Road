package ee.cyber.sdsb.proxy.serverproxy;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifies X509 certificates of SSL connection from clients.
 */
class ServerTrustManager implements X509TrustManager {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServerTrustManager.class);

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        LOG.debug("getAcceptedIssuers");

        // TODO: FIXME: Return an array of certificate authority
        // certificates which are trusted for authenticating peers.

        return new X509Certificate[] {/* caCert */};
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType)
            throws CertificateException {
        LOG.debug("checkClientTrusted {}", authType);

        // TODO: FIXME:
        // Siin peame siis verifitseerima IS-ilt saadetud SSL sertifikaadi.

    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs,
            String authType) throws CertificateException {
        LOG.debug("checkServerTrusted {}", authType);

        // Not allowed.
        throw new CertificateException();
    }
}

