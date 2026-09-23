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

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.security.auth.x500.X500Principal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Builds the PKCS#10 CSR for a DataSpace TLS certificate: the entered distinguished name as the subject and,
 * when given, exactly one DNS Subject Alternative Name. Shared between the manual CSR download and an ACME
 * order, so both paths produce identical CSR shapes for the same input.
 */
@UtilityClass
public class DsTlsCsrBuilder {

    /**
     * @param subjectAltName single DNS subject alternative name, or {@code null} for a distinguished-name-only CSR
     * @return a PEM-encoded PKCS#10 CSR
     * @throws IllegalArgumentException if {@code distinguishedName} cannot be parsed as an X.500 name
     */
    public byte[] buildPem(PrivateKey privateKey, PublicKey publicKey, String distinguishedName, String subjectAltName) {
        PKCS10CertificationRequest request = buildRequest(privateKey, publicKey, distinguishedName, subjectAltName);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                JcaPEMWriter pemWriter = new JcaPEMWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            pemWriter.writeObject(request);
            pemWriter.flush();
            return output.toByteArray();
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    /**
     * @param subjectAltName single DNS subject alternative name, or {@code null} for a distinguished-name-only CSR
     * @return a DER-encoded PKCS#10 CSR
     * @throws IllegalArgumentException if {@code distinguishedName} cannot be parsed as an X.500 name
     */
    public byte[] buildDer(PrivateKey privateKey, PublicKey publicKey, String distinguishedName, String subjectAltName) {
        try {
            return buildRequest(privateKey, publicKey, distinguishedName, subjectAltName).getEncoded();
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private PKCS10CertificationRequest buildRequest(PrivateKey privateKey, PublicKey publicKey, String distinguishedName,
                                                     String subjectAltName) {
        X500Principal subject = new X500Principal(distinguishedName);

        try {
            var requestBuilder = new JcaPKCS10CertificationRequestBuilder(subject, publicKey);
            if (subjectAltName != null) {
                ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
                GeneralNames sans = new GeneralNames(new GeneralName(GeneralName.dNSName, subjectAltName));
                extensionsGenerator.addExtension(Extension.subjectAlternativeName, false, sans);
                requestBuilder.addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());
            }

            ContentSigner contentSigner = new JcaContentSignerBuilder(SignAlgorithm.SHA256_WITH_RSA.name()).build(privateKey);
            return requestBuilder.build(contentSigner);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }
}
