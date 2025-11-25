/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.DiagnosticStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.dto.DownloadUrlConnectionStatus;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.exception.XrdRuntimeExceptionBuilder;
import org.niis.xroad.confclient.proto.CheckAndGetConnectionStatusRequest;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REQUEST;

@ExtendWith(MockitoExtension.class)
class DiagnosticConnectionServiceTest {

    @Mock
    GlobalConfProvider globalConfProvider;
    @Mock
    TokenService tokenService;
    @Mock
    AuthCertVerifier authCertVerifier;
    @Mock
    ManagementRequestSenderService managementRequestSenderService;
    @Mock
    ConfClientRpcClient confClientRpcClient;

    DiagnosticConnectionService service;

    @BeforeEach
    void setUp() {
        service = new DiagnosticConnectionService(globalConfProvider, tokenService, authCertVerifier, managementRequestSenderService,
                confClientRpcClient);
    }

    @Test
    void getGlobalConfStatusThenReturnHttp200() {
        when(globalConfProvider.findSourceAddresses()).thenReturn(Set.of("valid-host"));
        var requestHttp = CheckAndGetConnectionStatusRequest.newBuilder()
                .setProtocol("http")
                .setAddress("valid-host")
                .setPort(80)
                .build();
        var requestHttps = CheckAndGetConnectionStatusRequest.newBuilder()
                .setProtocol("https")
                .setAddress("valid-host")
                .setPort(443)
                .build();
        when(confClientRpcClient.checkAndGetConnectionStatus(requestHttp)).thenReturn(
                org.niis.xroad.rpc.common.DownloadUrlConnectionStatus.newBuilder()
                        .setDownloadUrl("http://valid-host:80/internalconf")
                        .build());
        when(confClientRpcClient.checkAndGetConnectionStatus(requestHttps)).thenReturn(
                org.niis.xroad.rpc.common.DownloadUrlConnectionStatus.newBuilder()
                        .setDownloadUrl("https://valid-host:443/internalconf")
                        .build());

        var statuses = service.getGlobalConfStatus();

        assertThat(statuses)
                .hasSize(2)
                .extracting(
                        DownloadUrlConnectionStatus::getDownloadUrl,
                        s -> s.getConnectionStatus().getStatus(),
                        s -> s.getConnectionStatus().getErrorCode()
                )
                .containsExactlyInAnyOrder(
                        tuple("http://valid-host:80/internalconf", DiagnosticStatus.OK, null),
                        tuple("https://valid-host:443/internalconf", DiagnosticStatus.OK, null)
                );
    }

    @Test
    void getGlobalConfStatusThenReturnUnknownHostErrors() {
        when(globalConfProvider.findSourceAddresses())
                .thenReturn(Set.of("unknown-host"));
        var requestHttp = CheckAndGetConnectionStatusRequest.newBuilder()
                .setProtocol("http")
                .setAddress("unknown-host")
                .setPort(80)
                .build();
        var requestHttps = CheckAndGetConnectionStatusRequest.newBuilder()
                .setProtocol("https")
                .setAddress("unknown-host")
                .setPort(443)
                .build();
        when(confClientRpcClient.checkAndGetConnectionStatus(requestHttp)).thenReturn(
                org.niis.xroad.rpc.common.DownloadUrlConnectionStatus.newBuilder()
                        .setDownloadUrl("http://unknown-host:80/internalconf")
                        .setErrorCode("unknown_host")
                        .build());
        when(confClientRpcClient.checkAndGetConnectionStatus(requestHttps)).thenReturn(
                org.niis.xroad.rpc.common.DownloadUrlConnectionStatus.newBuilder()
                        .setDownloadUrl("https://unknown-host:443/internalconf")
                        .setErrorCode("unknown_host")
                        .build());

        var statuses = service.getGlobalConfStatus();

        assertThat(statuses)
                .hasSize(2)
                .extracting(
                        DownloadUrlConnectionStatus::getDownloadUrl,
                        s -> s.getConnectionStatus().getStatus(),
                        s -> s.getConnectionStatus().getErrorCode()
                )
                .containsExactlyInAnyOrder(
                        tuple("http://unknown-host:80/internalconf", DiagnosticStatus.ERROR, "unknown_host"),
                        tuple("https://unknown-host:443/internalconf", DiagnosticStatus.ERROR, "unknown_host")
                );
    }

    @Test
    void getAuthCertReqStatusThenReturnSystemErrorWhenCertOk() {
        TokenInfo token = mock(TokenInfo.class);
        KeyInfo key = mock(KeyInfo.class);
        CertificateInfo cert = mock(CertificateInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of(key));
        when(key.getUsage()).thenReturn(KeyUsageInfo.AUTHENTICATION);
        when(key.getCerts()).thenReturn(List.of(cert));
        when(cert.getStatus()).thenReturn(CertificateInfo.STATUS_SAVED);
        doNothing().when(authCertVerifier).verify(any());

        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new XrdRuntimeExceptionBuilder(ErrorCode.withCode("management_service_error"))
                        .build());

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("management_service_error");
        assertThat(status.getValidationErrors()).isEmpty();
    }

    @Test
    void getAuthCertReqStatusThenReturnOkWhenInvalidRequestAndCertOk() {
        TokenInfo token = mock(TokenInfo.class);
        KeyInfo key = mock(KeyInfo.class);
        CertificateInfo cert = mock(CertificateInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of(key));
        when(key.getUsage()).thenReturn(KeyUsageInfo.AUTHENTICATION);
        when(key.getCerts()).thenReturn(List.of(cert));
        when(cert.getStatus()).thenReturn(CertificateInfo.STATUS_SAVED);
        doNothing().when(authCertVerifier).verify(any());

        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(XrdRuntimeException.systemException(INVALID_REQUEST, "InvalidRequest"));

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.OK);
        assertThat(status.getErrorCode()).isNull();
    }

    @Test
    void getAuthCertReqStatusThenReturnInvalidCertificateDeviation() {
        TokenInfo token = mock(TokenInfo.class);
        KeyInfo key = mock(KeyInfo.class);
        CertificateInfo cert = mock(CertificateInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of(key));
        when(key.getUsage()).thenReturn(KeyUsageInfo.AUTHENTICATION);
        when(key.getCerts()).thenReturn(List.of(cert));
        when(cert.getStatus()).thenReturn(CertificateInfo.STATUS_SAVED);
        InvalidCertificateException invalid = mock(InvalidCertificateException.class);
        when(invalid.getErrorDeviation()).thenReturn(new ErrorDeviation("certificate_not_found", List.of("No auth cert found")));
        org.mockito.Mockito.doThrow(invalid).when(authCertVerifier).verify(any());

        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(XrdRuntimeException.systemException(INVALID_REQUEST).build());

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("certificate_not_found");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("No auth cert found"));
        assertThat(status.getValidationErrors()).isEmpty();
    }

    @Test
    void getAuthCertReqStatusThenReturnUnexpectedXrdRuntimeExceptionionMessageWhenCertOk() {
        TokenInfo token = mock(TokenInfo.class);
        KeyInfo key = mock(KeyInfo.class);
        CertificateInfo cert = mock(CertificateInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of(key));
        when(key.getUsage()).thenReturn(KeyUsageInfo.AUTHENTICATION);
        when(key.getCerts()).thenReturn(List.of(cert));
        when(cert.getStatus()).thenReturn(CertificateInfo.STATUS_SAVED);
        doNothing().when(authCertVerifier).verify(any());

        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(XrdRuntimeException.systemInternalError("random_message"));

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("internal_error");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("random_message"));
        assertThat(status.getValidationErrors()).isEmpty();
    }

    @Test
    void getAuthCertReqStatusThenReturnNetworkError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new XrdRuntimeExceptionBuilder(ErrorCode.withCode("network_error"))
                        .cause(new UnresolvedAddressException())
                        .build());

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("network_error");
        assertThat(status.getValidationErrors()).containsEntry("certificate_not_found", List.of("No auth cert found"));
    }

    @Test
    void getAuthCertReqStatusThenReturnInternalError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(XrdRuntimeException.systemException(INTERNAL_ERROR).build());

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("internal_error");
        assertThat(status.getValidationErrors()).containsEntry("certificate_not_found", List.of("No auth cert found"));
    }

    @Test
    void getAuthCertReqStatusThenReturnCertificateNotFoundError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(XrdRuntimeException.systemException(INVALID_REQUEST).build());

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("certificate_not_found");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("No auth cert found"));
        assertThat(status.getValidationErrors()).isEmpty();
    }
}
