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
package org.niis.xroad.opmonitor.api;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.experimental.UtilityClass;
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
import org.niis.xroad.common.rpc.VaultKeyProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.security.SecureRandom;

/**
 * Operational monitoring daemon HTTP client.
 */
@Slf4j
@ApplicationScoped
@UtilityClass
public final class OpMonitoringDaemonHttpClient {

    // HttpClient configuration parameters.
    private static final int DEFAULT_CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int DEFAULT_CLIENT_MAX_CONNECTIONS_PER_ROUTE = 10000;

    /**
     * Creates HTTP client.
     *
     * @param vaultKeyProvider keys & trust provider for TLS
     * @return HTTP client
     * @throws Exception if creating a HTTPS client and SSLContext initialization fails
     */
    public static CloseableHttpClient createHttpClient(VaultKeyProvider vaultKeyProvider,
                                                       OpMonitorCommonProperties opMonitorCommonProperties) throws Exception {
        int connectionTimeoutMilliseconds = TimeUtils.secondsToMillis(opMonitorCommonProperties.service().connectionTimeoutSeconds());
        int socketTimeoutMilliseconds = TimeUtils.secondsToMillis(opMonitorCommonProperties.service().socketTimeoutSeconds());

        return createHttpClient(vaultKeyProvider, opMonitorCommonProperties, DEFAULT_CLIENT_MAX_TOTAL_CONNECTIONS,
                DEFAULT_CLIENT_MAX_CONNECTIONS_PER_ROUTE,
                connectionTimeoutMilliseconds, socketTimeoutMilliseconds);
    }

    /**
     * Creates HTTP client.
     *
     * @param vaultKeyProvider keys & trust provider for TLS
     * @param clientMaxTotalConnections     client max total connections
     * @param clientMaxConnectionsPerRoute  client max connections per route
     * @param connectionTimeoutMilliseconds connection timeout in milliseconds
     * @param socketTimeoutMilliseconds     socket timeout in milliseconds
     * @return HTTP client
     * @throws Exception if creating a HTTPS client and SSLContext
     *                   initialization fails
     */
    public static CloseableHttpClient createHttpClient(VaultKeyProvider vaultKeyProvider,
                                                       OpMonitorCommonProperties opMonitorCommonProperties,
                                                       int clientMaxTotalConnections, int clientMaxConnectionsPerRoute,
                                                       int connectionTimeoutMilliseconds, int socketTimeoutMilliseconds) throws Exception {
        log.trace("createHttpClient()");

        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.create();

        if ("https".equalsIgnoreCase(opMonitorCommonProperties.connection().scheme())) {
            sfr.register("https", createSSLSocketFactory(vaultKeyProvider));
        } else {
            sfr.register("http", PlainConnectionSocketFactory.INSTANCE);
        }

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(sfr.build());
        cm.setMaxTotal(clientMaxTotalConnections);
        cm.setDefaultMaxPerRoute(clientMaxConnectionsPerRoute);
        cm.setDefaultSocketConfig(SocketConfig.custom().setTcpNoDelay(true).build());

        RequestConfig.Builder rb = RequestConfig.custom()
                .setConnectTimeout(connectionTimeoutMilliseconds)
                .setConnectionRequestTimeout(connectionTimeoutMilliseconds)
                .setSocketTimeout(socketTimeoutMilliseconds);

        HttpClientBuilder cb = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        return cb.build();
    }

    private static SSLConnectionSocketFactory createSSLSocketFactory(VaultKeyProvider vaultKeyProvider) throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(
                new KeyManager[]{vaultKeyProvider.getKeyManager()},
                new TrustManager[]{vaultKeyProvider.getTrustManager()},
                new SecureRandom());

        return new SSLConnectionSocketFactory(ctx.getSocketFactory(), new String[]{CryptoUtils.SSL_PROTOCOL},
                SystemProperties.getXroadTLSCipherSuites(), NoopHostnameVerifier.INSTANCE);
        // We don't need hostname verification
    }

}
