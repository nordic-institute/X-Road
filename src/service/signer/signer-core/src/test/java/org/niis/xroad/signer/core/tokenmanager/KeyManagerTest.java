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

import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import org.junit.jupiter.api.Test;
import org.niis.xroad.signer.core.model.RuntimeKeyImpl;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.service.TokenKeyService;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class KeyManagerTest {

    private final TokenRegistryLoader tokenRegistryLoader = mock(TokenRegistryLoader.class);
    private final TokenKeyService tokenKeyService = mock(TokenKeyService.class);
    private final TokenRegistry tokenRegistry = new TokenRegistry(tokenRegistryLoader);

    private final KeyManager keyManager = new KeyManager(tokenRegistry, tokenKeyService);

    private static final long KEY_ID = 3L;
    private static final long TOKEN_ID = 7L;
    private static final String TOKEN_EXTERNAL_ID = "token-external-id";
    private static final String KEY_EXTERNAL_ID = "key-external-id";

    private final byte[] keystoreBytes = new byte[]{'k', 'e', 'y', 's', 't', 'o', 'r', 'e'};

    @Test
    void testAddKey() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.addKey(TOKEN_EXTERNAL_ID, KEY_EXTERNAL_ID, "publicKeyBase64", keystoreBytes, SignMechanism.CKM_RSA_PKCS,
                "friendlyName", "label");

        verify(tokenKeyService).save(TOKEN_ID, KEY_EXTERNAL_ID, "publicKeyBase64",
                keystoreBytes, SignMechanism.CKM_RSA_PKCS, "friendlyName", "label", true);

        verifyCacheRefresh();
    }

    @Test
    void testAddKeyNoKeyStore() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.addKey(TOKEN_EXTERNAL_ID, KEY_EXTERNAL_ID, "publicKeyBase64", SignMechanism.CKM_ECDSA,
                "friendlyName", "label");

        verify(tokenKeyService).save(TOKEN_ID, KEY_EXTERNAL_ID, "publicKeyBase64",
                null, SignMechanism.CKM_ECDSA, "friendlyName", "label", false);
        verifyCacheRefresh();
    }

    @Test
    void testSetKeyAvailable() {
        var keyMock = mock(RuntimeKeyImpl.class);
        initRegistry(keyMock);

        keyManager.setKeyAvailable(KEY_EXTERNAL_ID, true);

        verify(keyMock).setAvailable(true);
        verifyNoCacheRefresh();
    }

    @Test
    void testSetKeyFriendlyName() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.setKeyFriendlyName(KEY_EXTERNAL_ID, "newFriendlyName");

        verify(tokenKeyService).updateFriendlyName(KEY_ID, "newFriendlyName");
        verifyCacheRefresh();
    }

    @Test
    void testSetKeyLabel() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.setKeyLabel(KEY_EXTERNAL_ID, "new label");

        verify(tokenKeyService).updateLabel(KEY_ID, "new label");
        verifyCacheRefresh();
    }

    @Test
    void testSetKeyUsage() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.setKeyUsage(KEY_EXTERNAL_ID, KeyUsageInfo.AUTHENTICATION);

        verify(tokenKeyService).updateKeyUsage(KEY_ID, KeyUsageInfo.AUTHENTICATION);
        verifyCacheRefresh();
    }

    @Test
    void testRemoveKey() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.removeKey(KEY_EXTERNAL_ID);

        verify(tokenKeyService).delete(KEY_ID);
        verifyCacheRefresh();
    }

    @Test
    void testSetPublicKey() throws Exception {
        initRegistry(mock(RuntimeKeyImpl.class));

        keyManager.setPublicKey(KEY_EXTERNAL_ID, "newPublicKeyBase64");

        verify(tokenKeyService).updatePublicKey(KEY_ID, "newPublicKeyBase64");
        verifyCacheRefresh();
    }

    private void initRegistry(RuntimeKeyImpl keyMock) {
        RuntimeTokenImpl tokenMock = mock(RuntimeTokenImpl.class);
        when(tokenMock.id()).thenReturn(TOKEN_ID);
        when(tokenMock.externalId()).thenReturn(TOKEN_EXTERNAL_ID);
        when(keyMock.externalId()).thenReturn(KEY_EXTERNAL_ID);
        when(keyMock.id()).thenReturn(KEY_ID);
        when(tokenMock.keys()).thenReturn(Set.of(keyMock));
        when(tokenRegistryLoader.loadTokens()).thenReturn(Set.of(tokenMock));

        tokenRegistry.init();
    }

    private void verifyCacheRefresh() {
        verify(tokenRegistryLoader).refreshTokens(any());
    }

    private void verifyNoCacheRefresh() {
        verify(tokenRegistryLoader).loadTokens();
        verifyNoMoreInteractions(tokenRegistryLoader);
    }

}
