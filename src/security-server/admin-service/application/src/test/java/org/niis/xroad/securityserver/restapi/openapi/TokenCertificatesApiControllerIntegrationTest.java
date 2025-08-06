/*
 * The MIT License
 *
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.securityserver.restapi.openapi.model.KeyUsageDto;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleActionDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificateDto;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.service.TokenCertificateService;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.CertificateInfoBuilder;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_EXISTS;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_WRONG_CERT_USAGE;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCodes.CLIENT_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCodes.INVALID_CERTIFICATE;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.MOCK_CERTIFICATE_HASH;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.getMockAuthCertificate;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.getMockCertificate;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.getMockCertificateWithoutExtensions;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.assertLocationHeader;

/**
 * test certificates api
 */
public class TokenCertificatesApiControllerIntegrationTest extends AbstractApiControllerTestContext {

    @Autowired
    TokenCertificatesApiController tokenCertificatesApiController;

    private static final String AUTH_CERT_HASH = "1A24F22E9BA8DDD86DAC48D854AD3C8701D28391DB705A7FAD1AB09BD1";
    private static final String SIGN_CERT_HASH = "1A24F22E9BA8DDD86DAC48D854AD3C8701D28391DB705A7FAD1AB09BD2";
    private static final String UNKNOWN_CERT_HASH = "1A24F22E9BA8DDD86DAC48D854AD3C8701D28391DB705A7FAD1AB09BD0";

    @Before
    public void setup() throws Exception {
        doAnswer(answer -> "key-id").when(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
        doAnswer(answer -> null).when(globalConfProvider).verifyValidity();
        doAnswer(answer -> TestUtils.INSTANCE_FI).when(globalConfProvider).getInstanceIdentifier();
        doAnswer(answer -> TestUtils.getM1Ss1ClientId()).when(globalConfProvider).getSubjectName(any(), any());
        when(globalConfProvider.getApprovedCA(any(), any()))
                .thenReturn(new ApprovedCAInfo("testca", false, "ee.test.Profile", null, null, null, null));
        CertificateInfo signCertificateInfo = new CertificateInfoBuilder().certificate(getMockCertificate())
                .certificateStatus("SAVED").build();
        CertificateInfo authCertificateInfo = new CertificateInfoBuilder().certificate(getMockAuthCertificate())
                .certificateStatus("SAVED").build();
        CertificateInfo unknownCertificateInfo = new CertificateInfoBuilder()
                .certificate(getMockCertificateWithoutExtensions())
                .certificateStatus("SAVED").build();
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String certId = (String) args[0];
            if (AUTH_CERT_HASH.equals(certId)) {
                return authCertificateInfo;
            } else if (UNKNOWN_CERT_HASH.equals(certId)) {
                return unknownCertificateInfo;
            } else {
                return signCertificateInfo;
            }
        }).when(signerRpcClient).getCertForHash(any());
        doAnswer(answer -> new SignerRpcClient.KeyIdInfo("key-id", null)).when(signerRpcClient).getKeyIdForCertHash(any());
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder().id("key-id").build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .key(keyInfo)
                .build();
        doAnswer(answer -> Collections.singletonList(tokenInfo)).when(signerRpcClient).getTokens();
        TokenInfoAndKeyId tokenInfoAndKeyId = new TokenInfoAndKeyId(tokenInfo, keyInfo.getId());
        doAnswer(answer -> tokenInfoAndKeyId).when(signerRpcClient).getTokenAndKeyIdForCertRequestId(any());
        doAnswer(answer -> tokenInfoAndKeyId).when(signerRpcClient).getTokenAndKeyIdForCertHash(any());
        // by default all actions are possible
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCertificateActions(any(), any(), any());
        when(serverConfProvider.getIdentifier())
                .thenReturn(SecurityServerId.Conf.create("EE", "ORG", "consumer", "server"));
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificate() {
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        ResponseEntity<TokenCertificateDto> response = tokenCertificatesApiController.importCertificate(body);
        TokenCertificateDto addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSignCertificateDetails(addedCert);
        assertLocationHeader("/api/token-certificates/" + addedCert.getCertificateDetails().getHash(),
                response);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importSignCertificateWithWrongPermission() {
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        tokenCertificatesApiController.importCertificate(body);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importAuthCertificate() throws Exception {
        X509Certificate mockAuthCert = getMockAuthCertificate();
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(mockAuthCert)
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .build();
        doAnswer(answer -> certificateInfo).when(signerRpcClient).getCertForHash(any());
        Resource body = CertificateTestUtils.getResource(mockAuthCert.getEncoded());
        ResponseEntity<TokenCertificateDto> response = tokenCertificatesApiController.importCertificate(body);
        TokenCertificateDto addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertAuthCertificateDetails(addedCert);
        assertLocationHeader("/api/token-certificates/" + addedCert.getCertificateDetails().getHash(),
                response);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importAuthCertificateWithWrongPermission() throws Exception {
        X509Certificate mockAuthCert = getMockAuthCertificate();
        Resource body = CertificateTestUtils.getResource(mockAuthCert.getEncoded());
        tokenCertificatesApiController.importCertificate(body);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificateMissingClient() throws Exception {
        ClientId notFoundId = TestUtils.getClientId(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO,
                TestUtils.MEMBER_CODE_M2, TestUtils.SUBSYSTEM3);
        doAnswer(answer -> notFoundId).when(globalConfProvider).getSubjectName(any(), any());
        X509Certificate mockCert = getMockCertificate();
        Resource body = CertificateTestUtils.getResource(mockCert.getEncoded());
        var e = assertThrows(BadRequestException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(CLIENT_NOT_FOUND.code(), error.code());
        assertEquals(notFoundId.asEncodedId(), error.metadata().get(0));
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importExistingSignCertificate() throws Exception {
        doThrow(SignerException
                .tr(X_CERT_EXISTS, "mock code", "mock msg"))
                .when(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        var e = assertThrows(ConflictException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_CERTIFICATE_ALREADY_EXISTS, error.code());
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importIncorrectSignCertificate() throws Exception {
        doThrow(SignerException
                .tr(X_INCORRECT_CERTIFICATE, "mock code", "mock msg"))
                .when(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        var e = assertThrows(BadRequestException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(INVALID_CERTIFICATE.code(), error.code());

    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importWrongUsageSignCertificate() throws Exception {
        doThrow(SignerException
                .tr(X_WRONG_CERT_USAGE, "mock code", "mock msg"))
                .when(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        var e = assertThrows(BadRequestException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_CERTIFICATE_WRONG_USAGE, error.code());
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificateCsrMissing() throws Exception {
        doThrow(SignerException
                .tr(X_CSR_NOT_FOUND, "mock code", "mock msg"))
                .when(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        var e = assertThrows(ConflictException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_CSR_NOT_FOUND, error.code());

    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importSignCertificateKeyNotFound() throws Exception {
        doThrow(SignerException
                .tr(X_KEY_NOT_FOUND, "mock code", "mock msg"))
                .when(signerRpcClient).importCert(any(), any(), any(), anyBoolean());
        Resource body = CertificateTestUtils.getResource(CertificateTestUtils.getMockCertificateBytes());
        var e = assertThrows(BadRequestException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_KEY_NOT_FOUND, error.code());

    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importInvalidSignCertificate() throws Exception {
        Resource body = CertificateTestUtils.getResource(new byte[]{0, 0, 0, 0});
        var e = assertThrows(BadRequestException.class, () -> tokenCertificatesApiController.importCertificate(body));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(INVALID_CERTIFICATE.code(), error.code());

    }

    @Test
    @WithMockUser(authorities = {"VIEW_AUTH_CERT", "VIEW_SIGN_CERT"})
    public void getCertificateForHash() throws Exception {
        ResponseEntity<TokenCertificateDto> response =
                tokenCertificatesApiController.getCertificate(MOCK_CERTIFICATE_HASH);
        TokenCertificateDto addedCert = response.getBody();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSignCertificateDetails(addedCert);
    }

    @Test
    @WithMockUser(authorities = "VIEW_AUTH_CERT")
    public void getCertificateForHashAuthPermissions() throws Exception {
        ResponseEntity<TokenCertificateDto> response =
                tokenCertificatesApiController.getCertificate(AUTH_CERT_HASH);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        try {
            tokenCertificatesApiController.getCertificate(UNKNOWN_CERT_HASH);
            fail("should have thrown AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
        try {
            tokenCertificatesApiController.getCertificate(SIGN_CERT_HASH);
            fail("should have thrown AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = "VIEW_SIGN_CERT")
    public void getCertificateForHashSignPermissions() throws Exception {
        ResponseEntity<TokenCertificateDto> response =
                tokenCertificatesApiController.getCertificate(SIGN_CERT_HASH);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        try {
            tokenCertificatesApiController.getCertificate(AUTH_CERT_HASH);
            fail("should have thrown AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
        try {
            tokenCertificatesApiController.getCertificate(UNKNOWN_CERT_HASH);
            fail("should have thrown AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = "VIEW_UNKNOWN_CERT")
    public void getCertificateForHashUnknownPermissions() throws Exception {
        ResponseEntity<TokenCertificateDto> response =
                tokenCertificatesApiController.getCertificate(UNKNOWN_CERT_HASH);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        try {
            tokenCertificatesApiController.getCertificate(AUTH_CERT_HASH);
            fail("should have thrown AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
        try {
            tokenCertificatesApiController.getCertificate(SIGN_CERT_HASH);
            fail("should have thrown AccessDeniedException");
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = {"VIEW_AUTH_CERT", "VIEW_SIGN_CERT"})
    public void getCertificateForHashNotFound() throws Exception {
        doThrow(SignerException
                .tr(X_CERT_NOT_FOUND, "mock code", "mock msg"))
                .when(signerRpcClient).getCertForHash(any());
        var e = assertThrows(NotFoundException.class, () -> tokenCertificatesApiController.getCertificate(UNKNOWN_CERT_HASH));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND, error.code());
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importCertificateFromToken() throws Exception {
        ResponseEntity<TokenCertificateDto> response =
                tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
        TokenCertificateDto addedCert = response.getBody();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSignCertificateDetails(addedCert);
        assertLocationHeader("/api/token-certificates/" + addedCert.getCertificateDetails().getHash(),
                response);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importCertificateFromTokenActionNotPossible() throws Exception {
        // by default all actions are possible
        doReturn(EnumSet.noneOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCertificateActions(any(), any(), any());

        tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
    }

    @Test
    @WithMockUser(authorities = "IMPORT_SIGN_CERT")
    public void importCertificateFromTokenHashNotFound() throws Exception {
        doThrow(SignerException
                .tr(X_CERT_NOT_FOUND, "mock code", "mock msg"))
                .when(signerRpcClient).getCertForHash(any());
        var e = assertThrows(NotFoundException.class,
                () -> tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND, error.code());
    }

    @Test
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importAuthCertificateFromToken() throws Exception {
        X509Certificate mockAuthCert = getMockAuthCertificate();
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(mockAuthCert)
                .certificateStatus(CertificateInfo.STATUS_SAVED)
                .build();
        doAnswer(answer -> certificateInfo).when(signerRpcClient).getCertForHash(any());
        var e = assertThrows(TokenCertificateService.AuthCertificateNotSupportedException.class,
                () -> tokenCertificatesApiController.importCertificateFromToken(MOCK_AUTH_CERTIFICATE_HASH));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_AUTH_CERT_NOT_SUPPORTED, error.code());
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = "IMPORT_AUTH_CERT")
    public void importSignCertificateFromTokenWithWrongPermission() {
        tokenCertificatesApiController.importCertificateFromToken(MOCK_CERTIFICATE_HASH);
    }

    private static void assertSignCertificateDetails(TokenCertificateDto tokenCertificate) {
        CertificateDetailsDto certificateDetails = tokenCertificate.getCertificateDetails();
        assertEquals("N/A", certificateDetails.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"),
                certificateDetails.getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"),
                certificateDetails.getNotAfter());
        assertEquals("1", certificateDetails.getSerial());
        assertEquals(Integer.valueOf(3), certificateDetails.getVersion());
        assertEquals("SHA512withRSA", certificateDetails.getSignatureAlgorithm());
        assertEquals("RSA", certificateDetails.getPublicKeyAlgorithm());
        assertEquals("FAAFA4860332289F3083DE6BF955D4DF9AEEFB2B33CBCC66BD0EF27AB05C708D", certificateDetails.getHash());
        assertTrue(certificateDetails.getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(certificateDetails.getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(Integer.valueOf(65537), certificateDetails.getRsaPublicKeyExponent());
        assertEquals(new ArrayList<>(Collections.singletonList(KeyUsageDto.NON_REPUDIATION)),
                new ArrayList<>(certificateDetails.getKeyUsages()));
    }

    private static void assertAuthCertificateDetails(TokenCertificateDto tokenCertificate) {
        CertificateDetailsDto certificateDetails = tokenCertificate.getCertificateDetails();
        assertEquals("Customized Test CA CN", certificateDetails.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("2019-11-28T09:20:27Z"),
                certificateDetails.getNotBefore());
        assertEquals(OffsetDateTime.parse("2039-11-23T09:20:27Z"),
                certificateDetails.getNotAfter());
        assertEquals("8", certificateDetails.getSerial());
        assertEquals(Integer.valueOf(3), certificateDetails.getVersion());
        assertEquals("SHA256withRSA", certificateDetails.getSignatureAlgorithm());
        assertEquals("RSA", certificateDetails.getPublicKeyAlgorithm());
        assertEquals("54E2586715084EBF37FE5CA8B761A208CD0D710699FC866A49684D8F62DA28D2", certificateDetails.getHash());
        assertTrue(certificateDetails.getSignature().startsWith("a11c4675cf4e2fa1664464"));
        assertTrue(certificateDetails.getRsaPublicKeyModulus().startsWith("92e952dfc1d84648c2873"));
        assertEquals(Integer.valueOf(65537), certificateDetails.getRsaPublicKeyExponent());
        assertTrue(new HashSet<>(certificateDetails.getKeyUsages()).contains(KeyUsageDto.DIGITAL_SIGNATURE));
        assertTrue(new HashSet<>(certificateDetails.getKeyUsages()).contains(KeyUsageDto.KEY_ENCIPHERMENT));
        assertTrue(new HashSet<>(certificateDetails.getKeyUsages()).contains(KeyUsageDto.DATA_ENCIPHERMENT));
        assertTrue(new HashSet<>(certificateDetails.getKeyUsages()).contains(KeyUsageDto.KEY_AGREEMENT));
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificate() throws Exception {
        ResponseEntity<Void> response =
                tokenCertificatesApiController.deleteCertificate(MOCK_CERTIFICATE_HASH);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateNotFound() throws Exception {
        doThrow(SignerException
                .tr(X_CERT_NOT_FOUND, "mock code", "mock msg"))
                .when(signerRpcClient).getCertForHash(any());
        doThrow(SignerException
                .tr(X_CERT_NOT_FOUND, "mock code", "mock msg"))
                .when(signerRpcClient).deleteCert(any());
        var e = assertThrows(NotFoundException.class, () -> tokenCertificatesApiController.deleteCertificate(UNKNOWN_CERT_HASH));
        ErrorDeviation error = e.getErrorDeviation();
        assertEquals(DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND, error.code());
    }

    @Test
    @WithMockUser(authorities = {"VIEW_KEYS"})
    public void getPossibleActionsForCertificate() throws Exception {
        ResponseEntity<List<PossibleActionDto>> response = tokenCertificatesApiController
                .getPossibleActionsForCertificate(MOCK_CERTIFICATE_HASH);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Set<PossibleActionDto> allActions = new HashSet<>(Arrays.asList(PossibleActionDto.values()));
        assertEquals(allActions, new HashSet<>(response.getBody()));
    }

}
