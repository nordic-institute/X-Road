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
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils.KeyInfoBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * test key service.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class KeyServiceTest {

    // token ids for mocking
    private static final String GOOD_KEY_ID = "key-which-exists";
    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";

    @Autowired
    private KeyService keyService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private TokenService tokenService;

    @Before
    public void setup() throws Exception {
        TokenInfo tokenInfo = TokenTestUtils.createTestTokenInfo("good-token");

        KeyInfo mockKey = new KeyInfoBuilder().id(GOOD_KEY_ID).build();
        tokenInfo.getKeyInfo().add(mockKey);
        when(tokenService.getAllTokens()).thenReturn(Collections.singletonList(tokenInfo));

        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            String newKeyName = (String) arguments[1];
            if ("new-friendly-name-update-fails".equals(newKeyName)) {
                throw new CodedException(SIGNER_X + "." + X_KEY_NOT_FOUND);
            }
            ReflectionTestUtils.setField(mockKey, "friendlyName", newKeyName);
            return null;
        }).when(signerProxyFacade).setKeyFriendlyName(any(), any());
    }

    @Test
    public void getKey() throws Exception {
        try {
            keyService.getKey(KEY_NOT_FOUND_KEY_ID);
        } catch (KeyNotFoundException expected) {
        }
        KeyInfo keyInfo = keyService.getKey(GOOD_KEY_ID);
        assertEquals(GOOD_KEY_ID, keyInfo.getId());
    }

    @Test
    public void updateKeyFriendlyName() throws Exception {
        KeyInfo keyInfo = keyService.getKey(GOOD_KEY_ID);
        assertEquals("friendly-name", keyInfo.getFriendlyName());
        keyInfo = keyService.updateKeyFriendlyName(GOOD_KEY_ID, "new-friendly-name");
        assertEquals("new-friendly-name", keyInfo.getFriendlyName());
    }

    @Test(expected = KeyNotFoundException.class)
    public void updateKeyFriendlyNameKeyNotExist() throws Exception {
        keyService.updateKeyFriendlyName(KEY_NOT_FOUND_KEY_ID, "new-friendly-name");
    }

    @Test(expected = KeyNotFoundException.class)
    public void updateFriendlyNameUpdatingKeyFails() throws Exception {
        keyService.updateKeyFriendlyName(GOOD_KEY_ID, "new-friendly-name-update-fails");
    }

}
