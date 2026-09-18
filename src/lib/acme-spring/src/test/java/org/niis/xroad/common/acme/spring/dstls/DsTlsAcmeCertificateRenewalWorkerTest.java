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
package org.niis.xroad.common.acme.spring.dstls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.acme.spring.scheduling.CertificateRenewalScheduler;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.service.DsTlsCertificateService;

import javax.security.auth.x500.X500Principal;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsTlsAcmeCertificateRenewalWorkerTest {

    private static final String HOSTNAME = "ds.example.org";
    private static final String CA_NAME = "Test CA";
    private static final String CA_URL = "http://testca:8887";

    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private DsTlsCertificateService dsTlsCertificateService;
    @Mock
    private DsTlsAcmeService dsTlsAcmeService;
    @Mock
    private DsTlsAcmeHostContext hostContext;
    @Mock
    private CertificateRenewalScheduler scheduler;

    private DsTlsAcmeCertificateRenewalWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(globalConfProvider.isValid()).thenReturn(true);
        lenient().when(hostContext.requiresValidGlobalConf()).thenReturn(true);
        lenient().when(dsTlsCertificateService.recordAcmeOutcome(any())).thenReturn(true);
        worker = new DsTlsAcmeCertificateRenewalWorker(globalConfProvider, dsTlsCertificateService, dsTlsAcmeService, hostContext);
    }

    @Test
    void executeShouldPauseNotFailWhenGlobalConfIsInvalid() {
        when(globalConfProvider.isValid()).thenReturn(false);

        worker.execute(scheduler);

        verify(scheduler).globalConfInvalidated();
        verify(scheduler, never()).success();
        verify(scheduler, never()).failure();
        verify(hostContext).requiresValidGlobalConf();
        verifyNoMoreInteractions(hostContext);
        verifyNoInteractions(dsTlsCertificateService, dsTlsAcmeService);
    }

    @Test
    void executeShouldProceedWhenGlobalConfIsInvalidButHostContextDoesNotRequireIt() {
        // requiresValidGlobalConf() short-circuits the gate, so isValid() is never even consulted - stubbed
        // leniently only to document that its value genuinely doesn't matter here.
        lenient().when(globalConfProvider.isValid()).thenReturn(false);
        when(hostContext.requiresValidGlobalConf()).thenReturn(false);
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(false, null));

        worker.execute(scheduler);

        verify(scheduler, never()).globalConfInvalidated();
        verify(scheduler).success();
        verify(scheduler, never()).failure();
    }

    @Test
    void executeShouldSkipWithoutAnyBookkeepingWhenNoCertificateIsStored() {
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, null));

        worker.execute(scheduler);

        verify(dsTlsCertificateService, never()).recordAcmeOutcome(any());
        verify(dsTlsCertificateService, never()).storeAcmeEnrolledCertificate(any(), any(), any());
        verify(hostContext, never()).getDsTlsCertificationAuthorities();
        verify(hostContext, never()).notifyEnrollmentSuccess(any(), anyBoolean());
        verify(hostContext, never()).notifyEnrollmentFailure(any(), any());
        verifyNoInteractions(dsTlsAcmeService);
        verify(scheduler).success();
        verify(scheduler, never()).failure();
    }

    @Test
    void executeShouldSkipWithoutBookkeepingWhenTheIssuerIsNotADesignatedCa() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        X509Certificate currentCertificate = certificateSignedBy(generateRsaKeyPair(), "CN=Unrelated CA",
                "CN=" + HOSTNAME, generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(dsTlsCaInfo(caCert, CA_NAME, CA_URL)));

        worker.execute(scheduler);

        verify(dsTlsCertificateService, never()).recordAcmeOutcome(any());
        verify(dsTlsAcmeService, never()).getNextRenewalTime(any(), any());
        verify(hostContext, never()).notifyEnrollmentFailure(any(), any());
        verify(scheduler).success();
    }

    @Test
    void executeShouldSkipWithoutBookkeepingWhenTheIssuingCaHasNoAcmeServer() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME,
                "CN=" + HOSTNAME, generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(dsTlsCaInfo(caCert, CA_NAME, null)));

        worker.execute(scheduler);

        verify(dsTlsCertificateService, never()).recordAcmeOutcome(any());
        verify(dsTlsAcmeService, never()).getNextRenewalTime(any(), any());
        verify(scheduler).success();
    }

    @Test
    void executeShouldSkipPreservingAPriorErrorWhenNotYetDue() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME,
                "CN=" + HOSTNAME, generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate)))
                .thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).renew(any(), any(), any(), any());
        verify(dsTlsCertificateService, never()).storeAcmeEnrolledCertificate(any(), any(), any());
        verify(dsTlsCertificateService, never()).recordAcmeOutcome(any());
        verify(hostContext, never()).notifyEnrollmentSuccess(any(), anyBoolean());
        verify(hostContext, never()).notifyEnrollmentFailure(any(), any());
        verify(scheduler).success();
    }

    @Test
    void executeShouldRenewFromTheIssuingCaCopyingSubjectAndFirstSan() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);

        String subjectDn = "CN=" + HOSTNAME + ",OU=Dataspace,O=Example Org,C=US";
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, subjectDn,
                generateRsaKeyPair(), HOSTNAME, "extra.example.org");

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));

        X509Certificate newCert = selfSignedCertificate(generateRsaKeyPair(), "CN=" + HOSTNAME);
        when(dsTlsAcmeService.renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any())).thenReturn(List.of(newCert));
        Instant nextRenewal = Instant.now().plus(60, ChronoUnit.DAYS);
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(newCert))).thenReturn(nextRenewal);

        worker.execute(scheduler);

        ArgumentCaptor<byte[]> certRequestCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(dsTlsAcmeService).renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), certRequestCaptor.capture());

        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(certRequestCaptor.getValue());
        X500Principal csrSubject = new X500Principal(csr.getSubject().getEncoded());
        assertThat(csrSubject.getName()).isEqualTo(currentCertificate.getSubjectX500Principal().getName());

        verify(dsTlsCertificateService).storeAcmeEnrolledCertificate(any(), eq(new X509Certificate[]{newCert}), eq(nextRenewal));
        verify(hostContext).notifyEnrollmentSuccess(HOSTNAME, true);
        verify(scheduler).success();
        verify(scheduler, never()).failure();
    }

    @Test
    void executeShouldFallBackToThePublicHostnameWhenTheCertificateHasNoSan() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, "CN=" + HOSTNAME,
                generateRsaKeyPair());

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));
        when(hostContext.getPublicHostname()).thenReturn(HOSTNAME);

        X509Certificate newCert = selfSignedCertificate(generateRsaKeyPair(), "CN=" + HOSTNAME);
        when(dsTlsAcmeService.renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any())).thenReturn(List.of(newCert));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(newCert))).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService).renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any());
        verify(hostContext).notifyEnrollmentSuccess(HOSTNAME, true);
        verify(scheduler).success();
    }

    @Test
    void executeShouldRenewFromTheMatchingCaWhenSeveralAcmeCapableCasAreDesignated() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo matchingCa = dsTlsCaInfo(caCert, CA_NAME, CA_URL);

        X509Certificate otherCaCert = selfSignedCertificate(generateRsaKeyPair(), "CN=Other CA");
        ApprovedDsTlsCaInfo otherCa = dsTlsCaInfo(otherCaCert, "Other CA", "http://otherca:8887");

        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, "CN=" + HOSTNAME,
                generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(otherCa, matchingCa));
        when(dsTlsAcmeService.getNextRenewalTime(eq(matchingCa), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));

        X509Certificate newCert = selfSignedCertificate(generateRsaKeyPair(), "CN=" + HOSTNAME);
        when(dsTlsAcmeService.renew(eq(matchingCa), eq(HOSTNAME), eq(currentCertificate), any())).thenReturn(List.of(newCert));
        when(dsTlsAcmeService.getNextRenewalTime(eq(matchingCa), eq(newCert))).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService).renew(eq(matchingCa), eq(HOSTNAME), eq(currentCertificate), any());
        verify(dsTlsAcmeService, never()).renew(eq(otherCa), any(), any(), any());
        verify(dsTlsCertificateService, never()).recordAcmeOutcome(anyString());
        verify(scheduler).success();
    }

    @Test
    void executeShouldGenerateAFreshKeyPairForEveryRenewal() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, "CN=" + HOSTNAME,
                generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));

        X509Certificate newCert = selfSignedCertificate(generateRsaKeyPair(), "CN=" + HOSTNAME);
        when(dsTlsAcmeService.renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any())).thenReturn(List.of(newCert));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(newCert))).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);
        worker.execute(scheduler);

        ArgumentCaptor<PrivateKey> keyCaptor = ArgumentCaptor.forClass(PrivateKey.class);
        verify(dsTlsCertificateService, times(2)).storeAcmeEnrolledCertificate(keyCaptor.capture(), any(), any());
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void executeShouldRecordAndNotifyOnRenewalFailure() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, "CN=" + HOSTNAME,
                generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));
        when(dsTlsAcmeService.renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any()))
                .thenThrow(new IllegalStateException("CA unreachable"));

        worker.execute(scheduler);

        verify(dsTlsCertificateService, never()).storeAcmeEnrolledCertificate(any(), any(), any());
        verify(dsTlsCertificateService).recordAcmeOutcome("CA unreachable");
        verify(hostContext).notifyEnrollmentFailure(HOSTNAME, "CA unreachable");
        verify(scheduler).failure();
        verify(scheduler, never()).success();
    }

    @Test
    void executeShouldNotSendASecondFailureNotificationWhenTheErrorIsUnchanged() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, "CN=" + HOSTNAME,
                generateRsaKeyPair(), HOSTNAME);

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));
        when(dsTlsAcmeService.renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any()))
                .thenThrow(new IllegalStateException("CA unreachable"));
        when(dsTlsCertificateService.recordAcmeOutcome("CA unreachable")).thenReturn(false);

        worker.execute(scheduler);

        verify(dsTlsCertificateService).recordAcmeOutcome("CA unreachable");
        verify(hostContext, never()).notifyEnrollmentFailure(any(), any());
        verify(scheduler).failure();
    }

    @Test
    void executeShouldNotifyUsingTheConfiguredHostnameSourceWhenNoSanAndHostnameResolutionFails() throws Exception {
        KeyPair caKeyPair = generateRsaKeyPair();
        X509Certificate caCert = selfSignedCertificate(caKeyPair, "CN=" + CA_NAME);
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo(caCert, CA_NAME, CA_URL);
        X509Certificate currentCertificate = certificateSignedBy(caKeyPair, "CN=" + CA_NAME, "CN=" + HOSTNAME,
                generateRsaKeyPair());

        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(hostContext.getDsTlsCertificationAuthorities()).thenReturn(List.of(caInfo));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));
        when(hostContext.getPublicHostname()).thenThrow(new IllegalArgumentException("bad hostname"));
        when(hostContext.getConfiguredHostnameSource()).thenReturn("https://");

        worker.execute(scheduler);

        verify(dsTlsCertificateService, never()).storeAcmeEnrolledCertificate(any(), any(), any());
        verify(dsTlsCertificateService).recordAcmeOutcome(anyString());
        verify(hostContext).notifyEnrollmentFailure(eq("https://"), anyString());
        verify(scheduler).failure();
    }

    private static ApprovedDsTlsCaInfo dsTlsCaInfo(X509Certificate caCert, String name, String acmeServerDirectoryUrl) {
        return new ApprovedDsTlsCaInfo(name, caCert, List.of(), acmeServerDirectoryUrl, null, null);
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair, String subjectDn) throws Exception {
        return certificateSignedBy(keyPair, subjectDn, subjectDn, keyPair);
    }

    private static X509Certificate certificateSignedBy(KeyPair issuerKeyPair, String issuerDn, String subjectDn,
                                                        KeyPair subjectKeyPair, String... dnsSans) throws Exception {
        X500Name issuer = new X500Name(issuerDn);
        X500Name subject = new X500Name(subjectDn);
        var certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                subjectKeyPair.getPublic());
        if (dnsSans.length > 0) {
            GeneralName[] names = new GeneralName[dnsSans.length];
            for (int i = 0; i < dnsSans.length; i++) {
                names[i] = new GeneralName(GeneralName.dNSName, dnsSans[i]);
            }
            certBuilder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }
}
