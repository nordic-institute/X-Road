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
package ee.ria.xroad.common;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.util.CryptoUtils;

/**
 * Contains utility methods for creating test HTTP clients.
 */
public final class HttpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    private static final int CLIENT_TIMEOUT = 3000000; // milliseconds.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private HttpUtils() {
    }

    /**
     * Returns HTTP client with a specially configured thread pool.
     * @return CloseableHttpAsyncClient
     */
    public static CloseableHttpAsyncClient getDefaultHttpClient() {
        return getHttpClient(true);
    }

    /**
     * Returns HTTP client with no special thread pool configuration.
     *
     * In testclient web application we need to do it like this in order to
     * be able to do more than one consecutive request.
     *
     * @return CloseableHttpAsyncClient
     */
    public static CloseableHttpAsyncClient getHttpClientWithDefaultThreadPool() {
        return getHttpClient(false);
    }

    static CloseableHttpAsyncClient getHttpClient(
            boolean configureThreadPool) {
        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();

        if (configureThreadPool) {
            try {
                configureConnectionManager(builder, null);
            } catch (IOReactorException e) {
                throw new RuntimeException(e);
            }
        }

        return builder.build();
    }

    /**
     * Returns HTTP client that uses the provided client key manager.
     * @param clientKeyManager the client key manager
     * @return CloseableHttpAsyncClient
     * @throws Exception in case of any errors
     */
    public static CloseableHttpAsyncClient getHttpClient(
            X509ExtendedKeyManager clientKeyManager) throws Exception {
        return getHttpClient(clientKeyManager, new ClientTrustManager());
    }

    /**
     * Returns HTTP client that uses the provided key manager and client trust manager.
     * @param clientKeyManager the client key manager
     * @param clientTrustManager the client trust manager
     * @return CloseableHttpAsyncClient
     * @throws Exception in case of any errors
     */
    public static CloseableHttpAsyncClient getHttpClient(
            X509ExtendedKeyManager clientKeyManager,
            X509TrustManager clientTrustManager) throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {clientKeyManager},
                new TrustManager[] {clientTrustManager},
                new SecureRandom());

        Registry<SchemeIOSessionStrategy> sessionStrategyRegistry =
                RegistryBuilder.<SchemeIOSessionStrategy>create()
                    .register("http", NoopIOSessionStrategy.INSTANCE)
                    .register("https", new SSLIOSessionStrategy(ctx,
                            new AllowAllHostnameVerifier()))
                    .build();

        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();
        configureConnectionManager(builder, sessionStrategyRegistry);

        return builder.build();
    }

    private static void configureConnectionManager(
            HttpAsyncClientBuilder builder,
            Registry<SchemeIOSessionStrategy> sessionStrategyRegistry)
                    throws IOReactorException {
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(CLIENT_TIMEOUT)
                .setSoTimeout(CLIENT_TIMEOUT)
                .build();

        ConnectingIOReactor ioReactor =
                new DefaultConnectingIOReactor(ioReactorConfig);

        PoolingNHttpClientConnectionManager connManager = null;
        if (sessionStrategyRegistry != null) {
            connManager = new PoolingNHttpClientConnectionManager(ioReactor,
                    sessionStrategyRegistry);
        } else {
            connManager = new PoolingNHttpClientConnectionManager(ioReactor);
        }

        connManager.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connManager.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        builder.setConnectionManager(connManager);
    }

    private static class ClientTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                throws CertificateException {
            LOG.debug("checkClientTrusted()");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                throws CertificateException {
            LOG.debug("checkServerTrusted()");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            LOG.debug("getAcceptedIssuers()");
            return new X509Certificate[] {};
        }

    }
}
