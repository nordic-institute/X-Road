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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.globalconf.GlobalConfSource;
import ee.ria.xroad.common.conf.globalconf.TestGlobalConfWrapper;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.proxy.conf.KeyConfProvider;
import ee.ria.xroad.proxy.testutil.IntegrationTest;
import ee.ria.xroad.proxy.testutil.TestGlobalConf;
import ee.ria.xroad.proxy.testutil.TestKeyConf;
import ee.ria.xroad.proxy.testutil.TestServerConf;
import ee.ria.xroad.proxy.testutil.TestServerConfWrapper;
import ee.ria.xroad.proxy.testutil.TestService;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.experimental.categories.Category;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.ServerSocket;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;

/**
 * Base class for proxy integration tests
 * Starts and stops the test proxy instance and a service simulator.
 */
@Slf4j
@Category(IntegrationTest.class)
public abstract class AbstractProxyIntegrationTest {
    static final Set<Integer> RESERVED_PORTS = new HashSet<>();
    static final Instant CLOCK_FIXED_INSTANT = Instant.parse("2020-01-01T00:00:00Z");

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


    public static void setSystemProperties(Map<String, String> params) {
        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/etc/");
        System.setProperty("xroad.proxy.client-proxy.client-http-port", String.valueOf(proxyClientPort));
        System.setProperty("xroad.proxy.client-proxy.client-https-port", String.valueOf(getFreePort()));

        final String serverPort = String.valueOf(getFreePort());
        System.setProperty("xroad.proxy.server.listen-port", serverPort);
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, serverPort);

        System.setProperty("xroad.proxy.ocsp-responder.port", String.valueOf(getFreePort()));

        System.setProperty(SystemProperties.TEMP_FILES_PATH, "build/");

        System.setProperty(SystemProperties.PROXY_CLIENT_TIMEOUT, "15000");
        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        System.setProperty(SystemProperties.PROXY_HEALTH_CHECK_PORT, "5558");
        System.setProperty(SystemProperties.SERVER_CONF_CACHE_PERIOD, "0");

        System.setProperty(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.FALSE.toString());

        params.forEach(System::setProperty);
    }

    @Configuration
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
    }

    @AfterClass
    public static void teardown() {
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
