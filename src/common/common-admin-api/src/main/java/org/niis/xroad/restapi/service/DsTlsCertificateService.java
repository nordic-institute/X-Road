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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.crypto.RsaKeyManager;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.vault.DsTlsEnrollmentMethod;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.dstls.DsTlsAcmeAvailability;
import org.niis.xroad.restapi.dstls.DsTlsAcmeOrderResult;
import org.niis.xroad.restapi.dstls.DsTlsCertificateAcmeProvider;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.dstls.DsTlsCertificateValidator;
import org.niis.xroad.restapi.dstls.DsTlsCsrBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.niis.xroad.common.core.exception.ErrorCode.CSR_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_ACME_ORDER_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CA_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_CONFIGURED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_INVALID_SUBJECT_ALT_NAME;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_KEY_NOT_GENERATED;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_DISTINGUISHED_NAME;
import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

/**
 * Manages the DataSpace TLS certificate slot at OpenBao {@code tls/ds-https}, shared between Security Server and
 * Central Server admin services. The private key never crosses the admin API: it is generated and kept
 * server-side, an operator downloads a CSR built from it, and only a cert-only certificate chain is ever
 * uploaded or downloaded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DsTlsCertificateService {

    private static final int RSA_KEY_LENGTH = 2048;
    private static final String CERT_PEM_FILENAME = "./ds-https.pem";
    private static final String CERT_CER_FILENAME = "./ds-https.cer";
    private static final DsTlsAcmeAvailability ACME_NOT_AVAILABLE = new DsTlsAcmeAvailability(false, List.of(), null);
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private final VaultClient vaultClient;
    private final DsTlsCertificateValidator dsTlsCertificateValidator;
    private final ObjectProvider<DsTlsCertificateAcmeProvider> dsTlsCertificateAcmeProvider;

    public DsTlsCertificateStatus getStatus() {
        return readCredentials()
                .map(credentials -> new DsTlsCertificateStatus(true, leafOrNull(credentials)))
                .orElseGet(() -> new DsTlsCertificateStatus(false, null));
    }

    public void generateKey() {
        KeyPair keyPair = new RsaKeyManager(RSA_KEY_LENGTH).generateKeyPair();
        try {
            vaultClient.createDsHttpsTlsCredentials(new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[0]));
            log.info("Successfully generated DataSpace TLS key");
        } catch (Exception e) {
            log.error("Failed to store DataSpace TLS key", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        deleteEnrollmentStatus();
    }

    /**
     * @param subjectAltName single DNS subject alternative name, or {@code null} for a distinguished-name-only CSR
     */
    public byte[] generateCsr(String distinguishedName, String subjectAltName) {
        InternalSSLKey credentials = readCredentials()
                .orElseThrow(() -> new NotFoundException(DS_TLS_KEY_NOT_GENERATED.build()));
        if (subjectAltName != null) {
            validateSubjectAltName(subjectAltName);
        }
        try {
            return DsTlsCsrBuilder.buildPem(credentials.getKey(), publicKeyOf(credentials.getKey()), distinguishedName, subjectAltName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e, INVALID_DISTINGUISHED_NAME.build());
        } catch (Exception e) {
            throw new InternalServerErrorException(e, CSR_FAILED.build());
        }
    }

    /**
     * @return whether ACME ordering is currently available, and if so, from which designated certification
     *     authorities and under which public hostname
     */
    public DsTlsAcmeAvailability getAcmeAvailability() {
        DsTlsCertificateAcmeProvider provider = dsTlsCertificateAcmeProvider.getIfAvailable();
        return provider != null ? provider.getAvailability() : ACME_NOT_AVAILABLE;
    }

    /**
     * Orders a DataSpace TLS certificate via ACME from {@code caName} for {@code distinguishedName} and
     * {@code subjectAltName}, reusing the stored DS TLS key, and stores the issued chain through the existing
     * ACME store path. Ordering while a certificate already exists is allowed and replaces it.
     *
     * @return the issued leaf certificate
     */
    public X509Certificate orderCertificate(String caName, String distinguishedName, String subjectAltName) {
        InternalSSLKey credentials = readCredentials()
                .orElseThrow(() -> new NotFoundException(DS_TLS_KEY_NOT_GENERATED.build()));
        validateSubjectAltName(subjectAltName);

        DsTlsCertificateAcmeProvider provider = dsTlsCertificateAcmeProvider.getIfAvailable();
        if (provider == null) {
            throw new BadRequestException(DS_TLS_CA_NOT_FOUND.build(caName));
        }

        PrivateKey privateKey = credentials.getKey();
        PublicKey publicKey = publicKeyOf(privateKey);
        X509Certificate currentCertificate = leafOrNull(credentials);

        DsTlsAcmeOrderResult result;
        try {
            result = provider.order(caName, distinguishedName, subjectAltName, privateKey, publicKey, currentCertificate);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e, INVALID_DISTINGUISHED_NAME.build());
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            String error = describeError(e);
            recordAcmeOutcome(error);
            throw new InternalServerErrorException(e, DS_TLS_ACME_ORDER_FAILED.build(error));
        }

        X509Certificate[] chainArray = result.certificateChain().toArray(X509Certificate[]::new);
        storeAcmeEnrolledCertificate(privateKey, chainArray, result.nextRenewalTime());
        return chainArray[0];
    }

    private void validateSubjectAltName(String subjectAltName) {
        if (isBlank(subjectAltName) || WHITESPACE.matcher(subjectAltName).find()) {
            throw new BadRequestException(DS_TLS_INVALID_SUBJECT_ALT_NAME.build());
        }
    }

    private static String describeError(Exception ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    public X509Certificate uploadCertificate(byte[] certificateChainBytes) {
        InternalSSLKey credentials = readCredentials()
                .orElseThrow(() -> new NotFoundException(DS_TLS_KEY_NOT_GENERATED.build()));

        X509Certificate[] chain = dsTlsCertificateValidator.validate(publicKeyOf(credentials.getKey()), certificateChainBytes);
        try {
            vaultClient.createDsHttpsTlsCredentials(new InternalSSLKey(credentials.getKey(), chain));
        } catch (Exception e) {
            log.error("Failed to store DataSpace TLS certificate", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        writeEnrollmentStatus(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.MANUAL, null, null));
        log.info("Successfully stored DataSpace TLS certificate");
        return chain[0];
    }

    /**
     * Atomically stores a newly ACME-issued key and certificate chain, tagging the enrollment method
     * {@link DsTlsEnrollmentMethod#ACME}, recording the next renewal time and clearing any prior error. Before
     * writing, validates the chain's public key against {@code privateKey} as a self-check against a bug in the
     * enrollment pipeline, not a trust check on the issuing CA — nothing is written if that check fails.
     *
     * @param privateKey        the newly generated DS TLS private key
     * @param certificateChain  the certificate chain the ACME order returned, leaf certificate first
     * @param nextRenewalTime   when this credential is next due for ACME renewal
     */
    public void storeAcmeEnrolledCertificate(PrivateKey privateKey, X509Certificate[] certificateChain, Instant nextRenewalTime) {
        dsTlsCertificateValidator.validate(publicKeyOf(privateKey), certificateChain);

        try {
            vaultClient.createDsHttpsTlsCredentials(new InternalSSLKey(privateKey, certificateChain));
        } catch (Exception e) {
            log.error("Failed to store ACME-enrolled DataSpace TLS certificate", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        writeEnrollmentStatus(new DsTlsEnrollmentStatus(DsTlsEnrollmentMethod.ACME, nextRenewalTime, null));
        log.info("Successfully stored ACME-enrolled DataSpace TLS certificate");
    }

    /**
     * Records or clears the last enrollment/renewal error, without touching the served credential or the recorded
     * method/next-renewal-time. A no-op when {@code errorDescription} equals what is already recorded, so a caller
     * can call this every cycle without re-triggering anything downstream for an unchanged, still-failing state.
     *
     * @param errorDescription the enrollment/renewal error to record, or {@code null} to clear it
     * @return {@code true} if the recorded error changed, {@code false} if it was already exactly this value
     */
    public boolean recordAcmeOutcome(String errorDescription) {
        Optional<DsTlsEnrollmentStatus> existing = readEnrollmentStatus();
        String currentError = existing.map(DsTlsEnrollmentStatus::lastError).orElse(null);
        if (Objects.equals(currentError, errorDescription)) {
            return false;
        }

        DsTlsEnrollmentMethod method = existing.map(DsTlsEnrollmentStatus::method).orElse(DsTlsEnrollmentMethod.ACME);
        Instant nextRenewalTime = existing.map(DsTlsEnrollmentStatus::nextRenewalTime).orElse(null);
        writeEnrollmentStatus(new DsTlsEnrollmentStatus(method, nextRenewalTime, errorDescription));
        return true;
    }

    /**
     * Reads back the current DS TLS enrollment status.
     * <p>
     * When a certificate is stored, the reported method falls back to {@link DsTlsEnrollmentMethod#MANUAL} if no
     * enrollment status has ever been recorded for it - credentials written before this feature shipped default
     * safely to manual. When no certificate is stored yet, {@link DsTlsEnrollmentStatus#method()} is {@code null}
     * ("none configured"), but a recorded last error is still carried along so a stuck first enrollment attempt
     * stays visible.
     *
     * @return the current enrollment status
     */
    public DsTlsEnrollmentStatus getEnrollmentStatus() {
        boolean certificateAcquired = readCredentials().flatMap(this::leafOptional).isPresent();
        Optional<DsTlsEnrollmentStatus> stored = readEnrollmentStatus();
        String lastError = stored.map(DsTlsEnrollmentStatus::lastError).orElse(null);

        if (!certificateAcquired) {
            return new DsTlsEnrollmentStatus(null, null, lastError);
        }

        DsTlsEnrollmentMethod method = stored.map(DsTlsEnrollmentStatus::method).orElse(DsTlsEnrollmentMethod.MANUAL);
        Instant nextRenewalTime = stored.map(DsTlsEnrollmentStatus::nextRenewalTime).orElse(null);
        return new DsTlsEnrollmentStatus(method, nextRenewalTime, lastError);
    }

    public byte[] downloadCertificateTar() {
        X509Certificate certificate = readCredentials()
                .flatMap(this::leafOptional)
                .orElseThrow(() -> new NotFoundException(DS_TLS_CERTIFICATE_NOT_CONFIGURED.build()));

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (
                GzipCompressorOutputStream gzipCompressorOutputStream = new GzipCompressorOutputStream(byteArrayOutputStream);
                BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(gzipCompressorOutputStream);
                TarArchiveOutputStream tarOutputStream = new TarArchiveOutputStream(bufferedOutputStream)
        ) {
            ByteArrayOutputStream pemStream = new ByteArrayOutputStream();
            CryptoUtils.writeCertificatePem(certificate.getEncoded(), pemStream);
            writeFileToArchive(tarOutputStream, pemStream.toByteArray(), CERT_PEM_FILENAME);
            writeFileToArchive(tarOutputStream, certificate.getEncoded(), CERT_CER_FILENAME);
        } catch (Exception e) {
            log.error("Writing DataSpace TLS certificate to file failed", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        return byteArrayOutputStream.toByteArray();
    }

    private Optional<InternalSSLKey> readCredentials() {
        try {
            InternalSSLKey credentials = vaultClient.getDsHttpsTlsCredentials();
            if (credentials.getKey() == null) {
                return Optional.empty();
            }
            return Optional.of(credentials);
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(MISSING_SECRET)) {
                log.debug("DataSpace TLS key not yet generated", e);
                return Optional.empty();
            }
            log.error("Failed to read DataSpace TLS credentials from vault", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        } catch (Exception e) {
            log.error("Failed to read DataSpace TLS credentials from vault", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
    }

    private Optional<DsTlsEnrollmentStatus> readEnrollmentStatus() {
        try {
            return vaultClient.getDsTlsEnrollmentStatus();
        } catch (Exception e) {
            log.error("Failed to read DataSpace TLS enrollment status from vault", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
    }

    private void writeEnrollmentStatus(DsTlsEnrollmentStatus status) {
        try {
            vaultClient.createDsTlsEnrollmentStatus(status);
        } catch (Exception e) {
            log.error("Failed to store DataSpace TLS enrollment status in vault", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
    }

    /**
     * Deletes the recorded enrollment status outright rather than clearing its fields: {@link DsTlsEnrollmentStatus}
     * has no way to express "no method", so a fresh key with no enrollment history yet can only be represented by
     * the record being absent.
     */
    private void deleteEnrollmentStatus() {
        try {
            vaultClient.deleteDsTlsEnrollmentStatus();
        } catch (Exception e) {
            log.error("Failed to clear DataSpace TLS enrollment status in vault", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
    }

    private X509Certificate leafOrNull(InternalSSLKey credentials) {
        return leafOptional(credentials).orElse(null);
    }

    private Optional<X509Certificate> leafOptional(InternalSSLKey credentials) {
        X509Certificate[] chain = credentials.getCertChain();
        return chain.length == 0 ? Optional.empty() : Optional.of(chain[0]);
    }

    /**
     * Derives the public key from an RSA private key. The DataSpace TLS key is always generated locally as a
     * plain RSA key pair (see {@link #generateKey()}), so the public key can be reconstructed from the stored
     * private key's CRT parameters without ever storing or transmitting it separately.
     */
    private PublicKey publicKeyOf(PrivateKey privateKey) {
        if (privateKey instanceof RSAPrivateCrtKey rsaPrivateKey) {
            try {
                var spec = new RSAPublicKeySpec(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent());
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
            }
        }
        throw new InternalServerErrorException(INTERNAL_ERROR.build());
    }

    private void writeFileToArchive(TarArchiveOutputStream tarOutputStream, byte[] fileBytes, String fileName) throws IOException {
        TarArchiveEntry archiveEntry = new TarArchiveEntry(fileName);
        archiveEntry.setSize(fileBytes.length);
        tarOutputStream.putArchiveEntry(archiveEntry);
        tarOutputStream.write(fileBytes);
        tarOutputStream.closeArchiveEntry();
    }
}
