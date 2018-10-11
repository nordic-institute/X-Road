package ee.ria.xroad.proxy.util;

import ee.ria.xroad.common.conf.globalconf.AuthTrustManager;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.conf.AuthKeyManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility class to create SSLContexts
 */
public final class SSLContextUtil {

    private SSLContextUtil(){ }

    /**
     * Creates SSLContext used in between security servers
     * @return
     */
    public static SSLContext createXroadSSLContext() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {AuthKeyManager.getInstance()}, new TrustManager[] {new AuthTrustManager()},
                new SecureRandom());
        return ctx;
    }
}
