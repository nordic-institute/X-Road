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

import java.io.IOException;
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
