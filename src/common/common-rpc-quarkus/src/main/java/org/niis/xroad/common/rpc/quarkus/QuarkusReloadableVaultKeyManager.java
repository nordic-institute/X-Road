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
package org.niis.xroad.common.rpc.quarkus;

import io.grpc.util.AdvancedTlsX509KeyManager;
import io.grpc.util.AdvancedTlsX509TrustManager;
import io.grpc.util.CertificateUtils;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.vault.VaultPKISecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import io.quarkus.vault.pki.DataFormat;
import io.quarkus.vault.pki.GenerateCertificateOptions;
import io.quarkus.vault.pki.PrivateKeyEncoding;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.CommonRpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

@Slf4j
public class QuarkusReloadableVaultKeyManager implements VaultKeyProvider {
    private final CommonRpcProperties rpcProperties;
    private final AdvancedTlsX509KeyManager keyManager = new AdvancedTlsX509KeyManager();

    private final AdvancedTlsX509TrustManager trustManager;
    private final VaultPKISecretEngine pkiSecretEngine;
    private final Scheduler scheduler;

    public QuarkusReloadableVaultKeyManager(CommonRpcProperties rpcProperties,
                                            VaultPKISecretEngineFactory pkiSecretEngineFactory,
                                            Scheduler scheduler) throws CertificateException {
        this.rpcProperties = rpcProperties;
        this.scheduler = scheduler;
        this.pkiSecretEngine = pkiSecretEngineFactory.engine(rpcProperties.certificateProvisioning().secretStorePkiPath());

        this.trustManager = AdvancedTlsX509TrustManager.newBuilder()
                .setVerification(AdvancedTlsX509TrustManager.Verification.CERTIFICATE_AND_HOST_NAME_VERIFICATION)
                .build();
    }

    @PostConstruct
    public void init() throws Exception {
        if (scheduler.isStarted()) {
            log.info("Scheduling certificate reload job");
            scheduler.newJob(getClass().getSimpleName())
                    .setInterval("%sm".formatted(rpcProperties.certificateProvisioning().refreshIntervalMinutes()))
                    .setDelayed("0s")
                    .setTask(this::reload)
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .schedule();
        } else {
            log.warn("Scheduler is not started, certificate reload job is not scheduled!");
        }
    }

    @Override
    public KeyManager getKeyManager() {
        return keyManager;
    }

    @Override
    public TrustManager getTrustManager() {
        return trustManager;
    }

    public void reload(ScheduledExecution execution) {
        try {
            var request = buildVaultCertificateRequest();
            if (log.isDebugEnabled()) {
                log.debug("Requesting new certificate from Vault secret-store [{}] with request cn: {}, "
                                + "altNames: {}, ipSubjectAltNames: {}",
                        rpcProperties.certificateProvisioning().secretStorePkiPath(), "CN", "altNames", "ipSubjectAltNames");
            }

            var vaultResponse = pkiSecretEngine.generateCertificate(rpcProperties.certificateProvisioning().issuanceRoleName(), request);

            if (vaultResponse != null) {
                log.info("Received new certificate from Vault. [{}]", vaultResponse);
                var cert = vaultResponse.certificate.getCertificate();
                if (vaultResponse.privateKey.getData() instanceof String data) {
                    var privateKey = CertificateUtils.getPrivateKey(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
                    var certTrustChain = vaultResponse.issuingCA.getCertificate();

                    log.info("Received new certificate from Vault.");
                    keyManager.updateIdentityCredentials(new X509Certificate[]{cert}, privateKey);
                    trustManager.updateTrustCredentials(new X509Certificate[]{certTrustChain});
                }
            } else {
                log.error("Failed to get certificate from Vault. Data is null.");
            }
        } catch (Exception e) {
            log.error("Failed to reload certificate from Vault", e);
        }
    }

    private GenerateCertificateOptions buildVaultCertificateRequest() {
        var request = new GenerateCertificateOptions();
        request.setTimeToLive("%sm".formatted(rpcProperties.certificateProvisioning().ttlMinutes()));
        request.setFormat(DataFormat.PEM);
        request.setPrivateKeyEncoding(PrivateKeyEncoding.PKCS8);

        if (rpcProperties.certificateProvisioning().commonName() != null) {
            request.setSubjectCommonName(rpcProperties.certificateProvisioning().commonName());
        }
        if (rpcProperties.certificateProvisioning().altNames() != null) {
            request.setSubjectAlternativeNames(rpcProperties.certificateProvisioning().altNames());

        }
        if (rpcProperties.certificateProvisioning().ipSubjectAltNames() != null) {
            request.setIpSubjectAlternativeNames(rpcProperties.certificateProvisioning().ipSubjectAltNames());
        }
        return request;
    }
}
