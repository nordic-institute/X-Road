package ee.cyber.xroad.mediator.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;
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

import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
class HttpClientManagerImpl implements HttpClientManager {

    private static final int IDLE_MONITOR_TIMEOUT = 50;
    private static final int IDLE_MONITOR_INTERVAL = 100;
    // TODO Fine-tune connection parameters
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
        log.trace("shutdown()");

        if (connMonitor != null) {
            try {
                connMonitor.shutdown();
            } catch (Exception ignored) {
                log.warn("Error shutting down connection monitor");
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
        ctx.init(keyManager != null ? new KeyManager[] {keyManager} : null,
                new TrustManager[] {new DummyTrustManager()},
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
        connMonitor.setIntervalMilliseconds(IDLE_MONITOR_INTERVAL);
        connMonitor.setConnectionIdleTimeMilliseconds(IDLE_MONITOR_TIMEOUT);

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
