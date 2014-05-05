package ee.cyber.xroad.mediator.client;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.db.HibernateUtil;
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

    // Configuration parameters.
    // TODO: Make configurable
    private static final int SERVER_THREAD_POOL_SIZE = 5000;

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMediator.class);

    private static final String CLIENT_CONNECTOR_NAME = "ClientConnector";

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

    private void createConnectors() {
        String host = MediatorSystemProperties.getClientMediatorConnectorHost();
        int port = MediatorSystemProperties.getClientMediatorHttpPort();

        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName(CLIENT_CONNECTOR_NAME);
        connector.setHost(host);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(2 * Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        LOG.debug("ClientMediator HTTP connector created ({}:{})", host, port);
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

        LOG.info("ClientMediator started!\n\t" +
                "SDSB proxy address: {}\n\t" +
                "X-Road 5.0 proxy address: {}\n\t" +
                "X-Road 5.0 uriproxy address: {}",
                new Object[] { sdsbProxy, xroadProxy, xroadUriProxy });
    }

    @Override
    public void join() throws InterruptedException {
        server.join();
    }

    @Override
    public void stop() throws Exception {
        HibernateUtil.closeSessionFactory();

        clientManager.shutdown();
        server.stop();
    }
}
