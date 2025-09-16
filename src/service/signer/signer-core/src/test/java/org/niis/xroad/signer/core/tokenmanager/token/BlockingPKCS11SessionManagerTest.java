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

import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        // Given
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenReturn(managedSession);

            // When
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // Then
            assertNotNull(manager);
            staticSessionMock.verify(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID));
        }
    }

    @Test
    void constructorFailsWhenOpenSessionThrowsTokenException() {
        // Given
        TokenException expectedException = new TokenException("Open failed");
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenThrow(expectedException);

            // When & Assert
            TokenException thrown = assertThrows(TokenException.class, () -> {
                new BlockingPKCS11SessionManager(token, TOKEN_ID);
            });
            assertEquals(expectedException, thrown);
            staticSessionMock.verify(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID));
        }
    }

    @Test
    void constructorFailsWhenOpenSessionThrowsOtherException() {
        // Given
        RuntimeException underlyingException = new RuntimeException("Underlying problem");
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenThrow(underlyingException); // Simulate ManagedPKCS11Session wrapping it

            // When & Assert
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
        // Given
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.login()).thenReturn(true);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When
            boolean result = manager.login();

            // Then
            assertTrue(result);
            verify(managedSession).login();
        }
    }

    @Test
    void loginDelegatesAndReturnsFalse() throws Exception {
        // Given
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.login()).thenReturn(false);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When
            boolean result = manager.login();

            // Then
            assertFalse(result);
            verify(managedSession).login();
        }
    }

    @Test
    void loginPropagatesPkcs11Exception() throws Exception {
        // Given
        PKCS11Exception expectedException = new PKCS11Exception(1L);
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.login()).thenThrow(expectedException);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, manager::login);
            assertEquals(expectedException, thrown);
            verify(managedSession).login();
        }
    }

    @Test
    void logoutDelegatesAndReturnsTrue() throws Exception {
        // Given
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.logout()).thenReturn(true);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When
            boolean result = manager.logout();

            // Then
            assertTrue(result);
            verify(managedSession).logout();
        }
    }

    @Test
    void logoutDelegatesAndReturnsFalse() throws Exception {
        // Given
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.logout()).thenReturn(false);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When
            boolean result = manager.logout();

            // Then
            assertFalse(result);
            verify(managedSession).logout();
        }
    }

    @Test
    void logoutPropagatesPkcs11Exception() throws Exception {
        // Given
        PKCS11Exception expectedException = new PKCS11Exception(2L);
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            when(managedSession.logout()).thenThrow(expectedException);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When & Assert
            PKCS11Exception thrown = assertThrows(PKCS11Exception.class, manager::logout);
            assertEquals(expectedException, thrown);
            verify(managedSession).logout();
        }
    }

    @Test
    void executeWithSessionFuncSuccess() throws Exception {
        // Given
        String expectedResult = "OperationResult";
        AtomicReference<ManagedPKCS11Session> capturedSession = new AtomicReference<>();
        SessionProvider.FuncWithSession<String> operation = (session) -> {
            capturedSession.set(session);
            return expectedResult;
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When
            String actualResult = manager.executeWithSession(operation);

            // Then
            assertEquals(expectedResult, actualResult);
            assertEquals(managedSession, capturedSession.get());
        }
    }

    @Test
    void executeWithSessionFuncPropagatesException() throws Exception {
        // Given
        var expectedException = XrdRuntimeException.systemInternalError("Operation Failed");
        SessionProvider.FuncWithSession<String> operation = (session) -> {
            throw expectedException;
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When & Assert
            Exception thrown = assertThrows(Exception.class, () -> manager.executeWithSession(operation));
            assertEquals(expectedException, thrown);
        }
    }

    @Test
    void executeWithSessionConsumerSuccess() throws Exception {
        // Given
        AtomicReference<ManagedPKCS11Session> capturedSession = new AtomicReference<>();
        AtomicBoolean executed = new AtomicBoolean(false);
        SessionProvider.ConsumerWithSession operation = (session) -> {
            capturedSession.set(session);
            executed.set(true);
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When
            manager.executeWithSession(operation);

            // Then
            assertTrue(executed.get());
            assertEquals(managedSession, capturedSession.get());
        }
    }

    @Test
    void executeWithSessionConsumerPropagatesException() throws Exception {
        // Given
        var expectedException = XrdRuntimeException.systemInternalError("Operation Failed");
        SessionProvider.ConsumerWithSession operation = (session) -> {
            throw expectedException;
        };

        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);

            // When & Assert
            Exception thrown = assertThrows(Exception.class, () -> manager.executeWithSession(operation));
            assertEquals(expectedException, thrown);
        }
    }

    @Test
    void closeDelegates() throws Exception {
        // Given
        try (MockedStatic<ManagedPKCS11Session> staticSessionMock = mockStatic(ManagedPKCS11Session.class)) {
            staticSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);
            BlockingPKCS11SessionManager manager = new BlockingPKCS11SessionManager(token, TOKEN_ID);
            Mockito.doNothing().when(managedSession).close();

            // When
            manager.close();

            // Then
            verify(managedSession).close();
        }
    }
}
