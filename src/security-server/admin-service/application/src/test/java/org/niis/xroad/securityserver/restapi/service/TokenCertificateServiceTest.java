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
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldDescriptionImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfo;
import ee.ria.xroad.signer.protocol.dto.CertRequestInfoProto;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfoAndKeyId;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.facade.SignerProxyFacade;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_CSR_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_SSL_AUTH_FAILED;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_NOT_AVAILABLE;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.CertificateTestUtils.createCertificateInfo;

/**
 * Test TokenCertificateService
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class TokenCertificateServiceTest {
    public static final String GOOD_ADDRESS = "0.0.0.0";
    public static final String BAD_ADDRESS = "1.1.1.1";

    // hashes and ids for mocking
    private static final String EXISTING_CERT_HASH = "ok-cert";
    private static final String EXISTING_CERT_IN_AUTH_KEY_HASH = "ok-cert-auth";
    private static final String EXISTING_CERT_IN_SIGN_KEY_HASH = "ok-cert-sign";
    private static final String MISSING_CERTIFICATE_HASH = "MISSING_HASH";
    private static final String GOOD_KEY_ID = "key-which-exists";
    private static final String AUTH_KEY_ID = "auth-key";
    private static final String SIGN_KEY_ID = "sign-key";
    private static final String GOOD_CSR_ID = "csr-which-exists";
    private static final String GOOD_AUTH_CSR_ID = "auth-csr-which-exists";
    private static final String GOOD_SIGN_CSR_ID = "sign-csr-which-exists";
    private static final String CSR_NOT_FOUND_CSR_ID = "csr-404";
    private static final String SIGNER_EXCEPTION_CSR_ID = "signer-ex-csr";
    private static final String IO_EXCEPTION_MSG = "io-exception-msg";
    private static final String SSL_AUTH_ERROR_MESSAGE = "Security server has no valid authentication certificate";

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
    private ClientService clientService;

    @MockBean
    private ManagementRequestSenderService managementRequestSenderService;

    @MockBean
    private CertificateAuthorityService certificateAuthorityService;

    @SpyBean
    private KeyService keyService;

    @MockBean
    private GlobalConfService globalConfService;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @MockBean
    private ClientRepository clientRepository;

    @MockBean
    private ServerConfService serverConfService;

    @SpyBean
    private PossibleActionsRuleEngine possibleActionsRuleEngine;

    @MockBean
    private TokenService tokenService;

    private final ClientId.Conf client = ClientId.Conf.create(TestUtils.INSTANCE_FI,
            TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1);
    private final CertificateInfo signCert =
            new CertificateTestUtils.CertificateInfoBuilder().id(EXISTING_CERT_IN_SIGN_KEY_HASH).build();
    private final CertificateInfo authCert =
            new CertificateTestUtils.CertificateInfoBuilder().id(EXISTING_CERT_IN_AUTH_KEY_HASH)
                    .certificate(CertificateTestUtils.getMockAuthCertificate()).build();

    private CertRequestInfo newCertRequestInfo(String id) {
        return new CertRequestInfo(CertRequestInfoProto.newBuilder()
                .setId(id)
                .build());
    }

    @Before
    public void setup() throws Exception {
        when(clientService.getLocalClientMemberIds())
                .thenReturn(new HashSet<>(Collections.singletonList(client)));

        DnFieldDescription editableField = new DnFieldDescriptionImpl("O", "x", "default")
                .setReadOnly(false);
        when(certificateAuthorityService.getCertificateProfile(any(), any(), any(), anyBoolean()))
                .thenReturn(new DnFieldTestCertificateProfileInfo(
                        editableField, true));

        // need lots of mocking
        // construct some test keys, with csrs and certs
        // make used finders return data from these items:
        // keyService.getKey, signerProxyFacade.getKeyIdForCertHash,
        // signerProxyFacade.getCertForHash
        // mock delete-operations (deleteCertificate, deleteCsr)
        CertRequestInfo goodCsr = newCertRequestInfo(GOOD_CSR_ID);
        CertRequestInfo authCsr = newCertRequestInfo(GOOD_AUTH_CSR_ID);
        CertRequestInfo signCsr = newCertRequestInfo(GOOD_SIGN_CSR_ID);
        CertRequestInfo signerExceptionCsr = newCertRequestInfo(SIGNER_EXCEPTION_CSR_ID);
        KeyInfo authKey = new TokenTestUtils.KeyInfoBuilder().id(AUTH_KEY_ID)
                .keyUsageInfo(KeyUsageInfo.AUTHENTICATION)
                .csr(authCsr)
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
                .csr(signCsr)
                .cert(signCert)
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .friendlyName("fubar")
                .key(authKey)
                .key(signKey)
                .key(goodKey)
                .build();

        mockGetTokenAndKeyIdForCertificateHash(authKey, goodKey, signKey, tokenInfo);
        mockGetTokenAndKeyIdForCertificateRequestId(authKey, goodKey, signKey, tokenInfo);
        mockGetKey(authKey, goodKey, signKey);
        mockGetKeyIdForCertHash();
        mockGetCertForHash();
        mockDeleteCert();
        mockDeleteCertRequest();
        mockGetTokenForKeyId(tokenInfo);
        // activate / deactivate
        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String hash = (String) args[0];
            if (MISSING_CERTIFICATE_HASH.equals(hash)) {
                throw new CodedException(TokenCertificateService.CERT_NOT_FOUND_FAULT_CODE);
            }

            return null;
        }).when(signerProxyFacade).deactivateCert(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String hash = (String) args[0];
            if (MISSING_CERTIFICATE_HASH.equals(hash)) {
                throw new CodedException(TokenCertificateService.CERT_NOT_FOUND_FAULT_CODE);
            }
            return null;
        }).when(signerProxyFacade).activateCert(eq("certID"));

        // by default all actions are possible
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleTokenActions(any());
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleKeyActions(any(), any());
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCertificateActions(any(), any(), any());
        doReturn(EnumSet.allOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCsrActions(any());
    }

    private void mockGetTokenForKeyId(TokenInfo tokenInfo) throws KeyNotFoundException {
        doAnswer(invocation -> {
            String keyId = (String) invocation.getArguments()[0];
            switch (keyId) {
                case AUTH_KEY_ID:
                    return tokenInfo;
                case SIGN_KEY_ID:
                    return tokenInfo;
                case GOOD_KEY_ID:
                    return tokenInfo;
                default:
                    throw new KeyNotFoundException("unknown keyId: " + keyId);
            }
        }).when(tokenService).getTokenForKeyId(any());
    }

    private void mockDeleteCertRequest() throws Exception {
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

    private void mockDeleteCert() throws Exception {
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
    }

    private void mockGetCertForHash() throws Exception {
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
                    return new CertificateTestUtils.CertificateInfoBuilder().id(certHash).build();
                case MISSING_CERTIFICATE_HASH:
                    return createCertificateInfo(null, false, false, "status", "certID",
                            CertificateTestUtils.getMockAuthCertificateBytes(), null);
                default:
                    throw new RuntimeException("bad switch option: " + certHash);
            }
        }).when(signerProxyFacade).getCertForHash(any());
    }

    private void mockGetKeyIdForCertHash() throws Exception {
        // signerProxyFacade.getKeyIdForCertHash(hash)
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            if (certHash.equals(EXISTING_CERT_IN_AUTH_KEY_HASH)) {
                return new SignerProxy.KeyIdInfo(AUTH_KEY_ID, null);
            } else {
                return new SignerProxy.KeyIdInfo(SIGN_KEY_ID, null);
            }
        }).when(signerProxyFacade).getKeyIdForCertHash(any());
    }

    private void mockGetKey(KeyInfo authKey, KeyInfo goodKey, KeyInfo signKey) throws KeyNotFoundException {
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
    }

    private void mockGetTokenAndKeyIdForCertificateRequestId(KeyInfo authKey, KeyInfo goodKey, KeyInfo signKey,
            TokenInfo tokenInfo) throws KeyNotFoundException, CsrNotFoundException {
        doAnswer(invocation -> {
            String csrId = (String) invocation.getArguments()[0];
            switch (csrId) {
                case GOOD_AUTH_CSR_ID:
                    return new TokenInfoAndKeyId(tokenInfo, authKey.getId());
                case GOOD_SIGN_CSR_ID:
                    return new TokenInfoAndKeyId(tokenInfo, signKey.getId());
                case GOOD_CSR_ID:
                    return new TokenInfoAndKeyId(tokenInfo, goodKey.getId());
                case CSR_NOT_FOUND_CSR_ID:
                case SIGNER_EXCEPTION_CSR_ID:
                    // getTokenAndKeyIdForCertificateRequestId should work, exception comes later
                    return new TokenInfoAndKeyId(tokenInfo, goodKey.getId());
                default:
                    throw new CertificateNotFoundException("unknown csr: " + csrId);
            }
        }).when(tokenService).getTokenAndKeyIdForCertificateRequestId(any());
    }

    private void mockGetTokenAndKeyIdForCertificateHash(KeyInfo authKey, KeyInfo goodKey, KeyInfo signKey,
            TokenInfo tokenInfo) throws KeyNotFoundException, CertificateNotFoundException {
        doAnswer(invocation -> {
            String hash = (String) invocation.getArguments()[0];
            switch (hash) {
                case EXISTING_CERT_IN_AUTH_KEY_HASH:
                case CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH:
                    return new TokenInfoAndKeyId(tokenInfo, authKey.getId());
                case EXISTING_CERT_IN_SIGN_KEY_HASH:
                    return new TokenInfoAndKeyId(tokenInfo, signKey.getId());
                case NOT_FOUND_CERT_HASH:
                case EXISTING_CERT_HASH:
                case SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH:
                case SIGNER_EX_INTERNAL_ERROR_HASH:
                case SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH:
                case SIGNER_EX_TOKEN_READONLY_HASH:
                case CertificateTestUtils.MOCK_CERTIFICATE_HASH:
                    return new TokenInfoAndKeyId(tokenInfo, goodKey.getId());
                default:
                    throw new CertificateNotFoundException("unknown cert: " + hash);
            }
        }).when(tokenService).getTokenAndKeyIdForCertificateHash(any());
    }

    @Test
    public void generateCertRequest() throws Exception {
        // wrong key usage
        try {
            tokenCertificateService.generateCertRequest(AUTH_KEY_ID, client,
                    KeyUsageInfo.SIGNING, "ca", new HashMap<>(),
                    null);
            fail("should throw exception");
        } catch (WrongKeyUsageException expected) {
        }
        try {
            tokenCertificateService.generateCertRequest(SIGN_KEY_ID, client,
                    KeyUsageInfo.AUTHENTICATION, "ca", new HashMap<>(),
                    null);
            fail("should throw exception");
        } catch (WrongKeyUsageException expected) {
        }
        tokenCertificateService.generateCertRequest(SIGN_KEY_ID, client,
                KeyUsageInfo.SIGNING, "ca", ImmutableMap.of("O", "baz"),
                CertificateRequestFormat.DER);
    }

    @Test
    @WithMockUser(authorities = {"GENERATE_SIGN_CERT_REQ", "GENERATE_AUTH_CERT_REQ"})
    public void regenerateCertRequestSuccess() throws Exception {
        SignerProxy.GeneratedCertRequestInfo csrInfo = tokenCertificateService
                .regenerateCertRequest(AUTH_KEY_ID, GOOD_AUTH_CSR_ID, CertificateRequestFormat.PEM);
        verify(signerProxyFacade, times(1))
                .regenerateCertRequest(GOOD_AUTH_CSR_ID, CertificateRequestFormat.PEM);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"GENERATE_SIGN_CERT_REQ"})
    public void regenerateAuthCsrPermission() throws Exception {
        SignerProxy.GeneratedCertRequestInfo csrInfo = tokenCertificateService
                .regenerateCertRequest(AUTH_KEY_ID, GOOD_AUTH_CSR_ID, CertificateRequestFormat.PEM);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"GENERATE_AUTH_CERT_REQ"})
    public void regenerateSignCsrPermission() throws Exception {
        SignerProxy.GeneratedCertRequestInfo csrInfo = tokenCertificateService
                .regenerateCertRequest(SIGN_KEY_ID, GOOD_SIGN_CSR_ID, CertificateRequestFormat.PEM);
    }

    private CodedException signerException(String code) {
        return CodedException.tr(code, "mock-translation", "mock-message")
                .withPrefix(SIGNER_X);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSuccessfully() throws Exception {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_HASH);
        verify(signerProxyFacade, times(1)).deleteCert(EXISTING_CERT_HASH);
    }

    @Test(expected = ActionNotPossibleException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateActionNotPossible() throws Exception {
        EnumSet empty = EnumSet.noneOf(PossibleActionEnum.class);
        doReturn(empty).when(possibleActionsRuleEngine).getPossibleCertificateActions(any(), any(), any());
        tokenCertificateService.deleteCertificate(EXISTING_CERT_HASH);
    }

    @Test(expected = CertificateNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateHashNotFound() throws Exception {
        tokenCertificateService.deleteCertificate(NOT_FOUND_CERT_HASH);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerIdNotFound() throws Exception {
        try {
            tokenCertificateService.deleteCertificate(SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH);
            fail("should have thrown exception");
        } catch (CertificateNotFoundException expected) {
            ErrorDeviation errorDeviation = expected.getErrorDeviation();
            Assert.assertEquals(DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND_WITH_ID, errorDeviation.getCode());
            assertEquals(1, errorDeviation.getMetadata().size());
            assertEquals(SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH, errorDeviation.getMetadata().iterator().next());
        }
    }

    @Test(expected = CodedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerInternalError() throws Exception {
        tokenCertificateService.deleteCertificate(SIGNER_EX_INTERNAL_ERROR_HASH);
    }

    @Test(expected = CodedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerTokenNotAvailable() throws Exception {
        tokenCertificateService.deleteCertificate(SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH);
    }

    @Test(expected = CodedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerTokenReadonly() throws Exception {
        tokenCertificateService.deleteCertificate(SIGNER_EX_TOKEN_READONLY_HASH);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT"})
    public void deleteAuthCertificateWithoutPermission() throws Exception {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_AUTH_CERT"})
    public void deleteSignCertificateWithoutPermission() throws Exception {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_IN_SIGN_KEY_HASH);
    }

    @Test(expected = CsrNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsrCsrNotFound() throws Exception {
        tokenCertificateService.deleteCsr(CSR_NOT_FOUND_CSR_ID);
    }

    @Test(expected = CsrNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsrSignerExceptions() throws Exception {
        tokenCertificateService.deleteCsr(SIGNER_EXCEPTION_CSR_ID);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT"})
    public void deleteAuthCsrWithoutPermission() throws Exception {
        tokenCertificateService.deleteCsr(GOOD_AUTH_CSR_ID);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_AUTH_CERT"})
    public void deleteSignCsrWithoutPermission() throws Exception {
        tokenCertificateService.deleteCsr(GOOD_SIGN_CSR_ID);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsr() throws Exception {
        // success
        tokenCertificateService.deleteCsr(GOOD_CSR_ID);
        verify(signerProxyFacade, times(1)).deleteCertRequest(GOOD_CSR_ID);
    }

    @Test(expected = ActionNotPossibleException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsrActionNotPossible() throws Exception {
        doReturn(EnumSet.noneOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCsrActions(any());
        tokenCertificateService.deleteCsr(GOOD_CSR_ID);
    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void deActivateCertificateCheckPossibleActions() throws Exception {
        // we want to use the real rules for this test
        Mockito.reset(possibleActionsRuleEngine);
        // EXISTING_CERT_IN_SIGN_KEY_HASH - active
        // EXISTING_CERT_IN_AUTH_KEY_HASH - inactive
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            boolean active = false;
            switch (certHash) {
                case EXISTING_CERT_IN_SIGN_KEY_HASH:
                    active = false;
                    break;
                case EXISTING_CERT_IN_AUTH_KEY_HASH:
                    active = true;
                    break;
                default:
                    throw new RuntimeException("bad switch option: " + certHash);
            }
            return createCertificateInfo(null, active, true, "status", "certID",
                    CertificateTestUtils.getMockAuthCertificateBytes(), null);
        }).when(signerProxyFacade).getCertForHash(any());

        // can activate inactive
        tokenCertificateService.activateCertificate(EXISTING_CERT_IN_SIGN_KEY_HASH);

        // can deactivate active
        tokenCertificateService.deactivateCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);

        try {
            // can not activate already active -> possible actions exception
            tokenCertificateService.activateCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);
            fail("should throw XYZException");
        } catch (Exception expected) {
        }

        try {
            // can not deactivate already disabled -> possible actions exception
            tokenCertificateService.deactivateCertificate(EXISTING_CERT_IN_SIGN_KEY_HASH);
            fail("should throw XYZException");
        } catch (Exception expected) {
        }

    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void deActivateUnknownCertificate() throws Exception {
        // we want to use the real rules for this test
        Mockito.reset(possibleActionsRuleEngine);
        doReturn(createCertificateInfo(null, true, true, "status",
                "certID", CertificateTestUtils.getMockCertificateWithoutExtensionsBytes(), null))
                .when(signerProxyFacade).getCertForHash(any());

        try {
            // trying to deactivate this cert, which is neither sign nor auth, should result in exception
            tokenCertificateService.activateCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void activateMissingCertificate() throws Exception {
        try {
            tokenCertificateService.activateCertificate(MISSING_CERTIFICATE_HASH);
            fail("should throw CertificateNotFoundException");
        } catch (CertificateNotFoundException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void deactivateMissingCertificate() throws Exception {
        try {
            tokenCertificateService.deactivateCertificate(MISSING_CERTIFICATE_HASH);
            fail("should throw CertificateNotFoundException");
        } catch (CertificateNotFoundException e) {
        }
    }

    @Test
    public void registerAuthCertificate() throws Exception {
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        try {
            tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH, GOOD_ADDRESS);
        } catch (Exception e) {
            fail();
        }
    }

    @Test(expected = CodedException.class)
    public void registerAuthCertificateFail() throws Exception {
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any()))
                .thenThrow(new CodedException("FAILED"));
        tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH, BAD_ADDRESS);
    }

    @Test(expected = ActionNotPossibleException.class)
    public void registerAuthCertificateNotPossible() throws Exception {
        EnumSet empty = EnumSet.noneOf(PossibleActionEnum.class);
        doReturn(empty).when(possibleActionsRuleEngine).getPossibleCertificateActions(any(), any(), any());
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH, GOOD_ADDRESS);
    }

    @Test
    public void unregisterAuthCertificate() throws Exception {
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        try {
            tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void unregisterAuthCertNoValid() throws Exception {
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        when(managementRequestSenderService.sendAuthCertDeletionRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(
                        new CodedException(X_SSL_AUTH_FAILED, SSL_AUTH_ERROR_MESSAGE)
                                .withPrefix(SERVER_CLIENTPROXY_X)));
        try {
            tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH);
            fail("Should have thrown ManagementRequestSendingFailedException");
        } catch (ManagementRequestSendingFailedException e) {
            assertTrue(e.getErrorDeviation().getMetadata().get(0).contains(SSL_AUTH_ERROR_MESSAGE));
        }
    }

    @Test
    public void unregisterAuthCertAssertExceptionMessage() throws Exception {
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        when(managementRequestSenderService.sendAuthCertDeletionRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(
                        new IOException(IO_EXCEPTION_MSG)));
        try {
            tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH);
            fail("Should have thrown ManagementRequestSendingFailedException");
        } catch (ManagementRequestSendingFailedException e) {
            assertTrue(e.getErrorDeviation().getMetadata().contains(IO_EXCEPTION_MSG));
        }
    }

    @Test(expected = TokenCertificateService.SignCertificateNotSupportedException.class)
    public void registerSignCertificate() throws Exception {
        doAnswer(answer -> signCert).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_CERTIFICATE_HASH, GOOD_ADDRESS);
    }

    @Test(expected = TokenCertificateService.SignCertificateNotSupportedException.class)
    public void unregisterSignCertificate() throws Exception {
        doAnswer(answer -> signCert).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_CERTIFICATE_HASH);
    }

    @Test
    public void isValidAuthCert() {
        assertTrue(tokenCertificateService.isValidAuthCert(CertificateTestUtils.getMockAuthCertificate()));
        assertFalse(tokenCertificateService.isValidAuthCert(CertificateTestUtils.getMockSignCertificate()));
        assertFalse(
                tokenCertificateService.isValidAuthCert(CertificateTestUtils.getMockCertificateWithoutExtensions()));
    }

    @Test
    public void isValidSignCert() {
        assertFalse(tokenCertificateService.isValidSignCert(CertificateTestUtils.getMockAuthCertificate()));
        assertTrue(tokenCertificateService.isValidSignCert(CertificateTestUtils.getMockSignCertificate()));
        assertFalse(
                tokenCertificateService.isValidSignCert(CertificateTestUtils.getMockCertificateWithoutExtensions()));
    }

    @Test
    public void markAuthCertForDeletion() throws Exception {
        doAnswer(answer -> authCert).when(signerProxyFacade).getCertForHash(any());
        try {
            tokenCertificateService.markAuthCertForDeletion(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH);
        } catch (Exception e) {
            fail("Should not fail");
        }
    }
}
