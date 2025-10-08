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
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void checkVersionLocationExistsThenReturnHttp200() throws Exception {
        String downloadUrl = "http://cs:80/internalconf";
        HttpURLConnection mockConn = mock(HttpURLConnection.class);
        when(mockConn.getResponseCode()).thenReturn(200);

        URLStreamHandler handler = new URLStreamHandler() {
            @Override
            protected URLConnection openConnection(URL u) {
                return mockConn;
            }
        };
        URL fakeUrl = new URL(null, downloadUrl, handler);

        // reflectively call the private method
        var m = DiagnosticConnectionService.class
                .getDeclaredMethod("checkVersionLocationExists", URL.class);
        m.setAccessible(true);

        var status = (DownloadUrlConnectionStatus) m.invoke(service, fakeUrl);

        assertThat(status.getDownloadUrl()).isEqualTo(downloadUrl);
        assertThat(status.getConnectionStatus().getStatus()).isEqualTo(DiagnosticStatus.OK);
        assertThat(status.getConnectionStatus().getErrorCode()).isNull();
        assertThat(status.getConnectionStatus().getErrorMetadata()).isNull();
    }

    @Test
    void getGlobalConfStatusInfoThenReturnErrorStatuses() {
        when(globalConfProvider.findSourcesAddress())
                .thenReturn(List.of("cs"));

        var statuses = service.getGlobalConfStatusInfo();

        assertThat(statuses).hasSize(2);
        assertThat(statuses.getFirst().getDownloadUrl()).isEqualTo("http://cs:80/internalconf");
        assertThat(statuses.getFirst().getConnectionStatus().getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(statuses.getFirst().getConnectionStatus().getErrorCode()).isEqualTo("network_error");
        assertThat(statuses.getFirst().getConnectionStatus().getErrorMetadata()).contains("Connection refused");
        assertThat(statuses.get(1).getDownloadUrl()).isEqualTo("https://cs:443/internalconf");
        assertThat(statuses.get(1).getConnectionStatus().getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(statuses.get(1).getConnectionStatus().getErrorCode()).isEqualTo("network_error");
        assertThat(statuses.get(1).getConnectionStatus().getErrorMetadata()).contains("Connection refused");
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
}
