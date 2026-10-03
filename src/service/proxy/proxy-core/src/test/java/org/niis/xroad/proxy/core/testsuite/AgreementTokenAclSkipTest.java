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
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.agreementtoken.AgreementTokenGrant;
import org.niis.xroad.common.agreementtoken.AgreementTokenMinter;
import org.niis.xroad.common.agreementtoken.AgreementTokenScope;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static java.lang.String.valueOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;

/**
 * Proves the agreement-token ACL skip through a real request: a SOAP request carries the
 * {@code x-road-agreement-token} transport header only because the client proxy's DSP dependency
 * handed it a token, and the server proxy reads that header off the wrapped request rather than
 * from any test-only shortcut. Kept out of {@code testsuite.testcases} (and so out of
 * {@link org.niis.xroad.proxy.core.test.TestcaseLoader}'s scan) and out of {@link ProxyTests}'
 * shared context, since this is the only scenario in the suite that needs
 * {@code xroad.proxy.dsp-enabled=true} and a stubbed DSP response.
 */
class AgreementTokenAclSkipTest {

    private static final Map<String, String> PROPS = new HashMap<>();
    private static final ProxyTestSuiteHelper PROXY_TEST_SUITE_HELPER = new ProxyTestSuiteHelper();

    private static final ClientId PRODUCER = ClientId.Conf.create("EE", "BUSINESS", "producer");
    private static final ClientId CONSUMER = ClientId.Conf.create("EE", "BUSINESS", "consumer");
    private static final ServiceId GET_STATE = ServiceId.Conf.create(PRODUCER, "getState");
    private static final ServiceId OTHER_SERVICE = ServiceId.Conf.create(PRODUCER, "test");

    private TestContext ctx;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        PROPS.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        PROPS.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");
        PROPS.put("xroad.proxy.ssl-enabled", "false");
        PROPS.put("xroad.proxy.dsp-enabled", "true");

        PROXY_TEST_SUITE_HELPER.setPropsIfNotSet(PROPS);
        PROPS.put("xroad.proxy.server-port", valueOf(PROXY_TEST_SUITE_HELPER.proxyPort));

        PROXY_TEST_SUITE_HELPER.proxyProperties = buildProxyProperties(PROPS);
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

    @BeforeEach
    void setUp() {
        ctx = new TestContext(PROXY_TEST_SUITE_HELPER);
    }

    @AfterEach
    void tearDown() {
        ctx.destroy();
    }

    @Test
    void shouldSkipAclWithAValidAgreementToken() throws Exception {
        stubAssetAccess(mintToken(GET_STATE));

        assertTrue(noAclEntryTestCase().execute(ctx));
    }

    @Test
    void shouldRefuseWithTheDedicatedErrorForATokenNamingAnotherService() throws Exception {
        stubAssetAccess(mintToken(OTHER_SERVICE));

        var testCase = new MessageTestCase() {
            {
                requestFileName = "getstate.query";
            }

            @Override
            public boolean isQueryAllowed(ClientId sender, ServiceId service) {
                // A rejected token must be refused even though the ACL itself would allow the call.
                return true;
            }

            @Override
            protected void validateFaultResponse(Message receivedResponse) {
                assertErrorCode(SERVER_SERVERPROXY_X, AGREEMENT_TOKEN_REJECTED.code());
            }
        };

        assertTrue(testCase.execute(ctx));
    }

    @Test
    void shouldRefuseWithTheDedicatedErrorForAnExpiredToken() throws Exception {
        stubAssetAccess(mintExpiredToken(GET_STATE));

        var testCase = new MessageTestCase() {
            {
                requestFileName = "getstate.query";
            }

            @Override
            public boolean isQueryAllowed(ClientId sender, ServiceId service) {
                // A rejected token must be refused even though the ACL itself would allow the call.
                return true;
            }

            @Override
            protected void validateFaultResponse(Message receivedResponse) {
                assertErrorCode(SERVER_SERVERPROXY_X, AGREEMENT_TOKEN_REJECTED.code());
            }
        };

        assertTrue(testCase.execute(ctx));
    }

    private MessageTestCase noAclEntryTestCase() {
        return new MessageTestCase() {
            {
                requestFileName = "getstate.query";
                responseFile = "getstate.answer";
            }

            @Override
            public boolean isQueryAllowed(ClientId sender, ServiceId service) {
                return false;
            }

            @Override
            protected void validateNormalResponse(Message receivedResponse) {
                assertTrue(receivedResponse.isResponse());
            }
        };
    }

    private String mintToken(ServiceId service) {
        return new AgreementTokenMinter(ctx.getAgreementTokenKeyProvider(), ctx.getAgreementTokenProperties())
                .mint(new AgreementTokenGrant("agreement-1", CONSUMER, service, List.of(new AgreementTokenScope("*", "**"))));
    }

    private String mintExpiredToken(ServiceId service) {
        var pastMinter = new AgreementTokenMinter(ctx.getAgreementTokenKeyProvider(), ctx.getAgreementTokenProperties(),
                Clock.fixed(Instant.now().minusSeconds(120), ZoneOffset.UTC));
        return pastMinter.mint(new AgreementTokenGrant("agreement-1", CONSUMER, service, List.of(new AgreementTokenScope("*", "**"))));
    }

    private void stubAssetAccess(String token) {
        var endpoint = "http://127.0.0.1:" + PROXY_TEST_SUITE_HELPER.proxyProperties.serverProxyPort() + "/";
        when(ctx.getConsumerSideDspProcessor().execute(argThat(req -> GET_STATE.equals(req.serviceId()))))
                .thenReturn(new AssetAccessResponse(endpoint, token));
    }

    private static ProxyProperties buildProxyProperties(Map<String, String> overrides) {
        return new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(overrides)
                .build());
    }
}
