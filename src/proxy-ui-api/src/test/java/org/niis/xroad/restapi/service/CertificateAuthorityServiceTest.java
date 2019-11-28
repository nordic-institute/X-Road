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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
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

    @Before
    public void setup() {
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("fi-not-auth-only", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("est-auth-only", true,
                "ee.ria.xroad.common.certificateprofile.impl.SkEsteIdCertificateProfileInfoProvider"));
        when(globalConfService.getApprovedCAsForThisInstance()).thenReturn(approvedCAInfos);
    }

    @Test
    public void getCertificateAuthority() throws Exception {
        ApprovedCAInfo caInfo = certificateAuthorityService.getCertificateAuthority("fi-not-auth-only");
        assertEquals("ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider",
                caInfo.getCertificateProfileInfo());

        try {
            certificateAuthorityService.getCertificateAuthority("does-not-exist");
            fail("should have thrown exception");
        } catch (CertificateAuthorityNotFoundException expected) {
        }
    }


    @Test
    public void getCertificateAuthorities() {
        Collection<ApprovedCAInfo> caInfos = certificateAuthorityService.getCertificateAuthorities(null);
        assertEquals(2, caInfos.size());

        caInfos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING);
        assertEquals(1, caInfos.size());
        assertEquals("fi-not-auth-only", caInfos.iterator().next().getName());

        caInfos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        assertEquals(2, caInfos.size());

        when(globalConfService.getApprovedCAsForThisInstance()).thenReturn(new ArrayList<>());
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING).size());
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(null).size());
    }

    @Test
    public void getCertificateProfile() throws Exception {
        ClientId clientId = TestUtils.getClientId("FI", "GOV", "M1", null);

        // test handling of profile info parameters:
        //        private final SecurityServerId serverId;
        //        private final ClientId clientId; (sign only)
        //        private final String memberName;

        CertificateProfileInfo profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.SIGNING, clientId);
        assertTrue(profile instanceof FiVRKSignCertificateProfileInfo);
        assertEquals("FI/TEST-INMEM-SS/GOV", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("M1", profile.getSubjectFields()[3].getDefaultValue());
        assertTrue(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.AUTHENTICATION, clientId);
        assertTrue(profile instanceof FiVRKAuthCertificateProfileInfo);
        assertEquals("FI/TEST-INMEM-SS/GOV", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("", profile.getSubjectFields()[3].getDefaultValue());
        assertFalse(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("est-auth-only",
                KeyUsageInfo.AUTHENTICATION, clientId);
        assertTrue(profile instanceof AuthCertificateProfileInfo);
        assertEquals(0, profile.getSubjectFields().length);

        // exceptions
        try {
            certificateAuthorityService.getCertificateProfile("est-auth-only",
                    KeyUsageInfo.SIGNING, clientId);
            fail("should have thrown exception");
        } catch (WrongKeyUsageException expected) {
        }

        try {
            certificateAuthorityService.getCertificateProfile("this-does-not-exist",
                    KeyUsageInfo.SIGNING, clientId);
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
                    KeyUsageInfo.SIGNING, clientId);
            fail("should have thrown exception");
        } catch (CertificateProfileInstantiationException expected) {
        }
    }

}
