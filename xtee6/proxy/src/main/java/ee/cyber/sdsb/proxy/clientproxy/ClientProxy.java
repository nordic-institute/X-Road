package ee.cyber.sdsb.proxy.clientproxy;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;
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
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import ch.qos.logback.access.jetty.RequestLogImpl;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.AuthTrustManager;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.proxy.antidos.AntiDosConnector;
import ee.cyber.sdsb.proxy.conf.AuthKeyManager;

import static ee.cyber.sdsb.proxy.clientproxy.HandlerLoader.loadHandler;

/**
 * Client proxy that handles requests of service clients.
 */
@Slf4j
public class ClientProxy implements StartStop {

    private static final String CLIENTPROXY_HANDLERS =
            SystemProperties.PREFIX + "proxy.clientHandlers";

    // Configuration parameters.
    // TODO: #2576 Make configurable in the future
    private static final int SERVER_THREAD_POOL_SIZE = 5000;
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

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
        RegistryBuilder<ConnectionSocketFactory> sfr =
                RegistryBuilder.<ConnectionSocketFactory>create();

        sfr.register("http", PlainConnectionSocketFactory.INSTANCE);

        if (SystemProperties.isSslEnabled()) {
            sfr.register("https", createSSLSocketFactory());
        }

        PoolingHttpClientConnectionManager cm =
                new PoolingHttpClientConnectionManager(sfr.build());
        cm.setMaxTotal(CLIENT_MAX_TOTAL_CONNECTIONS);
        cm.setDefaultMaxPerRoute(CLIENT_MAX_CONNECTIONS_PER_ROUTE);

        int timeout = SystemProperties.getClientProxyTimeout();
        RequestConfig.Builder rb = RequestConfig.custom();
        rb.setConnectTimeout(timeout);
        rb.setConnectionRequestTimeout(timeout);
        rb.setStaleConnectionCheckEnabled(false);

        HttpClientBuilder cb = HttpClients.custom();
        cb.setConnectionManager(cm);
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

        log.info("SSL context successfully created");

        return new FastestConnectionSelectingSSLSocketFactory(ctx,
                CryptoUtils.INCLUDED_CIPHER_SUITES);
    }

    private void createConnectors() {
        createClientConnector(SystemProperties.getConnectorHost(),
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

        log.debug("Client HTTP connector created ({}:{})", hostname, port);
    }

    private void createHandlers() throws Exception {
        RequestLogImpl reqLog = new RequestLogImpl();
        reqLog.setResource("/logback-access-clientproxy.xml");
        reqLog.setQuiet(true);

        RequestLogHandler logHandler = new RequestLogHandler();
        logHandler.setRequestLog(reqLog);

        HandlerCollection handlers = new HandlerCollection();

        handlers.addHandler(logHandler);

        for (Handler handler : getClientHandlers()) {
            handlers.addHandler(handler);
        }

        server.setHandler(handlers);
    }

    private List<Handler> getClientHandlers() {
        List<Handler> handlers = new ArrayList<>();

        String handlerClassNames = System.getProperty(CLIENTPROXY_HANDLERS);
        if (!StringUtils.isBlank(handlerClassNames)) {
            for (String handlerClassName : handlerClassNames.split(",")) {
                try {
                    log.debug("Loading client handler {}", handlerClassName);
                    handlers.add(loadHandler(handlerClassName, client));
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to load client handler: "
                                    + handlerClassName, e);
                }
            }
        }

        log.debug("Loading default client handler");
        handlers.add(new ClientMessageHandler(client)); // default handler
        return handlers;
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

        HibernateUtil.closeSessionFactories();
    }

}
