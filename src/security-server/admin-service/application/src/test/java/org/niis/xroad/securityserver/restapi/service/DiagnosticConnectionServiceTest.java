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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.DiagnosticStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.dto.DownloadUrlConnectionStatus;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ExceptionCategory;
import org.niis.xroad.common.core.exception.XrdRuntimeExceptionBuilder;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.service.DiagnosticConnectionService.INVALID_SERVER_ADDRESS;

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

    DiagnosticConnectionService service;

    @BeforeEach
    void setUp() {
        service = new DiagnosticConnectionService(globalConfProvider, tokenService,  authCertVerifier, managementRequestSenderService);
    }

    @Test
    void checkAndGetConnectionStatusThenReturnHttp200() throws Exception {
        String downloadUrl = "http://unknown-host:80/internalconf";
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(200);

        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return mockConn;
            }
        };

        URL fakeUrl = URL.of(URI.create(downloadUrl), handler);

        var m = DiagnosticConnectionService.class.getDeclaredMethod("checkAndGetConnectionStatus", URL.class);
        m.setAccessible(true);
        var status = (DownloadUrlConnectionStatus) m.invoke(service, fakeUrl);

        assertThat(status.getDownloadUrl()).isEqualTo(downloadUrl);
        assertThat(status.getConnectionStatus().getStatus()).isEqualTo(DiagnosticStatus.OK);
        assertThat(status.getConnectionStatus().getErrorCode()).isNull();
        assertThat(status.getConnectionStatus().getErrorMetadata()).isEmpty();
    }

    @Test
    void getGlobalConfStatusInfoThenReturnUnknownHostErrors() {
        when(globalConfProvider.findSourceAddresses())
                .thenReturn(Set.of("unknown-host"));

        var statuses = service.getGlobalConfStatusInfo();

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
    void getGlobalConfStatusInfoThenReturnGlobalConfGetVersionError() throws Exception {
        String downloadUrl = "http://unknown-host:80/internalconf";
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(404);

        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return mockConn;
            }
        };

        URL fakeUrl = URL.of(URI.create(downloadUrl), handler);

        var m = DiagnosticConnectionService.class.getDeclaredMethod("checkAndGetConnectionStatus", URL.class);
        m.setAccessible(true);
        var status = (DownloadUrlConnectionStatus) m.invoke(service, fakeUrl);

        assertThat(status.getDownloadUrl()).isEqualTo(downloadUrl);
        assertThat(status.getConnectionStatus().getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getConnectionStatus().getErrorCode()).isEqualTo("global_conf_get_version_failed");
        assertThat(status.getConnectionStatus().getErrorMetadata()).isEqualTo(List.of("http://unknown-host:80/internalconf — HTTP 404 "));
    }

    @Test
    void getAuthCertRegStatusInfoThenReturnOkStatus() {
        TokenInfo token = mock(TokenInfo.class);
        KeyInfo key = mock(KeyInfo.class);
        CertificateInfo cert = mock(CertificateInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of(key));
        when(key.getUsage()).thenReturn(KeyUsageInfo.AUTHENTICATION);
        when(key.getCerts()).thenReturn(List.of(cert));
        when(cert.isActive()).thenReturn(true);
        when(cert.getCertificateBytes()).thenReturn("dummy".getBytes());
        doNothing().when(authCertVerifier).verify(any());

        var status = service.getAuthCertRegStatusInfo();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.OK);
        assertThat(status.getErrorCode()).isNull();
    }

    @Test
    void getAuthCertRegStatusInfoThenReturnNetworkError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, ErrorCode.withCode("network_error"))
                        .cause(new UnresolvedAddressException())
                        .build());

        var status = service.getAuthCertRegStatusInfo();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("network_error");
        assertThat(status.getValidationErrors()).containsEntry("certificate_not_found", List.of("No active auth cert found"));
    }

    @Test
    void getAuthCertRegStatusInfoThenReturnInternalError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new CodedException(X_INTERNAL_ERROR));

        var status = service.getAuthCertRegStatusInfo();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("internal_error");
        assertThat(status.getValidationErrors()).containsEntry("certificate_not_found", List.of("No active auth cert found"));
    }

    @Test
    void getAuthCertRegStatusInfoThenReturnCertificateNotFoundError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new CodedException(X_INVALID_REQUEST));

        var status = service.getAuthCertRegStatusInfo();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("certificate_not_found");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("No active auth cert found"));
        assertThat(status.getValidationErrors()).isEmpty();
    }

    @Test
    void getAuthCertRegStatusWithInvalidServerAddressInfoThenReturnCertificateNotFoundError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new CodedException(X_INVALID_REQUEST, INVALID_SERVER_ADDRESS));

        var status = service.getAuthCertRegStatusInfo();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("certificate_not_found");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("No active auth cert found"));
        assertThat(status.getValidationErrors()).isEmpty();
    }
}
