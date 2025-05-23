/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package org.niis.xroad.proxy.core.test;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.util.CryptoUtils;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * This server proxy dummy is currently only used by one SSL test case.
 * Currently it does not provide a very meaningful test case,
 * but it uses a different SSL certificate to cause the TrustVerifier to fail
 * when establishing the SSL connection.
 */
public class DummySslServerProxy extends Server {

    @SuppressWarnings("checkstyle:MagicNumber")
    public DummySslServerProxy(String host, int port, KeyManager keyManager) throws Exception {
        SslContextFactory.Server cf = new SslContextFactory.Server();
        cf.setIncludeProtocols(CryptoUtils.SSL_PROTOCOL);
        cf.setIncludeCipherSuites(SystemProperties.getXroadTLSCipherSuites());
        cf.setSessionCachingEnabled(true);
        cf.setNeedClientAuth(true);
        cf.setSslSessionTimeout(5000);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[]{keyManager},
                new TrustManager[]{new DummyAuthTrustManager()},
                new SecureRandom());
        cf.setSslContext(ctx);

        ServerConnector connector = new ServerConnector(this, cf);

        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> {
                    httpCf.getHttpConfiguration().setSendServerVersion(false);
                    Optional.ofNullable(httpCf.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class))
                            .ifPresent(customizer -> customizer.setSniHostCheck(false));
                });

        connector.setName("ClientSslConnector");
        connector.setHost(host);
        connector.setPort(port);

        addConnector(connector);
        setHandler(new DummyHandler());
    }

    @Override
    public void destroy() {
        try {
            stop();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class DummyHandler extends Handler.Abstract {

        @Override
        public boolean handle(Request request, Response response, Callback callback) {

            response.setStatus(HttpStatus.OK_200);
            callback.succeeded();
            return true;
        }
    }

    public static class DummyAuthKeyManager extends X509ExtendedKeyManager {

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
            return new X509Certificate[]{TestCertUtil.getInternalKey().certChain[0]};
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return TestCertUtil.getInternalKey().key;
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

    public static class DummyAuthTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{TestCertUtil.getCaCert()};
        }

    }
}
