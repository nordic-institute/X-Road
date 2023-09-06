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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
    public void setup() throws Exception {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (WRONG_SOFTTOKEN_PIN_TOKEN_ID.equals(tokenId)) {
                throw new CodedException(TokenService.PIN_INCORRECT_FAULT_CODE);
            } else if (WRONG_HSM_PIN_TOKEN_ID.equals(tokenId)) {
                throw new CodedException(TokenService.LOGIN_FAILED_FAULT_CODE, TokenService.CKR_PIN_INCORRECT_MESSAGE);
            } else if (UNKNOWN_LOGIN_FAIL_TOKEN_ID.equals(tokenId)) {
                throw new CodedException(TokenService.LOGIN_FAILED_FAULT_CODE, "dont know what happened");
            } else if (TOKEN_NOT_FOUND_TOKEN_ID.equals(tokenId)) {
                throw new CodedException(TokenService.TOKEN_NOT_FOUND_FAULT_CODE, "did not find it");
            } else if (UNRECOGNIZED_FAULT_CODE_TOKEN_ID.equals(tokenId)) {
                throw new CodedException("foo", "bar");
            } else {
                log.debug("activate successful");
            }
            return null;
        }).when(signerProxyFacade).activateToken(any(), any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String oldPin = new String((char[]) args[1]);
            String newPin = new String((char[]) args[2]);
            if (WRONG_SOFTTOKEN_PIN_TOKEN_ID.equals(oldPin)) {
                throw new CodedException(TokenService.PIN_INCORRECT_FAULT_CODE);
            } else {
                log.debug("activate successful");
            }
            return null;
        }).when(signerProxyFacade).updateSoftwareTokenPin(any(), any(), any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (TOKEN_NOT_FOUND_TOKEN_ID.equals(tokenId)) {
                throw new CodedException(TokenService.TOKEN_NOT_FOUND_FAULT_CODE, "did not find it");
            } else if (UNRECOGNIZED_FAULT_CODE_TOKEN_ID.equals(tokenId)) {
                throw new CodedException("foo", "bar");
            } else {
                log.debug("deactivate successful");
            }
            return null;
        }).when(signerProxyFacade).deactivateToken(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (TOKEN_NOT_FOUND_TOKEN_ID.equals(tokenId)) {
                throw new CodedException(TokenService.TOKEN_NOT_FOUND_FAULT_CODE, "did not find it");
            } else {
                return tokenInfo;
            }
        }).when(signerProxyFacade).getToken(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String newTokenName = (String) args[1];

            tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                    .friendlyName(newTokenName)
                    .key(new TokenTestUtils.KeyInfoBuilder().id(GOOD_KEY_ID).build())
                    .build();

            return null;
        }).when(signerProxyFacade).setTokenFriendlyName(any(), any());
        mockPossibleActionsRuleEngineAllowAll();
    }

    @Test
    public void activateToken() throws Exception {
        char[] password = "foobar".toCharArray();
        tokenService.activateToken("token-should-be-activatable", password);

        try {
            tokenService.activateToken(WRONG_SOFTTOKEN_PIN_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (TokenService.PinIncorrectException expected) {
        }

        try {
            tokenService.activateToken(WRONG_HSM_PIN_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (TokenService.PinIncorrectException expected) {
        }

        try {
            tokenService.activateToken(UNKNOWN_LOGIN_FAIL_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (CodedException expected) {
            Assert.assertEquals(TokenService.LOGIN_FAILED_FAULT_CODE, expected.getFaultCode());
            assertEquals("dont know what happened", expected.getFaultString());
        }

        try {
            tokenService.activateToken(TOKEN_NOT_FOUND_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (TokenNotFoundException expected) {
        }

        try {
            tokenService.activateToken(UNRECOGNIZED_FAULT_CODE_TOKEN_ID, password);
            fail("should have thrown exception");
        } catch (CodedException expected) {
            assertEquals("foo", expected.getFaultCode());
            assertEquals("bar", expected.getFaultString());
        }
        tokenPinValidator.setTokenPinEnforced(false);
    }

    @Test
    public void deactivateToken() throws Exception {
        tokenService.deactivateToken("token-should-be-deactivatable");

        try {
            tokenService.deactivateToken(TOKEN_NOT_FOUND_TOKEN_ID);
            fail("should have thrown exception");
        } catch (TokenNotFoundException expected) {
        }

        try {
            tokenService.deactivateToken(UNRECOGNIZED_FAULT_CODE_TOKEN_ID);
            fail("should have thrown exception");
        } catch (CodedException expected) {
            assertEquals("foo", expected.getFaultCode());
            assertEquals("bar", expected.getFaultString());
        }
    }

    @Test
    public void getToken() throws Exception {

        try {
            tokenService.getToken(TOKEN_NOT_FOUND_TOKEN_ID);
        } catch (TokenNotFoundException expected) {
        }

        TokenInfo token = tokenService.getToken(GOOD_TOKEN_ID);
        assertEquals(GOOD_TOKEN_NAME, token.getFriendlyName());
    }

    @Test
    public void updateTokenFriendlyName() throws Exception {
        TokenInfo token = tokenService.getToken(GOOD_TOKEN_ID);
        assertEquals(GOOD_TOKEN_NAME, token.getFriendlyName());
        token = tokenService.updateTokenFriendlyName(GOOD_TOKEN_ID, "friendly-neighborhood");
        assertEquals("friendly-neighborhood", token.getFriendlyName());
    }

    @Test(expected = TokenNotFoundException.class)
    public void updateNonExistingTokenFriendlyName() throws Exception {
        tokenService.updateTokenFriendlyName(TOKEN_NOT_FOUND_TOKEN_ID, "new-name");
    }

    @Test
    public void getUnknownSoftwareTokenInitStatus() throws Exception {
        when(signerProxyFacade.getTokens()).thenThrow(new Exception());
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
    public void updateTokenPinNotFound() throws Exception {
        tokenService.updateSoftwareTokenPin(TOKEN_NOT_FOUND_TOKEN_ID, "oldPin", "newPin");
    }

    @Test(expected = TokenService.PinIncorrectException.class)
    public void updateTokenPinIncorrect() throws Exception {
        tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, WRONG_SOFTTOKEN_PIN_TOKEN_ID, "newPin");
    }

    @Test(expected = InvalidCharactersException.class)
    public void updateTokenPinInvalidCharacters() throws Exception {
        Mockito.doThrow(InvalidCharactersException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, "oldPin", BAD_POLICY_PIN);
    }

    @Test(expected = WeakPinException.class)
    public void updateTokenPinWeak() throws Exception {
        Mockito.doThrow(WeakPinException.class).when(tokenPinValidator).validateSoftwareTokenPin(any());
        tokenService.updateSoftwareTokenPin(GOOD_TOKEN_ID, "oldPin", BAD_POLICY_PIN);
    }

    private void mockServices(PossibleActionsRuleEngine possibleActionsRuleEngineParam) {
        // override instead of mocking for better performance
        tokenService = new TokenService(signerProxyFacade, possibleActionsRuleEngineParam, auditDataHelper,
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
