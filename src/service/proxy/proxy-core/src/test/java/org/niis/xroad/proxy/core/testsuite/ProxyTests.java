/*
 * The MIT License
 *
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

package org.niis.xroad.proxy.core.testsuite;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestContext;
import org.niis.xroad.proxy.core.test.TestcaseLoader;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.X_NETWORK_ERROR;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper.PROXY_PORT;

public class ProxyTests {

    static ProxyProperties proxyProperties;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        Map<String, String> props = new HashMap<>();
        props.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        props.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");

        ProxyTestSuiteHelper.setPropsIfNotSet(props);

        proxyProperties = new SmallRyeConfigBuilder()
                .withMapping(ProxyProperties.class)
                .withSources(new PropertiesConfigSource(props, "testProperties"))
                .build()
                .getConfigMapping(ProxyProperties.class);

        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        ProxyTestSuiteHelper.proxyProperties = proxyProperties;
        ProxyTestSuiteHelper.startTestServices();
        ProxyTestSuiteHelper.startDummyProxy();
    }

    @AfterAll
    static void afterAll() {
        ProxyTestSuiteHelper.destroyTestServices();
    }

    TestContext ctx;

    @TestFactory
    Stream<DynamicTest> proxyTestSuiteNormalTestCases() {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> !(testCase instanceof UsingDummyServerProxy))
                .filter(testCase -> !(testCase instanceof UsingAbortingServerProxy))
                .filter(testCase -> !(testCase instanceof SslMessageTestCase))
                .filter(testCase -> !(testCase instanceof IsolatedSslMessageTestCase))
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, valueOf(PROXY_PORT));
        ctx = new TestContext(proxyProperties);

        return createDynamicTests(testCasesToRun);
    }

    @TestFactory
    Stream<DynamicTest> proxyTestSuiteNormalTestCasesDummyProxy() {
        // tests using dummy proxy
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> testCase instanceof UsingDummyServerProxy)
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, valueOf(ProxyTestSuiteHelper.DUMMY_SERVER_PROXY_PORT));
        ctx = new TestContext(proxyProperties, false);

        return createDynamicTests(testCasesToRun);
    }

    @TestFactory
    Stream<DynamicTest> proxyTestSuiteAbortingDummyProxy() {
        // tests using dummy proxy
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> testCase instanceof UsingAbortingServerProxy)
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SERVER_PORT, valueOf(PROXY_PORT));
        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");
        ctx = new TestContext(proxyProperties, false);

        return createDynamicTests(testCasesToRun);
    }

    @Test
    void serverProxyConnectionRefused() throws Exception {
        MessageTestCase testCase = new MessageTestCase() {
            {
                requestFileName = "getstate.query";
            }

            @Override
            protected void validateFaultResponse(Message receivedResponse) {
                assertErrorCode(SERVER_CLIENTPROXY_X, X_NETWORK_ERROR);
            }
        };

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, valueOf(PROXY_PORT));

        ctx = new TestContext(proxyProperties, false);

        assertTrue(testCase.execute(ctx));
    }

    @TestFactory
    Stream<DynamicTest> proxyTestSuiteSslTestCases() {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> testCase instanceof SslMessageTestCase)
                .filter(testCase -> !(testCase instanceof UsingDummyServerProxy))
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, valueOf(PROXY_PORT));

        ctx = new TestContext(proxyProperties);
        return createDynamicTests(testCasesToRun);
    }

    @TestFactory
    Stream<DynamicTest> proxyTestSuiteIsolatedSslTestCases() {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> testCase instanceof IsolatedSslMessageTestCase)
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");
        System.setProperty(SystemProperties.PROXY_SERVER_PORT, valueOf(PROXY_PORT));

        ctx = new TestContext(proxyProperties);
        return createDynamicTests(testCasesToRun);
    }

    private Stream<DynamicTest> createDynamicTests(List<MessageTestCase> testCasesToRun) {
        return testCasesToRun.stream()
                .map(testCase -> dynamicTest(testCase.getId(),
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                            try {
                                assertTrue(testCase.execute(ctx));
                            } finally {
                                if (ctx.serverProxy != null) {
                                    ctx.serverProxy.closeIdleConnections();
                                }
                            }
                        })));
    }

    @AfterEach
    void afterEach() {
        ctx.destroy();
        ctx = null;
    }

}
