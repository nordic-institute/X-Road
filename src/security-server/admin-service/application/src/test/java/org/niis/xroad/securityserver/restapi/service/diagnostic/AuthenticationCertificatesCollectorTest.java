/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service.diagnostic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.service.TokenService;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationCertificatesCollectorTest {
    private AuthenticationCertificatesCollector collector;
    @Mock
    private TokenService tokenService;

    @Mock
    private TokenInfo token1;
    @Mock
    private KeyInfo key11;
    @Mock
    private CertificateInfo cert111;

    @Mock
    private TokenInfo token2;
    @Mock
    private KeyInfo key21;
    @Mock
    private CertificateInfo cert211;
    @Mock
    private KeyInfo key22;

    @BeforeEach
    void setUp() {
        collector = new AuthenticationCertificatesCollector(tokenService);
    }

    @Test
    void testCollect() {
        when(tokenService.getAllTokens()).thenReturn(List.of(token1, token2));

        when(token1.getKeyInfo()).thenReturn(List.of(key11));
        when(key11.isForSigning()).thenReturn(false);
        when(key11.isForSigning()).thenReturn(false);
        when(key11.getCerts()).thenReturn(List.of(cert111));
        when(cert111.getCertificateDisplayName()).thenReturn("cert111");
        when(cert111.getStatus()).thenReturn("registered");
        when(cert111.isActive()).thenReturn(true);

        when(token2.getKeyInfo()).thenReturn(List.of(key21, key22));
        when(key21.isForSigning()).thenReturn(false);
        when(key21.getCerts()).thenReturn(List.of(cert211));
        when(key21.getCerts()).thenReturn(List.of(cert211));
        when(cert211.getCertificateDisplayName()).thenReturn("cert211");
        when(cert211.getStatus()).thenReturn("expired");
        when(cert211.isActive()).thenReturn(false);
        when(key22.isForSigning()).thenReturn(true);

        var result = collector.collect();
        assertThat(result).size().isEqualTo(2);

        assertThat(result.getFirst().active()).isTrue();
        assertThat(result.getFirst().name()).isEqualTo("cert111");
        assertThat(result.getFirst().status()).isEqualTo("registered");

        assertThat(result.get(1).active()).isFalse();
        assertThat(result.get(1).name()).isEqualTo("cert211");
        assertThat(result.get(1).status()).isEqualTo("expired");
    }
}
