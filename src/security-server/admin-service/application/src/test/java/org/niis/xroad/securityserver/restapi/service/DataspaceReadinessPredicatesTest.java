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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.niis.xroad.signer.client.SignerRpcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceReadinessPredicatesTest {

    private static final String INSTANCE = "DEV";

    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private ServerConfRepository serverConfRepository;
    @Mock
    private SignerRpcClient signerRpcClient;

    private DataspaceReadinessPredicates predicates;

    @BeforeEach
    void setUp() {
        predicates = new DataspaceReadinessPredicates(globalConfProvider, serverConfRepository, signerRpcClient);
    }

    @Test
    void isDataspaceReadyWhenOwnInstanceListsACredentialIssuer() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of("did:web:issuer.example"));

        assertThat(predicates.isDataspaceReady()).isTrue();
    }

    @Test
    void isDataspaceNotReadyWhenOwnInstanceListsNoCredentialIssuer() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE)).thenReturn(List.of());

        assertThat(predicates.isDataspaceReady()).isFalse();
    }

    @Test
    void isDataspaceNotReadyWhenInstanceIdentifierIsUnavailable() {
        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new IllegalStateException("no anchor"));

        assertThat(predicates.isDataspaceReady()).isFalse();
    }

    @Test
    void isDataspaceNotReadyWhenSharedParametersAreUnavailable() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE))
                .thenThrow(XrdRuntimeException.systemException(ErrorCode.INTERNAL_ERROR).build());

        assertThat(predicates.isDataspaceReady()).isFalse();
    }

    @Test
    void isDataspaceReadyFollowsGlobalConfWithoutRestart() {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE);
        when(globalConfProvider.getCredentialIssuerDids(INSTANCE))
                .thenReturn(List.of("did:web:issuer.example"))
                .thenReturn(List.of())
                .thenReturn(List.of("did:web:issuer.example"));

        assertThat(predicates.isDataspaceReady()).isTrue();
        assertThat(predicates.isDataspaceReady()).isFalse();
        assertThat(predicates.isDataspaceReady()).isTrue();
    }
}
