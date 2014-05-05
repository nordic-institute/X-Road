package ee.cyber.sdsb.proxy.clientproxy;

import java.io.IOException;
import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;

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
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.access.jetty.RequestLogImpl;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AuthTrustManager;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.proxy.antidos.AntiDosConnector;
import ee.cyber.sdsb.proxy.conf.AuthKeyManager;
import ee.cyber.sdsb.proxy.conf.ServerConf;

/**
 * Client proxy that handles requests of service clients.
 */
public class ClientProxy implements StartStop {

    // Configuration parameters.
    // TODO: Make configurable
    private static final int SERVER_THREAD_POOL_SIZE = 5000;
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientProxy.class);

    private static final boolean USE_ANTIDOS = true;

    static final String CLIENT_CONNECTOR_NAME = "ClientConnector";
    static final String CLIENT_SSL_CONNECTOR_NAME = "ClientSSLConnector";

    private Server server = new Server();

    private CloseableHttpClient client;

    public ClientProxy() throws Exception {
        configureServer();

        createClient();
        createConnectors();
        createHandlers();
    }

    private void configureServer() {
        server.setThreadPool(new QueuedThreadPool(SERVER_THREAD_POOL_SIZE));
    }

    private void createClient() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistry =
                RegistryBuilder.<ConnectionSocketFactory>create();

        socketFactoryRegistry.register("http",
                PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            socketFactoryRegistry.register("https", createSSLSocketFactory());
        }

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
        ctx.init(new KeyManager[] { AuthKeyManager.getInstance() },
                new TrustManager[] { AuthTrustManager.getInstance() },
                new SecureRandom());

        SSLConnectionSocketFactory socketFactory =
                new FastestConnectionSelectingSSLSocketFactory(ctx) {
            @Override
            protected void prepareSocket(SSLSocket s) throws IOException {
                s.setEnabledCipherSuites(CryptoUtils.INCLUDED_CIPHER_SUITES);
            }
        };

        LOG.info("SSL context successfully created");
        return socketFactory;
    }

    private void createConnectors() {
        createClientConnector(ServerConf.getConnectorHost(),
                SystemProperties.getClientProxyHttpPort());
    }

    private void createClientConnector(String hostname, int port) {
        SelectChannelConnector connector =
                USE_ANTIDOS ? new AntiDosConnector()
                            : new SelectChannelConnector();

        connector.setName(CLIENT_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(2 * Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        LOG.debug("Client HTTP connector created ({}:{})", hostname, port);
    }

    private void createHandlers() {
        RequestLogHandler logHandler = new RequestLogHandler();
        RequestLogImpl reqLog = new RequestLogImpl();

        reqLog.setResource("/logback-access-clientproxy.xml");
        reqLog.setQuiet(true);
        logHandler.setRequestLog(reqLog);

        ClientProxyHandler proxyHandler = new ClientProxyHandler(client);

        HandlerCollection handler = new HandlerCollection();

        handler.addHandler(logHandler);
        handler.addHandler(proxyHandler);

        server.setHandler(handler);
    }

    @Override
    public void start() throws Exception {
        server.start();
    }

    @Override
    public void join() throws InterruptedException {
        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    @Override
    public void stop() throws Exception {
        client.close();
        server.stop();
    }

}
