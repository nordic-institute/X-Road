package ee.cyber.sdsb.common.conf;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthTrustManager implements X509TrustManager {

    private static final Logger LOG =
            LoggerFactory.getLogger(AuthTrustManager.class);

    private static final AuthTrustManager instance = new AuthTrustManager();

    public static AuthTrustManager getInstance() {
        return instance;
    }

    private AuthTrustManager() {
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        LOG.debug("getAcceptedIssuers");

        return GlobalConf.getAuthTrustChain();
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
