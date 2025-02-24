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

package org.niis.xroad.proxy.core.addon.proxymonitor;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.niis.xroad.common.properties.ConfigUtils;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.monitor.rpc.EnvMonitorRpcChannelProperties;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.addon.BindableServiceRegistry;
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

import static ee.ria.xroad.common.TestPortUtils.findRandomPort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class ProxyMonitorMetaserviceTest {

    public static EnvMonitorRpcChannelProperties envMonitorRpcChannelProperties = ConfigUtils.initConfiguration(
            EnvMonitorRpcChannelProperties.class,
            Map.of(EnvMonitorRpcChannelProperties.PREFIX + ".port", String.valueOf(findRandomPort())));

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        System.setProperty("test.queries.dir", "src/test/queries");

        Map<String, String> proxyProps = new HashMap<>();
        proxyProps.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        proxyProps.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");

        ProxyTestSuiteHelper.setPropsIfNotSet(proxyProps);

        ProxyTestSuiteHelper.proxyProperties = ConfigUtils.initConfiguration(ProxyProperties.class, proxyProps);

        ProxyTestSuiteHelper.startTestServices();
        MonitorRpcClient monitorRpcClient = new MonitorRpcClient(new RpcChannelFactory(new InsecureRpcCredentialsConfigurer()),
                envMonitorRpcChannelProperties);
        monitorRpcClient.init();
        new ProxyMonitor().init(new BindableServiceRegistry(), monitorRpcClient);
    }

    @AfterAll
    static void afterAll() {
        ProxyTestSuiteHelper.destroyTestServices();
    }

    TestContext ctx;

    @TestFactory
    Stream<DynamicTest> proxyMonitorTests() throws Exception {
        List<MessageTestCase> testCasesToRun = TestcaseLoader.getAllTestCases(getClass().getPackageName() + ".testcases.");
        assertThat(testCasesToRun.size()).isGreaterThan(0);

        System.setProperty(SystemProperties.PROXY_SSL_SUPPORT, "false");
        ctx = new TestContext(ProxyTestSuiteHelper.proxyProperties);

        return testCasesToRun.stream()
                .map(testCase -> dynamicTest(testCase.getId(),
                        () -> assertTimeoutPreemptively(Duration.ofSeconds(6000), () -> {
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
