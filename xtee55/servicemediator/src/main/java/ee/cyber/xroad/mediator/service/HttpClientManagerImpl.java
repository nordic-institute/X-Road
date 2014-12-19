package ee.cyber.xroad.mediator.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
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
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.conf.InternalSSLKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.HttpClientManager;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;

class HttpClientManagerImpl implements HttpClientManager {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpClientManagerImpl.class);

    // TODO: Fine-tune connection parameters
    private static final int CLIENT_TIMEOUT = 300000; // in milliseconds.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 100;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 50;

    private final ServiceMediatorKeyManager keyManager;

    private CloseableHttpAsyncClient defaultClient;
    private IdleConnectionMonitorThread connMonitor;

    HttpClientManagerImpl() {
        try {
            InternalSSLKey key = MediatorServerConf.getSSLKey();
            this.keyManager =
                    key != null ? new ServiceMediatorKeyManager(key) : null;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public CloseableHttpAsyncClient getDefaultHttpClient() {
        if (defaultClient != null) {
            return defaultClient;
        }

        try {
            defaultClient = initHttpClient();
            defaultClient.start();
            connMonitor.start();

            return defaultClient;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public synchronized CloseableHttpAsyncClient getHttpClient(
            ClientId client) {
        return getDefaultHttpClient();
    }

    @Override
    public synchronized void shutdown() {
        LOG.trace("shutdown()");

        if (connMonitor != null) {
            try {
                connMonitor.shutdown();
            } catch (Exception ignored) {
            }
        }

        if (defaultClient != null) {
            try {
                defaultClient.close();
            } catch (IOException e) {
                throw translateException(e);
            }
        }
    }

    private CloseableHttpAsyncClient initHttpClient() throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(keyManager != null ? new KeyManager[] { keyManager } : null,
                new TrustManager[] { new DummyTrustManager() },
                new SecureRandom());

        HttpAsyncClientBuilder builder = HttpAsyncClients.custom();

        Registry<SchemeIOSessionStrategy> sessionStrategyRegistry =
                RegistryBuilder.<SchemeIOSessionStrategy>create()
                    .register("http", NoopIOSessionStrategy.INSTANCE)
                    .register("https", new CustomSSLIOSessionStrategy(ctx,
                            new AllowAllHostnameVerifier()))
                    .build();

        return buildHttpClient(builder, sessionStrategyRegistry);
    }

    private CloseableHttpAsyncClient buildHttpClient(
            HttpAsyncClientBuilder builder,
            Registry<SchemeIOSessionStrategy> sessionStrategyRegistry)
                    throws Exception {
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(CLIENT_TIMEOUT)
                .setSoTimeout(CLIENT_TIMEOUT)
                .build();
        ConnectingIOReactor ioReactor =
                new DefaultConnectingIOReactor(ioReactorConfig);

        PoolingNHttpClientConnectionManager connManager =
                new PoolingNHttpClientConnectionManager(ioReactor,
                        sessionStrategyRegistry);

        connManager.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        connManager.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        builder.setConnectionManager(connManager);

        RequestConfig requestConfig =
                RequestConfig.custom().setCookieSpec(
                        CookieSpecs.IGNORE_COOKIES).build();
        builder.setDefaultRequestConfig(requestConfig);

        connMonitor = new IdleConnectionMonitorThread(connManager);
        connMonitor.setIntervalMilliseconds(100);
        connMonitor.setConnectionIdleTimeMilliseconds(50);

        return builder.build();
    }

    class DummyTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
