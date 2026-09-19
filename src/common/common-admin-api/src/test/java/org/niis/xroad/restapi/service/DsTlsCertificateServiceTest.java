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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.ConflictException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.dstls.DsTlsAcmeAvailability;
import org.niis.xroad.restapi.dstls.DsTlsAcmeOrderResult;
import org.niis.xroad.restapi.dstls.DsTlsCertificateAcmeProvider;
import org.niis.xroad.restapi.dstls.DsTlsCertificateValidator;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_ACME_ORDER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CA_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_INVALID_SUBJECT_ALT_NAME;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_CERTIFICATE_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_NOT_GENERATED;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_DISTINGUISHED_NAME;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@ExtendWith(MockitoExtension.class)
class DsTlsCertificateServiceTest {

    @Mock
    private VaultClient vaultClient;
    @Mock
    private ObjectProvider<DsTlsCertificateAcmeProvider> acmeProvider;
    @Mock
    private DsTlsCertificateAcmeProvider dsTlsCertificateAcmeProvider;

    private final DsTlsCertificateValidator validator = new DsTlsCertificateValidator();

    private DsTlsCertificateService service;

    private DsTlsCertificateService service() {
        if (service == null) {
            service = new DsTlsCertificateService(vaultClient, validator, acmeProvider);
        }
        return service;
    }

    @Test
    void statusShouldReportNotGeneratedWhenVaultHasNoSlot() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());

        var status = service().getStatus();

        assertThat(status.keyGenerated()).isFalse();
        assertThat(status.certificateAcquired()).isFalse();
    }

    @Test
    void statusShouldPropagateAnInternalErrorWhenVaultFailsForAnUnrelatedReason() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("vault connection refused"));

        assertThatThrownBy(() -> service().getStatus())
                .isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void statusShouldReportKeyGeneratedWithoutCertificate() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        var status = service().getStatus();

        assertThat(status.keyGenerated()).isTrue();
        assertThat(status.certificateAcquired()).isFalse();
    }

    @Test
    void statusShouldReportAcquiredCertificate() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{cert}));

        var status = service().getStatus();

        assertThat(status.keyGenerated()).isTrue();
        assertThat(status.certificate()).isEqualTo(cert);
    }

    @Test
    void generateKeyShouldStoreAFreshRsaKeyWithNoCertificate() throws Exception {
        service().generateKey();

        ArgumentCaptor<InternalSSLKey> captor = ArgumentCaptor.forClass(InternalSSLKey.class);
        verify(vaultClient).createDsHttpsTlsCredentials(captor.capture());

        InternalSSLKey stored = captor.getValue();
        assertThat(stored.getKey()).isNotNull();
        assertThat(stored.getKey().getAlgorithm()).isEqualTo("RSA");
        assertThat(stored.getCertChain()).isEmpty();
    }

    @Test
    void generateKeyShouldClearAnyRecordedEnrollmentStatusByWritingAnEmptyRecord() throws Exception {
        service().generateKey();

        ArgumentCaptor<DsTlsEnrollmentStatus> captor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(captor.capture());
        assertThat(captor.getValue().method()).isNull();
        assertThat(captor.getValue().nextRenewalTime()).isNull();
        assertThat(captor.getValue().lastError()).isNull();
    }

    @Test
    void generateCsrShouldFailWhenNoKeyGenerated() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());

        assertThatThrownBy(() -> service().generateCsr("CN=ds.example.org", null))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_KEY_NOT_GENERATED.code()));
    }

    @Test
    void generateCsrShouldPropagateAnInternalErrorWhenVaultFailsForAnUnrelatedReason() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("vault connection refused"));

        assertThatThrownBy(() -> service().generateCsr("CN=ds.example.org", null))
                .isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void generateCsrShouldRejectAMalformedDistinguishedName() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        assertThatThrownBy(() -> service().generateCsr("not a dn", null))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(INVALID_DISTINGUISHED_NAME.code()));
    }

    @Test
    void generateCsrShouldRejectABlankSubjectAltNameWhenProvided() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        assertThatThrownBy(() -> service().generateCsr("CN=ds.example.org", " "))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_INVALID_SUBJECT_ALT_NAME.code()));
    }

    @Test
    void generateCsrShouldBuildADistinguishedNameOnlyRequestWhenNoSanGiven() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        byte[] csrBytes = service().generateCsr("CN=ds.example.org", null);

        PKCS10CertificationRequest csr = parseCsr(csrBytes);
        var publicKeyFromCsr = new JcaPEMKeyConverter().getPublicKey(csr.getSubjectPublicKeyInfo());
        assertThat(publicKeyFromCsr).isEqualTo(keyPair.getPublic());
        assertThat(csr.getSubject()).isEqualTo(new X500Name("CN=ds.example.org"));
        assertThat(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)).isEmpty();
    }

    @Test
    void generateCsrShouldCarryTheSanWhenGiven() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        byte[] csrBytes = service().generateCsr("CN=ds.example.org", "ds.example.org");

        PKCS10CertificationRequest csr = parseCsr(csrBytes);
        assertThat(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)).hasSize(1);
    }

    @Test
    void getAcmeAvailabilityShouldReportUnavailableWhenNoProviderIsWired() {
        when(acmeProvider.getIfAvailable()).thenReturn(null);

        DsTlsAcmeAvailability availability = service().getAcmeAvailability();

        assertThat(availability.available()).isFalse();
        assertThat(availability.caNames()).isEmpty();
        assertThat(availability.publicHostname()).isNull();
    }

    @Test
    void getAcmeAvailabilityShouldDelegateToTheWiredProvider() {
        DsTlsAcmeAvailability delegated = new DsTlsAcmeAvailability(true, List.of("Test CA"), "ss.example.org");
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.getAvailability()).thenReturn(delegated);

        assertThat(service().getAcmeAvailability()).isEqualTo(delegated);
    }

    @Test
    void orderCertificateShouldFailWhenNoKeyGenerated() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org"))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_KEY_NOT_GENERATED.code()));
        verify(acmeProvider, never()).getIfAvailable();
    }

    @Test
    void orderCertificateShouldRejectABlankSubjectAltName() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "CN=ds.example.org", " "))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_INVALID_SUBJECT_ALT_NAME.code()));
    }

    @Test
    void orderCertificateShouldRejectASubjectAltNameContainingWhitespace() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "CN=ds.example.org", "ds example.org"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_INVALID_SUBJECT_ALT_NAME.code()));
    }

    @Test
    void orderCertificateShouldRejectOrderingWhenNoAcmeProviderIsWired() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        when(acmeProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_CA_NOT_FOUND.code()));
    }

    @Test
    void orderCertificateShouldRejectAnInvalidDistinguishedNameFromTheProvider() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.order(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("bad dn"));

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "not a dn", "ds.example.org"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(INVALID_DISTINGUISHED_NAME.code()));
    }

    @Test
    void orderCertificateShouldPropagateAnUnknownCaWithoutRecordingItAsTheLastError() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.order(any(), any(), any(), any(), any(), any()))
                .thenThrow(new BadRequestException(DS_TLS_CA_NOT_FOUND.build("Unknown CA")));

        assertThatThrownBy(() -> service().orderCertificate("Unknown CA", "CN=ds.example.org", "ds.example.org"))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_CA_NOT_FOUND.code()));
        verify(vaultClient, never()).createDsTlsEnrollmentStatus(any());
    }

    @Test
    void orderCertificateShouldRecordAndWrapAnAcmeEngineFailure() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        when(vaultClient.getDsTlsEnrollmentStatus()).thenReturn(Optional.empty());
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.order(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("CA unreachable"));

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org"))
                .isInstanceOf(InternalServerErrorException.class)
                .satisfies(e -> assertThat(((InternalServerErrorException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_ACME_ORDER_FAILED.code()));

        ArgumentCaptor<DsTlsEnrollmentStatus> captor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(captor.capture());
        assertThat(captor.getValue().lastError()).isEqualTo("CA unreachable");
    }

    @Test
    void orderCertificateShouldStoreTheIssuedChainThroughTheAcmeStorePath() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        X509Certificate issued = selfSignedCertificate(keyPair);
        Instant nextRenewalTime = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.order(eq("Test CA"), eq("CN=ds.example.org"), eq("ds.example.org"),
                eq(keyPair.getPrivate()), any(), isNull()))
                .thenReturn(new DsTlsAcmeOrderResult(List.of(issued), nextRenewalTime));

        X509Certificate stored = service().orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org");

        assertThat(stored).isEqualTo(issued);
        ArgumentCaptor<InternalSSLKey> credentialsCaptor = ArgumentCaptor.forClass(InternalSSLKey.class);
        verify(vaultClient).createDsHttpsTlsCredentials(credentialsCaptor.capture());
        assertThat(credentialsCaptor.getValue().getCertChain()).containsExactly(issued);
        ArgumentCaptor<DsTlsEnrollmentStatus> statusCaptor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue().method()).isEqualTo(DsTlsEnrollmentMethod.ACME);
        assertThat(statusCaptor.getValue().nextRenewalTime()).isEqualTo(nextRenewalTime);
    }

    @Test
    void orderCertificateShouldReplaceAnExistingCertificate() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate existing = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{existing}));
        X509Certificate issued = selfSignedCertificate(keyPair);
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.order(eq("Test CA"), eq("CN=ds.example.org"), eq("ds.example.org"),
                eq(keyPair.getPrivate()), any(), eq(existing)))
                .thenReturn(new DsTlsAcmeOrderResult(List.of(issued), Instant.now()));

        X509Certificate stored = service().orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org");

        assertThat(stored).isEqualTo(issued);
    }

    @Test
    void uploadCertificateShouldFailWhenNoKeyGenerated() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());

        assertThatThrownBy(() -> service().uploadCertificate(new byte[0]))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_KEY_NOT_GENERATED.code()));
    }

    @Test
    void uploadCertificateShouldPropagateAnInternalErrorWhenVaultFailsForAnUnrelatedReason() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("vault connection refused"));

        assertThatThrownBy(() -> service().uploadCertificate(new byte[0]))
                .isInstanceOf(InternalServerErrorException.class);
    }

    @Test
    void uploadCertificateShouldRejectALeafThatDoesNotMatchTheStoredKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        X509Certificate certForOtherKey = selfSignedCertificate(generateRsaKeyPair());

        assertThatThrownBy(() -> service().uploadCertificate(toPem(certForOtherKey)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_KEY_CERTIFICATE_MISMATCH.code()));
    }

    @Test
    void uploadCertificateShouldStoreAMatchingLeafAlongsideTheExistingKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        X509Certificate cert = selfSignedCertificate(keyPair);

        X509Certificate stored = service().uploadCertificate(toPem(cert));

        assertThat(stored).isEqualTo(cert);
        ArgumentCaptor<InternalSSLKey> captor = ArgumentCaptor.forClass(InternalSSLKey.class);
        verify(vaultClient).createDsHttpsTlsCredentials(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo(keyPair.getPrivate());
        assertThat(captor.getValue().getCertChain()).containsExactly(cert);
    }

    @Test
    void uploadCertificateShouldRecordManualEnrollmentAndClearNextRenewalTimeAndLastError() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        X509Certificate cert = selfSignedCertificate(keyPair);

        service().uploadCertificate(toPem(cert));

        ArgumentCaptor<DsTlsEnrollmentStatus> captor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(DsTlsEnrollmentMethod.MANUAL);
        assertThat(captor.getValue().nextRenewalTime()).isNull();
        assertThat(captor.getValue().lastError()).isNull();
    }

    @Test
    void storeRenewedCertificateShouldStoreCredentialsAndTagAcmeStatusWhenTheSlotStillHoldsTheReplacedCertificate()
            throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair);
        Instant nextRenewalTime = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        X509Certificate replaced = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{replaced}));

        boolean stored = service().storeRenewedCertificate(replaced, keyPair.getPrivate(), new X509Certificate[]{cert}, nextRenewalTime);

        assertThat(stored).isTrue();

        ArgumentCaptor<InternalSSLKey> credentialsCaptor = ArgumentCaptor.forClass(InternalSSLKey.class);
        verify(vaultClient).createDsHttpsTlsCredentials(credentialsCaptor.capture());
        assertThat(credentialsCaptor.getValue().getKey()).isEqualTo(keyPair.getPrivate());
        assertThat(credentialsCaptor.getValue().getCertChain()).containsExactly(cert);

        ArgumentCaptor<DsTlsEnrollmentStatus> statusCaptor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(statusCaptor.capture());
        assertThat(statusCaptor.getValue().method()).isEqualTo(DsTlsEnrollmentMethod.ACME);
        assertThat(statusCaptor.getValue().nextRenewalTime()).isEqualTo(nextRenewalTime);
        assertThat(statusCaptor.getValue().lastError()).isNull();
    }

    @Test
    void storeRenewedCertificateShouldRejectAMismatchedChainWithoutWritingAnything() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate certForOtherKey = selfSignedCertificate(generateRsaKeyPair());

        X509Certificate replaced = selfSignedCertificate(keyPair);
        assertThatThrownBy(() -> service().storeRenewedCertificate(
                replaced, keyPair.getPrivate(), new X509Certificate[]{certForOtherKey}, Instant.now()))
                .isInstanceOf(BadRequestException.class)
                .satisfies(e -> assertThat(((BadRequestException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_KEY_CERTIFICATE_MISMATCH.code()));

        verify(vaultClient, never()).createDsHttpsTlsCredentials(any());
        verify(vaultClient, never()).createDsTlsEnrollmentStatus(any());
    }

    @Test
    void storeRenewedCertificateShouldDiscardTheRenewalWhenTheSlotNoLongerHoldsTheReplacedCertificate() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate replaced = selfSignedCertificate(keyPair);
        X509Certificate storedMeanwhile = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{storedMeanwhile}));
        KeyPair renewedKeyPair = generateRsaKeyPair();
        X509Certificate renewed = selfSignedCertificate(renewedKeyPair);

        boolean stored = service().storeRenewedCertificate(
                replaced, renewedKeyPair.getPrivate(), new X509Certificate[]{renewed}, Instant.now());

        assertThat(stored).isFalse();
        verify(vaultClient, never()).createDsHttpsTlsCredentials(any());
        verify(vaultClient, never()).createDsTlsEnrollmentStatus(any());
    }

    @Test
    void storeRenewedCertificateShouldDiscardTheRenewalWhenTheSlotHoldsAKeyOnly() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate replaced = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(generateRsaKeyPair().getPrivate(), new X509Certificate[0]));
        KeyPair renewedKeyPair = generateRsaKeyPair();
        X509Certificate renewed = selfSignedCertificate(renewedKeyPair);

        boolean stored = service().storeRenewedCertificate(
                replaced, renewedKeyPair.getPrivate(), new X509Certificate[]{renewed}, Instant.now());

        assertThat(stored).isFalse();
        verify(vaultClient, never()).createDsHttpsTlsCredentials(any());
    }

    @Test
    void orderCertificateShouldDiscardTheIssuedChainWhenTheKeyWasReplacedDuringTheOrder() throws Exception {
        KeyPair orderedKeyPair = generateRsaKeyPair();
        KeyPair regeneratedKeyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials())
                .thenReturn(new InternalSSLKey(orderedKeyPair.getPrivate(), new X509Certificate[0]))
                .thenReturn(new InternalSSLKey(regeneratedKeyPair.getPrivate(), new X509Certificate[0]));
        X509Certificate issued = selfSignedCertificate(orderedKeyPair);
        when(acmeProvider.getIfAvailable()).thenReturn(dsTlsCertificateAcmeProvider);
        when(dsTlsCertificateAcmeProvider.order(any(), any(), any(), eq(orderedKeyPair.getPrivate()), any(), isNull()))
                .thenReturn(new DsTlsAcmeOrderResult(List.of(issued), Instant.now()));

        assertThatThrownBy(() -> service().orderCertificate("Test CA", "CN=ds.example.org", "ds.example.org"))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getErrorDeviation().code())
                        .isEqualTo(DS_TLS_ACME_ORDER_FAILED.code()));
        verify(vaultClient, never()).createDsHttpsTlsCredentials(any());
        verify(vaultClient, never()).createDsTlsEnrollmentStatus(any());
    }

    @Test
    void generateKeyShouldSucceedWhenRecordingTheEnrollmentStatusFails() throws Exception {
        doThrow(new IllegalStateException("vault down")).when(vaultClient).createDsTlsEnrollmentStatus(any());

        service().generateKey();

        verify(vaultClient).createDsHttpsTlsCredentials(any());
    }

    @Test
    void uploadCertificateShouldSucceedWhenRecordingTheEnrollmentStatusFails() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
        X509Certificate cert = selfSignedCertificate(keyPair);
        doThrow(new IllegalStateException("vault down")).when(vaultClient).createDsTlsEnrollmentStatus(any());

        X509Certificate stored = service().uploadCertificate(toPem(cert));

        assertThat(stored).isEqualTo(cert);
        verify(vaultClient).createDsHttpsTlsCredentials(any());
    }

    @Test
    void recordAcmeOutcomeShouldStoreANewErrorWithoutTouchingMethodOrNextRenewalTime() {
        Instant nextRenewalTime = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        when(vaultClient.getDsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, null)));

        boolean changed = service().recordAcmeOutcome("CA unreachable");

        assertThat(changed).isTrue();
        ArgumentCaptor<DsTlsEnrollmentStatus> captor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(DsTlsEnrollmentMethod.ACME);
        assertThat(captor.getValue().nextRenewalTime()).isEqualTo(nextRenewalTime);
        assertThat(captor.getValue().lastError()).isEqualTo("CA unreachable");
    }

    @Test
    void recordAcmeOutcomeShouldBeANoOpWhenTheErrorIsUnchanged() {
        when(vaultClient.getDsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, null, "CA unreachable")));

        boolean changed = service().recordAcmeOutcome("CA unreachable");

        assertThat(changed).isFalse();
        verify(vaultClient, never()).createDsTlsEnrollmentStatus(any());
    }

    @Test
    void recordAcmeOutcomeShouldTagAcmeWhenNoStatusHasEverBeenRecorded() {
        when(vaultClient.getDsTlsEnrollmentStatus()).thenReturn(Optional.empty());

        boolean changed = service().recordAcmeOutcome("directory unreachable");

        assertThat(changed).isTrue();
        ArgumentCaptor<DsTlsEnrollmentStatus> captor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(DsTlsEnrollmentMethod.ACME);
        assertThat(captor.getValue().nextRenewalTime()).isNull();
        assertThat(captor.getValue().lastError()).isEqualTo("directory unreachable");
    }

    @Test
    void recordAcmeOutcomeShouldTagAcmeWhenTheRecordedMethodIsNull() {
        when(vaultClient.getDsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(null, null, null)));

        boolean changed = service().recordAcmeOutcome("directory unreachable");

        assertThat(changed).isTrue();
        ArgumentCaptor<DsTlsEnrollmentStatus> captor = ArgumentCaptor.forClass(DsTlsEnrollmentStatus.class);
        verify(vaultClient).createDsTlsEnrollmentStatus(captor.capture());
        assertThat(captor.getValue().method()).isEqualTo(DsTlsEnrollmentMethod.ACME);
        assertThat(captor.getValue().nextRenewalTime()).isNull();
        assertThat(captor.getValue().lastError()).isEqualTo("directory unreachable");
    }

    @Test
    void getEnrollmentStatusShouldReportNoneConfiguredWhenNothingIsStoredAtAll() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());
        when(vaultClient.getDsTlsEnrollmentStatus()).thenReturn(Optional.empty());

        var status = service().getEnrollmentStatus();

        assertThat(status.configured()).isFalse();
        assertThat(status.method()).isNull();
        assertThat(status.lastError()).isNull();
    }

    @Test
    void getEnrollmentStatusShouldReportNoneConfiguredWhenTheRecordedMethodIsNull() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());
        when(vaultClient.getDsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(null, null, null)));

        var status = service().getEnrollmentStatus();

        assertThat(status.configured()).isFalse();
        assertThat(status.method()).isNull();
        assertThat(status.lastError()).isNull();
    }

    @Test
    void getEnrollmentStatusShouldReportNoneConfiguredWithLastErrorWhenAFirstAttemptFailed() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(missingSecretException());
        when(vaultClient.getDsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, null, "directory unreachable")));

        var status = service().getEnrollmentStatus();

        assertThat(status.configured()).isFalse();
        assertThat(status.method()).isNull();
        assertThat(status.lastError()).isEqualTo("directory unreachable");
    }

    @Test
    void getEnrollmentStatusShouldFallBackToManualWhenACertificateExistsWithNoRecordedStatus() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{cert}));
        when(vaultClient.getDsTlsEnrollmentStatus()).thenReturn(Optional.empty());

        var status = service().getEnrollmentStatus();

        assertThat(status.method()).isEqualTo(DsTlsEnrollmentMethod.MANUAL);
        assertThat(status.nextRenewalTime()).isNull();
        assertThat(status.lastError()).isNull();
    }

    @Test
    void getEnrollmentStatusShouldReportAcmeWithNextRenewalTimeAndLastErrorWhenRecorded() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair);
        Instant nextRenewalTime = Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{cert}));
        when(vaultClient.getDsTlsEnrollmentStatus())
                .thenReturn(Optional.of(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, "transient error")));

        var status = service().getEnrollmentStatus();

        assertThat(status.method()).isEqualTo(DsTlsEnrollmentMethod.ACME);
        assertThat(status.nextRenewalTime()).isEqualTo(nextRenewalTime);
        assertThat(status.lastError()).isEqualTo("transient error");
    }

    @Test
    void downloadCertificateTarShouldFailWhenNoCertificateAcquired() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        assertThatThrownBy(() -> service().downloadCertificateTar()).isInstanceOf(NotFoundException.class);
    }

    @Test
    void downloadCertificateTarShouldReturnNonEmptyArchiveWhenCertificateAcquired() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair);
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[]{cert}));

        byte[] tar = service().downloadCertificateTar();

        assertThat(tar).isNotEmpty();
    }

    private static XrdRuntimeException missingSecretException() {
        return XrdRuntimeException.systemException(MISSING_SECRET)
                .details("Failed to get secret from Vault. Secret not found at path: tls/ds-https")
                .build();
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

    private static byte[] toPem(X509Certificate certificate) throws Exception {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
        }
        return stringWriter.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static PKCS10CertificationRequest parseCsr(byte[] csrBytes) throws Exception {
        try (PEMParser pemParser = new PEMParser(
                new InputStreamReader(new ByteArrayInputStream(csrBytes), StandardCharsets.UTF_8))) {
            return (PKCS10CertificationRequest) pemParser.readObject();
        }
    }
}
