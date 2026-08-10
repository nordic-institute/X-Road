/*
 * The MIT License
 *
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

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.TimeUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.validator.DsTlsMaterialValidator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;
import static org.niis.xroad.securityserver.restapi.service.DsTlsCertificateService.EnrollmentStatusView.EnrollmentMethod;

@ExtendWith(MockitoExtension.class)
class DsTlsCertificateServiceTest {

    @Mock
    private VaultClient vaultClient;
    @Mock
    private DsTlsMaterialValidator dsTlsMaterialValidator;
    @Mock
    private AuditDataHelper auditDataHelper;

    private DsTlsCertificateService service;

    @BeforeEach
    void setUp() {
        TimeUtils.setClock(Clock.systemDefaultZone());
        service = new DsTlsCertificateService(vaultClient, dsTlsMaterialValidator, auditDataHelper);
    }

    @AfterEach
    void tearDown() {
        TimeUtils.setClock(Clock.systemDefaultZone());
    }

    @Test
    void getDataspaceTlsCertificateShouldReturnLeafCertificate() throws Exception {
        X509Certificate certificate = selfSignedCertificate();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(null, new X509Certificate[]{certificate}));

        assertThat(service.getDataspaceTlsCertificate()).isEqualTo(certificate);
    }

    @Test
    void getDataspaceTlsCertificateShouldThrowNotFoundWhenSlotIsEmpty() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenThrow(XrdRuntimeException.systemException(MISSING_SECRET).build());

        assertThatThrownBy(() -> service.getDataspaceTlsCertificate())
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getDataspaceTlsCertificateShouldPropagateOtherVaultRuntimeFailures() throws Exception {
        var otherFailure = XrdRuntimeException.systemException(INTERNAL_ERROR).build();
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(otherFailure);

        assertThatThrownBy(() -> service.getDataspaceTlsCertificate())
                .isSameAs(otherFailure);
    }

    @Test
    void uploadDataspaceTlsCertificateShouldStoreValidatedMaterialAndAuditTheHash() throws Exception {
        X509Certificate certificate = selfSignedCertificate();
        KeyPair keyPair = generateKeyPair();
        InternalSSLKey validated = new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{certificate});
        byte[] keyBytes = "key".getBytes();
        byte[] certBytes = "cert".getBytes();
        when(dsTlsMaterialValidator.validate(keyBytes, certBytes)).thenReturn(validated);

        var uploaded = service.uploadDataspaceTlsCertificate(keyBytes, certBytes);

        verify(vaultClient).createDsHttpsTlsCredentials(validated, DsTlsEnrollmentMethod.MANUAL);
        verify(vaultClient).setDsHttpsTlsEnrollmentStatus(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, null, null));
        verify(auditDataHelper).putCertificateHash(certificate);
        assertThat(uploaded).isEqualTo(certificate);
    }

    @Test
    void getEnrollmentStatusShouldReturnNoneWhenNoCertificateIsStored() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenThrow(XrdRuntimeException.systemException(MISSING_SECRET).build());

        var status = service.getEnrollmentStatus();

        assertThat(status.enrollmentMethod()).isEqualTo(EnrollmentMethod.NONE);
        assertThat(status.nextRenewalTime()).isNull();
        assertThat(status.lastError()).isNull();
    }

    @Test
    void getEnrollmentStatusShouldDefaultToManualWhenCertificateExistsButNoStatusWasEverRecorded() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(null, new X509Certificate[]{selfSignedCertificate()}));
        when(vaultClient.getDsHttpsTlsEnrollmentStatus()).thenReturn(Optional.empty());

        var status = service.getEnrollmentStatus();

        assertThat(status.enrollmentMethod()).isEqualTo(EnrollmentMethod.MANUAL);
    }

    @Test
    void getEnrollmentStatusShouldReturnRecordedAcmeState() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(null, new X509Certificate[]{selfSignedCertificate()}));
        Instant nextRenewal = Instant.now().plus(30, ChronoUnit.DAYS);
        when(vaultClient.getDsHttpsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewal, "boom")));

        var status = service.getEnrollmentStatus();

        assertThat(status.enrollmentMethod()).isEqualTo(EnrollmentMethod.ACME);
        assertThat(status.nextRenewalTime()).isEqualTo(nextRenewal);
        assertThat(status.lastError()).isEqualTo("boom");
    }

    @Test
    void storeAcmeEnrolledCertificateShouldTagAcmeAndClearAnyPriorError() throws Exception {
        KeyPair keyPair = generateKeyPair();
        X509Certificate[] chain = {selfSignedCertificate()};
        Instant nextRenewal = Instant.now().plus(60, ChronoUnit.DAYS);

        service.storeAcmeEnrolledCertificate(keyPair.getPrivate(), chain, nextRenewal);

        verify(vaultClient).createDsHttpsTlsCredentials(any(InternalSSLKey.class), eq(DsTlsEnrollmentMethod.ACME));
        verify(vaultClient).setDsHttpsTlsEnrollmentStatus(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewal, null));
    }

    @Test
    void recordAcmeOutcomeShouldPreserveMethodAndRenewalTimeWhileUpdatingError() {
        Instant nextRenewal = Instant.now().plus(10, ChronoUnit.DAYS);
        when(vaultClient.getDsHttpsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewal, null)));

        boolean changed = service.recordAcmeOutcome("order failed");

        assertThat(changed).isTrue();
        verify(vaultClient).setDsHttpsTlsEnrollmentStatus(
                new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewal, "order failed"));
    }

    @Test
    void recordAcmeOutcomeShouldBeNoOpWhenErrorIsUnchanged() {
        when(vaultClient.getDsHttpsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, null, "same error")));

        boolean changed = service.recordAcmeOutcome("same error");

        assertThat(changed).isFalse();
        verify(vaultClient, never()).setDsHttpsTlsEnrollmentStatus(any());
    }

    private static X509Certificate selfSignedCertificate() throws Exception {
        return CertUtils.createSelfSignedCertificate("ds-tls-test", generateKeyPair(), 30)[0];
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
