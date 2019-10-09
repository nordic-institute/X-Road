/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.CodedException;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.niis.xroad.restapi.service.TokenService.CKR_PIN_INCORRECT_MESSAGE;
import static org.niis.xroad.restapi.service.TokenService.LOGIN_FAILED_FAULT_CODE;
import static org.niis.xroad.restapi.service.TokenService.PIN_INCORRECT_FAULT_CODE;
import static org.niis.xroad.restapi.service.TokenService.TOKEN_NOT_FOUND_FAULT_CODE;

/**
 * test token service.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class TokenServiceTest {

    private static final String WRONG_SOFTTOKEN_PIN = "wrong-soft-pin";
    private static final String WRONG_HSM_PIN = "wrong-soft-pin";
    private static final String UNKNOWN_LOGIN_FAIL = "unknown-login-fail";
    private static final String TOKEN_NOT_FOUND = "token-404";
    private static final String UNRECOGNIZED_FAULT_CODE = "unknown-faultcode";

    // to do: test with powermock, compare

    @Autowired
    private TokenService tokenService;

    @MockBean
    private SignerProxyFacadeService signerProxyFacadeService;

    @Before
    public void setup() throws Exception {
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (WRONG_SOFTTOKEN_PIN.equals(tokenId)) {
                throw new CodedException(PIN_INCORRECT_FAULT_CODE);
            } else if (WRONG_HSM_PIN.equals(tokenId)) {
                throw new CodedException(LOGIN_FAILED_FAULT_CODE, CKR_PIN_INCORRECT_MESSAGE);
            } else if (UNKNOWN_LOGIN_FAIL.equals(tokenId)) {
                throw new CodedException(LOGIN_FAILED_FAULT_CODE, "dont know what happened");
            } else if (TOKEN_NOT_FOUND.equals(tokenId)) {
                throw new CodedException(TOKEN_NOT_FOUND_FAULT_CODE, "did not find it");
            } else if (UNRECOGNIZED_FAULT_CODE.equals(tokenId)) {
                throw new CodedException("foo", "bar");
            } else {
                log.debug("activate successful");
            }
            return null;
        }).when(signerProxyFacadeService).activateToken(any(), any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String tokenId = (String) args[0];
            if (TOKEN_NOT_FOUND.equals(tokenId)) {
                throw new CodedException(TOKEN_NOT_FOUND_FAULT_CODE, "did not find it");
            } else if (UNRECOGNIZED_FAULT_CODE.equals(tokenId)) {
                throw new CodedException("foo", "bar");
            } else {
                log.debug("deactivate successful");
            }
            return null;
        }).when(signerProxyFacadeService).deactivateToken(any());
    }

    @Test
    @WithMockUser(authorities = { "ACTIVATE_TOKEN" })
    public void activateToken() {
        char[] password = "foobar".toCharArray();
        tokenService.activateToken("token-should-be-activatable", password);

        try {
            tokenService.activateToken(WRONG_SOFTTOKEN_PIN, password);
            fail("should have thrown exception");
        } catch (TokenService.PinIncorrectException expected) {
        }

        try {
            tokenService.activateToken(WRONG_HSM_PIN, password);
            fail("should have thrown exception");
        } catch (TokenService.PinIncorrectException expected) {
        }

        try {
            tokenService.activateToken(UNKNOWN_LOGIN_FAIL, password);
            fail("should have thrown exception");
        } catch (TokenService.UnspecifiedCoreCodedException expected) {
            assertEquals("core." + LOGIN_FAILED_FAULT_CODE, expected.getError().getCode());
            assertEquals("dont know what happened", expected.getError().getMetadata().iterator().next());
        }

        try {
            tokenService.activateToken(TOKEN_NOT_FOUND, password);
            fail("should have thrown exception");
        } catch (TokenService.TokenNotFoundException expected) {
        }

        try {
            tokenService.activateToken(UNRECOGNIZED_FAULT_CODE, password);
            fail("should have thrown exception");
        } catch (TokenService.UnspecifiedCoreCodedException expected) {
            assertEquals("core.foo", expected.getError().getCode());
            assertEquals("bar", expected.getError().getMetadata().iterator().next());
        }

    }

    @Test
    @WithMockUser(authorities = { "DEACTIVATE_TOKEN" })
    public void deactiveToken() {
        tokenService.deactivateToken("token-should-be-deactivatable");

        try {
            tokenService.deactivateToken(TOKEN_NOT_FOUND);
            fail("should have thrown exception");
        } catch (TokenService.TokenNotFoundException expected) {
        }

        try {
            tokenService.deactivateToken(UNRECOGNIZED_FAULT_CODE);
            fail("should have thrown exception");
        } catch (TokenService.UnspecifiedCoreCodedException expected) {
            assertEquals("core.foo", expected.getError().getCode());
            assertEquals("bar", expected.getError().getMetadata().iterator().next());
        }
    }
}
