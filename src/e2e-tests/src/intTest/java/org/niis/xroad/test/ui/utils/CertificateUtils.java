/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.test.ui.utils;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;

public final class CertificateUtils {

    private CertificateUtils() {
    }

    public static File getAsFile(byte[] certificate) throws Exception {
        final File certificateFile = File.createTempFile(UUID.randomUUID().toString(), null);
        try (FileOutputStream fos = new FileOutputStream(certificateFile)) {
            fos.write(certificate);
        }
        return certificateFile;
    }

    public static X509Certificate readCertificate(byte[] certBytes) throws CertificateException, IOException {
        CertificateFactory fact = CertificateFactory.getInstance("X.509");
        try (InputStream is = new ByteArrayInputStream(certBytes)) {
            final Collection<? extends Certificate> certs = fact.generateCertificates(is);
            return certs.toArray(new X509Certificate[0])[0];
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static byte[] generateAuthCert(String subjectCN) throws NoSuchAlgorithmException, OperatorCreationException, IOException {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        var subjectKey = keyPairGenerator.generateKeyPair();
        return generateAuthCert(subjectCN, subjectKey.getPublic(), subjectKey.getPrivate());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static byte[] generateAuthCert(String subjectCN, PublicKey subjectKey, PrivateKey privateKey)
            throws OperatorCreationException, IOException {
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        var issuer = new X500Principal("CN=Issuer");
        var subject = new X500Principal("CN=" + subjectCN);

        return new JcaX509v3CertificateBuilder(
                issuer,
                BigInteger.ONE,
                Date.from(Instant.now()),
                Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
                subject,
                subjectKey)
                .addExtension(Extension.create(
                        Extension.keyUsage,
                        true,
                        new KeyUsage(KeyUsage.digitalSignature)))
                .build(signer)
                .getEncoded();
    }


}
