/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.centralserver.restapi.dto.converter;

import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.niis.xroad.centralserver.restapi.dto.CertificateAuthority;
import org.niis.xroad.centralserver.restapi.dto.CertificateDetails;
import org.niis.xroad.centralserver.restapi.entity.CaInfo;
import org.niis.xroad.centralserver.restapi.service.exception.ValidationFailureException;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Set;

import static ee.ria.xroad.common.util.CertUtils.getIssuerCommonName;
import static ee.ria.xroad.common.util.CertUtils.getSubjectAlternativeNames;
import static ee.ria.xroad.common.util.CertUtils.getSubjectCommonName;
import static java.lang.String.valueOf;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage.INVALID_CERTIFICATE;

@Component
@RequiredArgsConstructor
public class CaInfoConverter {

    private static final int RADIX_FOR_HEX = 16;

    private final KeyUsageConverter keyUsageConverter;

    @SneakyThrows
    public CertificateDetails toCertificateDetails(CaInfo caInfo) {

        final X509Certificate[] certificates = CertUtils.readCertificateChain(caInfo.getCert());
        final X509Certificate certificate = certificates[0];

        final CertificateDetails certificateDetails = new CertificateDetails()
                .setHash(CryptoUtils.calculateCertHexHash(certificate.getEncoded()).toUpperCase())
                .setVersion(certificate.getVersion())
                .setSerial(valueOf(certificate.getSerialNumber()))
                .setSignatureAlgorithm(certificate.getSigAlgName())
                .setIssuerDistinguishedName(certificate.getIssuerDN().getName())
                .setNotBefore(certificate.getNotBefore().toInstant())
                .setNotAfter(certificate.getNotAfter().toInstant())
                .setSubjectDistinguishedName(certificate.getSubjectDN().getName())
                .setPublicKeyAlgorithm(certificate.getPublicKey().getAlgorithm())
                .setKeyUsages(keyUsageConverter.convert(certificate.getKeyUsage()))
                .setSubjectAlternativeNames(getSubjectAlternativeNames(certificate))
                .setSignature(CryptoUtils.encodeHex(certificate.getSignature()))
                .setIssuerCommonName(getIssuerCommonName(certificate))
                .setSubjectCommonName(getSubjectCommonName(certificate));

        final PublicKey publicKey = certificate.getPublicKey();
        if (publicKey instanceof RSAPublicKey) {
            final RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            certificateDetails.setRsaPublicKeyExponent(rsaPublicKey.getPublicExponent());
            certificateDetails.setRsaPublicKeyModulus(rsaPublicKey.getModulus().toString(RADIX_FOR_HEX));
        }

        return certificateDetails;
    }

    public CertificateAuthority toCertificateAuthority(CaInfo caInfo) {
        return new CertificateAuthority()
                .setId(caInfo.getId())
                .setCaCertificate(this.toCertificateDetails(caInfo))
                .setUpdatedAt(caInfo.getUpdatedAt())
                .setCreatedAt(caInfo.getCreatedAt());
    }

    public Set<CertificateAuthority> toCertificateAuthorities(Collection<CaInfo> caInfos) {
        return caInfos.stream()
                .map(this::toCertificateAuthority)
                .collect(toSet());
    }

    public CaInfo toCaInfo(byte[] certificate) {
        try {
            final X509Certificate[] certificates = CertUtils.readCertificateChain(certificate);
            final X509Certificate cert = certificates[0];

            final CaInfo caInfo = new CaInfo();
            caInfo.setCert(certificate);
            caInfo.setValidFrom(cert.getNotBefore().toInstant());
            caInfo.setValidTo(cert.getNotAfter().toInstant());
            return caInfo;
        } catch (Exception e) {
            throw new ValidationFailureException(INVALID_CERTIFICATE);
        }
    }

}
