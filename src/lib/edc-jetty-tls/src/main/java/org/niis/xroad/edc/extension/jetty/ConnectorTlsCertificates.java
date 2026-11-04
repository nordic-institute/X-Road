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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHashOrThrow;

/**
 * Checks and describes the certificate in a connector TLS keystore. A private key that does not belong to its
 * certificate passes keystore construction and Jetty's own load, and then fails every handshake, so it is
 * caught here before the keystore is served.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ConnectorTlsCertificates {

    private static final byte[] PROBE = "connector-tls-key-pair-probe".getBytes(StandardCharsets.UTF_8);
    private static final String UNKNOWN = "unknown";

    /**
     * The leaf certificate of the keystore's connector TLS entry, once its private key is proven to belong to it.
     * A key of an algorithm this check has no signature scheme for is accepted without the proof.
     */
    static X509Certificate leafOfMatchingKey(KeyStore keyStore) {
        try {
            var key = keyStore.getKey(InternalSSLKey.KEY_ALIAS, InternalSSLKey.getKEY_PASSWORD());
            var certificate = keyStore.getCertificate(InternalSSLKey.KEY_ALIAS);
            if (!(key instanceof PrivateKey privateKey) || !(certificate instanceof X509Certificate leaf)) {
                throw new ConnectorTlsKeyStoreLoadException(
                        "The connector TLS keystore has no private key entry with an X.509 certificate");
            }
            if (!keyMatches(privateKey, leaf.getPublicKey())) {
                throw new ConnectorTlsKeyStoreLoadException(
                        "The connector TLS private key does not match the public key of the certificate");
            }
            return leaf;
        } catch (GeneralSecurityException e) {
            throw new ConnectorTlsKeyStoreLoadException("Cannot read the connector TLS entry from the keystore", e);
        }
    }

    /**
     * The fingerprint of the keystore's connector TLS certificate, or {@code unknown} when it cannot be read.
     */
    static String fingerprintOrUnknown(KeyStore keyStore) {
        try {
            return keyStore.getCertificate(InternalSSLKey.KEY_ALIAS) instanceof X509Certificate certificate
                    ? hashOrUnknown(certificate)
                    : UNKNOWN;
        } catch (GeneralSecurityException _) {
            return UNKNOWN;
        }
    }

    static String describe(X509Certificate certificate) {
        return "%s (SAN %s, SHA-256 %s, expires %s)".formatted(certificate.getSubjectX500Principal().getName(),
                subjectAlternativeNames(certificate), hashOrUnknown(certificate), certificate.getNotAfter().toInstant());
    }

    private static String hashOrUnknown(X509Certificate certificate) {
        try {
            return calculateCertHexHashOrThrow(certificate);
        } catch (XrdRuntimeException _) {
            return UNKNOWN;
        }
    }

    private static String subjectAlternativeNames(X509Certificate certificate) {
        try {
            Collection<List<?>> names = certificate.getSubjectAlternativeNames();
            if (names == null || names.isEmpty()) {
                return "none";
            }
            return names.stream().map(entry -> String.valueOf(entry.get(1))).collect(Collectors.joining(", "));
        } catch (GeneralSecurityException _) {
            return UNKNOWN;
        }
    }

    private static boolean keyMatches(PrivateKey privateKey, PublicKey publicKey) {
        return switch (privateKey.getAlgorithm()) {
            case "RSA" -> signsAndVerifies("SHA256withRSA", privateKey, publicKey);
            case "EC" -> signsAndVerifies("SHA256withECDSA", privateKey, publicKey);
            case "Ed25519", "EdDSA" -> signsAndVerifies("EdDSA", privateKey, publicKey);
            default -> true;
        };
    }

    private static boolean signsAndVerifies(String algorithm, PrivateKey privateKey, PublicKey publicKey) {
        try {
            var signer = Signature.getInstance(algorithm);
            signer.initSign(privateKey);
            signer.update(PROBE);
            var signature = signer.sign();
            var verifier = Signature.getInstance(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(PROBE);
            return verifier.verify(signature);
        } catch (GeneralSecurityException _) {
            return false;
        }
    }
}
