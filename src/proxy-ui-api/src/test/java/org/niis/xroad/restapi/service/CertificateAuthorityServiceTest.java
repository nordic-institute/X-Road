/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * test CertificateAuthorityService
 */
public class CertificateAuthorityServiceTest extends AbstractServiceTestContext {
    public static final String MOCK_AUTH_CERT_SUBJECT =
            "SERIALNUMBER=CS/SS1/ORG, CN=ss1, O=SS5, C=FI";
    public static final String MOCK_AUTH_CERT_ISSUER =
            "CN=Customized Test CA CN, OU=Customized Test CA OU, O=Customized Test, C=FI";
    public static final String MOCK_TOP_CA_SUBJECT_DN =
            "CN=X-Road Test CA CN, OU=X-Road Test CA OU, O=X-Road Test, C=FI";
    public static final String MOCK_INTERMEDIATE_CA_SUBJECT_DN =
            "CN=int-cn, O=X-Road Test int";

    private ClientId ownerId;

    @Before
    public void setup() throws Exception {
        evictCache(); // start with empty cache
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("fi-not-auth-only", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("est-auth-only", true,
                "ee.ria.xroad.common.certificateprofile.impl.SkEsteIdCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("mock-top-ca", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("mock-intermediate-ca", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        when(globalConfService.getApprovedCAsForThisInstance()).thenReturn(approvedCAInfos);

        List<X509Certificate> caCerts = new ArrayList<>();
        caCerts.add(CertificateTestUtils.getMockCertificate());
        caCerts.add(CertificateTestUtils.getMockAuthCertificate());
        caCerts.add(CertificateTestUtils.getMockTopCaCertificate());
        caCerts.add(CertificateTestUtils.getMockIntermediateCaCertificate());
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
    public void getCertificateAuthorityInfo() throws Exception {
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
    public void caching() throws Exception {
        cacheEvictor.setEvict(false);

        certificateAuthorityService.getCertificateAuthorities(null);
        int expectedExecutions = 1;
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();

        // repeat comes from cache
        certificateAuthorityService.getCertificateAuthorities(null);
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();

        // different parameter - different cache key
        certificateAuthorityService.getCertificateAuthorities(null, true);
        expectedExecutions++;
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();

        // more parameters
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        certificateAuthorityService.getCertificateAuthorities(null, false);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, true);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, false);
        expectedExecutions += 4;
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();

        // repeats come from cache
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        certificateAuthorityService.getCertificateAuthorities(null, false);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, false);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, false);
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();

        // evict cache
        evictCache();
        certificateAuthorityService.getCertificateAuthorities(null);
        expectedExecutions++;
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();

        certificateAuthorityService.getCertificateAuthorities(null);
        verify(globalConfService, times(expectedExecutions)).getAllCaCertsForThisInstance();
    }

    private void evictCache() {
        cacheEvictor.setEvict(true);
        cacheEvictor.evict();
    }

    @Test
    public void buildPath() throws Exception {
        X509Certificate certificate = CertificateTestUtils.getMockAuthCertificate();
        String subject = MOCK_AUTH_CERT_SUBJECT;
        String issuer = MOCK_AUTH_CERT_ISSUER;
        assertEquals(subject, certificate.getSubjectDN().getName());
        assertEquals(issuer, certificate.getIssuerDN().getName());

        Map<String, String> subjectsToIssuers = new HashMap<>();
        subjectsToIssuers.put(subject, issuer);
        subjectsToIssuers.put(issuer, "a");
        subjectsToIssuers.put("a", "b");
        subjectsToIssuers.put("b", "c");
        subjectsToIssuers.put("c", "c");
        List<String> path = certificateAuthorityService.buildPath(certificate, subjectsToIssuers);
        assertEquals(Arrays.asList("c", "b", "a", issuer, subject), path);

        subjectsToIssuers = new HashMap<>();
        subjectsToIssuers.put(subject, issuer);
        subjectsToIssuers.put(issuer, "a");
        subjectsToIssuers.put("a", "b");
        subjectsToIssuers.put("b", "c");
        path = certificateAuthorityService.buildPath(certificate, subjectsToIssuers);
        assertEquals(Arrays.asList("b", "a", issuer, subject), path);

        subjectsToIssuers = new HashMap<>();
        subjectsToIssuers.put(subject, issuer);
        subjectsToIssuers.put(issuer, issuer);
        path = certificateAuthorityService.buildPath(certificate, subjectsToIssuers);
        assertEquals(Arrays.asList(issuer, subject), path);

        subjectsToIssuers = new HashMap<>();
        subjectsToIssuers.put(subject, issuer);
        path = certificateAuthorityService.buildPath(certificate, subjectsToIssuers);
        assertEquals(Arrays.asList(subject), path);

        certificate = CertificateTestUtils.getMockCertificate();
        subject = "CN=N/A";
        issuer = "CN=N/A";
        assertEquals(subject, certificate.getSubjectDN().getName());
        assertEquals(issuer, certificate.getIssuerDN().getName());

        subjectsToIssuers = new HashMap<>();
        subjectsToIssuers.put(subject, issuer);
        subjectsToIssuers.put(issuer, "a");
        subjectsToIssuers.put("a", "b");
        subjectsToIssuers.put("b", "c");
        path = certificateAuthorityService.buildPath(certificate, subjectsToIssuers);
        assertEquals(Arrays.asList(subject), path);
    }

    @Test
    public void getCertificateAuthorities() throws Exception {
        List<ApprovedCaDto> caDtos = certificateAuthorityService.getCertificateAuthorities(null);
        assertEquals(3, caDtos.size());

        caDtos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING);
        assertEquals(2, caDtos.size());
        ApprovedCaDto ca = caDtos.get(0);
        assertEquals("fi-not-auth-only", ca.getName());
        assertEquals(false, ca.isAuthenticationOnly());
        assertEquals("CN=N/A", ca.getIssuerDistinguishedName());
        assertEquals("CN=N/A", ca.getSubjectDistinguishedName());
        assertEquals(Arrays.asList("CN=N/A"), ca.getSubjectDnPath());
        assertEquals(true, ca.isTopCa());
        assertEquals("good", ca.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00Z"), ca.getNotAfter());

        caDtos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        assertEquals(3, caDtos.size());
        ApprovedCaDto ca2 = caDtos.get(1);
        assertEquals("est-auth-only", ca2.getName());
        assertEquals(true, ca2.isAuthenticationOnly());
        assertEquals(MOCK_AUTH_CERT_ISSUER, ca2.getIssuerDistinguishedName());
        assertEquals(MOCK_AUTH_CERT_SUBJECT, ca2.getSubjectDistinguishedName());
        assertEquals(Arrays.asList(MOCK_AUTH_CERT_SUBJECT), ca2.getSubjectDnPath());
        assertEquals(true, ca2.isTopCa());
        assertEquals("good", ca2.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2039-11-23T09:20:27Z"), ca2.getNotAfter());

        cacheEvictor.evict();
        when(globalConfService.getAllCaCertsForThisInstance()).thenReturn(new ArrayList<>());
        when(signerProxyFacade.getOcspResponses(any())).thenReturn(new String[]{});
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING).size());
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(null).size());
    }

    @Test
    public void getIntermediateCertificateAuthorities() throws Exception {
        List<ApprovedCaDto> caDtos = certificateAuthorityService.getCertificateAuthorities(null, true);
        assertEquals(4, caDtos.size());

        ApprovedCaDto topCa = caDtos.get(2);
        assertEquals("mock-top-ca", topCa.getName());
        assertEquals(false, topCa.isAuthenticationOnly());
        assertEquals(MOCK_TOP_CA_SUBJECT_DN, topCa.getIssuerDistinguishedName());
        assertEquals(MOCK_TOP_CA_SUBJECT_DN, topCa.getSubjectDistinguishedName());
        assertEquals(Arrays.asList(MOCK_TOP_CA_SUBJECT_DN), topCa.getSubjectDnPath());
        assertEquals(true, topCa.isTopCa());
        assertEquals("good", topCa.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2039-06-09T06:11:31Z"), topCa.getNotAfter());

        ApprovedCaDto intermediateCa = caDtos.get(3);
        assertEquals("mock-intermediate-ca", intermediateCa.getName());
        assertEquals(false, intermediateCa.isAuthenticationOnly());
        assertEquals(MOCK_TOP_CA_SUBJECT_DN, intermediateCa.getIssuerDistinguishedName());
        assertEquals(MOCK_INTERMEDIATE_CA_SUBJECT_DN, intermediateCa.getSubjectDistinguishedName());
        assertEquals(Arrays.asList(MOCK_TOP_CA_SUBJECT_DN, MOCK_INTERMEDIATE_CA_SUBJECT_DN),
                intermediateCa.getSubjectDnPath());
        assertEquals(false, intermediateCa.isTopCa());
        assertEquals("good", intermediateCa.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2040-02-28T07:53:49Z"), intermediateCa.getNotAfter());
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
                KeyUsageInfo.SIGNING, ownerId, false);
        assertTrue(profile instanceof FiVRKSignCertificateProfileInfo);
        assertEquals("test-i/ss/test-mclass", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("test-mcode", profile.getSubjectFields()[3].getDefaultValue());
        assertTrue(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.AUTHENTICATION, ownerId, false);
        assertTrue(profile instanceof FiVRKAuthCertificateProfileInfo);
        assertEquals("test-i/ss/test-mclass", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("", profile.getSubjectFields()[3].getDefaultValue());
        assertFalse(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("est-auth-only",
                KeyUsageInfo.AUTHENTICATION, ownerId, false);
        assertTrue(profile instanceof AuthCertificateProfileInfo);
        assertEquals(0, profile.getSubjectFields().length);

        // exceptions
        try {
            certificateAuthorityService.getCertificateProfile("est-auth-only",
                    KeyUsageInfo.SIGNING, ownerId, false);
            fail("should have thrown exception");
        } catch (WrongKeyUsageException expected) {
        }

        try {
            certificateAuthorityService.getCertificateProfile("this-does-not-exist",
                    KeyUsageInfo.SIGNING, ownerId, false);
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
                    KeyUsageInfo.SIGNING, ownerId, false);
            fail("should have thrown exception");
        } catch (CertificateProfileInstantiationException expected) {
        }
    }

}
