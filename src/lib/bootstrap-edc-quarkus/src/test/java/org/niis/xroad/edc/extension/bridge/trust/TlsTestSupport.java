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
package org.niis.xroad.edc.extension.bridge.trust;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Real-certificate and real-handshake support for the dataspace TLS trust tests: a small CA
 * hierarchy (root CA plus a {@code localhost} leaf it signs) and a minimal HTTPS server that serves
 * a given keystore, so trust decisions are proven against actual JSSE handshakes rather than mocks.
 */
public final class TlsTestSupport {

    private TlsTestSupport() {
    }

    public record TestCa(X509Certificate certificate, KeyPair keyPair) {
    }

    public record LeafCertificate(X509Certificate certificate, KeyPair keyPair) {
    }

    public static TestCa generateCa(String commonName) throws Exception {
        var keyPair = generateKeyPair();
        var subject = new X500Name("CN=" + commonName);
        var now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject,
                keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        var certificate = new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        return new TestCa(certificate, keyPair);
    }

    /** Issues a leaf certificate for {@code localhost} (SAN, matching the test client's SNI hostname). */
    public static LeafCertificate issueLocalhostLeaf(TestCa issuer) throws Exception {
        var leafKeyPair = generateKeyPair();
        var issuerName = new JcaX509CertificateHolder(issuer.certificate()).getSubject();
        var subject = new X500Name("CN=localhost");
        var now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                issuerName,
                BigInteger.valueOf(System.nanoTime()),
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(30, ChronoUnit.DAYS)),
                subject,
                leafKeyPair.getPublic());
        builder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
        var signer = new JcaContentSignerBuilder("SHA256withRSA").build(issuer.keyPair().getPrivate());
        var leaf = new JcaX509CertificateConverter().getCertificate(builder.build(signer));

        return new LeafCertificate(leaf, leafKeyPair);
    }

    public static KeyStore serverKeyStore(TestCa issuer, LeafCertificate leaf) throws Exception {
        var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("ds-tls", leaf.keyPair().getPrivate(), new char[0],
                new X509Certificate[] {leaf.certificate(), issuer.certificate()});
        return keyStore;
    }

    private static KeyPair generateKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    /** A minimal real HTTPS server: completes the handshake and answers every GET with "OK". */
    public static final class TestHttpsServer implements AutoCloseable {

        private final SSLServerSocket serverSocket;
        private final Thread acceptThread;

        private TestHttpsServer(SSLServerSocket serverSocket) {
            this.serverSocket = serverSocket;
            this.acceptThread = Thread.ofVirtual().start(this::acceptLoop);
        }

        public static TestHttpsServer start(KeyStore keyStore) throws Exception {
            var keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, new char[0]);
            var sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            var serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory()
                    .createServerSocket(0, 50, InetAddress.getByName("localhost"));
            return new TestHttpsServer(serverSocket);
        }

        public int port() {
            return serverSocket.getLocalPort();
        }

        private void acceptLoop() {
            while (!serverSocket.isClosed()) {
                try (var socket = (SSLSocket) serverSocket.accept()) {
                    respond(socket);
                } catch (IOException e) {
                    // Server closed, or the client aborted the handshake (the trust-rejection cases) -
                    // nothing to serve either way.
                }
            }
        }

        private void respond(SSLSocket socket) throws IOException {
            readHeaders(socket);
            var body = "OK";
            var response = "HTTP/1.1 200 OK\r\nContent-Length: " + body.length() + "\r\nConnection: close\r\n\r\n" + body;
            socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }

        private void readHeaders(SSLSocket socket) throws IOException {
            var in = socket.getInputStream();
            var buffer = new ByteArrayOutputStream();
            int next;
            while ((next = in.read()) != -1) {
                buffer.write(next);
                if (buffer.size() >= 4 && endsWithBlankLine(buffer.toByteArray())) {
                    return;
                }
            }
        }

        private static boolean endsWithBlankLine(byte[] data) {
            var length = data.length;
            return data[length - 4] == '\r' && data[length - 3] == '\n' && data[length - 2] == '\r' && data[length - 1] == '\n';
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            acceptThread.interrupt();
        }
    }
}
