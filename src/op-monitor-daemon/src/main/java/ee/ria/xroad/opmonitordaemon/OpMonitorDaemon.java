/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.StartStop;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.security.SecureRandom;

import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;

/**
 * The main HTTP(S) request handler of the operational monitoring daemon.
 * This class handles the requests for storing and querying operational data (JSON for storing, SOAP for querying).
 * SOAP requests for monitoring data are further processed by the QueryRequestProcessor class.
 */
@Slf4j
final class OpMonitorDaemon implements StartStop {

    private static final String CLIENT_CONNECTOR_NAME = "OpMonitorDaemonClientConnector";

    private static final int SSL_SESSION_TIMEOUT = 600;

    // The start timestamp is saved once the server has been started.
    // This value is reported over JMX.
    @Getter(AccessLevel.PRIVATE)
    private long startTimestamp;

    private Server server = new Server();

    private final MetricRegistry healthMetricRegistry = new MetricRegistry();
    private final JmxReporter reporter = JmxReporter.forRegistry(healthMetricRegistry).build();

    /**
     * Constructor. Creates the connector and request handlers.
     * @throws Exception in case of any errors
     */
    OpMonitorDaemon() throws Exception {
        createConnector();
        createHandler();
        registerHealthMetrics();
    }

    @Override
    public void start() throws Exception {
        startTimestamp = getEpochMillisecond();

        reporter.start();
        server.start();
    }

    @Override
    public void stop() throws Exception {
        server.stop();
        reporter.stop();
    }

    @Override
    public void join() throws InterruptedException {
        if (server.getThreadPool() != null) {
            server.join();
        }
    }

    private void createConnector() {
        String listenAddress = OpMonitoringSystemProperties.getOpMonitorHost();
        int port = OpMonitoringSystemProperties.getOpMonitorPort();

        String scheme = OpMonitoringSystemProperties.getOpMonitorDaemonScheme();
        ServerConnector connector = "https".equalsIgnoreCase(scheme)
                ? createDaemonSslConnector(server) : createDaemonConnector(server);

        connector.setName(CLIENT_CONNECTOR_NAME);
        connector.setHost(listenAddress);
        connector.setPort(port);
        connector.getConnectionFactories().stream()
                .filter(cf -> cf instanceof HttpConnectionFactory)
                .forEach(httpCf -> ((HttpConnectionFactory) httpCf).getHttpConfiguration().setSendServerVersion(false));

        server.addConnector(connector);

        log.info("OpMonitorDaemon {} created ({}:{})", connector.getClass().getSimpleName(), listenAddress, port);
    }

    private static ServerConnector createDaemonConnector(Server server) {
        return new ServerConnector(server);
    }

    @SneakyThrows
    private static ServerConnector createDaemonSslConnector(Server server) {
        var cf = new SslContextFactory.Server();
        cf.setNeedClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setIncludeProtocols(CryptoUtils.SSL_PROTOCOL);
        cf.setIncludeCipherSuites(SystemProperties.getXroadTLSCipherSuites());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);
        ctx.init(new KeyManager[] {new OpMonitorSslKeyManager()}, new TrustManager[] {new OpMonitorSslTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);
        return new ServerConnector(server, cf);
    }

    private void createHandler() {
        server.setHandler(new OpMonitorDaemonRequestHandler(healthMetricRegistry));
    }

    private void registerHealthMetrics() {
        HealthDataMetrics.registerInitialMetrics(healthMetricRegistry, this::getStartTimestamp);
    }
}
