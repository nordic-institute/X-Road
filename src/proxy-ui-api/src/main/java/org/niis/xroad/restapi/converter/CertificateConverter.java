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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.model.CertificateType;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.niis.xroad.restapi.openapi.model.Certificate;
import org.niis.xroad.restapi.openapi.model.CertificateStatus;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.stereotype.Component;

import java.security.cert.X509Certificate;

/**
 * Converter Certificate related data between openapi and service domain classes
 */
@Component
public class CertificateConverter {

    /**
     * convert CertificateType into openapi Certificate class
     * @param certificateType
     * @return
     */
    public Certificate convert(CertificateType certificateType) {
        X509Certificate x509Certificate = CryptoUtils.readCertificate(certificateType.getData());
        return convert(x509Certificate);
    }

    /**
     * convert CertificateInfo into openapi Certificate class
     * @param certificateInfo
     * @return
     */
    public Certificate convert(CertificateInfo certificateInfo) {
        X509Certificate x509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
        Certificate certificate = convert(x509Certificate);

        if (certificateInfo.isActive()) {
            certificate.setStatus(CertificateStatus.IN_USE);
        } else {
            certificate.setStatus(CertificateStatus.DISABLED);
        }
        return certificate;
    }

    /**
     * convert X509Certificate into openapi Certificate class.
     * certificate.state will be null.
     * @param x509Certificate
     * @return
     */
    public Certificate convert(X509Certificate x509Certificate) {
        Certificate certificate = new Certificate();

        String issuerCommonName = null;
        try {
            issuerCommonName = CertUtils.getIssuerCommonName(x509Certificate);
        } catch (CodedException didNotFindCommonName) {
        }
        certificate.setIssuerCommonName(issuerCommonName);

        certificate.setNotAfter(FormatUtils.fromDateToOffsetDateTime(x509Certificate.getNotAfter()));
        try {
            certificate.setHash(CryptoUtils.calculateCertHexHash(x509Certificate.getEncoded()).toUpperCase());
        } catch (Exception ex) {
            throw new IllegalStateException("cannot calculate cert hash", ex);
        }
        return certificate;
    }
}
