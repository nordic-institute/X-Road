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
import iaik.pkcs.pkcs11.SessionInfo;
import iaik.pkcs.pkcs11.State;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HardwareTokenSessionPoolTest {

    private static final String TOKEN_ID = "testToken-0";
    private static final char[] PIN = {'1', '2', '3', '4'};

    @Mock
    private SignerHwTokenAddonProperties properties;
    @Mock
    private Token token;
    @Mock
    private Session session;
    @Mock
    private SessionInfo sessionInfo;

    @BeforeEach
    void setUp() {
        // No common setup needed now, mocks are handled per test
    }

    @Test
    void shouldInitializePoolAndPrefill() throws Exception {
        // Arrange
        when(properties.poolMaxTotal()).thenReturn(5);
        when(properties.poolMinIdle()).thenReturn(1);
        when(properties.poolMaxIdle()).thenReturn(3);
        when(properties.sessionAcquireTimeout()).thenReturn(Duration.ofSeconds(10));

        // Create the mock ManagedPKCS11Session instance beforehand
        ManagedPKCS11Session managedSessionMockInstance = mock(ManagedPKCS11Session.class);
        when(managedSessionMockInstance.login()).thenReturn(true); // Successful login
        when(managedSessionMockInstance.getSessionHandle()).thenReturn(54321L);

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class);
             MockedStatic<ManagedPKCS11Session> managedSessionStaticMock = mockStatic(ManagedPKCS11Session.class)) {

            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);

            // Configure the static mock to return the pre-defined instance
            managedSessionStaticMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                    .thenReturn(managedSessionMockInstance);

            // Act
            HardwareTokenSessionPool pool = new HardwareTokenSessionPool(properties, token, TOKEN_ID);

            // Assert
            assertNotNull(pool);

            // Verify pool config was read
            verify(properties).poolMaxTotal();
            verify(properties).poolMinIdle();
            verify(properties).poolMaxIdle();
            verify(properties).sessionAcquireTimeout();

            // Verify session creation and login during prefill (minIdle = 1)
            managedSessionStaticMock.verify(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID), times(1));
            verify(managedSessionMockInstance).login(); // Verify login on the instance
            passwordStoreMock.verify(() -> PasswordStore.getPassword(TOKEN_ID), times(1)); // Pin fetched for login

            // Validation (`get`, `getSessionInfo`, `getState`) happens on borrow, not necessarily during prefill.
            // Remove verification for these methods in this test.
            // verify(managedSessionMockInstance, times(1)).get();
            // verify(session, times(1)).getSessionInfo();
            // verify(sessionInfo, times(1)).getState();

            pool.close();
            verify(managedSessionMockInstance).close(); // Verify session closed on pool close
        }
    }

    @Test
    void shouldFailInitializationIfTokenIsNull() {
        // Act & Assert
        CodedException thrown = assertThrows(CodedException.class, () -> {
            new HardwareTokenSessionPool(properties, null, TOKEN_ID);
        });
        assertThat(thrown.getMessage()).contains("Token is null for pool initialization");
    }


    @Test
    void shouldFailPrefillIfLoginFails() throws TokenException {
        // Arrange
        when(properties.poolMaxTotal()).thenReturn(5);
        when(properties.poolMinIdle()).thenReturn(1);
        when(properties.poolMaxIdle()).thenReturn(3);
        when(properties.sessionAcquireTimeout()).thenReturn(Duration.ofSeconds(10));
        // No session validation needed as prefill fails before validation

        // Act & Assert
        SignerException thrown = assertThrows(SignerException.class, () -> {
            try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class);
                 MockedStatic<ManagedPKCS11Session> managedSessionMock = mockStatic(ManagedPKCS11Session.class)) {

                passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);

                managedSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                        .thenAnswer(invocation -> {
                            ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
                            when(managedSession.login()).thenReturn(false); // Login fails
                            doNothing().when(managedSession).close(); // Mock close to prevent NPE in factory create failure path
                            return managedSession;
                        });

                new HardwareTokenSessionPool(properties, token, TOKEN_ID); // This should throw
            }
        });
        assertThat(thrown.getMessage()).contains("Failed to pre-fill session pool for token " + TOKEN_ID);
    }

    @Test
    void shouldFailPrefillIfPasswordNotAvailable() throws TokenException {
        // Arrange
        when(properties.poolMaxTotal()).thenReturn(5);
        when(properties.poolMinIdle()).thenReturn(1);
        when(properties.poolMaxIdle()).thenReturn(3);
        when(properties.sessionAcquireTimeout()).thenReturn(Duration.ofSeconds(10));

        // Act & Assert
         SignerException thrown = assertThrows(SignerException.class, () -> {
            try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class);
                 MockedStatic<ManagedPKCS11Session> managedSessionMock = mockStatic(ManagedPKCS11Session.class)) {

                passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(null); // No PIN

                managedSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID))
                        .thenAnswer(invocation -> mock(ManagedPKCS11Session.class));

                new HardwareTokenSessionPool(properties, token, TOKEN_ID); // This should throw
            }
        });
        assertThat(thrown.getMessage()).contains("Failed to pre-fill session pool for token " + TOKEN_ID);
        // Check the underlying cause reported by the factory is about the missing PIN
        assertThat(thrown.getCause()).isInstanceOf(CodedException.class);
        assertThat(thrown.getCause().getMessage()).contains("PIN not available in PasswordStore");

    }

    @Test
    void executeWithSessionShouldBorrowAndReturn() throws Exception {
        // Arrange
        when(properties.poolMaxTotal()).thenReturn(5);
        when(properties.poolMinIdle()).thenReturn(1);
        when(properties.poolMaxIdle()).thenReturn(3);
        when(properties.sessionAcquireTimeout()).thenReturn(Duration.ofSeconds(10));
        when(session.getSessionInfo()).thenReturn(sessionInfo); // Needed for validation during borrow
        when(sessionInfo.getState()).thenReturn(State.RO_USER_FUNCTIONS); // Needed for validation

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class);
             MockedStatic<ManagedPKCS11Session> managedSessionMock = mockStatic(ManagedPKCS11Session.class)) {

            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);
            ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
            when(managedSession.get()).thenReturn(session);
            when(managedSession.login()).thenReturn(true);
            when(managedSession.getSessionHandle()).thenReturn(111L);
            managedSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);

            HardwareTokenSessionPool pool = new HardwareTokenSessionPool(properties, token, TOKEN_ID);

            // Use a spy to verify borrow/return on the actual pool instance
            GenericObjectPool<ManagedPKCS11Session> actualPool = spy(getInternalPool(pool));
            setInternalPool(pool, actualPool); // Replace internal pool with spy

            AtomicBoolean operationExecuted = new AtomicBoolean(false);

            // Act
            pool.executeWithSession(s -> {
                assertThat(s).isSameAs(managedSession);
                operationExecuted.set(true);
            });

            // Assert
            assertTrue(operationExecuted.get());
            verify(actualPool).borrowObject();
            verify(actualPool).returnObject(managedSession);

            pool.close();
        }
    }

    @Test
    void executeWithSessionFuncShouldBorrowReturnAndReturnValue() throws Exception {
        // Arrange
        when(properties.poolMaxTotal()).thenReturn(5);
        when(properties.poolMinIdle()).thenReturn(1);
        when(properties.poolMaxIdle()).thenReturn(3);
        when(properties.sessionAcquireTimeout()).thenReturn(Duration.ofSeconds(10));
        when(session.getSessionInfo()).thenReturn(sessionInfo); // Needed for validation during borrow
        when(sessionInfo.getState()).thenReturn(State.RO_USER_FUNCTIONS); // Needed for validation

         try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class);
             MockedStatic<ManagedPKCS11Session> managedSessionMock = mockStatic(ManagedPKCS11Session.class)) {

            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(PIN);
            ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
            when(managedSession.get()).thenReturn(session);
            when(managedSession.login()).thenReturn(true);
            when(managedSession.getSessionHandle()).thenReturn(222L);
            managedSessionMock.when(() -> ManagedPKCS11Session.openSession(token, TOKEN_ID)).thenReturn(managedSession);


            HardwareTokenSessionPool pool = new HardwareTokenSessionPool(properties, token, TOKEN_ID);
            GenericObjectPool<ManagedPKCS11Session> actualPool = spy(getInternalPool(pool));
            setInternalPool(pool, actualPool); // Replace internal pool with spy

            String expectedResult = "Success";

            // Act
            String result = pool.executeWithSession(s -> {
                assertThat(s).isSameAs(managedSession);
                return expectedResult;
            });

            // Assert
            assertEquals(expectedResult, result);
            verify(actualPool).borrowObject();
            verify(actualPool).returnObject(managedSession);

            pool.close();
        }
    }

     @Test
    void sessionValidationShouldSucceed() throws Exception {
        // Arrange
        when(session.getSessionInfo()).thenReturn(sessionInfo); // Needed for validateObject call
        when(sessionInfo.getState()).thenReturn(State.RO_USER_FUNCTIONS); // Needed for validateObject call

        HardwareTokenSessionPool.ManagedPKCS11SessionFactory factory =
                new HardwareTokenSessionPool.ManagedPKCS11SessionFactory(token, TOKEN_ID);

        ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
        when(managedSession.get()).thenReturn(session);

        // Act & Assert
        assertTrue(factory.validateObject(new org.apache.commons.pool2.impl.DefaultPooledObject<>(managedSession)));
        verify(session).getSessionInfo();
        verify(sessionInfo).getState();
    }

    @Test
    void sessionValidationShouldFailIfSessionIsNull() {
        // Arrange
        HardwareTokenSessionPool.ManagedPKCS11SessionFactory factory =
                new HardwareTokenSessionPool.ManagedPKCS11SessionFactory(token, TOKEN_ID);

        // Act & Assert
        assertFalse(factory.validateObject(new org.apache.commons.pool2.impl.DefaultPooledObject<>(null)));
    }

    @Test
    void sessionValidationShouldFailIfGetSessionInfoThrows() throws Exception {
        // Arrange
        HardwareTokenSessionPool.ManagedPKCS11SessionFactory factory =
                new HardwareTokenSessionPool.ManagedPKCS11SessionFactory(token, TOKEN_ID);

        ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
        when(managedSession.get()).thenReturn(session);
        when(managedSession.getSessionHandle()).thenReturn(333L); // For logging in validateObject
        when(session.getSessionInfo()).thenThrow(new TokenException("Failed to get info")); // Specific mock for this test

        // Act & Assert
        assertFalse(factory.validateObject(new org.apache.commons.pool2.impl.DefaultPooledObject<>(managedSession)));
        verify(session).getSessionInfo();
    }

    @Test
    void sessionValidationShouldFailIfStateIsNull() throws Exception {
        // Arrange
        HardwareTokenSessionPool.ManagedPKCS11SessionFactory factory =
                new HardwareTokenSessionPool.ManagedPKCS11SessionFactory(token, TOKEN_ID);

        ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
        when(managedSession.get()).thenReturn(session);
        when(session.getSessionInfo()).thenReturn(sessionInfo);
        when(sessionInfo.getState()).thenReturn(null); // Specific mock for this test: Invalid state

        // Act & Assert
        assertFalse(factory.validateObject(new org.apache.commons.pool2.impl.DefaultPooledObject<>(managedSession)));
        verify(session).getSessionInfo();
        verify(sessionInfo).getState();
    }

    @Test
    void destroyObjectShouldCloseSession() throws Exception {
        // Arrange
        HardwareTokenSessionPool.ManagedPKCS11SessionFactory factory =
                new HardwareTokenSessionPool.ManagedPKCS11SessionFactory(token, TOKEN_ID);
        ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
        when(managedSession.getSessionHandle()).thenReturn(999L); // For logging

        // Act
        factory.destroyObject(new org.apache.commons.pool2.impl.DefaultPooledObject<>(managedSession));

        // Assert
        verify(managedSession).close();
    }

    @Test
    void destroyObjectShouldHandleCloseException() throws Exception {
        // Arrange
        HardwareTokenSessionPool.ManagedPKCS11SessionFactory factory =
                new HardwareTokenSessionPool.ManagedPKCS11SessionFactory(token, TOKEN_ID);
        ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
        when(managedSession.getSessionHandle()).thenReturn(888L); // For logging
        doThrow(new RuntimeException("Close failed")).when(managedSession).close();

        // Act
        // Should not throw exception
        factory.destroyObject(new org.apache.commons.pool2.impl.DefaultPooledObject<>(managedSession));

        // Assert
        verify(managedSession).close(); // Verify close was still called
    }

    // --- Helper methods for accessing/modifying the internal pool via reflection ---

    @SuppressWarnings("unchecked")
    private GenericObjectPool<ManagedPKCS11Session> getInternalPool(HardwareTokenSessionPool instance) throws Exception {
        java.lang.reflect.Field poolField = HardwareTokenSessionPool.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        return (GenericObjectPool<ManagedPKCS11Session>) poolField.get(instance);
    }

    private void setInternalPool(HardwareTokenSessionPool instance, GenericObjectPool<ManagedPKCS11Session> pool) throws Exception {
        java.lang.reflect.Field poolField = HardwareTokenSessionPool.class.getDeclaredField("pool");
        poolField.setAccessible(true);
        poolField.set(instance, pool);
    }
}
