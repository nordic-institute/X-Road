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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Loads the gRPC TLS identity and the trusted Security Server RPC CA from files, once, on {@link #init()}.
 * Any problem with the files fails {@code init()}; there is no retry and no fallback to another key source.
 */
@Slf4j
public class FileKeyProvider implements VaultKeyProvider {

    private final RpcProperties.FileIdentity fileIdentity;
    private final AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
    private final AdvancedTlsX509TrustManager trustManager;

    public FileKeyProvider(RpcProperties.FileIdentity fileIdentity) throws CertificateException {
        this.fileIdentity = fileIdentity;
        this.trustManager = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build();
    }

    @Override
    public void init() {
        var privateKeyPath = fileIdentity.privateKeyPath();
        var certificatePath = fileIdentity.certificatePath();
        var trustedCaPath = fileIdentity.trustedCaPath();
        try {
            var privateKey = CryptoUtils.getPrivateKey(new ByteArrayInputStream(readNonEmpty(privateKeyPath)));
            var certificateChain = readCertificates(certificatePath);
            var trustedCas = readCertificates(trustedCaPath);
            if (trustedCas.size() != 1) {
                throw new IllegalArgumentException("expected exactly one certificate in %s but found %d"
                        .formatted(trustedCaPath, trustedCas.size()));
            }

            keyManager.updateIdentityCredentials(certificateChain.toArray(X509Certificate[]::new), privateKey);
            trustManager.updateTrustCredentials(trustedCas.toArray(X509Certificate[]::new));
        } catch (Exception e) {
            throw XrdRuntimeException.systemInternalError(
                    "Failed to load the file-based RPC identity (private key %s, certificate %s, trusted CA %s)"
                            .formatted(privateKeyPath, certificatePath, trustedCaPath), e);
        }
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

    private static List<X509Certificate> readCertificates(Path path) throws IOException {
        var certificates = List.copyOf(CryptoUtils.readCertificates(readNonEmpty(path)));
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("no certificate found in " + path);
        }
        return certificates;
    }

    private static byte[] readNonEmpty(Path path) throws IOException {
        var content = Files.readAllBytes(path);
        if (content.length == 0) {
            throw new IllegalArgumentException("file is empty: " + path);
        }
        return content;
    }
}
