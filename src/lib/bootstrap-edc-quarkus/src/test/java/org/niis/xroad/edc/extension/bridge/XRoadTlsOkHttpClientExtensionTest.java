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

import ee.ria.xroad.common.TestPortUtils;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedDsTlsCaInfo;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the seam end to end: an {@link OkHttpClient} built by this extension makes a real HTTPS request that
 * succeeds against a peer chaining to a listed DS TLS CA, fails against one that does not, and — since an
 * unreadable globalconf must not abort boot — still gets built (rejecting everything) when globalconf throws.
 */
@ExtendWith(MockitoExtension.class)
class XRoadTlsOkHttpClientExtensionTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private HttpsServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void theBuiltClientAcceptsAPeerChainingToAListedCaAndRejectsOneThatDoesNot() throws Exception {
        var listedCa = selfSignedCa("Listed DS TLS CA");
        var unlistedCa = selfSignedCa("Unlisted CA");
        var acceptedServer = startHttpsServer(issueLeaf(listedCa, "accepted"));
        var rejectedServer = startHttpsServer(issueLeaf(unlistedCa, "rejected"));

        stubContext(List.of(dsTlsCaInfo("listed", listedCa.certificate())));
        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(get(client, acceptedServer)).isEqualTo(200);
        assertThatThrownBy(() -> get(client, rejectedServer)).isInstanceOf(IOException.class);
    }

    @Test
    void anUnreadableGlobalConfAtBootStillProducesAClientThatRejectsEverything() throws Exception {
        var ca = selfSignedCa("Would-be Listed CA");
        var wouldBeAcceptedServer = startHttpsServer(issueLeaf(ca, "would-be-accepted"));

        when(globalConfProvider.getInstanceIdentifier()).thenThrow(new IllegalStateException("globalconf unreachable"));
        stubContextServices();

        var client = new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        assertThat(client).isNotNull();
        assertThatThrownBy(() -> get(client, wouldBeAcceptedServer)).isInstanceOf(IOException.class);
    }

    /**
     * The exact key and default are a contract other layers rely on without compiling against this class:
     * ansible/compose test-environment overrides (needed because CS-side globalconf registration only takes
     * effect after a reload cycle) set the YAML key {@code xroad.edc.web.https.trust.reload-interval-seconds}
     * directly, with nothing to catch a silent rename at compile time.
     */
    @Test
    void theReloadIntervalSettingKeyAndDefaultAreQueriedFromContext() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(dsTlsCaInfo("any", ca.certificate())));

        new XRoadTlsOkHttpClientExtension().okHttpClient(context);

        verify(context).getSetting("xroad.edc.web.https.trust.reload-interval-seconds", 60L);
    }

    @Test
    void shutdownClosesTheReloaderWithoutThrowing() throws Exception {
        var ca = selfSignedCa("Any CA");
        startHttpsServer(issueLeaf(ca, "any"));
        stubContext(List.of(dsTlsCaInfo("any", ca.certificate())));
        var extension = new XRoadTlsOkHttpClientExtension();
        extension.okHttpClient(context);

        assertThatCode(extension::shutdown).doesNotThrowAnyException();
    }

    private void stubContext(List<ApprovedDsTlsCaInfo> cas) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedDsTlsCas(INSTANCE_IDENTIFIER)).thenReturn(cas);
        stubContextServices();
    }

    private void stubContextServices() {
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(context.getService(GlobalConfProvider.class)).thenReturn(globalConfProvider);
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.isNull())).thenReturn(null);
        // No setting configured in these tests: mirrors real behaviour (SettingResolver returns the
        // caller's own default) rather than Mockito's zero-value default, which would otherwise hand
        // PeriodicMaterialReloader.schedule a zero-second interval and blow up scheduleWithFixedDelay.
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    private static int get(OkHttpClient client, HttpsServer server) throws Exception {
        var request = new Request.Builder().url(URI.create("https://127.0.0.1:" + server.getAddress().getPort() + "/").toURL()).build();
        try (var response = client.newCall(request).execute()) {
            return response.code();
        }
    }

    private static ApprovedDsTlsCaInfo dsTlsCaInfo(String name, X509Certificate certificate) {
        return new ApprovedDsTlsCaInfo(name, certificate, List.of(), null, null, null);
    }

    private HttpsServer startHttpsServer(TestLeaf leaf) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", leaf.privateKey(), new char[0], leaf.chain());
        var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        var port = TestPortUtils.findRandomPort();
        var httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsServer.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        httpsServer.start();
        server = httpsServer;
        return httpsServer;
    }

    private static TestCa selfSignedCa(String commonName) throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=" + commonName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(), subject,
                keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestCa(keyPair, certificate);
    }

    private static TestLeaf issueLeaf(TestCa ca, String commonName) throws Exception {
        var leafKeyPair = generateKeyPair();
        var issuer = new X500Name(ca.certificate().getSubjectX500Principal().getName());
        var subject = new X500Name("CN=" + commonName);
        // OkHttp verifies the hostname independently of the trust manager under test here, against whatever
        // address the client actually dials (127.0.0.1) — irrelevant to the trust decision, but still required
        // for the real HTTPS request in this test to reach the trust manager check at all.
        var builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(), subject,
                leafKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.keyPair().getPrivate());
        var leafCertificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestLeaf(leafKeyPair.getPrivate(), new X509Certificate[] {leafCertificate, ca.certificate()});
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Date notBefore() {
        return Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static Date notAfter() {
        return Date.from(Instant.now().plus(365, ChronoUnit.DAYS));
    }

    private record TestCa(KeyPair keyPair, X509Certificate certificate) {
    }

    private record TestLeaf(PrivateKey privateKey, X509Certificate[] chain) {
    }
}
