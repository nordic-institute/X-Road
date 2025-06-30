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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;
import org.niis.xroad.signer.core.tokenmanager.CertManager;
import org.niis.xroad.signer.core.tokenmanager.KeyManager;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.TokenPinManager;
import org.niis.xroad.signer.core.tokenmanager.TokenRegistry;
import org.niis.xroad.signer.proto.Algorithm;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_PIN_POLICY_FAILURE;
import static ee.ria.xroad.common.crypto.identifier.SignMechanism.CKM_RSA_PKCS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SoftwareTokenWorkerFactoryTest {

    @Mock
    private SignerProperties signerProperties;
    @Mock
    private TokenManager tokenManager;
    @Mock
    private KeyManager keyManager;
    @Mock
    private CertManager certManager;
    @Mock
    private TokenLookup tokenLookup;
    @Mock
    private TokenPinManager pinManager;
    @Mock
    private TokenRegistry tokenRegistry;
    @Mock
    private TokenInfo tokenInfo;

    @InjectMocks
    private SoftwareTokenWorkerFactory factory;

    private static final String TOKEN_ID = "token-id";
    private static final String KEY_ID = "key-id";
    private final TokenDefinition tokenDefinition = new SoftwareTokenDefinition(Map.of(KeyAlgorithm.RSA, CKM_RSA_PKCS));
    private SoftwareTokenWorkerFactory.SoftwareTokenWorker tokenWorker;

    @BeforeEach
    void beforeEach() {
        when(tokenInfo.getId()).thenReturn(TOKEN_ID);
        tokenWorker = factory.create(tokenInfo, tokenDefinition);
    }

    @Test
    void testRefresh() throws Exception {
        when(pinManager.tokenHasPin(TOKEN_ID)).thenReturn(true);
        when(pinManager.verifyTokenPin(TOKEN_ID, "Secret1234".toCharArray())).thenReturn(true);
        var keyInfoMock = mock(KeyInfo.class);
        when(keyInfoMock.getId()).thenReturn(KEY_ID);
        when(tokenLookup.listKeys(TOKEN_ID)).thenReturn(List.of(keyInfoMock));
        when(tokenLookup.getSoftwareTokenKeyStore(KEY_ID)).thenReturn(Optional.of(
                IOUtils.toByteArray(getClass().getResourceAsStream("/keystore.p12"))));

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn("Secret1234".toCharArray());
            tokenWorker.refresh();
        }

        verify(tokenManager, times(2)).setTokenActive(TOKEN_ID, true);
        verify(tokenManager).setTokenStatus(TOKEN_ID, TokenStatusInfo.OK);
        verify(tokenRegistry).refresh();
        verify(keyManager).setKeyAvailable(KEY_ID, true);
    }

    @Test
    void testRefreshNoPinHashInToken() {
        when(pinManager.tokenHasPin(TOKEN_ID)).thenReturn(false);

        tokenWorker.refresh();

        verify(tokenManager).setTokenStatus(TOKEN_ID, TokenStatusInfo.NOT_INITIALIZED);
        verify(tokenManager).setTokenActive(TOKEN_ID, false);
        verify(tokenRegistry).refresh();
        verifyNoInteractions(keyManager);
    }

    @Test
    void testRefreshNoPinInPasswordStore() {
        when(pinManager.tokenHasPin(TOKEN_ID)).thenReturn(true);

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(null);
            tokenWorker.refresh();
        }

        verify(tokenManager).setTokenActive(TOKEN_ID, false);
        verify(tokenRegistry).refresh();
        verifyNoInteractions(keyManager);
    }

    @Test
    void testHandleGenerateKey() {
        var genKeyReq = GenerateKeyReq.newBuilder()
                .setTokenId(TOKEN_ID)
                .setKeyLabel("keyLabel")
                .setAlgorithm(Algorithm.RSA)
                .build();

        when(tokenLookup.isTokenActive(TOKEN_ID)).thenReturn(true);

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn("pin".toCharArray());

            tokenWorker.handleGenerateKey(genKeyReq);

            verify(keyManager).addKey(eq(TOKEN_ID), isA(String.class), any(), any(), eq(CKM_RSA_PKCS), eq("keyLabel"), eq("keyLabel"));
            verify(keyManager).setKeyAvailable(isA(String.class), eq(true));
            verify(tokenLookup).findKeyInfo(isA(String.class));
        }
    }

    @Test
    void testInitializeToken() {
        when(signerProperties.enforceTokenPinPolicy()).thenReturn(false);

        tokenWorker.initializeToken(new char[]{'p', 'i', 'n'});

        verify(pinManager).setTokenPin(TOKEN_ID, new char[]{'p', 'i', 'n'});
        verify(tokenManager).enableToken(tokenDefinition);
        verify(tokenManager).setTokenStatus(TOKEN_ID, TokenStatusInfo.OK);
    }

    @Test
    void testInitializeTokenPolicyFail() {
        when(signerProperties.enforceTokenPinPolicy()).thenReturn(true);

        var thrown = assertThrows(CodedException.class, () -> tokenWorker.initializeToken(new char[]{'p', 'i', 'n'}));
        assertEquals("Token PIN does not meet complexity requirements", thrown.getFaultString());
        assertEquals(X_TOKEN_PIN_POLICY_FAILURE, thrown.getFaultCode());

        verifyNoInteractions(pinManager, tokenManager);
    }

    @Test
    void testHandleUpdateTokenPin() {
        char[] oldPIN = "oldPin".toCharArray();
        char[] newPIN = "newPin".toCharArray();

        when(pinManager.verifyTokenPin(TOKEN_ID, oldPIN)).thenReturn(true);

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(oldPIN);

            tokenWorker.handleUpdateTokenPin("oldPin".toCharArray(), newPIN);

            passwordStoreMock.verify(() -> PasswordStore.storePassword(TOKEN_ID, null));
            verify(tokenManager).setTokenActive(TOKEN_ID, false);
            verify(pinManager).updateTokenPin(TOKEN_ID, oldPIN, newPIN);
        }
    }

    @Test
    void testHandleUpdateTokenPinIncorrectOldPin() {
        char[] oldPIN = "oldPin".toCharArray();

        when(pinManager.verifyTokenPin(TOKEN_ID, oldPIN)).thenReturn(false);

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(oldPIN);

            var thrown = assertThrows(CodedException.class, () -> tokenWorker.handleUpdateTokenPin("oldPin".toCharArray(), new char[0]));

            assertEquals("PinIncorrect: PIN incorrect", thrown.getMessage());

            verify(tokenManager).setTokenStatus(TOKEN_ID, TokenStatusInfo.USER_PIN_INCORRECT);
        }
    }

    @Test
    void testHandleUpdateTokenPinNoLogoutWhenUpdateFails() {
        char[] oldPIN = "oldPin".toCharArray();
        char[] newPIN = "newPin".toCharArray();

        when(pinManager.verifyTokenPin(TOKEN_ID, oldPIN)).thenReturn(true);
        doThrow(new CodedException("fail")).when(pinManager).updateTokenPin(TOKEN_ID, oldPIN, newPIN);

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn(oldPIN);

            assertThrows(CodedException.class, () -> tokenWorker.handleUpdateTokenPin("oldPin".toCharArray(), newPIN));

            passwordStoreMock.verify(() -> PasswordStore.storePassword(TOKEN_ID, null), never());
            verify(tokenManager, never()).setTokenActive(TOKEN_ID, false);
        }
    }

    @Test
    void testIsSoftwareToken() {
        boolean isSoftwareToken = tokenWorker.isSoftwareToken();

        assertTrue(isSoftwareToken);
    }

    @Test
    void testDeleteKey() {
        tokenWorker.deleteKey("dummyKey");

        verifyNoInteractions(tokenManager, keyManager, certManager, tokenLookup, pinManager, tokenRegistry);
    }

    @Test
    void testDeleteCert() {
        tokenWorker.deleteCert("dummyCertId");

        verify(certManager).removeCert("dummyCertId");
    }

    @Test
    void testSign() throws Exception {
        when(tokenLookup.isTokenActive(TOKEN_ID)).thenReturn(true);
        when(tokenLookup.isKeyAvailable(KEY_ID)).thenReturn(true);
        when(tokenLookup.getSoftwareTokenKeyStore(KEY_ID)).thenReturn(Optional.of(
                IOUtils.toByteArray(getClass().getResourceAsStream("/keystore.p12"))));

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn("Secret1234".toCharArray());

            var signature = tokenWorker.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, new byte[]{1, 2, 3});
            assertNotNull(signature);
        }
    }

    @Test
    void testSignTokenNotActive() {
        when(tokenLookup.isTokenActive(TOKEN_ID)).thenReturn(false);

        var thrown = assertThrows(CodedException.class, () -> tokenWorker.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, new byte[]{1, 2, 3}));
        assertEquals("TokenNotActive: Token 'token-id' not active", thrown.getMessage());
    }

    @Test
    void testSignKeyNotAvailable() {
        when(tokenLookup.isTokenActive(TOKEN_ID)).thenReturn(true);
        when(tokenLookup.isKeyAvailable(KEY_ID)).thenReturn(false);

        var thrown = assertThrows(CodedException.class, () -> tokenWorker.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, new byte[]{1, 2, 3}));
        assertEquals("KeyNotFound: Key 'key-id' not available", thrown.getMessage());
    }

    @Test
    void testSigKeyNotFound() {
        when(tokenLookup.isTokenActive(TOKEN_ID)).thenReturn(true);
        when(tokenLookup.isKeyAvailable(KEY_ID)).thenReturn(true);
        when(tokenLookup.getSoftwareTokenKeyStore(KEY_ID)).thenReturn(Optional.empty());

        var thrown = assertThrows(CodedException.class, () -> tokenWorker.sign(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, new byte[]{1, 2, 3}));
        assertEquals("KeyNotFound: Key 'key-id' not found", thrown.getMessage());
    }

    @Test
    void testSignCertificate() throws Exception {
        when(tokenLookup.isTokenActive(TOKEN_ID)).thenReturn(true);
        when(tokenLookup.isKeyAvailable(KEY_ID)).thenReturn(true);

        var keyInfoMock = mock(KeyInfo.class);
        var certInfoMock = mock(CertificateInfo.class);
        when(certInfoMock.getCertificateBytes()).thenReturn(TestCertUtil.getCaCert().getEncoded());
        when(keyInfoMock.getCerts()).thenReturn(List.of(certInfoMock));

        when(tokenLookup.getKeyInfo(KEY_ID)).thenReturn(keyInfoMock);
        when(tokenLookup.getSoftwareTokenKeyStore(KEY_ID)).thenReturn(
                Optional.of(IOUtils.toByteArray(getClass().getResourceAsStream("/keystore.p12"))));

        try (MockedStatic<PasswordStore> passwordStoreMock = mockStatic(PasswordStore.class)) {
            passwordStoreMock.when(() -> PasswordStore.getPassword(TOKEN_ID)).thenReturn("Secret1234".toCharArray());

            var result = tokenWorker.signCertificate(KEY_ID, SignAlgorithm.SHA256_WITH_RSA, "CN=Test",
                    TestCertUtil.getCaCert().getPublicKey());
            assertNotNull(result);
        }
    }

}
