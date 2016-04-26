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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.AuthTrustManager;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.SystemMetrics;
import ee.ria.xroad.proxy.clientproxy.FastestSocketSelector.SocketInfo;
import ee.ria.xroad.proxy.conf.AuthKeyManager;

import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.proxy.clientproxy.FastestConnectionSelectingSSLSocketFactory.ID_TARGETS;

/**
 * Fastest connection selecting SSL socket factory test program.
 */
@Slf4j
final class FastestConnectionSelectingSSLSocketFactoryIntegrationTest {

    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private static CloseableHttpClient client;

    private FastestConnectionSelectingSSLSocketFactoryIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        URI[] addresses = {
                new URI("https://localhost:8081"),
                new URI("https://localhost:8082"),
                new URI("https://localhost:8080")
        };

        logFH();

        testWithSender(addresses);
        //testFastestSocketSelector(addresses);
    }

    private static void testWithSender(URI[] addresses) throws Exception {
        createClient();

        InputStream content =
                new ByteArrayInputStream("Hello world".getBytes());
        for (int i = 0; i < 10; i++) {
            try (HttpSender sender = new HttpSender(client);) {
                sender.setAttribute(ID_TARGETS, addresses);
                sender.setTimeout(10);
                sender.doPost(new URI("https://localhost:1234"), content,
                        CHUNKED_LENGTH, "text/plain");
            } catch (Exception e) {
                log.trace("Ignoring HTTP sender error");
            }

            logFH();
        }
    }

    private static void testFastestSocketSelector(URI[] addresses)
            throws Exception {
        for (int i = 0; i < 10; i++) {
            FastestSocketSelector s = new FastestSocketSelector(addresses, 10);
            SocketInfo si = s.select();

            if (si != null) {
                si.getSocket().close();
            }

            logFH();
        }
    }

    private static void createClient() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create();

        socketFactoryRegistry.register("http",
                PlainConnectionSocketFactory.INSTANCE);

        socketFactoryRegistry.register("https", createSSLSocketFactory());

        PoolingHttpClientConnectionManager connMgr =
                new PoolingHttpClientConnectionManager(
                        socketFactoryRegistry.build());
        connMgr.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connMgr.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        int timeout = SystemProperties.getClientProxyTimeout();
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setStaleConnectionCheckEnabled(false);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setConnectionManager(connMgr);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        client = cb.build();
    }

    private static SSLConnectionSocketFactory createSSLSocketFactory()
            throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {AuthKeyManager.getInstance()},
                new TrustManager[] {new AuthTrustManager()},
                new SecureRandom());

        return new FastestConnectionSelectingSSLSocketFactory(ctx,
                        CryptoUtils.getINCLUDED_CIPHER_SUITES());
    }

    private static void logFH() {
        log.info("\nfree fd = {}\n",
                SystemMetrics.getFreeFileDescriptorCount());
    }
}
