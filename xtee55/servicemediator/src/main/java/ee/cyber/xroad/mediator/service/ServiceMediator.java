package ee.cyber.xroad.mediator.service;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemPropertiesLoader;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.xroad.mediator.MediatorServerConf;
import ee.cyber.xroad.mediator.MediatorServerConfImpl;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.cyber.xroad.mediator.common.HttpClientManager;

/**
 * Service proxy mediator that handles SDSB and X-Road 5.0 type requests,
 * converts between message versions and sends them to the service.
 */
public class ServiceMediator implements StartStop {

    static {
        new SystemPropertiesLoader() {
            @Override
            protected void loadWithCommonAndLocal() {
                load(MediatorSystemProperties.CONF_FILE_MEDIATOR_COMMON);
                load(MediatorSystemProperties.CONF_FILE_SERVICE_MEDIATOR);
            }
        };
    }

    // Configuration parameters.
    // TODO: Make configurable
    private static final int SERVER_THREAD_POOL_SIZE = 5000;

    private static final Logger LOG =
            LoggerFactory.getLogger(ServiceMediator.class);

    static final String CLIENT_CONNECTOR_NAME = "ClientConnector";
    static final String CLIENT_SSL_CONNECTOR_NAME = "ClientSSLConnector";

    private Server server = new Server();
    private HttpClientManager clientManager = new HttpClientManagerImpl();

    public ServiceMediator() throws Exception {
        MediatorServerConf.reload(new MediatorServerConfImpl());

        configureServer();

        createConnectors();
        createHandlers();
    }

    private void configureServer() {
        server.setThreadPool(new QueuedThreadPool(SERVER_THREAD_POOL_SIZE));
    }

    private void createConnectors() {
        String host = MediatorSystemProperties.getServiceMediatorConnectorHost();
        int port = MediatorSystemProperties.getServiceMediatorHttpPort();

        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setName(CLIENT_CONNECTOR_NAME);
        connector.setHost(host);
        connector.setPort(port);

        connector.setSoLingerTime(0);
        connector.setMaxIdleTime(0);

        connector.setAcceptors(2 * Runtime.getRuntime().availableProcessors());

        server.addConnector(connector);

        // TODO: Add SSL connector?

        LOG.debug("ServiceMediator HTTP connector created ({}:{})", host, port);
    }

    private void createHandlers() {
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new ServiceMediatorHandler(clientManager));

        server.setHandler(handlers);
    }

    @Override
    public void start() throws Exception {
        server.start();
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
}
