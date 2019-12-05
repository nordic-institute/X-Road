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

import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.Key;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.KeyService;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

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
    private static final String GOOD_SIGN_KEY_ID = "sign-key-which-exists";
    private static final String GOOD_AUTH_KEY_ID = "auth-key-which-exists";

    @MockBean
    private KeyService keyService;

    @MockBean
    private GlobalConfService globalConfService;

    @Autowired
    private KeysApiController keysApiController;

    @Before
    public void setUp() throws Exception {
        KeyInfo signKeyInfo = TokenTestUtils.createTestKeyInfo(GOOD_SIGN_KEY_ID, KeyUsageInfo.SIGNING);
        KeyInfo authKeyInfo = TokenTestUtils.createTestKeyInfo(GOOD_AUTH_KEY_ID, KeyUsageInfo.AUTHENTICATION);
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String keyId = (String) args[0];
            if (keyId.equals(GOOD_AUTH_KEY_ID)) {
                return authKeyInfo;
            } else if (keyId.equals(GOOD_SIGN_KEY_ID)) {
                return signKeyInfo;
            } else {
                throw new KeyNotFoundException("foo");
            }
        }).when(keyService).getKey(any());
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("fi-not-auth-only", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        when(globalConfService.getApprovedCAsForThisInstance()).thenReturn(approvedCAInfos);
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
}
