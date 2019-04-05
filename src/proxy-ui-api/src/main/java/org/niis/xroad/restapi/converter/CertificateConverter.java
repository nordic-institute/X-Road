/**
 * The MIT License
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
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Converter Certificate related data between openapi and (jaxb) service classes
 */
@Component
public class CertificateConverter {

    /**
     * convert CertificateInfo into openapi Certificate class
     * @param certificateInfo
     * @return
     */
    public org.niis.xroad.restapi.openapi.model.Certificate convert(CertificateInfo certificateInfo) {
        org.niis.xroad.restapi.openapi.model.Certificate certificate =
                new org.niis.xroad.restapi.openapi.model.Certificate();

        X509Certificate x509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
        certificate.setIssuerCommonName(CertUtils.getIssuerCommonName(x509Certificate));
        certificate.setIssuerDistinguishedName(x509Certificate.getIssuerDN().getName());
        certificate.setSubjectCommonName(CertUtils.getSubjectCommonName(x509Certificate));
        certificate.setSubjectDistinguishedName(x509Certificate.getSubjectDN().getName());

        certificate.setSerial(x509Certificate.getSerialNumber().toString());
        certificate.setVersion(x509Certificate.getVersion());

        certificate.setSignatureAlgorithm(x509Certificate.getSigAlgName());
        certificate.setKeyAlgorithm(x509Certificate.getPublicKey().getAlgorithm());

        if (certificateInfo.isActive()) {
            certificate.setState(org.niis.xroad.restapi.openapi.model.Certificate.StateEnum.IN_USE);
        } else {
            certificate.setState(org.niis.xroad.restapi.openapi.model.Certificate.StateEnum.DISABLED);
        }
        certificate.setNotBefore(asOffsetDateTime(x509Certificate.getNotBefore()));
        certificate.setNotAfter(asOffsetDateTime(x509Certificate.getNotAfter()));
        try {
            certificate.setHash(CryptoUtils.calculateCertHexHash(certificateInfo.getCertificateBytes()).toUpperCase());
        } catch (Exception ex) {
            throw new IllegalStateException("cannot calculate cert hash", ex);
        }
        return certificate;
    }

    private OffsetDateTime asOffsetDateTime(Date notBefore) {
        return notBefore.toInstant().atOffset(ZoneOffset.UTC);
    }
}
