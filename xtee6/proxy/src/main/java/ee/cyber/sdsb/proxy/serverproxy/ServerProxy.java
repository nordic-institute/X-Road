package ee.cyber.sdsb.proxy.serverproxy;

import java.security.SecureRandom;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
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
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import ch.qos.logback.access.jetty.RequestLogImpl;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AuthTrustManager;
import ee.cyber.sdsb.common.conf.serverconf.InternalSSLKey;
import ee.cyber.sdsb.common.conf.serverconf.ServerConf;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.proxy.antidos.AntiDosConnector;
import ee.cyber.sdsb.proxy.antidos.AntiDosSslConnector;
import ee.cyber.sdsb.proxy.conf.AuthKeyManager;

/**
 * Server proxy that handles requests of client proxies.
 */
@Slf4j
public class ServerProxy implements StartStop {

    // Configuration parameters.
    // TODO: #2576 Make configurable in the future
    private static final int SERVER_THREAD_POOL_SIZE = 5000;
    private static final int CLIENT_TIMEOUT = 300000; // 30 sec.
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    private static final boolean USE_ANTIDOS = true;

    static final String CLIENT_PROXY_CONNECTOR_NAME = "ClientProxyConnector";

    private Server server = new Server();

    private CloseableHttpClient client;
    private IdleConnectionMonitorThread connMonitor;

    private String listenAddress;

    public ServerProxy() throws Exception {
        this(SystemProperties.getServerProxyListenAddress());
    }

    public ServerProxy(String listenAddress) throws Exception {
        this.listenAddress = listenAddress;

        configureServer();

        createClient();
        createConnectors();
        createHandlers();
    }

    private void configureServer() {
        server.setThreadPool(new QueuedThreadPool(SERVER_THREAD_POOL_SIZE));
    }

    private void createClient() throws Exception {
        RegistryBuilder<ConnectionSocketFactory> sfr =
                RegistryBuilder.<ConnectionSocketFactory>create();
        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);
        sfr.register("https", createSSLSocketFactory());

        PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager(sfr.build());
        cm.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        cm.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);
        cm.setDefaultSocketConfig(
                SocketConfig.custom().setTcpNoDelay(true).build());

        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(CLIENT_TIMEOUT);
        rb.setConnectionRequestTimeout(CLIENT_TIMEOUT);
        rb.setStaleConnectionCheckEnabled(false);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setDefaultRequestConfig(rb.build());
        cb.setConnectionManager(cm);

        // Disable request retry
        cb.setRetryHandler(new DefaultHttpRequestRetryHandler(0, false));

        connMonitor = new IdleConnectionMonitorThread(cm);
        connMonitor.setIntervalMilliseconds(100);
        connMonitor.setConnectionIdleTimeMilliseconds(50);

        client = cb.build();
    }

    private static SSLConnectionSocketFactory createSSLSocketFactory()
            throws Exception {
        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(createServiceKeyManager(),
                new TrustManager[] { new ServiceTrustManager() },
                new SecureRandom());

        log.info("SSL context successfully created");

        return new CustomSSLSocketFactory(ctx,
                CryptoUtils.INCLUDED_CIPHER_SUITES,
                SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
    }

    private void createConnectors() throws Exception {
        int port = SystemProperties.getServerProxyPort();

        SelectChannelConnector connector = SystemProperties.isSslEnabled()
                ? createClientProxySslConnector()
                : createClientProxyConnector();

        connector.setName(CLIENT_PROXY_CONNECTOR_NAME);
        connector.setPort(port);
        connector.setHost(listenAddress);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(2 * Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        log.info("ClientProxy {} created ({}:{})",
                new Object[] { connector.getClass().getSimpleName(),
                    listenAddress, port });
    }

    private void createHandlers() {
        RequestLogHandler logHandler = new RequestLogHandler();
        RequestLogImpl reqLog = new RequestLogImpl();
        reqLog.setResource("/logback-access-serverproxy.xml");
        reqLog.setQuiet(true);
        logHandler.setRequestLog(reqLog);

        ServerProxyHandler proxyHandler = new ServerProxyHandler(client);

        HandlerCollection handler = new HandlerCollection();
        handler.addHandler(logHandler);
        handler.addHandler(proxyHandler);

        server.setHandler(handler);
    }

    @Override
    public void start() throws Exception {
        server.start();
        connMonitor.start();
    }

    @Override
    public void join() throws InterruptedException {
        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    @Override
    public void stop() throws Exception {
        connMonitor.shutdown();
        client.close();
        server.stop();

        HibernateUtil.closeSessionFactories();
    }

    public void closeIdleConnections() {
        connMonitor.closeNow();
    }

    private static SelectChannelConnector createClientProxyConnector() {
        return USE_ANTIDOS ? new AntiDosConnector()
                           : new SelectChannelConnector();
    }

    private static SslSelectChannelConnector createClientProxySslConnector()
            throws Exception {
        SslContextFactory cf = new SslContextFactory(false);
        cf.setNeedClientAuth(true);

        cf.setIncludeCipherSuites(CryptoUtils.INCLUDED_CIPHER_SUITES);
        cf.setSessionCachingEnabled(true);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] { AuthKeyManager.getInstance() },
                new TrustManager[] { AuthTrustManager.getInstance() },
                new SecureRandom());

        cf.setSslContext(ctx);

        return USE_ANTIDOS ? new AntiDosSslConnector(cf)
                           : new SslSelectChannelConnector(cf);
    }

    private static KeyManager[] createServiceKeyManager() throws Exception {
        InternalSSLKey key = ServerConf.getSSLKey();
        if (key != null) {
            return new KeyManager[] { new ServiceKeyManager(key) };
        }

        return null;
    }
}
