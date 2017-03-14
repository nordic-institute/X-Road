/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.serverproxy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import ch.qos.logback.access.jetty.RequestLogImpl;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.AuthTrustManager;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonHttpClient;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.antidos.AntiDosConnector;
import ee.ria.xroad.proxy.conf.AuthKeyManager;

/**
 * Server proxy that handles requests of client proxies.
 */
@Slf4j
public class ServerProxy implements StartStop {

    private static final int ACCEPTOR_COUNT = 2 * Runtime.getRuntime().availableProcessors();

    private static final int IDLE_MONITOR_TIMEOUT = 50;

    private static final int IDLE_MONITOR_INTERVAL = 100;

    // SSL session timeout in seconds
    private static final int SSL_SESSION_TIMEOUT = 600;

    private static final String CLIENT_PROXY_CONNECTOR_NAME = "ClientProxyConnector";

    private Server server = new Server();

    private CloseableHttpClient client;
    private IdleConnectionMonitorThread connMonitor;

    private String listenAddress;

    private CloseableHttpClient opMonitorClient;

    /**
     * Constructs and configures a new server proxy.
     * @throws Exception in case of any errors
     */
    public ServerProxy() throws Exception {
        this(SystemProperties.getServerProxyListenAddress());
    }

    /**
     * Constructs and configures a new client proxy with the specified listen address.
     * @param listenAddress the address this server proxy should listen at
     * @throws Exception in case of any errors
     */
    public ServerProxy(String listenAddress) throws Exception {
        this.listenAddress = listenAddress;

        configureServer();

        createClient();
        createOpMonitorClient();
        createConnectors();
        createHandlers();
    }

    private void configureServer() throws Exception {
        log.trace("configureServer()");

        Path file = Paths.get(SystemProperties.getJettyServerProxyConfFile());

        log.debug("Configuring server from {}", file);

        try (InputStream in = Files.newInputStream(file)) {
            new XmlConfiguration(in).configure(server);
        }
    }

    private void createClient() throws Exception {
        log.trace("createClient()");

        HttpClientCreator creator = new HttpClientCreator();

        connMonitor = new IdleConnectionMonitorThread(creator.getConnectionManager());
        connMonitor.setIntervalMilliseconds(IDLE_MONITOR_INTERVAL);
        connMonitor.setConnectionIdleTimeMilliseconds(IDLE_MONITOR_TIMEOUT);

        client = creator.getHttpClient();
    }

    private void createOpMonitorClient() throws Exception {
        opMonitorClient = OpMonitoringDaemonHttpClient.createHttpClient(ServerConf.getSSLKey(),
                TimeUtils.secondsToMillis(OpMonitoringSystemProperties.getOpMonitorServiceConnectionTimeoutSeconds()));
    }

    private void createConnectors() throws Exception {
        log.trace("createConnectors()");

        int port = SystemProperties.getServerProxyListenPort();

        ServerConnector connector = SystemProperties.isSslEnabled()
                ? createClientProxySslConnector(server) : createClientProxyConnector(server);

        connector.setName(CLIENT_PROXY_CONNECTOR_NAME);
        connector.setPort(port);
        connector.setHost(listenAddress);

        connector.setSoLingerTime(SystemProperties.getServerProxyConnectorSoLinger());
        connector.setIdleTimeout(SystemProperties.getServerProxyConnectorMaxIdleTime());

        connector.getConnectionFactories().stream()
                .filter(cf -> cf instanceof HttpConnectionFactory)
                .forEach(httpCf -> ((HttpConnectionFactory) httpCf).getHttpConfiguration().setSendServerVersion(false));

        server.addConnector(connector);

        log.info("ClientProxy {} created ({}:{})", connector.getClass().getSimpleName(), listenAddress, port);
    }

    private void createHandlers() {
        log.trace("createHandlers()");

        RequestLogHandler logHandler = new RequestLogHandler();
        RequestLogImpl reqLog = new RequestLogImpl();
        reqLog.setResource("/logback-access-serverproxy.xml");
        reqLog.setQuiet(true);
        logHandler.setRequestLog(reqLog);

        ServerProxyHandler proxyHandler = new ServerProxyHandler(client, opMonitorClient);

        HandlerCollection handler = new HandlerCollection();
        handler.addHandler(logHandler);
        handler.addHandler(proxyHandler);

        server.setHandler(handler);
    }

    @Override
    public void start() throws Exception {
        log.trace("start()");

        server.start();
        connMonitor.start();
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

        connMonitor.shutdown();
        client.close();
        opMonitorClient.close();
        server.stop();

        HibernateUtil.closeSessionFactories();
    }

    /**
     * Close idle connections.
     */
    public void closeIdleConnections() {
        connMonitor.closeNow();
    }

    private static ServerConnector createClientProxyConnector(Server server) {
        return SystemProperties.isAntiDosEnabled()
                ? new AntiDosConnector(server, ACCEPTOR_COUNT) : new ServerConnector(server, ACCEPTOR_COUNT, -1);
    }

    private static ServerConnector createClientProxySslConnector(Server server) throws Exception {
        SslContextFactory cf = new SslContextFactory(false);
        cf.setNeedClientAuth(true);
        cf.setIncludeCipherSuites(CryptoUtils.getINCLUDED_CIPHER_SUITES());
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[]{AuthKeyManager.getInstance()}, new TrustManager[]{new AuthTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);

        return SystemProperties.isAntiDosEnabled()
                ? new AntiDosConnector(server, ACCEPTOR_COUNT, cf)
                : new ServerConnector(server, ACCEPTOR_COUNT, -1, cf);
    }

}
