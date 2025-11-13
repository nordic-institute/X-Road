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
package org.niis.xroad.proxy.core.addon.proxymonitor.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.MimeTypes;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.test.TestSuiteGlobalConf;
import org.niis.xroad.proxy.core.test.TestSuiteServerConf;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;

/**
 * Unit tests for {@link ProxyMonitorServiceHandlerImpl}
 */
public class ProxyMonitorServiceHandlerTest {

    private static final String EXPECTED_XR_INSTANCE = "EE";
    private static final ClientId.Conf DEFAULT_OWNER_CLIENT = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "GOV",
            "1234TEST_CLIENT");

    private static final SecurityServerId.Conf DEFAULT_OWNER_SERVER =
            SecurityServerId.Conf.create(DEFAULT_OWNER_CLIENT, "ownerServer");

    private static final ServiceId.Conf MONITOR_SERVICE_ID = ServiceId.Conf.create(DEFAULT_OWNER_CLIENT,
            ProxyMonitorServiceHandlerImpl.SERVICE_CODE);

    private ServerConfProvider serverConfProvider;
    private GlobalConfProvider globalConfProvider;
    private ProxyMessage mockProxyMessage;

    private final MonitorRpcClient mockMonitorClient = mock(MonitorRpcClient.class);

    /**
     * Init data for tests
     */
    @Before
    public void init() throws IOException {
        serverConfProvider = new TestSuiteServerConf() {
            @Override
            public SecurityServerId.Conf getIdentifier() {
                return DEFAULT_OWNER_SERVER;
            }
        };
        globalConfProvider = new TestSuiteGlobalConf("src/test/resources/");

        mockProxyMessage = mock(ProxyMessage.class);

        when(mockProxyMessage.getSoapContentType()).thenReturn(MimeTypes.TEXT_XML_UTF8);
    }

    @Test
    public void shouldNotBeAbleToHandleWithIncorrectServiceCode() {

        // setup
        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);

        final ServiceId.Conf requestedService = ServiceId.Conf.create(DEFAULT_OWNER_CLIENT, "theWrongService");

        // execution & verification
        assertThat("Should not be able to handle wrong service",
                handlerToTest.canHandle(requestedService, mockProxyMessage), is(false));
    }

    @Test
    public void shouldBeAbleToHandleWithCorrectServiceCode() {

        // setup
        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);

        // execution & verification
        assertThat("Should be able to handle the right service",
                handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage), is(true));
    }

    @Test
    public void shouldNotNeedToVerifyWhenOwnerCalling() {

        // setup

        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);

        final SoapMessageImpl mockSoap = mock(SoapMessageImpl.class);
        when(mockSoap.getClient()).thenReturn(DEFAULT_OWNER_CLIENT);

        when(mockProxyMessage.getSoap()).thenReturn(mockSoap);

        // execution & verification
        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        assertThat("Should not need a verification", handlerToTest.shouldVerifyAccess(), is(false));
    }

    @Test
    public void shouldNotNeedToVerifyWhenAllowedMonitoringClientCalling() {

        // setup

        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);

        // the allowed monitoring client from test resources monitoring metricNames
        final ClientId.Conf allowedClient = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "BUSINESS",
                "producer");

        final SoapMessageImpl mockSoap = mock(SoapMessageImpl.class);
        when(mockSoap.getClient()).thenReturn(allowedClient);

        when(mockProxyMessage.getSoap()).thenReturn(mockSoap);

        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        // execution & verification
        assertThat("Should not need a verification", handlerToTest.shouldVerifyAccess(), is(false));
    }

    @Test
    public void shouldThrowWhenAccessNotAllowed() {

        // setup

        ProxyMonitorServiceHandlerImpl handlerToTest = new ProxyMonitorServiceHandlerImpl(serverConfProvider, globalConfProvider,
                mockMonitorClient);

        // the allowed monitoring client from test resources monitoring metricNames
        final ClientId.Conf nonAllowedClient = ClientId.Conf.create(EXPECTED_XR_INSTANCE, "COM",
                "justSomeClient");

        final SoapMessageImpl mockSoap = mock(SoapMessageImpl.class);
        when(mockSoap.getClient()).thenReturn(nonAllowedClient);

        when(mockProxyMessage.getSoap()).thenReturn(mockSoap);

        handlerToTest.canHandle(MONITOR_SERVICE_ID, mockProxyMessage);

        // execution

        var ce = assertThrows(XrdRuntimeException.class, handlerToTest::shouldVerifyAccess);

        assertEquals(ACCESS_DENIED.code(), ce.getErrorCode());
        assertTrue(ce.getMessage().contains("Request is not allowed"));

        // expecting an exception..
    }

}
