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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.Key;
import org.niis.xroad.restapi.service.CsrNotFoundException;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.KeyService;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * test keys api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class KeysApiControllerTest {

    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String GOOD_KEY_ID = "key-which-exists";
    private static final String GOOD_CSR_ID = "csr-which-exists";
    private static final String KEY_NOT_FOUND_CSR_ID = "csr-with-key-404";

    @MockBean
    private KeyService keyService;

    @MockBean
    private TokenCertificateService tokenCertificateService;

    @Autowired
    private KeysApiController keysApiController;

    @Before
    public void setUp() throws Exception {
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder().id(GOOD_KEY_ID).build();
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String keyId = (String) args[0];
            return returnKeyIfGoodId(keyInfo, keyId);
        }).when(keyService).getKey(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String csrId = (String) args[0];
            if (!GOOD_CSR_ID.equals(csrId)) {
                throw new CsrNotFoundException("bar");
            }
            return null;
        }).when(tokenCertificateService).deleteCsr(any());

    }

    private Object returnKeyIfGoodId(KeyInfo keyInfo, String keyId) throws KeyNotFoundException {
        if (!GOOD_KEY_ID.equals(keyId)) {
            throw new KeyNotFoundException("foo");
        } else {
            return keyInfo;
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

        ResponseEntity<Key> response = keysApiController.getKey(GOOD_KEY_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GOOD_KEY_ID, response.getBody().getId());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_AUTH_CERT" })
    public void deleteCsr() {
        try {
            // key id is not used
            keysApiController.deleteCsr(GOOD_KEY_ID, KEY_NOT_FOUND_CSR_ID);
            fail("should have thrown exception");
        } catch (ResourceNotFoundException expected) {
        }

        ResponseEntity<Void> response = keysApiController.deleteCsr(GOOD_KEY_ID, GOOD_CSR_ID);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
