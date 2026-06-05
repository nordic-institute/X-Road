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
package org.niis.xroad.edc.extension.bridge;

import dev.failsafe.RetryPolicy;
import okhttp3.OkHttpClient;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;

/**
 * Replaces the default EDC {@link OkHttpClient} with a TLS-aware version that trusts the
 * certificate(s) specified by {@code QUARKUS_VAULT_TLS_CA_CERT} (the same env var used by the
 * Quarkus vault client) in addition to the JVM default trust anchors. Required when EDC services
 * communicate with OpenBao over HTTPS using a self-signed certificate that is not in the JVM
 * default trust store — while still being able to reach other TLS peers (DID documents, issuer
 * services) whose certificates chain to regular trust anchors.
 *
 * <p>The {@code @Inject RetryPolicy} field forces EDC's dependency resolver to run this extension
 * after {@code RuntimeDefaultCoreServicesExtension}, whose {@code @Provider OkHttpClient} would
 * otherwise be the last registered instance.
 */
@Extension(XRoadTlsOkHttpClientExtension.NAME)
public class XRoadTlsOkHttpClientExtension implements ServiceExtension {

    static final String NAME = "X-Road TLS OkHttpClient";

    @Inject
    @SuppressWarnings("unused")
    private RetryPolicy<?> retryPolicy;

    @Override
    public String name() {
        return NAME;
    }

    @Provider
    public OkHttpClient okHttpClient(ServiceExtensionContext context) {
        var certPath = System.getenv("QUARKUS_VAULT_TLS_CA_CERT");
        if (certPath == null || certPath.isBlank()) {
            return new OkHttpClient.Builder().build();
        }
        try {
            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(combinedTrustStore(certPath));

            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(),
                            (X509TrustManager) tmf.getTrustManagers()[0])
                    .build();
        } catch (Exception e) {
            context.getMonitor().warning(
                    "Failed to load OpenBao TLS cert from %s — using default TLS trust: %s"
                            .formatted(certPath, e.getMessage()));
            return new OkHttpClient.Builder().build();
        }
    }

    private static KeyStore combinedTrustStore(String certPath) throws GeneralSecurityException, IOException {
        var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        var index = 0;
        var cf = CertificateFactory.getInstance("X.509");
        try (var in = new FileInputStream(certPath)) {
            for (var cert : cf.generateCertificates(in)) {
                keyStore.setCertificateEntry("vault-ca-" + index++, cert);
            }
        }

        var defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultTmf.init((KeyStore) null);
        for (var trustManager : defaultTmf.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509) {
                for (var issuer : x509.getAcceptedIssuers()) {
                    keyStore.setCertificateEntry("default-" + index++, issuer);
                }
            }
        }
        return keyStore;
    }
}
