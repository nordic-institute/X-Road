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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.impl.AuthKeyManager;
import org.niis.xroad.proxy.core.test.DummySslServerProxy;
import org.niis.xroad.test.globalconf.TestGlobalConf;
import org.niis.xroad.test.keyconf.TestKeyConf;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.URI;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static ee.ria.xroad.common.TestPortUtils.findRandomPort;
import static org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier.ID_PROVIDERNAME;
import static org.niis.xroad.proxy.core.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

/**
 * Fastest connection selecting SSL socket factory test program.
 */
@Slf4j
class FastestConnectionSelectingSSLSocketFactoryIntegrationTest {

    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private final DummySslServerProxy.DummyAuthKeyManager validAuthKey = new DummySslServerProxy.DummyAuthKeyManager() {
        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return TestCertUtil.getProducer().certChain;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return TestCertUtil.getProducer().key;
        }
    };

    private KeyConfProvider keyConfProvider;
    private AuthTrustVerifier authTrustVerifier;
    private CloseableHttpClient client;

    @BeforeEach
    public void setup() {
        GlobalConfProvider globalConfProvider = new TestGlobalConf();
        keyConfProvider = new TestKeyConf(globalConfProvider);
        authTrustVerifier = new AuthTrustVerifier(keyConfProvider, new CertHelper(globalConfProvider),
                new CertChainFactory(globalConfProvider));

        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void testWithSender() throws Exception {
        createClient();
        var host = "127.0.0.1";
        int port1 = findRandomPort();
        int port2 = findRandomPort();
        final URI uri1 = URI.create("https://%s:%s".formatted(host, port1));
        final URI uri2 = URI.create("https://%s:%s".formatted(host, port2));

        // try with only valid proxy running
        final DummySslServerProxy proxy = new DummySslServerProxy(host, port1, validAuthKey);
        try {
            proxy.start();
            testWithSender(uri1, uri2);
        } finally {
            proxy.stop();
        }

        // swap an invalid proxy to the valid address, set up valid proxy to the secondary address.
        final DummySslServerProxy invalid =
                new DummySslServerProxy(host, port1, new DummySslServerProxy.DummyAuthKeyManager());
        final DummySslServerProxy valid = new DummySslServerProxy(host, port2, validAuthKey);

        try {
            invalid.start();
            valid.start();
            testWithSender(uri1, uri2);
        } finally {
            valid.stop();
            invalid.stop();
        }
    }

    private void testWithSender(URI... addresses) throws Exception {
        try (HttpSender sender = new HttpSender(client)) {
            sender.setAttribute(ID_TARGETS, addresses);
            sender.setAttribute(ID_PROVIDERNAME, ServiceId.Conf.create("INSTANCE", "CLASS", "CODE", "SUB", "SERVICE"));
            sender.setConnectionTimeout(1000);
            sender.doGet(new URI("https://localhost:1234/"));
        }
    }

    private void createClient() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create();

        socketFactoryRegistry.register("http", PlainConnectionSocketFactory.INSTANCE);
        socketFactoryRegistry.register("https", createSSLSocketFactory());

        PoolingHttpClientConnectionManager connMgr =
                new PoolingHttpClientConnectionManager(
                        socketFactoryRegistry.build());
        connMgr.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connMgr.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        int timeout = 10000;
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setSocketTimeout(timeout);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setConnectionManager(connMgr);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        client = cb.build();
    }

    private SSLConnectionSocketFactory createSSLSocketFactory() throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[]{new AuthKeyManager(keyConfProvider)},
                new TrustManager[]{new NoopTrustManager()},
                new SecureRandom());

        return new FastestConnectionSelectingSSLSocketFactory(authTrustVerifier, ctx.getSocketFactory());
    }

    static class NoopTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) {
            //NOP
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) {
            //NOP
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

}

