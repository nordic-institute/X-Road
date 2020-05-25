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
package ee.ria.xroad.common.opmonitoring;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
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
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Operational monitoring daemon HTTP client.
 */
@Slf4j
public final class OpMonitoringDaemonHttpClient {

    // HttpClient configuration parameters.
    private static final int DEFAULT_CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int DEFAULT_CLIENT_MAX_CONNECTIONS_PER_ROUTE = 10000;

    private OpMonitoringDaemonHttpClient() {
    }

    /**
     * Creates HTTP client.
     * @param authKey the client's authentication key
     * @param connectionTimeoutMilliseconds connection timeout in milliseconds
     * @param socketTimeoutMilliseconds socket timeout in milliseconds
     * @return HTTP client
     * @throws Exception if creating a HTTPS client and SSLContext initialization fails
     */
    public static CloseableHttpClient createHttpClient(InternalSSLKey authKey,
            int connectionTimeoutMilliseconds, int socketTimeoutMilliseconds) throws Exception {
        return createHttpClient(authKey, DEFAULT_CLIENT_MAX_TOTAL_CONNECTIONS, DEFAULT_CLIENT_MAX_CONNECTIONS_PER_ROUTE,
                connectionTimeoutMilliseconds, socketTimeoutMilliseconds);
    }

    /**
     * Creates HTTP client.
     * @param authKey the client's authentication key
     * @param clientMaxTotalConnections client max total connections
     * @param clientMaxConnectionsPerRoute client max connections per route
     * @param connectionTimeoutMilliseconds connection timeout in milliseconds
     * @param socketTimeoutMilliseconds socket timeout in milliseconds
     * @return HTTP client
     * @throws Exception if creating a HTTPS client and SSLContext
     * initialization fails
     */
    public static CloseableHttpClient createHttpClient(InternalSSLKey authKey,
            int clientMaxTotalConnections, int clientMaxConnectionsPerRoute,
            int connectionTimeoutMilliseconds, int socketTimeoutMilliseconds) throws Exception {
        log.trace("createHttpClient()");

        RegistryBuilder<ConnectionSocketFactory> sfr = RegistryBuilder.<ConnectionSocketFactory>create();

        if ("https".equalsIgnoreCase(OpMonitoringSystemProperties.getOpMonitorDaemonScheme())) {
            sfr.register("https", createSSLSocketFactory(authKey));
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

    private static SSLConnectionSocketFactory createSSLSocketFactory(InternalSSLKey authKey) throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(getKeyManager(authKey), new TrustManager[] {new OpMonitorTrustManager()}, new SecureRandom());

        return new SSLConnectionSocketFactory(ctx.getSocketFactory(), new String[] {CryptoUtils.SSL_PROTOCOL},
                SystemProperties.getXroadTLSCipherSuites(), NoopHostnameVerifier.INSTANCE);
        // We don't need hostname verification
    }

    private static KeyManager[] getKeyManager(InternalSSLKey authKey) {
        if (authKey == null) {
            log.error("No internal TLS key required by operational monitoring daemon HTTP client");

            return null;
        }

        return new KeyManager[] {new OpMonitorClientKeyManager(authKey)};
    }

    private static final class OpMonitorTrustManager implements X509TrustManager {
        private X509Certificate opMonitorCert = null;

        private OpMonitorTrustManager() {
            String monitorCertPath = OpMonitoringSystemProperties.getOpMonitorCertificatePath();

            try (InputStream monitorCertStream = new FileInputStream(monitorCertPath)) {
                opMonitorCert = CryptoUtils.readCertificate(monitorCertStream);
            } catch (Exception e) {
                log.error("Could not load operational monitoring daemon certificate '{}'", monitorCertPath, e);
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            // As private manager of the client the method gets never called
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain.length == 0) {
                throw new CertificateException("Server did not send TLS certificate");
            }

            log.trace("Received server certificate {}", chain[0]);

            if (opMonitorCert == null) {
                throw new CertificateException(
                        "Operational monitoring daemon certificate not loaded, cannot verify server");
            }

            if (!chain[0].equals(opMonitorCert)) {
                throw new CertificateException(
                        "Server TLS certificate does not match expected operational monitoring daemon certificate");
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    @RequiredArgsConstructor
    private static final class OpMonitorClientKeyManager extends X509ExtendedKeyManager {
        private static final String ALIAS = "OpMonitorClientKeyManager";

        private final InternalSSLKey authKey;

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
            return authKey.getCertChain();
        }

        @Override
        public String[] getClientAliases(String keyType, Principal[] issuers) {
            return null;
        }

        @Override
        public PrivateKey getPrivateKey(String alias) {
            return authKey.getKey();
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
    }
}
