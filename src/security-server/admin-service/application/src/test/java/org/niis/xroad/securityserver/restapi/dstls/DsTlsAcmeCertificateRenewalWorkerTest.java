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
package org.niis.xroad.securityserver.restapi.dstls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
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
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DsTlsAcmeCertificateRenewalWorkerTest {

    private static final String HOSTNAME = "ds.example.org";

    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspace;
    @Mock
    private DsTlsCertificateService dsTlsCertificateService;
    @Mock
    private DsTlsAcmeService dsTlsAcmeService;
    @Mock
    private MailNotificationHelper mailNotificationHelper;
    @Mock
    private CertificateRenewalScheduler scheduler;

    private DsTlsAcmeCertificateRenewalWorker worker;

    @BeforeEach
    void setUp() {
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        lenient().when(globalConfProvider.isValid()).thenReturn(true);
        lenient().when(globalConfProvider.getInstanceIdentifier()).thenReturn("DEV");
        lenient().when(dsTlsCertificateService.recordAcmeOutcome(any())).thenReturn(true);
        worker = new DsTlsAcmeCertificateRenewalWorker(globalConfProvider, adminServiceProperties, dsTlsCertificateService,
                dsTlsAcmeService, mailNotificationHelper);
    }

    @Test
    void executeShouldPauseNotFailWhenGlobalConfIsInvalid() {
        when(globalConfProvider.isValid()).thenReturn(false);

        worker.execute(scheduler);

        verify(scheduler).globalConfInvalidated();
        verify(scheduler, never()).success();
        verify(scheduler, never()).failure();
        verifyNoInteractions(dsTlsCertificateService, dsTlsAcmeService);
    }

    @Test
    void executeShouldSkipAndSuspendSchedulingWhenDataSpaceIsNotEnabled() {
        when(dataspace.getIdentityHubUrl()).thenReturn("");

        worker.execute(scheduler);

        verify(dsTlsCertificateService).suspendAcmeScheduling();
        verify(scheduler).success();
        verify(scheduler, never()).failure();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void executeShouldSkipAndSuspendSchedulingWhenIdentityHubUrlIsBlank() {
        when(dataspace.getIdentityHubUrl()).thenReturn("   ");

        worker.execute(scheduler);

        verify(dsTlsCertificateService).suspendAcmeScheduling();
        verify(scheduler).success();
    }

    @Test
    void executeShouldRecordAnErrorAndFailWhenTheHostnameIsMalformed() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://");

        worker.execute(scheduler);

        verify(dsTlsCertificateService).recordAcmeOutcome(anyString());
        verify(dsTlsCertificateService, never()).suspendAcmeScheduling();
        verify(mailNotificationHelper).sendDsTlsAcmeFailureNotification(eq("https://"), anyString());
        verify(scheduler).failure();
        verify(scheduler, never()).success();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void executeShouldNotSendAFailureNotificationWhenTheMalformedHostnameErrorIsUnchanged() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://");
        when(dsTlsCertificateService.recordAcmeOutcome(anyString())).thenReturn(false);

        worker.execute(scheduler);

        verify(mailNotificationHelper, never()).sendDsTlsAcmeFailureNotification(any(), any());
        verify(scheduler).failure();
    }

    @Test
    void executeShouldSkipWithoutRecordingAnErrorWhenNoAcmeCapableCaIsDesignated() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(
                dsTlsCaInfo("Manual-only CA", null)));

        worker.execute(scheduler);

        verify(dsTlsCertificateService).recordAcmeOutcome(null);
        verify(dsTlsCertificateService, never()).suspendAcmeScheduling();
        verify(scheduler).success();
        verify(scheduler, never()).failure();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void executeShouldFailClosedWhenMoreThanOneAcmeCapableCaIsDesignated() {
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(
                dsTlsCaInfo("CA one", "http://ca-one:8887"),
                dsTlsCaInfo("CA two", "http://ca-two:8887")));

        worker.execute(scheduler);

        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(dsTlsCertificateService).recordAcmeOutcome(errorCaptor.capture());
        assertThat(errorCaptor.getValue()).isNotBlank();
        verify(mailNotificationHelper).sendDsTlsAcmeFailureNotification(eq(HOSTNAME), eq(errorCaptor.getValue()));
        verify(scheduler).failure();
        verify(scheduler, never()).success();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void executeShouldEnrollAFreshCertificateWhenNoneIsStoredYet() throws Exception {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(caInfo));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(false, null));

        X509Certificate newCert = selfSignedCertificate(generateRsaKeyPair());
        when(dsTlsAcmeService.enroll(eq(caInfo), eq(HOSTNAME), any())).thenReturn(List.of(newCert));
        Instant nextRenewal = Instant.now().plus(60, ChronoUnit.DAYS);
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), any())).thenReturn(nextRenewal);

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).renew(any(), any(), any(), any());
        ArgumentCaptor<byte[]> certRequestCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(dsTlsAcmeService).enroll(eq(caInfo), eq(HOSTNAME), certRequestCaptor.capture());
        assertThat(certRequestCaptor.getValue()).isNotEmpty();

        verify(dsTlsCertificateService).storeAcmeEnrolledCertificate(any(), eq(new X509Certificate[]{newCert}), eq(nextRenewal));
        verify(mailNotificationHelper).sendDsTlsAcmeSuccessNotification(HOSTNAME, false);
        verify(scheduler).success();
        verify(scheduler, never()).failure();
    }

    @Test
    void executeShouldTransparentlyReplaceAManuallyUploadedCertificateWhenDueForRenewal() throws Exception {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(caInfo));

        X509Certificate currentCertificate = selfSignedCertificate(generateRsaKeyPair());
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        // Due now: the manual certificate's own enrollment method is irrelevant to the renewal decision.
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().minusSeconds(1));

        X509Certificate newCert = selfSignedCertificate(generateRsaKeyPair());
        when(dsTlsAcmeService.renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any())).thenReturn(List.of(newCert));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(newCert))).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).enroll(any(), any(), any());
        verify(dsTlsAcmeService).renew(eq(caInfo), eq(HOSTNAME), eq(currentCertificate), any());
        verify(dsTlsCertificateService).storeAcmeEnrolledCertificate(any(), eq(new X509Certificate[]{newCert}), any());
        verify(mailNotificationHelper).sendDsTlsAcmeSuccessNotification(HOSTNAME, true);
        verify(scheduler).success();
    }

    @Test
    void executeShouldDoNothingWhenTheCurrentCertificateIsNotYetDue() throws Exception {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(caInfo));

        X509Certificate currentCertificate = selfSignedCertificate(generateRsaKeyPair());
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(true, currentCertificate));
        when(dsTlsAcmeService.getNextRenewalTime(eq(caInfo), eq(currentCertificate))).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).enroll(any(), any(), any());
        verify(dsTlsAcmeService, never()).renew(any(), any(), any(), any());
        verify(dsTlsCertificateService, never()).storeAcmeEnrolledCertificate(any(), any(), any());
        verify(dsTlsCertificateService).recordAcmeOutcome(null);
        verifyNoInteractions(mailNotificationHelper);
        verify(scheduler).success();
    }

    @Test
    void executeShouldRecordTheErrorAndFailWithoutTouchingTheServedCertificateWhenEnrollmentFails() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(caInfo));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(false, null));
        when(dsTlsAcmeService.enroll(any(), any(), any())).thenThrow(new IllegalStateException("CA unreachable"));

        worker.execute(scheduler);

        verify(dsTlsCertificateService, never()).storeAcmeEnrolledCertificate(any(), any(), any());
        verify(dsTlsCertificateService).recordAcmeOutcome("CA unreachable");
        verify(mailNotificationHelper).sendDsTlsAcmeFailureNotification(HOSTNAME, "CA unreachable");
        verify(scheduler).failure();
        verify(scheduler, never()).success();
    }

    @Test
    void executeShouldNotSendASecondFailureNotificationWhenTheErrorIsUnchanged() {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(caInfo));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(false, null));
        when(dsTlsAcmeService.enroll(any(), any(), any())).thenThrow(new IllegalStateException("CA unreachable"));
        when(dsTlsCertificateService.recordAcmeOutcome("CA unreachable")).thenReturn(false);

        worker.execute(scheduler);

        verify(dsTlsCertificateService).recordAcmeOutcome("CA unreachable");
        verify(mailNotificationHelper, never()).sendDsTlsAcmeFailureNotification(any(), any());
        verify(scheduler).failure();
    }

    @Test
    void executeShouldGenerateAFreshKeyPairForEveryEnrollment() throws Exception {
        ApprovedDsTlsCaInfo caInfo = dsTlsCaInfo("Test CA", "http://testca:8887");
        when(dataspace.getIdentityHubUrl()).thenReturn("https://" + HOSTNAME + ":7182");
        when(globalConfProvider.getApprovedDsTlsCas("DEV")).thenReturn(List.of(caInfo));
        when(dsTlsCertificateService.getStatus()).thenReturn(new DsTlsCertificateStatus(false, null));
        when(dsTlsAcmeService.enroll(any(), any(), any())).thenReturn(List.of(selfSignedCertificate(generateRsaKeyPair())));
        when(dsTlsAcmeService.getNextRenewalTime(any(), any())).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);
        worker.execute(scheduler);

        ArgumentCaptor<java.security.PrivateKey> keyCaptor = ArgumentCaptor.forClass(java.security.PrivateKey.class);
        verify(dsTlsCertificateService, times(2)).storeAcmeEnrolledCertificate(keyCaptor.capture(), any(), any());
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    private static ApprovedDsTlsCaInfo dsTlsCaInfo(String name, String acmeServerDirectoryUrl) {
        return new ApprovedDsTlsCaInfo(name, null, List.of(), acmeServerDirectoryUrl, null, null);
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair) throws Exception {
        X500Name subject = new X500Name("CN=ds-tls-test");
        var certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
    }
}
