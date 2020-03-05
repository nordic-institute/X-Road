/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.FiVRKAuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.FiVRKSignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.ApprovedCaDto;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.repository.ServerConfRepository;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

/**
 * test CertificateAuthorityService
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class CertificateAuthorityServiceTest {

    @Autowired
    CertificateAuthorityService certificateAuthorityService;

    @MockBean
    GlobalConfService globalConfService;

    @MockBean
    GlobalConfFacade globalConfFacade;

    @MockBean
    SignerProxyFacade signerProxyFacade;

    @MockBean
    ServerConfService serverConfService;

    @MockBean
    ClientService clientService;

    @MockBean
    ServerConfRepository serverConfRepository;

    private ClientId ownerId;


    @Before
    public void setup() throws Exception {
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("fi-not-auth-only", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("est-auth-only", true,
                "ee.ria.xroad.common.certificateprofile.impl.SkEsteIdCertificateProfileInfoProvider"));
        when(globalConfService.getApprovedCAsForThisInstance()).thenReturn(approvedCAInfos);

        List<X509Certificate> caCerts = new ArrayList<>();
        caCerts.add(CertificateTestUtils.getMockCertificate());
        caCerts.add(CertificateTestUtils.getWidgitsCertificate());
        when(globalConfService.getAllCaCertsForThisInstance()).thenReturn(caCerts);

        when(globalConfService.getApprovedCAForThisInstance(any())).thenAnswer(invocation -> {
            X509Certificate cert = (X509Certificate) invocation.getArguments()[0];
            for (int i = 0; i < caCerts.size(); i++) {
                if (caCerts.get(i) == cert) {
                    return approvedCAInfos.get(i);
                }
            }
            throw new RuntimeException("approved ca info not found");
        });

        String[] ocspResponses = caCerts.stream()
                .map(cert -> {
                    try {
                        byte[] bytes = CertificateTestUtils.generateOcspBytes(cert, CertificateStatus.GOOD);
                        return CryptoUtils.encodeBase64(bytes);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList())
                .toArray(new String[]{});
        doReturn(ocspResponses).when(signerProxyFacade).getOcspResponses(any());

        SecurityServerId securityServerId = SecurityServerId.create("test-i",
                "test-mclass", "test-mcode", "test-scode");
        ownerId = ClientId.create(securityServerId.getXRoadInstance(),
                securityServerId.getMemberClass(),
                securityServerId.getMemberCode());
        when(serverConfService.getSecurityServerOwnerId()).thenReturn(ownerId);
        when(serverConfService.getSecurityServerId()).thenReturn(securityServerId);
        when(clientService.getLocalClient(any())).thenReturn(new ClientType());
        when(globalConfFacade.getMemberName(any())).thenReturn("mock-member-name");
    }

    @Test
    public void getCertificateAuthority() throws Exception {
        ApprovedCAInfo caInfo = certificateAuthorityService.getCertificateAuthorityInfo("fi-not-auth-only");
        assertEquals("ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider",
                caInfo.getCertificateProfileInfo());

        try {
            certificateAuthorityService.getCertificateAuthorityInfo("does-not-exist");
            fail("should have thrown exception");
        } catch (CertificateAuthorityNotFoundException expected) {
        }
    }


    @Test
    public void getCertificateAuthorities() throws Exception {
        Collection<ApprovedCaDto> caDtos = certificateAuthorityService.getCertificateAuthorities(null);
        assertEquals(2, caDtos.size());

        caDtos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING);
        assertEquals(1, caDtos.size());
        assertEquals("fi-not-auth-only", caDtos.iterator().next().getCommonName());

        caDtos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        assertEquals(2, caDtos.size());

        when(globalConfService.getAllCaCertsForThisInstance()).thenReturn(new ArrayList<>());
        when(signerProxyFacade.getOcspResponses(any())).thenReturn(new String[]{});
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING).size());
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(null).size());
    }

    @Test
    public void getCertificateProfile() throws Exception {
        ClientId clientId = TestUtils.getClientId("test-i", "test-mclass", "test-mcode", null);
        when(clientService.getLocalClientMemberIds()).thenReturn(new HashSet<>(Collections.singletonList(clientId)));
        when(serverConfService.getSecurityServerId()).thenReturn(SecurityServerId.create(
                clientId.getXRoadInstance(), clientId.getMemberClass(), clientId.getMemberCode(), "ss"));

        // test handling of profile info parameters:
        //        private final SecurityServerId serverId;
        //        private final ClientId clientId; (sign only)
        //        private final String memberName;

        CertificateProfileInfo profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.SIGNING, ownerId);
        assertTrue(profile instanceof FiVRKSignCertificateProfileInfo);
        assertEquals("test-i/ss/test-mclass", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("test-mcode", profile.getSubjectFields()[3].getDefaultValue());
        assertTrue(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.AUTHENTICATION, ownerId);
        assertTrue(profile instanceof FiVRKAuthCertificateProfileInfo);
        assertEquals("test-i/ss/test-mclass", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("", profile.getSubjectFields()[3].getDefaultValue());
        assertFalse(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("est-auth-only",
                KeyUsageInfo.AUTHENTICATION, ownerId);
        assertTrue(profile instanceof AuthCertificateProfileInfo);
        assertEquals(0, profile.getSubjectFields().length);

        // exceptions
        try {
            certificateAuthorityService.getCertificateProfile("est-auth-only",
                    KeyUsageInfo.SIGNING, ownerId);
            fail("should have thrown exception");
        } catch (WrongKeyUsageException expected) {
        }

        try {
            certificateAuthorityService.getCertificateProfile("this-does-not-exist",
                    KeyUsageInfo.SIGNING, ownerId);
            fail("should have thrown exception");
        } catch (CertificateAuthorityNotFoundException expected) {
        }

        // cant instantiate
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("provider-class-does-not-exist", false,
                "ee.ria.xroad.common.certificateprofile.impl.NonExistentProvider"));
        when(globalConfService.getApprovedCAsForThisInstance()).thenReturn(approvedCAInfos);

        try {
            certificateAuthorityService.getCertificateProfile("provider-class-does-not-exist",
                    KeyUsageInfo.SIGNING, ownerId);
            fail("should have thrown exception");
        } catch (CertificateProfileInstantiationException expected) {
        }
    }

}
