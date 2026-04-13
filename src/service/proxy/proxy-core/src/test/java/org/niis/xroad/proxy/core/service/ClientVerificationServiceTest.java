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
package org.niis.xroad.proxy.core.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.RequestWrapper;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.proxy.core.clientproxy.IsAuthenticationData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.util.ClientAuthenticationService;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.Client;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CLIENT_IDENTIFIER;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.UNKNOWN_MEMBER;

class ClientVerificationServiceTest {

    private ServerConfProvider serverConfProvider;
    private ClientAuthenticationService clientAuthenticationService;
    private GlobalConfProvider globalConfProvider;
    private ProxyProperties proxyProperties;
    private CertHelper certHelper;
    private ClientVerificationService service;

    @BeforeEach
    void setUp() {
        serverConfProvider = mock(ServerConfProvider.class);
        clientAuthenticationService = mock(ClientAuthenticationService.class);
        globalConfProvider = mock(GlobalConfProvider.class);
        proxyProperties = mock(ProxyProperties.class);
        certHelper = mock(CertHelper.class);
        service = new ClientVerificationService(serverConfProvider, clientAuthenticationService,
                globalConfProvider, proxyProperties, certHelper);
    }

    // --- verifyClientStatus ---

    @Test
    void verifyClientStatusDoesNotThrowForRegisteredClient() {
        var clientId = mock(ClientId.class);
        when(serverConfProvider.getMemberStatus(clientId)).thenReturn(Client.STATUS_REGISTERED);

        assertThatCode(() -> service.verifyClientStatus(clientId)).doesNotThrowAnyException();
    }

    @Test
    void verifyClientStatusThrowsForNullClient() {
        assertThatThrownBy(() -> service.verifyClientStatus(null))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getCode()).isEqualTo(INVALID_CLIENT_IDENTIFIER.code()));
    }

    @Test
    void verifyClientStatusThrowsForUnregisteredClient() {
        var clientId = mock(ClientId.class);
        when(serverConfProvider.getMemberStatus(clientId)).thenReturn(null);

        assertThatThrownBy(() -> service.verifyClientStatus(clientId))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getCode()).isEqualTo(UNKNOWN_MEMBER.code()));
    }

    // --- verifyClientAuthentication ---

    @Test
    void verifyClientAuthenticationDelegatesToServiceWhenVerifyClientCertIsTrue() {
        var sender = mock(ClientId.class);
        var request = mock(RequestWrapper.class);
        var authData = mock(IsAuthenticationData.class);

        when(proxyProperties.verifyClientCert()).thenReturn(true);
        when(proxyProperties.logClientCert()).thenReturn(false);
        when(clientAuthenticationService.getIsAuthenticationData(request, false)).thenReturn(authData);

        service.verifyClientAuthentication(sender, request);

        verify(clientAuthenticationService).getIsAuthenticationData(request, false);
        verify(clientAuthenticationService).verifyClientAuthentication(sender, authData);
    }

    @Test
    void verifyClientAuthenticationSkipsWhenVerifyClientCertIsFalse() {
        var sender = mock(ClientId.class);
        var request = mock(RequestWrapper.class);

        when(proxyProperties.verifyClientCert()).thenReturn(false);

        service.verifyClientAuthentication(sender, request);

        verify(clientAuthenticationService, never()).getIsAuthenticationData(any(), anyBoolean());
        verify(clientAuthenticationService, never()).verifyClientAuthentication(any(), any());
    }

    // --- verifySslClientCert ---

    @Test
    void verifySslClientCertThrowsForEmptyOcspResponses() {
        var clientSslCerts = new X509Certificate[]{mock(X509Certificate.class)};
        var clientId = mock(ClientId.class);

        assertThatThrownBy(() -> service.verifySslClientCert("DEV", clientSslCerts, Collections.emptyList(), clientId))
                .isInstanceOf(XrdRuntimeException.class)
                .satisfies(e -> assertThat(((XrdRuntimeException) e).getCode()).isEqualTo(SSL_AUTH_FAILED.code()));
    }

    @Test
    void verifySslClientCertVerifiesChainWithTrustAnchor() throws Exception {
        var cert = mock(X509Certificate.class);
        var trustAnchor = mock(X509Certificate.class);
        var clientSslCerts = new X509Certificate[]{cert};
        var ocspResp = mock(OCSPResp.class);
        var ocspResponses = List.of(ocspResp);
        var clientId = mock(ClientId.class);
        var certChain = mock(CertChain.class);

        when(globalConfProvider.getCaCert("DEV", cert)).thenReturn(trustAnchor);

        try (MockedStatic<CertChainFactory> factory = mockStatic(CertChainFactory.class)) {
            factory.when(() -> CertChainFactory.create(any(String.class), any(X509Certificate[].class)))
                    .thenReturn(certChain);

            assertThatCode(() -> service.verifySslClientCert("DEV", clientSslCerts, ocspResponses, clientId))
                    .doesNotThrowAnyException();

            verify(certHelper).verifyAuthCert(certChain, ocspResponses, clientId);
        }
    }
}
