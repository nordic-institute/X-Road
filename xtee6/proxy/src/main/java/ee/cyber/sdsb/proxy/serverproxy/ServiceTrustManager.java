package ee.cyber.sdsb.proxy.serverproxy;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;


/**
 * Verifies X509 certificates of SSL connection of service.
 */
class ServiceTrustManager implements X509TrustManager {
    public X509Certificate[] getAcceptedIssuers()   {
        // TODO: FIXME: Return an array of certificate authority
        // certificates which are trusted for authenticating peers.

        return new X509Certificate[]{};
    }

    public void checkClientTrusted(X509Certificate[] certs,
            String authType) throws CertificateException {
        // Not allowed.
        throw new CertificateException();
    }

    public void checkServerTrusted(X509Certificate[] certs,
            String authType) throws CertificateException {
        // TODO: FIXME:
        // Siin peame siis verifitseerima adapterilt saadetud SSL sertifikaadi.
    }
}
