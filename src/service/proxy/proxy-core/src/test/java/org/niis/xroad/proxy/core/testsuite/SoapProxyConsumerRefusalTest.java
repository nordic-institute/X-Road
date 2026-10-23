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

package org.niis.xroad.proxy.core.testsuite;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestContext;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;

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
import static org.niis.xroad.common.core.exception.ErrorCode.LEGACY_CALL_REFUSED;

/**
 * Proves, through a real client proxy and server proxy, that a consumer whose local setting refuses legacy calls
 * fails an unnegotiated SOAP call itself with a {@code client.legacy_call_refused} fault and sends nothing to the provider, while the
 * same call is served when no setting refuses. Also proves that, without a local refusal, the call follows the
 * {@code legacyProtocolDisabled} value of the provider's instance and not that of the consumer's own instance, and
 * that a change of the global configuration applies to the next call. Kept out of {@code testsuite.testcases} so that
 * {@link org.niis.xroad.proxy.core.test.TestcaseLoader} does not scan it.
 */
class SoapProxyConsumerRefusalTest {

    private static final String LEGACY_CALL_REFUSED_FAULT = CLIENT_X + "." + LEGACY_CALL_REFUSED.code();
    private static final String PROVIDER_REFUSAL_FAULT = "server.serverproxy.agreement_token_rejected";
    private static final String PROVIDER_INSTANCE = "EE";
    private static final String OTHER_INSTANCE = "DEV";
    private static final Map<String, String> PROPS = new HashMap<>();
    private static final ProxyTestSuiteHelper PROXY_TEST_SUITE_HELPER = new ProxyTestSuiteHelper();
    private TestContext ctx;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));
        PROPS.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        PROPS.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");
        PROPS.put("xroad.proxy.ssl-enabled", "false");
        PROPS.put("xroad.proxy.dsp-enabled", "false");
        PROXY_TEST_SUITE_HELPER.setPropsIfNotSet(PROPS);
        PROPS.put("xroad.proxy.server-port", valueOf(PROXY_TEST_SUITE_HELPER.proxyPort));
        PROXY_TEST_SUITE_HELPER.commonProperties = new XRoadConfigCommonProperties(XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .overrides(Map.of("xroad.common.temp-files-path", "build/"))
                .build());
    }

    @AfterEach
    void tearDown() {
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.clear();
        if (ctx != null) {
            ctx.destroy();
        }
        PROXY_TEST_SUITE_HELPER.destroyTestServices();
    }

    @AfterAll
    static void afterAll() {
        PROXY_TEST_SUITE_HELPER.destroyTestServices();
    }

    @Test
    void shouldRefuseUnnegotiatedCallBeforeAnythingReachesTheProvider() throws Exception {
        startContext(true);
        var request = new Probe();
        request.execute(ctx);
        assertThat(request.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldServeUnnegotiatedCallWhenNothingRefuses() throws Exception {
        startContext(false);
        var request = new Probe();
        request.execute(ctx);
        assertThat(request.faultCode).isNull();
        assertTrue(request.served);
        assertThat(request.aclChecks.get()).isPositive();
    }

    @Test
    void shouldRefuseCallWhenTheOwnInstanceRefusesAndTheTargetIsInIt() throws Exception {
        startContext(false);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(PROVIDER_INSTANCE, true);

        var request = new Probe();
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldRefuseCallWhenOnlyTheProviderInstanceRefuses() throws Exception {
        startContext(false);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(PROVIDER_INSTANCE, true);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OTHER_INSTANCE, false);

        var request = new Probe().ownedBy(OTHER_INSTANCE);
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldMakeCallWhenOnlyTheConsumerInstanceRefuses() throws Exception {
        startContext(false);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(PROVIDER_INSTANCE, false);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OTHER_INSTANCE, true);

        var request = new Probe().ownedBy(OTHER_INSTANCE);
        request.execute(ctx);

        // the in-process provider shares the consumer's globalconf and refuses on its own side
        assertThat(request.faultCode).isEqualTo(PROVIDER_REFUSAL_FAULT);
    }

    @Test
    void shouldRefuseCallWhenOnlyTheLocalSettingRefuses() throws Exception {
        startContext(true);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(PROVIDER_INSTANCE, false);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OTHER_INSTANCE, false);

        var request = new Probe().ownedBy(OTHER_INSTANCE);
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldMakeCallWithoutRestartWhenTheInstanceValueIsRemoved() throws Exception {
        startContext(false);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(PROVIDER_INSTANCE, true);

        var refused = new Probe();
        refused.execute(ctx);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.remove(PROVIDER_INSTANCE);
        var served = new Probe();
        served.execute(ctx);

        assertThat(refused.faultCode).isEqualTo(LEGACY_CALL_REFUSED_FAULT);
        assertThat(refused.aclChecks).hasValue(0);
        assertThat(served.faultCode).isNull();
        assertTrue(served.served);
        assertThat(served.aclChecks.get()).isPositive();
    }

    private void startContext(boolean localRefusal) throws Exception {
        var props = new HashMap<>(PROPS);
        props.put("xroad.proxy.legacy-protocol-disabled", valueOf(localRefusal));
        PROXY_TEST_SUITE_HELPER.proxyProperties = new ProxyProperties(
                XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).overrides(props).build());
        PROXY_TEST_SUITE_HELPER.startTestServices();
        ctx = new TestContext(PROXY_TEST_SUITE_HELPER);
    }

    private static final class Probe extends MessageTestCase {
        private final AtomicInteger aclChecks = new AtomicInteger();
        private String faultCode;
        private boolean served;
        private String ownInstance = PROVIDER_INSTANCE;

        private Probe() {
            this.requestFileName = "getstate.query";
            this.responseFile = "getstate.answer";
        }

        private Probe ownedBy(String instance) {
            this.ownInstance = instance;
            return this;
        }

        @Override
        protected void startUp() throws Exception {
            super.startUp();
            globalConfProvider.setGlobalConfProvider(new TestSuiteGlobalConf(proxyTestSuiteHelper) {
                @Override
                public String getInstanceIdentifier() {
                    return ownInstance;
                }
            });
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
