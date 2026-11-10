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
package org.niis.xroad.common.rpc.file;

import ee.ria.xroad.common.util.CryptoUtils;

import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Loads the gRPC TLS identity and the trusted Security Server RPC CA from files, once, on {@link #init()}.
 * Any problem with the files fails {@code init()} with an error naming the file and the reason; there is no retry and
 * no fallback to another key source.
 */
@Slf4j
public class FileKeyProvider implements VaultKeyProvider {

    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final byte[] PROBE = {1, 2, 3, 4};

    private final RpcProperties.FileIdentity fileIdentity;
    private final AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
    private final AdvancedTlsX509TrustManager trustManager;

    private volatile X509Certificate trustedCa;

    public FileKeyProvider(RpcProperties.FileIdentity fileIdentity) throws CertificateException {
        this.fileIdentity = fileIdentity;
        this.trustManager = newTrustManager(null);
    }

    @Override
    public void init() {
        var privateKeyPath = requireSet(fileIdentity.privateKeyPath(), "private key path");
        var certificatePath = requireSet(fileIdentity.certificatePath(), "certificate path");
        var trustedCaPath = requireSet(fileIdentity.trustedCaPath(), "trusted CA path");

        var privateKey = readPrivateKey(privateKeyPath);
        var certificateChain = readCertificates(certificatePath);
        requireServerCertificateUsable(certificateChain.getFirst(), certificatePath);
        requireKeyMatchesCertificate(privateKey, privateKeyPath, certificateChain.getFirst(), certificatePath);
        var ca = readTrustedCa(trustedCaPath);

        try {
            keyManager.updateIdentityCredentials(certificateChain.toArray(X509Certificate[]::new), privateKey);
            trustManager.updateTrustCredentials(new X509Certificate[] {ca});
        } catch (GeneralSecurityException | IOException e) {
            throw invalid(trustedCaPath, "cannot apply the loaded identity", e);
        }
        trustedCa = ca;
        log.info("Loaded the file-based RPC identity from {}", certificatePath);
    }

    @Override
    public void shutdown() {
        // nothing to release
    }

    @Override
    public KeyManager getKeyManager() {
        return keyManager;
    }

    @Override
    public TrustManager getTrustManager() {
        return trustManager;
    }

    @Override
    public AdvancedTlsX509TrustManager createVerifyingTrustManager(
            AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier verifier) {
        var ca = trustedCa;
        if (ca == null) {
            throw XrdRuntimeException.systemInternalError(
                    "The file-based RPC identity is not loaded, cannot create the verifying trust manager");
        }
        try {
            var verifyingTrustManager = newTrustManager(verifier);
            verifyingTrustManager.updateTrustCredentials(new X509Certificate[] {ca});
            return verifyingTrustManager;
        } catch (GeneralSecurityException | IOException e) {
            throw XrdRuntimeException.systemInternalError("Failed to create the verifying trust manager", e);
        }
    }

    private static AdvancedTlsX509TrustManager newTrustManager(
            AdvancedTlsX509TrustManager.SslSocketAndEnginePeerVerifier verifier) throws CertificateException {
        var builder = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION);
        if (verifier != null) {
            builder.setSslSocketAndEnginePeerVerifier(verifier);
        }
        return builder.build();
    }

    private static Path requireSet(Path path, String description) {
        if (path == null || path.toString().isEmpty()) {
            throw XrdRuntimeException.systemInternalError(
                    "Invalid file-based RPC identity: the %s is not set".formatted(description));
        }
        return path;
    }

    private static PrivateKey readPrivateKey(Path path) {
        var content = readNonEmpty(path);
        var isPkcs8 = new String(content, StandardCharsets.UTF_8).lines().anyMatch(PKCS8_HEADER::equals);
        if (!isPkcs8) {
            throw invalid(path, "not an unencrypted PKCS#8 private key (expected a '%s' block)".formatted(PKCS8_HEADER));
        }
        try {
            return CryptoUtils.getPrivateKey(new ByteArrayInputStream(content));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw invalid(path, "the private key cannot be parsed", e);
        }
    }

    private static List<X509Certificate> readCertificates(Path path) {
        var content = readNonEmpty(path);
        List<X509Certificate> certificates;
        try {
            certificates = List.copyOf(CryptoUtils.readCertificates(content));
        } catch (RuntimeException e) {
            throw invalid(path, "the certificates cannot be parsed: " + e.getMessage());
        }
        if (certificates.isEmpty()) {
            throw invalid(path, "no certificate found");
        }
        return certificates;
    }

    private static X509Certificate readTrustedCa(Path path) {
        var certificates = readCertificates(path);
        if (certificates.size() != 1) {
            throw invalid(path, "expected exactly one CA certificate but found %d".formatted(certificates.size()));
        }
        var ca = certificates.getFirst();
        if (ca.getBasicConstraints() < 0) {
            throw invalid(path, "the certificate is not a CA certificate (basicConstraints CA:TRUE is required)");
        }
        requireCurrentlyValid(ca, path, "trusted CA certificate");
        return ca;
    }

    private static void requireServerCertificateUsable(X509Certificate certificate, Path path) {
        requireCurrentlyValid(certificate, path, "server certificate");
        if (certificate.getBasicConstraints() >= 0) {
            throw invalid(path, "the server certificate is a CA certificate (basicConstraints CA:TRUE is not allowed)");
        }
    }

    private static void requireCurrentlyValid(X509Certificate certificate, Path path, String description) {
        try {
            certificate.checkValidity();
        } catch (CertificateExpiredException e) {
            throw invalid(path, "the %s expired on %s".formatted(description, certificate.getNotAfter().toInstant()));
        } catch (CertificateNotYetValidException e) {
            throw invalid(path, "the %s is not valid before %s".formatted(description,
                    certificate.getNotBefore().toInstant()));
        }
    }

    private static void requireKeyMatchesCertificate(PrivateKey privateKey, Path privateKeyPath,
                                                     X509Certificate leaf, Path certificatePath) {
        var algorithm = switch (privateKey.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            default -> throw invalid(privateKeyPath, "unsupported private key algorithm " + privateKey.getAlgorithm());
        };
        boolean matches;
        try {
            var signer = Signature.getInstance(algorithm);
            signer.initSign(privateKey);
            signer.update(PROBE);
            var signature = signer.sign();

            var verifier = Signature.getInstance(algorithm);
            verifier.initVerify(leaf.getPublicKey());
            verifier.update(PROBE);
            matches = verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            matches = false;
        }
        if (!matches) {
            throw invalid(privateKeyPath, "the private key does not match the public key of the first certificate in "
                    + certificatePath);
        }
    }

    private static byte[] readNonEmpty(Path path) {
        byte[] content;
        try {
            content = Files.readAllBytes(path);
        } catch (IOException e) {
            throw invalid(path, "the file is missing or cannot be read", e);
        }
        if (content.length == 0) {
            throw invalid(path, "the file is empty");
        }
        return content;
    }

    private static XrdRuntimeException invalid(Path path, String reason) {
        return XrdRuntimeException.systemInternalError(
                "Invalid file-based RPC identity: %s: %s".formatted(path, reason));
    }

    private static XrdRuntimeException invalid(Path path, String reason, Throwable cause) {
        return XrdRuntimeException.systemInternalError(
                "Invalid file-based RPC identity: %s: %s".formatted(path, reason), cause);
    }
}
