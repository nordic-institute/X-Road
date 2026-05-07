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
import org.niis.xroad.proxy.core.clientproxy.dsp.AssetAccessResponse;
import org.niis.xroad.proxy.core.clientproxy.dsp.ControlPlaneNegotiationService;
import org.niis.xroad.proxy.core.clientproxy.dsp.DspRequest;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver;
import org.niis.xroad.proxy.core.service.ProviderSecurityServerResolver.ProviderAddress;

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
class DspSubProcessorTest {

    private static final String INSTANCE = "DEV";
    private static final String HOST_A = "ss-a.example.com";
    private static final String HOST_B = "ss-b.example.com";

    @Mock
    private ControlPlaneNegotiationService controlPlaneNegotiationService;
    @Mock
    private ProviderSecurityServerResolver providerSecurityServerResolver;
    @Mock
    private AssetAccessClientProperties clientProperties;

    private DspSubProcessor processor;
    private ServiceId serviceId;

    @BeforeEach
    void setUp() {
        processor = new DspSubProcessor(controlPlaneNegotiationService,
                providerSecurityServerResolver, clientProperties);
        serviceId = ServiceId.Conf.create(INSTANCE, "COM", "1234", "TestClient", "testService", "v1");
        lenient().when(clientProperties.counterPartyUrlScheme()).thenReturn("http");
        lenient().when(clientProperties.counterPartyPort()).thenReturn(8183);
        lenient().when(clientProperties.counterPartyBasePath()).thenReturn("/api/dsp");
    }

    @Test
    void executeReturnsAssetAccessResponseWithEndpointAndAuthorization() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        var expected = new AssetAccessResponse("http://dp.example.com/endpoint", "token-abc");
        when(controlPlaneNegotiationService.acquireAssetAccess(any(), any(), any())).thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, null));

        assertThat(result).isSameAs(expected);
        assertThat(result.endpoint()).isEqualTo("http://dp.example.com/endpoint");
        assertThat(result.authorization()).isEqualTo("token-abc");
    }

    @Test
    void assetIdDerivedFromServiceIdEncoding() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(controlPlaneNegotiationService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null));

        var assetIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(controlPlaneNegotiationService).acquireAssetAccess(assetIdCaptor.capture(), any(), any());
        assertThat(assetIdCaptor.getValue()).isEqualTo(serviceId.asEncodedId());
        assertThat(assetIdCaptor.getValue()).doesNotContain("test-asset");
    }

    @Test
    void counterPartyIdDerivedFromServiceClientId() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(new ProviderAddress(null, HOST_A)));
        when(controlPlaneNegotiationService.acquireAssetAccess(any(), any(), any()))
                .thenReturn(new AssetAccessResponse("http://dp/e", null));

        processor.execute(new DspRequest(serviceId, null));

        var counterPartyIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(controlPlaneNegotiationService).acquireAssetAccess(any(), counterPartyIdCaptor.capture(), any());
        assertThat(counterPartyIdCaptor.getValue()).isEqualTo(serviceId.getClientId().asEncodedId());
        assertThat(counterPartyIdCaptor.getValue()).doesNotContain("counter-party-id");
    }

    @Test
    void securityServerHintRestrictsAcquireToThatServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-a");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenReturn(List.of(new ProviderAddress(hint, HOST_A)));
        var expected = new AssetAccessResponse("http://dp/e", null);
        when(controlPlaneNegotiationService.acquireAssetAccess(
                eq(serviceId.asEncodedId()),
                eq(serviceId.getClientId().asEncodedId()),
                eq("http://" + HOST_A + ":8183/api/dsp"))).thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, hint));

        assertThat(result).isSameAs(expected);
        verify(controlPlaneNegotiationService).acquireAssetAccess(
                eq(serviceId.asEncodedId()),
                eq(serviceId.getClientId().asEncodedId()),
                eq("http://" + HOST_A + ":8183/api/dsp"));
    }

    @Test
    void hintNotAmongProviderServersThrowsInvalidSecurityServer() {
        var hint = SecurityServerId.Conf.create(INSTANCE, "COM", "1234", "ss-x");
        when(providerSecurityServerResolver.resolve(serviceId, hint))
                .thenThrow(XrdRuntimeException.systemException(
                        ErrorCode.INVALID_SECURITY_SERVER, "Invalid security server \"%s\"".formatted(hint)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, hint)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.INVALID_SECURITY_SERVER.code()));

        verify(controlPlaneNegotiationService, never()).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void noProviderAddressesThrowsUnknownMember() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenThrow(XrdRuntimeException.systemException(
                        ErrorCode.UNKNOWN_MEMBER, "Could not find addresses for service provider \"%s\"".formatted(serviceId)));

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.UNKNOWN_MEMBER.code()));

        verify(controlPlaneNegotiationService, never()).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void firstSecurityServerFailsSecondSucceeds() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var expected = new AssetAccessResponse("http://dp.b/e", "token-b");
        when(controlPlaneNegotiationService.acquireAssetAccess(any(), any(), any()))
                .thenThrow(new RuntimeException("SS A unreachable"))
                .thenReturn(expected);

        var result = processor.execute(new DspRequest(serviceId, null));

        assertThat(result).isSameAs(expected);
        verify(controlPlaneNegotiationService, times(2)).acquireAssetAccess(any(), any(), any());
    }

    @Test
    void allSecurityServersFailThrowsNetworkError() {
        when(providerSecurityServerResolver.resolve(serviceId, null))
                .thenReturn(List.of(
                        new ProviderAddress(null, HOST_A),
                        new ProviderAddress(null, HOST_B)));
        var firstFailure = new RuntimeException("SS A unreachable");
        var lastFailure = new RuntimeException("SS B unreachable");
        when(controlPlaneNegotiationService.acquireAssetAccess(any(), any(), any()))
                .thenThrow(firstFailure)
                .thenThrow(lastFailure);

        assertThatThrownBy(() -> processor.execute(new DspRequest(serviceId, null)))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(ex -> assertThat(((XrdRuntimeException) ex).getCode())
                        .isEqualTo(ErrorCode.NETWORK_ERROR.code()))
                .hasMessageContaining("candidate security servers failed")
                .hasRootCause(lastFailure);
    }
}
