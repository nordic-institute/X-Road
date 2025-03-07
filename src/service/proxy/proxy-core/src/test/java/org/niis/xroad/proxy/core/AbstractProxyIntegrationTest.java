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
import ee.ria.xroad.common.util.JobManager;
import ee.ria.xroad.common.util.TimeUtils;

import lombok.Getter;
import lombok.SneakyThrows;
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
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.opmonitoring.OpMonitoring;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.niis.xroad.proxy.core.test.TestService;
import org.niis.xroad.proxy.core.test.TestSigningCtxProvider;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.test.globalconf.EmptyGlobalConf;
import org.niis.xroad.test.globalconf.TestGlobalConf;
import org.niis.xroad.test.globalconf.TestGlobalConfWrapper;
import org.niis.xroad.test.keyconf.TestKeyConf;
import org.niis.xroad.test.serverconf.EmptyServerConf;
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
import java.util.Set;

import static ee.ria.xroad.common.SystemProperties.PROXY_SERVER_LISTEN_ADDRESS;
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
    protected static TestKeyConf clientKeyConf;
    protected static TestKeyConf serverKeyConf;
    protected static LoggingAuthTrustVerifier clientAuthTrustVerifier;

    @RegisterExtension
    protected static TestServiceExtension testServiceExtension = new TestServiceExtension();

    @BeforeAll
    public static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(CLOCK_FIXED_INSTANT, ZoneOffset.UTC));

        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");
        System.setProperty(SystemProperties.PROXY_CONNECTOR_HOST, "127.0.0.1");
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, String.valueOf(proxyClientPort));
        System.setProperty(SystemProperties.PROXY_CLIENT_HTTPS_PORT, String.valueOf(getFreePort()));

        final String serverPort = String.valueOf(getFreePort());
        System.setProperty(SystemProperties.PROXY_SERVER_LISTEN_PORT, serverPort);
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, serverPort);

        System.setProperty(SystemProperties.JETTY_CLIENTPROXY_CONFIGURATION_FILE, "src/test/clientproxy.xml");
        System.setProperty(SystemProperties.JETTY_SERVERPROXY_CONFIGURATION_FILE, "src/test/serverproxy.xml");
        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

        System.setProperty(PROXY_SERVER_LISTEN_ADDRESS, "127.0.0.1");

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        System.setProperty(SystemProperties.SERVER_CONF_CACHE_PERIOD, "0");

        System.setProperty(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.FALSE.toString());

        org.apache.xml.security.Init.init();

        OpMonitoring.init(new EmptyServerConf());
        MessageLog.init(mock(JobManager.class), new EmptyGlobalConf(), new EmptyServerConf());

        startServices();
    }

    static void startServices() throws Exception {
        startClientProxy();
        startServerProxy();
    }

    private static void startClientProxy() throws Exception {
        clientKeyConf = new TestKeyConf(TEST_GLOBAL_CONF);
        CertHelper certHelper = new CertHelper(TEST_GLOBAL_CONF);
        CertChainFactory certChainFactory = new CertChainFactory(TEST_GLOBAL_CONF);
        clientAuthTrustVerifier = new LoggingAuthTrustVerifier(clientKeyConf, certHelper, certChainFactory);
        SigningCtxProvider signingCtxProvider = new TestSigningCtxProvider(TEST_GLOBAL_CONF, clientKeyConf);
        CommonBeanProxy commonBeanProxy = new CommonBeanProxy(TEST_GLOBAL_CONF, TEST_SERVER_CONF,
                clientKeyConf, signingCtxProvider, certChainFactory, certHelper);

        clientProxy = new ClientProxy(commonBeanProxy, TEST_GLOBAL_CONF, clientKeyConf, TEST_SERVER_CONF,
                clientAuthTrustVerifier);
        clientProxy.afterPropertiesSet();
    }

    private static void startServerProxy() throws Exception {
        serverKeyConf = new TestKeyConf(TEST_GLOBAL_CONF);
        CertHelper certHelper = new CertHelper(TEST_GLOBAL_CONF);
        CertChainFactory certChainFactory = new CertChainFactory(TEST_GLOBAL_CONF);
        SigningCtxProvider signingCtxProvider = new TestSigningCtxProvider(TEST_GLOBAL_CONF, serverKeyConf);
        CommonBeanProxy commonBeanProxy = new CommonBeanProxy(TEST_GLOBAL_CONF, TEST_SERVER_CONF,
                serverKeyConf, signingCtxProvider, certChainFactory, certHelper);

        serverProxy = new ServerProxy(commonBeanProxy);
        serverProxy.afterPropertiesSet();
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
        public String getServiceAddress(ServiceId service) {
            return "http://127.0.0.1:" + testServiceExtension.getService().getPort();
        }
    }

    @Getter
    public static class LoggingAuthTrustVerifier extends AuthTrustVerifier {
        private final List<X509Certificate> verifiedCertificates = new ArrayList<>();

        public LoggingAuthTrustVerifier(KeyConfProvider keyConfProvider, CertHelper certHelper, CertChainFactory certChainFactory) {
            super(keyConfProvider, certHelper, certChainFactory);
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
