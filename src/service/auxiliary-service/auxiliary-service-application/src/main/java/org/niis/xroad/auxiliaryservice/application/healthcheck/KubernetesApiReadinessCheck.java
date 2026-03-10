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
package org.niis.xroad.auxiliaryservice.application.healthcheck;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.auxiliaryservice.application.config.AuxiliaryServiceReadinessCheckProperties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.ERROR;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.REASON;
import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATUS;

/**
 * Readiness check for Kubernetes API connectivity.
 */
@Slf4j
@Readiness
@ApplicationScoped
public class KubernetesApiReadinessCheck implements HealthCheck {

    private static final String NAME = "KUBERNETES_API_READINESS_CHECK";

    private final AuxiliaryServiceReadinessCheckProperties properties;
    private final SSLContext kubernetesApiSslContext;

    public KubernetesApiReadinessCheck(AuxiliaryServiceReadinessCheckProperties properties) throws GeneralSecurityException, IOException {
        this.properties = properties;

        kubernetesApiSslContext = createSslContext(properties.kubernetes());
    }

    @Override
    public HealthCheckResponse call() {
        var k8sProps = properties.kubernetes();

        if (!isRunningInKubernetes(k8sProps)) {
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .up()
                    .withData(STATUS, "NOT_REQUIRED")
                    .withData(REASON, "Not running in Kubernetes environment")
                    .build();
        }

        try {
            boolean apiAccessible = checkKubernetesApiAccess(k8sProps);
            if (apiAccessible) {
                return HealthCheckResponse.builder()
                        .name(NAME)
                        .up()
                        .withData(STATUS, "CONNECTED")
                        .build();
            } else {
                return HealthCheckResponse.builder()
                        .name(NAME)
                        .down()
                        .withData(ERROR, "Kubernetes API not accessible")
                        .build();
            }
        } catch (Exception e) {
            log.warn("Kubernetes API readiness check failed: {}", e.getMessage());
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .down()
                    .withData(ERROR, e.getMessage())
                    .build();
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    private boolean checkKubernetesApiAccess(AuxiliaryServiceReadinessCheckProperties.KubernetesApiProperties k8sProps)
            throws IOException, GeneralSecurityException {
        String k8sHost = k8sProps.serviceHost().orElseThrow();
        String k8sPort = k8sProps.servicePort().orElseThrow();

        String apiUrl = "https://" + k8sHost + ":" + k8sPort + "/readyz";
        String token = readToken(k8sProps);

        HttpsURLConnection connection = null;
        try {
            URI uri = URI.create(apiUrl);
            connection = (HttpsURLConnection) uri.toURL().openConnection();
            connection.setSSLSocketFactory(kubernetesApiSslContext.getSocketFactory());
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setConnectTimeout(k8sProps.connectTimeoutMs());
            connection.setReadTimeout(k8sProps.readTimeoutMs());

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readToken(AuxiliaryServiceReadinessCheckProperties.KubernetesApiProperties k8sProps) throws IOException {
        return Files.readString(Path.of(k8sProps.tokenPath())).trim();
    }

    private boolean isRunningInKubernetes(AuxiliaryServiceReadinessCheckProperties.KubernetesApiProperties k8sProps) {
        return k8sProps.serviceHost().filter(StringUtils::isNotEmpty).isPresent();
    }

    private SSLContext createSslContext(AuxiliaryServiceReadinessCheckProperties.KubernetesApiProperties k8sProps)
            throws GeneralSecurityException, IOException {
        Path caCertPath = Path.of(k8sProps.caCertPath());
        if (!Files.exists(caCertPath)) {
            // Fall back to default SSL context if CA cert not available
            return SSLContext.getDefault();
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert;
        try (var is = Files.newInputStream(caCertPath)) {
            caCert = (X509Certificate) cf.generateCertificate(is);
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("kubernetes-ca", caCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }
}
