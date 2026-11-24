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
package org.niis.xroad.globalconf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceReadinessTest {

    private static final String INSTANCE = "DEV";

    @Mock
    private GlobalConfProvider globalConfProvider;

    @BeforeEach
    void setUp() {
        lenient().when(globalConfProvider.isValid()).thenReturn(true);
    }

    @Test
    void isReadyWhenOwnInstanceListsACredentialIssuer() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of("did:web:issuer.example"));

        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isTrue();
    }

    @Test
    void isNotReadyWhenOwnInstanceListsNoCredentialIssuer() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of());

        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isFalse();
        verify(globalConfProvider, never()).isValid();
    }

    @Test
    void isNotReadyWhenInstanceIdentifierIsUnavailable() {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new IllegalStateException("no anchor"));

        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isFalse();
    }

    @Test
    void isNotReadyWhenSharedParametersAreUnavailable() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).build());

        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isFalse();
    }

    @Test
    void isReadyFollowsGlobalConfWithoutRestart() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE))
                .thenReturn(List.of("did:web:issuer.example"))
                .thenReturn(List.of())
                .thenReturn(List.of("did:web:issuer.example"));

        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isTrue();
        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isFalse();
        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isTrue();
    }

    @Test
    void isNotReadyWhenGlobalConfIsExpired() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of("did:web:issuer.example"));
        when(globalConfProvider.isValid()).thenReturn(false);

        assertThat(DataspaceReadiness.isReady(globalConfProvider)).isFalse();
    }
}
