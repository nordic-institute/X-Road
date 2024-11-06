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
package org.niis.xroad.common.rpc;

import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultCertificateRequest;
import org.springframework.vault.support.VaultCertificateResponse;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;

import static io.grpc.util.AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION;
import static io.grpc.util.CertificateUtils.getPrivateKey;
import static io.grpc.util.CertificateUtils.getX509Certificates;

@Slf4j
@RequiredArgsConstructor
public class ReloadableVaultKeyManager implements InitializingBean {
    private final VaultTemplate vaultTemplate;

    private final AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
    private final AdvancedTlsX509TrustManager trustManager;

    public ReloadableVaultKeyManager(VaultTemplate vaultTemplate) throws CertificateException {
        this.vaultTemplate = vaultTemplate;

        this.trustManager = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        reload();
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    @Scheduled(fixedRate = 3600000) //TODO use separate thread pool
    public void reload() throws Exception {
        VaultCertificateResponse serverCert = vaultTemplate.opsForPki()
                .issueCertificate("grpc-internal", VaultCertificateRequest.builder()
                        .commonName("localhost")
                        .ipSubjectAltNames(List.of("127.0.0.1"))
                        .ttl(Duration.ofHours(24))
                        .format("pem")
                        .privateKeyFormat("pkcs8")
                        .build());

        if (serverCert.getData() != null) {
            var cert = getX509Certificates(new ByteArrayInputStream(serverCert.getData().getCertificate().getBytes()));
            var privateKey = getPrivateKey(new ByteArrayInputStream(serverCert.getData().getPrivateKey().getBytes()));
            var certTrustChain = getX509Certificates(new ByteArrayInputStream(serverCert.getData().getIssuingCaCertificate().getBytes()));

            log.info("Received new certificate from Vault.");
            keyManager.updateIdentityCredentials(cert, privateKey);
            trustManager.updateTrustCredentials(certTrustChain);
        } else {
            log.error("Failed to get certificate from Vault. Data is null.");
        }
    }
}
