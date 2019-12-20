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
import ee.ria.xroad.common.certificateprofile.DnFieldDescription;
import ee.ria.xroad.common.certificateprofile.impl.DnFieldDescriptionImpl;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;
import ee.ria.xroad.signer.protocol.message.GenerateCertRequest;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.niis.xroad.restapi.util.TestUtils;
import org.niis.xroad.restapi.util.TokenTestUtils.KeyInfoBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.util.CertificateTestUtils.MOCK_AUTH_CERTIFICATE_HASH;
import static org.niis.xroad.restapi.util.CertificateTestUtils.MOCK_CERTIFICATE_HASH;

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

    @Autowired
    private TokenCertificateService tokenCertificateService;

    @MockBean
    private SignerProxyFacade signerProxyFacade;

    @MockBean
    private ClientService clientService;

    @MockBean
    private ManagementRequestService managementRequestService;

    @MockBean
    private CertificateAuthorityService certificateAuthorityService;

    @MockBean
    private KeyService keyService;

    private final ClientId client = ClientId.create(TestUtils.INSTANCE_FI,
            TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1);
    private static final String AUTH_KEY = "auth-id";
    private static final String SIGN_KEY = "sign-id";
    private static final String AMBIGUOUS_KEY = "ambiguous-id";

    @Before
    public void setup() throws Exception {
        when(clientService.getLocalClientMemberIds())
                .thenReturn(new HashSet<>(Collections.singletonList(client)));
        when(keyService.getKey(any())).thenAnswer((Answer<KeyInfo>) invocation -> {
            Object[] args = invocation.getArguments();
            String keyId = (String) args[0];
            if (AUTH_KEY.equals(keyId)) {
                return new KeyInfoBuilder().id(keyId).keyUsageInfo(KeyUsageInfo.AUTHENTICATION).build();
            } else if (SIGN_KEY.equals(keyId)) {
                return new KeyInfoBuilder().id(keyId).keyUsageInfo(KeyUsageInfo.SIGNING).build();
            } else if (AMBIGUOUS_KEY.equals(keyId)) {
                return new KeyInfoBuilder().id(keyId).build();
            } else {
                throw new RuntimeException("unknown key id " + keyId);
            }
        });
        DnFieldDescription editableField = new DnFieldDescriptionImpl("O", "x", "default")
                .setReadOnly(false);
        when(certificateAuthorityService.getCertificateProfile(any(), any(), any()))
                .thenReturn(new DnFieldTestCertificateProfileInfo(
                        editableField, true));
    }

    @Test
    public void generateCertRequest() throws Exception {
        // wrong key usage
        try {
            tokenCertificateService.generateCertRequest(AUTH_KEY, client,
                    KeyUsageInfo.SIGNING, "ca", new HashMap<>(),
                    null);
            fail("should throw exception");
        } catch (WrongKeyUsageException expected) {
        }
        try {
            tokenCertificateService.generateCertRequest(SIGN_KEY, client,
                    KeyUsageInfo.AUTHENTICATION, "ca", new HashMap<>(),
                    null);
            fail("should throw exception");
        } catch (WrongKeyUsageException expected) {
        }
        tokenCertificateService.generateCertRequest(SIGN_KEY, client,
                KeyUsageInfo.SIGNING, "ca", ImmutableMap.of("O", "baz"),
                GenerateCertRequest.RequestFormat.DER);
    }

    @Test
    public void registerAuthCertificate() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockAuthCertificate();
        CertificateInfo certificateInfo = CertificateTestUtils.createTestCertificateInfo(mockAuthCert,
                CertificateStatus.GOOD, "SAVED");
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.registerAuthCert(MOCK_AUTH_CERTIFICATE_HASH, GOOD_ADDRESS);
    }

    @Test(expected = CodedException.class)
    public void registerAuthCertificateFail() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockAuthCertificate();
        CertificateInfo certificateInfo = CertificateTestUtils.createTestCertificateInfo(mockAuthCert,
                CertificateStatus.GOOD, "SAVED");
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        when(managementRequestService.sendAuthCertRegisterRequest(any(), any(), any()))
                .thenThrow(new CodedException("FAILED"));
        tokenCertificateService.registerAuthCert(MOCK_AUTH_CERTIFICATE_HASH, BAD_ADDRESS);
    }

    @Test
    public void unregisterAuthCertificate() throws Exception {
        X509Certificate mockAuthCert = CertificateTestUtils.getMockAuthCertificate();
        CertificateInfo certificateInfo = CertificateTestUtils.createTestCertificateInfo(mockAuthCert,
                CertificateStatus.GOOD, "SAVED");
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.unregisterAuthCert(MOCK_AUTH_CERTIFICATE_HASH);

    }

    @Test(expected = TokenCertificateService.SignCertificateNotSupportedException.class)
    public void registerSignCertificate() throws Exception {
        X509Certificate mockSignCert = CertificateTestUtils.getMockCertificate();
        CertificateInfo certificateInfo = CertificateTestUtils.createTestCertificateInfo(mockSignCert,
                CertificateStatus.GOOD, "SAVED");
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.registerAuthCert(MOCK_CERTIFICATE_HASH, GOOD_ADDRESS);
    }

    @Test(expected = TokenCertificateService.SignCertificateNotSupportedException.class)
    public void unregisterSignCertificate() throws Exception {
        X509Certificate mockSignCert = CertificateTestUtils.getMockCertificate();
        CertificateInfo certificateInfo = CertificateTestUtils.createTestCertificateInfo(mockSignCert,
                CertificateStatus.GOOD, "SAVED");
        doAnswer(answer -> certificateInfo).when(signerProxyFacade).getCertForHash(any());
        tokenCertificateService.unregisterAuthCert(MOCK_CERTIFICATE_HASH);
    }
}
