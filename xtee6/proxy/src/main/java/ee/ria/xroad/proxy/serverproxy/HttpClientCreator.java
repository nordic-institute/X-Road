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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.security.SecureRandom;

/**
 * This class creates Apache {@link CloseableHttpClient}s with common security settings for use by both
 * {@link ServerProxy} and the Metadata Service.
 */
@Slf4j
public class HttpClientCreator {

    // Configuration parameters.
    // TODO #2576 Make configurable in the future
    private static final int CLIENT_TIMEOUT = 30000; // 30 sec.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    /**
     * A custom exception to use with the {@link HttpClientCreator} class, replacing throwing pure {@link Exception}s.
     */
    public static class HttpClientCreatorException extends Exception {
        public HttpClientCreatorException() {
        }

        public HttpClientCreatorException(String message) {
            super(message);
        }

        public HttpClientCreatorException(String message, Throwable cause) {
            super(message, cause);
        }

        public HttpClientCreatorException(Throwable cause) {
            super(cause);
        }

        public HttpClientCreatorException(String message, Throwable cause,
                                          boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }


    private PoolingHttpClientConnectionManager connectionManager;

    private CloseableHttpClient httpClient;

    /**
     * @return the {@link PoolingHttpClientConnectionManager}. It is lazily created if it does not exist yet.
     * @throws HttpClientCreatorException if creation fails.
     */
    public PoolingHttpClientConnectionManager getConnectionManager() throws HttpClientCreatorException {
        if (connectionManager == null) {
            build();
        }
        return connectionManager;
    }

    /**
     * @return the {@link CloseableHttpClient}. It is lazily created if it does not exist yet.
     * @throws HttpClientCreatorException if creation fails.
     */
    public CloseableHttpClient getHttpClient() throws HttpClientCreatorException {
        if (httpClient == null) {
            build();
        }
        return httpClient;
    }

    private void build() throws HttpClientCreatorException {

        RegistryBuilder<ConnectionSocketFactory> sfr =
                RegistryBuilder.create();
        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);
        try {
            sfr.register("https", createSSLSocketFactory());
        } catch (Exception e) {
            throw new HttpClientCreatorException("Creating SSL Socket Factory failed", e);
        }

        connectionManager =
                new PoolingHttpClientConnectionManager(sfr.build());
        connectionManager.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);
        connectionManager.setDefaultSocketConfig(
                SocketConfig.custom().setTcpNoDelay(true).build());

        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(CLIENT_TIMEOUT);
        rb.setConnectionRequestTimeout(CLIENT_TIMEOUT);
        rb.setStaleConnectionCheckEnabled(false);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setDefaultRequestConfig(rb.build());
        cb.setConnectionManager(connectionManager);

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));


        httpClient = cb.build();

    }

    private static SSLConnectionSocketFactory createSSLSocketFactory()
            throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(createServiceKeyManager(),
                new TrustManager[]{new ServiceTrustManager()},
                new SecureRandom());

        log.info("SSL context successfully created");

        return new CustomSSLSocketFactory(ctx,
                SystemProperties.getProxyClientTLSProtocols(),
                SystemProperties.getProxyClientTLSCipherSuites(),
                NoopHostnameVerifier.INSTANCE);
    }

    private static KeyManager[] createServiceKeyManager() throws Exception {
        InternalSSLKey key = ServerConf.getSSLKey();
        if (key != null) {
            return new KeyManager[]{new ServiceKeyManager(key)};
        }

        return null;
    }

}
