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

package org.niis.xroad.cs.test.utils;

import ee.ria.xroad.common.util.TimeUtils;

import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.security.auth.x500.X500Principal;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

import static java.lang.ClassLoader.getSystemResourceAsStream;

public final class CertificateUtils {

    private CertificateUtils() {
    }

    public static byte[] generateAuthCert(String certDistinguishedName) throws Exception {
        return generateAuthCertHolder(certDistinguishedName).getEncoded();
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public static X509CertificateHolder generateAuthCertHolder(String subjectDistinguishedName) throws Exception {
        var keyFactory = KeyFactory.getInstance("RSA");
        var certificateFactory = CertificateFactory.getInstance("X.509");
        PrivateKey privateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(readCaCertPrivateKeyBytes()));
        var caCertificate = (X509Certificate) certificateFactory
                .generateCertificate(getSystemResourceAsStream("container-files/etc/xroad/globalconf/root-ca.pem"));
        return generateAuthCert(caCertificate, privateKey, subjectDistinguishedName);
    }

    private static byte[] readCaCertPrivateKeyBytes() throws IOException {
        var keyInputStream = getSystemResourceAsStream("container-files/etc/xroad/globalconf/root-ca.key");
        String key = new String(keyInputStream.readAllBytes(), StandardCharsets.ISO_8859_1);
        Pattern parse = Pattern.compile("(?m)(?s)^---*BEGIN.*---*$(.*)^---*END.*---*$.*");
        String encoded = parse.matcher(key).replaceFirst("$1");
        return Base64.getMimeDecoder().decode(encoded);
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private static X509CertificateHolder generateAuthCert(X509Certificate issuerCertificate, PrivateKey privateKey,
                                                          String subjectDistinguishedName)
            throws OperatorCreationException, IOException {
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(privateKey);
        var subject = new X500Principal(subjectDistinguishedName);


        return new JcaX509v3CertificateBuilder(
                issuerCertificate.getSubjectX500Principal(),
                BigInteger.ONE,
                Date.from(TimeUtils.now().minus(1, ChronoUnit.MINUTES)),
                Date.from(TimeUtils.now().plus(365, ChronoUnit.DAYS)),
                subject,
                issuerCertificate.getPublicKey())
                .addExtension(Extension.create(
                        Extension.keyUsage,
                        true,
                        new KeyUsage(KeyUsage.digitalSignature)))
                .build(signer);
    }

}
