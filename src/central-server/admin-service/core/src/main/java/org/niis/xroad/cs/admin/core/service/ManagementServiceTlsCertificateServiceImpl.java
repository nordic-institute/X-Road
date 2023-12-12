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

import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import com.google.common.collect.Iterables;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.exception.ServiceException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.CertificateDetails;
import org.niis.xroad.cs.admin.api.service.ManagementServiceTlsCertificateService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Collection;

import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.BYTES_TO_CERTIFICATE_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CERTIFICATE_IMPORT_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CERTIFICATE_READ_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CERTIFICATE_WRITING_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.CSR_GENERATION_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.IMPORTED_CERTIFICATE_ALREADY_EXISTS;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.IMPORTED_KEY_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_CERTIFICATE;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_DISTINGUISHED_NAME;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.KEY_CERT_GENERATION_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.KEY_CERT_GENERATION_INTERRUPTED;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SUBJECT_NAME;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
class ManagementServiceTlsCertificateServiceImpl implements ManagementServiceTlsCertificateService {

    private static final String MANAGEMENT_SERVICE = "management-service";
    private static final String CERT_PEM_FILENAME = "./" + MANAGEMENT_SERVICE + ".pem";
    private static final String CERT_CER_FILENAME = "./" + MANAGEMENT_SERVICE + ".cer";
    private static final String MANAGEMENT_SERVICE_TLS_KEY_PATH = "%s" + MANAGEMENT_SERVICE + ".key";
    private static final String MANAGEMENT_SERVICE_TLS_CERT_PATH = "%s" + MANAGEMENT_SERVICE + ".crt";
    private static final String MANAGEMENT_SERVICE_TLS_P12_PATH = "%s" + MANAGEMENT_SERVICE + ".p12";
    private static final String GENERATE_CERT_SCRIPT_ARGS = "-n " + MANAGEMENT_SERVICE + " -f -S -p 2>&1";

    private final CertificateConverter certificateConverter;
    private final ExternalProcessRunner externalProcessRunner;
    private final AuditDataHelper auditDataHelper;

    @Value("${script.generate-certificate.path}")
    private final String generateCertificateScriptPath;
    @Value("${certificates.path}")
    private final String certificatesPath;

    public X509Certificate getTlsCertificate() {
        try {
            return CryptoUtils.readCertificate(Files.readAllBytes(Path.of(getManagementServiceTlsCertPath())));
        } catch (Exception e) {
            log.error("Cannot read management service TLS certificate", e);
            throw new ValidationFailureException(INVALID_CERTIFICATE, e);
        }
    }

    public CertificateDetails getTlsCertificateDetails() {
        return certificateConverter.toCertificateDetails(getTlsCertificate());
    }

    public byte[] getTlsCertificateTar() {
        X509Certificate certificate = getTlsCertificate();

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
            log.error("Writing certificate to file failed", e);
            throw new ValidationFailureException(CERTIFICATE_WRITING_FAILED, e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public byte[] generateCsr(String distinguishedName) {
        auditDataHelper.put(SUBJECT_NAME, distinguishedName);
        byte[] csrBytes = null;
        try {
            KeyPair keyPair = CertUtils.readKeyPairFromPemFile(getManagementServiceTlsKeyPath());
            csrBytes = CertUtils.generateCertRequest(keyPair.getPrivate(), keyPair.getPublic(), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new ValidationFailureException(INVALID_DISTINGUISHED_NAME, e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | OperatorCreationException e) {
            throw new ValidationFailureException(CSR_GENERATION_FAILED, e);
        }
        return csrBytes;
    }

    public CertificateDetails importTlsCertificate(byte[] certificateBytes) {
        Collection<X509Certificate> x509Certificates = null;
        try {
            // the imported file can be a single certificate or a chain
            x509Certificates = CryptoUtils.readCertificates(certificateBytes);
        } catch (Exception e) {
            throw new ValidationFailureException(BYTES_TO_CERTIFICATE_FAILED, e);
        }
        auditDataHelper.putCertificateHash(Iterables.get(x509Certificates, 0));
        verifyCertificateImportability(x509Certificates);

        try {
            // create pkcs12 checks the certificate chain validity
            CertUtils.createPkcs12(getManagementServiceTlsKeyPath(), certificateBytes, getManagementServiceTlsP12Path());
            CertUtils.writePemToFile(certificateBytes, getManagementServiceTlsCertPath());
        } catch (Exception e) {
            log.error("Failed to import management service TLS certificate", e);
            throw new ServiceException(CERTIFICATE_IMPORT_FAILED, e);
        }

        return certificateConverter.toCertificateDetails(Iterables.get(x509Certificates, 0));
    }

    public void generateTlsKeyAndCertificate() {
        try {
            externalProcessRunner.executeAndThrowOnFailure(generateCertificateScriptPath, GENERATE_CERT_SCRIPT_ARGS.split("\\s+"));
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            log.error("Failed to generate management service TLS key and certificate", e);
            throw new ServiceException(KEY_CERT_GENERATION_FAILED, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(KEY_CERT_GENERATION_INTERRUPTED, e);
        }

        // audit log hash of generated cert
        X509Certificate generatedCert = getTlsCertificate();
        auditDataHelper.putCertificateHash(generatedCert);
    }

    private void writeFileToArchive(TarArchiveOutputStream tarOutputStream, byte[] fileBytes, String fileName)
            throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
        archiveEntry.setSize(fileBytes.length);
        tarOutputStream.putArchiveEntry(archiveEntry);
        tarOutputStream.write(fileBytes);
        tarOutputStream.closeArchiveEntry();
    }

    private void verifyCertificateImportability(Collection<X509Certificate> newCertChain) {
        Collection<X509Certificate> certificateChain = getTlsCertificateChain();
        PublicKey internalPublicKey = Iterables.get(certificateChain, 0).getPublicKey();

        boolean found = newCertChain.stream().anyMatch(c -> c.getPublicKey().equals(internalPublicKey));
        if (!found) {
            throw new ValidationFailureException(IMPORTED_KEY_NOT_FOUND);
        } else if (Iterables.elementsEqual(certificateChain, newCertChain)) {
            throw new ValidationFailureException(IMPORTED_CERTIFICATE_ALREADY_EXISTS);
        }
    }

    private Collection<X509Certificate> getTlsCertificateChain() {
        try (FileInputStream fileInputStream = new FileInputStream(getManagementServiceTlsCertPath())) {
            return CryptoUtils.readCertificates(fileInputStream);
        } catch (Exception e) {
            log.error("Can't read management service tls certificate");
            throw new ValidationFailureException(CERTIFICATE_READ_FAILED, e);
        }
    }

    private String getManagementServiceTlsKeyPath() {
        return String.format(MANAGEMENT_SERVICE_TLS_KEY_PATH, certificatesPath);
    }

    private String getManagementServiceTlsCertPath() {
        return String.format(MANAGEMENT_SERVICE_TLS_CERT_PATH, certificatesPath);
    }

    private String getManagementServiceTlsP12Path() {
        return String.format(MANAGEMENT_SERVICE_TLS_P12_PATH, certificatesPath);
    }
}
