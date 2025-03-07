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
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jmx.JmxReporter;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;

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
@Startup
@ApplicationScoped
@RequiredArgsConstructor
public final class OpMonitorDaemon {
    private static final String CLIENT_CONNECTOR_NAME = "OpMonitorDaemonClientConnector";

    private static final int SSL_SESSION_TIMEOUT = 600;

    // The start timestamp is saved once the server has been started.
    // This value is reported over JMX.
    @Getter(AccessLevel.PRIVATE)
    private long startTimestamp;

    private final Server server = new Server();

    private final OpMonitorProperties opMonitorProperties;
    private final OpMonitorCommonProperties opMonitorCommonProperties;
    private final VaultKeyProvider vaultKeyProvider;
    private final GlobalConfProvider globalConfProvider;
    private final OperationalDataRecordManager operationalDataRecordManager;
    private final HealthDataMetrics healthDataMetrics;

    private final MetricRegistry healthMetricRegistry = new MetricRegistry();
    private final JmxReporter reporter = JmxReporter.forRegistry(healthMetricRegistry).build();

    @PostConstruct
    public void init() throws Exception {
        log.info("Creating OpMonitorDaemon.");
        startTimestamp = getEpochMillisecond();

        createConnector();
        createHandler();
        registerHealthMetrics();
        reporter.start();
        server.start();
        log.info("OpMonitorDaemon started.");
    }

    @PreDestroy
    public void destroy() throws Exception {
        server.stop();
        reporter.stop();
    }

    private void createConnector() {
        String listenAddress = opMonitorProperties.listenAddress();
        int port = opMonitorCommonProperties.connection().port();

        String scheme = opMonitorCommonProperties.connection().scheme();
        ServerConnector connector = "https".equalsIgnoreCase(scheme)
                ? createDaemonSslConnector() : createDaemonConnector();

        connector.setName(CLIENT_CONNECTOR_NAME);
        connector.setHost(listenAddress);
        connector.setPort(port);
        connector.getConnectionFactories().stream()
                .filter(cf -> cf instanceof HttpConnectionFactory)
                .forEach(httpCf -> ((HttpConnectionFactory) httpCf).getHttpConfiguration().setSendServerVersion(false));

        server.addConnector(connector);

        log.info("OpMonitorDaemon {} created ({}:{})", connector.getClass().getSimpleName(), listenAddress, port);
    }

    private ServerConnector createDaemonConnector() {
        return new ServerConnector(server);
    }

    @SneakyThrows
    private ServerConnector createDaemonSslConnector() {
        var cf = new SslContextFactory.Server();
        cf.setNeedClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setIncludeProtocols(CryptoUtils.SSL_PROTOCOL);
        cf.setIncludeCipherSuites(SystemProperties.getXroadTLSCipherSuites());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);

        ctx.init(new KeyManager[]{vaultKeyProvider.getKeyManager()},
                new TrustManager[]{vaultKeyProvider.getTrustManager()},
                new SecureRandom());

        cf.setSslContext(ctx);
        return new ServerConnector(server, cf);
    }

    private void createHandler() {
        server.setHandler(new OpMonitorDaemonRequestHandler(opMonitorProperties, globalConfProvider,
                healthMetricRegistry, operationalDataRecordManager, healthDataMetrics));
    }

    private void registerHealthMetrics() {
        healthDataMetrics.registerInitialMetrics(healthMetricRegistry, this::getStartTimestamp);
    }
}
