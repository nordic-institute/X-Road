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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.service.DsTlsCertificateService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.restapi.dstls.DsTlsCertificateValidator;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_CONFIGURED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
class DsTlsCertificateServiceImpl implements DsTlsCertificateService {

    private static final String CERT_PEM_FILENAME = "./ds-https.pem";
    private static final String CERT_CER_FILENAME = "./ds-https.cer";

    private final VaultClient vaultClient;
    private final DsTlsCertificateValidator dsTlsCertificateValidator;
    private final CertificateConverter certificateConverter;

    @Override
    public CertificateDetails getTlsCertificateDetails() {
        return getTlsCertificate()
                .map(certificateConverter::toCertificateDetails)
                .orElse(null);
    }

    @Override
    public byte[] getTlsCertificateTar() {
        X509Certificate certificate = getTlsCertificate()
                .orElseThrow(() -> new NotFoundException(DS_TLS_CERTIFICATE_NOT_CONFIGURED.build()));

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
            writeFileToArchive(tarOutputStream, pemStream.toByteArray(), CERT_PEM_FILENAME);
            writeFileToArchive(tarOutputStream, certificate.getEncoded(), CERT_CER_FILENAME);
        } catch (Exception e) {
            log.error("Writing DS TLS certificate to file failed", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public CertificateDetails uploadCertificate(byte[] keyBytes, byte[] certificateChainBytes) {
        var material = dsTlsCertificateValidator.validate(keyBytes, certificateChainBytes);
        try {
            vaultClient.createDsHttpsTlsCredentials(new InternalSSLKey(material.key(), material.certificateChain()));
        } catch (Exception e) {
            log.error("Failed to store DS TLS certificate", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        return certificateConverter.toCertificateDetails(material.leaf());
    }

    private Optional<X509Certificate> getTlsCertificate() {
        try {
            return Optional.of(vaultClient.getDsHttpsTlsCredentials().getCertChain()[0]);
        } catch (Exception e) {
            log.debug("DS TLS certificate not yet configured", e);
            return Optional.empty();
        }
    }

    private void writeFileToArchive(TarArchiveOutputStream tarOutputStream, byte[] fileBytes, String fileName) throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
        archiveEntry.setSize(fileBytes.length);
        tarOutputStream.putArchiveEntry(archiveEntry);
        tarOutputStream.write(fileBytes);
        tarOutputStream.closeArchiveEntry();
    }
}
