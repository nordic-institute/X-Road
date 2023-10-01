/*
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.CertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.FiVRKAuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.FiVRKSignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.ApprovedCAInfo;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.KeyUsageInfo;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.dto.ApprovedCaDto;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    @Autowired
    CertificateAuthorityService certificateAuthorityService;

    @Autowired
    CacheManager cacheManager;

    public static final String MOCK_AUTH_CERT_SUBJECT =
            "SERIALNUMBER=CS/SS1/ORG, CN=ss1, O=SS5, C=FI";
    public static final String MOCK_AUTH_CERT_ISSUER =
            "CN=Customized Test CA CN, OU=Customized Test CA OU, O=Customized Test, C=FI";
    public static final String MOCK_TOP_CA_SUBJECT_DN =
            "CN=X-Road Test CA CN, OU=X-Road Test CA OU, O=X-Road Test, C=FI";
    public static final String MOCK_INTERMEDIATE_CA_SUBJECT_DN =
            "CN=int-cn, O=X-Road Test int";

    @Before
    public void setup() throws Exception {
        evictCache(); // start with empty cache
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("fi-not-auth-only", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        // @deprecated The {@link SkEsteIdCertificateProfileInfoProvider} profile has been marked deprecated starting
        // from X-Road 7.2.0 and will be removed in a future version. This test should then also be cleaned up.
        approvedCAInfos.add(new ApprovedCAInfo("est-auth-only", true,
                "ee.ria.xroad.common.certificateprofile.impl.SkEsteIdCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("mock-top-ca", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        approvedCAInfos.add(new ApprovedCAInfo("mock-intermediate-ca", false,
                "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider"));
        when(globalConfFacade.getApprovedCAs(any())).thenReturn(approvedCAInfos);

        List<X509Certificate> caCerts = new ArrayList<>();
        caCerts.add(CertificateTestUtils.getMockCertificate());
        caCerts.add(CertificateTestUtils.getMockAuthCertificate());
        caCerts.add(CertificateTestUtils.getMockTopCaCertificate());
        caCerts.add(CertificateTestUtils.getMockIntermediateCaCertificate());
        when(globalConfFacade.getAllCaCerts(any())).thenReturn(caCerts);

        when(globalConfFacade.getApprovedCA(any(), any())).thenAnswer(invocation -> {
            X509Certificate cert = (X509Certificate) invocation.getArguments()[1];
            for (int i = 0; i < caCerts.size(); i++) {
                if (caCerts.get(i) == cert) {
                    return approvedCAInfos.get(i);
                }
            }
            throw new RuntimeException("approved ca info not found");
        });

        // ocsp responses are not fetched for all CAs
        // see CertificateAuthorityService#getCertificateAuthorities implementation
        Map<String, String> subjectsToIssuers = caCerts.stream().collect(
                Collectors.toMap(
                        x509 -> x509.getSubjectDN().getName(),
                        x509 -> x509.getIssuerDN().getName()));
        List<X509Certificate> filteredCerts = caCerts.stream()
                .filter(cert -> subjectsToIssuers.containsKey(cert.getIssuerDN().getName()))
                .collect(Collectors.toList());

        String[] ocspResponses = filteredCerts.stream()
                .map(cert -> {
                    try {
                        byte[] bytes = CertificateTestUtils.generateOcspBytes(cert, CertificateStatus.GOOD);
                        return CryptoUtils.encodeBase64(bytes);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList())
                .toArray(new String[] {});
        doReturn(ocspResponses).when(signerProxyFacade).getOcspResponses(any());
        when(clientRepository.getClient(any())).thenReturn(new ClientType());
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
        certificateAuthorityService.getCertificateAuthorities(null);
        int expectedExecutions = 1;
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());

        // repeat comes from cache
        certificateAuthorityService.getCertificateAuthorities(null);
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());

        // different parameter - different cache key
        certificateAuthorityService.getCertificateAuthorities(null, true);
        expectedExecutions++;
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());

        // more parameters
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        certificateAuthorityService.getCertificateAuthorities(null, false);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, true);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, false);
        expectedExecutions += 4;
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());

        // repeats come from cache
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        certificateAuthorityService.getCertificateAuthorities(null, false);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, false);
        certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING, false);
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());

        // evict cache
        evictCache();
        certificateAuthorityService.getCertificateAuthorities(null);
        expectedExecutions++;
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());

        certificateAuthorityService.getCertificateAuthorities(null);
        verify(globalConfFacade, times(expectedExecutions)).getAllCaCerts(any());
    }

    private void evictCache() {
        cacheManager.getCache(CertificateAuthorityService.GET_CERTIFICATE_AUTHORITIES_CACHE).clear();
    }

    @Test
    public void buildPath() {
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
        assertEquals(Collections.singletonList(subject), path);

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
        assertEquals(Collections.singletonList(subject), path);
    }

    @Test
    public void getCertificateAuthorities() throws Exception {
        List<ApprovedCaDto> caDtos = certificateAuthorityService.getCertificateAuthorities(null);
        assertEquals(3, caDtos.size());

        caDtos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING);
        assertEquals(2, caDtos.size());
        ApprovedCaDto ca = caDtos.get(0);
        assertEquals("fi-not-auth-only", ca.getName());
        assertFalse(ca.isAuthenticationOnly());
        assertEquals("CN=N/A", ca.getIssuerDistinguishedName());
        assertEquals("CN=N/A", ca.getSubjectDistinguishedName());
        assertEquals(Collections.singletonList("CN=N/A"), ca.getSubjectDnPath());
        assertTrue(ca.isTopCa());
        assertEquals("good", ca.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00Z"), ca.getNotAfter());

        caDtos = certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.AUTHENTICATION);
        assertEquals(3, caDtos.size());
        ApprovedCaDto ca2 = caDtos.get(1);
        assertEquals("est-auth-only", ca2.getName());
        assertTrue(ca2.isAuthenticationOnly());
        assertEquals(MOCK_AUTH_CERT_ISSUER, ca2.getIssuerDistinguishedName());
        assertEquals(MOCK_AUTH_CERT_SUBJECT, ca2.getSubjectDistinguishedName());
        assertEquals(Collections.singletonList(MOCK_AUTH_CERT_SUBJECT), ca2.getSubjectDnPath());
        assertTrue(ca2.isTopCa());
        assertEquals("not available", ca2.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2039-11-23T09:20:27Z"), ca2.getNotAfter());

        evictCache();
        when(globalConfFacade.getAllCaCerts(any())).thenReturn(new ArrayList<>());
        when(signerProxyFacade.getOcspResponses(any())).thenReturn(new String[] {});
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(KeyUsageInfo.SIGNING).size());
        assertEquals(0, certificateAuthorityService.getCertificateAuthorities(null).size());
    }

    @Test
    public void getIntermediateCertificateAuthorities() throws Exception {
        List<ApprovedCaDto> caDtos = certificateAuthorityService.getCertificateAuthorities(null, true);
        assertEquals(4, caDtos.size());

        ApprovedCaDto topCa = caDtos.get(2);
        assertEquals("mock-top-ca", topCa.getName());
        assertFalse(topCa.isAuthenticationOnly());
        assertEquals(MOCK_TOP_CA_SUBJECT_DN, topCa.getIssuerDistinguishedName());
        assertEquals(MOCK_TOP_CA_SUBJECT_DN, topCa.getSubjectDistinguishedName());
        assertEquals(Collections.singletonList(MOCK_TOP_CA_SUBJECT_DN), topCa.getSubjectDnPath());
        assertTrue(topCa.isTopCa());
        assertEquals("good", topCa.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2039-06-09T06:11:31Z"), topCa.getNotAfter());

        ApprovedCaDto intermediateCa = caDtos.get(3);
        assertEquals("mock-intermediate-ca", intermediateCa.getName());
        assertFalse(intermediateCa.isAuthenticationOnly());
        assertEquals(MOCK_TOP_CA_SUBJECT_DN, intermediateCa.getIssuerDistinguishedName());
        assertEquals(MOCK_INTERMEDIATE_CA_SUBJECT_DN, intermediateCa.getSubjectDistinguishedName());
        assertEquals(Arrays.asList(MOCK_TOP_CA_SUBJECT_DN, MOCK_INTERMEDIATE_CA_SUBJECT_DN),
                intermediateCa.getSubjectDnPath());
        assertFalse(intermediateCa.isTopCa());
        assertEquals("good", intermediateCa.getOcspResponse());
        assertEquals(OffsetDateTime.parse("2040-02-28T07:53:49Z"), intermediateCa.getNotAfter());
    }

    @Test
    public void getCertificateProfile() throws Exception {
        ClientType client = new ClientType();
        client.setIdentifier(COMMON_OWNER_ID);
        when(clientRepository.getAllLocalClients()).thenReturn(Collections.singletonList(client));

        // test handling of profile info parameters:
        //        private final SecurityServerId serverId;
        //        private final ClientId clientId; (sign only)
        //        private final String memberName;

        CertificateProfileInfo profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.SIGNING, COMMON_OWNER_ID, false);
        assertTrue(profile instanceof FiVRKSignCertificateProfileInfo);
        assertEquals("FI/SS1/GOV", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("M1", profile.getSubjectFields()[3].getDefaultValue());
        assertTrue(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("fi-not-auth-only",
                KeyUsageInfo.AUTHENTICATION, COMMON_OWNER_ID, false);
        assertTrue(profile instanceof FiVRKAuthCertificateProfileInfo);
        assertEquals("FI/SS1/GOV", profile.getSubjectFields()[2].getDefaultValue());
        assertEquals("", profile.getSubjectFields()[3].getDefaultValue());
        assertFalse(profile.getSubjectFields()[3].isReadOnly());

        profile = certificateAuthorityService.getCertificateProfile("est-auth-only",
                KeyUsageInfo.AUTHENTICATION, COMMON_OWNER_ID, false);
        assertTrue(profile instanceof AuthCertificateProfileInfo);
        assertEquals(0, profile.getSubjectFields().length);

        // exceptions
        try {
            certificateAuthorityService.getCertificateProfile("est-auth-only",
                    KeyUsageInfo.SIGNING, COMMON_OWNER_ID, false);
            fail("should have thrown exception");
        } catch (WrongKeyUsageException expected) {
        }

        try {
            certificateAuthorityService.getCertificateProfile("this-does-not-exist",
                    KeyUsageInfo.SIGNING, COMMON_OWNER_ID, false);
            fail("should have thrown exception");
        } catch (CertificateAuthorityNotFoundException expected) {
        }

        // cant instantiate
        List<ApprovedCAInfo> approvedCAInfos = new ArrayList<>();
        approvedCAInfos.add(new ApprovedCAInfo("provider-class-does-not-exist", false,
                "ee.ria.xroad.common.certificateprofile.impl.NonExistentProvider"));
        when(globalConfFacade.getApprovedCAs(any())).thenReturn(approvedCAInfos);

        try {
            certificateAuthorityService.getCertificateProfile("provider-class-does-not-exist",
                    KeyUsageInfo.SIGNING, COMMON_OWNER_ID, false);
            fail("should have thrown exception");
        } catch (CertificateProfileInstantiationException expected) {
        }
    }

}
