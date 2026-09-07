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
package org.niis.xroad.edc.trust;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.net.ssl.X509ExtendedTrustManager;

import java.security.cert.X509Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelegatingTrustManagerTest {

    @Mock
    private X509ExtendedTrustManager first;

    @Mock
    private X509ExtendedTrustManager second;

    @Test
    void checksAreForwardedToTheCurrentDelegate() throws Exception {
        var delegating = new DelegatingTrustManager(first);

        delegating.checkServerTrusted(null, "RSA");
        verify(first).checkServerTrusted(null, "RSA");
        verify(second, never()).checkServerTrusted(null, "RSA");
    }

    @Test
    void setDelegateSwapsWhichManagerFutureChecksReach() throws Exception {
        var delegating = new DelegatingTrustManager(first);

        delegating.setDelegate(second);
        delegating.checkServerTrusted(null, "RSA");

        verify(second).checkServerTrusted(null, "RSA");
        verify(first, never()).checkServerTrusted(null, "RSA");
    }

    @Test
    void getAcceptedIssuersReflectsTheCurrentDelegate() {
        var issuers = new X509Certificate[0];
        when(second.getAcceptedIssuers()).thenReturn(issuers);
        var delegating = new DelegatingTrustManager(first);

        delegating.setDelegate(second);

        assertThat(delegating.getAcceptedIssuers()).isSameAs(issuers);
    }
}
