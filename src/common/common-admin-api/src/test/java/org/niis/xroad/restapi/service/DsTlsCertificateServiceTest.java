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
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.dstls.DsTlsCertificateValidator;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_CERTIFICATE_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_NOT_GENERATED;

@ExtendWith(MockitoExtension.class)
class DsTlsCertificateServiceTest {

    @Mock
    private VaultClient vaultClient;

    private final DsTlsCertificateValidator validator = new DsTlsCertificateValidator();

    private DsTlsCertificateService service;

    private DsTlsCertificateService service() {
        if (service == null) {
            service = new DsTlsCertificateService(vaultClient, validator);
        }
        return service;
    }

    @Test
    void statusShouldReportNotGeneratedWhenVaultHasNoSlot() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("no secret"));

        var status = service().getStatus();

        assertThat(status.keyGenerated()).isFalse();
        assertThat(status.certificateAcquired()).isFalse();
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
    void generateCsrShouldFailWhenNoKeyGenerated() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("no secret"));

        assertThatThrownBy(() -> service().generateCsr("CN=ds.example.org"))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_KEY_NOT_GENERATED.code()));
    }

    @Test
    void generateCsrShouldBuildARequestForTheStoredKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));

        byte[] csrBytes = service().generateCsr("CN=ds.example.org");

        PKCS10CertificationRequest csr = parseCsr(csrBytes);
        var publicKeyFromCsr = new JcaPEMKeyConverter().getPublicKey(csr.getSubjectPublicKeyInfo());
        assertThat(publicKeyFromCsr).isEqualTo(keyPair.getPublic());
        assertThat(csr.getSubject()).isEqualTo(new X500Name("CN=ds.example.org"));
    }

    @Test
    void uploadCertificateShouldFailWhenNoKeyGenerated() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IllegalStateException("no secret"));

        assertThatThrownBy(() -> service().uploadCertificate(new byte[0]))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).getErrorDeviation().code()).isEqualTo(DS_TLS_KEY_NOT_GENERATED.code()));
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
