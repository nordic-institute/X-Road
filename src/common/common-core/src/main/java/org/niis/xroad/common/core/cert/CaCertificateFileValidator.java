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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.bouncycastle.util.io.pem.PemReader;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates that an uploaded file is exactly one valid CA certificate, PEM or DER encoded. Performs no I/O,
 * and no rejection carries any part of the input.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CaCertificateFileValidator {

    private static final String UTF8_BOM = "\u00EF\u00BB\u00BF";
    private static final String PEM_BEGIN_MARKER = "-----BEGIN ";
    private static final String CERTIFICATE_BLOCK_TYPE = "CERTIFICATE";
    private static final String PRIVATE_KEY_BLOCK_TYPE_FRAGMENT = "PRIVATE KEY";
    private static final Pattern PEM_BEGIN_LINE = Pattern.compile("^[ \\t]*-----BEGIN ([^\\r\\n]*?)-----[ \\t]*$", Pattern.MULTILINE);
    private static final String EMPTY_MESSAGE = "The file is empty.";
    private static final String NOT_A_CERTIFICATE_MESSAGE = "The file is not a PEM or DER encoded X.509 certificate.";
    private static final String MULTIPLE_CERTIFICATES_MESSAGE = "The file contains more than one certificate or PEM block.";
    private static final String KEY_MATERIAL_MESSAGE = "The file contains private key material. Upload the CA certificate only.";
    private static final String NOT_A_CA_MESSAGE = "The certificate is not a CA certificate: basic constraints with cA=true are required.";
    private static final String EXPIRED_MESSAGE = "The CA certificate has expired.";
    private static final String NOT_YET_VALID_MESSAGE = "The CA certificate is not yet valid.";

    /**
     * @param input the uploaded file content
     * @return the single, currently valid CA certificate the file holds
     * @throws XrdRuntimeException with a {@code ca_cert_*} error code if the file is not exactly one valid CA certificate
     */
    public static X509Certificate validate(byte[] input) {
        if (input == null || isBlank(input)) {
            throw rejection(ErrorCode.CA_CERT_EMPTY, EMPTY_MESSAGE);
        }
        String text = new String(input, StandardCharsets.ISO_8859_1);
        String pemText = text.startsWith(UTF8_BOM) ? text.substring(UTF8_BOM.length()) : text;
        X509Certificate certificate = pemText.contains(PEM_BEGIN_MARKER) ? parsePem(pemText) : parseDer(input);
        checkCa(certificate);
        checkValidity(certificate);
        return certificate;
    }

    private static boolean isBlank(byte[] input) {
        for (byte b : input) {
            if (!Character.isWhitespace(b)) {
                return false;
            }
        }
        return true;
    }

    private static X509Certificate parsePem(String text) {
        List<String> blockTypes = new ArrayList<>();
        var matcher = PEM_BEGIN_LINE.matcher(text);
        while (matcher.find()) {
            blockTypes.add(matcher.group(1));
        }
        if (blockTypes.stream().anyMatch(type -> type.contains(PRIVATE_KEY_BLOCK_TYPE_FRAGMENT))) {
            throw rejection(ErrorCode.CA_CERT_KEY_MATERIAL, KEY_MATERIAL_MESSAGE);
        }
        if (blockTypes.size() > 1) {
            throw rejection(ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES, MULTIPLE_CERTIFICATES_MESSAGE);
        }
        if (blockTypes.isEmpty() || !CERTIFICATE_BLOCK_TYPE.equals(blockTypes.getFirst())) {
            throw rejection(ErrorCode.CA_CERT_NOT_A_CERTIFICATE, NOT_A_CERTIFICATE_MESSAGE);
        }
        byte[] der;
        try (var reader = new PemReader(new StringReader(text))) {
            var block = reader.readPemObject();
            if (block == null) {
                throw rejection(ErrorCode.CA_CERT_NOT_A_CERTIFICATE, NOT_A_CERTIFICATE_MESSAGE);
            }
            der = block.getContent();
        } catch (IOException | RuntimeException e) {
            throw rejection(ErrorCode.CA_CERT_NOT_A_CERTIFICATE, NOT_A_CERTIFICATE_MESSAGE);
        }
        return parseDer(der);
    }

    private static X509Certificate parseDer(byte[] der) {
        List<Certificate> certificates;
        try {
            certificates = List.copyOf(CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(der)));
        } catch (CertificateException | RuntimeException e) {
            throw rejection(ErrorCode.CA_CERT_NOT_A_CERTIFICATE, NOT_A_CERTIFICATE_MESSAGE);
        }
        if (certificates.size() > 1) {
            throw rejection(ErrorCode.CA_CERT_MULTIPLE_CERTIFICATES, MULTIPLE_CERTIFICATES_MESSAGE);
        }
        if (certificates.isEmpty() || !(certificates.getFirst() instanceof X509Certificate certificate)) {
            throw rejection(ErrorCode.CA_CERT_NOT_A_CERTIFICATE, NOT_A_CERTIFICATE_MESSAGE);
        }
        return certificate;
    }

    private static void checkCa(X509Certificate certificate) {
        if (certificate.getBasicConstraints() < 0) {
            throw rejection(ErrorCode.CA_CERT_NOT_A_CA, NOT_A_CA_MESSAGE);
        }
    }

    private static void checkValidity(X509Certificate certificate) {
        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException e) {
            throw rejection(ErrorCode.CA_CERT_EXPIRED, EXPIRED_MESSAGE);
        } catch (CertificateNotYetValidException e) {
            throw rejection(ErrorCode.CA_CERT_NOT_YET_VALID, NOT_YET_VALID_MESSAGE);
        }
    }

    private static XrdRuntimeException rejection(ErrorCode errorCode, String message) {
        return XrdRuntimeException.systemException(errorCode).details(message).build();
    }
}
