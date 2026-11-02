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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.conn.ConnectTimeoutException;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.RestRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.impl.ownserver.ServerConfProviderIdentitySource;

import javax.net.ssl.SSLHandshakeException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_AGREEMENT_TOKEN;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.ACCESS_DENIED;
import static org.niis.xroad.common.core.exception.ErrorCode.AGREEMENT_TOKEN_REJECTED;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.LEGACY_CALL_REFUSED;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;

class ClientRestMessageProcessorTest {

    @SneakyThrows
    @Test
    void processShouldAddOpMonitoringData() {
        var opMonitoringData = new OpMonitoringData(CLIENT, 100);
        var globalConfProvider = mock(GlobalConfProvider.class);
        var serverConfProvider = mock(ServerConfProvider.class);
        var processor = createProcessor(globalConfProvider, serverConfProvider);

        RequestWrapper request = RequestWrapper.of(getMockedRequest());
        var respWrapper = mock(ResponseWrapper.class);
        var ctx = new RestRequestContext(request, respWrapper, opMonitoringData);

        assertThrows(XrdRuntimeException.class, () -> processor.process(ctx));

        verifyOpMonitoringData(opMonitoringData.getData());
    }

    private ClientRestMessageProcessor createProcessor(GlobalConfProvider globalConfProvider,
                                                       ServerConfProvider serverConfProvider) {
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .build());
        var commonProperties = new XRoadConfigCommonProperties(XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .build());
        var opMonitoringDataHelper = new OpMonitoringDataHelper(globalConfProvider, serverConfProvider,
                new OwnSecurityServerResolver(new ServerConfProviderIdentitySource(serverConfProvider), globalConfProvider));
        var httpSenderProvider = mock(HttpSenderProvider.class);
        var messageSigningService = mock(MessageSigningService.class);
        var clientVerificationService = mock(ClientVerificationService.class);
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        when(httpSenderProvider.createClientHttpSender()).thenReturn(mock(HttpSender.class));
        when(clientRequestPreparationService.prepareRequest(any(), any(), any(URI.class), any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "No address found"));
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        when(consumerSideDspProcessor.execute(any()))
                .thenReturn(new AssetAccessResponse("https://localhost:5500/", null));

        return new ClientRestMessageProcessor(
                messageSigningService,
                httpSenderProvider,
                clientVerificationService,
                opMonitoringDataHelper,
                globalConfProvider,
                proxyProperties,
                commonProperties,
                mock(OcspVerifierFactory.class),
                clientRequestPreparationService,
                consumerSideDspProcessor,
                mock(IdentifierValidationService.class),
                mock(LegacyCallRefusalPolicy.class),
                mock(DspSendFailureHandler.class)
        );
    }

    @Test
    void isManagementRequestReturnsTrueWhenServiceIdMatchesManagementSubsystem() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(management, "clientReg");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        assertThat(processor.isManagementRequest(serviceId)).isTrue();
    }

    @Test
    void isManagementRequestReturnsFalseForNonManagementClient() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var other = ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(other, "mock1");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void isManagementRequestReturnsFalseWhenGlobalConfManagementServiceIsNull() {
        var other = ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(null);
        var serviceId = ServiceId.Conf.create(other, "mock1");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        assertThat(processor.isManagementRequest(serviceId)).isFalse();
    }

    @Test
    void managementServiceIdProducesDspRequestWithManagementFlagTrue() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(management, "clientReg");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        // isManagementRequest drives the management flag in the DspRequest constructed by sendRequest.
        var dspRequest = new DspRequest(serviceId, serviceId.getClientId(), null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isTrue();
    }

    @Test
    void nonManagementServiceIdProducesDspRequestWithManagementFlagFalse() {
        var management = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
        var other = ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
        var globalConfProvider = mock(GlobalConfProvider.class);
        when(globalConfProvider.getManagementRequestService()).thenReturn(management);
        var serviceId = ServiceId.Conf.create(other, "testService");

        var processor = createProcessor(globalConfProvider, mock(ServerConfProvider.class));

        var dspRequest = new DspRequest(serviceId, serviceId.getClientId(), null, processor.isManagementRequest(serviceId));
        assertThat(dspRequest.managementSubsystem()).isFalse();
    }

    private static final String PROVIDER_ADDRESS = "https://provider.example/service";
    private static final String AGREEMENT_TOKEN = "token-abc-123";

    @Test
    void sendRequestAddsAgreementTokenHeaderWhenAssetAccessCarriesToken() throws Exception {
        var harness = createHarness(true);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN));
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var httpSender = mock(HttpSender.class);

        var tokenPresented = harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(httpSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        assertThat(tokenPresented).isTrue();
    }

    @Test
    void sendRequestOmitsAgreementTokenHeaderWhenAssetAccessHasNoAuthorization() throws Exception {
        var harness = createHarness(true);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new AssetAccessResponse(PROVIDER_ADDRESS, null));
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var httpSender = mock(HttpSender.class);

        var tokenPresented = harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
        assertThat(tokenPresented).isFalse();
    }

    @Test
    void sendRequestOmitsAgreementTokenHeaderWhenDspDisabled() throws Exception {
        var harness = createHarness(false);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var httpSender = mock(HttpSender.class);

        harness.processor().sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
        verify(harness.consumerSideDspProcessor(), never()).execute(any());
    }

    @Test
    void sendRequestAddsAgreementTokenHeaderOnEveryAttempt() throws Exception {
        var harness = createHarness(true);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN));
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var firstAttemptSender = mock(HttpSender.class);
        var replayedAttemptSender = mock(HttpSender.class);

        // executeWithRetry invokes sendRequest fresh on every attempt with a new HttpSender, re-resolving
        // asset access each time; two direct calls reproduce a first attempt and a retried attempt.
        harness.processor().sendRequest(firstAttemptSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());
        harness.processor().sendRequest(replayedAttemptSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(firstAttemptSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        verify(replayedAttemptSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
    }

    @Test
    void tokenRejectedByProviderDropsCachedAssetAccessSoNextRequestAcquiresAfresh() {
        var harness = createHarness(true);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), restRequest(), serviceId(), false);

        var dspRequest = ArgumentCaptor.forClass(DspRequest.class);
        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(dspRequest.capture());
        assertThat(dspRequest.getValue().serviceId()).isEqualTo(serviceId());
        assertThat(dspRequest.getValue().sender()).isEqualTo(restRequest().getClientId());
    }

    @Test
    void otherProviderFailuresKeepTheCachedAssetAccess() {
        var harness = createHarness(true);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(UNKNOWN_MEMBER, "no such member"), restRequest(), serviceId(), true);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any());
    }

    @Test
    void anAccessDenialOfAPresentedTokenDropsCachedAssetAccess() {
        var harness = createHarness(true);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"), restRequest(), serviceId(), true);

        var dspRequest = ArgumentCaptor.forClass(DspRequest.class);
        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(dspRequest.capture());
        assertThat(dspRequest.getValue().serviceId()).isEqualTo(serviceId());
    }

    @Test
    void anAccessDenialWithoutAPresentedTokenKeepsTheCachedAssetAccess() {
        var harness = createHarness(true);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"), restRequest(), serviceId(), false);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any());
    }

    @Test
    void tokenRejectionDoesNothingWhenDspDisabled() {
        var harness = createHarness(false);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), restRequest(), serviceId(), true);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any());
    }

    @Test
    void sendRequestRefusesUnnegotiatedCallWhenLocalSettingRefuses() throws Exception {
        var harness = createHarness(false, true);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);

        assertThatThrownBy(() -> harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext()))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo("client." + LEGACY_CALL_REFUSED.code()));

        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
        verify(harness.consumerSideDspProcessor(), never()).execute(any());
    }

    @Test
    void sendRequestRefusesUnnegotiatedCallWhenProviderInstanceRefuses() throws Exception {
        var harness = createHarness(false, false);
        when(harness.globalConfProvider().isLegacyProtocolDisabled(serviceId().getXRoadInstance())).thenReturn(true);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);

        assertThatThrownBy(() -> harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext()))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.isCausedBy(LEGACY_CALL_REFUSED)).isTrue());

        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
    }

    @Test
    void sendRequestSendsUnnegotiatedCallWhenNothingRefuses() throws Exception {
        var harness = createHarness(false, false);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);

        harness.processor().sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), nullable(HttpEntity.class));
    }

    @Test
    void sendRequestChecksTheRefusalOnEveryUnnegotiatedAttempt() throws Exception {
        var harness = createHarness(false, false);
        stubUnnegotiatedAddress(harness);

        harness.processor().sendRequest(mock(HttpSender.class), null, null, restRequest(), serviceId(), "req-1",
                restRequestContext());
        harness.processor().sendRequest(mock(HttpSender.class), null, null, restRequest(), serviceId(), "req-1",
                restRequestContext());

        verify(harness.legacyCallRefusalPolicy(), times(2)).checkConsumerCallAllowed(serviceId());
    }

    @Test
    void sendRequestNegotiatesAndSendsWhenDspEnabledEvenIfLegacyCallsAreRefused() throws Exception {
        var harness = createHarness(true, true);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN));
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var httpSender = mock(HttpSender.class);

        harness.processor().sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), nullable(HttpEntity.class));
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
    }

    @Test
    void sendRequestSendsRequestThatBypassesDspWithoutNegotiatingOrTokenEvenIfLegacyCallsAreRefused() throws Exception {
        var harness = createHarness(true, true);
        when(harness.consumerSideDspProcessor().bypassesDsp(any())).thenReturn(true);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);

        harness.processor().sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext());

        verify(harness.consumerSideDspProcessor(), never()).execute(any());
        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), nullable(HttpEntity.class));
        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
    }

    @Test
    void sendRequestSurfacesTargetResolutionErrorInsteadOfTheRefusal() throws Exception {
        var harness = createHarness(false, true);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "No address found"));
        var httpSender = mock(HttpSender.class);

        assertThatThrownBy(() -> harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext()))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.isCausedBy(UNKNOWN_MEMBER)).isTrue());

        verifyNoInteractions(harness.legacyCallRefusalPolicy());
        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
    }

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultOnTheDspSendReachesTheFailureHandlerAndIsRethrownUnchanged(Throwable fault) throws Exception {
        var harness = createHarness(true);
        stubDspAcquisition(harness, WON_BY_SS_A);
        var httpSender = failingSender(fault);

        assertThatThrownBy(() -> harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext()))
                .isSameAs(fault);

        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
        verify(harness.clientRequestPreparationService()).markAddressUnusableIfHandshakeFailure(httpSender, fault);
        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
    }

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultOnTheLegacySendDoesNotReachTheFailureHandler(Throwable fault) throws Exception {
        var harness = createHarness(false);
        stubUnnegotiatedAddress(harness);
        var httpSender = failingSender(fault);

        assertThatThrownBy(() -> harness.processor()
                .sendRequest(httpSender, null, null, restRequest(), serviceId(), "req-1", restRequestContext()))
                .isSameAs(fault);

        verify(harness.clientRequestPreparationService()).markAddressUnusableIfHandshakeFailure(httpSender, fault);
        verifyNoInteractions(harness.dspSendFailureHandler());
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
    }

    @ParameterizedTest
    @MethodSource("answersFromALiveProvider")
    void anAnswerFromALiveProviderNeitherDropsNorReportsTheWinner(Throwable answer) throws Exception {
        var harness = createHarness(true);
        stubDspAcquisition(harness, WON_BY_SS_A);

        assertThatThrownBy(() -> harness.processor().sendRequest(failingSender(answer), null, null, restRequest(),
                serviceId(), "req-1", restRequestContext())).isSameAs(answer);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
    }

    @Test
    void aSendThatCompletesDoesNotReachTheFailureHandler() throws Exception {
        var harness = createHarness(true);
        stubDspAcquisition(harness, WON_BY_SS_A);

        harness.processor().sendRequest(mock(HttpSender.class), null, null, restRequest(), serviceId(), "req-1",
                restRequestContext());

        verifyNoInteractions(harness.dspSendFailureHandler());
    }

    @Test
    void aTokenRejectionOnlyDropsTheEntryAndALaterTransportFaultDropsAndReports() throws Exception {
        var harness = createHarness(true);
        stubDspAcquisition(harness, WON_BY_SS_A);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), restRequest(), serviceId(), true);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(any(DspRequest.class));
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());

        var fault = new ConnectException("refused");
        assertThatThrownBy(() -> harness.processor().sendRequest(failingSender(fault), null, null, restRequest(),
                serviceId(), "req-1", restRequestContext())).isSameAs(fault);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
    }

    @Test
    void aTokenRejectionOnTheSiblingAfterATransportFaultReportsOnlyTheFailedServer() throws Exception {
        var harness = createHarness(true);
        stubDspAcquisition(harness, WON_BY_SS_A);
        var fault = new ConnectException("refused");
        assertThatThrownBy(() -> harness.processor().sendRequest(failingSender(fault), null, null, restRequest(),
                serviceId(), "req-1", restRequestContext())).isSameAs(fault);
        stubDspAcquisition(harness, WON_BY_SS_B);

        harness.processor().sendRequest(mock(HttpSender.class), null, null, restRequest(), serviceId(), "req-2",
                restRequestContext());
        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), restRequest(), serviceId(), true);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(any(DspRequest.class));
        verify(harness.consumerSideDspProcessor(), times(1)).reportCounterPartyFailure(any(), any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(eq(SS_B_ID), any());
        verify(harness.consumerSideDspProcessor(), times(1)).invalidateAssetAccessWonBy(any());
    }

    static Stream<Throwable> transportFaults() {
        return Stream.of(
                new ConnectException("refused"),
                new ConnectTimeoutException("connect timed out"),
                new SSLHandshakeException("handshake_failure"),
                new SocketTimeoutException("Read timed out"));
    }

    static Stream<Throwable> answersFromALiveProvider() {
        return Stream.of(
                XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s: %s", 500,
                        "Internal Server Error"),
                XrdRuntimeException.systemException(SERVICE_FAILED, "service failed"));
    }

    private static final String SS_A_ID = "did:web:ss-a.example%3A7183:v1:DEV:COM:222";
    private static final String SS_A_ADDRESS = "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SS_B_ID = "did:web:ss-b.example%3A7183:v1:DEV:COM:222";
    private static final String SS_B_ADDRESS = "https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final AssetAccessResponse WON_BY_SS_A =
            new AssetAccessResponse("https://ss-a.example:8183/data", AGREEMENT_TOKEN, SS_A_ID, SS_A_ADDRESS);
    private static final AssetAccessResponse WON_BY_SS_B =
            new AssetAccessResponse("https://ss-b.example:8183/data", AGREEMENT_TOKEN, SS_B_ID, SS_B_ADDRESS);

    private static void stubDspAcquisition(RestHarness harness, AssetAccessResponse assetAccess) {
        when(harness.consumerSideDspProcessor().execute(any())).thenReturn(assetAccess);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(assetAccess.endpoint())), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(assetAccess.endpoint())});
    }

    private static HttpSender failingSender(Throwable failure) throws Exception {
        var httpSender = mock(HttpSender.class);
        doThrow(failure).when(httpSender).doPost(any(URI.class), nullable(HttpEntity.class));
        return httpSender;
    }

    private static void stubUnnegotiatedAddress(RestHarness harness) {
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
    }

    private static RestRequest restRequest() {
        return new RestRequest("GET", "/r1/DEV/COM/222/TestClient/testService", null, List.of(), "req-1");
    }

    private static ServiceId serviceId() {
        return ServiceId.Conf.create("DEV", "COM", "222", "TestClient", "testService");
    }

    private static RestRequestContext restRequestContext() {
        return new RestRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class), null);
    }

    private record RestHarness(ClientRestMessageProcessor processor, DspRequestProcessor consumerSideDspProcessor,
                               ClientRequestPreparationService clientRequestPreparationService,
                               GlobalConfProvider globalConfProvider, LegacyCallRefusalPolicy legacyCallRefusalPolicy,
                               DspSendFailureHandler dspSendFailureHandler) {
    }

    private RestHarness createHarness(boolean dspEnabled) {
        return createHarness(dspEnabled, false);
    }

    private RestHarness createHarness(boolean dspEnabled, boolean legacyProtocolDisabled) {
        var globalConfProvider = mock(GlobalConfProvider.class);
        var serverConfProvider = mock(ServerConfProvider.class);
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.proxy.dsp-enabled", String.valueOf(dspEnabled),
                        "xroad.proxy.legacy-protocol-disabled", String.valueOf(legacyProtocolDisabled)))
                .build());
        var commonProperties = new XRoadConfigCommonProperties(XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .build());
        var opMonitoringDataHelper = new OpMonitoringDataHelper(globalConfProvider, serverConfProvider,
                new OwnSecurityServerResolver(new ServerConfProviderIdentitySource(serverConfProvider), globalConfProvider));
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var legacyCallRefusalPolicy = spy(new LegacyCallRefusalPolicy(proxyProperties, globalConfProvider));
        var dspSendFailureHandler = spy(new DspSendFailureHandler(consumerSideDspProcessor));

        var processor = new ClientRestMessageProcessor(
                mock(MessageSigningService.class),
                mock(HttpSenderProvider.class),
                mock(ClientVerificationService.class),
                opMonitoringDataHelper,
                globalConfProvider,
                proxyProperties,
                commonProperties,
                mock(OcspVerifierFactory.class),
                clientRequestPreparationService,
                consumerSideDspProcessor,
                mock(IdentifierValidationService.class),
                legacyCallRefusalPolicy,
                dspSendFailureHandler);
        return new RestHarness(processor, consumerSideDspProcessor, clientRequestPreparationService,
                globalConfProvider, legacyCallRefusalPolicy, dspSendFailureHandler);
    }

    private void verifyOpMonitoringData(Map<String, Object> data) {
        assertEquals("Client", data.get("securityServerType"));
        assertEquals("REST", data.get("serviceType"));
        assertEquals("pets", data.get("serviceCode"));
        assertEquals("GET", data.get("restMethod"));
        assertNull(data.get("restPath"));
        assertEquals(Version.XROAD_VERSION, data.get("xRoadVersion"));
        assertNotNull(data.get("clientXRoadInstance"), "DEV");
        assertEquals("1234", data.get("clientMemberCode"));
        assertEquals("TestService", data.get("clientSubsystemCode"));
        assertEquals("DEV", data.get("serviceXRoadInstance"));
        assertEquals("1234", data.get("serviceMemberCode"));
        assertEquals("TestService", data.get("serviceSubsystemCode"));
        assertEquals("1", data.get("messageProtocolVersion"));
        assertNotNull(data.get("xRequestId"));
        assertEquals(false, data.get("succeeded"));
    }

    private Request getMockedRequest() {
        final var request = mock(Request.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHttpURI()).thenReturn(HttpURI.build(URI.create("http://localhost:4210/r1/DEV/COM/1234/TestService/pets/pets/1")));
        var clientId = new PreEncodedHttpField(HEADER_CLIENT_ID, "DEV/COM/1234/TestService");
        when(request.getHeaders()).thenReturn(HttpFields.from(clientId));
        return request;
    }
}
