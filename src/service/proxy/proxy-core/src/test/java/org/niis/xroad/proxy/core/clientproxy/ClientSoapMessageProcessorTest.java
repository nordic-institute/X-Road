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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageProcessor.ReplaySoapProxyMessageEntity;
import org.niis.xroad.proxy.core.clientproxy.ClientSoapMessageProcessor.SentRequest;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspAcquisition;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.util.ClientSoapRequestContext;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;

import javax.net.ssl.SSLHandshakeException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_AGREEMENT_TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
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
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACQUISITION_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATA_PLANE_UNREACHABLE;
import static org.niis.xroad.common.core.exception.ErrorCode.HTTP_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.LEGACY_CALL_REFUSED;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

@Timeout(30)
class ClientSoapMessageProcessorTest {

    private static final int PIPE_BYTES = 5_000;

    private final Logger processorLogger = Logger.getLogger(ClientSoapMessageProcessor.class.getName());
    private final List<LogRecord> loggedWarnings = new CopyOnWriteArrayList<>();
    private final Handler warningCollector = new Handler() {
        @Override
        public void publish(LogRecord logRecord) {
            if (logRecord.getLevel().intValue() >= Level.WARNING.intValue()) {
                loggedWarnings.add(logRecord);
            }
        }
        @Override
        public void flush() {
            // records are kept in memory
        }
        @Override
        public void close() {
            // nothing to release
        }
    };
    private Level originalLoggerLevel;
    private boolean originalUseParentHandlers;

    @BeforeEach
    void collectWarnings() {
        originalLoggerLevel = processorLogger.getLevel();
        originalUseParentHandlers = processorLogger.getUseParentHandlers();
        processorLogger.setLevel(Level.ALL);
        processorLogger.setUseParentHandlers(false);
        processorLogger.addHandler(warningCollector);
    }

    @AfterEach
    void stopCollectingWarnings() {
        processorLogger.removeHandler(warningCollector);
        processorLogger.setUseParentHandlers(originalUseParentHandlers);
        processorLogger.setLevel(originalLoggerLevel);
    }

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

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultOfTheFirstServerIsServedByTheSibling(Throwable fault) throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var request = newCompletedRequest();
        var failedSsA = failingSender(fault);
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, failedSsA, servedBySsB);

        var sent = send(harness, request);

        assertThat(sent.httpSender()).isSameAs(servedBySsB);
        assertThat(sent.assetAccess()).isSameAs(WON_BY_SS_B);
        verify(failedSsA).close();
        verify(servedBySsB, never()).close();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), any(ReplaySoapProxyMessageEntity.class));
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
        verify(harness.dspSendFailureHandler(), never()).handle(any(), eq(WON_BY_SS_B));
    }

    @Test
    void whenEveryCandidateFailsTheCallerGetsTheDataPlaneErrorAfterOneAcquisitionPerCandidate() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var request = newCompletedRequest();
        var refusedBySsA = new ConnectException("refused by A");
        var refusedBySsB = new ConnectException("refused by B");
        var failedSsA = failingSender(refusedBySsA);
        var failedSsB = failingReplaySender(refusedBySsB);
        stubSenders(harness, failedSsA, failedSsB);

        assertThatThrownBy(() -> harness.processor().processRequest(request.ctx(), request.decoder(), "req-1", null))
                .satisfies(thrown -> assertDataPlaneUnreachable(thrown, refusedBySsB));

        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.httpSenderProvider(), times(2)).createClientHttpSender();
        verify(failedSsA).close();
        verify(failedSsB).close();
        verify(harness.dspSendFailureHandler()).handle(refusedBySsA, WON_BY_SS_A);
        verify(harness.dspSendFailureHandler()).handle(refusedBySsB, WON_BY_SS_B);
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any(DspRequest.class));
    }

    @Test
    void aFailedNewAcquisitionAfterATransportFaultReachesTheCallerAndTheFaultIsLogged() throws Exception {
        var harness = createRefusalHarness(true, false);
        var fault = new ConnectException("refused");
        var acquisitionFailure = XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED, "no provider granted access");
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new DspAcquisition(WON_BY_SS_A, 2))
                .thenThrow(acquisitionFailure);
        stubDspEndpoint(harness, WON_BY_SS_A);
        var request = newCompletedRequest();
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> harness.processor().processRequest(request.ctx(), request.decoder(), "req-1", null))
                .isSameAs(acquisitionFailure);

        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        assertThat(loggedWarnings).anySatisfy(warning -> {
            assertThat(warning.getThrown()).isSameAs(fault);
            assertThat(warning.getMessage()).contains("xRequestId: req-1");
        });
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any(DspRequest.class));
    }

    @Test
    void withRequestRetryOffAFailedSendIsNotResentButTheFailedServerIsDroppedAndReported() throws Exception {
        var harness = createRefusalHarness(true, false, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var request = newUnfinishedRequest();
        var fault = new ConnectException("refused");
        var failedSsA = failingSender(fault);
        stubSenders(harness, failedSsA, mock(HttpSender.class));

        assertThatThrownBy(() -> harness.processor().processRequest(request.ctx(), request.decoder(), "req-1", null))
                .satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        verify(failedSsA).close();
        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any(DspRequest.class));
    }

    @Test
    void aRequestPartlySentBeforeTheFaultIsCompletedAndReplayedInFullToTheSibling() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var request = newUnfinishedRequest();
        var requestBytes = new byte[PIPE_BYTES];
        var fed = new CountDownLatch(1);
        Thread.ofVirtual().start(() -> {
            try (var out = request.ctx().reqOuts()) {
                out.write(requestBytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                fed.countDown();
                request.ctx().httpSenderGate().countDown();
            }
        });
        var cutOffSsA = mock(HttpSender.class);
        doAnswer(invocation -> {
            invocation.<InputStream>getArgument(1).readNBytes(PIPE_BYTES / 10);
            throw new SocketTimeoutException("Read timed out");
        }).when(cutOffSsA).doPost(any(URI.class), any(InputStream.class), anyLong(), nullable(String.class));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, cutOffSsA, servedBySsB);

        var sent = send(harness, request);

        assertThat(sent.httpSender()).isSameAs(servedBySsB);
        assertThat(fed.getCount()).isZero();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), any(ReplaySoapProxyMessageEntity.class));
    }

    @Test
    void aSingleCandidateThatFailsEndsTheCallWithoutAnotherAcquisition() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 1, WON_BY_SS_A, WON_BY_SS_B);
        var request = newUnfinishedRequest();
        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> send(harness, request)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
    }

    @Test
    void aForcedTargetThatFailsEndsTheCallWithoutAnotherAcquisition() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 1, WON_BY_SS_A, WON_BY_SS_B);
        var forcedTarget = SecurityServerId.Conf.create("DEV", "COM", "222", "SS_A");
        var request = newUnfinishedRequest();
        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> harness.processor().send(new DspRequest(serviceId(), OTHER_CLIENT, forcedTarget, false),
                request.ctx(), request.decoder(), "req-1", null))
                .satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        var dspRequest = ArgumentCaptor.forClass(DspRequest.class);
        verify(harness.consumerSideDspProcessor(), times(1)).execute(dspRequest.capture());
        assertThat(dspRequest.getValue().targetSecurityServer()).isEqualTo(forcedTarget);
    }

    @ParameterizedTest
    @MethodSource("answersFromALiveProvider")
    void anAnswerFromALiveProviderIsNotResent(Throwable answer) throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var request = newUnfinishedRequest();
        stubSenders(harness, failingSender(answer));

        assertThatThrownBy(() -> send(harness, request)).isSameAs(answer);

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
    }

    @Test
    void aTokenRejectionAfterAFailoverIsHandledWithTheAccessOfTheSibling() throws Exception {
        var failure = XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed");
        var withoutToken = new AssetAccessResponse(WON_BY_SS_A.endpoint(), null, WON_BY_SS_A.winner());

        var harness = processAfterFailover(withoutToken, WON_BY_SS_B, failure);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(any(DspRequest.class));
    }

    @Test
    void aTokenRejectionAfterAFailoverDoesNotUseTheAccessOfTheFailedServer() throws Exception {
        var failure = XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed");
        var withoutToken = new AssetAccessResponse(WON_BY_SS_B.endpoint(), null, WON_BY_SS_B.winner());

        var harness = processAfterFailover(WON_BY_SS_A, withoutToken, failure);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any(DspRequest.class));
    }

    @Test
    void aTokenRejectionWhileSendingDropsTheAssetAccessOnceWithoutAnotherAcquisition() throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var request = newUnfinishedRequest();
        var rejection = XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected");
        stubSenders(harness, failingSender(rejection));

        assertThatThrownBy(() -> harness.processor().processRequest(request.ctx(), request.decoder(), "req-1", null))
                .isSameAs(rejection);

        verify(harness.consumerSideDspProcessor(), times(1)).invalidateAssetAccess(any(DspRequest.class));
        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
    }

    @ParameterizedTest
    @MethodSource("accessesOfTheFailedAttempt")
    void anAccessDenialWhileSendingNeverDropsTheAssetAccess(AssetAccessResponse access) throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, access, WON_BY_SS_B);
        var request = newUnfinishedRequest();
        var denial = XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed");
        stubSenders(harness, failingSender(denial));

        assertThatThrownBy(() -> harness.processor().processRequest(request.ctx(), request.decoder(), "req-1", null))
                .isSameAs(denial);

        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccess(any(DspRequest.class));
    }

    static Stream<AssetAccessResponse> accessesOfTheFailedAttempt() {
        return Stream.of(WON_BY_SS_A, new AssetAccessResponse(WON_BY_SS_A.endpoint(), null, WON_BY_SS_A.winner()));
    }

    static Stream<Throwable> transportFaults() {
        return Stream.of(
                new ConnectException("refused"),
                new UnknownHostException("ss-a.example"),
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

    private record Request(ClientSoapRequestContext ctx, SoapRequestDecoder decoder) {
    }

    private static Request newUnfinishedRequest() throws Exception {
        var ctx = newSoapRequestContext();
        var decoder = newUnnegotiatedDecoder(ctx);
        var encoder = mock(ProxyMessageEncoder.class);
        setField(decoder, "encoder", encoder);
        setField(decoder, "outputContentType", "multipart/mixed; boundary=request");
        return new Request(ctx, decoder);
    }

    private static Request newCompletedRequest() throws Exception {
        var request = newUnfinishedRequest();
        request.ctx().reqOuts().close();
        request.ctx().httpSenderGate().countDown();
        return request;
    }

    private static ServiceId serviceId() {
        return ServiceId.Conf.create(OTHER_CLIENT, "testService");
    }

    private static SentRequest send(RefusalHarness harness, Request request) throws Exception {
        return harness.processor().send(new DspRequest(serviceId(), OTHER_CLIENT, null, false),
                request.ctx(), request.decoder(), "req-1", null);
    }

    private static void stubAcquisitions(RefusalHarness harness, int candidateCount, AssetAccessResponse... accesses) {
        var acquisitions = Stream.of(accesses)
                .map(access -> new DspAcquisition(access, candidateCount))
                .toArray(DspAcquisition[]::new);
        var remaining = Arrays.copyOfRange(acquisitions, 1, acquisitions.length);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(acquisitions[0], remaining)
                .thenThrow(new AssertionError("more acquisitions than the test expects"));
        for (var access : accesses) {
            stubDspEndpoint(harness, access);
        }
    }

    private static void stubSenders(RefusalHarness harness, HttpSender first, HttpSender... rest) {
        when(harness.httpSenderProvider().createClientHttpSender()).thenReturn(first, rest);
    }

    private static HttpSender failingReplaySender(Throwable failure) throws Exception {
        var httpSender = mock(HttpSender.class);
        doThrow(failure).when(httpSender).doPost(any(URI.class), any(ReplaySoapProxyMessageEntity.class));
        return httpSender;
    }

    private static void assertDataPlaneUnreachable(Throwable thrown, Throwable cause) {
        assertThat(thrown).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(IO_ERROR)).isTrue();
            assertThat(e.getErrorCodeMetadata()).contains("originalCode=" + DSP_DATA_PLANE_UNREACHABLE.code());
            assertThat(e.getCause()).isSameAs(cause);
        });
    }

    private RefusalHarness processAfterFailover(AssetAccessResponse failedServer, AssetAccessResponse sibling,
                                                XrdRuntimeException siblingResponseFailure) throws Exception {
        var harness = createRefusalHarness(true, false);
        stubAcquisitions(harness, 2, failedServer, sibling);
        var request = newCompletedRequest();
        var answeringSibling = mock(HttpSender.class);
        when(answeringSibling.getResponseHeaders()).thenThrow(siblingResponseFailure);
        stubSenders(harness, failingSender(new ConnectException("refused")), answeringSibling);
        assertThatThrownBy(() -> harness.processor().processRequest(request.ctx(), request.decoder(), "req-1", null))
                .isSameAs(siblingResponseFailure);
        return harness;
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
                                  DspSendFailureHandler dspSendFailureHandler,
                                  HttpSenderProvider httpSenderProvider) {
    }

    private RefusalHarness createRefusalHarness(boolean dspEnabled, boolean legacyProtocolDisabled) {
        return createRefusalHarness(dspEnabled, legacyProtocolDisabled, true);
    }

    private RefusalHarness createRefusalHarness(boolean dspEnabled, boolean legacyProtocolDisabled,
                                                boolean requestRetryEnabled) {
        var globalConfProvider = mock(GlobalConfProvider.class);
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.proxy.dsp-enabled", String.valueOf(dspEnabled),
                        "xroad.proxy.legacy-protocol-disabled", String.valueOf(legacyProtocolDisabled),
                        "xroad.proxy.client-proxy.enable-request-retry", String.valueOf(requestRetryEnabled)))
                .build());
        var clientRequestPreparationService = mock(ClientRequestPreparationService.class);
        when(clientRequestPreparationService.prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(PROVIDER_ADDRESS)});
        var legacyCallRefusalPolicy = spy(new LegacyCallRefusalPolicy(proxyProperties, globalConfProvider));
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var dspSendFailureHandler = spy(new DspSendFailureHandler(consumerSideDspProcessor));
        var httpSenderProvider = mock(HttpSenderProvider.class);
        var processor = new ClientSoapMessageProcessor(
                mock(MessageSigningService.class),
                httpSenderProvider,
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
                consumerSideDspProcessor, dspSendFailureHandler, httpSenderProvider);
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
