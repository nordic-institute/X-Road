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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.EncoderUtils;

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.restapi.openapi.model.CertificateDetails;
import org.niis.xroad.restapi.openapi.model.KeyUsage;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static ee.ria.xroad.common.crypto.NamedCurves.getCurveName;
import static ee.ria.xroad.common.crypto.NamedCurves.getEncodedPoint;

/**
 * Converts an {@link X509Certificate} into the shared {@link CertificateDetails} OpenAPI model.
 */
@Component
@SuppressWarnings("checkstyle:MagicNumber") // index numbers are the clearest way to represent the key usage bits
public class DsTlsCertificateDetailsConverter {

    private static final int RADIX_FOR_HEX = 16;

    // maps a X509Certificate.getKeyUsage bit index to the corresponding KeyUsage value
    private static final Map<Integer, KeyUsage> BIT_TO_USAGE = new HashMap<>();

    static {
        BIT_TO_USAGE.put(0, KeyUsage.DIGITAL_SIGNATURE);
        BIT_TO_USAGE.put(1, KeyUsage.NON_REPUDIATION);
        BIT_TO_USAGE.put(2, KeyUsage.KEY_ENCIPHERMENT);
        BIT_TO_USAGE.put(3, KeyUsage.DATA_ENCIPHERMENT);
        BIT_TO_USAGE.put(4, KeyUsage.KEY_AGREEMENT);
        BIT_TO_USAGE.put(5, KeyUsage.KEY_CERT_SIGN);
        BIT_TO_USAGE.put(6, KeyUsage.CRL_SIGN);
        BIT_TO_USAGE.put(7, KeyUsage.ENCIPHER_ONLY);
        BIT_TO_USAGE.put(8, KeyUsage.DECIPHER_ONLY);
    }

    public CertificateDetails convert(X509Certificate certificate) {
        var details = new CertificateDetails()
                .hash(CryptoUtils.calculateCertHexHashOrThrow(certificate).toUpperCase())
                .version(certificate.getVersion())
                .serial(String.valueOf(certificate.getSerialNumber()))
                .signatureAlgorithm(certificate.getSigAlgName())
                .issuerDistinguishedName(certificate.getIssuerX500Principal().toString())
                .issuerCommonName(issuerCommonNameOrNull(certificate))
                .subjectDistinguishedName(certificate.getSubjectX500Principal().toString())
                .subjectCommonName(subjectCommonNameOrNull(certificate))
                .subjectAlternativeNames(subjectAlternativeNamesOrNull(certificate))
                .publicKeyAlgorithm(certificate.getPublicKey().getAlgorithm())
                .signature(EncoderUtils.encodeHex(certificate.getSignature()))
                .notBefore(certificate.getNotBefore().toInstant().atOffset(ZoneOffset.UTC))
                .notAfter(certificate.getNotAfter().toInstant().atOffset(ZoneOffset.UTC))
                .keyUsages(convertKeyUsages(certificate.getKeyUsage()));

        PublicKey publicKey = certificate.getPublicKey();
        switch (publicKey) {
            case RSAPublicKey rsaPublicKey -> details
                    .rsaPublicKeyExponent(rsaPublicKey.getPublicExponent().intValue())
                    .rsaPublicKeyModulus(rsaPublicKey.getModulus().toString(RADIX_FOR_HEX));
            case ECPublicKey ecPublicKey -> details
                    .ecPublicParameters(getCurveName(ecPublicKey))
                    .ecPublicKeyPoint(getEncodedPoint(ecPublicKey));
            default -> throw XrdRuntimeException.systemInternalError(
                    "Unexpected type of public key: " + publicKey.getClass().getName());
        }
        return details;
    }

    private String issuerCommonNameOrNull(X509Certificate certificate) {
        try {
            return CertUtils.getIssuerCommonName(certificate);
        } catch (XrdRuntimeException e) {
            return null;
        }
    }

    private String subjectCommonNameOrNull(X509Certificate certificate) {
        try {
            return CertUtils.getSubjectCommonName(certificate);
        } catch (XrdRuntimeException e) {
            return null;
        }
    }

    private String subjectAlternativeNamesOrNull(X509Certificate certificate) {
        try {
            return CertUtils.getSubjectAlternativeNames(certificate);
        } catch (XrdRuntimeException e) {
            return null;
        }
    }

    private Set<KeyUsage> convertKeyUsages(boolean[] keyUsageBits) {
        EnumSet<KeyUsage> usages = EnumSet.noneOf(KeyUsage.class);
        if (keyUsageBits != null) {
            for (int i = 0; i < Math.min(BIT_TO_USAGE.size(), keyUsageBits.length); i++) {
                if (keyUsageBits[i]) {
                    usages.add(BIT_TO_USAGE.get(i));
                }
            }
        }
        return usages;
    }
}
