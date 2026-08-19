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
import ee.ria.xroad.common.util.CertUtils;
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
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.restapi.dstls.DsTlsCertificateStatus;
import org.niis.xroad.restapi.dstls.DsTlsCertificateValidator;
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
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.CSR_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DS_TLS_CERTIFICATE_NOT_CONFIGURED;
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

    private final VaultClient vaultClient;
    private final DsTlsCertificateValidator dsTlsCertificateValidator;

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
    }

    public byte[] generateCsr(String distinguishedName) {
        InternalSSLKey credentials = readCredentials()
                .orElseThrow(() -> new NotFoundException(DS_TLS_KEY_NOT_GENERATED.build()));
        try {
            return CertUtils.generateCertRequest(credentials.getKey(), publicKeyOf(credentials.getKey()), distinguishedName);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e, INVALID_DISTINGUISHED_NAME.build());
        } catch (Exception e) {
            throw new InternalServerErrorException(e, CSR_FAILED.build());
        }
    }

    public X509Certificate uploadCertificate(byte[] certificateChainBytes) {
        InternalSSLKey credentials = readCredentials()
                .orElseThrow(() -> new NotFoundException(DS_TLS_KEY_NOT_GENERATED.build()));

        X509Certificate[] chain = dsTlsCertificateValidator.validate(publicKeyOf(credentials.getKey()), certificateChainBytes);
        try {
            vaultClient.createDsHttpsTlsCredentials(new InternalSSLKey(credentials.getKey(), chain));
            log.info("Successfully stored DataSpace TLS certificate");
        } catch (Exception e) {
            log.error("Failed to store DataSpace TLS certificate", e);
            throw new InternalServerErrorException(e, INTERNAL_ERROR.build());
        }
        return chain[0];
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
