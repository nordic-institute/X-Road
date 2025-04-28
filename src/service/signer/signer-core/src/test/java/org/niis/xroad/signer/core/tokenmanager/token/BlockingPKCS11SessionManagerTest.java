package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.CodedException;

import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockingPKCS11SessionManagerTest {

    private static final String TOKEN_ID = "block-token-0";

    @Mock
    private Token token;
    @Mock
    private ManagedPKCS11Session managedSession;

    @Test
    void constructorSuccess() throws Exception {
        // Arrange
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenReturn(managedSession);

            // Act
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Assert
            assertNotNull(manager);
            staticSessionMock.verify(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID));
        }
    }

    @Test
    void constructorFailsWhenOpenSessionThrowsTokenException() throws Exception {
        // Arrange
        TokenException expectedException = new TokenException("Open failed");
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenThrow(expectedException);

            // Act & Assert
            TokenException thrown = assertThrows(TokenException.class, () -> {
                new BlockingPKCS11SessionManager(token, TOKEN_ID);
            });
            assertEquals(expectedException, thrown);
            staticSessionMock.verify(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID));
        }
    }

    @Test
    void constructorFailsWhenOpenSessionThrowsOtherException() throws Exception {
        // Arrange
        RuntimeException underlyingException = new RuntimeException("Underlying problem");
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenThrow(underlyingException); // Simulate ManagedPKCS11Session wrapping it

            // Act & Assert
            // Constructor wraps non-TokenExceptions in CodedException
            CodedException thrown = assertThrows(CodedException.class, () -> {
                new BlockingPKCS11SessionManager(token, TOKEN_ID);
            });
            assertThat(thrown.getMessage()).contains("Failed to create session");
            assertEquals(underlyingException, thrown.getCause());
            staticSessionMock.verify(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID));
        }
    }

    @Test
    void loginDelegatesAndReturnsTrue() throws Exception {
        // Arrange
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.login()).thenReturn(true);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act
            boolean result = manager.login();

            // Assert
            assertTrue(result);
            verify(managedSession).login();
        }
    }

    @Test
    void loginDelegatesAndReturnsFalse() throws Exception {
        // Arrange
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.login()).thenReturn(false);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act
            boolean result = manager.login();

            // Assert
            assertFalse(result);
            verify(managedSession).login();
        }
    }

    @Test
    void loginPropagatesPkcs11Exception() throws Exception {
        // Arrange
        PKCS11Exception expectedException = new PKCS11Exception(1L);
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.login()).thenThrow(expectedException);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, manager::login);
            assertEquals(expectedException, thrown);
            verify(managedSession).login();
        }
    }

    @Test
    void logoutDelegatesAndReturnsTrue() throws Exception {
        // Arrange
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.logout()).thenReturn(true);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act
            boolean result = manager.logout();

            // Assert
            assertTrue(result);
            verify(managedSession).logout();
        }
    }

    @Test
    void logoutDelegatesAndReturnsFalse() throws Exception {
        // Arrange
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.logout()).thenReturn(false);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act
            boolean result = manager.logout();

            // Assert
            assertFalse(result);
            verify(managedSession).logout();
        }
    }

    @Test
    void logoutPropagatesPkcs11Exception() throws Exception {
        // Arrange
        PKCS11Exception expectedException = new PKCS11Exception(2L);
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.logout()).thenThrow(expectedException);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, manager::logout);
            assertEquals(expectedException, thrown);
            verify(managedSession).logout();
        }
    }

    @Test
    void executeWithSessionFuncSuccess() throws Exception {
        // Arrange
        String expectedResult = "OperationResult";
        AtomicReference<ManagedPKCS11Session> capturedSession = new AtomicReference<>();
        SessionProvider.FuncWithSession<String> operation = (session) -> {
            capturedSession.set(session);
            return expectedResult;
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act
            String actualResult = manager.executeWithSession(operation);

            // Assert
            assertEquals(expectedResult, actualResult);
            assertEquals(managedSession, capturedSession.get());
        }
    }

    @Test
    void executeWithSessionFuncPropagatesException() throws Exception {
        // Arrange
        Exception expectedException = new RuntimeException("Operation Failed");
        SessionProvider.FuncWithSession<String> operation = (session) -> {
            throw expectedException;
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act & Assert
            Exception thrown = assertThrows(Exception.class, () -> manager.executeWithSession(operation));
            assertEquals(expectedException, thrown);
        }
    }

    @Test
    void executeWithSessionConsumerSuccess() throws Exception {
        // Arrange
        AtomicReference<ManagedPKCS11Session> capturedSession = new AtomicReference<>();
        AtomicBoolean executed = new AtomicBoolean(false);
        SessionProvider.ConsumerWithSession operation = (session) -> {
            capturedSession.set(session);
            executed.set(true);
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act
            manager.executeWithSession(operation);

            // Assert
            assertTrue(executed.get());
            assertEquals(managedSession, capturedSession.get());
        }
    }

    @Test
    void executeWithSessionConsumerPropagatesException() throws Exception {
        // Arrange
        Exception expectedException = new RuntimeException("Operation Failed");
        SessionProvider.ConsumerWithSession operation = (session) -> {
            throw expectedException;
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Act & Assert
            Exception thrown = assertThrows(Exception.class, () -> manager.executeWithSession(operation));
            assertEquals(expectedException, thrown);
        }
    }

    @Test
    void closeDelegates() throws Exception {
        // Arrange
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);
            Mockito.doNothing().when(managedSession).close();

            // Act
            manager.close();

            // Assert
            verify(managedSession).close();
        }
    }
} 