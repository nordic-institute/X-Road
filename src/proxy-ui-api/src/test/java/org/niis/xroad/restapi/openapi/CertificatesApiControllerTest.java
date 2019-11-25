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

import ee.ria.xroad.common.CodedException;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.KeyUsage;
import org.niis.xroad.restapi.service.CertificateAlreadyExistsException;
import org.niis.xroad.restapi.service.ClientNotFoundException;
import org.niis.xroad.restapi.service.KeyNotFoundException;
import org.niis.xroad.restapi.service.TokenCertificateService;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;

import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * test certificates api
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class CertificatesApiControllerTest {

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @Autowired
    private CertificatesApiController certificatesApiController;

    @Before
    public void setup() throws Exception {
        doAnswer(answer -> "key-id").when(signerProxyFacade).importCert(any(), any(), any());
        doAnswer(answer -> null).when(globalConfFacade).verifyValidity();
        doAnswer(answer -> TestUtils.INSTANCE_FI).when(globalConfFacade).getInstanceIdentifier();
        doAnswer(answer -> TestUtils.getM1Ss1ClientId()).when(globalConfFacade).getSubjectName(any(), any());
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addSignCertificate() {
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        ResponseEntity<CertificateDetails> response = certificatesApiController.addCertificate(body);
        CertificateDetails addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertCertificateDetails(addedCert);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void addAuthCertificate() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockCertificate();
        CertificateTestUtils.setAsAuthCert(mockAuthCert);
        Resource body = CertificateTestUtils.getResource(mockAuthCert.getEncoded());
        ResponseEntity<CertificateDetails> response = certificatesApiController.addCertificate(body);
        CertificateDetails addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertCertificateDetails(addedCert);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addSignCertificateMissingClient() throws Exception {
        doAnswer(answer -> TestUtils.getClientId(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO,
                TestUtils.MEMBER_CODE_M2, TestUtils.SUBSYSTEM3))
                .when(globalConfFacade).getSubjectName(any(), any());
        X509Certificate mockCert = CertificateTestUtils.getMockCertificate();
        Resource body = CertificateTestUtils.getResource(mockCert.getEncoded());
        try {
            certificatesApiController.addCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(ClientNotFoundException.ERROR_CLIENT_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addExistingSignCertificate() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_CERT_EXISTS, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            certificatesApiController.addCertificate(body);
        } catch (ConflictException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(CertificateAlreadyExistsException.ERROR_CERTIFICATE_ALREADY_EXISTS, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addIncorrectSignCertificate() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_INCORRECT_CERTIFICATE, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            certificatesApiController.addCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(TokenCertificateService.InvalidCertificateException.INVALID_CERT, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addWrongUsageSignCertificate() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_WRONG_CERT_USAGE, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            certificatesApiController.addCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(TokenCertificateService.WrongCertificateUsageException.ERROR_CERTIFICATE_WRONG_USAGE,
                    error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addSignCertificateCsrMissing() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_CSR_NOT_FOUND, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            certificatesApiController.addCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(TokenCertificateService.CsrNotFoundException.ERROR_CSR_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addSignCertificateKeyNotFound() throws Exception {
        doThrow(CodedException
                .tr(SIGNER_X + "." + X_KEY_NOT_FOUND, "mock code", "mock msg"))
                .when(signerProxyFacade).importCert(any(), any(), any());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        try {
            certificatesApiController.addCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(KeyNotFoundException.ERROR_KEY_NOT_FOUND, error.getCode());
        }
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void addInvalidSignCertificate() throws Exception {
        Resource body = CertificateTestUtils.getResource(new byte[] {0, 0, 0, 0});
        try {
            certificatesApiController.addCertificate(body);
        } catch (BadRequestException e) {
            ErrorDeviation error = e.getErrorDeviation();
            assertEquals(TokenCertificateService.InvalidCertificateException.INVALID_CERT, error.getCode());
        }
    }

    private static void assertCertificateDetails(CertificateDetails certificateDetails) {
        assertEquals("N/A", certificateDetails.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"),
                certificateDetails.getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"),
                certificateDetails.getNotAfter());
        assertEquals("1", certificateDetails.getSerial());
        assertEquals(new Integer(3), certificateDetails.getVersion());
        assertEquals("SHA512withRSA", certificateDetails.getSignatureAlgorithm());
        assertEquals("RSA", certificateDetails.getPublicKeyAlgorithm());
        assertEquals("A2293825AA82A5429EC32803847E2152A303969C", certificateDetails.getHash());
        assertTrue(certificateDetails.getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(certificateDetails.getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(new Integer(65537), certificateDetails.getRsaPublicKeyExponent());
        assertEquals(new ArrayList<>(Collections.singletonList(KeyUsage.NON_REPUDIATION)),
                new ArrayList<>(certificateDetails.getKeyUsages()));
    }
}
