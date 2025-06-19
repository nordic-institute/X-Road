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
