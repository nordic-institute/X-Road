/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.wsdlvalidator;

import org.apache.cxf.tools.validator.WSDLValidator;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Overrides the default HTTPSURLConnection SSL socket factory with one that trusts all server certificates and
 * does not verify host names. In addition, if a keystore file and password are provided, the socket factory uses
 * the keystore as a source for client certificates. Finally, calls WSDLValidator with the original command line
 * arguments.
 *
 * The keystore file name and password are read from the following system properties
 * <ul>
 * <li>ee.ria.xroad.internalKeyStore</li>
 * <li>ee.ria.xroad.internalKeyStorePassword</li>
 * </ul>
 *
 * @see org.apache.cxf.tools.validator.WSDLValidator
 */
public final class WSDLValidatorLoader {

    public static final String INTERNAL_KEY_STORE = "ee.ria.xroad.internalKeyStore";
    public static final String INTERNAL_KEY_STORE_PASSWORD = "ee.ria.xroad.internalKeyStorePassword";

    private WSDLValidatorLoader() { }

    /**
     * WSDLValidator wrapper
     */
    public static void main(String[] args) throws Exception {
        final String keyStore = System.getProperty(INTERNAL_KEY_STORE);
        final String keyStorePassword = System.getProperty(INTERNAL_KEY_STORE_PASSWORD);

        KeyManager[] keyManagers = null;
        if (keyStore != null && keyStorePassword != null) {
            final char[] password = keyStorePassword.toCharArray();
            final KeyStore ks = KeyStore.getInstance("pkcs12");
            try (FileInputStream fis = new FileInputStream(keyStore)) {
                ks.load(fis, password);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, password);
            keyManagers = kmf.getKeyManagers();
        }

        final SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(keyManagers, new TrustManager[] {new NoopTrustManager()}, new SecureRandom());

        HttpsURLConnection.setDefaultHostnameVerifier((s, sslSession) -> true);
        HttpsURLConnection.setDefaultSSLSocketFactory(sslCtx.getSocketFactory());

        WSDLValidator.main(args);
    }

    static class NoopTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            //NOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            //NOP
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

    }
}
