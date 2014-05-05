package ee.cyber.sdsb.common;

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

import ee.cyber.sdsb.common.util.CryptoUtils;

public class HttpUtils {

    private static final Logger LOG = LoggerFactory.getLogger(HttpUtils.class);

    private static final int CLIENT_TIMEOUT = 300000; // milliseconds.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    public static CloseableHttpAsyncClient getDefaultHttpClient() {
        return getHttpClient(true);
    }

    /**
     * Returns HTTP client with no special thread pool configuration.
     *
     * XXX In testclient web application we need to do it like this in order to
     * be able to do more than one consecutive requests.
     *
     * @return
     */
    public static CloseableHttpAsyncClient getHttpClientWithDefaultThreadPool() {
        return getHttpClient(false);
    }

    public static CloseableHttpAsyncClient getHttpClient(
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

    public static CloseableHttpAsyncClient getHttpClient(
            X509ExtendedKeyManager clientKeyManager) throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] { clientKeyManager },
                new TrustManager[] { new ClientTrustManager() },
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
                .setSoTimeout(30000)
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
