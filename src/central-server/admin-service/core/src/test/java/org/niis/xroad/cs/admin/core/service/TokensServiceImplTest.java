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

package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.TokenInfoProto;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.TokenInfo;
import org.niis.xroad.cs.admin.api.dto.TokenLoginRequest;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.ConfigurationSigningKeysService;
import org.niis.xroad.cs.admin.core.converter.TokenInfoMapper;
import org.niis.xroad.cs.admin.core.exception.SignerProxyException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.OK;
import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.USER_PIN_FINAL_TRY;
import static ee.ria.xroad.signer.protocol.dto.TokenStatusInfo.USER_PIN_LOCKED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.LOGIN;
import static org.niis.xroad.cs.admin.api.dto.PossibleTokenAction.LOGOUT;
import static org.niis.xroad.cs.admin.core.service.TokensServiceImpl.SOFTWARE_TOKEN_ID;

@ExtendWith(MockitoExtension.class)
class TokensServiceImplTest {

    private static final String TOKEN_ID = UUID.randomUUID().toString();
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String TOKEN_SERIAL_NUMBER = "serialNumber";
    private static final String TOKEN_FRIENDLY_NAME = "friendlyName";

    @Mock
    private ConfigurationSigningKeysService configurationSigningKeysService;
    @Mock
    private AuditDataHelper auditDataHelper;
    @Mock
    private TokenActionsResolverImpl tokenActionsResolver;
    @Mock
    private SignerProxyFacade signerProxyFacade;
    @Mock
    private TokenInfoMapper tokenInfoMapper;
    @Mock
    private TokenInfo tokenInfo;

    @InjectMocks
    private TokensServiceImpl tokensService;

    @Test
    void getTokens() throws Exception {
        ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo(OK);
        when(signerProxyFacade.getTokens()).thenReturn(List.of(signerTokenInfo));
        when(tokenInfoMapper.toTarget(signerTokenInfo)).thenReturn(tokenInfo);

        Set<TokenInfo> result = tokensService.getTokens();

        assertThat(result).hasSize(1);
        assertThat(result.iterator().next()).isEqualTo(tokenInfo);
    }

    @Test
    void getTokensShouldThrowException() throws Exception {
        doThrow(new Exception()).when(signerProxyFacade).getTokens();

        assertThatThrownBy(() -> tokensService.getTokens())
                .isInstanceOf(ServiceException.class)
                .hasMessage("Error getting tokens");
    }

    @Test
    void loginShouldThrowWhenTokenNotFound() throws Exception {
        when(signerProxyFacade.getToken(TOKEN_ID)).thenThrow(new CodedException("Signer.TokenNotFound"));

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Token not found");
    }

    @Test
    void loginShouldThrowPinLocked() throws Exception {
        final ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo(USER_PIN_LOCKED);

        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(signerTokenInfo);

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Token PIN locked");

        assertAuditMessages();
    }

    @Test
    void loginShouldThrowFinalTryException() throws Exception {
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(mockTokenInfo(OK), mockTokenInfo(USER_PIN_FINAL_TRY));
        doThrow(new CodedException("")).when(signerProxyFacade).activateToken(TOKEN_ID, PASSWORD.toCharArray());

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Tries left: 1");
        assertAuditMessages();
        verify(signerProxyFacade, times(2)).getToken(TOKEN_ID);
    }

    @Test
    void loginShouldThrowUserPinLockedOnActivation() throws Exception {
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(mockTokenInfo(OK), mockTokenInfo(USER_PIN_LOCKED));
        doThrow(new CodedException("")).when(signerProxyFacade).activateToken(TOKEN_ID, PASSWORD.toCharArray());

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Token PIN locked");
        assertAuditMessages();
        verify(signerProxyFacade, times(2)).getToken(TOKEN_ID);
    }

    @Test
    void loginShouldThrowOtherException() throws Exception {
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(mockTokenInfo(OK));
        doThrow(new Exception()).when(signerProxyFacade).activateToken(TOKEN_ID, PASSWORD.toCharArray());

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(SignerProxyException.class)
                .hasMessage("Token activation failed");
        assertAuditMessages();
    }

    @Test
    void loginShouldThrowIncorrectPinFormatWhenTooShort() throws Exception {
        Map<String, String> tokenParams = Map.of("Min PIN length", "42");
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(mockTokenInfo(tokenParams));

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Incorrect PIN format");

        assertAuditMessages();
    }

    @Test
    void loginShouldThrowIncorrectPinFormatWhenTooLong() throws Exception {
        Map<String, String> tokenParams = Map.of("Max PIN length", "7");
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(mockTokenInfo(tokenParams));

        assertThatThrownBy(() -> tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD)))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessage("Incorrect PIN format");

        assertAuditMessages();
    }

    @Test
    void login() throws Exception {
        final ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo(OK);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(signerTokenInfo);
        when(tokenInfoMapper.toTarget(signerTokenInfo)).thenReturn(tokenInfo);

        final TokenInfo result = tokensService.login(new TokenLoginRequest(TOKEN_ID, PASSWORD));

        assertThat(result).isEqualTo(tokenInfo);
        verify(signerProxyFacade, times(2)).getToken(TOKEN_ID);
        verify(signerProxyFacade).activateToken(TOKEN_ID, PASSWORD.toCharArray());
        verify(tokenActionsResolver).requireAction(LOGIN, signerTokenInfo, List.of());

        assertAuditMessages();
    }

    @Test
    void logout() throws Exception {
        final ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo(OK);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(signerTokenInfo);
        when(tokenInfoMapper.toTarget(signerTokenInfo)).thenReturn(tokenInfo);

        final TokenInfo result = tokensService.logout(TOKEN_ID);

        assertThat(result).isEqualTo(tokenInfo);
        verify(signerProxyFacade, times(2)).getToken(TOKEN_ID);
        verify(signerProxyFacade).deactivateToken(TOKEN_ID);
        verify(tokenActionsResolver).requireAction(LOGOUT, signerTokenInfo, List.of());

        assertAuditMessages();
    }

    @Test
    void logoutShouldThrowNotFound() throws Exception {
        when(signerProxyFacade.getToken(TOKEN_ID)).thenThrow(new CodedException("Signer.TokenNotFound"));

        assertThatThrownBy(() -> tokensService.logout(TOKEN_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Token not found");
    }

    @Test
    void logoutShouldThrowOtherExceptionWhenGetTokenFails() throws Exception {
        when(signerProxyFacade.getToken(TOKEN_ID)).thenThrow(new Exception());

        assertThatThrownBy(() -> tokensService.logout(TOKEN_ID))
                .isInstanceOf(SignerProxyException.class)
                .hasMessage("Signer proxy exception");
    }

    @Test
    void logoutShouldThrowExceptionWhenDeactivateFails() throws Exception {
        final ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo(OK);
        when(signerProxyFacade.getToken(TOKEN_ID)).thenReturn(signerTokenInfo);
        doThrow(new RuntimeException()).when(signerProxyFacade).deactivateToken(TOKEN_ID);

        assertThatThrownBy(() -> tokensService.logout(TOKEN_ID))
                .isInstanceOf(SignerProxyException.class)
                .hasMessage("Token deactivation failed");
        assertAuditMessages();
    }

    @Test
    void hasHardwareTokensReturnsTrue() throws Exception {
        ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo("1");
        when(signerProxyFacade.getTokens()).thenReturn(List.of(signerTokenInfo));

        boolean result = tokensService.hasHardwareTokens();

        assertThat(result).isTrue();
    }

    @Test
    void hasHardwareTokensReturnsFalse() throws Exception {
        ee.ria.xroad.signer.protocol.dto.TokenInfo signerTokenInfo = mockTokenInfo(SOFTWARE_TOKEN_ID);
        when(signerProxyFacade.getTokens()).thenReturn(List.of(signerTokenInfo));

        boolean result = tokensService.hasHardwareTokens();

        assertThat(result).isFalse();
    }

    private ee.ria.xroad.signer.protocol.dto.TokenInfo mockTokenInfo(String tokenId,
                                                                     TokenStatusInfo status,
                                                                     Map<String, String> tokenParams) {
        return new ee.ria.xroad.signer.protocol.dto.TokenInfo(TokenInfoProto.newBuilder()
                .setType("type")
                .setFriendlyName(TOKEN_FRIENDLY_NAME)
                .setId(tokenId)
                .setReadOnly(false)
                .setAvailable(true)
                .setActive(false)
                .setSerialNumber(TOKEN_SERIAL_NUMBER)
                .setLabel("label")
                .setSlotIndex(13)
                .setStatus(status)
                .putAllTokenInfo(tokenParams)
                .build());
    }

    private ee.ria.xroad.signer.protocol.dto.TokenInfo mockTokenInfo(TokenStatusInfo status) {
        return mockTokenInfo(TOKEN_ID, status, new HashMap<>());
    }

    private ee.ria.xroad.signer.protocol.dto.TokenInfo mockTokenInfo(Map<String, String> tokenParams) {
        return mockTokenInfo(TOKEN_ID, OK, tokenParams);
    }

    private ee.ria.xroad.signer.protocol.dto.TokenInfo mockTokenInfo(String tokenId) {
        return mockTokenInfo(tokenId, OK, new HashMap<>());
    }

    private void assertAuditMessages() {
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_ID, TOKEN_ID);
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_SERIAL_NUMBER, TOKEN_SERIAL_NUMBER);
        verify(auditDataHelper).put(RestApiAuditProperty.TOKEN_FRIENDLY_NAME, TOKEN_FRIENDLY_NAME);
    }
}
