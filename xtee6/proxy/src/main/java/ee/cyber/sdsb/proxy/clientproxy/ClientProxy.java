package ee.cyber.sdsb.proxy.clientproxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

import ch.qos.logback.access.jetty.RequestLogImpl;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.conf.globalconf.AuthTrustManager;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.proxy.conf.AuthKeyManager;

import static ee.cyber.sdsb.proxy.clientproxy.HandlerLoader.loadHandler;

/**
 * Client proxy that handles requests of service clients.
 */
@Slf4j
public class ClientProxy implements StartStop {

    // SSL session timeout
    private static final int SSL_SESSION_TIMEOUT = 600;

    private static final String CLIENTPROXY_HANDLERS =
            SystemProperties.PREFIX + "proxy.clientHandlers";

    // Configuration parameters.
    // TODO: #2576 Make configurable in the future
    private static final int CLIENT_MAX_TOTAL_CONNECTIONS = 10000;
    private static final int CLIENT_MAX_CONNECTIONS_PER_ROUTE = 2500;

    static final String CLIENT_HTTP_CONNECTOR_NAME = "ClientConnector";
    static final String CLIENT_HTTPS_CONNECTOR_NAME = "ClientSSLConnector";

    private Server server = new Server();

    private CloseableHttpClient client;

    public ClientProxy() throws Exception {
        configureServer();

        createClient();
        createConnectors();
        createHandlers();
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        File configFile = new File(
                SystemProperties.getJettyClientProxyConfFile());

        log.debug("Configuring server from {}", configFile);
        try (InputStream in = new FileInputStream(configFile)) {
            XmlConfiguration config = new XmlConfiguration(in);
            config.configure(server);
        }
    }

    private void createClient() throws Exception {
        log.trace("createClient()");

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
        return new FastestConnectionSelectingSSLSocketFactory(ctx,
                CryptoUtils.INCLUDED_CIPHER_SUITES);
    }

    private void createConnectors() throws Exception {
        log.trace("createConnectors()");

        createClientHttpConnector(SystemProperties.getConnectorHost(),
                SystemProperties.getClientProxyHttpPort());

        createClientHttpsConnector(SystemProperties.getConnectorHost(),
                SystemProperties.getClientProxyHttpsPort());
    }

    private void createClientHttpConnector(String hostname, int port) {
        log.trace("createClientHttpConnector({}, {})", hostname, port);

        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName(CLIENT_HTTP_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        log.info("Client HTTP connector created ({}:{})", hostname, port);
    }

    private void createClientHttpsConnector(String hostname, int port)
            throws Exception {
        log.trace("createClientHttpsConnector({}, {})", hostname, port);

        SslContextFactory cf = new SslContextFactory(false);
        cf.setWantClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] { new ClientSslKeyManager() },
                new TrustManager[] { new ClientSslTrustManager() },
                new SecureRandom());

        cf.setSslContext(ctx);

        SslSelectChannelConnector connector =
                new SslSelectChannelConnector(cf);

        connector.setName(CLIENT_HTTPS_CONNECTOR_NAME);
        connector.setHost(hostname);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        log.info("Client HTTPS connector created ({}:{})", hostname, port);
    }

    private void createHandlers() throws Exception {
        log.trace("createHandlers()");

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
                    log.trace("Loading client handler {}", handlerClassName);
                    handlers.add(loadHandler(handlerClassName, client));
                } catch (Exception e) {
                    throw new RuntimeException(
                            "Failed to load client handler: "
                                    + handlerClassName, e);
                }
            }
        }

        log.trace("Loading default client handler");
        handlers.add(new ClientMessageHandler(client)); // default handler
        return handlers;
    }

    @Override
    public void start() throws Exception {
        log.trace("start()");

        server.start();
    }

    @Override
    public void join() throws InterruptedException {
        log.trace("join()");

        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    @Override
    public void stop() throws Exception {
        log.trace("stop()");

        client.close();
        server.stop();

        HibernateUtil.closeSessionFactories();
    }

    private static class ClientSslTrustManager implements X509TrustManager {

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
