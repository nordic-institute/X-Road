package ee.cyber.xroad.mediator.client;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorServerConfImpl;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.cyber.xroad.mediator.common.HttpClientManager;

/**
 * Client proxy mediator that handles SDSB and X-Road 5.0 type requests
 * and routes them to SDSB or X-Road 5.0 client proxy.
 */
public class ClientMediator implements StartStop {

    // SSL session timeout in seconds
    private static final int SSL_SESSION_TIMEOUT = 600;

    static {
        new SystemPropertiesLoader() {
            @Override
            protected void loadWithCommonAndLocal() {
                load(MediatorSystemProperties.CONF_FILE_MEDIATOR_COMMON);
                load(MediatorSystemProperties.CONF_FILE_CLIENT_MEDIATOR);
            }
        };
    }

    // Configuration parameters.
    // TODO: Make configurable
    private static final int SERVER_THREAD_POOL_SIZE = 5000;

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMediator.class);

    private static final String CLIENT_HTTP_CONNECTOR_NAME = "ClientConnector";
    private static final String CLIENT_HTTPS_CONNECTOR_NAME =
            "ClientSslConnector";

    private Server server = new Server();
    private HttpClientManager clientManager = new HttpClientManagerImpl();

    public ClientMediator() throws Exception {
        MediatorServerConf.reload(new MediatorServerConfImpl());

        configureServer();

        createConnectors();
        createHandlers();
    }

    private void configureServer() {
        server.setThreadPool(new QueuedThreadPool(SERVER_THREAD_POOL_SIZE));
    }

    private void createConnectors() throws Exception {
        createClientHttpConnector();
        createClientHttpsConnector();
    }

    private void createClientHttpConnector() {
        String host = MediatorSystemProperties.getClientMediatorConnectorHost();
        int port = MediatorSystemProperties.getClientMediatorHttpPort();

        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName(CLIENT_HTTP_CONNECTOR_NAME);
        connector.setHost(host);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        LOG.info("ClientMediator HTTP connector created ({}:{})", host, port);
    }

    private void createClientHttpsConnector() throws Exception {
        String host = MediatorSystemProperties.getClientMediatorConnectorHost();
        int port = MediatorSystemProperties.getClientMediatorHttpsPort();

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
        connector.setHost(host);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        LOG.info("ClientMediator HTTPS connector created ({}:{})", host, port);
    }

    private void createHandlers() {
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new ClientMediatorHandler(clientManager));

        server.setHandler(handlers);
    }

    @Override
    public void start() throws Exception {
        server.start();

        String sdsbProxy = MediatorSystemProperties.getSdsbProxyAddress();
        String xroadProxy = MediatorSystemProperties.getXroadProxyAddress();
        String xroadUriProxy =
                MediatorSystemProperties.getXroadUriProxyAddress();

        LOG.info("ClientMediator started!\n\t"
                + "SDSB proxy address: {}\n\t"
                + "X-Road 5.0 proxy address: {}\n\t"
                + "X-Road 5.0 uriproxy address: {}",
                new Object[] { sdsbProxy, xroadProxy, xroadUriProxy });
    }

    @Override
    public void join() throws InterruptedException {
        server.join();
    }

    @Override
    public void stop() throws Exception {
        HibernateUtil.closeSessionFactories();

        clientManager.shutdown();
        server.stop();
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
