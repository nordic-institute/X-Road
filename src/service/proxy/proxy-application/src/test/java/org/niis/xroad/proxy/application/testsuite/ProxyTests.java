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

package org.niis.xroad.proxy.application.testsuite;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class ProxyTests {

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        System.setProperty("xroad.proxy.jetty-serverproxy-configuration-file", "src/test/serverproxy.xml");
        System.setProperty("xroad.proxy.jetty-ocsp-responder-configuration-file", "src/test/ocsp-responder.xml");
        System.setProperty("xroad.proxy.jetty-clientproxy-configuration-file", "src/test/clientproxy.xml");
        System.setProperty("logback.configurationFile", "src/test/logback-proxytest.xml");

        ProxyTestSuiteHelper.setPropsIfNotSet();

        System.setProperty(SystemProperties.DATABASE_PROPERTIES, "src/test/resources/hibernate.properties");

        ProxyTestSuiteHelper.startTestServices();
        ProxyTestSuiteHelper.startDummyProxies();
    }

    @AfterAll
    static void afterAll() {
        ProxyTestSuiteHelper.destroyTestServices();
    }

    TestContext ctx;

    @TestFactory
    Stream<DynamicTest> proxyTestSuite_nonSslTestCases() {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> !(testCase instanceof SslMessageTestCase))
                .filter(testCase -> !(testCase instanceof IsolatedSslMessageTestCase))
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");
        ctx = new TestContext();

        return createDynamicTests(testCasesToRun);
    }

    @TestFactory
    Stream<DynamicTest> proxyTestSuite_SslTestCases() {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> testCase instanceof SslMessageTestCase)
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");
        ctx = new TestContext();
        return createDynamicTests(testCasesToRun);
    }

    @TestFactory
    Stream<DynamicTest> proxyTestSuite_IsolatedSslTestCases() {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getTestCasesToRun(new String[]{}).stream()
                .filter(testCase -> testCase instanceof IsolatedSslMessageTestCase)
                .toList();
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "true");
        ctx = new TestContext();
        return createDynamicTests(testCasesToRun);
    }

    private Stream<DynamicTest> createDynamicTests(List<MessageTestCase> testCasesToRun) {
        return testCasesToRun.stream()
                .map(testCase -> dynamicTest(testCase.getId(),
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
                            try {
                                assertTrue(testCase.execute(ctx));
                            } finally {
                                ctx.serverProxy.closeIdleConnections();
                            }
                        })));
    }

    @AfterEach
    void afterEach() {
        ctx.destroy();
        ctx = null;
    }

}
