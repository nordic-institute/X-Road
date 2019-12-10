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
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    private static final String AUTH_KEY_ID = "auth-key";
    private static final String SIGN_KEY_ID = "sign-key";
    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String GOOD_CSR_ID = "csr-which-exists";
    private static final String CSR_NOT_FOUND_CSR_ID = "csr-404";
    private static final String SIGNER_EXCEPTION_CSR_ID = "signer-ex-csr";

    @Autowired
    private KeyService keyService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    private Map<String, KeyInfo> keyNamesToKeys;

    @Before
    public void setup() throws Exception {
        keyNamesToKeys = new HashMap<>();
        TokenInfo tokenInfo = TokenTestUtils.createTestTokenInfo("good-token");
        CertRequestInfo goodCsr = new CertRequestInfo(GOOD_CSR_ID, null, null);
        CertRequestInfo signerExceptionCsr = new CertRequestInfo(
                SIGNER_EXCEPTION_CSR_ID, null, null);

        keyNamesToKeys.put(GOOD_KEY_ID, new TokenTestUtils.KeyInfoBuilder()
                .id(GOOD_KEY_ID)
                .csr(goodCsr)
                .csr(signerExceptionCsr)
                .build());
        keyNamesToKeys.put(AUTH_KEY_ID, new TokenTestUtils.KeyInfoBuilder().id(AUTH_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .csr(goodCsr)
                .build());
        keyNamesToKeys.put(SIGN_KEY_ID, new TokenTestUtils.KeyInfoBuilder().id(SIGN_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(goodCsr)
                .build());
        tokenInfo.getKeyInfo().addAll(keyNamesToKeys.values());
        when(signerProxyFacade.getTokens()).thenReturn(Collections.singletonList(tokenInfo));

        doAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            String newKeyName = (String) arguments[1];
            if ("new-friendly-name-update-fails".equals(newKeyName)) {
                throw new CodedException(SIGNER_X + "." + X_KEY_NOT_FOUND);
            }
            ReflectionTestUtils.setField(keyNamesToKeys.get(GOOD_KEY_ID), "friendlyName", newKeyName);
            return null;
        }).when(signerProxyFacade).setKeyFriendlyName(any(), any());

        doAnswer(invocation -> {
            String csrId = (String) invocation.getArguments()[0];
            if (GOOD_CSR_ID.equals(csrId)) {
                return null;
            } else if (SIGNER_EXCEPTION_CSR_ID.equals(csrId)) {
                throw CodedException.tr(X_CSR_NOT_FOUND,
                "csr_not_found", "Certificate request '%s' not found", csrId);
            } else if (CSR_NOT_FOUND_CSR_ID.equals(csrId)) {
                throw new KeyService.CsrNotFoundException("not found");
            } else {
                throw new KeyService.CsrNotFoundException("not found");
            }
        }).when(signerProxyFacade).deleteCertRequest(any());
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

    @Test(expected = KeyNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsrKeyNotFound() throws Exception {
        keyService.deleteCsr(KEY_NOT_FOUND_KEY_ID, GOOD_CSR_ID);
    }
    @Test(expected = KeyService.CsrNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsrCsrNotFound() throws Exception {
        keyService.deleteCsr(GOOD_KEY_ID, CSR_NOT_FOUND_CSR_ID);
    }
    @Test(expected = KeyService.CsrNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsrSignerExceptions() throws Exception {
        keyService.deleteCsr(GOOD_KEY_ID, SIGNER_EXCEPTION_CSR_ID);
    }
    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT" })
    public void deleteAuthCsrWithoutPermission() throws Exception {
        keyService.deleteCsr(AUTH_KEY_ID, GOOD_CSR_ID);
    }
    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "DELETE_AUTH_CERT" })
    public void deleteSignCsrWithoutPermission() throws Exception {
        keyService.deleteCsr(SIGN_KEY_ID, GOOD_CSR_ID);
    }
    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsr() throws Exception {
        // success
        keyService.deleteCsr(GOOD_KEY_ID, GOOD_CSR_ID);
        verify(signerProxyFacade, times(1)).deleteCertRequest(GOOD_CSR_ID);
    }
}
