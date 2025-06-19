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

package org.niis.xroad.securityserver.restapi.config;

import ee.ria.xroad.common.SystemProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import java.io.IOException;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class CustomClientTlsSSLSocketFactoryTest {

    private SSLSocketFactory mockInternalFactory;
    private CustomClientTlsSSLSocketFactory customFactory;
    private SSLSocket mockSSLSocket;

    @BeforeEach
    void setUp() {
        mockInternalFactory = mock(SSLSocketFactory.class);
        customFactory = new CustomClientTlsSSLSocketFactory(mockInternalFactory);
        mockSSLSocket = mock(SSLSocket.class);
    }

    @Test
    void whenCreateSocketThenAppliesProtocolsAndCipherSuites() throws IOException {
        when(mockInternalFactory.createSocket(anyString(), anyInt())).thenReturn(mockSSLSocket);

        Socket socket = customFactory.createSocket("example.com", 443);

        verify(mockSSLSocket).setEnabledProtocols(SystemProperties.getProxyClientTLSProtocols());
        verify(mockSSLSocket).setEnabledCipherSuites(SystemProperties.getProxyClientTLSCipherSuites());
        assertEquals(mockSSLSocket, socket);
    }

    @Test
    void whenCreateSocketThenWithNonSSLSocketDoesNotApplySettings() throws IOException {
        Socket plainSocket = mock(Socket.class);
        when(mockInternalFactory.createSocket(anyString(), anyInt())).thenReturn(plainSocket);

        Socket result = customFactory.createSocket("example.com", 443);

        verifyNoMoreInteractions(plainSocket);
        assertEquals(plainSocket, result);
    }

    @Test
    void whenGetSupportedCipherSuitesThenReturnsConfiguredSuites() {
        assertArrayEquals(
                SystemProperties.getProxyClientTLSCipherSuites(),
                customFactory.getSupportedCipherSuites()
        );
    }

    @Test
    void whenGetDefaultCipherSuitesThenDelegatesToInternalFactory() {
        String[] defaultSuites = {"TLS_AES_128_GCM_SHA256"};
        when(mockInternalFactory.getDefaultCipherSuites()).thenReturn(defaultSuites);

        assertArrayEquals(defaultSuites, customFactory.getDefaultCipherSuites());
    }
}
