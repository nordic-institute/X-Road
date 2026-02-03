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

package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.objects.KeyTypeAttribute;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HardwareTokenSignerTest {

    private static final String KEY_ID = "keyId";
    private static final String TOKEN_ID = "tokenId";
    private static final char[] PIN = "pin".toCharArray();

    @Mock
    HardwareTokenSigner.SignPrivateKeyProvider privateKeyProvider;
    @Mock
    TokenDefinition tokenDefinition;
    @Mock
    Token token;
    @Mock
    SignerHwTokenAddonProperties properties;

    @Test
    void testSignNoSessionProvider() {
        try (var signer = HardwareTokenSigner.create(privateKeyProvider, tokenDefinition, token, TOKEN_ID, PIN, properties)) {

            var thrown = assertThrows(XrdRuntimeException.class,
                    () -> signer.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, "data".getBytes()));

            assertEquals("Session provider is null", thrown.getDetails());
        }
    }

    @Test
    void testSignNoPrivateKey() throws Exception {
        var sessionProvider = mock(SessionProvider.class);
        when(privateKeyProvider.getManagementSessionProvider()).thenReturn(sessionProvider);
        when(tokenDefinition.resolveSignMechanismName(KeyAlgorithm.RSA)).thenReturn(Optional.of(SignMechanism.CKM_RSA_PKCS));

        when(sessionProvider.executeWithSession(isA(SessionProvider.FuncWithSession.class)))
                .thenAnswer(invocation -> {
                    SessionProvider.FuncWithSession<Session> f = invocation.getArgument(0);
                    return f.apply(mock(ManagedPKCS11Session.class));
                });

        when(privateKeyProvider.getPrivateKey(any(), eq(KEY_ID))).thenReturn(null);

        try (var signer = HardwareTokenSigner.create(privateKeyProvider, tokenDefinition, token, TOKEN_ID, PIN, properties)) {

            var thrown = assertThrows(XrdRuntimeException.class,
                    () -> signer.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, "data".getBytes()));
            assertEquals("Key 'keyId' not found on token 'null'", thrown.getDetails());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSign(boolean pinVerificationPerSigning) throws Exception {
        var sessionProvider = mock(SessionProvider.class);
        when(privateKeyProvider.getManagementSessionProvider()).thenReturn(sessionProvider);
        when(tokenDefinition.resolveSignMechanismName(KeyAlgorithm.RSA)).thenReturn(Optional.of(SignMechanism.CKM_RSA_PKCS));
        when(tokenDefinition.pinVerificationPerSigning()).thenReturn(pinVerificationPerSigning);

        ManagedPKCS11Session managedSession = mock(ManagedPKCS11Session.class);
        Session sessionMock = mock(Session.class);
        when(managedSession.get()).thenReturn(sessionMock);

        when(sessionProvider.executeWithSession(isA(SessionProvider.FuncWithSession.class)))
                .thenAnswer(invocation -> {
                    SessionProvider.FuncWithSession<Session> f = invocation.getArgument(0);
                    return f.apply(managedSession);
                });

        PrivateKey privateKey = mock(PrivateKey.class);
        KeyTypeAttribute keyTypeAttribute = mock(KeyTypeAttribute.class);
        when(keyTypeAttribute.toString()).thenReturn("RSA");
        when(privateKey.getKeyType()).thenReturn(keyTypeAttribute);
        when(privateKeyProvider.getPrivateKey(any(), eq(KEY_ID))).thenReturn(privateKey);

        try (var signer = HardwareTokenSigner.create(privateKeyProvider, tokenDefinition, token, TOKEN_ID, PIN, properties)) {

            signer.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, "data".getBytes());

            verify(managedSession, pinVerificationPerSigning ? times(1) : never()).login(PIN);
            verify(managedSession, pinVerificationPerSigning ? times(1) : never()).logout();

            verify(sessionMock).signInit(isA(Mechanism.class), eq(privateKey));
            verify(sessionMock).sign("data".getBytes());
        }
    }

}
