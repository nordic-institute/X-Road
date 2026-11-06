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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConnectorTlsCertificatesTest {

    private static final String SUBJECT = "CN=eddsa-connector-tls";

    @Test
    void acceptsAnEd25519KeyThatBelongsToItsCertificate() throws Exception {
        var keyPair = ed25519KeyPair();
        var certificate = selfSigned(keyPair, keyPair);

        var leaf = ConnectorTlsCertificates.leafOfMatchingKey(keyStoreOf(keyPair, certificate));

        assertThat(leaf).isEqualTo(certificate);
    }

    @Test
    void rejectsAnEd25519KeyThatDoesNotBelongToItsCertificate() throws Exception {
        var keyPair = ed25519KeyPair();
        var otherKeyPair = ed25519KeyPair();
        var certificateOfOtherKey = selfSigned(otherKeyPair, otherKeyPair);

        var keyStore = keyStoreOf(keyPair, certificateOfOtherKey);

        assertThatThrownBy(() -> ConnectorTlsCertificates.leafOfMatchingKey(keyStore))
                .isInstanceOf(ConnectorTlsKeyStoreLoadException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void acceptsAnEd448KeyThatBelongsToItsCertificate() throws Exception {
        var keyPair = keyPair("Ed448");
        var certificate = selfSigned("Ed448", keyPair, keyPair);

        var leaf = ConnectorTlsCertificates.leafOfMatchingKey(keyStoreOf(keyPair, certificate));

        assertThat(leaf).isEqualTo(certificate);
    }

    @Test
    void describesACertificateWithItsSubjectAndFingerprint() throws Exception {
        var keyPair = ed25519KeyPair();
        var certificate = selfSigned(keyPair, keyPair);

        assertThat(ConnectorTlsCertificates.describe(certificate))
                .contains(SUBJECT)
                .contains("SAN none")
                .doesNotContain("SHA-256 unknown");
    }

    private static KeyPair ed25519KeyPair() throws Exception {
        return keyPair("Ed25519");
    }

    private static KeyPair keyPair(String algorithm) throws Exception {
        return KeyPairGenerator.getInstance(algorithm).generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair subjectKeys, KeyPair signingKeys) throws Exception {
        return selfSigned("Ed25519", subjectKeys, signingKeys);
    }

    private static X509Certificate selfSigned(String algorithm, KeyPair subjectKeys, KeyPair signingKeys)
            throws Exception {
        var subject = new X500Name(SUBJECT);
        var now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(3600)), subject, subjectKeys.getPublic());
        var signer = new JcaContentSignerBuilder(algorithm).build(signingKeys.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static KeyStore keyStoreOf(KeyPair keyPair, X509Certificate certificate) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, keyPair.getPrivate(), InternalSSLKey.getKEY_PASSWORD(),
                new X509Certificate[] {certificate});
        return keyStore;
    }
}
