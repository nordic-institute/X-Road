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
package org.niis.xroad.restapi.validator;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.BadRequestException;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bouncycastle.openssl.PEMParser.TYPE_CERTIFICATE;
import static org.bouncycastle.openssl.PEMParser.TYPE_PRIVATE_KEY;
import static org.niis.xroad.common.core.exception.ErrorCode.CERT_VALIDITY_TIME;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CERTIFICATE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_TLS_PRIVATE_KEY;
import static org.niis.xroad.common.core.exception.ErrorCode.TLS_KEY_CERTIFICATE_MISMATCH;

class DsTlsMaterialValidatorTest {

    private final DsTlsMaterialValidator validator = new DsTlsMaterialValidator();

    @Test
    void validateShouldAcceptMatchingRsaKeyAndCertificate() throws Exception {
        KeyPair keyPair = generateKeyPair("RSA", 2048);
        X509Certificate certificate = selfSignedCertificate(keyPair, 30);

        var result = validator.validate(pem(keyPair.getPrivate()), pem(certificate));

        assertThat(result.getKey()).isEqualTo(keyPair.getPrivate());
        assertThat(result.getCertChain()).containsExactly(certificate);
    }

    @Test
    void validateShouldAcceptMatchingEcKeyAndCertificate() throws Exception {
        KeyPair keyPair = generateKeyPair("EC", 256);
        X509Certificate certificate = selfSignedCertificate(keyPair, 30);

        var result = validator.validate(pem(keyPair.getPrivate()), pem(certificate));

        assertThat(result.getKey()).isEqualTo(keyPair.getPrivate());
        assertThat(result.getCertChain()).containsExactly(certificate);
    }

    @Test
    void validateShouldPreserveFullCertificateChain() throws Exception {
        KeyPair leafKeyPair = generateKeyPair("RSA", 2048);
        X509Certificate leaf = selfSignedCertificate(leafKeyPair, 30);
        KeyPair intermediateKeyPair = generateKeyPair("RSA", 2048);
        X509Certificate intermediate = selfSignedCertificate(intermediateKeyPair, 30);

        byte[] chainPem = concat(pem(leaf), pem(intermediate));

        var result = validator.validate(pem(leafKeyPair.getPrivate()), chainPem);

        assertThat(result.getCertChain()).containsExactly(leaf, intermediate);
    }

    @Test
    void validateShouldRejectMalformedPrivateKey() throws Exception {
        KeyPair keyPair = generateKeyPair("RSA", 2048);
        X509Certificate certificate = selfSignedCertificate(keyPair, 30);

        assertThatThrownBy(() -> validator.validate("not a key".getBytes(), pem(certificate)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorDeviation().code())
                .isEqualTo(INVALID_TLS_PRIVATE_KEY.code());
    }

    @Test
    void validateShouldRejectMalformedCertificate() throws Exception {
        KeyPair keyPair = generateKeyPair("RSA", 2048);

        assertThatThrownBy(() -> validator.validate(pem(keyPair.getPrivate()), "not a certificate".getBytes()))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorDeviation().code())
                .isEqualTo(INVALID_CERTIFICATE.code());
    }

    @Test
    void validateShouldRejectExpiredCertificate() throws Exception {
        KeyPair keyPair = generateKeyPair("RSA", 2048);
        // notBefore = now, notAfter = 1 day in the past -> already expired
        X509Certificate certificate = selfSignedCertificate(keyPair, -1);

        assertThatThrownBy(() -> validator.validate(pem(keyPair.getPrivate()), pem(certificate)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorDeviation().code())
                .isEqualTo(CERT_VALIDITY_TIME.code());
    }

    @Test
    void validateShouldRejectKeyNotMatchingCertificate() throws Exception {
        KeyPair keyPair = generateKeyPair("RSA", 2048);
        KeyPair otherKeyPair = generateKeyPair("RSA", 2048);
        X509Certificate certificate = selfSignedCertificate(otherKeyPair, 30);

        assertThatThrownBy(() -> validator.validate(pem(keyPair.getPrivate()), pem(certificate)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorDeviation().code())
                .isEqualTo(TLS_KEY_CERTIFICATE_MISMATCH.code());
    }

    @Test
    void validateShouldRejectMismatchedKeyAlgorithm() throws Exception {
        KeyPair rsaKeyPair = generateKeyPair("RSA", 2048);
        KeyPair ecKeyPair = generateKeyPair("EC", 256);
        X509Certificate certificate = selfSignedCertificate(ecKeyPair, 30);

        assertThatThrownBy(() -> validator.validate(pem(rsaKeyPair.getPrivate()), pem(certificate)))
                .isInstanceOf(BadRequestException.class)
                .extracting(e -> ((BadRequestException) e).getErrorDeviation().code())
                .isEqualTo(TLS_KEY_CERTIFICATE_MISMATCH.code());
    }

    private static KeyPair generateKeyPair(String algorithm, int keySize) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(algorithm);
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair, long expirationInDays) throws Exception {
        String signatureAlgorithm = "EC".equals(keyPair.getPrivate().getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA";
        X500Name subject = new X500Name("CN=ds-tls-test");
        Instant now = Instant.now();

        var certificateBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now),
                Date.from(now.plus(expirationInDays, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
        X509CertificateHolder holder = certificateBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    private static byte[] pem(PrivateKey privateKey) throws Exception {
        return writePem(TYPE_PRIVATE_KEY, privateKey.getEncoded());
    }

    private static byte[] pem(X509Certificate certificate) throws Exception {
        return writePem(TYPE_CERTIFICATE, certificate.getEncoded());
    }

    private static byte[] writePem(String type, byte[] derBytes) throws Exception {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject(type, derBytes));
        }
        return stringWriter.toString().getBytes();
    }

    private static byte[] concat(byte[]... chunks) {
        return Stream.of(chunks)
                .reduce(new byte[0], DsTlsMaterialValidatorTest::append);
    }

    private static byte[] append(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
