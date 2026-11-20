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
package org.niis.xroad.common.rpc.test;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Writes test key material to PEM files in the layout the file-based identity loader reads.
 */
public final class PemFiles {

    private static final int PEM_LINE_LENGTH = 64;
    private static final Base64.Encoder ENCODER = Base64.getMimeEncoder(PEM_LINE_LENGTH, new byte[] {'\n'});

    private PemFiles() {
    }

    public static Path writeCertificates(Path file, X509Certificate... certificates) throws IOException {
        var pem = new StringBuilder();
        for (var certificate : certificates) {
            pem.append(block("CERTIFICATE", encoded(certificate)));
        }
        return write(file, pem.toString());
    }

    /** Writes the key as unencrypted PKCS#8. */
    public static Path writePrivateKey(Path file, PrivateKey privateKey) throws IOException {
        return write(file, block("PRIVATE KEY", privateKey.getEncoded()));
    }

    /**
     * Writes the key as SEC1 {@code EC PRIVATE KEY}, the format OpenSSL emits for EC keys by default.
     */
    public static void writeEcPrivateKeySec1(Path file, PrivateKey ecPrivateKey) throws IOException {
        var sec1 = PrivateKeyInfo.getInstance(ecPrivateKey.getEncoded()).parsePrivateKey().toASN1Primitive().getEncoded();
        write(file, block("EC PRIVATE KEY", sec1));
    }

    /**
     * Writes the key as password-protected PKCS#8 ({@code ENCRYPTED PRIVATE KEY}).
     */
    public static void writeEncryptedPrivateKey(Path file, PrivateKey privateKey, char[] password) throws Exception {
        var encryptor = new JceOpenSSLPKCS8EncryptorBuilder(PKCSObjectIdentifiers.pbeWithSHAAnd3_KeyTripleDES_CBC)
                .setPassword(password)
                .build();
        var pem = new StringWriter();
        try (var writer = new JcaPEMWriter(pem)) {
            writer.writeObject(new JcaPKCS8Generator(privateKey, encryptor));
        }
        write(file, pem.toString());
    }

    public static Path write(Path file, String content) throws IOException {
        return Files.writeString(file, content, StandardCharsets.US_ASCII);
    }

    private static byte[] encoded(X509Certificate certificate) throws IOException {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IOException(e);
        }
    }

    private static String block(String type, byte[] der) {
        return "-----BEGIN %s-----\n%s\n-----END %s-----\n".formatted(type, ENCODER.encodeToString(der), type);
    }
}
