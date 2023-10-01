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
package org.niis.xroad.securityserver.restapi.openapi;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.securityserver.restapi.openapi.model.Key;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleAction;
import org.niis.xroad.securityserver.restapi.service.CsrNotFoundException;
import org.niis.xroad.securityserver.restapi.service.KeyNotFoundException;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * test keys api
 */
public class KeysApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    KeysApiController keysApiController;

    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String GOOD_SIGN_KEY_ID = "sign-key-which-exists";
    private static final String GOOD_AUTH_KEY_ID = "auth-key-which-exists";
    private static final String GOOD_CSR_ID = "csr-which-exists";
    private static final String KEY_NOT_FOUND_CSR_ID = "csr-with-key-404";

    private KeyInfo signKeyInfo;
    private KeyInfo authKeyInfo;

    @Before
    public void setUp() throws Exception {
        signKeyInfo = new TokenTestUtils.KeyInfoBuilder().id(GOOD_SIGN_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING).build();
        authKeyInfo = new TokenTestUtils.KeyInfoBuilder().id(GOOD_AUTH_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build();
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String keyId = (String) args[0];
            return returnKeyInfoOrThrow(keyId);
        }).when(keyService).getKey(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String csrId = (String) args[0];
            if (!GOOD_CSR_ID.equals(csrId)) {
                throw new CsrNotFoundException("bar");
            }
            return null;
        }).when(tokenCertificateService).deleteCsr(any());

        // by default all actions are possible
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(tokenCertificateService)
                .getPossibleActionsForCsr(any());
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(keyService)
                .getPossibleActionsForKey(any());

    }

    private Object returnKeyInfoOrThrow(String keyId) throws KeyNotFoundException {
        if (keyId.equals(GOOD_AUTH_KEY_ID)) {
            return authKeyInfo;
        } else if (keyId.equals(GOOD_SIGN_KEY_ID)) {
            return signKeyInfo;
        } else {
            throw new KeyNotFoundException("foo");
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getKey() {
        try {
            keysApiController.getKey(KEY_NOT_FOUND_KEY_ID);
            fail("should have thrown exception");
        } catch (ResourceNotFoundException expected) {
        }

        ResponseEntity<Key> response = keysApiController.getKey(GOOD_SIGN_KEY_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GOOD_SIGN_KEY_ID, response.getBody().getId());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_CERT" })
    public void deleteCsr() {
        try {
            // key id is not used
            keysApiController.deleteCsr(GOOD_SIGN_KEY_ID, KEY_NOT_FOUND_CSR_ID);
            fail("should have thrown exception");
        } catch (ResourceNotFoundException expected) {
        }

        ResponseEntity<Void> response = keysApiController.deleteCsr(GOOD_SIGN_KEY_ID, GOOD_CSR_ID);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getPossibleActionsForCsr() {
        ResponseEntity<List<PossibleAction>> response = keysApiController
                .getPossibleActionsForCsr(GOOD_SIGN_KEY_ID, GOOD_CSR_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<PossibleAction> allActions = new HashSet(Arrays.asList(PossibleAction.values()));
        assertEquals(allActions, new HashSet<>(response.getBody()));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_KEYS" })
    public void getPossibleActionsForKey() {
        ResponseEntity<List<PossibleAction>> response = keysApiController
                .getPossibleActionsForKey(GOOD_SIGN_KEY_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<PossibleAction> allActions = new HashSet(Arrays.asList(PossibleAction.values()));
        assertEquals(allActions, new HashSet<>(response.getBody()));
    }

}
