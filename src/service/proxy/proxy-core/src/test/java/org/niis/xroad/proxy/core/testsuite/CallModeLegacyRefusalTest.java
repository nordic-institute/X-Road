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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.ErrorCodes.CLIENT_X;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.common.core.exception.ErrorCode.LEGACY_CALL_REFUSED;

/**
 * Proves, through a real client proxy and server proxy, that the client proxy refuses an unnegotiated call itself
 * when the instance or the local setting refuses legacy calls, and that no connection to the server proxy is made.
 * The called DID is not published and DSP is left at its default (enabled), so the refusal comes from the call-mode
 * selection wired into the client proxy. Kept out of {@code testsuite.testcases} so that
 * {@link org.niis.xroad.proxy.core.test.TestcaseLoader} does not scan it.
 */
class CallModeLegacyRefusalTest {

    private static final String LEGACY_CALL_REFUSED_FAULT = CLIENT_X + "." + LEGACY_CALL_REFUSED.code();
    private static final String PROVIDER_INSTANCE = "EE";

    private static final Map<String, String> PROPS = new HashMap<>();
    private static final ProxyTestSuiteHelper PROXY_TEST_SUITE_HELPER = new ProxyTestSuiteHelper();

    private final AtomicInteger serverProxyConnections = new AtomicInteger();
    private TestContext ctx;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        PROPS.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        PROPS.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");
        PROPS.put("xroad.proxy.ssl-enabled", "true");

        PROXY_TEST_SUITE_HELPER.setPropsIfNotSet(PROPS);
        PROPS.put("xroad.proxy.server-port", valueOf(PROXY_TEST_SUITE_HELPER.proxyPort));

        PROXY_TEST_SUITE_HELPER.commonProperties = new XRoadConfigCommonProperties(XRoadConfigBuilder.create()
                .overrides(Map.of("xroad.common.temp-files-path", "build/"))
                .build());
        PROXY_TEST_SUITE_HELPER.proxyProperties = buildProxyProperties(PROPS);
        PROXY_TEST_SUITE_HELPER.startTestServices();
    }

    @AfterAll
    static void afterAll() {
        PROXY_TEST_SUITE_HELPER.destroyTestServices();
    }

    @BeforeEach
    void setUp() {
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.clear();
    }

    @AfterEach
    void tearDown() {
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.clear();
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @Test
    void shouldRefuseUnpublishedCallBeforeConnectingWhenTheInstanceRefuses() throws Exception {
        startContext(PROPS);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(PROVIDER_INSTANCE, true);

        var request = new Probe();
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
        assertThat(serverProxyConnections).hasValue(0);
    }

    @Test
    void shouldRefuseUnpublishedCallBeforeConnectingWhenOnlyTheLocalSettingRefuses() throws Exception {
        var props = new HashMap<>(PROPS);
        props.put("xroad.proxy.legacy-protocol-disabled", "true");
        startContext(props);

        var request = new Probe();
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
        assertThat(serverProxyConnections).hasValue(0);
    }

    @Test
    void shouldMakeUnpublishedCallWhenNothingRefuses() throws Exception {
        startContext(PROPS);

        var request = new Probe();
        request.execute(ctx);

        assertThat(request.faultCode).isNull();
        assertTrue(request.served);
        assertThat(request.aclChecks.get()).isPositive();
        assertThat(serverProxyConnections.get()).isPositive();
    }

    private void startContext(Map<String, String> props) {
        PROXY_TEST_SUITE_HELPER.proxyProperties = buildProxyProperties(props);
        ctx = new TestContext(PROXY_TEST_SUITE_HELPER, true, mock(MonitorRpcClient.class), null,
                serverProxyConnections::incrementAndGet);
        ctx.routeThroughCallModeSelection();
    }

    private static ProxyProperties buildProxyProperties(Map<String, String> overrides) {
        return new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(overrides)
                .build());
    }

    private static final class Probe extends MessageTestCase {
        private final AtomicInteger aclChecks = new AtomicInteger();
        private String faultCode;
        private boolean served;

        private Probe() {
            this.requestFileName = "getstate.query";
            this.responseFile = "getstate.answer";
        }

        @Override
        public boolean isQueryAllowed(ClientId sender, ServiceId service) {
            aclChecks.incrementAndGet();
            return true;
        }

        @Override
        protected void validateNormalResponse(Message response) {
            served = true;
        }

        @Override
        protected void validateFaultResponse(Message response) {
            faultCode = ((SoapFault) response.getSoap()).getCode();
        }
    }
}
