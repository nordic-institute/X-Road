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
package org.niis.xroad.signer.jpa.mapper;

import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.common.identifiers.jpa.mapper.XRoadIdMapper;
import org.niis.xroad.signer.core.model.BasicCertInfo;
import org.niis.xroad.signer.core.model.CertData;
import org.niis.xroad.signer.jpa.entity.SignerCertificateEntity;

import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class CertMapper implements GenericUniDirectionalMapper<SignerCertificateEntity, BasicCertInfo> {
    private final XRoadIdMapper xroadIdMapper;

    @Override
    public BasicCertInfo toTarget(SignerCertificateEntity source) {
        var x509Certificate = CryptoUtils.readCertificate(source.getData());
        var certHash = calculateCertHexHash(x509Certificate);

        return new CertData(
                source.getId(),
                source.getExternalId(),
                source.getKeyId(),
                xroadIdMapper.toTarget(source.getMember()),
                source.getStatus(),
                source.getActive(),
                source.getRenewedCertHash(),
                source.getRenewalError(),
                source.getNextRenewalTime(),
                source.getOcspVerifyError(),
                x509Certificate,
                certHash
        );
    }

    private String calculateCertHexHash(X509Certificate certificate) {
        try {
            return CryptoUtils.calculateCertHexHash(certificate);
        } catch (Exception e) {
            log.error("Failed to calculate certificate hash for {}", certificate, e);
            throw translateException(e);
        }
    }

}
