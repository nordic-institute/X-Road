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

import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldDescriptionImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.messagelog.database.MessageLogDatabaseCtx;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.niis.xroad.common.acme.AcmeService;
import org.niis.xroad.common.core.exception.ErrorDeviation;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedCAInfo;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.exceptions.DeviationCodes;
import org.niis.xroad.securityserver.restapi.repository.ClientRepository;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.dto.TokenInfoAndKeyId;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.CertRequestInfoProto;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
import static org.niis.xroad.common.core.exception.ErrorCode.CERT_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.CSR_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.SSL_AUTH_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_NOT_AVAILABLE;
import static org.niis.xroad.common.core.exception.ErrorCode.TOKEN_READONLY;
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
@ActiveProfiles("test")
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
    private static final String HASH_FOR_ACME_IMPORT = "e77dd7e47f081b6d579bb6b9482e17358e167862b66d70d50955886b535857a7";
    private static final String CA_NAME = "ca";
    private static final String PROFILE_CLASS = "ee.test.profile.TestProfile";

    @Autowired
    private TokenCertificateService tokenCertificateService;

    @MockitoBean
    private SignerRpcClient signerRpcClient;

    @MockitoBean
    protected OpMonitorClient opMonitorClient;

    @MockitoBean
    private ClientService clientService;

    @MockitoBean
    private ManagementRequestSenderService managementRequestSenderService;

    @MockitoBean
    private CertificateAuthorityService certificateAuthorityService;

    @MockitoSpyBean
    private KeyService keyService;

    @MockitoBean
    private GlobalConfService globalConfService;

    @MockitoBean
    private GlobalConfProvider globalConfProvider;

    @MockitoBean
    private ClientRepository clientRepository;

    @MockitoBean
    private ServerConfService serverConfService;

    @MockitoSpyBean
    private PossibleActionsRuleEngine possibleActionsRuleEngine;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private AcmeService acmeService;

    @MockitoBean
    MonitorRpcClient monitorClient;

    @MockitoBean
    ProxyRpcClient proxyRpcClient;

    @MockitoBean
    ConfClientRpcClient confClientRpcClient;

    @MockitoBean
    ServerConfDatabaseCtx databaseCtx;

    @MockitoBean
    MessageLogDatabaseCtx messageLogDatabaseCtx;

    private final ClientId.Conf client = ClientId.Conf.create(TestUtils.INSTANCE_FI,
            TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1);
    private final CertificateInfo signCert =
            new CertificateTestUtils.CertificateInfoBuilder().id(EXISTING_CERT_IN_SIGN_KEY_HASH).build();
    private final CertificateInfo authCert =
            new CertificateTestUtils.CertificateInfoBuilder().id(EXISTING_CERT_IN_AUTH_KEY_HASH)
                    .certificate(CertificateTestUtils.getMockAuthCertificate()).build();
    private final ApprovedCAInfo acmeCA = new ApprovedCAInfo(CA_NAME, false, PROFILE_CLASS,
            "http://test-ca/acme", "123.4.5.6", "5", "6");

    private CertRequestInfo newCertRequestInfo(String id) {
        return new CertRequestInfo(CertRequestInfoProto.newBuilder()
                .setId(id)
                .setMemberId(ClientIdMapper.toDto(client))
                .setSubjectName("CN=common name")
                .setSubjectAltName("ss0")
                .build());
    }

    @Before
    public void setup() throws Exception {
        when(clientService.getLocalClientMemberIds())
                .thenReturn(new HashSet<>(Collections.singletonList(client)));

        DnFieldDescription editableOField = new DnFieldDescriptionImpl("O", "x", "default")
                .setReadOnly(false);
        DnFieldDescription editableCNField = new DnFieldDescriptionImpl("CN", "y", "default")
                .setReadOnly(false);
        DnFieldDescription editableSANField = new DnFieldDescriptionImpl("subjectAltName", "z", "default")
                .setReadOnly(false);
        when(certificateAuthorityService.getCertificateProfile(any(), any(), any(), anyBoolean()))
                .thenReturn(new DnFieldTestCertificateProfileInfo(
                        new DnFieldDescription[]{editableOField, editableCNField, editableSANField}, true));
        when(certificateAuthorityService.getCertificateAuthorityInfo(CA_NAME)).thenReturn(acmeCA);

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
                throw XrdRuntimeException.systemException(CERT_NOT_FOUND).build();
            }

            return null;
        }).when(signerRpcClient).deactivateCert(any());

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            String hash = (String) args[0];
            if (MISSING_CERTIFICATE_HASH.equals(hash)) {
                throw XrdRuntimeException.systemException(CERT_NOT_FOUND).build();
            }
            return null;
        }).when(signerRpcClient).activateCert(eq("certID"));

        //doAnswer(answer -> signCert).when(signerProxyFacade).getCertForHash(any());

        when(globalConfProvider.getInstanceIdentifier()).thenReturn(TestUtils.INSTANCE_FI);
        when(globalConfProvider.getSubjectName(any(), any())).thenReturn(client);

        when(clientRepository.clientExists(any(), anyBoolean())).thenReturn(true);

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
            return switch (keyId) {
                case AUTH_KEY_ID, GOOD_KEY_ID, SIGN_KEY_ID -> tokenInfo;
                default -> throw new KeyNotFoundException("unknown keyId: " + keyId);
            };
        }).when(tokenService).getTokenForKeyId(any());
    }

    private void mockDeleteCertRequest() {
        // signerProxyFacade.deleteCertRequest(id)
        doAnswer(invocation -> {
            String csrId = (String) invocation.getArguments()[0];
            if (GOOD_CSR_ID.equals(csrId)) {
                return null;
            } else if (SIGNER_EXCEPTION_CSR_ID.equals(csrId)) {
                throw XrdRuntimeException.systemException(CSR_NOT_FOUND).build();
            } else if (CSR_NOT_FOUND_CSR_ID.equals(csrId)) {
                throw new CsrNotFoundException("not found");
            } else {
                throw new CsrNotFoundException("not found");
            }
        }).when(signerRpcClient).deleteCertRequest(any());
    }

    private void mockDeleteCert() {
        // attempts to delete either succeed or throw specific exceptions
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            return switch (certHash) {
                case EXISTING_CERT_HASH -> null;
                case SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH -> throw XrdRuntimeException.systemException(CERT_NOT_FOUND).build();
                case SIGNER_EX_INTERNAL_ERROR_HASH -> throw XrdRuntimeException.systemException(INTERNAL_ERROR).build();
                case SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH -> throw XrdRuntimeException.systemException(TOKEN_NOT_AVAILABLE).build();
                case SIGNER_EX_TOKEN_READONLY_HASH -> throw XrdRuntimeException.systemException(TOKEN_READONLY).build();

                default -> throw new RuntimeException("bad switch option: " + certHash);
            };
        }).when(signerRpcClient).deleteCert(any());
    }

    private void mockGetCertForHash() {
        // signerProxyFacade.getCertForHash(hash)
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            return switch (certHash) {
                case NOT_FOUND_CERT_HASH -> throw XrdRuntimeException.systemException(CERT_NOT_FOUND).build();
                case EXISTING_CERT_HASH, EXISTING_CERT_IN_AUTH_KEY_HASH, EXISTING_CERT_IN_SIGN_KEY_HASH,
                     SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH, SIGNER_EX_INTERNAL_ERROR_HASH, SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH,
                     SIGNER_EX_TOKEN_READONLY_HASH, HASH_FOR_ACME_IMPORT ->
                    // cert will have same id as hash
                        new CertificateTestUtils.CertificateInfoBuilder().id(certHash).build();
                case MISSING_CERTIFICATE_HASH -> createCertificateInfo(null, false, false, "status", "certID",
                        CertificateTestUtils.getMockAuthCertificateBytes(), null, null);
                default -> throw new RuntimeException("bad switch option: " + certHash);
            };
        }).when(signerRpcClient).getCertForHash(any());
    }

    private void mockGetKeyIdForCertHash() {
        // signerProxyFacade.getKeyIdForCertHash(hash)
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            if (certHash.equals(EXISTING_CERT_IN_AUTH_KEY_HASH)) {
                return new SignerRpcClient.KeyIdInfo(AUTH_KEY_ID, null);
            } else {
                return new SignerRpcClient.KeyIdInfo(SIGN_KEY_ID, null);
            }
        }).when(signerRpcClient).getKeyIdForCertHash(any());
    }

    private void mockGetKey(KeyInfo authKey, KeyInfo goodKey, KeyInfo signKey) throws KeyNotFoundException {
        // keyService.getKey(keyId)
        doAnswer(invocation -> {
            String keyId = (String) invocation.getArguments()[0];
            return switch (keyId) {
                case AUTH_KEY_ID -> authKey;
                case SIGN_KEY_ID -> signKey;
                case GOOD_KEY_ID -> goodKey;
                default -> throw new KeyNotFoundException("unknown keyId: " + keyId);
            };
        }).when(keyService).getKey(any());
    }

    private void mockGetTokenAndKeyIdForCertificateRequestId(KeyInfo authKey, KeyInfo goodKey, KeyInfo signKey,
                                                             TokenInfo tokenInfo) throws KeyNotFoundException, CsrNotFoundException {
        doAnswer(invocation -> {
            String csrId = (String) invocation.getArguments()[0];
            return switch (csrId) {
                case GOOD_AUTH_CSR_ID -> new TokenInfoAndKeyId(tokenInfo, authKey.getId());
                case GOOD_SIGN_CSR_ID -> new TokenInfoAndKeyId(tokenInfo, signKey.getId());
                case GOOD_CSR_ID -> new TokenInfoAndKeyId(tokenInfo, goodKey.getId());
                case CSR_NOT_FOUND_CSR_ID, SIGNER_EXCEPTION_CSR_ID ->
                    // getTokenAndKeyIdForCertificateRequestId should work, exception comes later
                        new TokenInfoAndKeyId(tokenInfo, goodKey.getId());
                default -> throw new CertificateNotFoundException("unknown csr: " + csrId);
            };
        }).when(tokenService).getTokenAndKeyIdForCertificateRequestId(any());
    }

    private void mockGetTokenAndKeyIdForCertificateHash(KeyInfo authKey, KeyInfo goodKey, KeyInfo signKey,
                                                        TokenInfo tokenInfo) throws KeyNotFoundException, CertificateNotFoundException {
        doAnswer(invocation -> {
            String hash = (String) invocation.getArguments()[0];
            return switch (hash) {
                case EXISTING_CERT_IN_AUTH_KEY_HASH, CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH ->
                        new TokenInfoAndKeyId(tokenInfo, authKey.getId());
                case EXISTING_CERT_IN_SIGN_KEY_HASH -> new TokenInfoAndKeyId(tokenInfo, signKey.getId());
                case NOT_FOUND_CERT_HASH, EXISTING_CERT_HASH, SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH, SIGNER_EX_INTERNAL_ERROR_HASH,
                     SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH, SIGNER_EX_TOKEN_READONLY_HASH, CertificateTestUtils.MOCK_CERTIFICATE_HASH ->
                        new TokenInfoAndKeyId(tokenInfo, goodKey.getId());
                default -> throw new CertificateNotFoundException("unknown cert: " + hash);
            };
        }).when(tokenService).getTokenAndKeyIdForCertificateHash(any());
    }

    @Test
    public void generateCertRequest() {
        // wrong key usage
        assertThrows(WrongKeyUsageException.class, () -> tokenCertificateService.generateCertRequest(AUTH_KEY_ID, client,
                KeyUsageInfo.SIGNING, CA_NAME, new HashMap<>(),
                null, false));

        assertThrows(WrongKeyUsageException.class, () -> tokenCertificateService.generateCertRequest(SIGN_KEY_ID, client,
                KeyUsageInfo.AUTHENTICATION, CA_NAME, new HashMap<>(),
                null, false));

        tokenCertificateService.generateCertRequest(SIGN_KEY_ID, client,
                KeyUsageInfo.SIGNING, CA_NAME, ImmutableMap.of("O", "baz", "CN", "common name", "subjectAltName", "subject alt name"),
                CertificateRequestFormat.DER, false);
    }

    @Test
    public void generateCertRequestSAN() {
        tokenCertificateService.generateCertRequest(SIGN_KEY_ID,
                client,
                KeyUsageInfo.SIGNING,
                CA_NAME,
                ImmutableMap.of("CN", "test-common-name", "O", "test-org", "subjectAltName", "test-alt-name"),
                CertificateRequestFormat.DER,
                false);
        verify(signerRpcClient)
                .generateCertRequest(SIGN_KEY_ID,
                        client.getMemberId(),
                        KeyUsageInfo.SIGNING,
                        "O=test-org, CN=test-common-name",
                        "test-alt-name",
                        CertificateRequestFormat.DER,
                        PROFILE_CLASS);
    }

    @Test
    @WithMockUser(authorities = {"IMPORT_SIGN_CERT"})
    public void generateAcmeCert() throws Exception {
        when(signerRpcClient.generateCertRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SignerRpcClient.GeneratedCertRequestInfo(null, CertificateTestUtils.getMockSignCsrBytes(),
                        null, null, null));
        X509Certificate mockSignCertificate = CertificateTestUtils.getMockSignCertificate();
        when(acmeService.orderCertificateFromACMEServer(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mockSignCertificate));
        when(globalConfProvider.getApprovedCA(any(), any()))
                .thenReturn(new ApprovedCAInfo("testca", false, "ee.test.Profile", null, null, null, null));
        tokenCertificateService.generateCertRequest(SIGN_KEY_ID, client,
                KeyUsageInfo.SIGNING, CA_NAME,
                ImmutableMap.of("CN", "test-common-name", "O", "test-org", "subjectAltName", "test-alt-name"),
                CertificateRequestFormat.DER, true);
        verify(signerRpcClient)
                .generateCertRequest(SIGN_KEY_ID,
                        client.getMemberId(),
                        KeyUsageInfo.SIGNING,
                        "O=test-org, CN=test-common-name",
                        "test-alt-name",
                        CertificateRequestFormat.DER,
                        PROFILE_CLASS);
        verify(signerRpcClient).importCert(mockSignCertificate.getEncoded(),
                CertificateInfo.STATUS_REGISTERED,
                client.getMemberId(),
                false);
    }

    @Test
    @WithMockUser(authorities = {"GENERATE_SIGN_CERT_REQ", "GENERATE_AUTH_CERT_REQ"})
    public void regenerateCertRequestSuccess() {
        tokenCertificateService
                .regenerateCertRequest(AUTH_KEY_ID, GOOD_AUTH_CSR_ID, CertificateRequestFormat.PEM);
        verify(signerRpcClient, times(1))
                .regenerateCertRequest(GOOD_AUTH_CSR_ID, CertificateRequestFormat.PEM);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"GENERATE_SIGN_CERT_REQ"})
    public void regenerateAuthCsrPermission() {
        tokenCertificateService
                .regenerateCertRequest(AUTH_KEY_ID, GOOD_AUTH_CSR_ID, CertificateRequestFormat.PEM);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"GENERATE_AUTH_CERT_REQ"})
    public void regenerateSignCsrPermission() {
        tokenCertificateService
                .regenerateCertRequest(SIGN_KEY_ID, GOOD_SIGN_CSR_ID, CertificateRequestFormat.PEM);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSuccessfully() {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_HASH);
        verify(signerRpcClient, times(1)).deleteCert(EXISTING_CERT_HASH);
    }

    @Test(expected = ActionNotPossibleException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateActionNotPossible() {
        EnumSet<PossibleActionEnum> empty = EnumSet.noneOf(PossibleActionEnum.class);
        doReturn(empty).when(possibleActionsRuleEngine).getPossibleCertificateActions(any(), any(), any());
        tokenCertificateService.deleteCertificate(EXISTING_CERT_HASH);
    }

    @Test(expected = CertificateNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateHashNotFound() {
        tokenCertificateService.deleteCertificate(NOT_FOUND_CERT_HASH);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerIdNotFound() {
        CertificateNotFoundException exception = assertThrows(CertificateNotFoundException.class, () ->
                tokenCertificateService.deleteCertificate(SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH));
        ErrorDeviation errorDeviation = exception.getErrorDeviation();
        Assert.assertEquals(DeviationCodes.ERROR_CERTIFICATE_NOT_FOUND_WITH_ID, errorDeviation.code());
        assertEquals(1, errorDeviation.metadata().size());
        assertEquals(SIGNER_EX_CERT_WITH_ID_NOT_FOUND_HASH, errorDeviation.metadata().getFirst());
    }

    @Test(expected = XrdRuntimeException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerInternalError() {
        tokenCertificateService.deleteCertificate(SIGNER_EX_INTERNAL_ERROR_HASH);
    }

    @Test(expected = XrdRuntimeException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerTokenNotAvailable() {
        tokenCertificateService.deleteCertificate(SIGNER_EX_TOKEN_NOT_AVAILABLE_HASH);
    }

    @Test(expected = XrdRuntimeException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCertificateSignerTokenReadonly() {
        tokenCertificateService.deleteCertificate(SIGNER_EX_TOKEN_READONLY_HASH);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT"})
    public void deleteAuthCertificateWithoutPermission() {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_AUTH_CERT"})
    public void deleteSignCertificateWithoutPermission() {
        tokenCertificateService.deleteCertificate(EXISTING_CERT_IN_SIGN_KEY_HASH);
    }

    @Test(expected = CsrNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsrCsrNotFound() {
        tokenCertificateService.deleteCsr(CSR_NOT_FOUND_CSR_ID);
    }

    @Test(expected = CsrNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsrSignerExceptions() {
        tokenCertificateService.deleteCsr(SIGNER_EXCEPTION_CSR_ID);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT"})
    public void deleteAuthCsrWithoutPermission() {
        tokenCertificateService.deleteCsr(GOOD_AUTH_CSR_ID);
    }

    @Test(expected = AccessDeniedException.class)
    @WithMockUser(authorities = {"DELETE_AUTH_CERT"})
    public void deleteSignCsrWithoutPermission() {
        tokenCertificateService.deleteCsr(GOOD_SIGN_CSR_ID);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsr() {
        // success
        tokenCertificateService.deleteCsr(GOOD_CSR_ID);
        verify(signerRpcClient, times(1)).deleteCertRequest(GOOD_CSR_ID);
    }

    @Test(expected = ActionNotPossibleException.class)
    @WithMockUser(authorities = {"DELETE_SIGN_CERT", "DELETE_AUTH_CERT"})
    public void deleteCsrActionNotPossible() {
        doReturn(EnumSet.noneOf(PossibleActionEnum.class)).when(possibleActionsRuleEngine)
                .getPossibleCsrActions(any());
        tokenCertificateService.deleteCsr(GOOD_CSR_ID);
    }

    @Test
    @WithMockUser(authorities = {"GENERATE_SIGN_CERT_REQ", "IMPORT_SIGN_CERT"})
    public void orderAcmeCertificate() throws Exception {
        byte[] csrBytes = CertificateTestUtils.getMockSignCsrBytes();
        X509Certificate mockSignCertificate = CertificateTestUtils.getMockSignCertificate();
        when(acmeService.orderCertificateFromACMEServer(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(mockSignCertificate));
        when(signerRpcClient.regenerateCertRequest(any(), any()))
                .thenReturn(new SignerRpcClient.GeneratedCertRequestInfo(null, csrBytes, null, null, null));
        when(globalConfProvider.getCaCert(any(), any()))
                .thenReturn(CertificateTestUtils.getMockIntermediateCaCertificate());
        when(globalConfProvider.getApprovedCA(any(), any()))
                .thenReturn(new ApprovedCAInfo("testca", false, "ee.test.Profile", "http://test-ca/acme", "123.4.5.6", "5", "6"));
        tokenCertificateService.orderAcmeCertificate(CA_NAME, GOOD_CSR_ID, KeyUsageInfo.SIGNING);
        verify(acmeService).orderCertificateFromACMEServer("common name",
                "ss0",
                KeyUsageInfo.SIGNING,
                acmeCA,
                client.asEncodedId(),
                csrBytes);
        verify(signerRpcClient).importCert(mockSignCertificate.getEncoded(),
                CertificateInfo.STATUS_REGISTERED,
                client.getMemberId(),
                false);
        verify(signerRpcClient).setNextPlannedRenewal(any(), any());
    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void deActivateCertificateCheckPossibleActions() {
        // we want to use the real rules for this test
        Mockito.reset(possibleActionsRuleEngine);
        // EXISTING_CERT_IN_SIGN_KEY_HASH - active
        // EXISTING_CERT_IN_AUTH_KEY_HASH - inactive
        doAnswer(invocation -> {
            String certHash = (String) invocation.getArguments()[0];
            boolean active = switch (certHash) {
                case EXISTING_CERT_IN_SIGN_KEY_HASH -> false;
                case EXISTING_CERT_IN_AUTH_KEY_HASH -> true;
                default -> throw new RuntimeException("bad switch option: " + certHash);
            };
            return createCertificateInfo(null, active, true, "status", "certID",
                    CertificateTestUtils.getMockAuthCertificateBytes(), null, null);
        }).when(signerRpcClient).getCertForHash(any());

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
    public void deActivateUnknownCertificate() {
        // we want to use the real rules for this test
        Mockito.reset(possibleActionsRuleEngine);
        doReturn(createCertificateInfo(null, true, true, "status",
                "certID", CertificateTestUtils.getMockCertificateWithoutExtensionsBytes(), null, null))
                .when(signerRpcClient).getCertForHash(any());

        try {
            // trying to deactivate this cert, which is neither sign nor auth, should result in exception
            tokenCertificateService.activateCertificate(EXISTING_CERT_IN_AUTH_KEY_HASH);
            fail("should throw RuntimeException");
        } catch (RuntimeException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void activateMissingCertificate() {
        assertThrows(CertificateNotFoundException.class, () ->
                tokenCertificateService.activateCertificate(MISSING_CERTIFICATE_HASH));
    }

    @Test
    @WithMockUser(authorities = {"ACTIVATE_DISABLE_AUTH_CERT", "ACTIVATE_DISABLE_SIGN_CERT"})
    public void deactivateMissingCertificate() {
        assertThrows(CertificateNotFoundException.class, () ->
                tokenCertificateService.deactivateCertificate(MISSING_CERTIFICATE_HASH));
    }

    @Test
    public void registerAuthCertificate() {
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        try {
            tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH, GOOD_ADDRESS);
        } catch (Exception e) {
            fail();
        }
    }

    @Test(expected = XrdRuntimeException.class)
    public void registerAuthCertificateFail() {
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        when(managementRequestSenderService.sendAuthCertRegisterRequest(any(), any(), anyBoolean()))
                .thenThrow(XrdRuntimeException.systemInternalError("FAILED"));
        tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH, BAD_ADDRESS);
    }

    @Test(expected = ActionNotPossibleException.class)
    public void registerAuthCertificateNotPossible() {
        EnumSet<PossibleActionEnum> empty = EnumSet.noneOf(PossibleActionEnum.class);
        doReturn(empty).when(possibleActionsRuleEngine).getPossibleCertificateActions(any(), any(), any());
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH, GOOD_ADDRESS);
    }

    @Test
    public void unregisterAuthCertificate() {
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        try {
            tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void unregisterAuthCertNoValid() {
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        when(managementRequestSenderService.sendAuthCertDeletionRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(
                        XrdRuntimeException.systemException(SSL_AUTH_FAILED, SSL_AUTH_ERROR_MESSAGE)
                                .withPrefix(SERVER_CLIENTPROXY_X)));

        var err = assertThrows(ManagementRequestSendingFailedException.class,
                () -> tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH));
        assertTrue(err.getErrorDeviation().metadata().getFirst().contains(SSL_AUTH_ERROR_MESSAGE));
    }

    @Test
    public void unregisterAuthCertAssertExceptionMessage() {
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        when(managementRequestSenderService.sendAuthCertDeletionRequest(any()))
                .thenThrow(new ManagementRequestSendingFailedException(
                        new IOException(IO_EXCEPTION_MSG)));

        var err = assertThrows(ManagementRequestSendingFailedException.class,
                () -> tokenCertificateService.unregisterAuthCert(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH));
        assertTrue(err.getErrorDeviation().metadata().contains(IO_EXCEPTION_MSG));
    }

    @Test(expected = TokenCertificateService.SignCertificateNotSupportedException.class)
    public void registerSignCertificate() {
        doAnswer(answer -> signCert).when(signerRpcClient).getCertForHash(any());
        tokenCertificateService.registerAuthCert(CertificateTestUtils.MOCK_CERTIFICATE_HASH, GOOD_ADDRESS);
    }

    @Test(expected = TokenCertificateService.SignCertificateNotSupportedException.class)
    public void unregisterSignCertificate() {
        doAnswer(answer -> signCert).when(signerRpcClient).getCertForHash(any());
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
    public void markAuthCertForDeletion() {
        doAnswer(answer -> authCert).when(signerRpcClient).getCertForHash(any());
        try {
            tokenCertificateService.markAuthCertForDeletion(CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH);
        } catch (Exception e) {
            fail("Should not fail");
        }
    }
}
