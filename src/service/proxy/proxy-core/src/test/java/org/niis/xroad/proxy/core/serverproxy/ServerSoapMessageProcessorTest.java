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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
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

class ServerSoapMessageProcessorTest {

    private static final ClientId.Conf CLIENT = ClientId.Conf.create("DEV", "COM", "222", "TESTCLIENT");
    private static final ServiceId.Conf SERVICE =
            ServiceId.Conf.create(ClientId.Conf.create("DEV", "COM", "222", "PROVIDER"), "getData", "v1");
    private static final String AGREEMENT_TOKEN = "token-abc-123";

    private final ServerConfProvider serverConfProvider = mock(ServerConfProvider.class);
    private final AgreementTokenAccessCheck agreementTokenAccessCheck = mock(AgreementTokenAccessCheck.class);

    private final ServerSoapMessageProcessor processor = new ServerSoapMessageProcessor(
            mock(MessageSigningService.class),
            mock(ClientVerificationService.class),
            mock(OpMonitoringDataHelper.class),
            mock(GlobalConfProvider.class),
            serverConfProvider,
            mock(ProxyProperties.class),
            mock(CommonProperties.class),
            mock(OcspVerifierFactory.class),
            mock(ServiceHandlerLoader.class),
            mock(IdentifierValidationService.class),
            agreementTokenAccessCheck);

    @Test
    void shouldSkipAclWhenAgreementTokenIsAccepted() throws Exception {
        when(serverConfProvider.serviceExists(SERVICE)).thenReturn(true);
        when(agreementTokenAccessCheck.decide(AGREEMENT_TOKEN, CLIENT, SERVICE))
                .thenReturn(new AgreementTokenAccessCheck.Decision.Accepted());

        processor.verifyAccess(requestMessageFor(CLIENT), SERVICE, AGREEMENT_TOKEN);

        verify(serverConfProvider, never()).isQueryAllowed(any(), any());
    }

    @Test
    void shouldConsultAclWhenNoTokenIsPresent() throws Exception {
        when(serverConfProvider.serviceExists(SERVICE)).thenReturn(true);
        when(agreementTokenAccessCheck.decide(null, CLIENT, SERVICE)).thenReturn(new AgreementTokenAccessCheck.Decision.Absent());
        when(serverConfProvider.isQueryAllowed(CLIENT, SERVICE)).thenReturn(true);

        processor.verifyAccess(requestMessageFor(CLIENT), SERVICE, null);

        verify(serverConfProvider).isQueryAllowed(CLIENT, SERVICE);
    }

    @Test
    void shouldConsultAclWhenKeyMaterialIsUnavailable() throws Exception {
        when(serverConfProvider.serviceExists(SERVICE)).thenReturn(true);
        when(agreementTokenAccessCheck.decide(AGREEMENT_TOKEN, CLIENT, SERVICE))
                .thenReturn(new AgreementTokenAccessCheck.Decision.Unavailable());
        when(serverConfProvider.isQueryAllowed(CLIENT, SERVICE)).thenReturn(true);

        processor.verifyAccess(requestMessageFor(CLIENT), SERVICE, AGREEMENT_TOKEN);

        verify(serverConfProvider).isQueryAllowed(CLIENT, SERVICE);
    }

    @Test
    void shouldDenyAccessWhenNeitherTokenNorAclGrantIt() throws Exception {
        when(serverConfProvider.serviceExists(SERVICE)).thenReturn(true);
        when(agreementTokenAccessCheck.decide(null, CLIENT, SERVICE)).thenReturn(new AgreementTokenAccessCheck.Decision.Absent());
        when(serverConfProvider.isQueryAllowed(CLIENT, SERVICE)).thenReturn(false);

        var requestMessage = requestMessageFor(CLIENT);

        assertThatThrownBy(() -> processor.verifyAccess(requestMessage, SERVICE, null))
                .isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void shouldRefuseWithAgreementTokenRejectedWhenTheTokenIsRejectedEvenIfTheAclWouldAllow() throws Exception {
        when(serverConfProvider.serviceExists(SERVICE)).thenReturn(true);
        when(agreementTokenAccessCheck.decide(AGREEMENT_TOKEN, CLIENT, SERVICE))
                .thenReturn(new AgreementTokenAccessCheck.Decision.Rejected("SCOPE_MISMATCH"));

        var requestMessage = requestMessageFor(CLIENT);

        assertThatThrownBy(() -> processor.verifyAccess(requestMessage, SERVICE, AGREEMENT_TOKEN))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).isCausedBy(AGREEMENT_TOKEN_REJECTED)).isTrue());
        verify(serverConfProvider, never()).isQueryAllowed(any(), any());
    }

    private static ProxyMessage requestMessageFor(ClientId.Conf client) throws Exception {
        var soapMessage = mock(SoapMessageImpl.class);
        when(soapMessage.getClient()).thenReturn(client);

        var requestMessage = new ProxyMessage(null, null);
        requestMessage.soap(soapMessage, Map.of());
        return requestMessage;
    }
}
