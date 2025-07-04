/*
 * The MIT License
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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.CodedException;

import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedPKCS11SessionTest {

    private static final String TOKEN_ID = "test-token-1";
    private static final char[] PIN = {'p', 'i', 'n'};
    private static final long SESSION_HANDLE = 9876L;

    @Mock
    private Token token;
    @Mock
    private Session session;

    @BeforeEach
    void setUp() {
        // Common setup for mocks if needed, though most will be test-specific
    }

    @Test
    void openSessionSuccess() throws TokenException {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);

        // When
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // Then
        assertNotNull(managedSession);
        assertEquals(session, managedSession.get());
        verify(token).openSession(eq(Token.SessionType.SERIAL_SESSION), eq(true), any(), any());
    }

    @Test
    void openSessionFailsWhenTokenIsNull() {
        // When & Assert
        CodedException thrown = assertThrows(CodedException.class, () -> {
            ManagedPKCS11Session.openSession(null, TOKEN_ID);
        });
        assertThat(thrown.getMessage()).contains("Token is null");
    }

    @Test
    void openSessionFailsWhenUnderlyingOpenThrows() throws TokenException {
        // Given
        TokenException expectedException = new TokenException("Open failed");
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenThrow(expectedException);

        // When & Assert
        TokenException thrown = assertThrows(TokenException.class, () -> {
            ManagedPKCS11Session.openSession(token, TOKEN_ID);
        });
        assertEquals(expectedException, thrown);
        verify(token).openSession(eq(Token.SessionType.SERIAL_SESSION), eq(true), any(), any());
    }

    @Test
    void getSessionHandle() throws TokenException {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        when(session.getSessionHandle()).thenReturn(SESSION_HANDLE);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // When
        long handle = managedSession.getSessionHandle();

        // Then
        assertEquals(SESSION_HANDLE, handle);
        verify(session).getSessionHandle();
    }

    @Test
    void loginSuccess() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        doNothing().when(session).login(Session.CKUserType.USER, PIN);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class)) {
            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);

            // When
            boolean result = managedSession.login();

            // Then
            assertTrue(result);
            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            verify(session).login(Session.CKUserType.USER, PIN);
        }
    }

    @Test
    void loginFailsIfPinNotFound() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class)) {
            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(null);

            // When
            boolean result = managedSession.login();

            // Then
            assertFalse(result);
            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            verify(session, never()).login(anyLong(), any()); // Login not called
        }
    }

    @Test
    void loginThrowsIfSessionLoginThrowsPkcs11Exception() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        PKCS11Exception expectedException = new PKCS11Exception(0L);
        doThrow(expectedException).when(session).login(Session.CKUserType.USER, PIN);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class)) {
            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);

            // When & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, managedSession::login);
            assertEquals(expectedException, thrown);

            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            verify(session).login(Session.CKUserType.USER, PIN);
        }
    }

    @Test
    void loginFailsIfSessionLoginThrowsOtherException() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        RuntimeException expectedException = new RuntimeException("Other login failure");
        doThrow(expectedException).when(session).login(Session.CKUserType.USER, PIN);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class)) {
            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);

            // When
            boolean result = managedSession.login();

            // Then
            assertFalse(result);
            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            verify(session).login(Session.CKUserType.USER, PIN);
        }
    }

    @Test
    void logoutSuccess() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        doNothing().when(session).logout();

        // When
        boolean result = managedSession.logout();

        // Then
        assertTrue(result);
        verify(session).logout();
    }

    @Test
    void logoutThrowsIfSessionLogoutThrowsPkcs11Exception() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        PKCS11Exception expectedException = new PKCS11Exception(1L);
        doThrow(expectedException).when(session).logout();

        // When & Assert
        PKCS11Exception thrown = assertThrows(PKCS11Exception.class, managedSession::logout);
        assertEquals(expectedException, thrown);
        verify(session).logout();
    }

    @Test
    void logoutFailsIfSessionLogoutThrowsOtherException() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        RuntimeException expectedException = new RuntimeException("Other logout failure");
        doThrow(expectedException).when(session).logout();

        // When
        boolean result = managedSession.logout();

        // Then
        assertFalse(result);
        verify(session).logout();
    }

    @Test
    void closeSuccess() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        doNothing().when(session).closeSession();
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // When
        managedSession.close();

        // Then
        verify(session).closeSession();
    }

    @Test
    void closeHandlesTokenException() throws Exception {
        // Given
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        doThrow(new TokenException("Close failed")).when(session).closeSession();
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // When
        // Should not throw, only log warning
        managedSession.close();

        // Then
        verify(session).closeSession(); // Verify close was still called
    }
}
