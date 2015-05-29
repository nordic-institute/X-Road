package ee.ria.xroad.common.conf.globalconf;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;

/**
 * This trust manager is used for connections between the security servers.
 */
@Slf4j
public class AuthTrustManager implements X509TrustManager {

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        log.trace("getAcceptedIssuers");
        try {
            return GlobalConf.getAuthTrustChain();
        } catch (Exception e) {
            log.error("Error getting authentication trust chain", e);
            return new X509Certificate[] {};
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType)
            throws CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType)
            throws CertificateException {
        // Check for the certificates later in AuthTrustVerifier
    }

}
