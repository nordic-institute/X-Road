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
package org.niis.xroad.securityserver.restapi.dstls;

import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;

import lombok.experimental.UtilityClass;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import javax.security.auth.x500.X500Principal;

import java.security.KeyPair;

/**
 * Builds the PKCS#10 CSR for a DS TLS certificate enrollment or renewal directly with BouncyCastle: the public
 * hostname as both the subject common name and a DNS Subject Alternative Name. Self-contained on purpose, so that
 * the shared, DN-only {@code CertUtils.generateCertRequest} utility other flows depend on stays untouched.
 */
@UtilityClass
class DsTlsCsrBuilder {

    /**
     * Builds a DER-encoded PKCS#10 CSR for {@code hostname}, signed by {@code keyPair}.
     *
     * @param keyPair  the freshly generated DS TLS key pair the CSR is built for
     * @param hostname the server's public DataSpace-facing hostname, used as both the subject CN and the DNS SAN
     * @return the DER-encoded certificate request
     */
    byte[] build(KeyPair keyPair, String hostname) {
        try {
            X500Principal subject = new X500Principal("CN=" + hostname);

            ExtensionsGenerator extensionsGenerator = new ExtensionsGenerator();
            GeneralNames subjectAltName = new GeneralNames(new GeneralName(GeneralName.dNSName, hostname));
            extensionsGenerator.addExtension(Extension.subjectAlternativeName, false, subjectAltName);

            var requestBuilder = new JcaPKCS10CertificationRequestBuilder(subject, keyPair.getPublic())
                    .addAttribute(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extensionsGenerator.generate());

            ContentSigner contentSigner = new JcaContentSignerBuilder(SignAlgorithm.SHA256_WITH_RSA.name()).build(keyPair.getPrivate());

            return requestBuilder.build(contentSigner).getEncoded();
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }
}
