package ee.cyber.sdsb.proxy.serverproxy;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Dummy trust manager class, actual server certificate verification is
 * done in CustomSSLSocketFactory.
 */
@Slf4j
class ServiceTrustManager implements X509TrustManager {

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        log.trace("checkClientTrusted()");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        log.trace("checkServerTrusted()");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        log.trace("getAcceptedIssuers()");
        return new X509Certificate[] {};
    }

}
