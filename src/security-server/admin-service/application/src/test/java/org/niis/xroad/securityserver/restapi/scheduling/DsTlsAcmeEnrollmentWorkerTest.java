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
package org.niis.xroad.securityserver.restapi.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.acme.testsupport.AcmeTestFixtures;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;
import org.niis.xroad.securityserver.restapi.acme.AcmeConfig;
import org.niis.xroad.securityserver.restapi.acme.DsTlsAcmeService;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.service.DsTlsCertificateService;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_FOUND;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DsTlsAcmeEnrollmentWorkerTest {

    private static final String HOSTNAME = "ss1.example.org";
    private static final String CA_NAME = "test-ds-tls-ca";
    private static final String DIRECTORY_URL = "https://ca.example.org/directory";

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private GlobalConfProvider globalConfProvider;
    @Mock
    private DsTlsAcmeService dsTlsAcmeService;
    @Mock
    private DsTlsCertificateService dsTlsCertificateService;
    @Mock
    private AcmeConfig acmeConfig;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ScheduledJobHelper scheduledJobHelper;
    @Mock
    private MailNotificationHelper mailNotificationHelper;
    @Mock
    private DsTlsAcmeEnrollmentScheduler scheduler;

    private DsTlsAcmeEnrollmentWorker worker;
    private AdminServiceProperties.Dataspace dataspace;

    @BeforeEach
    void setUp() {
        worker = new DsTlsAcmeEnrollmentWorker(adminServiceProperties, globalConfProvider, dsTlsAcmeService,
                dsTlsCertificateService, acmeConfig, scheduledJobHelper, mailNotificationHelper);
        dataspace = new AdminServiceProperties.Dataspace();
        when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        when(globalConfProvider.isValid()).thenReturn(true);
        when(dsTlsCertificateService.recordAcmeOutcome(any())).thenReturn(true);
    }

    @Test
    void skipsAndRecoversWhenGlobalConfIsInvalid() {
        when(globalConfProvider.isValid()).thenReturn(false);

        worker.execute(scheduler);

        verify(scheduler).globalConfInvalidated();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void skipsQuietlyWhenDataspaceFeatureIsNotEnabled() {
        dataspace.setIdentityHubUrl("");

        worker.execute(scheduler);

        verify(scheduler).success();
        verifyNoInteractions(dsTlsAcmeService);
        verifyNoInteractions(mailNotificationHelper);
    }

    @Test
    void routesMalformedHostnameConfigurationToFailurePathWithoutEscaping() {
        dataspace.setIdentityHubUrl("https://[not-a-valid-host");

        worker.execute(scheduler);

        verify(scheduler).failure();
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(dsTlsCertificateService).recordAcmeOutcome(errorCaptor.capture());
        assertThat(errorCaptor.getValue()).isNotNull();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void skipsQuietlyWhenNoAcmeCapableCaIsDesignated() {
        dataspace.setIdentityHubUrl("https://" + HOSTNAME + ":7183");
        when(globalConfProvider.getApprovedDsTlsCas(any())).thenReturn(List.of(caWithoutAcme()));

        worker.execute(scheduler);

        verify(scheduler).success();
        verifyNoInteractions(dsTlsAcmeService);
        verifyNoInteractions(mailNotificationHelper);
    }

    @Test
    void enrollsFreshWhenNoCertificateIsStoredYet() throws Exception {
        dataspace.setIdentityHubUrl("https://" + HOSTNAME + ":7183");
        ApprovedDsTlsCaInfo ca = caWithAcme();
        when(globalConfProvider.getApprovedDsTlsCas(any())).thenReturn(List.of(ca));
        when(dsTlsCertificateService.getDataspaceTlsCertificate())
                .thenThrow(new NotFoundException(DS_TLS_CERTIFICATE_NOT_FOUND.build()));
        KeyPair keyPair = AcmeTestFixtures.rsaKeyPair();
        X509Certificate issued = selfSignedTestCert();
        when(dsTlsAcmeService.generateKeyPair()).thenReturn(keyPair);
        when(dsTlsAcmeService.generateCsr(keyPair, HOSTNAME)).thenReturn(new byte[]{1, 2, 3});
        when(dsTlsAcmeService.orderCertificate(ca, HOSTNAME, new byte[]{1, 2, 3})).thenReturn(List.of(issued));
        when(dsTlsAcmeService.getNextRenewalTime(eq(ca), any())).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService).checkAccountKeyPairAndRenewIfNecessary(ca);
        verify(dsTlsAcmeService, never()).renew(any(), anyString(), any(), any());
        verify(dsTlsCertificateService).storeAcmeEnrolledCertificate(eq(keyPair.getPrivate()), any(), any());
        verify(mailNotificationHelper).sendDsTlsAcmeSuccessNotification(any(), any(), eq(true));
        verify(scheduler).success();
    }

    @Test
    void renewsWhenCurrentCertificateIsDueAndSkipsWhenNotDue() throws Exception {
        dataspace.setIdentityHubUrl("https://" + HOSTNAME + ":7183");
        ApprovedDsTlsCaInfo ca = caWithAcme();
        when(globalConfProvider.getApprovedDsTlsCas(any())).thenReturn(List.of(ca));
        X509Certificate current = selfSignedTestCert();
        when(dsTlsCertificateService.getDataspaceTlsCertificate()).thenReturn(current);
        when(dsTlsAcmeService.hasRenewalInfo(ca)).thenReturn(true);
        when(dsTlsAcmeService.isRenewalRequired(ca, current)).thenReturn(false);

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).renew(any(), anyString(), any(), any());
        verify(dsTlsAcmeService, never()).orderCertificate(any(), anyString(), any());
        verifyNoInteractions(mailNotificationHelper);
        verify(scheduler).success();
    }

    @Test
    void reEnrollsViaAcmeWhenRenewalIsDueEvenIfCurrentCertWasManuallyUploaded() throws Exception {
        dataspace.setIdentityHubUrl("https://" + HOSTNAME + ":7183");
        ApprovedDsTlsCaInfo ca = caWithAcme();
        when(globalConfProvider.getApprovedDsTlsCas(any())).thenReturn(List.of(ca));
        X509Certificate current = selfSignedTestCert();
        when(dsTlsCertificateService.getDataspaceTlsCertificate()).thenReturn(current);
        when(dsTlsAcmeService.hasRenewalInfo(ca)).thenReturn(true);
        when(dsTlsAcmeService.isRenewalRequired(ca, current)).thenReturn(true);
        KeyPair keyPair = AcmeTestFixtures.rsaKeyPair();
        X509Certificate renewed = selfSignedTestCert();
        when(dsTlsAcmeService.generateKeyPair()).thenReturn(keyPair);
        when(dsTlsAcmeService.generateCsr(keyPair, HOSTNAME)).thenReturn(new byte[]{4, 5, 6});
        when(dsTlsAcmeService.renew(ca, HOSTNAME, current, new byte[]{4, 5, 6})).thenReturn(List.of(renewed));
        when(dsTlsAcmeService.getNextRenewalTime(eq(ca), any())).thenReturn(Instant.now().plus(60, ChronoUnit.DAYS));

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).orderCertificate(any(), anyString(), any());
        verify(dsTlsCertificateService).storeAcmeEnrolledCertificate(eq(keyPair.getPrivate()), any(), any());
        verify(mailNotificationHelper).sendDsTlsAcmeSuccessNotification(any(), any(), eq(false));
        verify(scheduler).success();
    }

    @Test
    void notifiesOnFailureAndDedupesUnchangedErrorOnTheNextTick() throws Exception {
        dataspace.setIdentityHubUrl("https://" + HOSTNAME + ":7183");
        ApprovedDsTlsCaInfo ca = caWithAcme();
        when(globalConfProvider.getApprovedDsTlsCas(any())).thenReturn(List.of(ca));
        when(dsTlsCertificateService.getDataspaceTlsCertificate())
                .thenThrow(new NotFoundException(DS_TLS_CERTIFICATE_NOT_FOUND.build()));
        when(dsTlsAcmeService.generateKeyPair()).thenThrow(new IllegalStateException("acme server unreachable"));

        when(dsTlsCertificateService.recordAcmeOutcome(anyString())).thenReturn(true);
        worker.execute(scheduler);
        verify(scheduler).failure();
        verify(mailNotificationHelper, times(1)).sendDsTlsAcmeFailureNotification(any(), any(), anyString());

        when(dsTlsCertificateService.recordAcmeOutcome(anyString())).thenReturn(false);
        worker.execute(scheduler);
        verify(mailNotificationHelper, times(1)).sendDsTlsAcmeFailureNotification(any(), any(), anyString());
    }

    private static ApprovedDsTlsCaInfo caWithAcme() {
        return new ApprovedDsTlsCaInfo(CA_NAME, new byte[0], List.of(), DIRECTORY_URL, null);
    }

    private static ApprovedDsTlsCaInfo caWithoutAcme() {
        return new ApprovedDsTlsCaInfo(CA_NAME, new byte[0], List.of(), null, null);
    }

    private static X509Certificate selfSignedTestCert() throws Exception {
        return AcmeTestFixtures.selfSignedCertificate(AcmeTestFixtures.rsaKeyPair(), HOSTNAME,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(89, ChronoUnit.DAYS));
    }
}
