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

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.keyconf.KeyConfProvider;
import org.niis.xroad.keyconf.dto.AuthKey;
import org.niis.xroad.proxy.core.conf.SigningCtx;
import org.niis.xroad.proxy.core.conf.SigningCtxProvider;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageSigningServiceTest {

    private KeyConfProvider keyConfProvider;
    private SigningCtxProvider signingCtxProvider;
    private MessageSigningService messageSigningService;

    @BeforeEach
    void setUp() {
        keyConfProvider = mock(KeyConfProvider.class);
        signingCtxProvider = mock(SigningCtxProvider.class);
        messageSigningService = new MessageSigningService(keyConfProvider, signingCtxProvider);
    }

    @Test
    void getAuthKeyDelegatesToKeyConfProvider() {
        var authKey = mock(AuthKey.class);
        when(keyConfProvider.getAuthKey()).thenReturn(authKey);

        var result = messageSigningService.getAuthKey();

        assertThat(result).isSameAs(authKey);
        verify(keyConfProvider).getAuthKey();
    }

    @Test
    void getAllOcspResponsesDelegatesToKeyConfProvider() throws CertificateEncodingException, IOException {
        var cert = mock(X509Certificate.class);
        var certs = List.of(cert);
        var ocspResp = mock(OCSPResp.class);
        when(keyConfProvider.getAllOcspResponses(certs)).thenReturn(List.of(ocspResp));

        var result = messageSigningService.getAllOcspResponses(certs);

        assertThat(result).containsExactly(ocspResp);
        verify(keyConfProvider).getAllOcspResponses(certs);
    }

    @Test
    void createSigningCtxDelegatesToSigningCtxProvider() {
        var clientId = mock(ClientId.class);
        var signingCtx = mock(SigningCtx.class);
        when(signingCtxProvider.createSigningCtx(clientId)).thenReturn(signingCtx);

        var result = messageSigningService.createSigningCtx(clientId);

        assertThat(result).isSameAs(signingCtx);
        verify(signingCtxProvider).createSigningCtx(clientId);
    }
}
