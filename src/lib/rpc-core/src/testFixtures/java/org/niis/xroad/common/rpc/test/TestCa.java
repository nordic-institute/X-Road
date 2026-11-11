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
package org.niis.xroad.common.rpc.test;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mints independent self-signed test certificate authorities and leaf certificates signed by them, so tests can run
 * real TLS handshakes against several distinct CAs.
 */
public final class TestCa {

    private static final AtomicLong SERIAL = new AtomicLong(1);
    private static final int RSA_KEY_SIZE = 2048;
    private static final int VALIDITY_DAYS = 365;

    private final KeyPair keyPair;
    private final X509Certificate certificate;

    private TestCa(KeyPair keyPair, X509Certificate certificate) {
        this.keyPair = keyPair;
        this.certificate = certificate;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public static TestCa selfSigned(String commonName) throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=" + commonName);
        var builder = new JcaX509v3CertificateBuilder(subject, nextSerial(), notBefore(), notAfter(), subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new TestCa(keyPair, new JcaX509CertificateConverter().getCertificate(builder.build(signer)));
    }

    /**
     * Issues a leaf certificate with the given subject common name and subject alternative names.
     *
     * @param dnsNames DNS subject alternative names
     * @param ipAddresses IP subject alternative names
     */
    public TestLeaf issueLeaf(String commonName, List<String> dnsNames, List<String> ipAddresses) throws Exception {
        var leafKeyPair = generateKeyPair();
        var issuer = new X500Name(certificate.getSubjectX500Principal().getName());
        var subject = new X500Name("CN=" + commonName);
        var builder = new JcaX509v3CertificateBuilder(issuer, nextSerial(), notBefore(), notAfter(), subject,
                leafKeyPair.getPublic());

        var names = new ArrayList<GeneralName>();
        dnsNames.forEach(name -> names.add(new GeneralName(GeneralName.dNSName, name)));
        ipAddresses.forEach(address -> names.add(new GeneralName(GeneralName.iPAddress, address)));
        if (!names.isEmpty()) {
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(names.toArray(GeneralName[]::new)));
        }

        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var leafCertificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestLeaf(leafKeyPair.getPrivate(), new X509Certificate[] {leafCertificate, certificate});
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }

    private static BigInteger nextSerial() {
        return BigInteger.valueOf(SERIAL.incrementAndGet());
    }

    private static Date notBefore() {
        return Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static Date notAfter() {
        return Date.from(Instant.now().plus(VALIDITY_DAYS, ChronoUnit.DAYS));
    }

    /**
     * A leaf private key with its certificate chain, leaf first.
     */
    public record TestLeaf(PrivateKey privateKey, X509Certificate[] chain) {
    }
}
