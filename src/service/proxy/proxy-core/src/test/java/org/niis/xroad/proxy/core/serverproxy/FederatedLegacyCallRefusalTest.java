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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;

/**
 * Proves, through a real client proxy and server proxy, that a provider decides whether to refuse a tokenless call
 * by the {@code legacyProtocolDisabled} value of its own instance, never by the value of the caller's instance. The
 * test global configuration names the provider's own instance {@value #OWN_INSTANCE} while the caller belongs to
 * {@value #CALLER_INSTANCE}, and publishes the value per instance through
 * {@link ProxyTestSuiteHelper#legacyProtocolDisabledByInstance}.
 */
class FederatedLegacyCallRefusalTest {

    private static final String OWN_INSTANCE = "DEV";
    private static final String CALLER_INSTANCE = "EE";
    private static final String AGREEMENT_TOKEN_REJECTED_FAULT = SERVER_SERVERPROXY_X + "." + AGREEMENT_TOKEN_REJECTED.code();

    private static final ProxyTestSuiteHelper PROXY_TEST_SUITE_HELPER = new ProxyTestSuiteHelper();

    private TestContext ctx;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        Map<String, String> props = new HashMap<>();
        props.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        props.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");
        props.put("xroad.proxy.ssl-enabled", "false");

        PROXY_TEST_SUITE_HELPER.setPropsIfNotSet(props);
        props.put("xroad.proxy.server-port", valueOf(PROXY_TEST_SUITE_HELPER.proxyPort));

        PROXY_TEST_SUITE_HELPER.proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(props)
                .build());
        PROXY_TEST_SUITE_HELPER.commonProperties = new XRoadConfigCommonProperties(XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .overrides(Map.of("xroad.common.temp-files-path", "build/"))
                .build());
        PROXY_TEST_SUITE_HELPER.startTestServices();
    }

    @AfterAll
    static void afterAll() {
        PROXY_TEST_SUITE_HELPER.destroyTestServices();
    }

    @AfterEach
    void tearDown() {
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.clear();
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @ParameterizedTest(name = "own instance refuses={0}, caller instance refuses={1} -> refused={2}")
    @CsvSource({
            "true,  false, true",
            "false, true,  false",
            "false, false, false"
    })
    void refusalFollowsTheProvidersOwnInstance(boolean ownInstanceRefuses, boolean callerInstanceRefuses, boolean refused)
            throws Exception {
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, ownInstanceRefuses);
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(CALLER_INSTANCE, callerInstanceRefuses);
        ctx = new TestContext(PROXY_TEST_SUITE_HELPER);

        var request = new FederatedProbe();
        request.execute(ctx);

        if (refused) {
            assertThat(request.faultCode).isEqualTo(AGREEMENT_TOKEN_REJECTED_FAULT);
            assertThat(request.aclChecks).hasValue(0);
        } else {
            assertThat(request.faultCode).isNull();
            assertThat(request.served).isTrue();
            assertThat(request.aclChecks.get()).isPositive();
        }
    }

    private static final class FederatedProbe extends MessageTestCase {
        private final AtomicInteger aclChecks = new AtomicInteger();
        private String faultCode;
        private boolean served;

        private FederatedProbe() {
            this.requestFileName = "getstate.query";
            this.responseFile = "getstate.answer";
        }

        @Override
        public boolean isQueryAllowed(ClientId sender, ServiceId service) {
            assertThat(sender.getXRoadInstance()).isEqualTo(CALLER_INSTANCE);
            aclChecks.incrementAndGet();
            return true;
        }

        @Override
        protected void startUp() throws Exception {
            super.startUp();
            globalConfProvider.setGlobalConfProvider(new TestSuiteGlobalConf(proxyTestSuiteHelper) {
                @Override
                public String getInstanceIdentifier() {
                    return OWN_INSTANCE;
                }
            });
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
