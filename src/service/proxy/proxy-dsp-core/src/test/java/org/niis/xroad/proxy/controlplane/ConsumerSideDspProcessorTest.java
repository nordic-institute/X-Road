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
import ee.ria.xroad.common.identifier.SecurityServerId;
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
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;

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
import static org.mockito.ArgumentMatchers.isNull;
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
    private static final String SYSTEM_DID_B = "did:web:xrd-ss1.lxd%3A7183:v1:system";
    private static final String SYSTEM_URL_A = "https://xrd-ss0.lxd:8183/api/dsp/system/http-dsp-profile-2025-1";
    private static final String SYSTEM_URL_B = "https://xrd-ss1.lxd:8183/api/dsp/system/http-dsp-profile-2025-1";
    private static final String BLANK_HOST = "";
    private static final String UNKNOWN_HOST = "unknown.example.com";
    private static final ClientId SENDER = ClientId.Conf.create(INSTANCE, "COM", "4321", "SenderSub");
    private static final String SENDER_MEMBER_CTX_ID = "DEV:COM:4321";
    private static final Duration DEADLINE = Duration.ofSeconds(60);

    @Mock
    private AssetAccessAcquisitionService assetAccessAcquisitionService;
    @Mock
    private ProviderSecurityServerResolver providerSecurityServerResolver;
    @Mock
    private AssetAccessClientProperties clientProperties;

    private ConsumerSideDspProcessor processor;
    private ServiceId serviceId;

    @BeforeEach
    void setUp() {
        lenient().when(clientProperties.acquisitionDeadline()).thenReturn(DEADLINE);
        processor = new ConsumerSideDspProcessor(assetAccessAcquisitionService, providerSecurityServerResolver,
                clientProperties);
        serviceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "testService", "v1");
    }

    @Test
    void ordinaryRequestNegotiatesAsSenderMemberDerivedContext() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(DID_A, URL_A)), any(), any(), any());
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
    void managementRequestTargetsDerivedSystemDidAndUrlAndNegotiatesAsSenderMemberContext() {
        var managementServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "ManagementSub", "clientReg");
        when(providerSecurityServerResolver.resolve(managementServiceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(managementServiceId, SENDER, null, true));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(SYSTEM_DID_A, SYSTEM_URL_A)), any(), any(), any());
    }

    @Test
    void nonWhitelistedServiceUnderManagementSubsystemUsesMemberTargetNotSystem() {
        var nonWhitelistedService = ServiceId.Conf.create(INSTANCE, "COM", "1234", "ManagementSub", "otherService");
        when(providerSecurityServerResolver.resolve(nonWhitelistedService, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(nonWhitelistedService, SENDER, null, true));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void versionedManagementServiceUnderManagementSubsystemUsesMemberTargetNotSystem() {
        var versionedService = ServiceId.Conf.create(INSTANCE, "COM", "1234", "ManagementSub", "clientReg", "v1");
        when(providerSecurityServerResolver.resolve(versionedService, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(versionedService, SENDER, null, true));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void builtinServiceTargetsDerivedSystemDidAndUrlAndNegotiatesAsSenderMemberContext() {
        var builtinServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, "listMethods");
        when(providerSecurityServerResolver.resolve(builtinServiceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(builtinServiceId, SENDER, null, false));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(SYSTEM_DID_A, SYSTEM_URL_A)), any(), any(), any());
    }

    @Test
    void managementRequestTargetsDerivedSystemDidAndUrlForCrossServerCandidate() {
        var managementServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "ManagementSub", "clientReg");
        when(providerSecurityServerResolver.resolve(managementServiceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(managementServiceId, SENDER, null, true));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(SYSTEM_DID_B, SYSTEM_URL_B)), any(), any(), any());
    }

    @Test
    void builtinServiceTargetsDerivedSystemDidAndUrlForCrossServerCandidate() {
        var builtinServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, "getSecurityServerHealthData");
        when(providerSecurityServerResolver.resolve(builtinServiceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(builtinServiceId, SENDER, null, false));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(SYSTEM_DID_B, SYSTEM_URL_B)), any(), any(), any());
    }

    @Test
    void executeReturnsAssetAccessResponseWithEndpointAndAuthorization() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var expected = new AssetAccessResponse("http://dp.example.com/endpoint", "token-abc", WINNER);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any())).thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false));

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
    void serverLevelMetaserviceDoesNotBypassDsp(String serviceCode) {
        var metaservice = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, serviceCode);

        assertThat(processor.bypassesDsp(new DspRequest(metaservice, SENDER, null, false))).isFalse();
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
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        var assetIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), assetIdCaptor.capture(), any(), any(), any(), any());
        assertThat(assetIdCaptor.getValue()).isEqualTo(serviceId.asEncodedId());
    }

    @Test
    void counterPartyIdAndAddressDerivedFromProviderMemberAndCandidateAddress() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void memberTargetDerivedForAnyRegisteredAddress() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, UNKNOWN_HOST)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), any(),
                eq(List.of(new AcquisitionCandidate("did:web:unknown.example.com%3A7183:v1:" + PROVIDER_MEMBER_CTX_ID,
                        "https://unknown.example.com:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1"))),
                any(), any(), any());
    }

    @Test
    void providerSubsystemCollapsesToMemberInDerivedTarget() {
        var otherSubsystemService = ServiceId.Conf.create(INSTANCE, "COM", "1234", "OtherSub", "otherService");
        when(providerSecurityServerResolver.resolve(otherSubsystemService, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(otherSubsystemService, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void memberCodeRequiringEncodingIsPercentEncodedInDerivedTarget() {
        var encodedMemberService = ServiceId.Conf.create(INSTANCE, "COM", "12+34", "Sub", "svc");
        when(providerSecurityServerResolver.resolve(encodedMemberService, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(encodedMemberService, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), any(),
                eq(List.of(new AcquisitionCandidate("did:web:xrd-ss0.lxd%3A7183:v1:DEV:COM:12%2B34",
                        "https://xrd-ss0.lxd:8183/api/dsp/DEV:COM:12%252B34/http-dsp-profile-2025-1"))),
                any(), any(), any());
    }

    @Test
    void securityServerHintRestrictsAcquireToThatServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-a");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenReturn(List.of(new ProviderAddress(hint, HOST_A)));
        var expected = new AssetAccessResponse("http://dp/e", null, WINNER);
        when(assetAccessAcquisitionService.acquireAssetAccess(
                any(), eq(serviceId.asEncodedId()), eq(single(DID_A, URL_A)), any(), any(), any())).thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, SENDER, hint, false));

        assertThat(result.access()).isSameAs(expected);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), eq(serviceId.asEncodedId()), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void executeCountsEveryDistinctCandidate() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B),
                        new ProviderAddress(null, HOST_C)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false));

        assertThat(result.candidateCount()).isEqualTo(3);
    }

    @Test
    void executeHandsTheConfiguredAcquisitionDeadlineToTheAcquisition() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), eq(DEADLINE)))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), any(), any(), any(), any(), eq(DEADLINE));
    }

    @Test
    void executeCountsAForcedTargetAsOneCandidate() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-b");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenReturn(List.of(new ProviderAddress(hint, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        var result = processor.execute(new DspRequest(serviceId, SENDER, hint, false));

        assertThat(result.candidateCount()).isEqualTo(1);
    }

    @Test
    void executeCountsCandidatesOfTheSameServerOnce() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false));

        assertThat(result.candidateCount()).isEqualTo(2);
    }

    @Test
    void aTriedServerIsDroppedFromTheCandidatesRegardlessOfLetterCase() {
        stubProviderAddressesAndAcquisition(HOST_A, HOST_B);
        var tried = "HTTPS://XRD-SS0.LXD:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1";

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false).withTriedServer(tried));

        assertThat(result.candidateCount()).isEqualTo(1);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_B, URL_B)), any(), any(), any());
    }

    @Test
    void aTriedServerIsDroppedWhateverParticipantContextItsAddressNames() {
        stubProviderAddressesAndAcquisition(HOST_A, HOST_B);

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false).withTriedServer(SYSTEM_URL_A));

        assertThat(result.candidateCount()).isEqualTo(1);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_B, URL_B)), any(), any(), any());
    }

    @Test
    void aTriedAddressOnTheDefaultPortDoesNotDropTheServerOfTheDspPort() {
        stubProviderAddressesAndAcquisition(HOST_A, HOST_B);
        var tried = "https://xrd-ss0.lxd/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1";

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false).withTriedServer(tried));

        assertThat(result.candidateCount()).isEqualTo(2);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(List.of(new AcquisitionCandidate(DID_A, URL_A), new AcquisitionCandidate(DID_B, URL_B))),
                any(), any(), any());
    }

    @Test
    void theCandidateCountIsTheNumberOfDistinctServersLeftAfterTheTriedOnesAreDropped() {
        stubProviderAddressesAndAcquisition(HOST_A, HOST_A, HOST_B, HOST_C);

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false).withTriedServer(
                "https://xrd-ss2.lxd:8183/api/dsp/" + PROVIDER_MEMBER_CTX_ID + "/http-dsp-profile-2025-1"));

        assertThat(result.candidateCount()).isEqualTo(2);
    }

    @Test
    void executeDoesNotCountAnAddressWhoseTargetCannotBeDerived() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, BLANK_HOST),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        var result = processor.execute(new DspRequest(serviceId, SENDER, null, false));

        assertThat(result.candidateCount()).isEqualTo(1);
    }

    @Test
    void sendsOneAcquisitionCallCarryingEveryCandidate() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));
        processor.execute(new DspRequest(serviceId, SENDER, null, false));
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(
                any(), any(),
                argThat(candidates -> candidates.size() == 2 && candidates.containsAll(List.of(
                        new AcquisitionCandidate(DID_A, URL_A),
                        new AcquisitionCandidate(DID_B, URL_B)))),
                isNull(), any(), any());
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void systemContextRequestSendsOneCallCarryingEverySystemCandidate() {
        var builtinServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, "listMethods");
        when(providerSecurityServerResolver.resolve(builtinServiceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));
        processor.execute(new DspRequest(builtinServiceId, SENDER, null, false));
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(
                any(), any(),
                argThat(candidates -> candidates.size() == 2 && candidates.containsAll(List.of(
                        new AcquisitionCandidate(SYSTEM_DID_A, SYSTEM_URL_A),
                        new AcquisitionCandidate(SYSTEM_DID_B, SYSTEM_URL_B)))),
                any(), any(), any());
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void forcedTargetSendsAOneElementCandidateSetAndTheForcedTarget() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-b");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenReturn(List.of(new ProviderAddress(hint, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));
        processor.execute(new DspRequest(serviceId, SENDER, hint, false));
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(
                eq(SENDER_MEMBER_CTX_ID), eq(serviceId.asEncodedId()),
                eq(single(DID_B, URL_B)), eq(hint), eq(SENDER), any());
    }

    @Test
    void hintNotAmongProviderServersThrowsInvalidSecurityServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-x");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenThrow(XrdRuntimeException.systemException(
                        ErrorCode.INVALID_SECURITY_SERVER, "Invalid security server \"%s\"".formatted(hint)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, hint, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.INVALID_SECURITY_SERVER.code()));

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void noProviderAddressesThrowsUnknownMember() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenThrow(XrdRuntimeException.systemException(
                        ErrorCode.UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"".formatted(serviceId)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.UNKNOWN_MEMBER.code()));

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void emptyCandidateListThrowsUnknownMember() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.UNKNOWN_MEMBER.code()));

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonManagementRequestTargetsDerivedMemberDidAndUrl() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void selfCallCandidateByServerIdUsesMemberTarget() {
        var candidateServerId = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss0-local");
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(candidateServerId, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void builtinServiceCodeOnSubsystemUsesMemberTarget() {
        var notBuiltin = ServiceId.Conf.create(INSTANCE, "COM", "1234", "Sub", "getSecurityServerMetrics");
        when(providerSecurityServerResolver.resolve(notBuiltin, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(notBuiltin, SENDER, null, false));

        verify(assetAccessAcquisitionService).acquireAssetAccess(
                any(), any(), eq(single(DID_A, URL_A)), any(), any(), any());
    }

    @Test
    void remoteDatasetNotFoundFromReachedProviderMapsToAccessDenied() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_DATASET_NOT_FOUND.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED.code()));
    }

    @Test
    void remoteOffersNotFoundFromReachedProviderMapsToAccessDenied() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_OFFERS_NOT_FOUND.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED.code()));
    }

    @Test
    void remoteDatasetNotFoundSanitizesToGenericAccessDeniedMessage() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_DATASET_NOT_FOUND.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
                    assertThat(xrd.getDetails())
                            .isEqualTo("The request is not allowed by the provider security server.")
                            .doesNotContain("No catalog offer found for service")
                            .doesNotContain(serviceId.asEncodedId());
                    assertThat(xrd.getErrorCodeMetadata())
                            .containsExactly("originalCode=" + ErrorCode.DSP_DATASET_NOT_FOUND.code());
                });
    }

    @Test
    void remoteOffersNotFoundSanitizesToGenericAccessDeniedMessage() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_OFFERS_NOT_FOUND.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
                    assertThat(xrd.getDetails())
                            .isEqualTo("The request is not allowed by the provider security server.")
                            .doesNotContain("No catalog offer found for service");
                    assertThat(xrd.getErrorCodeMetadata())
                            .containsExactly("originalCode=" + ErrorCode.DSP_OFFERS_NOT_FOUND.code());
                });
    }

    @Test
    void dspExceptionMetadataRoundTripCarriesOriginalCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_NEGOTIATION_FAILED.code()))
                .details("negotiation timed out")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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
    void emptyCandidatesYieldsUnknownMemberWithOriginalDspCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of());
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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
    }

    @Test
    void acquisitionFailedFromAllCandidatesYieldsIoErrorWithOriginalDspCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_ACQUISITION_FAILED.code()))
                .details("All 2 candidates failed")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenThrow(dspException);
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var failure = XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR, "catalog fetch failed");
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenThrow(failure);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isSameAs(failure)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.getCode()).isEqualTo(ErrorCode.NETWORK_ERROR.code());
                    assertThat(xrd.getDetails()).isEqualTo("catalog fetch failed");
                });
    }

    @Test
    void unmappedAcquisitionFailureYieldsSanitizedIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var grpcFailure = new StatusRuntimeException(Status.UNAVAILABLE);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenThrow(grpcFailure);
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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
    void candidateWithUnderivableTargetIsSkippedAndTheRestAreSent() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, BLANK_HOST),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));
        processor.execute(new DspRequest(serviceId, SENDER, null, false));
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(
                any(), any(), eq(single(DID_B, URL_B)), any(), any(), any());
    }

    @Test
    void everyCandidateUnderivableFailsWithValidationErrorAndNoAcquisitionCall() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, BLANK_HOST),
                        new ProviderAddress(null, BLANK_HOST)));
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR.code()));
        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any(), any(), any(), any());
    }

    @Test
    void dspCatalogFetchFailedPerCandidateMapsToIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_CATALOG_FETCH_FAILED.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_CATALOG_FETCH_FAILED.code()))
                .details("EDC catalog request to " + URL_A + " failed after 3 candidate hosts")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var futureDspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace.dsp_unknown_future_code"))
                .details("host=" + HOST_A + " candidate acquisition failed via edc negotiation API")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), eq(single(DID_A, URL_A)), any(), any(), any()))
                .thenThrow(futureDspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, SENDER, null, false)))
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


    @Test
    void ordinaryRequestPassesTheSenderAsConsumerClient() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(serviceId, SENDER, null, false));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(DID_A, URL_A)), any(), eq(SENDER), any());
    }

    @Test
    void managementRequestPassesTheSenderAsConsumerClient() {
        var managementServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "ManagementSub", "clientReg");
        when(providerSecurityServerResolver.resolve(managementServiceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));

        processor.execute(new DspRequest(managementServiceId, SENDER, null, true));

        verify(assetAccessAcquisitionService)
                .acquireAssetAccess(eq(SENDER_MEMBER_CTX_ID), any(), eq(single(SYSTEM_DID_A, SYSTEM_URL_A)), any(), eq(SENDER), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dsp_access_refused", "dsp_dataset_not_found", "dsp_offers_not_found"})
    void aRememberedRefusalReachesTheClientLikeTheFirstRefusal(String refusalCode) {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var refusal = XrdRuntimeException.systemException(ErrorCode.withCode("proxy.dataspace." + refusalCode)).build();
        var memory = new RefusalMemory(() -> 0L);
        var key = new AssetAccessCache.CacheKey(SENDER_MEMBER_CTX_ID, serviceId.asEncodedId(),
                Set.copyOf(single(DID_A, URL_A)), null, "http-dsp-profile-2025-1", SENDER.asEncodedId());
        memory.rememberIfRefusal(key, refusal);
        var request = new DspRequest(serviceId, SENDER, null, false);

        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenThrow(refusal);
        var first = (XrdRuntimeException) catchThrowable(() -> processor.execute(request));
        doThrow(memory.recall(key).orElseThrow())
                .when(assetAccessAcquisitionService).acquireAssetAccess(any(), any(), any(), any(), any(), any());
        var repeat = (XrdRuntimeException) catchThrowable(() -> processor.execute(request));

        assertThat(repeat.getCode()).isEqualTo(first.getCode()).isEqualTo(ErrorCode.ACCESS_DENIED.code());
        assertThat(repeat.getDetails()).isEqualTo(first.getDetails());
        assertThat(repeat.getErrorCodeMetadata())
                .isEqualTo(first.getErrorCodeMetadata())
                .containsExactly("originalCode=" + refusalCode);
    }

    private void stubProviderAddressesAndAcquisition(String... hosts) {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(Stream.of(hosts).map(host -> new ProviderAddress(null, host)).toList());
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any(), any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null, WINNER));
    }

    private static List<AcquisitionCandidate> single(String counterPartyId, String counterPartyAddress) {
        return List.of(new AcquisitionCandidate(counterPartyId, counterPartyAddress));
    }
}
