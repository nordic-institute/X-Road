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

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DsTlsCsrBuilderTest {

    private static final String MULTI_ATTRIBUTE_DN = "C=FI, O=X-Road Test, OU=X-Road Test CA OU, CN=ds.example.org";

    @Test
    void buildDerShouldCarryTheDistinguishedNameAsSubjectWithNoSanExtensionWhenAbsent() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(
                DsTlsCsrBuilder.buildDer(keyPair.getPrivate(), keyPair.getPublic(), MULTI_ATTRIBUTE_DN, null));

        assertThat(csr.getSubject()).isEqualTo(new X500Name(MULTI_ATTRIBUTE_DN));
        assertThat(csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)).isEmpty();
    }

    @Test
    void buildDerShouldCarrySubjectAndSingleDnsSanWhenPresent() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(
                DsTlsCsrBuilder.buildDer(keyPair.getPrivate(), keyPair.getPublic(), MULTI_ATTRIBUTE_DN, "ds.example.org"));

        assertThat(csr.getSubject()).isEqualTo(new X500Name(MULTI_ATTRIBUTE_DN));
        GeneralNames sans = extractSans(csr);
        assertThat(sans.getNames()).hasSize(1);
        assertThat(sans.getNames()[0].getTagNo()).isEqualTo(GeneralName.dNSName);
        assertThat(sans.getNames()[0].getName().toString()).isEqualTo("ds.example.org");
    }

    @Test
    void buildDerShouldSignTheRequestWithTheGivenKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        PKCS10CertificationRequest csr = new PKCS10CertificationRequest(
                DsTlsCsrBuilder.buildDer(keyPair.getPrivate(), keyPair.getPublic(), "CN=ds.example.org", "ds.example.org"));

        var publicKeyFromCsr = new JcaPEMKeyConverter().getPublicKey(csr.getSubjectPublicKeyInfo());
        assertThat(publicKeyFromCsr).isEqualTo(keyPair.getPublic());
        var verifierProvider = new JcaContentVerifierProviderBuilder().build(keyPair.getPublic());
        assertThat(new JcaPKCS10CertificationRequest(csr).isSignatureValid(verifierProvider)).isTrue();
    }

    @Test
    void buildDerShouldRejectAMalformedDistinguishedName() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        assertThatThrownBy(() -> DsTlsCsrBuilder.buildDer(keyPair.getPrivate(), keyPair.getPublic(), "not a dn", "ds.example.org"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildPemShouldEncodeTheSameRequestAsPem() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        byte[] pemBytes = DsTlsCsrBuilder.buildPem(keyPair.getPrivate(), keyPair.getPublic(), MULTI_ATTRIBUTE_DN, "ds.example.org");

        PKCS10CertificationRequest csr = parsePem(pemBytes);
        assertThat(csr.getSubject()).isEqualTo(new X500Name(MULTI_ATTRIBUTE_DN));
        assertThat(extractSans(csr).getNames()).hasSize(1);
    }

    @Test
    void buildPemShouldRejectAMalformedDistinguishedName() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        assertThatThrownBy(() -> DsTlsCsrBuilder.buildPem(keyPair.getPrivate(), keyPair.getPublic(), "not a dn", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GeneralNames extractSans(PKCS10CertificationRequest csr) {
        var attributes = csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest);
        assertThat(attributes).hasSize(1);
        ASN1Encodable[] values = attributes[0].getAttrValues().toArray();
        Extensions extensions = Extensions.getInstance(values[0]);
        return GeneralNames.fromExtensions(extensions, org.bouncycastle.asn1.x509.Extension.subjectAlternativeName);
    }

    private static PKCS10CertificationRequest parsePem(byte[] pemBytes) throws Exception {
        try (PEMParser pemParser = new PEMParser(
                new InputStreamReader(new ByteArrayInputStream(pemBytes), StandardCharsets.UTF_8))) {
            return (PKCS10CertificationRequest) pemParser.readObject();
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
