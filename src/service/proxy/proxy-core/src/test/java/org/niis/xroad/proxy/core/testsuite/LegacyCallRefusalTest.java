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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapFault;
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
import org.niis.xroad.monitor.common.MetricsGroup;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.test.Message;
import org.niis.xroad.proxy.core.test.MessageTestCase;
import org.niis.xroad.proxy.core.test.ProxyTestSuiteHelper;
import org.niis.xroad.proxy.core.test.TestContext;
import org.niis.xroad.proxy.core.test.TestSuiteServerConf;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static java.lang.String.valueOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;

/**
 * Proves, through a real client proxy and server proxy, that a provider which refuses legacy calls rejects every
 * request without an agreement token and still serves requests that carry a valid one. A request is tokenless
 * because the consumer-side DSP dependency of the client proxy is left unstubbed and so hands out no token. The
 * instance value is published through {@link ProxyTestSuiteHelper#legacyProtocolDisabledByInstance}. Kept out of
 * {@code testsuite.testcases} so that {@link org.niis.xroad.proxy.core.test.TestcaseLoader} does not scan it.
 */
class LegacyCallRefusalTest {

    private static final String OWN_INSTANCE = "EE";
    private static final String TOKEN_REJECTED_FAULT = SERVER_SERVERPROXY_X + "." + AGREEMENT_TOKEN_REJECTED.code();

    private static final Map<String, String> PROPS = new HashMap<>();
    private static final ProxyTestSuiteHelper PROXY_TEST_SUITE_HELPER = new ProxyTestSuiteHelper();

    private static final ClientId PRODUCER = ClientId.Conf.create("EE", "BUSINESS", "producer");
    private static final ClientId CONSUMER = ClientId.Conf.create("EE", "BUSINESS", "consumer");
    private static final ServiceId GET_STATE = ServiceId.Conf.create(PRODUCER, "getState");
    private static final ServiceId LIST_METHODS = ServiceId.Conf.create(
            ClientId.Conf.create("EE", "BUSINESS", "producer", "SUB"), "listMethods", "v1");
    private static final ServiceId GET_METRICS = ServiceId.Conf.create(PRODUCER, "getSecurityServerMetrics");
    private static final ServiceId GET_OPERATIONAL_DATA = ServiceId.Conf.create(PRODUCER, "getSecurityServerOperationalData");

    private TestContext ctx;

    @BeforeAll
    static void beforeAll() throws Exception {
        TimeUtils.setClock(Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC));

        PROPS.put("xroad.proxy.server.jetty-configuration-file", "src/test/serverproxy.xml");
        PROPS.put("xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml");
        PROPS.put("xroad.proxy.ssl-enabled", "false");
        PROPS.put("xroad.proxy.dsp-enabled", "true");
        PROPS.put("xroad.proxy.addon.op-monitor.enabled", "true");

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
    void shouldRefuseTokenlessRequestWithoutConsultingTheAclWhenTheInstanceRefuses() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);

        var request = new Probe("getstate.query", "getstate.answer");
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(TOKEN_REJECTED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldRefuseTokenlessRequestWhenOnlyTheLocalSettingRefuses() throws Exception {
        ctx = new TestContext(PROXY_TEST_SUITE_HELPER, true, mock(MonitorRpcClient.class), null, null,
                buildProxyProperties(withLocalRefusal()));

        var request = new Probe("getstate.query", "getstate.answer");
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(TOKEN_REJECTED_FAULT);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldServeTokenlessRequestAfterTheAclCheckWhenNothingRefuses() throws Exception {
        startContext();

        var request = new Probe("getstate.query", "getstate.answer");
        request.execute(ctx);

        assertThat(request.faultCode).isNull();
        assertTrue(request.served);
        assertThat(request.aclChecks.get()).isPositive();
    }

    @Test
    void shouldRestoreTheAclCheckWithoutRestartWhenTheInstanceValueIsRemoved() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);

        var refused = new Probe("getstate.query", "getstate.answer");
        refused.execute(ctx);
        ctx.serverProxy.closeIdleConnections();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.remove(OWN_INSTANCE);
        var served = new Probe("getstate.query", "getstate.answer");
        served.execute(ctx);

        assertThat(refused.faultCode).isEqualTo(TOKEN_REJECTED_FAULT);
        assertThat(refused.aclChecks).hasValue(0);
        assertThat(served.faultCode).isNull();
        assertTrue(served.served);
        assertThat(served.aclChecks.get()).isPositive();
    }

    @Test
    void shouldServeRequestWithAValidTokenWhenTheInstanceRefuses() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);
        stubAssetAccess(CONSUMER, GET_STATE);

        var request = new Probe("getstate.query", "getstate.answer");
        request.execute(ctx);

        assertThat(request.faultCode).isNull();
        assertTrue(request.served);
        assertThat(request.aclChecks).hasValue(0);
    }

    @Test
    void shouldRefuseTokenlessMetaserviceRequestWhenTheInstanceRefuses() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);

        var request = new Probe("listMethods.query", null);
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(TOKEN_REJECTED_FAULT);
    }

    @Test
    void shouldServeMetaserviceRequestWithAValidTokenWhenTheInstanceRefuses() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);
        stubAssetAccess(CONSUMER, LIST_METHODS);

        var request = new Probe("listMethods.query", null);
        request.execute(ctx);

        assertThat(request.faultCode).isNull();
        assertTrue(request.served);
    }

    @Test
    void shouldRefuseTokenlessProxyMonitoringRequestWhenTheInstanceRefuses() throws Exception {
        startContext(monitorReturningMetrics());
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);

        var request = new Probe("getMetrics.query", null).ownedBy(PRODUCER);
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(TOKEN_REJECTED_FAULT);
    }

    @Test
    void shouldServeProxyMonitoringRequestWithAValidTokenWhenTheInstanceRefuses() throws Exception {
        startContext(monitorReturningMetrics());
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);
        stubAssetAccess(PRODUCER, GET_METRICS);

        var request = new Probe("getMetrics.query", null).ownedBy(PRODUCER);
        request.execute(ctx);

        assertThat(request.faultCode).isNull();
        assertTrue(request.served);
    }

    @Test
    void shouldRefuseTokenlessOperationalMonitoringRequestWhenTheInstanceRefuses() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);

        var request = new Probe("getSecurityServerOperationalData.query", null).ownedBy(PRODUCER);
        request.execute(ctx);

        assertThat(request.faultCode).isEqualTo(TOKEN_REJECTED_FAULT);
    }

    @Test
    void shouldNotRefuseOperationalMonitoringRequestWithAValidTokenWhenTheInstanceRefuses() throws Exception {
        startContext();
        PROXY_TEST_SUITE_HELPER.legacyProtocolDisabledByInstance.put(OWN_INSTANCE, true);
        stubAssetAccess(PRODUCER, GET_OPERATIONAL_DATA);

        var request = new Probe("getSecurityServerOperationalData.query", null).ownedBy(PRODUCER);
        request.execute(ctx);

        assertThat(request.faultCode).isNotEqualTo(TOKEN_REJECTED_FAULT);
    }

    private void startContext() {
        startContext(mock(MonitorRpcClient.class));
    }

    private void startContext(MonitorRpcClient monitorRpcClient) {
        ctx = new TestContext(PROXY_TEST_SUITE_HELPER, true, monitorRpcClient);
    }

    private static MonitorRpcClient monitorReturningMetrics() {
        var monitor = mock(MonitorRpcClient.class);
        when(monitor.getMetrics(anyList(), anyBoolean())).thenReturn(MetricsGroup.newBuilder().setName("metrics").build());
        return monitor;
    }

    private String mintToken(ClientId client, ServiceId service) {
        return new AgreementTokenMinter(ctx.getAgreementTokenKeyProvider(), ctx.getAgreementTokenProperties())
                .mint(new AgreementTokenGrant("agreement-1", client, service, List.of(new AgreementTokenScope("*", "**"))));
    }

    private void stubAssetAccess(ClientId client, ServiceId service) {
        var endpoint = "http://127.0.0.1:" + PROXY_TEST_SUITE_HELPER.proxyProperties.serverProxyPort() + "/";
        var response = new AssetAccessResponse(endpoint, mintToken(client, service));
        when(ctx.getConsumerSideDspProcessor().execute(argThat(req -> service.equals(req.serviceId()))))
                .thenReturn(response);
    }

    private static Map<String, String> withLocalRefusal() {
        var props = new HashMap<>(PROPS);
        props.put("xroad.proxy.legacy-protocol-disabled", "true");
        return props;
    }

    private static ProxyProperties buildProxyProperties(Map<String, String> overrides) {
        return new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(overrides)
                .build());
    }

    private static final class Probe extends MessageTestCase {
        private final AtomicInteger aclChecks = new AtomicInteger();
        private ClientId owner;
        private String faultCode;
        private boolean served;

        private Probe(String requestFileName, String responseFile) {
            this.requestFileName = requestFileName;
            this.responseFile = responseFile;
        }

        private Probe ownedBy(ClientId serverOwner) {
            this.owner = serverOwner;
            return this;
        }

        @Override
        public boolean isQueryAllowed(ClientId sender, ServiceId service) {
            aclChecks.incrementAndGet();
            return true;
        }

        @Override
        protected void startUp() throws Exception {
            super.startUp();
            if (owner != null) {
                serverConfProvider.setServerConfProvider(new TestSuiteServerConf(proxyTestSuiteHelper) {
                    @Override
                    public SecurityServerId.Conf getIdentifier() {
                        return SecurityServerId.Conf.create(owner, "ownerServer");
                    }
                });
            }
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
