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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import org.eclipse.jetty.http.HttpFields;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class ClientSoapMessageProcessorTest {

    private static final ClientId MANAGEMENT_CLIENT =
            ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
    private static final ClientId OTHER_CLIENT =
            ClientId.Conf.create("DEV", "COM", "4321", "TestClient");

    private static final String SOAP_REQUEST = "src/test/queries/simple.query";
    private static final Map<String, String> SSL_DISABLED = Map.of("xroad.proxy.ssl-enabled", "false");

    private final ClientRequestPreparationService clientRequestPreparationService =
            mock(ClientRequestPreparationService.class);
    private final DspRequestProcessor consumerSideDspProcessor = mock(DspRequestProcessor.class);

    @Test
    void isManagementRequestReturnsTrueWhenServiceIdMatchesManagementSubsystem() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(MANAGEMENT_CLIENT, "clientReg");

        var processor = createProcessor(globalConfProvider);

        assertThat(processor.isManagementRequest(serviceId)).isTrue();
    }

    @Test
    void isManagementRequestReturnsFalseWhenGlobalConfManagementServiceIsNull() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void isManagementRequestReturnsFalseForNonManagementClient() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void managementServiceIdProducesDspRequestWithManagementFlagTrue() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(MANAGEMENT_CLIENT, "clientReg");

        var processor = createProcessor(globalConfProvider);

        // isManagementRequest drives the management flag in the DspRequest constructed by processRequest.
        // Verify the flag value matches what the processor would pass.
        var dspRequest = new DspRequest(serviceId, null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isTrue();
    }

    @Test
    void nonManagementServiceIdProducesDspRequestWithManagementFlagFalse() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        var dspRequest = new DspRequest(serviceId, null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isFalse();
    }

    @Test
    void dspPathNegotiatesAndThenSendsToTheAddressesResolvedForTheMember() throws Exception {
        var processor = createProcessor(mock(GlobalConfProvider.class), SSL_DISABLED);

        try (var ctx = soapRequestContext()) {
            assertThrows(XrdRuntimeException.class, () -> processor.process(ctx));
        }

        var inOrder = inOrder(consumerSideDspProcessor, clientRequestPreparationService);
        inOrder.verify(consumerSideDspProcessor).execute(any());
        inOrder.verify(clientRequestPreparationService).prepareRequest(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonDspPathSendsWithoutNegotiating() throws Exception {
        var overrides = new HashMap<>(SSL_DISABLED);
        overrides.put("xroad.proxy.dsp-enabled", "false");
        var processor = createProcessor(mock(GlobalConfProvider.class), overrides);

        try (var ctx = soapRequestContext()) {
            assertThrows(XrdRuntimeException.class, () -> processor.process(ctx));
        }

        verify(consumerSideDspProcessor, never()).execute(any());
        verify(clientRequestPreparationService).prepareRequest(any(), any(), any(), any(), any(), any());
    }

    /**
     * Drives the real request-handling thread over a stored X-Road SOAP request. SSL is disabled in
     * these fixtures so the handler does not reach OCSP writing, which would need a signing service
     * with a real auth key.
     */
    private ClientSoapRequestContext soapRequestContext() throws IOException {
        var request = mock(RequestWrapper.class);
        when(request.getContentType()).thenReturn("text/xml; charset=UTF-8");
        when(request.getHeaders()).thenReturn(HttpFields.EMPTY);
        when(request.getInputStream()).thenReturn(Files.newInputStream(Paths.get(SOAP_REQUEST)));
        return new ClientSoapRequestContext(request, mock(ResponseWrapper.class), mock(OpMonitoringData.class));
    }

    private ClientSoapMessageProcessor createProcessor(GlobalConfProvider globalConfProvider) {
        return createProcessor(globalConfProvider, Map.of());
    }

    private ClientSoapMessageProcessor createProcessor(GlobalConfProvider globalConfProvider,
                                                       Map<String, String> proxyOverrides) {
        // Both branches prepare the request the same way, so failing here ends the send in either.
        when(clientRequestPreparationService.prepareRequest(any(), any(), any(), any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "No address found"));
        var httpSenderProvider = mock(HttpSenderProvider.class);
        when(httpSenderProvider.createClientHttpSender()).thenReturn(mock(HttpSender.class));

        return new ClientSoapMessageProcessor(
                mock(MessageSigningService.class),
                httpSenderProvider,
                mock(ClientVerificationService.class),
                mock(OpMonitoringDataHelper.class),
                globalConfProvider,
                new ProxyProperties(XRoadConfigBuilder.create()
                        .register(ProxyConfigKeys.instance())
                        .overrides(proxyOverrides)
                        .build()),
                new XRoadConfigCommonProperties(XRoadConfigBuilder.create().register(CommonConfigKeys.instance()).build()),
                mock(OcspVerifierFactory.class),
                clientRequestPreparationService,
                consumerSideDspProcessor,
                mock(IdentifierValidationService.class));
    }

}
