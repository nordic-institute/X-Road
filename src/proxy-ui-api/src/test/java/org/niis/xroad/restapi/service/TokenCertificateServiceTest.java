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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.service.TokenCertificateService.CsrNotFoundException;
import org.niis.xroad.restapi.util.CertificateTestUtils.CertificateInfoBuilder;
import org.niis.xroad.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_AVAILABLE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * test TokenCertificateService.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class TokenCertificateServiceTest {

    // hashes and ids for mocking
    private static final String EXISTING_CERT_HASH = "ok-cert";
    private static final String EXISTING_CERT_IN_AUTH_KEY_HASH = "ok-cert-auth";
    private static final String EXISTING_CERT_IN_SIGN_KEY_HASH = "ok-cert-sign";
    private static final String GOOD_KEY_ID = "key-which-exists";
    private static final String KEY_NOT_FOUND_KEY_ID = "key-404";
    private static final String AUTH_KEY_ID = "auth-key";
    private static final String SIGN_KEY_ID = "sign-key";
    private static final String GOOD_CSR_ID = "csr-which-exists";
    private static final String CSR_NOT_FOUND_CSR_ID = "csr-404";
    private static final String SIGNER_EXCEPTION_CSR_ID = "signer-ex-csr";

    // for this signerProxy.getCertForHash throws not found
    private static final String NOT_FOUND_CERT_HASH = "not-found-cert";
    // for these signerProxy.deleteCert throws different exceptions
    private static final String SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH = "signer-id-not-found-cert";
    private static final String SIGNER_EX_INTERNAL_ERROR_HASH = "signer-internal-error-cert";
    private static final String SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH = "signer-token-na-cert";
    private static final String SIGNER_EX_TOKEN_READONLY_HASH = "signer-token-readonly-cert";

    @Autowired
    private TokenCertificateService tokenCertificateService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private KeyService keyService;

    @MockBean
    private GlobalConfService globalConfService;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private ClientRepository clientRepository;

    @Before
    public void setUp() throws Exception {
        // need lots of mocking
        // construct some test keys, with csrs and certs
        // make used finders return data from these items:
        // keyService.getKey, signerProxyFacade.getKeyIdForCertHash,
        // signerProxyFacade.getCertForHash
        // mock delete-operations (deleteCertificate, deleteCsr)
        CertRequestInfo goodCsr = new CertRequestInfo(GOOD_CSR_ID, null, null);
        CertRequestInfo signerExceptionCsr = new CertRequestInfo(
                SIGNER_EXCEPTION_CSR_ID, null, null);
        CertificateInfo authCert = new CertificateInfoBuilder().id(EXISTING_CERT_IN_AUTH_KEY_HASH).build();
        CertificateInfo signCert = new CertificateInfoBuilder().id(EXISTING_CERT_IN_SIGN_KEY_HASH).build();
        KeyInfo authKey = new TokenTestUtils.KeyInfoBuilder().id(AUTH_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .csr(goodCsr)
                .cert(authCert)
                .build();
        KeyInfo goodKey = new TokenTestUtils.KeyInfoBuilder()
                .id(GOOD_KEY_ID)
                .csr(goodCsr)
                .csr(signerExceptionCsr)
                .build();
        KeyInfo signKey = new TokenTestUtils.KeyInfoBuilder()
                .id(SIGN_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.SIGNING)
                .csr(goodCsr)
                .cert(signCert)
                .build();

        // keyService.getKey(keyId)
        doAnswer(invocation -> {
            String keyId = (String) invocation.getArguments()[0];
            switch (keyId) {
                case AUTH_KEY_ID:
                    return authKey;
                case SIGN_KEY_ID:
                    return signKey;
                case GOOD_KEY_ID:
                    return goodKey;
                default:
                    throw new KeyNotFoundException("unknown keyId: " + keyId);
            }
        }).when(keyService).getKey(any());

        // signerProxyFacade.getKeyIdForCertHash(hash)
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            if (certHash.equals(EXISTING_CERT_IN_AUTH_KEY_HASH)) {
                return AUTH_KEY_ID;
            } else {
                return SIGN_KEY_ID;
            }
        }).when(signerProxyFacade).getKeyIdForCertHash(any());

        // signerProxyFacade.getCertForHash(hash)
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            switch (certHash) {
                case NOT_FOUND_CERT_HASH:
                    throw signerException(X_CERT_NOT_FOUND);
                case EXISTING_CERT_HASH:
                case EXISTING_CERT_IN_AUTH_KEY_HASH:
                case EXISTING_CERT_IN_SIGN_KEY_HASH:
                case SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH:
                case SIGNER_EX_INTERNAL_ERROR_HASH:
                case SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH:
                case SIGNER_EX_TOKEN_READONLY_HASH:
                    // cert will have same id as hash
                    return new CertificateInfoBuilder().id(certHash).build();
                default:
                    throw new RuntimeException("bad switch option: " + certHash);
            }
        }).when(signerProxyFacade).getCertForHash(any());

        // attempts to delete either succeed or throw specific exceptions
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            switch (certHash) {
                case EXISTING_CERT_HASH:
                    return null;
                case SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH:
                    throw signerException(X_CERT_NOT_FOUND);
                case SIGNER_EX_INTERNAL_ERROR_HASH:
                    throw signerException(X_INTERNAL_ERROR);
                case SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH:
                    throw signerException(X_TOKEN_NOT_AVAILABLE);
                case SIGNER_EX_TOKEN_READONLY_HASH:
                    throw signerException(X_TOKEN_READONLY);
                default:
                    throw new RuntimeException("bad switch option: " + certHash);
            }
        }).when(signerProxyFacade).deleteCert(any());

        // signerProxyFacade.deleteCertRequest(id)
        doAnswer(invocation -> {
            String csrId = (String) invocation.getArguments()[0];
            if (GOOD_CSR_ID.equals(csrId)) {
                return null;
            } else if (SIGNER_EXCEPTION_CSR_ID.equals(csrId)) {
                throw CodedException.tr(X_CSR_NOT_FOUND,
                        "csr_not_found", "Certificate request '%s' not found", csrId)
                        .withPrefix(SIGNER_X);
            } else if (CSR_NOT_FOUND_CSR_ID.equals(csrId)) {
                throw new CsrNotFoundException("not found");
            } else {
                throw new CsrNotFoundException("not found");
            }
        }).when(signerProxyFacade).deleteCertRequest(any());
    }

    private CodedException signerException(String code) {
        return CodedException.tr(code, "mock-translation", "mock-message")
                .withPrefix(SIGNER_X);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateSuccessfully() throws Exception {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_HASH);
        verify(signerProxyFacade, times(1)).deleteCert(EXISTING_CERT_HASH);

    }

    @Test(expected = CertificateNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateHashNotFound() throws Exception {
        tokenCertificateService.deleteCertificate(NOT_FOUND_CERT_HASH);
    }

    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateSignerIdNotFound() throws Exception {
        try {
            tokenCertificateService.deleteCertificate(SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH);
            fail("should have thrown exception");
        } catch (CertificateNotFoundException expected) {
            ErrorDeviation errorDeviation = expected.getErrorDeviation();
            assertEquals(CertificateNotFoundException.ERROR_CERTIFICATE_NOT_FOUND_WITH_ID, errorDeviation.getCode());
            assertEquals(1, errorDeviation.getMetadata().size());
            assertEquals(SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH, errorDeviation.getMetadata().iterator().next());
        }
    }

    @Test(expected = TokenCertificateService.SignerOperationFailedException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateSignerInternalError() throws Exception {
        tokenCertificateService.deleteCertificate(SIGNER_EX_INTERNAL_ERROR_HASH);
    }

    @Test(expected = TokenCertificateService.KeyNotOperationalException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateSignerTokenNotAvailable() throws Exception {
        tokenCertificateService.deleteCertificate(SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH);
    }

    @Test(expected = TokenCertificateService.KeyNotOperationalException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCertificateSignerTokenReadonly() throws Exception {
        tokenCertificateService.deleteCertificate(SIGNER_EX_TOKEN_READONLY_HASH);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT" })
    public void deleteAuthCertificateWithoutPermission() throws Exception {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "DELETE_AUTH_CERT" })
    public void deleteSignCertificateWithoutPermission() throws Exception {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_IN_SIGN_KEY_HASH);
    }


    @Test(expected = KeyNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsrKeyNotFound() throws Exception {
        tokenCertificateService.deleteCsr(KEY_NOT_FOUND_KEY_ID, GOOD_CSR_ID);
    }
    @Test(expected = CsrNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsrCsrNotFound() throws Exception {
        tokenCertificateService.deleteCsr(GOOD_KEY_ID, CSR_NOT_FOUND_CSR_ID);
    }
    @Test(expected = CsrNotFoundException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsrSignerExceptions() throws Exception {
        tokenCertificateService.deleteCsr(GOOD_KEY_ID, SIGNER_EXCEPTION_CSR_ID);
    }
    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "DELETE_SIGN_CERT" })
    public void deleteAuthCsrWithoutPermission() throws Exception {
        tokenCertificateService.deleteCsr(AUTH_KEY_ID, GOOD_CSR_ID);
    }
    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = { "DELETE_AUTH_CERT" })
    public void deleteSignCsrWithoutPermission() throws Exception {
        tokenCertificateService.deleteCsr(SIGN_KEY_ID, GOOD_CSR_ID);
    }
    @Test
    @WithMockUser(authorities = { "DELETE_SIGN_CERT", "DELETE_AUTH_CERT" })
    public void deleteCsr() throws Exception {
        // success
        tokenCertificateService.deleteCsr(GOOD_KEY_ID, GOOD_CSR_ID);
        verify(signerProxyFacade, times(1)).deleteCertRequest(GOOD_CSR_ID);
    }


}
