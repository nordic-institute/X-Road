package ee.cyber.xroad.mediator.client;

import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;

/**
 * Client proxy mediator that handles X-Road 6.0 and X-Road 5.0 type requests
 * and routes them to X-Road 6.0 or X-Road 5.0 client proxy.
 */
@Slf4j
public class ClientMediator implements StartStop {

    // SSL session timeout in seconds
    private static final int SSL_SESSION_TIMEOUT = 600;

    // Configuration parameters.
    // TODO Make configurable
    private static final int SERVER_THREAD_POOL_SIZE = 5000;

    private static final String CLIENT_HTTP_CONNECTOR_NAME = "ClientConnector";
    private static final String CLIENT_HTTPS_CONNECTOR_NAME =
            "ClientSslConnector";

    private Server server = new Server();
    private HttpClientManager clientManager = new HttpClientManagerImpl();

    /**
     * Reloads configuration and configures a new client mediator.
     * @throws Exception in case of any errors
     */
    public ClientMediator() throws Exception {
        try {
            V5IsAuthentication.loadConf();
        } catch (Exception e) {
            log.error("Cannot load authentication methods and certificates "
                    + "of information systems of 5.0 X-Road: {}",
                    e.getMessage());
        }

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

        log.info("ClientMediator HTTP connector created ({}:{})", host, port);
    }

    private void createClientHttpsConnector() throws Exception {
        String host = MediatorSystemProperties.getClientMediatorConnectorHost();
        int port = MediatorSystemProperties.getClientMediatorHttpsPort();

        SslContextFactory cf = new SslContextFactory(false);
        cf.setWantClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new ClientSslKeyManager()},
                new TrustManager[] {new ClientSslTrustManager()},
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

        log.info("ClientMediator HTTPS connector created ({}:{})", host, port);
    }

    private void createHandlers() {
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new ClientMediatorHandler(clientManager));

        server.setHandler(handlers);
    }

    @Override
    public void start() throws Exception {
        server.start();

        String xroadProxy = MediatorSystemProperties.getXRoadProxyAddress();
        String v5XRoadProxy = MediatorSystemProperties.getV5XRoadProxyAddress();
        String xroadUriProxy =
                MediatorSystemProperties.getV5XRoadUriProxyAddress();

        log.info("ClientMediator started!\n\t"
                + "X-Road 6.0 proxy address: {}\n\t"
                + "X-Road 5.0 proxy address: {}\n\t"
                + "X-Road 5.0 uriproxy address: {}",
                new Object[] {xroadProxy, v5XRoadProxy, xroadUriProxy});
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
