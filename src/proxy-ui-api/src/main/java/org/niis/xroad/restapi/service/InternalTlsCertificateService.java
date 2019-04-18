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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.util.CryptoUtils;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.niis.xroad.restapi.repository.InternalTlsCertificateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

/**
 * Operations related to internal tls certificates
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class InternalTlsCertificateService {

    private static final String CERT_PEM_FILENAME = "./cert.pem";
    private static final String CERT_CER_FILENAME = "./cert.cer";

    @Autowired
    @Setter
    private InternalTlsCertificateRepository internalTlsCertificateRepository;

    @PreAuthorize("hasAuthority('VIEW_PROXY_INTERNAL_CERT')")
    public X509Certificate getInternalTlsCertificate() {
        return internalTlsCertificateRepository.getInternalTlsCertificate();
    }

    /**
     * Builds a tar.gz package which contains internal tls certificate as
     * two files:
     * - cert.pem PEM encoded certificate
     * - cert.cer DER encoded certificate
     *
     * @return stream that contains the exported cert.tar.gz
     */
    @PreAuthorize("hasAuthority('EXPORT_PROXY_INTERNAL_CERT')")
    public byte[] exportInternalTlsCertificate() {
        X509Certificate certificate = internalTlsCertificateRepository.getInternalTlsCertificate();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (
                GzipCompressorOutputStream gzipCompressorOutputStream =
                        new GzipCompressorOutputStream(byteArrayOutputStream);
                BufferedOutputStream bufferedOutputStream =
                        new BufferedOutputStream(gzipCompressorOutputStream);
                TarArchiveOutputStream tarOutputStream =
                        new TarArchiveOutputStream(bufferedOutputStream)
        ) {
            ByteArrayOutputStream pemStream = new ByteArrayOutputStream();
            CryptoUtils.writeCertificatePem(certificate.getEncoded(), pemStream);
            byte[] pemBytes = pemStream.toByteArray();
            TarArchiveEntry pemEntry = new TarArchiveEntry(CERT_PEM_FILENAME);
            pemEntry.setSize(pemBytes.length);
            writeArchiveEntry(tarOutputStream, pemBytes, pemEntry);

            TarArchiveEntry derEntry = new TarArchiveEntry(CERT_CER_FILENAME);
            byte[] derBytes = certificate.getEncoded();
            derEntry.setSize(derBytes.length);
            writeArchiveEntry(tarOutputStream, derBytes, derEntry);

        } catch (IOException | CertificateEncodingException e) {
            log.error("writing certificate file failed", e);
            throw new RuntimeException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void writeArchiveEntry(TarArchiveOutputStream tarOutputStream,
                                   byte[] pemBytes, TarArchiveEntry pemEntry) throws IOException {
        tarOutputStream.putArchiveEntry(pemEntry);
        tarOutputStream.write(pemBytes);
        tarOutputStream.closeArchiveEntry();
    }
}
