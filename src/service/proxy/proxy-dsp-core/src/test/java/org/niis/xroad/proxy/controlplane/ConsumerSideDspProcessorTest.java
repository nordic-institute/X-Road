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
package org.niis.xroad.proxy.controlplane;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.core.dsp.AcquisitionCandidate;
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerSideDspProcessorTest {

    private static final AcquisitionCandidate WINNER = new AcquisitionCandidate(
            "did:web:ss-a.example%3A7183:v1:DEV:COM:222",
            "https://ss-a.example:8183/api/dsp/DEV:COM:222/http-dsp-profile-2025-1");
    private static final String INSTANCE = "DEV";
    private static final String HOST_A = "xrd-ss0.lxd";
    private static final String HOST_B = "xrd-ss1.lxd";
    private static final String HOST_C = "xrd-ss2.lxd";
    private static final String PROVIDER_MEMBER_CTX_ID = "DEV:COM:1234";
    private static final String DID_A = "did:web:xrd-ss0.lxd%3A7183:v1:" + PROVIDER_MEMBER_CTX_ID;
    private static final String DID_B = "did:web:xrd-ss1.lxd%3A7183:v1:" + PROVIDER_MEMBER_CTX_ID;
    private static final String URL_A = "https://xrd-ss0.lxd:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1";
    private static final String URL_B = "https://xrd-ss1.lxd:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1";
    private static final String SYSTEM_DID_A = "did:web:xrd-ss0.lxd%3A7183:v1:system";
    private static final String SYSTEM_URL_A = "https://xrd-ss0.lxd:8183/api/dsp/system/http-dsp-profile-2025-1";
    private static final ClientId SENDER = ClientId.Conf.create(INSTANCE, "COM", "4321", "SenderSub");
    private static final String SENDER_MEMBER_CTX_ID = "DEV:COM:4321";
    private static final Duration DEADLINE = Duration.ofSeconds(60);

    @Mock
    private AssetAccessAcquisitionService assetAccessAcquisitionService;
    @Mock
    private AssetAccessClientProperties clientProperties;

    private ConsumerSideDspProcessor processor;
    private ServiceId serviceId;

    @BeforeEach
    void setUp() {
        lenient().when(clientProperties.acquisitionDeadline()).thenReturn(DEADLINE);
        processor = new ConsumerSideDspProcessor(assetAccessAcquisitionService, clientProperties);
        serviceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "testService", "v1");
    }

    @Test
    void ordinaryRequestNegotiatesAsSenderMemberDerivedContextWithTheSenderAsConsumerClient() {
        stubAcquisition();

        processor.execute(requestTo(HOST_A));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(DID_A, URL_A)), eq(SENDER), any());
    }

    @Test
    void invalidateDropsCachedAssetAccessOfTheSenderMemberContextAndServiceAsset() {
        processor.invalidateAssetAccess(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService)
                .invalidateAssetAccess(SENDER_MEMBER_CTX_ID, serviceId.asEncodedId(), SENDER);
    }

    @Test
    void invalidatingByWinnerReachesTheAcquisitionService() {
        processor.invalidateAssetAccessWonBy(URL_A);

        verify(assetAccessAcquisitionService).invalidateAssetAccessWonBy(URL_A);
    }

    @Test
    void reportingAFailureReachesTheAcquisitionService() {
        processor.reportCounterPartyFailure(DID_A, URL_A);

        verify(assetAccessAcquisitionService).reportCounterPartyFailure(DID_A, URL_A);
    }

    @Test
    void managementRequestNegotiatesAsSenderMemberContextWithTheSuppliedSystemCandidate() {
        var managementServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "ManagementSub", "clientReg");
        stubAcquisition();

        processor.execute(new DspRequest(managementServiceId, SENDER, null, true)
                .withCandidates(single(SYSTEM_DID_A, SYSTEM_URL_A)));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(SYSTEM_DID_A, SYSTEM_URL_A)), eq(SENDER), any());
    }

    @Test
    void executeReturnsAssetAccessResponseWithEndpointAndAuthorization() {
        var expected = new AssetAccessResponse("http://dp.example.com/endpoint", "token-abc", WINNER);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any())).thenReturn(expected);

        var result = processor.execute(requestTo(HOST_A));

        assertThat(result.access()).isSameAs(expected);
        assertThat(result.access().endpoint()).isEqualTo("http://dp.example.com/endpoint");
        assertThat(result.access().authorization()).isEqualTo("token-abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"listMethods", "allowedMethods", "getWsdl", "getOpenAPI"})
    void subsystemAddressedMetaserviceBypassesDsp(String serviceCode) {
        var metaservice = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", serviceCode);

        assertThat(processor.bypassesDsp(new DspRequest(metaservice, SENDER, null, false))).isTrue();
    }

    @Test
    void subsystemAddressedMetaserviceWithAVersionBypassesDsp() {
        var metaservice = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "listMethods", "v1");

        assertThat(processor.bypassesDsp(new DspRequest(metaservice, SENDER, null, false))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"listMethods", "allowedMethods", "getWsdl", "getOpenAPI"})
    void memberAddressedMetaserviceBypassesDsp(String serviceCode) {
        var metaservice = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, serviceCode);

        assertThat(processor.bypassesDsp(new DspRequest(metaservice, SENDER, null, false))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"getSecurityServerMetrics", "getSecurityServerOperationalData",
            "getSecurityServerHealthData", "testService"})
    void memberAddressedNonMetaserviceDoesNotBypassDsp(String serviceCode) {
        var service = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, serviceCode);

        assertThat(processor.bypassesDsp(new DspRequest(service, SENDER, null, false))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"getSecurityServerMetrics", "getSecurityServerOperationalData",
            "getSecurityServerHealthData", "testService"})
    void subsystemAddressedNonMetaserviceDoesNotBypassDsp(String serviceCode) {
        var service = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", serviceCode);

        assertThat(processor.bypassesDsp(new DspRequest(service, SENDER, null, false))).isFalse();
    }

    @Test
    void assetIdDerivedFromServiceIdEncoding() {
        stubAcquisition();

        processor.execute(requestTo(HOST_A));

        var assetIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), assetIdCaptor.capture(), any(), any(), any());
        assertThat(assetIdCaptor.getValue()).isEqualTo(serviceId.asEncodedId());
    }

    @Test
    void executeCountsEveryDistinctCandidate() {
        stubAcquisition();

        var result = processor.execute(requestTo(HOST_A, HOST_B, HOST_C));

        assertThat(result.candidateCount()).isEqualTo(3);
    }

    @Test
    void executeHandsTheConfiguredAcquisitionDeadlineToTheAcquisition() {
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), eq(DEADLINE)))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(requestTo(HOST_A));

        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), any(), any(), any(), eq(DEADLINE));
    }

    @Test
    void executeCountsCandidatesOfTheSameServerOnce() {
        stubAcquisition();

        var result = processor.execute(requestTo(HOST_A, HOST_A, HOST_B));

        assertThat(result.candidateCount()).isEqualTo(2);
    }

    @Test
    void aTriedServerIsDroppedFromTheCandidatesRegardlessOfLetterCase() {
        stubAcquisition();
        var tried = "HTTPS://XRD-SS0.LXD:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1";

        var result = processor.execute(requestTo(HOST_A, HOST_B).withTriedServer(tried));

        assertThat(result.candidateCount()).isEqualTo(1);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_B, URL_B)), any(), any());
    }

    @Test
    void aTriedServerIsDroppedWhateverParticipantContextItsAddressNames() {
        stubAcquisition();

        var result = processor.execute(requestTo(HOST_A, HOST_B).withTriedServer(SYSTEM_URL_A));

        assertThat(result.candidateCount()).isEqualTo(1);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_B, URL_B)), any(), any());
    }

    @Test
    void aTriedAddressOnTheDefaultPortDoesNotDropTheServerOfTheDspPort() {
        stubAcquisition();
        var tried = "https://xrd-ss0.lxd/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1";

        var result = processor.execute(requestTo(HOST_A, HOST_B).withTriedServer(tried));

        assertThat(result.candidateCount()).isEqualTo(2);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(List.of(new AcquisitionCandidate(DID_A, URL_A), new AcquisitionCandidate(DID_B, URL_B))), any(), any());
    }

    @Test
    void theCandidateCountIsTheNumberOfDistinctServersLeftAfterTheTriedOnesAreDropped() {
        stubAcquisition();

        var result = processor.execute(requestTo(HOST_A, HOST_A, HOST_B, HOST_C).withTriedServer(
                "https://xrd-ss2.lxd:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1"));

        assertThat(result.candidateCount()).isEqualTo(2);
    }

    @Test
    void sendsOneAcquisitionCallCarryingEveryCandidate() {
        stubAcquisition();

        processor.execute(requestTo(HOST_A, HOST_B));

        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(
                any(), any(),
                argThat(candidates -> candidates.size() == 2 && candidates.containsAll(List.of(
                        new AcquisitionCandidate(DID_A, URL_A),
                        new AcquisitionCandidate(DID_B, URL_B)))), any(), any());
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(any(), any(), any(), any(), any());
    }

    @Test
    void aRequestWithNoCandidatesYieldsUnknownMemberWithOriginalDspCode() {
        assertThatThrownBy(() -> processor.execute(requestTo()))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.UNKNOWN_MEMBER)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_PROVIDER_NOT_FOUND.code());
                    assertThat(xrd.getDetails())
                            .isNotBlank()
                            .doesNotContain(serviceId.asEncodedId())
                            .doesNotContainIgnoringCase("dsp_");
                });

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any(), any(), any());
    }

    @Test
    void aRequestWhoseEveryCandidateWasTriedYieldsUnknownMemberAndNoAcquisitionCall() {
        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A).withTriedServer(URL_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).isCausedBy(ErrorCode.UNKNOWN_MEMBER)).isTrue());

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dsp_dataset_not_found", "dsp_offers_not_found"})
    void remoteNotFoundFromReachedProviderMapsToAccessDenied(String notFoundCode) {
        var dspException = XrdRuntimeException.systemException(ErrorCode.withCode("proxy.dataspace." + notFoundCode)).build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
                    assertThat(xrd.getDetails())
                            .isEqualTo("The request is not allowed by the provider security server.")
                            .doesNotContain("No catalog offer found for service")
                            .doesNotContain(serviceId.asEncodedId());
                    assertThat(xrd.getErrorCodeMetadata()).containsExactly("originalCode=" + notFoundCode);
                });
    }

    @Test
    void dspExceptionMetadataRoundTripCarriesOriginalCode() {
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_NEGOTIATION_FAILED.code()))
                .details("negotiation timed out")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.SERVICE_FAILED)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_NEGOTIATION_FAILED.code());
                });
    }

    @Test
    void acquisitionFailedFromAllCandidatesYieldsIoErrorWithOriginalDspCode() {
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_ACQUISITION_FAILED.code()))
                .details("All 2 candidates failed")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A, HOST_B)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_ACQUISITION_FAILED.code());
                    assertThat(xrd.getDetails())
                            .isNotBlank()
                            .doesNotContain("candidates failed")
                            .doesNotContain(serviceId.asEncodedId());
                });
    }

    @Test
    void nonDspXRoadFailureFromAcquisitionPassesThroughUnchanged() {
        var failure = XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR, "catalog fetch failed");
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A, HOST_B)))
                .isSameAs(failure)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.getCode()).isEqualTo(ErrorCode.NETWORK_ERROR.code());
                    assertThat(xrd.getDetails()).isEqualTo("catalog fetch failed");
                });
    }

    @Test
    void unmappedAcquisitionFailureYieldsSanitizedIoError() {
        var grpcFailure = new StatusRuntimeException(Status.UNAVAILABLE);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any()))
                .thenThrow(grpcFailure);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_ACQUISITION_FAILED.code());
                    assertThat(xrd.getCause()).isSameAs(grpcFailure);
                    assertThat(xrd.getDetails())
                            .doesNotContain("UNAVAILABLE")
                            .doesNotContain(serviceId.asEncodedId());
                });
    }

    @Test
    void dspCatalogFetchFailedPerCandidateMapsToIoError() {
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_CATALOG_FETCH_FAILED.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_CATALOG_FETCH_FAILED.code());
                });
    }

    @Test
    void anchorCatalogFetchFailedSurfacesGenericDetailsWithNoDspOrEdcLeak() {
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_CATALOG_FETCH_FAILED.code()))
                .details("EDC catalog request to " + URL_A + " failed after 3 candidate hosts")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getDetails())
                            .isNotBlank()
                            .doesNotContain(URL_A)
                            .doesNotContain("3 candidate hosts")
                            .doesNotContainIgnoringCase("dsp_")
                            .doesNotContainIgnoringCase("edc");
                });
    }

    @Test
    void fastPathUnrecognizedDspCodeIsCollapsedToGenericInsteadOfLeaking() {
        var futureDspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace.dsp_unknown_future_code"))
                .details("host=" + HOST_A + " candidate acquisition failed via edc negotiation API")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any()))
                .thenThrow(futureDspException);

        assertThatThrownBy(() -> processor.execute(requestTo(HOST_A)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.getCode()).isEqualTo(ErrorCode.IO_ERROR.code());
                    assertThat(xrd.getDetails())
                            .isNotBlank()
                            .doesNotContain(HOST_A)
                            .doesNotContainIgnoringCase("dsp_")
                            .doesNotContainIgnoringCase("edc");
                    assertThat(xrd.getErrorCodeMetadata()).containsExactly("originalCode=dsp_unknown_future_code");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dsp_access_refused", "dsp_dataset_not_found", "dsp_offers_not_found"})
    void aRememberedRefusalReachesTheClientLikeTheFirstRefusal(String refusalCode) {
        var refusal = XrdRuntimeException.systemException(ErrorCode.withCode("proxy.dataspace." + refusalCode)).build();
        var memory = new RefusalMemory(() -> 0L);
        var key = new AssetAccessCache.CacheKey(SENDER_MEMBER_CTX_ID, serviceId.asEncodedId(),
                Set.copyOf(single(DID_A, URL_A)), "http-dsp-profile-2025-1", SENDER.asEncodedId());
        memory.rememberIfRefusal(key, refusal);
        var request = requestTo(HOST_A);

        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any()))
                .thenThrow(refusal);
        var first = (XrdRuntimeException) catchThrowable(() -> processor.execute(request));
        doThrow(memory.recall(key).orElseThrow())
                .when(assetAccessAcquisitionService).acquireAssetAccess(any(), any(), any(), any(), any());
        var repeat = (XrdRuntimeException) catchThrowable(() -> processor.execute(request));

        assertThat(repeat.getCode()).isEqualTo(first.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        assertThat(repeat.getDetails()).isEqualTo(first.getDetails());
        assertThat(repeat.getErrorCodeMetadata())
                .isEqualTo(first.getErrorCodeMetadata())
                .containsExactly("originalCode=" + refusalCode);
    }

    private void stubAcquisition() {
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));
    }

    private DspRequest requestTo(String... hosts) {
        return new DspRequest(serviceId, SENDER, null, false)
                .withCandidates(Stream.of(hosts).map(ConsumerSideDspProcessorTest::memberCandidate).toList());
    }

    private static AcquisitionCandidate memberCandidate(String host) {
        return new AcquisitionCandidate(
                "did:web:" + host + "%3A7183:v1:" + PROVIDER_MEMBER_CTX_ID,
                "https://" + host + ":8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1");
    }

    private static List<AcquisitionCandidate> single(String counterPartyId, String counterPartyAddress) {
        return List.of(new AcquisitionCandidate(counterPartyId, counterPartyAddress));
    }
}
