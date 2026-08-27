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
package org.niis.xroad.restapi.dstls;

import org.bouncycastle.asn1.x500.X500Name;
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
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_EXPIRED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_YET_VALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_CERTIFICATE_MISMATCH;

class DsTlsCertificateValidatorTest {

    private final DsTlsCertificateValidator validator = new DsTlsCertificateValidator();

    @Test
    void validCertificateMatchingExpectedKeyShouldPass() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));

        X509Certificate[] chain = validator.validate(keyPair.getPublic(), toPem(cert));

        assertEquals(1, chain.length);
        assertEquals(cert, chain[0]);
    }

    @Test
    void chainWithIntermediateShouldPassBasedOnLeafOnly() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        KeyPair intermediateKeyPair = generateRsaKeyPair();
        X509Certificate leaf = selfSignedCertificate(keyPair, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));
        X509Certificate intermediate = selfSignedCertificate(intermediateKeyPair, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));

        byte[] chainBytes = concatPem(toPem(leaf), toPem(intermediate));
        X509Certificate[] chain = validator.validate(keyPair.getPublic(), chainBytes);

        assertEquals(2, chain.length);
        assertEquals(leaf, chain[0]);
    }

    @Test
    void malformedCertificateShouldBeRejected() {
        byte[] garbage = "not a certificate".getBytes();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(generateRsaKeyPair().getPublic(), garbage));
        assertEquals(DS_TLS_CERTIFICATE_PARSE_FAILED.code(), exception.getErrorDeviation().code());
    }

    @Test
    void emptyCertificateBytesShouldBeRejected() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(generateRsaKeyPair().getPublic(), new byte[0]));
        assertEquals(DS_TLS_CERTIFICATE_PARSE_FAILED.code(), exception.getErrorDeviation().code());
    }

    @Test
    void mismatchedKeyShouldBeRejected() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        PublicKey otherPublicKey = generateRsaKeyPair().getPublic();
        X509Certificate cert = selfSignedCertificate(keyPair, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(otherPublicKey, toPem(cert)));
        assertEquals(DS_TLS_KEY_CERTIFICATE_MISMATCH.code(), exception.getErrorDeviation().code());
    }

    @Test
    void expiredCertificateShouldBeRejected() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair, Instant.now().minus(30, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(keyPair.getPublic(), toPem(cert)));
        assertEquals(DS_TLS_CERTIFICATE_EXPIRED.code(), exception.getErrorDeviation().code());
    }

    @Test
    void notYetValidCertificateShouldBeRejected() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair, Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(keyPair.getPublic(), toPem(cert)));
        assertEquals(DS_TLS_CERTIFICATE_NOT_YET_VALID.code(), exception.getErrorDeviation().code());
    }

    @Test
    void parsedChainMatchingExpectedKeyShouldPass() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate cert = selfSignedCertificate(keyPair, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));

        X509Certificate[] chain = validator.validate(keyPair.getPublic(), new X509Certificate[]{cert});

        assertEquals(1, chain.length);
        assertEquals(cert, chain[0]);
    }

    @Test
    void parsedEmptyChainShouldBeRejected() {
        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(generateRsaKeyPair().getPublic(), new X509Certificate[0]));
        assertEquals(DS_TLS_CERTIFICATE_PARSE_FAILED.code(), exception.getErrorDeviation().code());
    }

    @Test
    void parsedChainWithMismatchedKeyShouldBeRejected() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        PublicKey otherPublicKey = generateRsaKeyPair().getPublic();
        X509Certificate cert = selfSignedCertificate(keyPair, Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().plus(365, ChronoUnit.DAYS));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> validator.validate(otherPublicKey, new X509Certificate[]{cert}));
        assertEquals(DS_TLS_KEY_CERTIFICATE_MISMATCH.code(), exception.getErrorDeviation().code());
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate selfSignedCertificate(KeyPair keyPair, Instant notBefore, Instant notAfter) throws Exception {
        X500Name subject = new X500Name("CN=ds-tls-test");

        var certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(notBefore),
                Date.from(notAfter),
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
        return stringWriter.toString().getBytes();
    }

    private static byte[] concatPem(byte[]... pemBlocks) {
        StringBuilder sb = new StringBuilder();
        for (byte[] pemBlock : pemBlocks) {
            sb.append(new String(pemBlock));
        }
        return sb.toString().getBytes();
    }
}
