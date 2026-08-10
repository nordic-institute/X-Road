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
package org.niis.xroad.restapi.validator;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import org.niis.xroad.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Collection;

import static org.niis.xroad.common.core.exception.ErrorCode.CERT_VALIDITY_TIME;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CERTIFICATE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_TLS_PRIVATE_KEY;
import static org.niis.xroad.common.core.exception.ErrorCode.TLS_KEY_CERTIFICATE_MISMATCH;

@Component
public class DsTlsMaterialValidator {

    private static final byte[] MATCH_CHALLENGE = "xroad-tls-key-certificate-match".getBytes(StandardCharsets.UTF_8);

    public InternalSSLKey validate(byte[] keyBytes, byte[] certificateChainBytes) {
        PrivateKey privateKey = parsePrivateKey(keyBytes);
        X509Certificate[] certificateChain = parseCertificateChain(certificateChainBytes);
        X509Certificate leafCertificate = certificateChain[0];

        verifyValidityWindow(leafCertificate);
        verifyKeyMatchesCertificate(privateKey, leafCertificate);

        return new InternalSSLKey(privateKey, certificateChain);
    }

    private PrivateKey parsePrivateKey(byte[] keyBytes) {
        try {
            return CryptoUtils.getPrivateKey(new ByteArrayInputStream(keyBytes));
        } catch (Exception e) {
            throw new BadRequestException(e, INVALID_TLS_PRIVATE_KEY.build());
        }
    }

    private X509Certificate[] parseCertificateChain(byte[] certificateChainBytes) {
        Collection<X509Certificate> certificates;
        try {
            certificates = CryptoUtils.readCertificates(certificateChainBytes);
        } catch (Exception e) {
            throw new BadRequestException(e, INVALID_CERTIFICATE.build());
        }
        if (certificates.isEmpty()) {
            throw new BadRequestException(INVALID_CERTIFICATE.build());
        }
        return certificates.toArray(X509Certificate[]::new);
    }

    private void verifyValidityWindow(X509Certificate certificate) {
        if (!CertUtils.isValid(certificate)) {
            throw new BadRequestException(CERT_VALIDITY_TIME.build());
        }
    }

    private void verifyKeyMatchesCertificate(PrivateKey privateKey, X509Certificate certificate) {
        try {
            String algorithm = signatureAlgorithm(privateKey);

            Signature signer = Signature.getInstance(algorithm);
            signer.initSign(privateKey);
            signer.update(MATCH_CHALLENGE);
            byte[] signature = signer.sign();

            Signature verifier = Signature.getInstance(algorithm);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(MATCH_CHALLENGE);

            if (!verifier.verify(signature)) {
                throw new BadRequestException(TLS_KEY_CERTIFICATE_MISMATCH.build());
            }
        } catch (GeneralSecurityException e) {
            throw new BadRequestException(e, TLS_KEY_CERTIFICATE_MISMATCH.build());
        }
    }

    private String signatureAlgorithm(PrivateKey privateKey) {
        return switch (privateKey.getAlgorithm()) {
            case "RSA" -> SignAlgorithm.SHA256_WITH_RSA.name();
            case "EC" -> SignAlgorithm.SHA256_WITH_ECDSA.name();
            default -> throw new BadRequestException(INVALID_TLS_PRIVATE_KEY.build());
        };
    }
}
