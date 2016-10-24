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
package ee.ria.xroad.proxy.testsuite;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.PortNumbers;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;

/**
 * This server proxy dummy is currently only used by one SSL test case.
 * Currently it does not provide a very meaningful test case,
 * but it uses a different SSL certificate to cause the TrustVerifier to fail
 * when establishing the SSL connection.
 */
@SuppressWarnings("unchecked")
public class DummySslServerProxy extends Server implements StartStop {

    private static final Logger LOG =
            LoggerFactory.getLogger(DummySslServerProxy.class);

    DummySslServerProxy() throws Exception {
        SslContextFactory cf = new SslContextFactory(false);
        cf.setNeedClientAuth(true);

        cf.setIncludeCipherSuites(CryptoUtils.getINCLUDED_CIPHER_SUITES());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new DummyAuthKeyManager()},
                new TrustManager[] {new DummyAuthTrustManager()},
                new SecureRandom());
        cf.setSslContext(ctx);

        SslSelectChannelConnector connector = new SslSelectChannelConnector(cf);

        connector.setName("ClientSslConnector");
        connector.setHost("127.0.0.5");
        connector.setPort(PortNumbers.PROXY_PORT);

        addConnector(connector);
    }

    private class DummyAuthKeyManager extends X509ExtendedKeyManager {

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public String chooseClientAlias(String[] keyType, Principal[] issuers,
                Socket socket) {
            return "AuthKeyManager";
        }

        @Override
        public String[] getServerAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public String chooseServerAlias(String keyType, Principal[] issuers,
                Socket socket) {
            return "AuthKeyManager";
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return new X509Certificate[] {TestCertUtil.getProducer().cert};
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return TestCertUtil.getProducer().key;
        }

        @Override
        public String chooseEngineClientAlias(String[] keyType, Principal[] issuers,
                SSLEngine engine) {
            return "AuthKeyManager";
        }

        @Override
        public String chooseEngineServerAlias(String keyType, Principal[] issuers,
                SSLEngine engine) {
            return "AuthKeyManager";
        }

    }

    private class DummyAuthTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
       }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[] {TestCertUtil.getCaCert()};
        }

    }
}
