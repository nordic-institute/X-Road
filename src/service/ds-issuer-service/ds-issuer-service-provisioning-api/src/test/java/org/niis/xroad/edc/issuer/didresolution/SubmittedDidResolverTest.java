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
package org.niis.xroad.edc.issuer.didresolution;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.eclipse.edc.json.JacksonTypeManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.bridge.XRoadTlsOkHttpClientExtension;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.model.ApprovedConnectorTlsCAInfo;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmittedDidResolverTest {

    private static final String INSTANCE_IDENTIFIER = "TEST";
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(3);

    @Mock
    private ServiceExtensionContext context;

    @Mock
    private Monitor monitor;

    @Mock
    private GlobalConfProvider globalConfProvider;

    private final XRoadTlsOkHttpClientExtension tlsExtension = new XRoadTlsOkHttpClientExtension();

    private HttpsServer server;

    @AfterEach
    void tearDown() {
        tlsExtension.shutdown();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void aDidWhoseDocumentNamesItselfIsResolved() throws Exception {
        var ca = selfSignedCa();
        var did = startDidServer(ca);
        var resolver = resolverTrusting(ca);

        var resolution = resolver.resolve(did);

        assertThat(resolution.isResolved()).isTrue();
        assertThat(resolution.did()).isEqualTo(did);
        assertThat(resolution.reason()).isNull();
    }

    private SubmittedDidResolver resolverTrusting(TestCa ca) {
        when(globalConfProvider.getInstanceIdentifier()).thenReturn(INSTANCE_IDENTIFIER);
        when(globalConfProvider.getApprovedConnectorTlsCAs(INSTANCE_IDENTIFIER))
                .thenReturn(List.of(new ApprovedConnectorTlsCAInfo("test", ca.certificate(), List.of(), null, null, null)));
        lenient().when(context.getMonitor()).thenReturn(monitor);
        lenient().when(context.getService(GlobalConfProvider.class)).thenReturn(globalConfProvider);
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.isNull())).thenReturn(null);
        lenient().when(context.getSetting(ArgumentMatchers.anyString(), ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(1));

        var runtimeClient = tlsExtension.okHttpClient(context);
        return SubmittedDidResolver.create(runtimeClient, new JacksonTypeManager().getMapper(), FETCH_TIMEOUT, monitor);
    }

    private String startDidServer(TestCa ca) throws Exception {
        var leafKeyPair = generateKeyPair();
        var issuer = new X500Name(ca.certificate().getSubjectX500Principal().getName());
        var subject = new X500Name("CN=did-host");
        var builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(),
                subject, leafKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false,
                        new GeneralNames(new GeneralName(GeneralName.iPAddress, "127.0.0.1")));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.keyPair().getPrivate());
        var leaf = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", leafKeyPair.getPrivate(), new char[0], new X509Certificate[] {leaf, ca.certificate()});
        var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        var did = "did:web:127.0.0.1%3A" + server.getAddress().getPort() + ":v1:DEV:COM:222";
        var document = ("{\"@context\":[\"https://www.w3.org/ns/did/v1\"],\"id\":\"" + did + "\"}").getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/DEV/COM/222/did.json", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, document.length);
            exchange.getResponseBody().write(document);
            exchange.close();
        });
        server.start();
        return did;
    }

    private static TestCa selfSignedCa() throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=Test DS TLS CA");
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(),
                subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new TestCa(keyPair, new JcaX509CertificateConverter().getCertificate(builder.build(signer)));
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
}
