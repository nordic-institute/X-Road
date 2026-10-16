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

import com.sun.net.httpserver.HttpHandler;
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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An HTTPS server on the loopback address that serves one DID document, whose behaviour tests replace per path,
 * and that counts the requests it receives per path.
 */
final class DidStubServer implements AutoCloseable {

    static final String DID_PATH = "/v1/DEV/COM/222/did.json";

    private static final KeyPair LEAF_KEY_PAIR = generateKeyPair();

    private final HttpsServer server;
    private final String did;
    private final Map<String, HttpHandler> routes = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    private DidStubServer(HttpsServer server) {
        this.server = server;
        this.did = didOfPort(server.getAddress().getPort());
        route(DID_PATH, respond(200, minimalDocument(did)));
        server.createContext("/", exchange -> {
            var path = exchange.getRequestURI().getPath();
            requestCounts.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
            var handler = routes.get(path);
            if (handler == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
            } else {
                handler.handle(exchange);
            }
        });
        server.start();
    }

    static DidStubServer start(Ca ca) throws Exception {
        return start(ca, "127.0.0.1");
    }

    static DidStubServer start(Ca ca, String certificateAddress) throws Exception {
        return start(ca, new GeneralName(GeneralName.iPAddress, certificateAddress));
    }

    static DidStubServer startForHost(Ca ca, String hostName) throws Exception {
        return start(ca, new GeneralName(GeneralName.dNSName, hostName));
    }

    private static DidStubServer start(Ca ca, GeneralName subjectAlternativeName) throws Exception {
        var leafKeyPair = LEAF_KEY_PAIR;
        var issuer = new X500Name(ca.certificate().getSubjectX500Principal().getName());
        var builder = new JcaX509v3CertificateBuilder(issuer, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(),
                new X500Name("CN=did-host"), leafKeyPair.getPublic())
                .addExtension(Extension.subjectAlternativeName, false, new GeneralNames(subjectAlternativeName));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(ca.keyPair().getPrivate());
        var leaf = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", leafKeyPair.getPrivate(), new char[0], new X509Certificate[] {leaf, ca.certificate()});
        var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, new char[0]);
        var sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        return new DidStubServer(server);
    }

    static Ca newCa(String commonName) throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=" + commonName);
        var builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()), notBefore(), notAfter(),
                subject, keyPair.getPublic())
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new Ca(keyPair, new JcaX509CertificateConverter().getCertificate(builder.build(signer)));
    }

    static String didOfPort(int port) {
        return "did:web:127.0.0.1%3A" + port + ":v1:DEV:COM:222";
    }

    static HttpHandler respond(int status, String body) {
        return exchange -> {
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        };
    }

    static String minimalDocument(String did) {
        return "{\"@context\":[\"https://www.w3.org/ns/did/v1\"],\"id\":\"" + did + "\"}";
    }

    String did() {
        return did;
    }

    int port() {
        return server.getAddress().getPort();
    }

    void route(String path, HttpHandler handler) {
        routes.put(path, handler);
    }

    int requests(String path) {
        var count = requestCounts.get(path);
        return count == null ? 0 : count.get();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static KeyPair generateKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Date notBefore() {
        return Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private static Date notAfter() {
        return Date.from(Instant.now().plus(365, ChronoUnit.DAYS));
    }

    record Ca(KeyPair keyPair, X509Certificate certificate) {
    }
}
