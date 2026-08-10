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
package org.niis.xroad.edc.web.jetty;

import ee.ria.xroad.common.TestPortUtils;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.web.spi.configuration.PortMapping;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the servlet-registration contract against real TLS handshakes on live connectors, mirroring
 * stock EDC's actual {@code JettyService} bytecode (decompiled and inspected for this test): {@code
 * registerServlet}'s first argument is a context ALIAS matched by equality against {@link
 * PortMapping#name()}, and the servlet is mounted at that mapping's {@link PortMapping#path()} - a
 * different string from the alias in any realistic setup.
 */
class XRoadJettyServiceTest {

    private static final Monitor MONITOR = new Monitor() {
    };

    @Test
    void registerServletMountsAtThePortMappingPathNotAtTheAlias() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new PortMappingRegistryImpl();
        // Realistic alias/path pair: the name "sts" is not, and does not contain, the mount path.
        registry.register(new PortMapping("sts", port, "/api/sts"));
        var service = new XRoadJettyService(MONITOR, registry);
        service.applyKeyStore(selfSignedCertificate("sts-connector").keyStore());
        service.start();
        try {
            service.registerServlet("sts", helloServlet("sts-hello"));

            assertThat(httpsGet(port, "/api/sts")).contains("sts-hello");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void registerServletRejectsAPathStyleArgumentThatIsNotARegisteredAlias() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new PortMappingRegistryImpl();
        registry.register(new PortMapping("sts", port, "/api/sts"));
        var service = new XRoadJettyService(MONITOR, registry);
        service.applyKeyStore(selfSignedCertificate("sts-connector").keyStore());
        service.start();
        try {
            // "/api/sts" is the mapping's PATH, not its NAME - passing it as the alias argument must
            // NOT match the "sts" mapping. This is the regression the stock-EDC decompile called for:
            // a path-style argument is a different kind of string from a context alias.
            assertThatThrownBy(() -> service.registerServlet("/api/sts", helloServlet("wrong")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("/api/sts");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void bootFailsWithoutAKeystoreRatherThanFallingBackToPlaintext() {
        var port = TestPortUtils.findRandomPort();
        var registry = new PortMappingRegistryImpl();
        registry.register(new PortMapping("default", port, "/api"));
        var service = new XRoadJettyService(MONITOR, registry);

        // No applyKeyStore() call at all: starting without ever supplying a keystore must fail
        // loudly rather than silently falling back to a plaintext connector.
        assertThatThrownBy(service::start).isInstanceOf(EdcException.class);
    }

    @Test
    void rotatingTheKeystoreServesTheNewCertificateOnALiveConnectorWithoutRestart() throws Exception {
        var port = TestPortUtils.findRandomPort();
        var registry = new PortMappingRegistryImpl();
        registry.register(new PortMapping("default", port, "/api"));
        var service = new XRoadJettyService(MONITOR, registry);

        var certA = selfSignedCertificate("first");
        var certB = selfSignedCertificate("second");

        service.applyKeyStore(certA.keyStore());
        service.start();
        try {
            service.registerServlet("default", helloServlet("hi"));

            assertThat(servedCertificate(port)).isEqualTo(certA.certificate());
            assertThat(httpsGet(port, "/api")).contains("hi");

            service.applyKeyStore(certB.keyStore());

            assertThat(servedCertificate(port)).isEqualTo(certB.certificate());
            // Same connector, same server, no restart - routing still works after the reload.
            assertThat(httpsGet(port, "/api")).contains("hi");
        } finally {
            service.shutdown();
        }
    }

    private static HttpServlet helloServlet(String body) {
        return new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.getWriter().write(body);
            }
        };
    }

    private record SelfSignedCertificate(X509Certificate certificate, KeyStore keyStore) {
    }

    /**
     * Builds a self-signed RSA certificate for {@code localhost} (SAN, matching the test client's SNI
     * hostname) - {@code label} only distinguishes otherwise-identical certificates from each other in
     * assertions (e.g. proving a reload actually swapped the served certificate).
     */
    private static SelfSignedCertificate selfSignedCertificate(String label) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();

        var subject = new X500Name("CN=localhost,OU=" + label);
        var now = Instant.now();
        var certificateBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        certificateBuilder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var holder = certificateBuilder.build(signer);
        var certificate = new JcaX509CertificateConverter().getCertificate(holder);

        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("ds-https", keyPair.getPrivate(), null, new X509Certificate[] {certificate});

        return new SelfSignedCertificate(certificate, keyStore);
    }

    private static String httpsGet(int port, String path) throws Exception {
        try (var socket = (SSLSocket) trustAllSslContext().getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            writeGetRequest(socket, path);
            var response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var bodyStart = response.indexOf("\r\n\r\n");
            return bodyStart >= 0 ? response.substring(bodyStart + 4) : response;
        }
    }

    private static X509Certificate servedCertificate(int port) throws Exception {
        try (var socket = (SSLSocket) trustAllSslContext().getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            return (X509Certificate) socket.getSession().getPeerCertificates()[0];
        }
    }

    private static void writeGetRequest(SSLSocket socket, String path) throws IOException {
        var request = "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
        socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static SSLContext trustAllSslContext() throws Exception {
        var trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, new TrustManager[] {trustAll}, null);
        return sslContext;
    }
}
