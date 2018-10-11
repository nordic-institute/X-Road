/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.logging.RequestLogImplFixLogback1052;
import ee.ria.xroad.common.opmonitoring.OpMonitoringDaemonHttpClient;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.proxy.XroadProxy;
import ee.ria.xroad.proxy.antidos.AntiDosConnector;

import ch.qos.logback.access.jetty.RequestLogImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

/**
 * Server proxy that handles requests of client proxies.
 */
@Slf4j
public class ServerProxy extends XroadProxy {

    private static final int ACCEPTOR_COUNT = 2 * Runtime.getRuntime().availableProcessors();

    private static final int IDLE_MONITOR_TIMEOUT = 50;

    private static final int IDLE_MONITOR_INTERVAL = 100;

    private static final int CONNECTOR_SO_LINGER_MILLIS = SystemProperties.getServerProxyConnectorSoLinger() * 1000;

    private static final String CLIENT_PROXY_CONNECTOR_NAME = "ClientProxyConnector";

    private String listenAddress;

    private CloseableHttpClient opMonitorClient;

    private HttpClientCreator creator;

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
        opMonitorClient = createOpMonitorClient();
    }

    /**
     * Close idle connections.
     */
    public void closeIdleConnections() {
        getConnectionMonitor().closeNow();
    }

    @Override
    public void stop() throws Exception {
        log.trace("stop()");
        opMonitorClient.close();
        super.stop();
    }

    @Override
    protected IdleConnectionMonitorThread createConnectionMonitor() throws Exception {
        IdleConnectionMonitorThread monitor = new IdleConnectionMonitorThread(getCreator().getConnectionManager());
        monitor.setIntervalMilliseconds(IDLE_MONITOR_INTERVAL);
        monitor.setConnectionIdleTimeMilliseconds(IDLE_MONITOR_TIMEOUT);
        return monitor;
    }

    @Override
    protected Path getJettyServerConfFilePath() {
        return Paths.get(SystemProperties.getJettyServerProxyConfFile());
    }

    @Override
    protected CloseableHttpClient createClient() throws Exception {
        log.trace("createClient()");
        return getCreator().getHttpClient();
    }

    @Override
    protected Collection<ServerConnector> createConnectors() throws Exception {
        log.trace("createConnectors()");

        int port = SystemProperties.getServerProxyListenPort();

        ServerConnector connector = SystemProperties.isSslEnabled()
                ? createClientProxySslConnector() : createClientProxyConnector();

        connector.setName(CLIENT_PROXY_CONNECTOR_NAME);
        connector.setPort(port);
        connector.setHost(listenAddress);

        connector.setSoLingerTime(CONNECTOR_SO_LINGER_MILLIS);
        connector.setIdleTimeout(SystemProperties.getServerProxyConnectorMaxIdleTime());

        connector.getConnectionFactories().stream()
                .filter(cf -> cf instanceof HttpConnectionFactory)
                .forEach(httpCf -> ((HttpConnectionFactory) httpCf).getHttpConfiguration().setSendServerVersion(false));

        log.info("ClientProxy {} created ({}:{})", connector.getClass().getSimpleName(), listenAddress, port);

        return Arrays.asList(connector);
    }

    @Override
    protected AbstractHandler createHandlers() {
        log.trace("createHandlers()");

        RequestLogHandler logHandler = new RequestLogHandler();
        RequestLogImpl reqLog = new RequestLogImplFixLogback1052();
        reqLog.setResource("/logback-access-serverproxy.xml");
        reqLog.setQuiet(true);
        logHandler.setRequestLog(reqLog);

        ServerProxyHandler proxyHandler = new ServerProxyHandler(getClient(), opMonitorClient);

        HandlerCollection handler = new HandlerCollection();
        handler.addHandler(logHandler);
        handler.addHandler(proxyHandler);

        return handler;
    }

    private HttpClientCreator getCreator() {
        if (creator == null) {
            creator = new HttpClientCreator();
        }
        return creator;
    }

    private CloseableHttpClient createOpMonitorClient() throws Exception {
        return OpMonitoringDaemonHttpClient.createHttpClient(ServerConf.getSSLKey(),
                TimeUtils.secondsToMillis(OpMonitoringSystemProperties.getOpMonitorServiceConnectionTimeoutSeconds()),
                TimeUtils.secondsToMillis(OpMonitoringSystemProperties.getOpMonitorServiceSocketTimeoutSeconds()));
    }

    private ServerConnector createClientProxyConnector() {
        return SystemProperties.isAntiDosEnabled()
                ? new AntiDosConnector(getServer(), ACCEPTOR_COUNT)
                : new ServerConnector(getServer(), ACCEPTOR_COUNT, -1);
    }

    private ServerConnector createClientProxySslConnector() throws Exception {
        SslContextFactory cf = new SslContextFactory(false);
        cf.setNeedClientAuth(true);
        cf.setIncludeCipherSuites(getAcceptedCipherSuites());
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);

        cf.setSslContext(createSSLContext());

        return SystemProperties.isAntiDosEnabled()
                ? new AntiDosConnector(getServer(), ACCEPTOR_COUNT, cf)
                : new ServerConnector(getServer(), ACCEPTOR_COUNT, -1, cf);
    }

}
