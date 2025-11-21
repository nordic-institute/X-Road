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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.apache.http.Header;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.dto.DownloadUrlConnectionStatus;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.ExceptionCategory;
import org.niis.xroad.common.core.exception.XrdRuntimeExceptionBuilder;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.util.AuthCertVerifier;
import org.niis.xroad.serverconf.ServerConfProvider;
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticConnectionServiceTest {

    private static final ClientId CLIENT_ID = ClientId.Conf.create("DEV", "COM", "4321");
    private static final ClientId TARGET_CLIENT_ID = ClientId.Conf.create("DEV", "COM", "1234", "MANAGEMENT");
    private static final SecurityServerId SECURITY_SERVER_ID = SecurityServerId.Conf.create("DEV", "COM", "1234", "SS0");

    @Mock
    GlobalConfProvider globalConfProvider;
    @Mock
    TokenService tokenService;
    @Mock
    AuthCertVerifier authCertVerifier;
    @Mock
    ManagementRequestSenderService managementRequestSenderService;
    @Mock
    ServerConfProvider serverConfProvider;

    DiagnosticConnectionService service;

    @BeforeEach
    void setUp() {
        service = new DiagnosticConnectionService(globalConfProvider, tokenService, authCertVerifier, managementRequestSenderService,
                serverConfProvider);
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
    void getGlobalConfStatusThenReturnUnknownHostErrors() {
        when(globalConfProvider.findSourceAddresses())
                .thenReturn(Set.of("unknown-host"));

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
    void getGlobalConfStatusThenReturnGlobalConfGetVersionError() throws Exception {
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
                .thenThrow(new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, ErrorCode.withCode("management_service_error"))
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
                .thenThrow(new CodedException("InvalidRequest"));

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
                .thenThrow(new CodedException(X_INVALID_REQUEST));

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("certificate_not_found");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("No auth cert found"));
        assertThat(status.getValidationErrors()).isEmpty();
    }

    @Test
    void getAuthCertReqStatusThenReturnUnexpectedCodedExceptionMessageWhenCertOk() {
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
                .thenThrow(new CodedException("SomeOtherCode", "random_message"));

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("random_message");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("random_message"));
        assertThat(status.getValidationErrors()).isEmpty();
    }


    @Test
    void getAuthCertReqStatusThenReturnNetworkError() {
        TokenInfo token = mock(TokenInfo.class);
        when(tokenService.getToken(PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID)).thenReturn(token);
        when(token.getKeyInfo()).thenReturn(List.of());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), any(Boolean.class)))
                .thenThrow(new XrdRuntimeExceptionBuilder(ExceptionCategory.SYSTEM, ErrorCode.withCode("network_error"))
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
                .thenThrow(new CodedException(X_INTERNAL_ERROR));

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
                .thenThrow(new CodedException(X_INVALID_REQUEST));

        var status = service.getAuthCertReqStatus();

        assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.ERROR);
        assertThat(status.getErrorCode()).isEqualTo("certificate_not_found");
        assertThat(status.getErrorMetadata()).isEqualTo(List.of("No auth cert found"));
        assertThat(status.getValidationErrors()).isEmpty();
    }

    @Test
    void getOtherSecurityServerStatusWithRestThenReturnHttp200() throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse httpResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);

        when(statusLine.getStatusCode()).thenReturn(200);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);

        try (MockedStatic<HttpClients> httpClientsMock = org.mockito.Mockito.mockStatic(HttpClients.class)) {
            HttpClientBuilder builder = mock(HttpClientBuilder.class);
            httpClientsMock.when(HttpClients::custom).thenReturn(builder);

            doReturn(builder).when(builder).setSSLSocketFactory(any());
            doReturn(builder).when(builder).setDefaultRequestConfig(any());
            doReturn(builder).when(builder).disableAutomaticRetries();
            doReturn(httpClient).when(builder).build();

            var status = service.getOtherSecurityServerStatus("REST", CLIENT_ID, TARGET_CLIENT_ID, SECURITY_SERVER_ID);

            assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.OK);
            assertThat(status.getErrorCode()).isNull();
            assertThat(status.getErrorMetadata()).isEmpty();
        }
    }

    @Test
    void getOtherSecurityServerStatusWithSoapThenReturnHttp200() throws Exception {
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        StatusLine statusLine = mock(StatusLine.class);

        when(statusLine.getStatusCode()).thenReturn(200);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusLine);
        when(closeableHttpResponse.getEntity()).thenReturn(new StringEntity(getMockSoapResponse(), ContentType.TEXT_XML));

        Header contentTypeHeader = new BasicHeader("Content-Type", "text/xml; charset=UTF-8");
        when(closeableHttpResponse.getAllHeaders()).thenReturn(new Header[] {contentTypeHeader});

        when(httpClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(closeableHttpResponse);

        try (MockedStatic<HttpClients> httpClientsMock = org.mockito.Mockito.mockStatic(HttpClients.class)) {
            HttpClientBuilder builder = mock(HttpClientBuilder.class);
            httpClientsMock.when(HttpClients::custom).thenReturn(builder);

            doReturn(builder).when(builder).setSSLSocketFactory(any());
            doReturn(builder).when(builder).setDefaultRequestConfig(any());
            doReturn(builder).when(builder).disableAutomaticRetries();
            doReturn(httpClient).when(builder).build();

            var status = service.getOtherSecurityServerStatus("SOAP", CLIENT_ID, TARGET_CLIENT_ID, SECURITY_SERVER_ID);

            assertThat(status.getStatus()).isEqualTo(DiagnosticStatus.OK);
            assertThat(status.getErrorCode()).isNull();
            assertThat(status.getErrorMetadata()).isEmpty();
        }
    }

    @Test
    void getOtherSecurityServerStatusWithWrongTypeThenReturnIllegalStateException() {
        assertThatThrownBy(() ->
                service.getOtherSecurityServerStatus("WRONG_TYPE", CLIENT_ID, TARGET_CLIENT_ID, SECURITY_SERVER_ID)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("should not get here");
    }

    private static String getMockSoapResponse() {
        return """
                <SOAP-ENV:Envelope
                    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xrd="http://x-road.eu/xsd/xroad.xsd"
                    xmlns:id="http://x-road.eu/xsd/identifiers">
                  <SOAP-ENV:Header>
                    <xrd:client id:objectType="SUBSYSTEM">
                      <id:xRoadInstance>DEV</id:xRoadInstance>
                      <id:memberClass>COM</id:memberClass>
                      <id:memberCode>4321</id:memberCode>
                      <id:subsystemCode>SUBSYSTEM</id:subsystemCode>
                    </xrd:client>
                    <xrd:service id:objectType="SERVICE">
                      <id:xRoadInstance>DEV</id:xRoadInstance>
                      <id:memberClass>COM</id:memberClass>
                      <id:memberCode>1234</id:memberCode>
                      <id:subsystemCode>MANAGEMENT</id:subsystemCode>
                      <id:serviceCode>listMethods</id:serviceCode>
                    </xrd:service>
                    <xrd:securityServer id:objectType="SERVER">
                      <id:xRoadInstance>DEV</id:xRoadInstance>
                      <id:memberClass>COM</id:memberClass>
                      <id:memberCode>1234</id:memberCode>
                      <id:serverCode>SS0</id:serverCode>
                    </xrd:securityServer>
                    <xrd:id>12345</xrd:id>
                    <xrd:protocolVersion>4.0</xrd:protocolVersion>
                  </SOAP-ENV:Header>
                  <SOAP-ENV:Body>
                    <xrd:listMethodsResponse/>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """;
    }
}
