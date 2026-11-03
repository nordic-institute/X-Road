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

import org.apache.http.conn.ConnectTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageProcessor.ReplaySoapProxyMessageEntity;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;

import javax.net.ssl.SSLHandshakeException;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.Map;
import java.util.stream.Stream;

import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_AGREEMENT_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

class ClientSoapMessageProcessorTest {

    private static final AcquisitionCandidate WINNER = new AcquisitionCandidate(
            "did:web:ss-a.example%3A7183:v1:DEV:COM:222",
            "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    private static final ClientId MANAGEMENT_CLIENT =
            ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
    private static final ClientId OTHER_CLIENT =
            ClientId.Conf.create("DEV", "COM", "4321", "TestClient");
    private static final String PROVIDER_ADDRESS = "https://provider.example/service";
    private static final String AGREEMENT_TOKEN = "token-abc-123";
    private static final String SS_A_ID = "did:web:ss-a.example%3A7183:v1:DEV:COM:222";
    private static final String SS_A_ADDRESS = "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SS_B_ID = "did:web:ss-b.example%3A7183:v1:DEV:COM:222";
    private static final String SS_B_ADDRESS = "https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final AssetAccessResponse WON_BY_SS_A =
            new AssetAccessResponse("https://ss-a.example:8183/data", AGREEMENT_TOKEN, new AcquisitionCandidate(SS_A_ID, SS_A_ADDRESS));
    private static final AssetAccessResponse WON_BY_SS_B =
            new AssetAccessResponse("https://ss-b.example:8183/data", AGREEMENT_TOKEN, new AcquisitionCandidate(SS_B_ID, SS_B_ADDRESS));

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
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER);

        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, assetAccess, null);

        verify(httpSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
    }

    @Test
    void sendRequestOmitsAgreementTokenHeaderWhenAssetAccessHasNoAuthorization() throws Exception {
        var harness = createHarness();
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, null, WINNER);

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
        setField(decoder, "requestSoap", soapMessage);
        var httpSender = mock(HttpSender.class);

        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null);

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
    }

    @Test
    void sendRequestAddsAgreementTokenHeaderOnEveryAttempt() throws Exception {
        var harness = createHarness();
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newSoapRequestDecoder(ctx);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER);
        var firstAttemptSender = mock(HttpSender.class);
        var replayedAttemptSender = mock(HttpSender.class);

        var replayEntity = mock(ReplaySoapProxyMessageEntity.class);

        harness.processor().sendRequest(firstAttemptSender, ctx, decoder, "req-1", null, assetAccess, null);
        harness.processor().sendRequest(replayedAttemptSender, ctx, decoder, "req-1", null, assetAccess, replayEntity);

        verify(firstAttemptSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        verify(replayedAttemptSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        verify(replayedAttemptSender).doPost(any(URI.class), eq(replayEntity));
    }

    @Test
    void tokenRejectedByProviderDropsCachedAssetAccessSoNextRequestAcquiresAfresh() {
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var processor = createProcessor(true, consumerSideDspProcessor);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);

        processor.dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), dspRequest, null);

        verify(consumerSideDspProcessor).invalidateAssetAccess(dspRequest);
    }

    @Test
    void otherProviderFailuresKeepTheCachedAssetAccess() {
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var processor = createProcessor(true, consumerSideDspProcessor);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);

        processor.dropAssetAccessOnTokenRejection(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "no such member"), dspRequest,
                new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER));

        verify(consumerSideDspProcessor, never()).invalidateAssetAccess(any());
    }

    @Test
    void anAccessDenialOfAPresentedTokenDropsCachedAssetAccess() {
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var processor = createProcessor(true, consumerSideDspProcessor);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);

        processor.dropAssetAccessOnTokenRejection(XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"), dspRequest,
                new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER));

        verify(consumerSideDspProcessor).invalidateAssetAccess(dspRequest);
    }

    @Test
    void anAccessDenialWithoutAPresentedTokenKeepsTheCachedAssetAccess() {
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var processor = createProcessor(true, consumerSideDspProcessor);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);

        processor.dropAssetAccessOnTokenRejection(XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"), dspRequest,
                new AssetAccessResponse(PROVIDER_ADDRESS, null, WINNER));
        processor.dropAssetAccessOnTokenRejection(XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"), dspRequest,
                null);

        verify(consumerSideDspProcessor, never()).invalidateAssetAccess(any());
    }

    @Test
    void tokenRejectionDoesNothingWhenDspDisabled() {
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var processor = createProcessor(false, consumerSideDspProcessor);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);

        processor.dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), dspRequest, null);

        verify(consumerSideDspProcessor, never()).invalidateAssetAccess(any());
    }

    @Test
    void sendRequestRefusesUnnegotiatedCallWhenLocalSettingRefuses() throws Exception {
        var harness = createRefusalHarness(false, true);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        assertThatThrownBy(() -> harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo("client." + LEGACY_CALL_REFUSED.code()));
        verifyNoInteractions(httpSender);
    }

    @Test
    void sendRequestRefusesUnnegotiatedCallWhenProviderInstanceRefuses() throws Exception {
        var harness = createRefusalHarness(false, false);
        when(harness.globalConfProvider().isLegacyProtocolDisabled(OTHER_CLIENT.getXRoadInstance())).thenReturn(true);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        assertThatThrownBy(() -> harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.isCausedBy(LEGACY_CALL_REFUSED)).isTrue());
        verifyNoInteractions(httpSender);
    }

    @Test
    void sendRequestSendsUnnegotiatedCallWhenNothingRefuses() throws Exception {
        var harness = createRefusalHarness(false, false);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null);
        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), any(InputStream.class), eq((long) CHUNKED_LENGTH),
                nullable(String.class));
    }

    @Test
    void sendRequestNegotiatesAndSendsWhenDspEnabledEvenIfLegacyCallsAreRefused() throws Exception {
        var harness = createRefusalHarness(true, true);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(PROVIDER_ADDRESS)), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        var assetAccess = new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER);
        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, assetAccess, null);
        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), any(InputStream.class), eq((long) CHUNKED_LENGTH),
                nullable(String.class));
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
    }

    @Test
    void sendRequestDoesNotTreatMissingDspResponseAsUnnegotiatedWhenDspEnabled() throws Exception {
        var harness = createRefusalHarness(true, true);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null);
        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), any(InputStream.class), eq((long) CHUNKED_LENGTH),
                nullable(String.class));
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
    }

    @Test
    void sendRequestSurfacesTargetResolutionErrorInsteadOfTheRefusal() throws Exception {
        var harness = createRefusalHarness(false, true);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "No address found"));
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = mock(HttpSender.class);
        assertThatThrownBy(() -> harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.isCausedBy(UNKNOWN_MEMBER)).isTrue());
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
        verify(httpSender, never()).doPost(any(URI.class), any(InputStream.class), anyLong(), any());
    }

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultOnTheDspSendReachesTheFailureHandlerAndIsRethrownUnchanged(Throwable fault) throws Exception {
        var harness = createRefusalHarness(true, false);
        stubDspEndpoint(harness, WON_BY_SS_A);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = failingSender(fault);

        assertThatThrownBy(() -> harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, WON_BY_SS_A, null))
                .isSameAs(fault);

        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
        verify(harness.clientRequestPreparationService()).markAddressUnusableIfHandshakeFailure(httpSender, fault);
        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
    }

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultOnTheLegacySendDoesNotReachTheFailureHandler(Throwable fault) throws Exception {
        var harness = createRefusalHarness(false, false);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var httpSender = failingSender(fault);

        assertThatThrownBy(() -> harness.processor().sendRequest(httpSender, ctx, decoder, "req-1", null, null, null))
                .isSameAs(fault);

        verify(harness.clientRequestPreparationService()).markAddressUnusableIfHandshakeFailure(httpSender, fault);
        verifyNoInteractions(harness.dspSendFailureHandler());
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
    }

    @ParameterizedTest
    @MethodSource("answersFromALiveProvider")
    void anAnswerFromALiveProviderNeitherDropsNorReportsTheWinner(Throwable answer) throws Exception {
        var harness = createRefusalHarness(true, false);
        stubDspEndpoint(harness, WON_BY_SS_A);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);

        assertThatThrownBy(() -> harness.processor()
                .sendRequest(failingSender(answer), ctx, decoder, "req-1", null, WON_BY_SS_A, null)).isSameAs(answer);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
    }

    @Test
    void aSendThatCompletesDoesNotReachTheFailureHandler() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubDspEndpoint(harness, WON_BY_SS_A);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);

        harness.processor().sendRequest(mock(HttpSender.class), ctx, decoder, "req-1", null, WON_BY_SS_A, null);

        verifyNoInteractions(harness.dspSendFailureHandler());
    }

    @Test
    void aTokenRejectionOnlyDropsTheEntryAndALaterTransportFaultDropsAndReports() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubDspEndpoint(harness, WON_BY_SS_A);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), dspRequest, WON_BY_SS_A);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(dspRequest);
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());

        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var fault = new ConnectException("refused");
        assertThatThrownBy(() -> harness.processor()
                .sendRequest(failingSender(fault), ctx, decoder, "req-1", null, WON_BY_SS_A, null)).isSameAs(fault);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
    }

    @Test
    void aTokenRejectionOnTheSiblingAfterATransportFaultReportsOnlyTheFailedServer() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubDspEndpoint(harness, WON_BY_SS_A);
        stubDspEndpoint(harness, WON_BY_SS_B);
        var dspRequest = new DspRequest(ServiceId.Conf.create(OTHER_CLIENT, "svc"), OTHER_CLIENT, null, false);
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var fault = new ConnectException("refused");
        assertThatThrownBy(() -> harness.processor()
                .sendRequest(failingSender(fault), ctx, decoder, "req-1", null, WON_BY_SS_A, null)).isSameAs(fault);

        harness.processor().sendRequest(mock(HttpSender.class), ctx, decoder, "req-2", null, WON_BY_SS_B, null);
        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), dspRequest, WON_BY_SS_B);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(dspRequest);
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

    private static void stubDspEndpoint(RefusalHarness harness, AssetAccessResponse assetAccess) {
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(assetAccess.endpoint())), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(assetAccess.endpoint())});
    }

    private static HttpSender failingSender(Throwable failure) throws Exception {
        var httpSender = mock(HttpSender.class);
        doThrow(failure).when(httpSender)
                .doPost(any(URI.class), any(InputStream.class), anyLong(), nullable(String.class));
        return httpSender;
    }

    private static ClientSoapRequestContext newSoapRequestContext() throws Exception {
        return new ClientSoapRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class), null);
    }

    private static SoapRequestDecoder newSoapRequestDecoder(ClientSoapRequestContext ctx) {
        return new SoapRequestDecoder(ctx, mock(MessageSigningService.class), "build/", "req-1",
                mock(ProxyProperties.class), mock(OpMonitoringDataHelper.class));
    }

    private static SoapRequestDecoder newUnnegotiatedDecoder(ClientSoapRequestContext ctx) throws Exception {
        var decoder = newSoapRequestDecoder(ctx);
        var soapMessage = mock(SoapMessageImpl.class);
        when(soapMessage.getSecurityServer()).thenReturn(SecurityServerId.Conf.create("DEV", "COM", "222", "SS1"));
        setField(decoder, "requestSoap", soapMessage);
        setField(decoder, "requestServiceId", ServiceId.Conf.create(OTHER_CLIENT, "testService"));
        return decoder;
    }

    private static void setField(SoapRequestDecoder decoder, String name, Object value) throws Exception {
        Field field = SoapRequestDecoder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(decoder, value);
    }

    private record Harness(ClientSoapMessageProcessor processor, ClientRequestPreparationService clientRequestPreparationService) {
    }

    private record RefusalHarness(ClientSoapMessageProcessor processor,
                                  ClientRequestPreparationService clientRequestPreparationService,
                                  GlobalConfProvider globalConfProvider, LegacyCallRefusalPolicy legacyCallRefusalPolicy,
                                  DspRequestProcessor consumerSideDspProcessor,
                                  DspSendFailureHandler dspSendFailureHandler) {
    }

    private RefusalHarness createRefusalHarness(boolean dspEnabled, boolean legacyProtocolDisabled) {
        var globalConfProvider = mock(GlobalConfProvider.class);
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.proxy.dsp-enabled", String.valueOf(dspEnabled),
                        "xroad.proxy.legacy-protocol-disabled", String.valueOf(legacyProtocolDisabled)))
                .build());
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        when(clientRequestPreparationService.prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var legacyCallRefusalPolicy = spy(new LegacyCallRefusalPolicy(proxyProperties, globalConfProvider));
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var dspSendFailureHandler = spy(new DspSendFailureHandler(consumerSideDspProcessor));
        var processor = new ClientSoapMessageProcessor(
                mock(MessageSigningService.class),
                mock(HttpSenderProvider.class),
                mock(ClientVerificationService.class),
                mock(OpMonitoringDataHelper.class),
                globalConfProvider,
                proxyProperties,
                new XRoadConfigCommonProperties(XRoadConfigBuilder.create().register(CommonConfigKeys.instance()).build()),
                mock(OcspVerifierFactory.class),
                clientRequestPreparationService,
                consumerSideDspProcessor,
                mock(IdentifierValidationService.class),
                legacyCallRefusalPolicy,
                dspSendFailureHandler);
        return new RefusalHarness(processor, clientRequestPreparationService, globalConfProvider, legacyCallRefusalPolicy,
                consumerSideDspProcessor, dspSendFailureHandler);
    }

    private Harness createHarness() {
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        var processor = createProcessor(mock(GlobalConfProvider.class), clientRequestPreparationService);
        return new Harness(processor, clientRequestPreparationService);
    }

    private ClientSoapMessageProcessor createProcessor(GlobalConfProvider globalConfProvider) {
        return createProcessor(globalConfProvider, mock(ClientRequestPreparationService.class));
    }

    private ClientSoapMessageProcessor createProcessor(boolean dspEnabled, DspRequestProcessor consumerSideDspProcessor) {
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(Map.of("xroad.proxy.dsp-enabled", String.valueOf(dspEnabled)))
                .build());
        return new ClientSoapMessageProcessor(
                mock(MessageSigningService.class),
                mock(HttpSenderProvider.class),
                mock(ClientVerificationService.class),
                mock(OpMonitoringDataHelper.class),
                mock(GlobalConfProvider.class),
                proxyProperties,
                new XRoadConfigCommonProperties(XRoadConfigBuilder.create().register(CommonConfigKeys.instance()).build()),
                mock(OcspVerifierFactory.class),
                mock(ClientRequestPreparationService.class),
                consumerSideDspProcessor,
                mock(IdentifierValidationService.class),
                mock(LegacyCallRefusalPolicy.class),
                mock(DspSendFailureHandler.class));
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
                mock(IdentifierValidationService.class),
                mock(LegacyCallRefusalPolicy.class),
                mock(DspSendFailureHandler.class));
    }
}
