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
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageProcessor.ReplaySoapProxyMessageEntity;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;

import java.lang.reflect.Field;
import java.net.URI;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_AGREEMENT_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientSoapMessageProcessorTest {

    private static final ClientId MANAGEMENT_CLIENT =
            ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
    private static final ClientId OTHER_CLIENT =
            ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
    private static final String PROVIDER_ADDRESS = "https://provider.example/service";
    private static final String AGREEMENT_TOKEN = "token-abc-123";

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
        var dspRequest = new DspRequest(serviceId, serviceId.getClientId(), null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isTrue();
    }

    @Test
    void nonManagementServiceIdProducesDspRequestWithManagementFlagFalse() {
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(MANAGEMENT_CLIENT);
        var serviceId = ServiceId.Conf.create(OTHER_CLIENT, "testService");

        var processor = createProcessor(globalConfProvider);

        var dspRequest = new DspRequest(serviceId, serviceId.getClientId(), null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isFalse();
    }

    @Test
    void sendRequestAddsAgreementTokenHeaderWhenAssetAccessCarriesToken() throws Exception {
        var harness = createHarness();
        when(harness.clientRequestPreparationService().prepareRequest(any(), any(), any(URI.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN);

        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, assetAccess, null);

        verify(httpSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
    }

    @Test
    void sendRequestOmitsAgreementTokenHeaderWhenAssetAccessHasNoAuthorization() throws Exception {
        var harness = createHarness();
        when(harness.clientRequestPreparationService().prepareRequest(any(), any(), any(URI.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, null);

        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, assetAccess, null);

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
    }

    @Test
    void sendRequestOmitsAgreementTokenHeaderWhenDspDisabled() throws Exception {
        var harness = createHarness();
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), any(SecurityServerId.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var soapMessage = mock(SoapMessageImpl.class);
        when(soapMessage.getSecurityServer()).thenReturn(SecurityServerId.Conf.create("DEV", "COM", "222", "SS1"));
        setRequestSoap(decoder, soapMessage);
        var httpSender = mock(HttpSender.class);

        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null);

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
    }

    @Test
    void sendRequestAddsAgreementTokenHeaderOnEveryAttempt() throws Exception {
        var harness = createHarness();
        when(harness.clientRequestPreparationService().prepareRequest(any(), any(), any(URI.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN);
        var firstAttemptSender = mock(HttpSender.class);
        var replayedAttemptSender = mock(HttpSender.class);

        var replayEntity = mock(ReplaySoapProxyMessageEntity.class);

        harness.processor().sendRequest(firstAttemptSender, ctx, decoder, "req-1", null, assetAccess, null);
        harness.processor().sendRequest(replayedAttemptSender, ctx, decoder, "req-1", null, assetAccess, replayEntity);

        verify(firstAttemptSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        verify(replayedAttemptSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        verify(replayedAttemptSender).doPost(any(URI.class), eq(replayEntity));
    }

    private static ClientSoapRequestContext newSoapRequestContext() throws Exception {
        return new ClientSoapRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class), null);
    }

    private static SoapRequestDecoder newSoapRequestDecoder(ClientSoapRequestContext ctx) {
        return new SoapRequestDecoder(ctx, mock(MessageSigningService.class), "build/", "req-1",
                mock(ProxyProperties.class), mock(OpMonitoringDataHelper.class));
    }

    private static void setRequestSoap(SoapRequestDecoder decoder, SoapMessageImpl soapMessage) throws Exception {
        Field field = SoapRequestDecoder.class.getDeclaredField("requestSoap");
        field.setAccessible(true);
        field.set(decoder, soapMessage);
    }

    private record Harness(ClientSoapMessageProcessor processor, ClientRequestPreparationService clientRequestPreparationService) {
    }

    private Harness createHarness() {
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        var processor = createProcessor(mock(GlobalConfProvider.class), clientRequestPreparationService);
        return new Harness(processor, clientRequestPreparationService);
    }

    private ClientSoapMessageProcessor createProcessor(GlobalConfProvider globalConfProvider) {
        return createProcessor(globalConfProvider, mock(ClientRequestPreparationService.class));
    }

    private ClientSoapMessageProcessor createProcessor(GlobalConfProvider globalConfProvider,
                                                       ClientRequestPreparationService clientRequestPreparationService) {
        return new ClientSoapMessageProcessor(
                mock(MessageSigningService.class),
                mock(HttpSenderProvider.class),
                mock(ClientVerificationService.class),
                mock(OpMonitoringDataHelper.class),
                globalConfProvider,
                mock(ProxyProperties.class),
                new XRoadConfigCommonProperties(XRoadConfigBuilder.create().register(CommonConfigKeys.instance()).build()),
                mock(OcspVerifierFactory.class),
                clientRequestPreparationService,
                mock(DspRequestProcessor.class),
                mock(IdentifierValidationService.class));
    }
}
