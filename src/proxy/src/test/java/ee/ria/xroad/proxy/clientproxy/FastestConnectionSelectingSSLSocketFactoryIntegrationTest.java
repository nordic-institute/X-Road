/**
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
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.SystemMetrics;
import ee.ria.xroad.proxy.conf.AuthKeyManager;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.DummySslServerProxy;
import ee.ria.xroad.proxy.testutil.IntegrationTest;
import ee.ria.xroad.proxy.testutil.TestGlobalConf;
import ee.ria.xroad.proxy.testutil.TestKeyConf;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.net.ServerSocket;
import java.net.URI;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.proxy.clientproxy.AuthTrustVerifier.ID_PROVIDERNAME;
import static ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

/**
 * Fastest connection selecting SSL socket factory test program.
 */
@Slf4j
@Category(IntegrationTest.class)
public class FastestConnectionSelectingSSLSocketFactoryIntegrationTest {

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

    private CloseableHttpClient client;

    @Before
    public void setup() {
        KeyConf.reload(new TestKeyConf());
        GlobalConf.reload(new TestGlobalConf());
    }

    @Test
    public void testWithSender() throws Exception {
        Assume.assumeTrue("OS not supported.", SystemUtils.IS_OS_LINUX);

        createClient();
        int port1 = getFreePort();
        int port2 = getFreePort();
        final URI uri1 = URI.create("https://127.0.0.5:" + port1);
        final URI uri2 = URI.create("https://127.0.0.5:" + port2);

        // try with only valid proxy running
        final DummySslServerProxy proxy = new DummySslServerProxy(port1, validAuthKey);
        try {
            proxy.start();
            testWithSender(uri1, uri2);
        } finally {
            proxy.stop();
        }

        // swap an invalid proxy to the valid address, set up valid proxy to the secondary address.
        final DummySslServerProxy invalid =
                new DummySslServerProxy(port1, new DummySslServerProxy.DummyAuthKeyManager());
        final DummySslServerProxy valid = new DummySslServerProxy(port2, validAuthKey);

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
        ctx.init(new KeyManager[] {AuthKeyManager.getInstance()},
                new TrustManager[] {new NoopTrustManager()},
                new SecureRandom());

        return new FastestConnectionSelectingSSLSocketFactory(ctx);
    }

    private void logFH() {
        log.info("\nfree fd = {}\n", SystemMetrics.getFreeFileDescriptorCount());
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

    static int getFreePort() {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

