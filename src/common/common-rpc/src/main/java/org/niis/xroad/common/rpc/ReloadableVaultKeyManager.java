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

import ee.ria.xroad.common.properties.CommonRpcProperties;

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
import java.util.concurrent.TimeUnit;

import static io.grpc.util.AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION;
import static io.grpc.util.CertificateUtils.getPrivateKey;
import static io.grpc.util.CertificateUtils.getX509Certificates;
import static org.niis.xroad.common.rpc.RpcConfig.BEAN_VIRTUAL_THREAD_SCHEDULER;

@Slf4j
@RequiredArgsConstructor
public class ReloadableVaultKeyManager implements InitializingBean {
    private static final String CERTIFICATE_FORMAT = "pem";
    private static final String PRIVATE_KEY_FORMAT = "pkcs8";

    private final CommonRpcProperties.CertificateProvisionProperties certificateProvisionProperties;
    private final VaultTemplate vaultTemplate;

    private final AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();
    private final AdvancedTlsX509TrustManager trustManager;

    public ReloadableVaultKeyManager(CommonRpcProperties.CertificateProvisionProperties certificateProvisionProperties,
                                     VaultTemplate vaultTemplate) throws CertificateException {
        this.certificateProvisionProperties = certificateProvisionProperties;
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

    @Scheduled(fixedRateString = "${xroad.common.rpc.certificate-provisioning.refresh-interval-minutes}", timeUnit = TimeUnit.MINUTES,
            scheduler = BEAN_VIRTUAL_THREAD_SCHEDULER)
    public void reload() throws Exception {
        VaultCertificateResponse vaultResponse = vaultTemplate.opsForPki(certificateProvisionProperties.secretStorePkiPath())
                .issueCertificate(certificateProvisionProperties.issuanceRoleName(), buildVaultCertificateRequest());

        if (vaultResponse.getData() != null) {
            var data = vaultResponse.getData();
            var cert = getX509Certificates(new ByteArrayInputStream(data.getCertificate().getBytes()));
            var privateKey = getPrivateKey(new ByteArrayInputStream(data.getPrivateKey().getBytes()));
            var certTrustChain = getX509Certificates(new ByteArrayInputStream(data.getIssuingCaCertificate().getBytes()));

            log.info("Received new certificate from Vault.");
            keyManager.updateIdentityCredentials(cert, privateKey);
            trustManager.updateTrustCredentials(certTrustChain);
        } else {
            log.error("Failed to get certificate from Vault. Data is null.");
        }
    }

    private VaultCertificateRequest buildVaultCertificateRequest() {
        var builder = VaultCertificateRequest.builder()
                .ttl(Duration.ofMinutes(certificateProvisionProperties.ttlMinutes()))
                .format(CERTIFICATE_FORMAT)
                .privateKeyFormat(PRIVATE_KEY_FORMAT);

        if (certificateProvisionProperties.commonName() != null) {
            builder.commonName(certificateProvisionProperties.commonName());
        }
        if (certificateProvisionProperties.altNames() != null) {
            builder.altNames(certificateProvisionProperties.altNames());
        }
        if (certificateProvisionProperties.ipSubjectAltNames() != null) {
            builder.ipSubjectAltNames(certificateProvisionProperties.ipSubjectAltNames());
        }
        return builder.build();
    }
}
