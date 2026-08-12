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
package org.niis.xroad.cs.admin.core.acme;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.niis.xroad.common.acme.testsupport.AcmeTestFixtures;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.dto.DsTlsCa;
import org.niis.xroad.cs.admin.api.service.DsTlsCasService;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificateService;
import org.niis.xroad.cs.admin.core.dataspace.DataspaceIssuerProperties;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_FOUND;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DsTlsAcmeEnrollmentWorkerTest {

    private static final String HOSTNAME = "cs1.example.org";
    private static final String CA_NAME = "test-ds-tls-ca";
    private static final String DIRECTORY_URL = "https://ca.example.org/directory";

    @Mock
    private DataspaceIssuerProperties dataspaceIssuerProperties;
    @Mock
    private DsTlsCasService dsTlsCasService;
    @Mock
    private DsTlsAcmeService dsTlsAcmeService;
    @Mock
    private DsTlsCertificateService dsTlsCertificateService;
    @Mock
    private AcmeConfig acmeConfig;
    @Mock
    private DsTlsAcmeEnrollmentScheduler scheduler;

    private DsTlsAcmeEnrollmentWorker worker;

    @BeforeEach
    void setUp() {
        worker = new DsTlsAcmeEnrollmentWorker(dataspaceIssuerProperties, dsTlsCasService, dsTlsAcmeService,
                dsTlsCertificateService, acmeConfig);
        when(dataspaceIssuerProperties.getHost()).thenReturn(HOSTNAME);
    }

    @Test
    void skipsQuietlyWhenIssuerHostIsBlank() {
        when(dataspaceIssuerProperties.getHost()).thenReturn(" ");

        worker.execute(scheduler);

        verify(scheduler).success();
        verifyNoInteractions(dsTlsAcmeService, dsTlsCasService);
    }

    @Test
    void skipsQuietlyWhenNoAcmeCapableCaIsDesignated() {
        when(dsTlsCasService.findAll()).thenReturn(List.of(caWithoutAcme()));

        worker.execute(scheduler);

        verify(scheduler).success();
        verifyNoInteractions(dsTlsAcmeService);
    }

    @Test
    void enrollsFreshWhenNoCertificateIsStoredYet() throws Exception {
        DsTlsCa ca = caWithAcme();
        when(dsTlsCasService.findAll()).thenReturn(List.of(ca));
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
        verify(dsTlsCertificateService).recordAcmeOutcome(null);
        verify(scheduler).success();
    }

    @Test
    void renewsWhenCurrentCertificateIsDueAndSkipsWhenNotDue() throws Exception {
        DsTlsCa ca = caWithAcme();
        when(dsTlsCasService.findAll()).thenReturn(List.of(ca));
        X509Certificate current = selfSignedTestCert();
        when(dsTlsCertificateService.getDataspaceTlsCertificate()).thenReturn(current);
        when(dsTlsAcmeService.hasRenewalInfo(ca)).thenReturn(true);
        when(dsTlsAcmeService.isRenewalRequired(ca, current)).thenReturn(false);

        worker.execute(scheduler);

        verify(dsTlsAcmeService, never()).renew(any(), anyString(), any(), any());
        verify(dsTlsAcmeService, never()).orderCertificate(any(), anyString(), any());
        verify(scheduler).success();
    }

    @Test
    void reEnrollsViaAcmeWhenRenewalIsDueEvenIfCurrentCertWasManuallyUploaded() throws Exception {
        DsTlsCa ca = caWithAcme();
        when(dsTlsCasService.findAll()).thenReturn(List.of(ca));
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
        verify(scheduler).success();
    }

    @Test
    void recordsFailureAndDedupesUnchangedErrorOnTheNextTick() throws Exception {
        DsTlsCa ca = caWithAcme();
        when(dsTlsCasService.findAll()).thenReturn(List.of(ca));
        when(dsTlsCertificateService.getDataspaceTlsCertificate())
                .thenThrow(new NotFoundException(DS_TLS_CERTIFICATE_NOT_FOUND.build()));
        when(dsTlsAcmeService.generateKeyPair()).thenThrow(new IllegalStateException("acme server unreachable"));

        worker.execute(scheduler);
        verify(scheduler).failure();
        ArgumentCaptor<String> errorCaptor = ArgumentCaptor.forClass(String.class);
        verify(dsTlsCertificateService).recordAcmeOutcome(errorCaptor.capture());
        assertThat(errorCaptor.getValue()).isEqualTo("acme server unreachable");
    }

    private static DsTlsCa caWithAcme() {
        return new DsTlsCa().setName(CA_NAME).setAcmeServerDirectoryUrl(DIRECTORY_URL);
    }

    private static DsTlsCa caWithoutAcme() {
        return new DsTlsCa().setName(CA_NAME);
    }

    private static X509Certificate selfSignedTestCert() throws Exception {
        return AcmeTestFixtures.selfSignedCertificate(AcmeTestFixtures.rsaKeyPair(), HOSTNAME,
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(89, ChronoUnit.DAYS));
    }
}
