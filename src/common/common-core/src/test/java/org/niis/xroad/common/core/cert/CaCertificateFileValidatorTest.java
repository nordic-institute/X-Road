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
package org.niis.xroad.common.core.cert;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaCertificateFileValidatorTest {

    private static KeyPair rootKeyPair;
    private static X509Certificate rootCa;
    private static X509Certificate intermediateCa;
    private static X509Certificate leaf;
    private static X509Certificate leafWithoutBasicConstraints;
    private static X509Certificate expiredCa;
    private static X509Certificate notYetValidCa;
    private static X509Certificate ecCa;
    private static X509Certificate ed25519Ca;
    private static X509Certificate dsaCa;

    @BeforeAll
    static void createCertificates() throws Exception {
        rootKeyPair = newKeyPair();
        rootCa = issue("CN=Root CA", rootKeyPair, "CN=Root CA", rootKeyPair, new BasicConstraints(true), -1, 365);

        KeyPair intermediateKeyPair = newKeyPair();
        intermediateCa = issue("CN=Root CA", rootKeyPair, "CN=Intermediate CA", intermediateKeyPair, new BasicConstraints(0), -1, 365);

        KeyPair leafKeyPair = newKeyPair();
        leaf = issue("CN=Root CA", rootKeyPair, "CN=Op-monitor", leafKeyPair, new BasicConstraints(false), -1, 365);
        leafWithoutBasicConstraints = issue("CN=Root CA", rootKeyPair, "CN=Op-monitor", leafKeyPair, null, -1, 365);

        expiredCa = issue("CN=Old CA", rootKeyPair, "CN=Old CA", rootKeyPair, new BasicConstraints(true), -30, -1);
        notYetValidCa = issue("CN=Future CA", rootKeyPair, "CN=Future CA", rootKeyPair, new BasicConstraints(true), 1, 30);
        ecCa = selfSignedCa("EC", 256, "SHA256withECDSA");
        ed25519Ca = selfSignedCa("Ed25519", 0, "Ed25519");
        dsaCa = selfSignedCa("DSA", 2048, "SHA256withDSA");
    }

    @Test
    void shouldAcceptPemRootCa() throws Exception {
        assertThat(CaCertificateFileValidator.validate(pem(rootCa))).isEqualTo(rootCa);
    }

    @Test
    void shouldAcceptPemIntermediateCa() throws Exception {
        assertThat(CaCertificateFileValidator.validate(pem(intermediateCa))).isEqualTo(intermediateCa);
    }

    @Test
    void shouldAcceptEcCa() throws Exception {
        assertThat(CaCertificateFileValidator.validate(pem(ecCa))).isEqualTo(ecCa);
        assertThat(CaCertificateFileValidator.validate(ecCa.getEncoded())).isEqualTo(ecCa);
    }

    @Test
    void shouldAcceptDerCa() throws Exception {
        assertThat(CaCertificateFileValidator.validate(rootCa.getEncoded())).isEqualTo(rootCa);
        assertThat(CaCertificateFileValidator.validate(intermediateCa.getEncoded())).isEqualTo(intermediateCa);
    }

    @Test
    void shouldIgnoreTextOutsideThePemBlock() throws Exception {
        String bagAttributes = "Bag Attributes\n    friendlyName: op-monitor-ca\nsubject=CN=Root CA\nissuer=CN=Root CA\n";
        String content = bagAttributes + new String(pem(rootCa), StandardCharsets.US_ASCII) + "\ntrailing text\n";

        assertThat(CaCertificateFileValidator.validate(content.getBytes(StandardCharsets.UTF_8))).isEqualTo(rootCa);
    }

    @Test
    void shouldAcceptWindowsLineEndings() throws Exception {
        String content = new String(pem(rootCa), StandardCharsets.US_ASCII).replace("\n", "\r\n");

        assertThat(CaCertificateFileValidator.validate(content.getBytes(StandardCharsets.US_ASCII))).isEqualTo(rootCa);
    }

    @Test
    void shouldRejectEmptyInput() {
        assertRejected(new byte[0], ErrorCode.CA_CERT_EMPTY);
        assertRejected(" \r\n\t\n".getBytes(StandardCharsets.US_ASCII), ErrorCode.CA_CERT_EMPTY);
    }

    @Test
    void shouldRejectRandomBytes() {
        byte[] random = new byte[256];
        new Random(42).nextBytes(random);
        random[0] = 0x01;

        assertRejected(random, ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
        assertRejected("this is not a certificate".getBytes(StandardCharsets.US_ASCII), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
    }

    @Test
    void shouldRejectMalformedPem() throws Exception {
        String valid = new String(pem(rootCa), StandardCharsets.US_ASCII);
        String withoutEnd = valid.substring(0, valid.indexOf("-----END"));
        String garbageBody = "-----BEGIN CERTIFICATE-----\n!!!not base64!!!\n-----END CERTIFICATE-----\n";
        String notDerBody = "-----BEGIN CERTIFICATE-----\nAAECAwQF\n-----END CERTIFICATE-----\n";

        assertRejected(withoutEnd.getBytes(StandardCharsets.US_ASCII), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
        assertRejected(garbageBody.getBytes(StandardCharsets.US_ASCII), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
        assertRejected(notDerBody.getBytes(StandardCharsets.US_ASCII), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
    }

    @Test
    void shouldRejectTwoPemCertificates() throws Exception {
        assertRejected(concat(pem(intermediateCa), pem(rootCa)), ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
    }

    @Test
    void shouldRejectTwoDerCertificates() throws Exception {
        assertRejected(concat(rootCa.getEncoded(), intermediateCa.getEncoded()), ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
    }

    @Test
    void shouldRejectASecondPemBlockOfAnyType() throws Exception {
        byte[] publicKey = pemBlock(rootKeyPair.getPublic());
        byte[] certificateAfterPublicKey = concat(pem(rootCa), publicKey);
        byte[] certificateBeforePublicKey = concat(publicKey, pem(rootCa));
        byte[] unknownBlock = concat(pem(rootCa), "-----BEGIN SOMETHING ELSE-----\nAAEC\n-----END SOMETHING ELSE-----\n"
                .getBytes(StandardCharsets.US_ASCII));

        assertRejected(certificateAfterPublicKey, ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
        assertRejected(certificateBeforePublicKey, ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
        assertRejected(unknownBlock, ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
    }

    @Test
    void shouldRejectASinglePemBlockThatIsNotACertificate() throws Exception {
        assertRejected(pemBlock(rootKeyPair.getPublic()), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
    }

    @Test
    void shouldRejectCertificatePlusPrivateKey() throws Exception {
        assertRejected(concat(pem(rootCa), pemBlock(rootKeyPair.getPrivate())), ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected(concat(pemBlock(rootKeyPair.getPrivate()), pem(rootCa)), ErrorCode.CA_CERT_KEY_MATERIAL);
    }

    @Test
    void shouldRejectCertificatePlusIndentedPrivateKey() throws Exception {
        byte[] indentedKey = indent(pemBlock(rootKeyPair.getPrivate()), "  ");
        byte[] tabIndentedKey = indent(pemBlock(rootKeyPair.getPrivate()), "\t");
        assertRejected(concat(pem(rootCa), indentedKey), ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected(concat(indentedKey, pem(rootCa)), ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected(concat(pem(rootCa), tabIndentedKey), ErrorCode.CA_CERT_KEY_MATERIAL);
    }

    @Test
    void shouldRejectCertificatePlusIndentedSecondCertificate() throws Exception {
        assertRejected(concat(pem(rootCa), indent(pem(intermediateCa), "  ")), ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
        assertRejected(concat(indent(pem(intermediateCa), "\t"), pem(rootCa)), ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES);
    }

    @Test
    void shouldRejectASingleIndentedCertificate() throws Exception {
        assertRejected(indent(pem(rootCa), "  "), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
    }

    @Test
    void shouldAcceptPemWithUtf8Bom() throws Exception {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        assertThat(CaCertificateFileValidator.validate(concat(bom, pem(rootCa)))).isEqualTo(rootCa);
    }

    @Test
    void shouldNotStripAUtf8BomFromDerInput() throws Exception {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        assertRejected(concat(bom, rootCa.getEncoded()), ErrorCode.CA_CERT_NOT_A_CERTIFICATE);
    }

    @Test
    void shouldRejectPrivateKeyOnly() throws Exception {
        assertRejected(pemBlock(rootKeyPair.getPrivate()), ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected("-----BEGIN RSA PRIVATE KEY-----\nAAEC\n-----END RSA PRIVATE KEY-----\n".getBytes(StandardCharsets.US_ASCII),
                ErrorCode.CA_CERT_KEY_MATERIAL);
        assertRejected("-----BEGIN ENCRYPTED PRIVATE KEY-----\nAAEC\n-----END ENCRYPTED PRIVATE KEY-----\n"
                .getBytes(StandardCharsets.US_ASCII), ErrorCode.CA_CERT_KEY_MATERIAL);
    }

    @Test
    void shouldRejectLeafCertificate() throws Exception {
        assertRejected(pem(leaf), ErrorCode.CA_CERT_NOT_A_CA);
        assertRejected(leaf.getEncoded(), ErrorCode.CA_CERT_NOT_A_CA);
        assertRejected(pem(leafWithoutBasicConstraints), ErrorCode.CA_CERT_NOT_A_CA);
    }

    @Test
    void shouldRejectCaWithUnsupportedKeyAlgorithm() throws Exception {
        assertRejected(pem(ed25519Ca), ErrorCode.CA_CERT_UNSUPPORTED_KEY_ALGORITHM);
        assertRejected(ed25519Ca.getEncoded(), ErrorCode.CA_CERT_UNSUPPORTED_KEY_ALGORITHM);
        assertRejected(pem(dsaCa), ErrorCode.CA_CERT_UNSUPPORTED_KEY_ALGORITHM);
    }

    @Test
    void shouldRejectExpiredCa() throws Exception {
        assertRejected(pem(expiredCa), ErrorCode.CA_CERT_EXPIRED);
    }

    @Test
    void shouldRejectNotYetValidCa() throws Exception {
        assertRejected(pem(notYetValidCa), ErrorCode.CA_CERT_NOT_YET_VALID);
    }

    private static XrdRuntimeException assertRejected(byte[] input, ErrorCode expected) {
        var thrown = assertThatThrownBy(() -> CaCertificateFileValidator.validate(input))
                .isInstanceOf(XrdRuntimeException.class)
                .actual();
        var rejected = (XrdRuntimeException) thrown;

        assertThat(rejected.isCausedBy(expected)).isTrue();
        assertThat(rejected.getCause()).isNull();
        String inputText = new String(input, StandardCharsets.ISO_8859_1).strip();
        if (!inputText.isEmpty()) {
            assertThat(rejected.getMessage()).doesNotContain(inputText);
        }
        assertThat(rejected.getMessage()).doesNotContain("BEGIN").doesNotContain("MII");
        return rejected;
    }

    private static KeyPair newKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCa(String keyAlgorithm, int keySize, String signatureAlgorithm) throws Exception {
        var generator = KeyPairGenerator.getInstance(keyAlgorithm);
        if (keySize > 0) {
            generator.initialize(keySize);
        }
        var keyPair = generator.generateKeyPair();
        var name = new X500Name("CN=" + keyAlgorithm + " CA");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                name, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static X509Certificate issue(String issuer, KeyPair issuerKeyPair, String subject, KeyPair subjectKeyPair,
                                         BasicConstraints basicConstraints, int notBeforeDaysFromNow, int notAfterDaysFromNow)
            throws Exception {
        var builder = new JcaX509v3CertificateBuilder(new X500Name(issuer), BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().plus(notBeforeDaysFromNow, ChronoUnit.DAYS)),
                Date.from(Instant.now().plus(notAfterDaysFromNow, ChronoUnit.DAYS)),
                new X500Name(subject), subjectKeyPair.getPublic());
        if (basicConstraints != null) {
            builder.addExtension(Extension.basicConstraints, true, basicConstraints);
        }
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuerKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static byte[] pem(X509Certificate certificate) throws IOException {
        return pemBlock(certificate);
    }

    private static byte[] pemBlock(Object object) throws IOException {
        var writer = new StringWriter();
        try (var pemWriter = new JcaPEMWriter(writer)) {
            pemWriter.writeObject(object);
        }
        return writer.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] indent(byte[] pem, String indentation) {
        String text = new String(pem, StandardCharsets.US_ASCII);
        return text.lines().map(line -> indentation + line + "\n").collect(Collectors.joining()).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
