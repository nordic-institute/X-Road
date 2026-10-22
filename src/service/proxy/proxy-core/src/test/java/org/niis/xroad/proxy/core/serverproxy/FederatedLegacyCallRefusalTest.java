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
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapMessageImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;

class FederatedLegacyCallRefusalTest {

    private static final String OWN_INSTANCE = "XRD8";
    private static final String CALLER_INSTANCE = "DEV";
    private static final ClientId.Conf CALLER = ClientId.Conf.create(CALLER_INSTANCE, "COM", "222", "TESTCLIENT");
    private static final ServiceId.Conf SERVICE =
            ServiceId.Conf.create(ClientId.Conf.create(OWN_INSTANCE, "COM", "111", "PROVIDER"), "getData", "v1");
    private static final String REQUEST_PATH = "/foo/bar";

    private final GlobalConfProvider globalConfProvider = mock(GlobalConfProvider.class);
    private final ServerConfProvider serverConfProvider = mock(ServerConfProvider.class);
    private final AgreementTokenAccessCheck agreementTokenAccessCheck = mock(AgreementTokenAccessCheck.class);
    private final ProxyProperties proxyProperties = mock(ProxyProperties.class);
    private final LegacyCallRefusalPolicy policy = new LegacyCallRefusalPolicy(proxyProperties, globalConfProvider);

    private final ServerRestMessageProcessor restProcessor = new ServerRestMessageProcessor(
            mock(MessageSigningService.class),
            mock(ClientVerificationService.class),
            mock(OpMonitoringDataHelper.class),
            globalConfProvider,
            serverConfProvider,
            proxyProperties,
            mock(CommonProperties.class),
            mock(OcspVerifierFactory.class),
            mock(ServiceHandlerLoader.class),
            mock(IdentifierValidationService.class),
            agreementTokenAccessCheck,
            policy);

    private final ServerSoapMessageProcessor soapProcessor = new ServerSoapMessageProcessor(
            mock(MessageSigningService.class),
            mock(ClientVerificationService.class),
            mock(OpMonitoringDataHelper.class),
            globalConfProvider,
            serverConfProvider,
            proxyProperties,
            mock(CommonProperties.class),
            mock(OcspVerifierFactory.class),
            mock(ServiceHandlerLoader.class),
            mock(IdentifierValidationService.class),
            agreementTokenAccessCheck,
            policy);

    @BeforeEach
    void setUp() {
        when(proxyProperties.legacyProtocolDisabled()).thenReturn(false);
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(OWN_INSTANCE);
        when(serverConfProvider.serviceExists(SERVICE)).thenReturn(true);
        when(serverConfProvider.isQueryAllowed(any(), any())).thenReturn(true);
        when(serverConfProvider.isQueryAllowed(any(), any(), any(), any())).thenReturn(true);
        when(agreementTokenAccessCheck.decide(any(), any(), any()))
                .thenReturn(new AgreementTokenAccessCheck.Decision.Absent());
        when(agreementTokenAccessCheck.decide(any(), any(), any(), any(), any()))
                .thenReturn(new AgreementTokenAccessCheck.Decision.Absent());
    }

    @ParameterizedTest(name = "own instance={0}, caller instance={1} -> refused={2}")
    @CsvSource({
            "true,  false, true",
            "false, true,  false",
            "false, false, false"
    })
    void restRefusalFollowsTheProvidersOwnInstance(boolean ownInstance, boolean callerInstance, boolean refused) {
        publish(ownInstance, callerInstance);
        var requestMessage = restRequestMessage();

        if (refused) {
            assertThatThrownBy(() -> restProcessor.verifyAccess(SERVICE, requestMessage, null))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(e -> assertThat(((XrdRuntimeException) e).isCausedBy(AGREEMENT_TOKEN_REJECTED)).isTrue())
                    .hasMessageContaining("AGREEMENT_TOKEN_REQUIRED");
            verify(serverConfProvider, never()).isQueryAllowed(any(), any(), any(), any());
        } else {
            restProcessor.verifyAccess(SERVICE, requestMessage, null);
            verify(serverConfProvider).isQueryAllowed(CALLER, SERVICE, "GET", REQUEST_PATH);
        }
    }

    @ParameterizedTest(name = "own instance={0}, caller instance={1} -> refused={2}")
    @CsvSource({
            "true,  false, true",
            "false, true,  false",
            "false, false, false"
    })
    void soapRefusalFollowsTheProvidersOwnInstance(boolean ownInstance, boolean callerInstance, boolean refused)
            throws Exception {
        publish(ownInstance, callerInstance);
        var requestMessage = soapRequestMessage();

        if (refused) {
            assertThatThrownBy(() -> soapProcessor.verifyAccess(requestMessage, SERVICE, null))
                    .isInstanceOf(XrdRuntimeException.class)
                    .satisfies(e -> assertThat(((XrdRuntimeException) e).isCausedBy(AGREEMENT_TOKEN_REJECTED)).isTrue())
                    .hasMessageContaining("AGREEMENT_TOKEN_REQUIRED");
            verify(serverConfProvider, never()).isQueryAllowed(any(), any());
        } else {
            soapProcessor.verifyAccess(requestMessage, SERVICE, null);
            verify(serverConfProvider).isQueryAllowed(CALLER, SERVICE);
        }
    }

    private void publish(boolean ownInstance, boolean callerInstance) {
        when(globalConfProvider.isLegacyProtocolDisabled(OWN_INSTANCE)).thenReturn(ownInstance);
        when(globalConfProvider.isLegacyProtocolDisabled(CALLER_INSTANCE)).thenReturn(callerInstance);
    }

    private static ServerRestMessageProcessor.VerifyingProxyMessage restRequestMessage() {
        var rest = mock(RestRequest.class);
        when(rest.getClientId()).thenReturn(CALLER);
        when(rest.getVerb()).thenReturn(RestRequest.Verb.GET);
        when(rest.getServicePath()).thenReturn(REQUEST_PATH);

        var requestMessage = mock(ServerRestMessageProcessor.VerifyingProxyMessage.class);
        when(requestMessage.getRest()).thenReturn(rest);
        return requestMessage;
    }

    private static ProxyMessage soapRequestMessage() throws Exception {
        var soapMessage = mock(SoapMessageImpl.class);
        when(soapMessage.getClient()).thenReturn(CALLER);

        var requestMessage = new ProxyMessage(null, null);
        requestMessage.soap(soapMessage, Map.of());
        return requestMessage;
    }
}
