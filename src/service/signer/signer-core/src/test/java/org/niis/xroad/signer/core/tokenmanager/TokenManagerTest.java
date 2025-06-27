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

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import org.junit.jupiter.api.Test;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.model.RuntimeTokenImpl;
import org.niis.xroad.signer.core.service.TokenWriteService;
import org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenDefinition;
import org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenDefinition;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TokenManagerTest {

    private static final String TOKEN_EXTERNAL_ID = "0";

    private final TokenWriteService tokenWriteService = mock(TokenWriteService.class);
    private final TokenLookup tokenLookup = mock(TokenLookup.class);
    private final TokenRegistryLoader tokenRegistryLoader = mock(TokenRegistryLoader.class);
    private final TokenRegistry tokenRegistry = new TokenRegistry(tokenRegistryLoader);

    private final TokenManager tokenManager = new TokenManager(tokenRegistry, tokenWriteService, tokenLookup);

    @Test
    void testCreateTokenSoftToken() throws Exception {
        var softwareTokenDefinition = new SoftwareTokenDefinition(Map.of(
                KeyAlgorithm.RSA, SignMechanism.CKM_RSA_PKCS));
        var mockTokenInfo = mock(TokenInfo.class);
        when(tokenLookup.getTokenInfo(TOKEN_EXTERNAL_ID)).thenReturn(mockTokenInfo);

        tokenManager.createToken(softwareTokenDefinition);

        verify(tokenWriteService).save(TOKEN_EXTERNAL_ID, "softToken", "softToken-0", null, null);
        verify(tokenLookup).getTokenInfo(TOKEN_EXTERNAL_ID);
        verifyCacheRefresh();
    }

    @Test
    void testCreateTokenHwToken() throws Exception {
        var hwTokenDefinitionMock = mock(HardwareTokenDefinition.class);
        when(hwTokenDefinitionMock.getId()).thenReturn("hkwId");
        when(hwTokenDefinitionMock.moduleType()).thenReturn("hwModuleType");
        when(hwTokenDefinitionMock.slotIndex()).thenReturn(2);
        when(hwTokenDefinitionMock.label()).thenReturn("hwLabel");
        when(hwTokenDefinitionMock.serialNumber()).thenReturn("hwSerialNumber");

        tokenManager.createToken(hwTokenDefinitionMock);

        verify(tokenWriteService).save("hkwId", "hwModuleType", "hwModuleType-hwSerialNumber-hwLabel-2", "hwLabel", "hwSerialNumber");
        verify(tokenLookup).getTokenInfo("hkwId");
        verifyCacheRefresh();
    }

    @Test
    void testEnableToken() {
        RuntimeTokenImpl tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        var tokenDefinition = new SoftwareTokenDefinition(Map.of(
                KeyAlgorithm.RSA, SignMechanism.CKM_RSA_PKCS));

        tokenManager.enableToken(tokenDefinition);

        verify(tokenMock).setTokenDefinition(tokenDefinition);
        verifyNoCacheRefresh();
    }

    @Test
    void testDisableToken() {
        var tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        tokenManager.disableToken(TOKEN_EXTERNAL_ID);

        verify(tokenMock).setTokenDefinition(null);
        verifyNoCacheRefresh();
    }

    @Test
    void testSetTokenActive() {
        var tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        tokenManager.setTokenActive(TOKEN_EXTERNAL_ID, true);

        verify(tokenMock).setActive(true);
        verifyNoCacheRefresh();
    }

    @Test
    void testSetTokenFriendlyName() throws Exception {
        var tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        tokenManager.setTokenFriendlyName(TOKEN_EXTERNAL_ID, "newFriendlyName");

        verify(tokenWriteService).updateFriendlyName(1L, "newFriendlyName");
        verifyCacheRefresh();
    }

    @Test
    void testSetTokenStatus() {
        var tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        tokenManager.setTokenStatus(TOKEN_EXTERNAL_ID, TokenStatusInfo.USER_PIN_LOCKED);

        verify(tokenMock).setStatus(TokenStatusInfo.USER_PIN_LOCKED);
        verifyNoCacheRefresh();
    }

    @Test
    void testDeleteToken() throws Exception {
        var tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        tokenManager.deleteToken(TOKEN_EXTERNAL_ID);

        verify(tokenWriteService).delete(1L);
        verifyCacheRefresh();
    }

    @Test
    void testSetTokenIfo() {
        var tokenMock = mock(RuntimeTokenImpl.class);
        initRegistry(tokenMock);

        Map<String, String> tokenInfo = new HashMap<>();

        tokenManager.setTokenInfo(TOKEN_EXTERNAL_ID, tokenInfo);

        verify(tokenMock).setInfo(tokenInfo);
        verifyNoCacheRefresh();
    }

    private void initRegistry(RuntimeTokenImpl tokenMock) {
        when(tokenMock.id()).thenReturn(1L);
        when(tokenMock.externalId()).thenReturn(TOKEN_EXTERNAL_ID);
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
