/*
 * The MIT License
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
package org.niis.xroad.cs.admin.core.converter;

import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.dto.SecurityServerAuthenticationCertificateDetails;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;

import static ee.ria.xroad.common.util.CertUtils.getIssuerCommonName;
import static ee.ria.xroad.common.util.CertUtils.getSubjectAlternativeNames;
import static ee.ria.xroad.common.util.CertUtils.getSubjectCommonName;
import static java.lang.String.valueOf;

@Component
@RequiredArgsConstructor
public class CertificateConverter {

    private static final int RADIX_FOR_HEX = 16;
    private final KeyUsageConverter keyUsageConverter;

    public CertificateDetails toCertificateDetails(final byte[] cert) {
        if (cert == null) {
            return null;
        }
        CertificateDetails certificateDetails = new CertificateDetails();
        populateCertificateDetails(certificateDetails, cert);
        return certificateDetails;
    }

    public CertificateDetails toCertificateDetails(final X509Certificate certificate) {
        CertificateDetails certificateDetails = new CertificateDetails();
        populateCertificateDetails(certificateDetails, certificate);
        return certificateDetails;
    }

    public SecurityServerAuthenticationCertificateDetails toCertificateDetails(final AuthCertEntity authCert) {
        SecurityServerAuthenticationCertificateDetails authCertificateDetails =
                new SecurityServerAuthenticationCertificateDetails(authCert.getId());
        populateCertificateDetails(authCertificateDetails, authCert.getCert());
        return authCertificateDetails;
    }

    @SneakyThrows
    private void populateCertificateDetails(final CertificateDetails certificateDetails, final X509Certificate certificate) {

        populateCertificateDetails(certificateDetails, certificate, certificate.getEncoded());
    }

    @SneakyThrows
    private void populateCertificateDetails(final CertificateDetails certificateDetails, byte[] cert) {
        final X509Certificate[] certificates = CertUtils.readCertificateChain(cert);
        final X509Certificate certificate = certificates[0];

        populateCertificateDetails(certificateDetails, certificate, cert);
    }

    @SneakyThrows
    private void populateCertificateDetails(final CertificateDetails certificateDetails, final X509Certificate certificate, byte[] cert) {
        certificateDetails
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
                .setSubjectCommonName(getSubjectCommonName(certificate))
                .setEncoded(cert);

        final PublicKey publicKey = certificate.getPublicKey();
        if (publicKey instanceof RSAPublicKey) {
            final RSAPublicKey rsaPublicKey = (RSAPublicKey) publicKey;
            certificateDetails.setRsaPublicKeyExponent(rsaPublicKey.getPublicExponent());
            certificateDetails.setRsaPublicKeyModulus(rsaPublicKey.getModulus().toString(RADIX_FOR_HEX));
        }
    }
}
