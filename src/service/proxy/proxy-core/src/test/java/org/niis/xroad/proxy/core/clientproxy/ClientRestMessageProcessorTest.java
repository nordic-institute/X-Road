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
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.HttpSender;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.ResponseWrapper;

import com.google.common.base.Ticker;
import com.google.common.primitives.Bytes;
import lombok.SneakyThrows;
import org.apache.http.HttpEntity;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.message.BasicHeader;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.PreEncodedHttpField;
import org.eclipse.jetty.server.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.impl.XRoadConfigCommonProperties;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.clientproxy.ClientRestMessageProcessor.SentRequest;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspAcquisition;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.dsp.DspRequestProcessor;
import org.niis.xroad.proxy.core.messagelog.MessageLog;
import org.niis.xroad.proxy.core.messagelog.NullLogManager;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.service.ClientVerificationService;
import org.niis.xroad.proxy.core.service.HttpSenderProvider;
import org.niis.xroad.proxy.core.service.LegacyCallRefusalPolicy;
import org.niis.xroad.proxy.core.service.MessageSigningService;
import org.niis.xroad.proxy.core.service.ServiceAddressResolver;
import org.niis.xroad.proxy.core.util.IdentifierValidationService;
import org.niis.xroad.proxy.core.util.OpMonitoringDataHelper;
import org.niis.xroad.proxy.core.util.RestRequestContext;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.impl.ownserver.ServerConfProviderIdentitySource;

import javax.net.ssl.SSLHandshakeException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static ee.ria.xroad.common.crypto.Digests.DEFAULT_DIGEST_ALGORITHM;
import static ee.ria.xroad.common.util.MimeTypes.TEXT_XML_UTF8;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_AGREEMENT_TOKEN;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CLIENT_ID;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SECURITY_SERVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SERVICE_SECURITY_SERVER_ADDRESS;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.CLIENT;

class ClientRestMessageProcessorTest {

    private static final int BODY_SIZE = 200_000;
    private static final int BODY_CUT_OFF = 10_000;

    private final Logger processorLogger = Logger.getLogger(ClientRestMessageProcessor.class.getName());
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

    @TempDir
    private Path tempDir;

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
                .thenReturn(new DspAcquisition(new AssetAccessResponse("https://localhost:5500/", null, WINNER), 1));

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

        // isManagementRequest drives the management flag in the DspRequest constructed by send.
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

    private static final AcquisitionCandidate WINNER = new AcquisitionCandidate(
            "did:web:ss-a.example%3A7183:v1:DEV:COM:222",
            "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    private static final String PROVIDER_ADDRESS = "https://provider.example/service";
    private static final String AGREEMENT_TOKEN = "token-abc-123";

    @Test
    void sendAddsAgreementTokenHeaderWhenAssetAccessCarriesToken() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER));
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        var sent = send(harness);

        verify(httpSender).addHeader(HEADER_AGREEMENT_TOKEN, AGREEMENT_TOKEN);
        assertThat(sent.isTokenPresented()).isTrue();
        assertThat(sent.getHttpSender()).isSameAs(httpSender);
    }

    @Test
    void sendOmitsAgreementTokenHeaderWhenAssetAccessHasNoAuthorization() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, new AssetAccessResponse(PROVIDER_ADDRESS, null, WINNER));
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        var sent = send(harness);

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
        assertThat(sent.isTokenPresented()).isFalse();
    }

    @Test
    void sendOmitsAgreementTokenHeaderWhenDspDisabled() throws Exception {
        var harness = createHarness(false);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        send(harness);

        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
        verify(harness.consumerSideDspProcessor(), never()).execute(any());
    }

    @Test
    void afterAFailoverTheSiblingIsSentItsOwnAgreementTokenAndTheFailedServersTokenIsNotSentAgain() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var failedSsA = failingSender(new ConnectException("refused"));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, failedSsA, servedBySsB);

        send(harness);

        verify(failedSsA).addHeader(HEADER_AGREEMENT_TOKEN, SS_A_TOKEN);
        verify(failedSsA, never()).addHeader(HEADER_AGREEMENT_TOKEN, SS_B_TOKEN);
        verify(servedBySsB).addHeader(HEADER_AGREEMENT_TOKEN, SS_B_TOKEN);
        verify(servedBySsB, never()).addHeader(HEADER_AGREEMENT_TOKEN, SS_A_TOKEN);
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
    void sendRefusesUnnegotiatedCallWhenLocalSettingRefuses() throws Exception {
        var harness = createHarness(false, true);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo("client." + LEGACY_CALL_REFUSED.code()));

        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
        verify(httpSender).close();
        verify(harness.consumerSideDspProcessor(), never()).execute(any());
    }

    @Test
    void sendRefusesUnnegotiatedCallWhenProviderInstanceRefuses() throws Exception {
        var harness = createHarness(false, false);
        when(harness.globalConfProvider().isLegacyProtocolDisabled(serviceId().getXRoadInstance())).thenReturn(true);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.isCausedBy(LEGACY_CALL_REFUSED)).isTrue());

        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
    }

    @Test
    void sendSendsUnnegotiatedCallWhenNothingRefuses() throws Exception {
        var harness = createHarness(false, false);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        send(harness);

        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), nullable(HttpEntity.class));
    }

    @Test
    void sendChecksTheRefusalOnEveryUnnegotiatedRequest() throws Exception {
        var harness = createHarness(false, false);
        stubUnnegotiatedAddress(harness);
        stubSenders(harness, mock(HttpSender.class), mock(HttpSender.class));

        send(harness);
        send(harness);

        verify(harness.legacyCallRefusalPolicy(), times(2)).checkConsumerCallAllowed(serviceId());
    }

    @Test
    void sendNegotiatesAndSendsWhenDspEnabledEvenIfLegacyCallsAreRefused() throws Exception {
        var harness = createHarness(true, true);
        stubAcquisitions(harness, 1, new AssetAccessResponse(PROVIDER_ADDRESS, AGREEMENT_TOKEN, WINNER));
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        send(harness);

        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), nullable(HttpEntity.class));
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
    }

    @Test
    void sendSendsRequestThatBypassesDspWithoutNegotiatingOrTokenEvenIfLegacyCallsAreRefused() throws Exception {
        var harness = createHarness(true, true);
        when(harness.consumerSideDspProcessor().bypassesDsp(any())).thenReturn(true);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        send(harness);

        verify(harness.consumerSideDspProcessor(), never()).execute(any());
        verify(httpSender).doPost(eq(URI.create(PROVIDER_ADDRESS)), nullable(HttpEntity.class));
        verify(httpSender, never()).addHeader(eq(HEADER_AGREEMENT_TOKEN), any());
        verifyNoInteractions(harness.legacyCallRefusalPolicy());
    }

    @Test
    void sendSurfacesTargetResolutionErrorInsteadOfTheRefusal() throws Exception {
        var harness = createHarness(false, true);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), nullable(SecurityServerId.class), any(), any(), any()))
                .thenThrow(XrdRuntimeException.systemException(UNKNOWN_MEMBER, "No address found"));
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness))
                .isInstanceOfSatisfying(XrdRuntimeException.class, e -> assertThat(e.isCausedBy(UNKNOWN_MEMBER)).isTrue());

        verifyNoInteractions(harness.legacyCallRefusalPolicy());
        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
    }

    @Test
    void aHandshakeFailureOnTheLegacyPathIsRetriedOnAFreshSender() throws Exception {
        var harness = createHarness(false);
        stubUnnegotiatedAddress(harness);
        when(harness.clientRequestPreparationService().shouldRetry(any(), any(), anyInt())).thenReturn(true, false);
        var failedSender = failingSender(new SSLHandshakeException("handshake_failure"));
        var servedSender = mock(HttpSender.class);
        stubSenders(harness, failedSender, servedSender);

        var sent = send(harness);

        assertThat(sent.getHttpSender()).isSameAs(servedSender);
        verify(failedSender).close();
        verify(servedSender, never()).close();
        verify(harness.consumerSideDspProcessor(), never()).execute(any());
        verifyNoInteractions(harness.dspSendFailureHandler());
    }

    @ParameterizedTest
    @MethodSource("transportFaults")
    void aTransportFaultOnTheDspSendReachesTheFailureHandlerAndEndsAsTheDataPlaneError(Throwable fault) throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A);
        var httpSender = failingSender(fault);
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

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
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness)).isSameAs(fault);

        verify(harness.clientRequestPreparationService()).markAddressUnusableIfHandshakeFailure(httpSender, fault);
        verifyNoInteractions(harness.dspSendFailureHandler());
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
    }

    @ParameterizedTest
    @MethodSource("answersFromALiveProvider")
    void anAnswerFromALiveProviderIsNotResentAndNeitherDropsNorReportsTheWinner(Throwable answer) throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var httpSender = failingSender(answer);
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness)).isSameAs(answer);

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
        verify(httpSender).close();
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
    }

    @ParameterizedTest
    @MethodSource("answersFromALiveProvider")
    void anAnswerFromALiveProviderOnTheLegacySendReachesTheCallerAndStaysOutOfTheDspPath(Throwable answer) throws Exception {
        var harness = createHarness(false);
        stubUnnegotiatedAddress(harness);
        var httpSender = mock(HttpSender.class);
        when(httpSender.getResponseHeaders()).thenThrow(answer);
        stubSenders(harness, httpSender);
        var restRequest = restRequest();

        assertThatThrownBy(() -> harness.processor().processRequest(mock(RequestWrapper.class), null, restRequest,
                restRequest.getClientId(), serviceId(), "req-1", restRequestContext())).isSameAs(answer);

        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        verify(httpSender).close();
        verifyNoInteractions(harness.dspSendFailureHandler());
        verifyNoInteractions(harness.consumerSideDspProcessor());
    }

    @ParameterizedTest
    @MethodSource("answersParsedFromTheFirstResponse")
    void aProviderFaultParsedFromTheFirstResponseEndsTheCallWithoutFailoverOrReport(
            XrdRuntimeException answer, boolean dropsCachedAssetAccess) throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var httpSender = mock(HttpSender.class);
        when(httpSender.getResponseHeaders()).thenThrow(answer);
        stubSenders(harness, httpSender);
        var restRequest = restRequest();

        assertThatThrownBy(() -> harness.processor().processRequest(mock(RequestWrapper.class), null, restRequest,
                restRequest.getClientId(), serviceId(), "req-1", restRequestContext())).isSameAs(answer);

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        verify(httpSender).close();
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());
        verify(harness.consumerSideDspProcessor(), times(dropsCachedAssetAccess ? 1 : 0)).invalidateAssetAccess(any(DspRequest.class));
    }

    @Test
    void aSendThatCompletesDoesNotReachTheFailureHandler() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A);
        stubSenders(harness, mock(HttpSender.class));

        send(harness);

        verifyNoInteractions(harness.dspSendFailureHandler());
    }

    @Test
    void aTokenRejectionOnlyDropsTheEntryAndALaterTransportFaultDropsAndReports() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A);

        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), restRequest(), serviceId(), true);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(any(DspRequest.class));
        verify(harness.consumerSideDspProcessor(), never()).invalidateAssetAccessWonBy(any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(any(), any());

        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault));
        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
    }

    @Test
    void aTokenRejectionOnTheSiblingAfterATransportFaultReportsOnlyTheFailedServer() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A, WON_BY_SS_B);
        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault), mock(HttpSender.class));
        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        send(harness);
        harness.processor().dropAssetAccessOnTokenRejection(
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), restRequest(), serviceId(), true);

        verify(harness.consumerSideDspProcessor()).invalidateAssetAccess(any(DspRequest.class));
        verify(harness.consumerSideDspProcessor(), times(1)).reportCounterPartyFailure(any(), any());
        verify(harness.consumerSideDspProcessor(), never()).reportCounterPartyFailure(eq(SS_B_ID), any());
        verify(harness.consumerSideDspProcessor(), times(1)).invalidateAssetAccessWonBy(any());
    }

    @Test
    void aRefusedConnectionToTheFirstServerIsServedByTheSibling() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var refusedBySsA = failingSender(new ConnectException("refused"));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, refusedBySsA, servedBySsB);

        var sent = send(harness);

        assertThat(sent.getHttpSender()).isSameAs(servedBySsB);
        verify(refusedBySsA).close();
        verify(servedBySsB, never()).close();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), nullable(HttpEntity.class));
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.dspSendFailureHandler()).handle(any(ConnectException.class), eq(WON_BY_SS_A));
        verify(harness.dspSendFailureHandler(), never()).handle(any(), eq(WON_BY_SS_B));
    }

    @Test
    void anUnresolvableHostOfTheFirstServerIsServedByTheSibling() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var unresolvableSsA = failingSender(
                XrdRuntimeException.systemException(IO_ERROR, new UnknownHostException("ss-a.example")));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, unresolvableSsA, servedBySsB);

        var sent = send(harness);

        assertThat(sent.getHttpSender()).isSameAs(servedBySsB);
        verify(unresolvableSsA).close();
        verify(servedBySsB, never()).close();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), nullable(HttpEntity.class));
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.dspSendFailureHandler()).handle(any(XrdRuntimeException.class), eq(WON_BY_SS_A));
        verify(harness.dspSendFailureHandler(), never()).handle(any(), eq(WON_BY_SS_B));
    }

    @Test
    void aTlsHandshakeFailureOfTheFirstServerIsServedByTheSibling() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var failedBySsA = failingSender(new SSLHandshakeException("handshake_failure"));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, failedBySsA, servedBySsB);

        var sent = send(harness);

        assertThat(sent.getHttpSender()).isSameAs(servedBySsB);
        verify(failedBySsA).close();
        verify(servedBySsB, never()).close();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), nullable(HttpEntity.class));
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.dspSendFailureHandler()).handle(any(SSLHandshakeException.class), eq(WON_BY_SS_A));
        verify(harness.dspSendFailureHandler(), never()).handle(any(), eq(WON_BY_SS_B));
    }

    @Test
    void aConnectTimeoutOfTheFirstServerIsServedByTheSibling() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var timedOutSsA = failingSender(new ConnectTimeoutException("connect timed out"));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, timedOutSsA, servedBySsB);

        var sent = send(harness);

        assertThat(sent.getHttpSender()).isSameAs(servedBySsB);
        verify(timedOutSsA).close();
        verify(servedBySsB, never()).close();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), nullable(HttpEntity.class));
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.dspSendFailureHandler()).handle(any(ConnectTimeoutException.class), eq(WON_BY_SS_A));
        verify(harness.dspSendFailureHandler(), never()).handle(any(), eq(WON_BY_SS_B));
    }

    @Test
    void aReadTimeoutOfTheFirstServerIsServedByTheSibling() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var timedOutSsA = failingSender(new SocketTimeoutException("Read timed out"));
        var servedBySsB = mock(HttpSender.class);
        stubSenders(harness, timedOutSsA, servedBySsB);

        var sent = send(harness);

        assertThat(sent.getHttpSender()).isSameAs(servedBySsB);
        verify(timedOutSsA).close();
        verify(servedBySsB, never()).close();
        verify(servedBySsB).doPost(eq(URI.create(WON_BY_SS_B.endpoint())), nullable(HttpEntity.class));
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.dspSendFailureHandler()).handle(any(SocketTimeoutException.class), eq(WON_BY_SS_A));
        verify(harness.dspSendFailureHandler(), never()).handle(any(), eq(WON_BY_SS_B));
    }

    @Test
    void aFailedNewAcquisitionAfterATransportFaultReachesTheCallerAndTheFaultIsLogged() throws Exception {
        var harness = createHarness(true);
        var fault = new ConnectException("refused");
        var acquisitionFailure = XrdRuntimeException.systemException(DSP_ACQUISITION_FAILED, "no provider granted access");
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new DspAcquisition(WON_BY_SS_A, 2))
                .thenThrow(acquisitionFailure);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(WON_BY_SS_A.endpoint())), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(WON_BY_SS_A.endpoint())});
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> send(harness)).isSameAs(acquisitionFailure);

        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        assertThat(loggedWarnings).anySatisfy(warning -> {
            assertThat(warning.getThrown()).isSameAs(fault);
            assertThat(warning.getMessage()).contains("xRequestId: req-1");
        });
    }

    @ParameterizedTest
    @MethodSource("protocolRejections")
    void aProtocolRejectionOnTheNewAcquisitionAfterATransportFaultReachesTheCallerWithItsCode(ErrorCode code) throws Exception {
        var harness = createHarness(true);
        var fault = new ConnectException("refused");
        var rejection = XrdRuntimeException.systemException(code, "refused by sibling");
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new DspAcquisition(WON_BY_SS_A, 2))
                .thenThrow(rejection);
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(WON_BY_SS_A.endpoint())), any(), any(), any()))
                .thenReturn(new URI[]{URI.create(WON_BY_SS_A.endpoint())});
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertRefusalWithCode(thrown, code, rejection));

        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
    }

    @ParameterizedTest
    @MethodSource("protocolRejections")
    void aRefusalFromTheSiblingAfterATransportFaultReachesTheCallerWithItsCode(ErrorCode code) throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var refusal = XrdRuntimeException.systemException(code, "refused by sibling");
        var failedSsA = failingSender(new ConnectException("refused"));
        var refusingSsB = failingSender(refusal);
        stubSenders(harness, failedSsA, refusingSsB);

        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertRefusalWithCode(thrown, code, refusal));

        verify(refusingSsB).addHeader(HEADER_AGREEMENT_TOKEN, SS_B_TOKEN);
        verify(refusingSsB, never()).addHeader(HEADER_AGREEMENT_TOKEN, SS_A_TOKEN);
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
    }

    @ParameterizedTest
    @MethodSource("protocolRejections")
    void aRefusalInTheResponseOfTheSiblingAfterATransportFaultReachesTheCallerWithItsCode(ErrorCode code) throws Exception {
        assertRefusalInTheSiblingResponseReachesTheCaller(code, senderAnsweringWithFault(code));
    }

    @ParameterizedTest
    @MethodSource("protocolRejections")
    void aRefusalInTheMultipartResponseOfTheSiblingAfterATransportFaultReachesTheCallerWithItsCode(ErrorCode code)
            throws Exception {
        assertRefusalInTheSiblingResponseReachesTheCaller(code, senderAnsweringWithMultipartFault(code));
    }

    private void assertRefusalInTheSiblingResponseReachesTheCaller(ErrorCode code, HttpSender refusingSsB) throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var failedSsA = failingSender(new ConnectException("refused"));
        stubSenders(harness, failedSsA, refusingSsB);
        var restRequest = restRequest();

        assertThatThrownBy(() -> harness.processor().processRequest(mock(RequestWrapper.class), null, restRequest,
                restRequest.getClientId(), serviceId(), "req-1", restRequestContext()))
                .satisfies(thrown -> assertCausedByCodeNotIoError(thrown, code));

        verify(refusingSsB).addHeader(HEADER_AGREEMENT_TOKEN, SS_B_TOKEN);
        verify(refusingSsB, never()).addHeader(HEADER_AGREEMENT_TOKEN, SS_A_TOKEN);
        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
    }

    @Test
    void operationalMonitoringRecordsTheSiblingThatServedAfterAFailover() throws Exception {
        var addressResolver = mock(ServiceAddressResolver.class);
        when(addressResolver.resolve(any(), any(), any()))
                .thenReturn(List.of(URI.create("https://ss-a.example:5500/"), URI.create("https://ss-b.example:5500/")));
        var harness = createHarness(true, false, true, addressResolver);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new DspAcquisition(WON_BY_SS_A, 2), new DspAcquisition(WON_BY_SS_B, 2))
                .thenThrow(new AssertionError("more acquisitions than the test expects"));
        stubSenders(harness, failingSender(new ConnectException("refused")), mock(HttpSender.class));
        var opMonitoringData = new OpMonitoringData(CLIENT, 100);
        var restRequest = restRequest();
        var ctx = restRequestContext();

        harness.clientRequestPreparationService().recordServiceSecurityServerAddress(
                serviceId(), restRequest.getTargetSecurityServer(), ctx, opMonitoringData);
        assertThat(opMonitoringData.getData()).containsEntry(SERVICE_SECURITY_SERVER_ADDRESS, "ss-a.example");

        harness.processor().send(mock(RequestWrapper.class), opMonitoringData, restRequest, restRequest.getClientId(),
                serviceId(), "req-1", ctx);

        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        assertThat(opMonitoringData.getData()).containsEntry(SERVICE_SECURITY_SERVER_ADDRESS, "ss-b.example");
    }

    @Test
    void withRequestRetryOffARefusedSendIsNotResentButTheFailedServerIsDroppedAndReported() throws Exception {
        var harness = createHarness(true, false, false);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var fault = new ConnectException("refused");
        var refusedBySsA = failingSender(fault);
        stubSenders(harness, refusedBySsA, mock(HttpSender.class));

        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        verify(refusedBySsA).close();
        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
        verify(harness.consumerSideDspProcessor()).invalidateAssetAccessWonBy(SS_A_ADDRESS);
        verify(harness.consumerSideDspProcessor()).reportCounterPartyFailure(SS_A_ID, SS_A_ADDRESS);
    }

    @Test
    void aFaultAfterPartOfTheBodyWasWrittenIsResentInFullFromTheStartToTheSibling() throws Exception {
        var harness = createHarness(true);
        stubSigning(harness.messageSigningService());
        MessageLog.init(new NullLogManager(harness.globalConfProvider(), mock(ServerConfProvider.class)));
        var body = randomBody();
        var request = mock(RequestWrapper.class);
        when(request.getInputStream()).thenReturn(new ByteArrayInputStream(body));
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var writtenToSsA = new ByteArrayOutputStream();
        var writtenToSsB = new ByteArrayOutputStream();
        var cutOffSsA = mock(HttpSender.class);
        doAnswer(invocation -> {
            invocation.<HttpEntity>getArgument(1).writeTo(new CutOffOutputStream(writtenToSsA, BODY_CUT_OFF));
            return null;
        }).when(cutOffSsA).doPost(any(URI.class), nullable(HttpEntity.class));
        var servedBySsB = mock(HttpSender.class);
        doAnswer(invocation -> {
            invocation.<HttpEntity>getArgument(1).writeTo(writtenToSsB);
            return null;
        }).when(servedBySsB).doPost(any(URI.class), nullable(HttpEntity.class));
        stubSenders(harness, cutOffSsA, servedBySsB);

        var sent = send(harness, request, restRequest());

        assertThat(sent.getHttpSender()).isSameAs(servedBySsB);
        verify(cutOffSsA).close();
        verify(servedBySsB, never()).close();
        assertThat(writtenToSsA.size()).isEqualTo(BODY_CUT_OFF);
        assertThat(writtenToSsB.toByteArray()).startsWith(writtenToSsA.toByteArray());
        assertThat(Bytes.indexOf(writtenToSsB.toByteArray(), body)).isNotNegative();
        verify(harness.dspSendFailureHandler()).handle(any(XrdRuntimeException.class), eq(WON_BY_SS_A));
    }

    @Test
    void whenEveryCandidateRefusesTheCallerGetsTheDataPlaneErrorAfterOneAcquisitionPerCandidate() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 2, WON_BY_SS_A, WON_BY_SS_B);
        var fault = new ConnectException("refused");
        var refusedBySsA = failingSender(fault);
        var refusedBySsB = failingSender(fault);
        stubSenders(harness, refusedBySsA, refusedBySsB);

        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        verify(harness.consumerSideDspProcessor(), times(2)).execute(any());
        verify(harness.httpSenderProvider(), times(2)).createClientHttpSender();
        verify(refusedBySsA).close();
        verify(refusedBySsB).close();
        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_A);
        verify(harness.dspSendFailureHandler()).handle(fault, WON_BY_SS_B);
    }

    @Test
    void aSingleCandidateThatRefusesEndsTheCallWithoutAnotherAcquisition() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A, WON_BY_SS_B);
        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> send(harness)).satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
    }

    @Test
    void aForcedTargetThatRefusesEndsTheCallWithoutAnotherAcquisition() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A, WON_BY_SS_B);
        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault));
        var forcedTarget = SecurityServerId.Conf.create("DEV", "COM", "222", "SS_A");

        assertThatThrownBy(() -> send(harness, mock(RequestWrapper.class), restRequestForcedTo(forcedTarget)))
                .satisfies(thrown -> assertDataPlaneUnreachable(thrown, fault));

        var dspRequest = ArgumentCaptor.forClass(DspRequest.class);
        verify(harness.consumerSideDspProcessor(), times(1)).execute(dspRequest.capture());
        assertThat(dspRequest.getValue().targetSecurityServer()).isEqualTo(forcedTarget);
    }

    @Test
    void theSenderIsClosedWhenTheFailoverCheckRefusesOnTheLastCandidate() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A);
        var refusedSender = failingSender(new ConnectException("refused"));
        stubSenders(harness, refusedSender);

        assertThatThrownBy(() -> send(harness)).isInstanceOf(XrdRuntimeException.class);

        verify(refusedSender).close();
    }

    @Test
    void theUnreachableErrorReachesTheConsumerAsIoErrorWithTheOriginalCodeInTheMetadata() throws Exception {
        var harness = createHarness(true);
        stubAcquisitions(harness, 1, WON_BY_SS_A);
        var fault = new ConnectException("refused");
        stubSenders(harness, failingSender(fault));

        assertThatThrownBy(() -> send(harness)).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(IO_ERROR)).isTrue();
            assertThat(e.isCausedBy(DSP_DATA_PLANE_UNREACHABLE)).isFalse();
            assertThat(e.getErrorCodeMetadata()).contains("originalCode=" + DSP_DATA_PLANE_UNREACHABLE.code());
            assertThat(e.getCause()).isSameAs(fault);
        });
    }

    @Test
    void anAcquisitionFailureIsThrownUnchangedAndNotFailedOver() throws Exception {
        var harness = createHarness(true);
        var failure = XrdRuntimeException.systemException(IO_ERROR, new ConnectException("control plane unreachable"));
        when(harness.consumerSideDspProcessor().execute(any())).thenThrow(failure);

        assertThatThrownBy(() -> send(harness)).isSameAs(failure);

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
        verifyNoInteractions(harness.httpSenderProvider());
        verifyNoInteractions(harness.dspSendFailureHandler());
    }

    @Test
    void aPreparationFailureAfterAcquisitionIsThrownUnchangedAndNotFailedOver() throws Exception {
        var harness = createHarness(true);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(new DspAcquisition(WON_BY_SS_A, 2))
                .thenThrow(new AssertionError("more acquisitions than the test expects"));
        var failure = XrdRuntimeException.systemException(IO_ERROR, new ConnectException("no address"));
        when(harness.clientRequestPreparationService()
                .prepareRequest(any(), any(), eq(URI.create(WON_BY_SS_A.endpoint())), any(), any(), any()))
                .thenThrow(failure);
        var httpSender = mock(HttpSender.class);
        stubSenders(harness, httpSender);

        assertThatThrownBy(() -> send(harness)).isSameAs(failure);

        verify(harness.consumerSideDspProcessor(), times(1)).execute(any());
        verify(harness.httpSenderProvider(), times(1)).createClientHttpSender();
        verify(httpSender).close();
        verify(httpSender, never()).doPost(any(URI.class), nullable(HttpEntity.class));
        verifyNoInteractions(harness.dspSendFailureHandler());
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
                XrdRuntimeException.systemException(SERVICE_FAILED, "service failed"),
                XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"),
                XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"));
    }

    static Stream<Arguments> answersParsedFromTheFirstResponse() {
        return Stream.of(
                Arguments.of(XrdRuntimeException.systemException(SERVICE_FAILED, "service failed"), false),
                Arguments.of(XrdRuntimeException.systemException(HTTP_ERROR, "Server responded with error %s: %s", 500,
                        "Internal Server Error"), false),
                Arguments.of(XrdRuntimeException.systemException(AGREEMENT_TOKEN_REJECTED, "rejected"), true),
                Arguments.of(XrdRuntimeException.systemException(ACCESS_DENIED, "not allowed"), true));
    }

    static Stream<ErrorCode> protocolRejections() {
        return Stream.of(ACCESS_DENIED, SERVICE_FAILED, AGREEMENT_TOKEN_REJECTED);
    }

    private static final String SS_A_ID = "did:web:ss-a.example%3A7183:v1:DEV:COM:222";
    private static final String SS_A_ADDRESS = "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SS_B_ID = "did:web:ss-b.example%3A7183:v1:DEV:COM:222";
    private static final String SS_B_ADDRESS = "https://ss-b.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1";
    private static final String SS_A_TOKEN = "token-issued-by-ss-a";
    private static final String SS_B_TOKEN = "token-issued-by-ss-b";
    private static final AssetAccessResponse WON_BY_SS_A =
            new AssetAccessResponse("https://ss-a.example:8183/data", SS_A_TOKEN, new AcquisitionCandidate(SS_A_ID, SS_A_ADDRESS));
    private static final AssetAccessResponse WON_BY_SS_B =
            new AssetAccessResponse("https://ss-b.example:8183/data", SS_B_TOKEN, new AcquisitionCandidate(SS_B_ID, SS_B_ADDRESS));

    /**
     * Makes {@code execute} return the given accesses in order, each reporting {@code candidateCount} candidates,
     * and fail the test on any further acquisition, so a broken attempt limit cannot loop.
     */
    private static void stubAcquisitions(RestHarness harness, int candidateCount, AssetAccessResponse... accesses) {
        var acquisitions = Stream.of(accesses)
                .map(access -> new DspAcquisition(access, candidateCount))
                .toArray(DspAcquisition[]::new);
        var remaining = Arrays.copyOfRange(acquisitions, 1, acquisitions.length);
        when(harness.consumerSideDspProcessor().execute(any()))
                .thenReturn(acquisitions[0], remaining)
                .thenThrow(new AssertionError("more acquisitions than the test expects"));
        for (var access : accesses) {
            when(harness.clientRequestPreparationService()
                    .prepareRequest(any(), any(), eq(URI.create(access.endpoint())), any(), any(), any()))
                    .thenReturn(new URI[]{URI.create(access.endpoint())});
        }
    }

    private static void stubSenders(RestHarness harness, HttpSender first, HttpSender... rest) {
        when(harness.httpSenderProvider().createClientHttpSender()).thenReturn(first, rest);
    }

    private static SentRequest send(RestHarness harness) throws Exception {
        return send(harness, mock(RequestWrapper.class), restRequest());
    }

    private static SentRequest send(RestHarness harness, RequestWrapper jRequest, RestRequest restRequest) throws Exception {
        return harness.processor().send(jRequest, null, restRequest, restRequest.getClientId(),
                serviceId(), "req-1", restRequestContext());
    }

    private static void stubSigning(MessageSigningService messageSigningService) throws Exception {
        var chain = mock(CertChain.class);
        when(chain.getAllCertsWithoutTrustedRoot()).thenReturn(List.of());
        when(messageSigningService.getAuthKey()).thenReturn(new AuthKey(chain, null));
        when(messageSigningService.getAllOcspResponses(any())).thenReturn(List.of());
        when(messageSigningService.createSigningCtx(any()))
                .thenReturn(builder -> new SignatureData("<Signature>test-signature</Signature>"));
    }

    private static byte[] randomBody() {
        var body = new byte[BODY_SIZE];
        new Random(42).nextBytes(body);
        return body;
    }

    private static void assertDataPlaneUnreachable(Throwable thrown, Throwable cause) {
        assertThat(thrown).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(IO_ERROR)).isTrue();
            assertThat(e.getErrorCodeMetadata()).contains("originalCode=" + DSP_DATA_PLANE_UNREACHABLE.code());
            assertThat(e.getCause()).isSameAs(cause);
        });
    }

    private static void assertRefusalWithCode(Throwable thrown, ErrorCode code, XrdRuntimeException refusal) {
        assertThat(thrown).isSameAs(refusal);
        assertCausedByCodeNotIoError(thrown, code);
    }

    private static void assertCausedByCodeNotIoError(Throwable thrown, ErrorCode code) {
        assertThat(thrown).isInstanceOfSatisfying(XrdRuntimeException.class, e -> {
            assertThat(e.isCausedBy(code)).isTrue();
            assertThat(e.isCausedBy(IO_ERROR)).isFalse();
        });
    }

    private static HttpSender senderAnsweringWithFault(ErrorCode code) throws Exception {
        return senderAnswering(TEXT_XML_UTF8, faultXml(code).getBytes(StandardCharsets.UTF_8));
    }

    private static HttpSender senderAnsweringWithMultipartFault(ErrorCode code) throws Exception {
        var body = new ByteArrayOutputStream();
        var encoder = new ProxyMessageEncoder(body, DEFAULT_DIGEST_ALGORITHM);
        encoder.fault(faultXml(code));
        encoder.close();
        return senderAnswering(encoder.getContentType(), body.toByteArray());
    }

    private static HttpSender senderAnswering(String contentType, byte[] body) throws Exception {
        var httpSender = mock(HttpSender.class);
        when(httpSender.getResponseHeaders()).thenReturn(Map.of());
        when(httpSender.getResponseContentType()).thenReturn(contentType);
        when(httpSender.getResponseContent()).thenReturn(new ByteArrayInputStream(body));
        return httpSender;
    }

    private static String faultXml(ErrorCode code) {
        return SoapFault.createFaultXml(XrdRuntimeException.systemException(code, "refused by sibling"));
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

    private static RestRequest restRequestForcedTo(SecurityServerId target) {
        var header = new BasicHeader(HEADER_SECURITY_SERVER, String.join("/", target.getXRoadInstance(),
                target.getMemberClass(), target.getMemberCode(), target.getServerCode()));
        return new RestRequest("GET", "/r1/DEV/COM/222/TestClient/testService", null, List.of(header), "req-1");
    }

    private static ServiceId serviceId() {
        return ServiceId.Conf.create("DEV", "COM", "222", "TestClient", "testService");
    }

    private static RestRequestContext restRequestContext() {
        return new RestRequestContext(mock(RequestWrapper.class), mock(ResponseWrapper.class), null);
    }

    private static final class CutOffOutputStream extends OutputStream {
        private final OutputStream sink;
        private int remaining;

        CutOffOutputStream(OutputStream sink, int allowedBytes) {
            this.sink = sink;
            this.remaining = allowedBytes;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            var allowed = Math.min(len, remaining);
            sink.write(b, off, allowed);
            remaining -= allowed;
            if (allowed < len) {
                throw new SSLHandshakeException("Received fatal alert: certificate_required");
            }
        }
    }

    private record RestHarness(ClientRestMessageProcessor processor, DspRequestProcessor consumerSideDspProcessor,
                               ClientRequestPreparationService clientRequestPreparationService,
                               GlobalConfProvider globalConfProvider, LegacyCallRefusalPolicy legacyCallRefusalPolicy,
                               DspSendFailureHandler dspSendFailureHandler, HttpSenderProvider httpSenderProvider,
                               MessageSigningService messageSigningService) {
    }

    private RestHarness createHarness(boolean dspEnabled) {
        return createHarness(dspEnabled, false);
    }

    private RestHarness createHarness(boolean dspEnabled, boolean legacyProtocolDisabled) {
        return createHarness(dspEnabled, legacyProtocolDisabled, true);
    }

    private RestHarness createHarness(boolean dspEnabled, boolean legacyProtocolDisabled, boolean requestRetryEnabled) {
        return createHarness(dspEnabled, legacyProtocolDisabled, requestRetryEnabled, null);
    }

    private RestHarness createHarness(boolean dspEnabled, boolean legacyProtocolDisabled, boolean requestRetryEnabled,
                                      ServiceAddressResolver addressResolverOfRealPreparation) {
        var globalConfProvider = mock(GlobalConfProvider.class);
        var serverConfProvider = mock(ServerConfProvider.class);
        var proxyProperties = new ProxyProperties(XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.proxy.dsp-enabled", String.valueOf(dspEnabled),
                        "xroad.proxy.legacy-protocol-disabled", String.valueOf(legacyProtocolDisabled),
                        "xroad.proxy.client-proxy.enable-request-retry", String.valueOf(requestRetryEnabled)))
                .build());
        var commonProperties = new XRoadConfigCommonProperties(XRoadConfigBuilder.create()
                .register(CommonConfigKeys.instance())
                .overrides(Map.of("xroad.common.temp-files-path", tempDir.toString()))
                .build());
        var opMonitoringDataHelper = new OpMonitoringDataHelper(globalConfProvider, serverConfProvider,
                new OwnSecurityServerResolver(new ServerConfProviderIdentitySource(serverConfProvider), globalConfProvider));
        var clientRequestPreparationService = addressResolverOfRealPreparation == null
                ? mock(ClientRequestPreparationService.class)
                : new ClientRequestPreparationService(addressResolverOfRealPreparation, proxyProperties,
                opMonitoringDataHelper, new UnusableAddressTracker(Duration.ofSeconds(180), Ticker.systemTicker()));
        var consumerSideDspProcessor = mock(DspRequestProcessor.class);
        var legacyCallRefusalPolicy = spy(new LegacyCallRefusalPolicy(proxyProperties, globalConfProvider));
        var dspSendFailureHandler = spy(new DspSendFailureHandler(consumerSideDspProcessor));
        var httpSenderProvider = mock(HttpSenderProvider.class);

        var messageSigningService = mock(MessageSigningService.class);

        var processor = new ClientRestMessageProcessor(
                messageSigningService,
                httpSenderProvider,
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
                globalConfProvider, legacyCallRefusalPolicy, dspSendFailureHandler, httpSenderProvider,
                messageSigningService);
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
