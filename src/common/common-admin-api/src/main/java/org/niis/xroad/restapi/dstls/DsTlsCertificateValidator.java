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

import ee.ria.xroad.common.util.CryptoUtils;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.niis.xroad.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Collection;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_EXPIRED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_YET_VALID;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_PARSE_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_CERTIFICATE_MISMATCH;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_PARSE_FAILED;

/**
 * Shared validator for a manually uploaded DS TLS private key and certificate chain, used by both Security
 * Server and Central Server admin services. Checks that the material parses, that the leaf certificate's
 * validity window covers the current time, and that the key matches the leaf certificate's public key.
 * <p>
 * Deliberately does not verify that the chain is signed by a designated {@code approvedDsTlsCa} entry -
 * peers enforce that trust, the upload only needs to be internally consistent.
 */
@Component
public class DsTlsCertificateValidator {

    private static final byte[] KEY_MATCH_CHALLENGE = "x-road-ds-tls-key-certificate-match".getBytes(StandardCharsets.UTF_8);

    /**
     * Parses and validates an uploaded private key and certificate chain.
     *
     * @param keyBytes              PEM encoded private key (PKCS#1 or PKCS#8, RSA or EC)
     * @param certificateChainBytes PEM encoded certificate or certificate chain, leaf certificate first
     * @return the parsed key and certificate chain
     * @throws BadRequestException with an actionable {@link org.niis.xroad.common.core.exception.ErrorCode}
     *                              if the material is malformed, expired/not yet valid, or the key and
     *                              certificate do not match
     */
    public DsTlsUploadMaterial validate(byte[] keyBytes, byte[] certificateChainBytes) {
        PrivateKey key = parsePrivateKey(keyBytes);
        X509Certificate[] chain = parseCertificateChain(certificateChainBytes);
        X509Certificate leaf = chain[0];

        checkValidityWindow(leaf);
        checkKeyMatchesCertificate(key, leaf.getPublicKey());

        return new DsTlsUploadMaterial(key, chain);
    }

    private PrivateKey parsePrivateKey(byte[] keyBytes) {
        try (PEMParser pemParser = new PEMParser(
                new InputStreamReader(new ByteArrayInputStream(keyBytes), StandardCharsets.UTF_8))) {
            Object parsed = pemParser.readObject();
            var converter = new JcaPEMKeyConverter();
            if (parsed instanceof PEMKeyPair pemKeyPair) {
                return converter.getKeyPair(pemKeyPair).getPrivate();
            }
            if (parsed instanceof PrivateKeyInfo privateKeyInfo) {
                return converter.getPrivateKey(privateKeyInfo);
            }
            throw new BadRequestException(DS_TLS_KEY_PARSE_FAILED.build());
        } catch (IOException e) {
            throw new BadRequestException(e, DS_TLS_KEY_PARSE_FAILED.build());
        }
    }

    private X509Certificate[] parseCertificateChain(byte[] certificateChainBytes) {
        Collection<X509Certificate> certificates;
        try {
            certificates = CryptoUtils.readCertificates(certificateChainBytes);
        } catch (Exception e) {
            throw new BadRequestException(e, DS_TLS_CERTIFICATE_PARSE_FAILED.build());
        }
        if (certificates.isEmpty()) {
            throw new BadRequestException(DS_TLS_CERTIFICATE_PARSE_FAILED.build());
        }
        return certificates.toArray(X509Certificate[]::new);
    }

    private void checkValidityWindow(X509Certificate leaf) {
        try {
            leaf.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new BadRequestException(e, DS_TLS_CERTIFICATE_EXPIRED.build());
        } catch (CertificateNotYetValidException e) {
            throw new BadRequestException(e, DS_TLS_CERTIFICATE_NOT_YET_VALID.build());
        }
    }

    private void checkKeyMatchesCertificate(PrivateKey key, PublicKey certificatePublicKey) {
        try {
            String signatureAlgorithm = signatureAlgorithmFor(key.getAlgorithm());

            Signature signer = Signature.getInstance(signatureAlgorithm);
            signer.initSign(key);
            signer.update(KEY_MATCH_CHALLENGE);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(certificatePublicKey);
            verifier.update(KEY_MATCH_CHALLENGE);

            if (!verifier.verify(signature)) {
                throw new BadRequestException(DS_TLS_KEY_CERTIFICATE_MISMATCH.build());
            }
        } catch (GeneralSecurityException e) {
            throw new BadRequestException(e, DS_TLS_KEY_CERTIFICATE_MISMATCH.build());
        }
    }

    private String signatureAlgorithmFor(String keyAlgorithm) {
        return switch (keyAlgorithm) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            default -> throw new BadRequestException(DS_TLS_KEY_PARSE_FAILED.build());
        };
    }
}
