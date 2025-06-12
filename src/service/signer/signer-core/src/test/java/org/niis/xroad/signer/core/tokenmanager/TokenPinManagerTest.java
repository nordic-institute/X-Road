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

package org.niis.xroad.signer.core.tokenmanager;

import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.service.TokenService;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwarePinHasher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenPinManagerTest {

    private static final long TOKEN_ID = 123;
    private static final String TOKEN_EXTERNAL_ID = "externalId";

    private final TokenService tokenService = mock(TokenService.class);
    private final SoftwarePinHasher softwarePinHasher = mock(SoftwarePinHasher.class);
    private final TokenRegistryLoader tokenRegistryLoader = mock(TokenRegistryLoader.class);

    private final TokenRegistry tokenRegistry = new TokenRegistry(tokenRegistryLoader);
    private final TokenPinManager tokenPinManager = new TokenPinManager(tokenRegistry, tokenService, softwarePinHasher);

    @Test
    void testSetTokenPin() throws Exception {
        RuntimeTokenImpl tokenMock = tokenMock(TOKEN_EXTERNAL_ID);
        when(softwarePinHasher.hashPin("newPin".toCharArray())).thenReturn(new byte[]{1, 2, 3});

        initRegistry(Set.of(tokenMock));

        tokenPinManager.setTokenPin(TOKEN_EXTERNAL_ID, "newPin".toCharArray());

        verify(tokenService).setInitialTokenPin(TOKEN_ID, new byte[]{1, 2, 3});
        verify(tokenRegistryLoader).refreshTokens(Set.of(tokenMock));
    }

    @Test
    void testUpdateTokenPin() throws Exception {
        RuntimeTokenImpl tokenMock = tokenMock(TOKEN_EXTERNAL_ID);
        when(tokenMock.softwareTokenPinHash()).thenReturn(Optional.of(new byte[]{1, 2, 3}));

        long keyId = 123L;
        String keyAlias = "3A480F9A822DB1AF0349A1184AB84B5B74ED23EF";
        RuntimeKeyImpl keyMock = mock(RuntimeKeyImpl.class);
        when(keyMock.softwareKeyStore()).thenReturn(Optional.of(loadTestKeystore("keystore.p12")));
        when(keyMock.externalId()).thenReturn(keyAlias);
        when(keyMock.id()).thenReturn(keyId);
        when(tokenMock.keys()).thenReturn(Set.of(keyMock));

        when(softwarePinHasher.hashPin("newPin".toCharArray())).thenReturn(new byte[]{7, 8, 9});

        initRegistry(Set.of(tokenMock));

        tokenPinManager.updateTokenPin(TOKEN_EXTERNAL_ID, "Secret1234".toCharArray(), "newPin".toCharArray());

        ArgumentCaptor<Map<Long, byte[]>> captor = ArgumentCaptor.forClass(Map.class);
        verify(tokenService).updateTokenPin(eq(TOKEN_ID), captor.capture(), eq(new byte[]{7, 8, 9}));

        assertEquals(1, captor.getValue().size());
        assertNotNull(captor.getValue().get(keyId));
        assertLoadKey(captor.getValue().get(keyId), keyAlias, "newPin".toCharArray());

        verify(tokenRegistryLoader).refreshTokens(Set.of(tokenMock));
    }

    private void assertLoadKey(byte[] keystore, String keyLabel, char[] pin) throws Exception {
        KeyStore keyStore = CryptoUtils.loadPkcs12KeyStore(new ByteArrayInputStream(keystore), pin);

        assertNotNull(keyStore.getKey(keyLabel, pin));
    }

    private byte[] loadTestKeystore(String filename) {
        try (InputStream is = TokenPinManagerTest.class.getResourceAsStream("/keystore/" + filename)) {
            if (is == null) {
                throw new IOException("Keystore file not found: " + filename);
            }
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load keystore", e);
        }
    }

    @Test
    void testVerifyTokenPin() {
        RuntimeTokenImpl tokenMock = tokenMock(TOKEN_EXTERNAL_ID);
        when(tokenMock.softwareTokenPinHash()).thenReturn(Optional.of(new byte[]{'h', 'a', 's', 'h'}));

        when(softwarePinHasher.hashPin("token-pin".toCharArray())).thenReturn(new byte[]{'h', 'a', 's', 'h'});

        initRegistry(Set.of(tokenMock));

        assertTrue(tokenPinManager.verifyTokenPin(TOKEN_EXTERNAL_ID, "token-pin".toCharArray()));
    }

    @Test
    void testTokenHasPin() {
        RuntimeTokenImpl tokenMock = tokenMock(TOKEN_EXTERNAL_ID);
        when(tokenMock.softwareTokenPinHash()).thenReturn(Optional.of(new byte[]{'h', 'a', 's', 'h'}));

        RuntimeTokenImpl noPinTokenMock = tokenMock("no-pin-token");
        when(noPinTokenMock.softwareTokenPinHash()).thenReturn(Optional.of(new byte[0]));

        initRegistry(Set.of(tokenMock, noPinTokenMock));

        assertTrue(tokenPinManager.tokenHasPin(TOKEN_EXTERNAL_ID));
        assertFalse(tokenPinManager.tokenHasPin("no-pin-token"));
    }

    private RuntimeTokenImpl tokenMock(String externalId) {
        RuntimeTokenImpl tokenMock = mock(RuntimeTokenImpl.class);
        when(tokenMock.id()).thenReturn(TOKEN_ID);
        when(tokenMock.externalId()).thenReturn(externalId);
        return tokenMock;
    }

    private void initRegistry(Set<RuntimeTokenImpl> tokens) {
        when(tokenRegistryLoader.loadTokens()).thenReturn(tokens);
        tokenRegistry.init();
    }

}
