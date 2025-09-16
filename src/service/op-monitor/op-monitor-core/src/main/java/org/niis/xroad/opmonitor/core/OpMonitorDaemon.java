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
import ee.ria.xroad.common.conf.InternalSSLKey;
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
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;
import org.niis.xroad.opmonitor.core.config.OpMonitorTlsProperties;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Stream;

import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static java.util.Arrays.stream;

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
    private final OpMonitorTlsProperties opMonitorTlsProperties;
    private final GlobalConfProvider globalConfProvider;
    private final VaultClient vaultClient;
    private final VaultKeyClient vaultKeyClient;
    private final OperationalDataRecordManager operationalDataRecordManager;
    private final HealthDataMetrics healthDataMetrics;
    private final ScheduledExecutorService tlsClientCertificateRefreshScheduler =
            Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());

    private final MetricRegistry healthMetricRegistry = new MetricRegistry();
    private final JmxReporter reporter = JmxReporter.forRegistry(healthMetricRegistry).build();

    @PostConstruct
    @ArchUnitSuppressed("NoVanillaExceptions")
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
    @ArchUnitSuppressed("NoVanillaExceptions")
    public void destroy() throws Exception {
        tlsClientCertificateRefreshScheduler.shutdown();
        server.stop();
        reporter.stop();
    }

    private void createConnector()
            throws NoSuchAlgorithmException, KeyManagementException, CertificateException, IOException, InvalidKeySpecException {
        String listenAddress = opMonitorProperties.listenAddress();
        int port = opMonitorCommonProperties.connection().port();

        String scheme = opMonitorCommonProperties.connection().scheme();
        ServerConnector connector = "https".equalsIgnoreCase(scheme)
                ? createDaemonSslConnector() : createDaemonConnector();

        connector.setName(CLIENT_CONNECTOR_NAME);
        connector.setHost(listenAddress);
        connector.setPort(port);
        connector.getConnectionFactories().stream()
                .filter(HttpConnectionFactory.class::isInstance)
                .map(HttpConnectionFactory.class::cast)
                .forEach(httpCf -> httpCf.getHttpConfiguration().setSendServerVersion(false));

        server.addConnector(connector);

        log.info("OpMonitorDaemon {} created ({}:{})", connector.getClass().getSimpleName(), listenAddress, port);
    }

    private ServerConnector createDaemonConnector() {
        return new ServerConnector(server);
    }


    private ServerConnector createDaemonSslConnector()
            throws NoSuchAlgorithmException, KeyManagementException, CertificateException, IOException, InvalidKeySpecException {
        var cf = new SslContextFactory.Server();
        cf.setNeedClientAuth(true);
        cf.setSessionCachingEnabled(true);
        cf.setSslSessionTimeout(SSL_SESSION_TIMEOUT);
        cf.setIncludeProtocols(CryptoUtils.SSL_PROTOCOL);
        cf.setIncludeCipherSuites(SystemProperties.getXroadTLSCipherSuites());

        SSLContext ctx = SSLContext.getInstance(CryptoUtils.SSL_PROTOCOL);

        ensureOpMonitorTlsKeyPresent();

        ctx.init(new KeyManager[]{new OpMonitorSslKeyManager(vaultClient)},
                new TrustManager[]{createTrustManager()},
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

    private void ensureOpMonitorTlsKeyPresent()
            throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            vaultClient.getOpmonitorTlsCredentials();
        } catch (Exception e) {
            log.warn("Unable to locate op-monitor TLS credentials, attempting to create new ones", e);
            var vaultKeyData = vaultKeyClient.provisionNewCerts();
            var certChain = Stream.concat(stream(vaultKeyData.identityCertChain()), stream(vaultKeyData.trustCerts()))
                    .toArray(X509Certificate[]::new);
            var internalTlsKey = new InternalSSLKey(vaultKeyData.identityPrivateKey(), certChain);
            vaultClient.createOpmonitorTlsCredentials(internalTlsKey);
            log.info("Successfully created op-monitor TLS credentials");
        }
    }

    private OpMonitorSslTrustManager createTrustManager() {
        if (opMonitorTlsProperties.clientCertificateRefreshInterval().toSeconds() < 1) {
            return new OpMonitorSslTrustManager(vaultClient);
        } else {
            var certificateRefreshIntervalInSeconds = opMonitorTlsProperties.clientCertificateRefreshInterval().toSeconds();
            return new OpMonitorSslTrustManager(vaultClient, tlsClientCertificateRefreshScheduler, certificateRefreshIntervalInSeconds);
        }
    }

}
