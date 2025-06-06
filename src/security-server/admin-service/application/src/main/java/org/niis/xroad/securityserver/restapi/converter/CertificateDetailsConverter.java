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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.EncoderUtils;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateDetailsDto;
import org.niis.xroad.serverconf.model.Certificate;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;

import static ee.ria.xroad.common.crypto.NamedCurves.getCurveName;
import static ee.ria.xroad.common.crypto.NamedCurves.getEncodedPoint;

/**
 * Converter for CertificateDetailsDto related data between openapi and service domain classes
 */
@Component
@RequiredArgsConstructor
public class CertificateDetailsConverter {

    public static final int RADIX_FOR_HEX = 16;
    private final KeyUsageConverter keyUsageConverter;

    /**
     * convert Certificate into openapi Certificate class
     * @param certificate certificate
     * @return CertificateDetailsDto
     */
    public CertificateDetailsDto convert(Certificate certificate) {
        X509Certificate x509Certificate = CryptoUtils.readCertificate(certificate.getData());
        return convert(x509Certificate);
    }

    /**
     * convert CertificateInfo into openapi Certificate class
     * @param certificateInfo
     * @return
     */
    public CertificateDetailsDto convert(CertificateInfo certificateInfo) {
        X509Certificate x509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
        return convert(x509Certificate);
    }

    /**
     * convert X509Certificate into openapi Certificate class.
     * certificate.state will be null.
     * @param x509Certificate
     * @return
     */
    public CertificateDetailsDto convert(X509Certificate x509Certificate) {
        CertificateDetailsDto certificate = new CertificateDetailsDto();

        String issuerCommonName = null;
        String subjectCommonName = null;
        String subjectAlternativeNames = null;
        try {
            issuerCommonName = CertUtils.getIssuerCommonName(x509Certificate);
        } catch (CodedException didNotFindIssuerCommonName) {
        }
        try {
            subjectCommonName = CertUtils.getSubjectCommonName(x509Certificate);
        } catch (CodedException didNotFindSubjectCommonName) {
        }
        try {
            subjectAlternativeNames = CertUtils.getSubjectAlternativeNames(x509Certificate);
        } catch (CodedException certParsingFailed) {
        }
        certificate.setIssuerCommonName(issuerCommonName);
        certificate.setIssuerDistinguishedName(x509Certificate.getIssuerX500Principal().toString());
        certificate.setSubjectCommonName(subjectCommonName);
        certificate.setSubjectDistinguishedName(x509Certificate.getSubjectX500Principal().toString());
        certificate.setSubjectAlternativeNames(subjectAlternativeNames);

        certificate.setSerial(x509Certificate.getSerialNumber().toString());
        certificate.setVersion(x509Certificate.getVersion());

        certificate.setSignatureAlgorithm(x509Certificate.getSigAlgName());
        certificate.setPublicKeyAlgorithm(x509Certificate.getPublicKey().getAlgorithm());

        certificate.setKeyUsages(new HashSet<>(keyUsageConverter.convert(x509Certificate.getKeyUsage())));

        PublicKey publicKey = x509Certificate.getPublicKey();
        switch (publicKey) {
            case RSAPublicKey rsaPublicKey -> {
                certificate.setRsaPublicKeyExponent(rsaPublicKey.getPublicExponent().intValue());
                certificate.setRsaPublicKeyModulus(rsaPublicKey.getModulus().toString(RADIX_FOR_HEX));
            }
            case ECPublicKey ecPublicKey -> {
                certificate.setEcPublicParameters(getCurveName(ecPublicKey));
                certificate.setEcPublicKeyPoint(getEncodedPoint(ecPublicKey));
            }
            default -> throw new IllegalStateException("Unexpected type of public key: " + publicKey.getClass().getName());
        }


        certificate.setSignature(EncoderUtils.encodeHex(x509Certificate.getSignature()));
        certificate.setNotBefore(FormatUtils.fromDateToOffsetDateTime(x509Certificate.getNotBefore()));
        certificate.setNotAfter(FormatUtils.fromDateToOffsetDateTime(x509Certificate.getNotAfter()));
        try {
            certificate.setHash(CryptoUtils.calculateCertHexHash(x509Certificate.getEncoded()).toUpperCase());
        } catch (Exception ex) {
            throw new IllegalStateException("cannot calculate cert hash", ex);
        }
        return certificate;
    }
}
