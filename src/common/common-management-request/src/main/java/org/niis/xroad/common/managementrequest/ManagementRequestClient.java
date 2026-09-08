/*
 * The MIT License
 *
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

package org.niis.xroad.common.managementrequest;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Client that sends managements requests to the Central Server.
 */
@Slf4j
public final class ManagementRequestClient implements InitializingBean, DisposableBean {

    // HttpClient configuration parameters.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 100;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 25;

    private final VaultKeyProvider vaultKeyProvider;
    private final GlobalConfProvider globalConfProvider;
    private final int connectTimeout;
    private final int socketTimeout;
    private final boolean isEnabledPooledConnectionReuse;

    private CloseableHttpClient centralHttpClient;
    private CloseableHttpClient proxyHttpClient;


    HttpSender createCentralHttpSender() {
        return createSender(centralHttpClient);
    }

    HttpSender createProxyHttpSender() {
        return createSender(proxyHttpClient);
    }

    private HttpSender createSender(CloseableHttpClient client) {
        HttpSender httpSender = new HttpSender(client, isEnabledPooledConnectionReuse);

        httpSender.setConnectionTimeout(connectTimeout);
        httpSender.setSocketTimeout(socketTimeout);

        return httpSender;
    }

    ManagementRequestClient(VaultKeyProvider vaultKeyProvider, GlobalConfProvider globalConfProvider,
                            int connectTimeout, int socketTimeout, boolean isEnabledPooledConnectionReuse) {
        this.vaultKeyProvider = vaultKeyProvider;
        this.globalConfProvider = globalConfProvider;
        this.connectTimeout = connectTimeout;
        this.socketTimeout = socketTimeout;
        this.isEnabledPooledConnectionReuse = isEnabledPooledConnectionReuse;
        try {
            createCentralHttpClient();
            createProxyHttpClient();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR)
                    .cause(e)
                    .details("Unable to initialize management request client")
                    .build();
        }
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Starting ManagementRequestClient...");
    }

    @Override
    public void destroy() {
        log.info("Stopping ManagementRequestClient...");

        IOUtils.closeQuietly(proxyHttpClient);
        IOUtils.closeQuietly(centralHttpClient);
    }

    // -- Helper methods ------------------------------------------------------

    @SuppressWarnings("java:S4830")
    private void createCentralHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        log.trace("createCentralHttpClient()");

        TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
                // As manager of the client the method gets never called
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain.length == 0) {
                    throw new CertificateException("Central server did not send TLS certificate");
                }

                X509Certificate centralServerSslCert;

                try {
                    centralServerSslCert = globalConfProvider.getCentralServerSslCertificate();
                } catch (Exception e) {
                    throw new CertificateException("Could not get central server TLS certificate from global conf", e);
                }

                if (centralServerSslCert == null) {
                    throw new CertificateException("Central server TLS certificate is not in global conf");
                }

                if (!centralServerSslCert.equals(chain[0])) {
                    throw new CertificateException("Central server TLS certificate does not match in global conf");
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        centralHttpClient = createHttpClient(null, new TrustManager[]{tm});
    }

    private void createProxyHttpClient() throws NoSuchAlgorithmException, KeyManagementException {
        log.trace("createProxyHttpClient()");

        proxyHttpClient = createHttpClient(
                new KeyManager[]{vaultKeyProvider.getKeyManager()},
                new TrustManager[]{new NoopTrustManager()}
        );
    }

    private CloseableHttpClient createHttpClient(KeyManager[] keyManagers, TrustManager[] trustManagers)
            throws NoSuchAlgorithmException, KeyManagementException {
        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.<ConnectionSocketFactory>create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(keyManagers, trustManagers, new SecureRandom());

        SSLConnectionSocketFactory sf = new SSLConnectionSocketFactory(ctx, NoopHostnameVerifier.INSTANCE);

        sfr.register("https", sf);

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(sfr.build());
        cm.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        cm.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(connectTimeout);
        rb.setConnectionRequestTimeout(connectTimeout);
        rb.setSocketTimeout(socketTimeout);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setConnectionManager(cm);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        return cb.build();
    }

    @SuppressWarnings("java:S4830") // Won't fix: Works as designed
    private static final class NoopTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // never called as this is trustmanager of a client
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // ClientProxy of same instance is called so server is trusted
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }
}
