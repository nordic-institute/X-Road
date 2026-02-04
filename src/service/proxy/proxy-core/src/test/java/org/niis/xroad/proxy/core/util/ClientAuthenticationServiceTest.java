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

package org.niis.xroad.proxy.core.util;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.RequestWrapper;

import org.eclipse.jetty.http.HttpURI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.proxy.core.clientproxy.IsAuthenticationData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.IsAuthentication;
import org.niis.xroad.serverconf.ServerConfProvider;

import javax.net.ssl.X509TrustManager;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;

@ExtendWith(MockitoExtension.class)
class ClientAuthenticationServiceTest {

    @Mock
    private ServerConfProvider serverConfProvider;
    @Mock
    private VaultKeyProvider vaultKeyProvider;
    @Mock
    private ProxyProperties proxyProperties;

    @InjectMocks
    private ClientAuthenticationService clientAuthenticationService;

    @Test
    void testGetIsAuthenticationDataWithCertificateAndHttps() {
        RequestWrapper request = mock(RequestWrapper.class);
        X509Certificate cert = mock(X509Certificate.class);
        when(request.getHttpURI()).thenReturn(HttpURI.build("https://url"));
        when(request.getPeerCertificates()).thenReturn(Optional.of(new X509Certificate[]{cert}));

        IsAuthenticationData data = clientAuthenticationService.getIsAuthenticationData(request, true);

        assertFalse(data.isPlaintextConnection());
        assertEquals(cert, data.cert());
    }

    @Test
    void testGetIsAuthenticationDataWithoutCertificateAndPlaintext() {
        RequestWrapper request = mock(RequestWrapper.class);
        when(request.getHttpURI()).thenReturn(HttpURI.build("http://url"));
        when(request.getPeerCertificates()).thenReturn(Optional.empty());

        IsAuthenticationData data = clientAuthenticationService.getIsAuthenticationData(request, false);

        assertTrue(data.isPlaintextConnection());
        assertNull(data.cert());
    }

    @Test
    void testVerifyClientAuthenticationClientNotFoundThrowsException() {
        ClientId clientId = mock(ClientId.class);
        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(null);

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> clientAuthenticationService.verifyClientAuthentication(clientId,
                        new IsAuthenticationData(null, false)));

        assertEquals(INTERNAL_ERROR.code(), ex.getErrorCode());
    }

    @Test
    void testVerifyClientAuthenticationSslNoAuthButPlaintextThrowsException() {
        ClientId clientId = mock(ClientId.class);
        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLNOAUTH);

        IsAuthenticationData auth = new IsAuthenticationData(null, true);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> clientAuthenticationService.verifyClientAuthentication(clientId, auth));

        assertEquals(SSL_AUTH_FAILED.code(), ex.getErrorCode());
    }

    @Test
    void testVerifyClientAuthenticationSslAuthWithoutCertThrowsException() {
        ClientId clientId = mock(ClientId.class);
        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLAUTH);

        IsAuthenticationData auth = new IsAuthenticationData(null, false);

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> clientAuthenticationService.verifyClientAuthentication(clientId, auth));

        assertEquals(SSL_AUTH_FAILED.code(), ex.getErrorCode());
    }

    @Test
    void testVerifyClientAuthenticationSslAuthWithVaultCertAccepted() {
        ClientId clientId = mock(ClientId.class);
        X509Certificate clientCert = mock(X509Certificate.class);
        X509Certificate vaultIssuer = mock(X509Certificate.class);
        X509TrustManager trustManager = mock(X509TrustManager.class);

        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLAUTH);
        when(vaultKeyProvider.getTrustManager()).thenReturn(trustManager);
        when(trustManager.getAcceptedIssuers()).thenReturn(new X509Certificate[]{vaultIssuer});

        IsAuthenticationData auth = new IsAuthenticationData(clientCert, false);
        clientAuthenticationService.verifyClientAuthentication(clientId, auth);
    }

    @Test
    void testVerifyClientAuthenticationSslAuthWithMatchingCertsValid() {
        ClientId clientId = mock(ClientId.class);
        X509Certificate cert = mock(X509Certificate.class);

        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLAUTH);
        when(serverConfProvider.getIsCerts(clientId)).thenReturn(List.of(cert));

        IsAuthenticationData auth = new IsAuthenticationData(cert, false);
        assertDoesNotThrow(() -> clientAuthenticationService.verifyClientAuthentication(clientId, auth));
    }

    @Test
    void testVerifyClientAuthenticationSslAuthWithNonMatchingCertThrowsException() {
        ClientId clientId = mock(ClientId.class);
        X509Certificate cert = mock(X509Certificate.class);
        X509Certificate otherCert = mock(X509Certificate.class);

        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLAUTH);
        when(serverConfProvider.getIsCerts(clientId)).thenReturn(List.of(otherCert));

        IsAuthenticationData auth = new IsAuthenticationData(cert, false);

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> clientAuthenticationService.verifyClientAuthentication(clientId, auth));

        assertEquals(SSL_AUTH_FAILED.code(), ex.getErrorCode());
    }

    @Test
    void testClientIsCertPeriodValidationExpiredCertThrowsWhenEnforced() throws Exception {
        ClientId clientId = mock(ClientId.class);
        X509Certificate cert = mock(X509Certificate.class);
        doThrow(new CertificateExpiredException()).when(cert).checkValidity();
        when(proxyProperties.enforceClientIsCertValidityPeriodCheck()).thenReturn(true);
        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLAUTH);
        when(serverConfProvider.getIsCerts(clientId)).thenReturn(List.of(cert));

        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> clientAuthenticationService.verifyClientAuthentication(clientId,
                        new IsAuthenticationData(cert, false)));

        assertEquals(SSL_AUTH_FAILED.code(), ex.getErrorCode());
    }

    @Test
    void testClientIsCertPeriodValidationNotYetValidCertLogsWarning() throws Exception {
        ClientId clientId = mock(ClientId.class);
        X509Certificate cert = mock(X509Certificate.class);
        doThrow(new CertificateNotYetValidException()).when(cert).checkValidity();
        when(proxyProperties.enforceClientIsCertValidityPeriodCheck()).thenReturn(false);
        when(serverConfProvider.getIsAuthentication(clientId)).thenReturn(IsAuthentication.SSLAUTH);
        when(serverConfProvider.getIsCerts(clientId)).thenReturn(List.of(cert));

        assertDoesNotThrow(() ->
                clientAuthenticationService.verifyClientAuthentication(clientId,
                        new IsAuthenticationData(cert, false)));
    }

}
