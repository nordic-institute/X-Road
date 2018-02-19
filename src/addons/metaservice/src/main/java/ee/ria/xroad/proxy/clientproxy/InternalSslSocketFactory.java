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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.util.InternalKeyManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 *  Holds an SSL socket factory that only trusts the internal certificate
 *  and presents the internal cert when connecting
 */
final class InternalSslSocketFactory {

    private static volatile SSLSocketFactory sslSocketFactory;
    private static Object lock = new Object();

    private InternalSslSocketFactory() { }

    static SSLSocketFactory getInstance() throws Exception {
        if (sslSocketFactory == null) {
            synchronized (lock) {
                if (sslSocketFactory == null) {
                    final InternalSSLKey sslKey = ServerConf.getSSLKey();
                    SSLContext sslContext = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
                    sslContext.init(
                            new KeyManager[]{new InternalKeyManager(sslKey)},
                            new TrustManager[]{new InternalTrustManager(sslKey.getCert())},
                            new SecureRandom());
                    sslSocketFactory = sslContext.getSocketFactory();
                }
            }
        }
        return sslSocketFactory;
    }

    static final class InternalTrustManager implements X509TrustManager {

        private final X509Certificate internalCert;

        private InternalTrustManager(X509Certificate internalCert) {
            this.internalCert = internalCert;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            //nop
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain == null || chain.length == 0 || !internalCert.equals(chain[0])) {
                throw new CertificateException("Not trusted");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

}
