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

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.dsp.AssetAccessAcquisitionService;
import org.niis.xroad.proxy.core.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.dsp.DspRequest;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerSideDspProcessorTest {

    private static final String INSTANCE = "DEV";
    private static final String HOST_A = "xrd-ss0.lxd";
    private static final String HOST_B = "xrd-ss1.lxd";
    private static final String DID_A = "did:web:xrd-ss0.lxd%3A7183";
    private static final String DID_B = "did:web:xrd-ss1.lxd%3A7183";
    private static final String URL_A = "https://xrd-ss0.lxd:8183/api/dsp/xrd-ss0.lxd/2025-1";
    private static final String URL_B = "https://xrd-ss1.lxd:8183/api/dsp/xrd-ss1.lxd/2025-1";
    private static final String MGMT_DID_A = "did:web:xrd-ss0.lxd%3A7183:mgmt";
    private static final String MGMT_URL_A = "https://xrd-ss0.lxd:8183/api/dsp/xrd-ss0.lxd-mgmt/2025-1";
    private static final String UNKNOWN_HOST = "unknown.example.com";

    @Mock
    private AssetAccessAcquisitionService assetAccessAcquisitionService;
    @Mock
    private ProviderSecurityServerResolver providerSecurityServerResolver;
    @Mock
    private ServerConfProvider serverConfProvider;
    @Mock
    private GlobalConfProvider globalConfProvider;

    private ConsumerSideDspProcessor processor;
    private ServiceId serviceId;

    @BeforeEach
    void setUp() {
        processor = new ConsumerSideDspProcessor(assetAccessAcquisitionService, providerSecurityServerResolver,
                serverConfProvider, globalConfProvider);
        serviceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "testService", "v1");
    }

    @Test
    void executeReturnsAssetAccessResponseWithEndpointAndAuthorization() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var expected = new AssetAccessResponse("http://dp.example.com/endpoint", "token-abc");
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any())).thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, null, false));

        assertThat(result).isSameAs(expected);
        assertThat(result.endpoint()).isEqualTo("http://dp.example.com/endpoint");
        assertThat(result.authorization()).isEqualTo("token-abc");
    }

    @Test
    void metadataServiceFlowsThroughDspAndReturnsNonNull() {
        var listMethods = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "listMethods");
        when(providerSecurityServerResolver.resolve(listMethods, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var expected = new AssetAccessResponse("http://dp/e", null);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any())).thenReturn(expected);

        var result = processor.execute(new DspRequest(listMethods, null, false));

        assertThat(result).isSameAs(expected);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void assetIdDerivedFromServiceIdEncoding() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, false));

        var assetIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(assetIdCaptor.capture(), any(), any());
        assertThat(assetIdCaptor.getValue()).isEqualTo(serviceId.asEncodedId());
    }

    @Test
    void counterPartyIdAndAddressLookedUpFromTargetMap() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(URL_A);
    }

    @Test
    void securityServerHintRestrictsAcquireToThatServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-a");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenReturn(List.of(new ProviderAddress(hint, HOST_A)));
        var expected = new AssetAccessResponse("http://dp/e", null);
        when(assetAccessAcquisitionService.acquireAssetAccess(
                eq(serviceId.asEncodedId()), eq(DID_A), eq(URL_A))).thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, hint, false));

        assertThat(result).isSameAs(expected);
        verify(assetAccessAcquisitionService).acquireAssetAccess(
                eq(serviceId.asEncodedId()), eq(DID_A), eq(URL_A));
    }

    @Test
    void hintNotAmongProviderServersThrowsInvalidSecurityServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-x");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenThrow(XrdRuntimeException.systemException(
                        ErrorCode.INVALID_SECURITY_SERVER, "Invalid security server \"%s\"".formatted(hint)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, hint, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.INVALID_SECURITY_SERVER.code()));

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void noProviderAddressesThrowsUnknownMember() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenThrow(XrdRuntimeException.systemException(
                        ErrorCode.UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"".formatted(serviceId)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.UNKNOWN_MEMBER.code()));

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void oneCandidateFailsOtherSucceeds() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var expected = new AssetAccessResponse("http://dp.b/e", "token-b");
        // ConsumerSideDspProcessor#execute shuffles candidates, so A may be tried before or after B.
        // A always fails, B always succeeds — result is `expected` regardless of order.
        // A's stub is lenient so when shuffle picks B first (and A is never tried) the
        // strict-stubbing check doesn't fire.
        lenient().when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(new RuntimeException("SS A unreachable"));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, null, false));

        assertThat(result).isSameAs(expected);
    }

    @Test
    void allSecurityServersFailThrowsIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var failureA = new RuntimeException("SS A unreachable");
        var failureB = new RuntimeException("SS B unreachable");
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(failureA);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenThrow(failureB);

        // Candidate order is shuffled, so the chained root cause is whichever was iterated
        // last — either A or B.
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.IO_ERROR.code()))
                .hasMessageContaining("candidate security servers failed")
                .satisfies(ex -> assertThat(ex.getCause()).isIn(failureA, failureB));
    }

    @Test
    void unmappedHostAddressIsSkippedAndOtherCandidateUsed() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, UNKNOWN_HOST),
                        new ProviderAddress(null, HOST_A)));
        var expected = new AssetAccessResponse("http://dp.a/e", null);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, null, false));

        assertThat(result).isSameAs(expected);
        verify(assetAccessAcquisitionService, times(1)).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void allCandidatesUnmappedThrowsIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, UNKNOWN_HOST)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.IO_ERROR.code()))
                .hasMessageContaining("candidate security servers failed");

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void managementRequestTargetsMgmtCtxDidAndUrl() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, true));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(MGMT_DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(MGMT_URL_A);
    }

    @Test
    void allCandidatesHomogeneousUnknownMemberPreservesCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var failure = XrdRuntimeException.systemException(ErrorCode.UNKNOWN_MEMBER, "catalog miss for asset");
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(failure);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenThrow(failure);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.UNKNOWN_MEMBER.code()));
    }

    @Test
    void allCandidatesHomogeneousNetworkErrorPreservesCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var failure = XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR, "catalog fetch failed");
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(failure);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenThrow(failure);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.NETWORK_ERROR.code()));
    }

    @Test
    void mixedCodesAcrossCandidatesFallBackToIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.UNKNOWN_MEMBER, "catalog miss"));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.NETWORK_ERROR, "fetch failed"));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.IO_ERROR.code()));
    }

    @Test
    void emptyCandidateListThrowsUnknownMember() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of());

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.UNKNOWN_MEMBER.code()));

        verify(assetAccessAcquisitionService, never()).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void localRoutingFailureMixedWithRemoteFailureFallsBackToIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, UNKNOWN_HOST),
                        new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.UNKNOWN_MEMBER, "catalog miss"));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.IO_ERROR.code()));
    }

    @Test
    void nonManagementRequestTargetsHostCtxDidAndUrl() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(URL_A);
    }

    @Test
    void builtinServiceRequestTargetsMgmtCtxDidAndUrl() {
        var builtinServiceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", null, "getSecurityServerMetrics");
        when(providerSecurityServerResolver.resolve(builtinServiceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(builtinServiceId, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(MGMT_DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(MGMT_URL_A);
    }

    @Test
    void selfCallByServerIdRoutesViaMgmtCtx() {
        var localServerId = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss0-local");
        when(serverConfProvider.getIdentifier()).thenReturn(localServerId);
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(localServerId, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(MGMT_DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(MGMT_URL_A);
    }

    @Test
    void selfCallByHostAddressRoutesViaMgmtCtx() {
        var localServerId = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss0-local");
        when(serverConfProvider.getIdentifier()).thenReturn(localServerId);
        when(globalConfProvider.getSecurityServerAddress(localServerId)).thenReturn(HOST_A);
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(MGMT_DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(MGMT_URL_A);
    }

    @Test
    void remoteCandidateUsesHostCtxEvenWhenLocalServerKnown() {
        var localServerId = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss0-local");
        when(serverConfProvider.getIdentifier()).thenReturn(localServerId);
        when(globalConfProvider.getSecurityServerAddress(localServerId)).thenReturn(HOST_A);
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(DID_B);
        assertThat(addrCaptor.getValue()).isEqualTo(URL_B);
    }

    @Test
    void builtinServiceCodeOnSubsystemUsesHostCtx() {
        var notBuiltin = ServiceId.Conf.create(INSTANCE, "COM", "1234", "Sub", "getSecurityServerMetrics");
        when(providerSecurityServerResolver.resolve(notBuiltin, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(notBuiltin, null, false));

        var idCaptor = ArgumentCaptor.forClass(String.class);
        var addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(assetAccessAcquisitionService).acquireAssetAccess(any(), idCaptor.capture(), addrCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(DID_A);
        assertThat(addrCaptor.getValue()).isEqualTo(URL_A);
    }

    @Test
    void remoteDatasetNotFoundFromReachedProviderMapsToIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_DATASET_NOT_FOUND.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).isCausedBy(ErrorCode.IO_ERROR)).isTrue());
    }

    @Test
    void allCandidatesThrowSameDspCodeSurfacesAsMappedLegacyCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_CATALOG_FETCH_FAILED.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(dspException);
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).isCausedBy(ErrorCode.IO_ERROR)).isTrue());
    }

    @Test
    void dspExceptionMetadataRoundTripCarriesOriginalCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_NEGOTIATION_FAILED.code()))
                .details("negotiation timed out")
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(dspException);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
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
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.UNKNOWN_MEMBER)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_DATASET_NOT_FOUND.code());
                });
    }
    @Test
    void missingCounterPartyTargetYieldsIoErrorWithOriginalDspCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, UNKNOWN_HOST)));
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_ACQUISITION_FAILED.code());
                });
    }
    @Test
    void allCandidatesFailedAggregationYieldsIoErrorWithOriginalDspCode() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(new RuntimeException("SS A unreachable"));
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_B), eq(URL_B)))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.IO_ERROR, "network down"));
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_ACQUISITION_FAILED.code());
                });
    }
    @Test
    void dspCatalogFetchFailedPerCandidateMapsToIoError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var dspException = XrdRuntimeException.systemException(
                        ErrorCode.withCode("proxy.dataspace." + ErrorCode.DSP_CATALOG_FETCH_FAILED.code()))
                .build();
        when(assetAccessAcquisitionService.acquireAssetAccess(any(), eq(DID_A), eq(URL_A)))
                .thenThrow(dspException);
        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null, false)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> {
                    var xrd = (XrdRuntimeException) ex;
                    assertThat(xrd.isCausedBy(ErrorCode.IO_ERROR)).isTrue();
                    assertThat(xrd.getErrorCodeMetadata()).hasSize(1);
                    assertThat(xrd.getErrorCodeMetadata().getFirst())
                            .isEqualTo("originalCode=" + ErrorCode.DSP_CATALOG_FETCH_FAILED.code());
                });
    }

}
