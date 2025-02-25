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
import ee.ria.xroad.common.util.TimeUtils;

import org.apache.http.client.HttpClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.proxy.core.antidos.AntiDosConfiguration;
import org.niis.xroad.proxy.core.clientproxy.AuthTrustVerifier;
import org.niis.xroad.proxy.core.clientproxy.ClientProxy;
import org.niis.xroad.proxy.core.clientproxy.ClientRestMessageHandler;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.configuration.ProxyClientConfig;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.messagelog.NullLogManager;
import org.niis.xroad.proxy.core.opmonitoring.NullOpMonitoringBuffer;
import org.niis.xroad.proxy.core.opmonitoring.OpMonitoring;
import org.niis.xroad.proxy.core.serverproxy.ServerProxy;
import org.niis.xroad.proxy.core.serverproxy.ServiceHandlerLoader;
import org.niis.xroad.proxy.core.test.TestService;
import org.niis.xroad.proxy.core.test.TestSigningCtxProvider;
import org.niis.xroad.proxy.core.test.util.ListInstanceWrapper;
import org.niis.xroad.proxy.core.util.CertHashBasedOcspResponderClient;
import org.niis.xroad.proxy.core.util.CommonBeanProxy;
import org.niis.xroad.test.globalconf.TestGlobalConf;
import org.niis.xroad.test.globalconf.TestGlobalConfWrapper;
import org.niis.xroad.test.keyconf.TestKeyConf;
import org.niis.xroad.test.serverconf.TestServerConf;
import org.niis.xroad.test.serverconf.TestServerConfWrapper;

import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

    private static ClientProxy clientProxy;
    private static ServerProxy serverProxy;

    protected static int proxyClientPort = getFreePort();
    protected static int servicePort = getFreePort();
    protected static TestService service;

    protected static final TestServerConfWrapper TEST_SERVER_CONF = new TestServerConfWrapper(new TestServerConf(servicePort));
    protected static final TestGlobalConfWrapper TEST_GLOBAL_CONF = new TestGlobalConfWrapper(new TestGlobalConf());

    @Rule
    public final ExternalResource serviceResource = new ExternalResource() {
        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    try {
                        before();
                        service.before();
                        try {
                            base.evaluate();
                        } finally {
                            service.assertOk();
                        }
                    } finally {
                        after();
                    }
                }
            };
        }
    };

    @BeforeClass
    public static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(CLOCK_FIXED_INSTANT, ZoneOffset.UTC));

        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");

        final String serverPort = String.valueOf(getFreePort());
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, serverPort);

        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");

        System.setProperty(SystemProperties.SERVER_CONF_CACHE_PERIOD, "0");

        org.apache.xml.security.Init.init();
        Map<String, String> properties = Map.of(
                "xroad.proxy.server.listen-address", "127.0.0.1",
                "xroad.proxy.server.listen-port", serverPort,
                "xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml",
                "xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml",
                "xroad.proxy.client-proxy.connector-host", "127.0.0.1",
                "xroad.proxy.client-proxy.client-http-port", valueOf(proxyClientPort),
                "xroad.proxy.client-proxy.client-https-port", valueOf(getFreePort())
        );

        ProxyProperties proxyProperties = ConfigUtils.initConfiguration(ProxyProperties.class, properties);
        startServices(proxyProperties);
    }

    static void startServices(ProxyProperties proxyProperties) throws Exception {
        service = new TestService(servicePort);
        service.start();

        KeyConfProvider keyConfProvider = new TestKeyConf(TEST_GLOBAL_CONF);
        CertHelper certHelper = new CertHelper(TEST_GLOBAL_CONF);
        AuthTrustVerifier authTrustVerifier = new AuthTrustVerifier(mock(CertHashBasedOcspResponderClient.class),
                TEST_GLOBAL_CONF, keyConfProvider, certHelper);
        SigningCtxProvider signingCtxProvider = new TestSigningCtxProvider(TEST_GLOBAL_CONF, keyConfProvider);

        CommonBeanProxy commonBeanProxy = new CommonBeanProxy(TEST_GLOBAL_CONF, TEST_SERVER_CONF,
                keyConfProvider, signingCtxProvider, certHelper, null);

        HttpClient httpClient = new ProxyClientConfig.ProxyHttpClientInitializer()
                .proxyHttpClient(proxyProperties.clientProxy(), authTrustVerifier, TEST_GLOBAL_CONF, keyConfProvider);

        ClientRestMessageHandler restMessageHandler = new ClientRestMessageHandler(commonBeanProxy, httpClient);
        clientProxy = new ClientProxy(TEST_SERVER_CONF, proxyProperties.clientProxy(),
                new ListInstanceWrapper<>(List.of(restMessageHandler)));
        clientProxy.init();

        OpMonitorCommonProperties opMonitorCommonProperties = ConfigUtils.defaultConfiguration(OpMonitorCommonProperties.class);
        ServiceHandlerLoader serviceHandlerLoader = new ServiceHandlerLoader(TEST_SERVER_CONF, TEST_GLOBAL_CONF,
                mock(MonitorRpcClient.class), proxyProperties.addOn(), opMonitorCommonProperties);
        serverProxy = new ServerProxy(proxyProperties.server(), mock(AntiDosConfiguration.class), commonBeanProxy, serviceHandlerLoader,
                opMonitorCommonProperties);
        serverProxy.init();

        OpMonitoring.init(new NullOpMonitoringBuffer(null));
        MessageLog.init(new NullLogManager(TEST_GLOBAL_CONF, TEST_SERVER_CONF));
    }

    @AfterClass
    public static void afterAll() throws Exception {
        RESERVED_PORTS.clear();
        service.destroy();
        serverProxy.destroy();
        clientProxy.destroy();
    }

    @After
    public void after() {
        TEST_SERVER_CONF.setServerConfProvider(new TestServerConf(servicePort));
        TEST_GLOBAL_CONF.setGlobalConfProvider(new TestGlobalConf());
    }

    static int getFreePort() {
        while (true) {
            try (ServerSocket ss = new ServerSocket(0)) {
                final int port = ss.getLocalPort();
                if (RESERVED_PORTS.add(port)) {
                    return port;
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
