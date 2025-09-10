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
package org.niis.xroad.common.vault;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.IOException;
import java.io.StringWriter;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

import static org.bouncycastle.openssl.PEMParser.TYPE_CERTIFICATE;
import static org.bouncycastle.openssl.PEMParser.TYPE_PRIVATE_KEY;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public interface VaultClient {
    String PRIVATEKEY_KEY = "privateKey";
    String CERTIFICATE_KEY = "certificate";

    String INTERNAL_TLS_CREDENTIALS_PATH = "tls/internal";
    String OPMONITOR_TLS_CREDENTIALS_PATH = "tls/opmonitor";
    String PROXY_UI_API_TLS_CREDENTIALS_PATH = "tls/proxy-ui-api";

    InternalSSLKey getInternalTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    InternalSSLKey getOpmonitorTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    InternalSSLKey getProxyUyApiTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    void createInternalTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    void createOpmonitorTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    void createProxyUiApiTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    default String toPem(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject(TYPE_PRIVATE_KEY, privateKey.getEncoded());
            pemWriter.writeObject(pemObject);
        }
        return stringWriter.toString();
    }

    default String toPem(X509Certificate certificate) throws IOException, CertificateEncodingException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject(TYPE_CERTIFICATE, certificate.getEncoded());
            pemWriter.writeObject(pemObject);
        }
        return stringWriter.toString();
    }
}
