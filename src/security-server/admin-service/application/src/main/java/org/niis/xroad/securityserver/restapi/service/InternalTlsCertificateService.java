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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

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
@PreAuthorize("isAuthenticated()")
public class InternalTlsCertificateService {

    private static final String CERT_PEM_FILENAME = "./cert.pem";
    private static final String CERT_CER_FILENAME = "./cert.cer";

    private final ProxyRpcClient proxyRpcClient;
    private final AuditDataHelper auditDataHelper;

    @Autowired
    public InternalTlsCertificateService(ProxyRpcClient proxyRpcClient,
                                         AuditDataHelper auditDataHelper) {
        this.proxyRpcClient = proxyRpcClient;
        this.auditDataHelper = auditDataHelper;
    }

    public X509Certificate getInternalTlsCertificate() {
        try {
            return proxyRpcClient.getInternalTlsCertificate();
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to obtain TLS certificate", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    /**
     * Builds a tar.gz package which contains internal tls certificate as
     * two files:
     * - cert.pem PEM encoded certificate
     * - cert.cer DER encoded certificate
     *
     * @return byte array that contains the exported certs.tar.gz
     */
    public byte[] exportInternalTlsCertificate() {
        X509Certificate certificate = getInternalTlsCertificate();

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

        } catch (IOException | CertificateEncodingException e) {
            log.error("writing certificate file failed", e);
            throw XrdRuntimeException.systemException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * create a file inside the tar container
     */
    private void writeFileToArchive(TarArchiveOutputStream tarOutputStream, byte[] fileBytes, String fileName)
            throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
        archiveEntry.setSize(fileBytes.length);
        tarOutputStream.putArchiveEntry(archiveEntry);
        tarOutputStream.write(fileBytes);
        tarOutputStream.closeArchiveEntry();
    }

    /**
     * Generates a new TLS key and certificate for internal use for the current Security Server. A runtime
     * exception will be thrown if the generation is interrupted or otherwise unable to be executed.
     *
     * @throws InterruptedException if the thread running the key generator is interrupted. <b>The interrupted thread
     *                              has already been handled with so you can choose to ignore this exception if you so please.</b>
     */
    public void generateInternalTlsKeyAndCertificate() {
        try {
            X509Certificate generatedCert = proxyRpcClient.generateInternalTlsKeyAndCertificate();
            auditDataHelper.putCertificateHash(generatedCert);
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate TLS key & certificate", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    /**
     * Imports a new internal TLS certificate.
     *
     * @param certificateBytes
     * @return X509Certificate
     * @throws InvalidCertificateException
     */
    public X509Certificate importInternalTlsCertificate(byte[] certificateBytes) {
        try {
            var certificate = proxyRpcClient.importInternalTlsCertificate(certificateBytes);
            auditDataHelper.putCertificateHash(certificate);
            return certificate;
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to import TLS certificate", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }

    /**
     * Generate internal auth cert CSR
     *
     * @param distinguishedName
     * @return
     * @throws InvalidDistinguishedNameException if {@code distinguishedName} does not conform to
     *                                           <a href="http://www.ietf.org/rfc/rfc1779.txt">RFC 1779</a> or
     *                                           <a href="http://www.ietf.org/rfc/rfc2253.txt">RFC 2253</a>
     */
    public byte[] generateInternalCsr(String distinguishedName) {
        try {
            auditDataHelper.put(RestApiAuditProperty.SUBJECT_NAME, distinguishedName);
            return proxyRpcClient.generateInternalCsr(distinguishedName);
        } catch (CodedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate TLS CSR", e);
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR, e);
        }
    }
}
