/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.managementrequest;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.StartStop;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
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

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import java.net.Socket;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Client that sends managements requests to the Central Server.
 */
@Slf4j
public final class ManagementRequestClient implements StartStop {

    // HttpClient configuration parameters.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 100;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 25;

    private final GlobalConfProvider globalConfProvider;

    private CloseableHttpClient centralHttpClient;
    private CloseableHttpClient proxyHttpClient;


    HttpSender createCentralHttpSender() {
        return createSender(centralHttpClient);
    }

    HttpSender createProxyHttpSender() {
        return createSender(proxyHttpClient);
    }

    private static HttpSender createSender(CloseableHttpClient client) {
        HttpSender httpSender = new HttpSender(client);

        int timeout = SystemProperties.getClientProxyTimeout();
        int socketTimeout = SystemProperties.getClientProxyHttpClientTimeout();

        httpSender.setConnectionTimeout(timeout);
        httpSender.setSocketTimeout(socketTimeout);

        return httpSender;
    }

    ManagementRequestClient(GlobalConfProvider globalConfProvider) {
        this.globalConfProvider = globalConfProvider;
        try {
            createCentralHttpClient();
            createProxyHttpClient();
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize management request client", e);
        }
    }

    @Override
    public void start() throws Exception {
        log.info("Starting ManagementRequestClient...");
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping ManagementRequestClient...");

        IOUtils.closeQuietly(proxyHttpClient);
        IOUtils.closeQuietly(centralHttpClient);
    }

    @Override
    public void join() throws InterruptedException {
        // Not applicable
    }

    // -- Helper methods ------------------------------------------------------

    @SuppressWarnings("java:S4830")
    private void createCentralHttpClient() throws Exception {
        log.trace("createCentralHttpClient()");

        TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // As manager of the client the method gets never called
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                if (chain.length == 0) {
                    throw new CertificateException("Central server did not send TLS certificate");
                }

                X509Certificate centralServerSslCert = null;

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

    private void createProxyHttpClient() throws Exception {
        log.trace("createProxyHttpClient()");

        String keyStore = SystemProperties.getManagementRequestSenderClientKeystore();
        String trustStore = SystemProperties.getManagementRequestSenderClientTruststore();

        if (StringUtils.isAllEmpty(keyStore, trustStore)) {
            proxyHttpClient = createProxyHttpClientWithInternalKey();
        } else {
            proxyHttpClient = createProxyHttpClient(keyStore, SystemProperties.getManagementRequestSenderClientKeystorePassword(),
                    trustStore, SystemProperties.getManagementRequestSenderClientTruststorePassword());
        }
    }

    @SuppressWarnings("java:S4830") // Won't fix: Works as designed ("Server certificates should be verified")
    private CloseableHttpClient createProxyHttpClientWithInternalKey() throws Exception {
        TrustManager tm = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // never called as this is trustmanager of a client
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                // localhost called so server is trusted
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        };

        KeyManager km = new X509ExtendedKeyManager() {

            private static final String ALIAS = "MgmtAuthKeyManager";

            @Override
            public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
                return ALIAS;
            }

            @Override
            public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
                return ALIAS;
            }

            @Override
            public X509Certificate[] getCertificateChain(String alias) {
                try {
                    return InternalSSLKey.load().getCertChain();
                } catch (Exception e) {
                    log.error("Failed to load internal TLS key", e);
                    return new X509Certificate[]{};
                }
            }

            @Override
            public String[] getClientAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public PrivateKey getPrivateKey(String alias) {
                try {
                    return InternalSSLKey.load().getKey();
                } catch (Exception e) {
                    log.error("Failed to load internal TLS key", e);

                    return null;
                }
            }

            @Override
            public String[] getServerAliases(String keyType, Principal[] issuers) {
                return null;
            }

            @Override
            public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
                return ALIAS;
            }

            @Override
            public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
                return ALIAS;
            }
        };

        return createHttpClient(new KeyManager[]{km}, new TrustManager[]{tm});
    }

    private CloseableHttpClient createProxyHttpClient(String keyStorePath, char[] keyStorePassword, String trustStorePath,
                                                      char[] trustStorePassword) throws Exception {

        Objects.requireNonNull(keyStorePath, "Management request client key store path is not provided.");
        Objects.requireNonNull(trustStorePath, "Management request client trust store path is not provided.");

        KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(Paths.get(keyStorePath).toFile(), keyStorePassword);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword);

        KeyStore trustStore = CryptoUtils.loadPkcs12KeyStore(Paths.get(trustStorePath).toFile(), trustStorePassword);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        return createHttpClient(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers());
    }

    private static CloseableHttpClient createHttpClient(KeyManager[] keyManagers, TrustManager[] trustManagers) throws Exception {
        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.<ConnectionSocketFactory>create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(keyManagers, trustManagers, new SecureRandom());

        SSLConnectionSocketFactory sf = new SSLConnectionSocketFactory(ctx,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

        sfr.register("https", sf);

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(sfr.build());
        cm.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        cm.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        int timeout = SystemProperties.getClientProxyTimeout();
        int socketTimeout = SystemProperties.getClientProxyHttpClientTimeout();

        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setSocketTimeout(socketTimeout);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setConnectionManager(cm);
        cb.setDefaultRequestConfig(rb.build());

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        return cb.build();
    }
}
