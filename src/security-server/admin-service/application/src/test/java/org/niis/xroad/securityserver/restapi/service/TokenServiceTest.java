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
package org.niis.xroad.securityserver.restapi.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.LOGIN_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_PIN_INCORRECT;

/**
 * test token service.
 */
@Slf4j
public class TokenServiceTest extends AbstractServiceTestContext {

    @Autowired
    TokenService tokenService;

    @Autowired
    PossibleActionsRuleEngine possibleActionsRuleEngine;

    @Autowired
    AuditDataHelper auditDataHelper;

    // token ids for mocking
    private static final String WRONG_SOFTTOKEN_PIN_TOKEN_ID = "wrong-soft-pin";
    private static final String WRONG_HSM_PIN_TOKEN_ID = "wrong-soft-pin";
    private static final String UNKNOWN_LOGIN_FAIL_TOKEN_ID = "unknown-login-fail";
    private static final String TOKEN_NOT_FOUND_TOKEN_ID = "token-404";
    private static final String UNRECOGNIZED_FAULT_CODE_TOKEN_ID = "unknown-faultcode";
    private static final String GOOD_KEY_ID = "key-which-exists";
    private static final String GOOD_TOKEN_NAME = "good-token";
    private static final String BAD_POLICY_PIN = "-";

    public static final String GOOD_TOKEN_ID = "token-which-exists";

    private TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
            .friendlyName(GOOD_TOKEN_NAME)
            .key(new TokenTestUtils.KeyInfoBuilder().id(GOOD_KEY_ID).build())
            .build();

    @Before
    public void setup() {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            switch (tokenId) {
                case WRONG_SOFTTOKEN_PIN_TOKEN_ID -> throw XrdRuntimeException.systemException(TOKEN_PIN_INCORRECT).build();
                case UNKNOWN_LOGIN_FAIL_TOKEN_ID -> throw XrdRuntimeException.systemException(LOGIN_FAILED)
                        .origin(ErrorOrigin.SIGNER)
                        .details("dont know what happened").build();
                case TOKEN_NOT_FOUND_TOKEN_ID -> throw XrdRuntimeException.systemException(TOKEN_NOT_FOUND)
                        .details("did not find it").build();
                case UNRECOGNIZED_FAULT_CODE_TOKEN_ID -> throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                        .origin(ErrorOrigin.SIGNER)
                        .details("bar").build();
                case null, default -> log.debug("activate successful");
            }
            return null;
        }).when(signerRpcClient).activateToken(any(), any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String oldPin = new String((char[]) args[1]);
            if (WRONG_SOFTTOKEN_PIN_TOKEN_ID.equals(oldPin)) {
                throw XrdRuntimeException.systemException(TOKEN_PIN_INCORRECT).build();
            } else {
                log.debug("activate successful");
            }
            return null;
        }).when(signerRpcClient).updateTokenPin(any(), any(), any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (TOKEN_NOT_FOUND_TOKEN_ID.equals(tokenId)) {
                throw XrdRuntimeException.systemException(TOKEN_NOT_FOUND).build();
            } else if (UNRECOGNIZED_FAULT_CODE_TOKEN_ID.equals(tokenId)) {
                throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                        .origin(ErrorOrigin.SIGNER)
                        .details("bar").build();
            } else {
                log.debug("deactivate successful");
            }
            return null;
        }).when(signerRpcClient).deactivateToken(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (TOKEN_NOT_FOUND_TOKEN_ID.equals(tokenId)) {
                throw XrdRuntimeException.systemException(TOKEN_NOT_FOUND).build();
            } else {
                return tokenInfo;
            }
        }).when(signerRpcClient).getToken(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String newTokenName = (String) args[1];

            tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                    .friendlyName(newTokenName)
                    .key(new TokenTestUtils.KeyInfoBuilder().id(GOOD_KEY_ID).build())
                    .build();

            return null;
        }).when(signerRpcClient).setTokenFriendlyName(any(), any());
        mockPossibleActionsRuleEngineAllowAll();
    }

    @Test
    public void activateToken() {
        char[] password = "foobar".toCharArray();
        tokenService.activateToken("token-should-be-activatable", password);

        assertThrows(TokenService.PinIncorrectException.class, () -> tokenService.activateToken(WRONG_SOFTTOKEN_PIN_TOKEN_ID, password));
        assertThrows(TokenService.PinIncorrectException.class, () -> tokenService.activateToken(WRONG_HSM_PIN_TOKEN_ID, password));

        try {
            tokenService.activateToken(UNKNOWN_LOGIN_FAIL_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (XrdRuntimeException expected) {
            Assert.assertTrue(expected.getCode().endsWith("." + ErrorCode.LOGIN_FAILED.code()));
            assertEquals("dont know what happened", expected.getFaultString());
        }

        assertThrows(TokenNotFoundException.class, () ->
                tokenService.activateToken(TOKEN_NOT_FOUND_TOKEN_ID, password)
        );

        try {
            tokenService.activateToken(UNRECOGNIZED_FAULT_CODE_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (XrdRuntimeException expected) {
            assertEquals("signer.internal_error", expected.getCode());
            assertEquals("bar", expected.getDetails());
        }
        tokenPinValidator.setTokenPinEnforced(false);
    }

    @Test
    public void deactivateToken() {
        tokenService.deactivateToken("token-should-be-deactivatable");

        assertThrows(TokenNotFoundException.class, () ->
                tokenService.deactivateToken(TOKEN_NOT_FOUND_TOKEN_ID)
        );

        try {
            tokenService.deactivateToken(UNRECOGNIZED_FAULT_CODE_TOKEN_ID);
            fail("should have thrown exception");
        } catch (XrdRuntimeException expected) {
            assertEquals("signer.internal_error", expected.getCode());
        }
    }

    @Test
    public void getToken() {

        assertThrows(TokenNotFoundException.class, () -> tokenService.getToken(TOKEN_NOT_FOUND_TOKEN_ID));

        TokenInfo token = tokenService.getToken(GOOD_TOKEN_ID);
        assertEquals(GOOD_TOKEN_NAME, token.getFriendlyName());
    }

    @Test
    public void updateTokenFriendlyName() {
        TokenInfo token = tokenService.getToken(GOOD_TOKEN_ID);
        assertEquals(GOOD_TOKEN_NAME, token.getFriendlyName());
        token = tokenService.updateTokenFriendlyName(GOOD_TOKEN_ID, "friendly-neighborhood");
        assertEquals("friendly-neighborhood", token.getFriendlyName());
    }

    @Test
    public void deleteToken() {
        TokenInfo token = tokenService.getToken(GOOD_TOKEN_ID);
        assertEquals(GOOD_TOKEN_NAME, token.getFriendlyName());
        tokenService.deleteToken(GOOD_TOKEN_ID);
    }

    @Test(expected = TokenNotFoundException.class)
    public void updateNonExistingTokenFriendlyName() {
        tokenService.updateTokenFriendlyName(TOKEN_NOT_FOUND_TOKEN_ID, "new-name");
    }

    public void deleteNonExistingToken() {
        assertThrows(NotFoundException.class, () -> tokenService.deleteToken(TOKEN_NOT_FOUND_TOKEN_ID));
    }

    @Test
    public void getUnknownSoftwareTokenInitStatus() {
        when(signerRpcClient.getTokens()).thenThrow(XrdRuntimeException.systemException(INTERNAL_ERROR).origin(ErrorOrigin.SIGNER).build());
        TokenInitStatusInfo tokenStatus = tokenService.getSoftwareTokenInitStatus();
        assertEquals(TokenInitStatusInfo.UNKNOWN, tokenStatus);
    }

    @Test
    public void updateTokenPinSuccess() {
        try {
            tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, "oldPin", "newPin");
        } catch (Exception e) {
            fail("should not throw exceptions");
        }
    }

    @Test(expected = TokenNotFoundException.class)
    public void updateTokenPinNotFound() {
        tokenService.updateSoftwareTokenPin(TOKEN_NOT_FOUND_TOKEN_ID, "oldPin", "newPin");
    }

    @Test(expected = TokenService.PinIncorrectException.class)
    public void updateTokenPinIncorrect() {
        tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, WRONG_SOFTTOKEN_PIN_TOKEN_ID, "newPin");
    }

    @Test(expected = InvalidCharactersException.class)
    public void updateTokenPinInvalidCharacters() {
        Mockito.doThrow(InvalidCharactersException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, "oldPin", BAD_POLICY_PIN);
    }

    @Test(expected = WeakPinException.class)
    public void updateTokenPinWeak() {
        Mockito.doThrow(WeakPinException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, "oldPin", BAD_POLICY_PIN);
    }

    private void mockServices(PossibleActionsRuleEngine possibleActionsRuleEngineParam) {
        // override instead of mocking for better performance
        tokenService = new TokenService(signerRpcClient, possibleActionsRuleEngineParam, auditDataHelper,
                tokenPinValidator);
    }

    private void mockPossibleActionsRuleEngineAllowAll() {
        possibleActionsRuleEngine = new PossibleActionsRuleEngine() {
            @Override
            public void requirePossibleTokenAction(PossibleActionEnum action, TokenInfo token) throws
                    ActionNotPossibleException {
                // noop
            }
        };
        mockServices(possibleActionsRuleEngine);
    }
}
