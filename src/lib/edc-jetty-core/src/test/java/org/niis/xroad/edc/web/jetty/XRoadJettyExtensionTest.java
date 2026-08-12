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
import ee.ria.xroad.common.conf.InternalSSLKey;

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
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.web.spi.WebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.vault.VaultClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadJettyExtensionTest {

    @Mock
    private VaultClient vaultClient;

    private ServiceExtensionContext context;
    private XRoadJettyExtension extension;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        port = TestPortUtils.findRandomPort();
        extension = new XRoadJettyExtension();
        setField(extension, "apiConfiguration", new XRoadJettyExtension.DefaultApiConfiguration(port, "/api"));
        setField(extension, "reloadIntervalSeconds", 3600L);
        setField(extension, "vaultClient", vaultClient);

        context = mock(ServiceExtensionContext.class);
        lenient().when(context.getMonitor()).thenReturn(new ConsoleMonitor());
    }

    @Test
    void initializeFailsFastWithAnActionableErrorWhenTheVaultSlotIsEmpty() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenThrow(new IOException("secret not found at tls/ds-https"));

        assertThatThrownBy(() -> extension.initialize(context))
                .isInstanceOf(EdcException.class)
                .hasMessageContaining("tls/ds-https");

        verify(context, never()).registerService(eq(WebServer.class), any());
    }

    @Test
    void initializeRegistersTheOwnedWebServerWhenTheCertificateIsPresent() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(selfSignedInternalSslKey());

        extension.initialize(context);

        verify(context).registerService(eq(WebServer.class), any(XRoadJettyService.class));
        verify(context).registerService(eq(XRoadJettyService.class), any(XRoadJettyService.class));
    }

    @Test
    void shutdownIsSafeToCallWithoutAPriorInitialize() {
        extension.shutdown();
    }

    @Test
    void endToEndFromVaultCredentialsToARealHttpsResponse() throws Exception {
        when(vaultClient.getDsHttpsTlsCredentials()).thenReturn(selfSignedInternalSslKey());

        var captor = ArgumentCaptor.forClass(WebServer.class);

        extension.initialize(context);
        verify(context).registerService(eq(WebServer.class), captor.capture());
        var webServer = captor.getValue();

        // Mirrors the real EDC boot order: consumer extensions (Jersey et al.) call registerServlet
        // from their own start(), which the dependency graph always runs after this extension's
        // start() - registerServlet only works once start() has built the real per-path handlers.
        extension.start();
        try {
            webServer.registerServlet("default", helloServlet("hi-from-ds-https"));
            assertThat(httpsGet(port, "/api")).contains("hi-from-ds-https");
        } finally {
            extension.shutdown();
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

    private static InternalSSLKey selfSignedInternalSslKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();

        var subject = new X500Name("CN=localhost");
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
        var certificate = new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));

        return new InternalSSLKey(keyPair.getPrivate(), new X509Certificate[] {certificate});
    }

    private static String httpsGet(int port, String path) throws Exception {
        try (var socket = (SSLSocket) trustAllSslContext().getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            var request = "GET " + path + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            var response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            var bodyStart = response.indexOf("\r\n\r\n");
            return bodyStart >= 0 ? response.substring(bodyStart + 4) : response;
        }
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

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
