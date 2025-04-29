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
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);

        // Act
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // Assert
        assertNotNull(managedSession);
        assertEquals(session, managedSession.get());
        verify(token).openSession(eq(Token.SessionType.SERIAL_SESSION), eq(true), any(), any());
    }

    @Test
    void openSessionFailsWhenTokenIsNull() {
        // Act & Assert
        CodedException thrown = assertThrows(CodedException.class, () -> {
            ManagedPKCS11Session.openSession(null, TOKEN_ID);
        });
        assertThat(thrown.getMessage()).contains("Token is null");
    }

    @Test
    void openSessionFailsWhenUnderlyingOpenThrows() throws TokenException {
        // Arrange
        TokenException expectedException = new TokenException("Open failed");
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenThrow(expectedException);

        // Act & Assert
        TokenException thrown = assertThrows(TokenException.class, () -> {
            ManagedPKCS11Session.openSession(token, TOKEN_ID);
        });
        assertEquals(expectedException, thrown);
        verify(token).openSession(eq(Token.SessionType.SERIAL_SESSION), eq(true), any(), any());
    }

    @Test
    void getSessionHandle() throws TokenException {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        when(session.getSessionHandle()).thenReturn(SESSION_HANDLE);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // Act
        long handle = managedSession.getSessionHandle();

        // Assert
        assertEquals(SESSION_HANDLE, handle);
        verify(session).getSessionHandle();
    }

    @Test
    void loginSuccess() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class);
             MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {

            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);
            htuMock.when(() -> HardwareTokenUtil.login(session, PIN)).thenAnswer(invocation -> null); // Mock void method

            // Act
            boolean result = managedSession.login();

            // Assert
            assertTrue(result);
            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            htuMock.verify(() -> HardwareTokenUtil.login(session, PIN));
        }
    }

    @Test
    void loginFailsIfPinNotFound() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class);
             MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {

            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(null);

            // Act
            boolean result = managedSession.login();

            // Assert
            assertFalse(result);
            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            htuMock.verify(() -> HardwareTokenUtil.login(any(), any()), never()); // Login util not called
        }
    }

    @Test
    void loginThrowsIfUtilThrowsPkcs11Exception() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        PKCS11Exception expectedException = new PKCS11Exception(0L);

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class);
             MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {

            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);
            htuMock.when(() -> HardwareTokenUtil.login(session, PIN)).thenThrow(expectedException);

            // Act & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, managedSession::login);
            assertEquals(expectedException, thrown);

            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            htuMock.verify(() -> HardwareTokenUtil.login(session, PIN));
        }
    }

    @Test
    void loginFailsIfUtilThrowsOtherException() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        RuntimeException expectedException = new RuntimeException("Other login failure");

        try (MockedStatic<PasswordStore> psMock = mockStatic(PasswordStore.class);
             MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {

            psMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);
            htuMock.when(() -> HardwareTokenUtil.login(session, PIN)).thenThrow(expectedException);

            // Act
            boolean result = managedSession.login();

            // Assert
            assertFalse(result);
            psMock.verify(() -> PasswordStore.getPassword(TOKEN_ID));
            htuMock.verify(() -> HardwareTokenUtil.login(session, PIN));
        }
    }

    @Test
    void logoutSuccess() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        try (MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {
            htuMock.when(() -> HardwareTokenUtil.logout(session)).thenAnswer(invocation -> null);

            // Act
            boolean result = managedSession.logout();

            // Assert
            assertTrue(result);
            htuMock.verify(() -> HardwareTokenUtil.logout(session));
        }
    }

    @Test
    void logoutThrowsIfUtilThrowsPkcs11Exception() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        PKCS11Exception expectedException = new PKCS11Exception(1L);

        try (MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {
            htuMock.when(() -> HardwareTokenUtil.logout(session)).thenThrow(expectedException);

            // Act & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, managedSession::logout);
            assertEquals(expectedException, thrown);
            htuMock.verify(() -> HardwareTokenUtil.logout(session));
        }
    }

    @Test
    void logoutFailsIfUtilThrowsOtherException() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);
        RuntimeException expectedException = new RuntimeException("Other logout failure");

        try (MockedStatic<HardwareTokenUtil> htuMock = mockStatic(HardwareTokenUtil.class)) {
            htuMock.when(() -> HardwareTokenUtil.logout(session)).thenThrow(expectedException);

            // Act
            boolean result = managedSession.logout();

            // Assert
            assertFalse(result);
            htuMock.verify(() -> HardwareTokenUtil.logout(session));
        }
    }

    @Test
    void closeSuccess() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        doNothing().when(session).closeSession();
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // Act
        managedSession.close();

        // Assert
        verify(session).closeSession();
    }

    @Test
    void closeHandlesTokenException() throws Exception {
        // Arrange
        when(token.openSession(anyBoolean(), anyBoolean(), any(), any())).thenReturn(session);
        doThrow(new TokenException("Close failed")).when(session).closeSession();
        ManagedPKCS11Session managedSession = ManagedPKCS11Session.openSession(token, TOKEN_ID);

        // Act
        // Should not throw, only log warning
        managedSession.close();

        // Assert
        verify(session).closeSession(); // Verify close was still called
    }
}
