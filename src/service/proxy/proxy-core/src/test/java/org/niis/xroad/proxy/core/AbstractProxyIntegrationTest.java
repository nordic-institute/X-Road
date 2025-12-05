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
package org.niis.xroad.proxy.core;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestPortUtils;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.http.client.HttpClient;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.common.rpc.NoopVaultKeyProvider;
import org.niis.xroad.common.vault.NoopVaultClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.messagelog.MessageRecordEncryption;
import org.niis.xroad.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.antidos.AntiDosConfiguration;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.clientproxy.ClientRestMessageHandler;
import org.niis.xroad.proxy.core.clientproxy.ReloadingSSLSocketFactory;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.configuration.ProxyClientConfig;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.messagelog.NullLogManager;
import org.niis.xroad.proxy.core.serverproxy.ClientProxyVersionVerifier;
import org.niis.xroad.proxy.core.serverproxy.HttpClientCreator;
import org.niis.xroad.proxy.core.serverproxy.IdleConnectionMonitorThread;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.niis.xroad.proxy.core.serverproxy.ServerProxyHandler;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerLoader;
import org.niis.xroad.proxy.core.test.TestService;
import org.niis.xroad.proxy.core.test.TestSigningCtxProvider;
import org.niis.xroad.proxy.core.test.util.ListInstanceWrapper;
import org.niis.xroad.proxy.core.util.CertHashBasedOcspResponderClient;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;
import org.niis.xroad.proxy.core.util.MessageProcessorFactory;
import org.niis.xroad.test.globalconf.TestGlobalConf;
import org.niis.xroad.test.globalconf.TestGlobalConfWrapper;
import org.niis.xroad.test.keyconf.TestKeyConf;
import org.niis.xroad.test.serverconf.TestServerConf;
import org.niis.xroad.test.serverconf.TestServerConfWrapper;

import javax.net.ssl.SSLSession;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.valueOf;
import static org.mockito.Mockito.mock;

/**
 * Base class for proxy integration tests
 * Starts and stops the test proxy instance and a service simulator.
 */
public abstract class AbstractProxyIntegrationTest {
    private static final Set<Integer> RESERVED_PORTS = new HashSet<>();
    private static final Instant CLOCK_FIXED_INSTANT = Instant.parse("2020-01-01T00:00:00Z");

    protected static ClientProxy clientProxy;
    protected static ServerProxy serverProxy;

    protected static int proxyClientPort = getFreePort();

    protected static final TestServerConfWrapper TEST_SERVER_CONF = new TestServerConfWrapper(new TestServiceServerConf());
    protected static final TestGlobalConfWrapper TEST_GLOBAL_CONF = new TestGlobalConfWrapper(new TestGlobalConf());
    protected static final OcspVerifierFactory OCSP_VERIFIER_FACTORY = new OcspVerifierFactory();
    protected static TestKeyConf clientKeyConf;
    protected static TestKeyConf serverKeyConf;
    protected static LoggingAuthTrustVerifier clientAuthTrustVerifier;

    @RegisterExtension
    protected static TestServiceExtension testServiceExtension = new TestServiceExtension();

    @BeforeAll
    public static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(CLOCK_FIXED_INSTANT, ZoneOffset.UTC));

        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");

        final String serverPort = String.valueOf(getFreePort());

        org.apache.xml.security.Init.init();

        Map<String, String> properties = Map.of(
                "xroad.proxy.server.listen-address", "127.0.0.1",
                "xroad.proxy.server.listen-port", serverPort,
                "xroad.proxy.server-port", serverPort,
                "xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml",
                "xroad.proxy.client-proxy.client-timeout", "15000",
                "xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml",
                "xroad.proxy.client-proxy.connector-host", "127.0.0.1",
                "xroad.proxy.client-proxy.client-http-port", valueOf(proxyClientPort),
                "xroad.proxy.client-proxy.client-https-port", valueOf(getFreePort())
        );

        ProxyProperties proxyProperties = ConfigUtils.initConfiguration(ProxyProperties.class, properties);
        CommonProperties commonProperties = ConfigUtils.initConfiguration(CommonProperties.class, Map.of(
                "xroad.common.temp-files-path", "build/"
        ));
        startServices(proxyProperties, commonProperties);
    }

    static void startServices(ProxyProperties proxyProperties, CommonProperties commonProperties) throws Exception {
        startClientProxy(proxyProperties, commonProperties);
        startServerProxy(proxyProperties, commonProperties);

        MessageLog.init(new NullLogManager(TEST_GLOBAL_CONF, TEST_SERVER_CONF));
    }

    private static void startClientProxy(ProxyProperties proxyProperties, CommonProperties commonProperties) throws Exception {
        clientKeyConf = new TestKeyConf(TEST_GLOBAL_CONF);
        CertHelper certHelper = new CertHelper(TEST_GLOBAL_CONF, OCSP_VERIFIER_FACTORY);
        clientAuthTrustVerifier = new LoggingAuthTrustVerifier(mock(CertHashBasedOcspResponderClient.class),
                TEST_GLOBAL_CONF, clientKeyConf, certHelper);
        SigningCtxProvider signingCtxProvider = new TestSigningCtxProvider(TEST_GLOBAL_CONF, clientKeyConf);
        ClientAuthenticationService clientAuthenticationService = new ClientAuthenticationService(TEST_SERVER_CONF,
                new NoopVaultKeyProvider(), proxyProperties);

        EncryptionConfigProvider encryptionConfigProvider = mock(EncryptionConfigProvider.class);
        var messageRecordEncryption = mock(MessageRecordEncryption.class);

        ReloadingSSLSocketFactory reloadingSSLSocketFactory = new ReloadingSSLSocketFactory(TEST_GLOBAL_CONF, clientKeyConf);
        HttpClient httpClient = new ProxyClientConfig.ProxyHttpClientInitializer()
                .proxyHttpClient(proxyProperties, clientAuthTrustVerifier, reloadingSSLSocketFactory);
        MessageProcessorFactory messageProcessorFactory =
                new MessageProcessorFactory(httpClient, null, proxyProperties, TEST_GLOBAL_CONF, TEST_SERVER_CONF,
                        clientAuthenticationService, clientKeyConf, signingCtxProvider, OCSP_VERIFIER_FACTORY, commonProperties, null,
                        null, null, null, encryptionConfigProvider, messageRecordEncryption);

        ClientRestMessageHandler restMessageHandler = new ClientRestMessageHandler(messageProcessorFactory,
                proxyProperties, TEST_GLOBAL_CONF, clientKeyConf, new NoOpMonitoringBuffer());
        clientProxy = new ClientProxy(TEST_SERVER_CONF, proxyProperties.clientProxy(), reloadingSSLSocketFactory,
                new ListInstanceWrapper<>(List.of(restMessageHandler)));
        clientProxy.init();
    }

    private static void startServerProxy(ProxyProperties proxyProperties, CommonProperties commonProperties) throws Exception {
        serverKeyConf = new TestKeyConf(TEST_GLOBAL_CONF);
        CertHelper certHelper = new CertHelper(TEST_GLOBAL_CONF, OCSP_VERIFIER_FACTORY);
        SigningCtxProvider signingCtxProvider = new TestSigningCtxProvider(TEST_GLOBAL_CONF, serverKeyConf);

        EncryptionConfigProvider encryptionConfigProvider = mock(EncryptionConfigProvider.class);
        var messageRecordEncryption = mock(MessageRecordEncryption.class);

        ServiceHandlerLoader serviceHandlerLoader = new ServiceHandlerLoader(TEST_SERVER_CONF, TEST_GLOBAL_CONF,
                mock(MonitorRpcClient.class), commonProperties, proxyProperties, new NoopVaultClient());
        HttpClientCreator httpClientCreator = new HttpClientCreator(TEST_SERVER_CONF,
                proxyProperties.clientProxy().clientTlsProtocols(), proxyProperties.clientProxy().clientTlsCiphers());
        ClientAuthenticationService clientAuthenticationService = new ClientAuthenticationService(
                TEST_SERVER_CONF, new NoopVaultKeyProvider(), proxyProperties);
        MessageProcessorFactory messageProcessorFactory = new MessageProcessorFactory(
                null, httpClientCreator.getHttpClient(), proxyProperties, TEST_GLOBAL_CONF, TEST_SERVER_CONF,
                clientAuthenticationService, serverKeyConf,
                signingCtxProvider, OCSP_VERIFIER_FACTORY, commonProperties, null, null,
                serviceHandlerLoader, certHelper, encryptionConfigProvider, messageRecordEncryption);
        ServerProxyHandler serverProxyHandler = new ServerProxyHandler(messageProcessorFactory, proxyProperties.server(),
                mock(ClientProxyVersionVerifier.class), TEST_GLOBAL_CONF,
                new NoOpMonitoringBuffer());
        serverProxy = new ServerProxy(proxyProperties, TEST_GLOBAL_CONF, serverKeyConf,
                serverProxyHandler, mock(IdleConnectionMonitorThread.class), mock(AntiDosConfiguration.class));
        serverProxy.init();
    }

    @AfterAll
    public static void afterAll() throws Exception {
        RESERVED_PORTS.clear();
        serverProxy.destroy();
        clientProxy.destroy();
    }

    @AfterEach
    public void after() {
        TEST_SERVER_CONF.setServerConfProvider(new TestServiceServerConf());
        TEST_GLOBAL_CONF.setGlobalConfProvider(new TestGlobalConf());
        clientAuthTrustVerifier.clearVerifiedCertificates();
    }

    protected void setServiceHandler(TestService.Handler handler) {
        testServiceExtension.getService().setHandler(handler);
    }

    static int getFreePort() {
        while (true) {
            final int port = TestPortUtils.findRandomPort();
            if (RESERVED_PORTS.add(port)) {
                return port;
            }
        }
    }

    @Getter
    public static class TestServiceExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

        private TestService service;

        @Override
        public void beforeAll(ExtensionContext context) throws Exception {
            service = new TestService(0);
            service.start();
        }

        @Override
        public void afterAll(ExtensionContext context) throws Exception {
            service.destroy();
        }

        @Override
        public void beforeEach(ExtensionContext context) {
            service.before();
        }

        @Override
        public void afterEach(ExtensionContext context) throws Exception {
            service.assertOk();
        }
    }

    public static class TestServiceServerConf extends TestServerConf {

        @Override
        public String getServiceAddress(ServiceId serviceId) {
            return "http://127.0.0.1:" + testServiceExtension.getService().getPort();
        }
    }

    @Getter
    public static class LoggingAuthTrustVerifier extends AuthTrustVerifier {
        private final List<X509Certificate> verifiedCertificates = new ArrayList<>();

        public LoggingAuthTrustVerifier(CertHashBasedOcspResponderClient certHashBasedOcspResponderClient,
                                        GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider, CertHelper certHelper) {
            super(certHashBasedOcspResponderClient, globalConfProvider, keyConfProvider, certHelper);
        }

        @SneakyThrows
        @Override
        protected void verify(HttpContext context, SSLSession sslSession, URI selectedAddress) {
            X509Certificate[] peerCerts = (X509Certificate[]) sslSession.getPeerCertificates();
            if (peerCerts.length > 0) {
                verifiedCertificates.add(peerCerts[0]);
            }
            super.verify(context, sslSession, selectedAddress);
        }

        public void clearVerifiedCertificates() {
            verifiedCertificates.clear();
        }
    }
}
