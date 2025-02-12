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
package org.niis.xroad.proxy.application;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;
import org.niis.xroad.proxy.core.test.TestService;
import org.niis.xroad.proxy.core.test.TestSigningCtxProvider;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.test.globalconf.TestGlobalConf;
import org.niis.xroad.test.globalconf.TestGlobalConfWrapper;
import org.niis.xroad.test.keyconf.TestKeyConf;
import org.niis.xroad.test.serverconf.TestServerConf;
import org.niis.xroad.test.serverconf.TestServerConfWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.GenericApplicationContext;

import java.net.ServerSocket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static ee.ria.xroad.common.SystemProperties.OCSP_RESPONDER_LISTEN_ADDRESS;
import static ee.ria.xroad.common.SystemProperties.PROXY_SERVER_LISTEN_ADDRESS;
import static org.mockito.Mockito.mock;

/**
 * Base class for proxy integration tests
 * Starts and stops the test proxy instance and a service simulator.
 */
public abstract class AbstractProxyIntegrationTest {
    private static final Set<Integer> RESERVED_PORTS = new HashSet<>();
    private static final Instant CLOCK_FIXED_INSTANT = Instant.parse("2020-01-01T00:00:00Z");

    private static GenericApplicationContext applicationContext;

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

    static class TestProxyMain extends ProxyMain {
        @Override
        protected void loadSystemProperties() {
            System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");
            System.setProperty(SystemProperties.PROXY_CONNECTOR_HOST, "127.0.0.1");
            System.setProperty(SystemProperties.PROXY_CLIENT_HTTP_PORT, String.valueOf(proxyClientPort));
            System.setProperty(SystemProperties.PROXY_CLIENT_HTTPS_PORT, String.valueOf(getFreePort()));

            final String serverPort = String.valueOf(getFreePort());
            System.setProperty(SystemProperties.PROXY_SERVER_LISTEN_PORT, serverPort);
            System.setProperty(SystemProperties.PROXY_SERVER_PORT, serverPort);

            System.setProperty(SystemProperties.OCSP_RESPONDER_PORT, String.valueOf(getFreePort()));
            System.setProperty(SystemProperties.JETTY_CLIENTPROXY_CONFIGURATION_FILE, "src/test/clientproxy.xml");
            System.setProperty(SystemProperties.JETTY_SERVERPROXY_CONFIGURATION_FILE, "src/test/serverproxy.xml");
            System.setProperty(SystemProperties.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE, "src/test/ocsp-responder.xml");
            System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

            System.setProperty(PROXY_SERVER_LISTEN_ADDRESS, "127.0.0.1");
            System.setProperty(OCSP_RESPONDER_LISTEN_ADDRESS, "127.0.0.1");

            System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
            System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

            System.setProperty(SystemProperties.PROXY_HEALTH_CHECK_PORT, "5558");
            System.setProperty(SystemProperties.SERVER_CONF_CACHE_PERIOD, "0");

            System.setProperty(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.FALSE.toString());
            super.loadSystemProperties();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestProxySpringConfig {

        @Bean
        TestService testService() {
            service = new TestService(servicePort);
            return service;
        }

        @Bean
        @Primary
        GlobalConfSource globalConfSource() {
            return mock(GlobalConfSource.class);
        }

        @Bean
        @Primary
        GlobalConfProvider globalConfProvider() {
            return TEST_GLOBAL_CONF;
        }

        @Bean
        KeyConfProvider keyConfProvider() {
            return new TestKeyConf(TEST_GLOBAL_CONF);
        }

        @Bean
        ServerConfProvider serverConfProvider() {
            return TEST_SERVER_CONF;
        }

        @Bean
        @Primary
        SigningCtxProvider signingCtxProvider(GlobalConfProvider globalConfProvider, KeyConfProvider keyConfProvider) {
            return new TestSigningCtxProvider(globalConfProvider, keyConfProvider);
        }
    }

    @BeforeClass
    public static void setup() throws Exception {
        TimeUtils.setClock(Clock.fixed(CLOCK_FIXED_INSTANT, ZoneOffset.UTC));
        applicationContext = new TestProxyMain().createApplicationContext(TestProxySpringConfig.class);
    }

    @AfterClass
    public static void teardown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        RESERVED_PORTS.clear();
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
