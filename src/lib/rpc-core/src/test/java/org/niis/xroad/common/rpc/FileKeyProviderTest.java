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
package org.niis.xroad.common.rpc;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ClientCalls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.file.FileKeyProvider;
import org.niis.xroad.common.rpc.test.PemFiles;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TlsTestChannels;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileKeyProviderTest {
    private static final String HOST = "localhost";
    private static final byte[] PAYLOAD = {1, 2, 3};
    private static final long DEADLINE_SECONDS = 10;

    @TempDir
    Path dir;

    private TestCa securityServerRpcCa;
    private TestCa opMonitorCa;
    private Path privateKeyPath;
    private Path certificatePath;
    private Path trustedCaPath;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        securityServerRpcCa = TestCa.selfSigned("Security Server RPC CA");
        opMonitorCa = TestCa.selfSigned("op-monitor CA");
        var serverIdentity = opMonitorCa.issueLeaf("op-monitor", List.of(HOST), List.of());

        privateKeyPath = PemFiles.writePrivateKey(dir.resolve("server.key"), serverIdentity.privateKey());
        certificatePath = PemFiles.writeCertificates(dir.resolve("server.crt"), serverIdentity.chain()[0]);
        trustedCaPath = PemFiles.writeCertificates(dir.resolve("trusted-ca.crt"), securityServerRpcCa.certificate());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void clientSignedByTheTrustedCaCompletesTheMutualTlsHandshake() throws Exception {
        startServer(loadedProvider());

        connectWithClientCertificateFrom(securityServerRpcCa);

        assertThat(echo()).isEqualTo(PAYLOAD);
    }

    @Test
    void clientSignedByAnotherCaIsRejected() throws Exception {
        startServer(loadedProvider());

        connectWithClientCertificateFrom(TestCa.selfSigned("Other CA"));

        assertRejected();
    }

    @Test
    void ecPrivateKeyInPkcs8LoadsAndServes() throws Exception {
        var ecIdentity = opMonitorCa.issueEcLeaf("op-monitor", List.of(HOST), List.of());
        PemFiles.writePrivateKey(privateKeyPath, ecIdentity.privateKey());
        PemFiles.writeCertificates(certificatePath, ecIdentity.chain()[0]);
        startServer(loadedProvider());

        connectWithClientCertificateFrom(securityServerRpcCa);

        assertThat(echo()).isEqualTo(PAYLOAD);
    }

    @Test
    void certificateChainWithIntermediateLoads() throws Exception {
        var identity = opMonitorCa.issueLeaf("op-monitor", List.of(HOST), List.of());
        PemFiles.writePrivateKey(privateKeyPath, identity.privateKey());
        PemFiles.writeCertificates(certificatePath, identity.chain());

        assertThatCode(this::loadedProvider).doesNotThrowAnyException();
    }

    @Test
    void missingFileFailsInit() throws Exception {
        Files.delete(privateKeyPath);
        assertInitFails(privateKeyPath, "missing or cannot be read");
    }

    @Test
    void unreadableFileFailsInit() throws Exception {
        Files.delete(certificatePath);
        Files.createDirectory(certificatePath);
        assertInitFails(certificatePath, "missing or cannot be read");
    }

    @Test
    void emptyFileFailsInit() throws Exception {
        PemFiles.write(certificatePath, "");
        assertInitFails(certificatePath, "empty");
    }

    @Test
    void unsetPathFailsInitWithAGenericMessage() {
        var provider = providerFor(Path.of(""), certificatePath, trustedCaPath);
        assertThatThrownBy(provider::init)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("the private key path is not set")
                .hasMessageNotContaining("xroad.");
    }

    @Test
    void privateKeyFileWithoutAKeyFailsInit() throws Exception {
        PemFiles.write(privateKeyPath, "not a key");
        assertInitFails(privateKeyPath, "PKCS#8");
    }

    @Test
    void encryptedPrivateKeyFailsInit() throws Exception {
        var identity = opMonitorCa.issueLeaf("op-monitor", List.of(HOST), List.of());
        PemFiles.writeEncryptedPrivateKey(privateKeyPath, identity.privateKey(), "secret".toCharArray());
        assertInitFails(privateKeyPath, "PKCS#8");
    }

    @Test
    void sec1EcPrivateKeyFailsInit() throws Exception {
        var ecIdentity = opMonitorCa.issueEcLeaf("op-monitor", List.of(HOST), List.of());
        PemFiles.writeEcPrivateKeySec1(privateKeyPath, ecIdentity.privateKey());
        PemFiles.writeCertificates(certificatePath, ecIdentity.chain()[0]);
        assertInitFails(privateKeyPath, "PKCS#8");
    }

    @Test
    void corruptPrivateKeyFailsInit() throws Exception {
        PemFiles.write(privateKeyPath, "-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----\n");
        assertInitFails(privateKeyPath, "cannot be parsed");
    }

    @Test
    void certificateFileWithoutACertificateFailsInit() throws Exception {
        PemFiles.write(certificatePath, "not a certificate");
        assertInitFails(certificatePath, "cannot be parsed");
    }

    @Test
    void privateKeyOfAnotherCertificateFailsInit() throws Exception {
        var other = opMonitorCa.issueLeaf("other", List.of(HOST), List.of());
        PemFiles.writePrivateKey(privateKeyPath, other.privateKey());
        assertInitFails(privateKeyPath, "does not match");
    }

    @Test
    void privateKeyOfAnotherKeyTypeFailsInit() throws Exception {
        var ecIdentity = opMonitorCa.issueEcLeaf("other", List.of(HOST), List.of());
        PemFiles.writePrivateKey(privateKeyPath, ecIdentity.privateKey());
        assertInitFails(privateKeyPath, "does not match");
    }

    @Test
    void trustedCaFileWithoutACertificateFailsInit() throws Exception {
        PemFiles.write(trustedCaPath, "not a certificate");
        assertInitFails(trustedCaPath, "cannot be parsed");
    }

    @Test
    void trustedCaFileWithTwoCaCertificatesFailsInit() throws Exception {
        PemFiles.writeCertificates(trustedCaPath, securityServerRpcCa.certificate(), opMonitorCa.certificate());
        assertInitFails(trustedCaPath, "exactly one");
    }

    @Test
    void trustedCaFileWithANonCaCertificateFailsInit() throws Exception {
        PemFiles.writeCertificates(trustedCaPath, TestCa.selfSignedNonCa("not a CA"));
        assertInitFails(trustedCaPath, "not a CA certificate");
    }

    @Test
    void expiredServerCertificateFailsInit() throws Exception {
        writeServerIdentity(opMonitorCa.issueLeaf("op-monitor", List.of(HOST), List.of(), daysFromNow(-10), daysFromNow(-1)));
        assertInitFails(certificatePath, "server certificate expired");
    }

    @Test
    void notYetValidServerCertificateFailsInit() throws Exception {
        writeServerIdentity(opMonitorCa.issueLeaf("op-monitor", List.of(HOST), List.of(), daysFromNow(1), daysFromNow(10)));
        assertInitFails(certificatePath, "server certificate is not valid before");
    }

    @Test
    void serverCertificateThatIsACaFailsInit() throws Exception {
        writeServerIdentity(opMonitorCa.issueCaLeaf("op-monitor", List.of(HOST), List.of()));
        assertInitFails(certificatePath, "server certificate is a CA certificate");
    }

    @Test
    void expiredTrustedCaFailsInit() throws Exception {
        PemFiles.writeCertificates(trustedCaPath, TestCa.selfSigned("expired CA", daysFromNow(-10), daysFromNow(-1)).certificate());
        assertInitFails(trustedCaPath, "trusted CA certificate expired");
    }

    @Test
    void notYetValidTrustedCaFailsInit() throws Exception {
        PemFiles.writeCertificates(trustedCaPath, TestCa.selfSigned("future CA", daysFromNow(1), daysFromNow(10)).certificate());
        assertInitFails(trustedCaPath, "trusted CA certificate is not valid before");
    }

    @Test
    void verifyingTrustManagerBeforeInitFails() throws Exception {
        var provider = newProvider();
        assertThatThrownBy(() -> provider.createVerifyingTrustManager(null))
                .isInstanceOf(XrdRuntimeException.class);
    }

    private void writeServerIdentity(TestCa.TestLeaf identity) throws Exception {
        PemFiles.writePrivateKey(privateKeyPath, identity.privateKey());
        PemFiles.writeCertificates(certificatePath, identity.chain()[0]);
    }

    private static Instant daysFromNow(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private FileKeyProvider newProvider() {
        return providerFor(privateKeyPath, certificatePath, trustedCaPath);
    }

    private static FileKeyProvider providerFor(Path privateKey, Path certificate, Path trustedCa) {
        try {
            return new FileKeyProvider(new RpcProperties.FileIdentity(privateKey, certificate, trustedCa));
        } catch (CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    private FileKeyProvider loadedProvider() {
        var provider = newProvider();
        provider.init();
        return provider;
    }

    private void assertInitFails(Path failingFile, String reason) {
        assertThatThrownBy(newProvider()::init)
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining(failingFile.toString())
                .hasMessageContaining(reason);
    }

    private void startServer(VaultKeyProvider keyProvider) throws Exception {
        var credentials = new TlsRpcCredentialsConfigurer(keyProvider).createServerCredentials();
        server = NettyServerBuilder.forPort(0, credentials).addService(new ByteEchoService()).build().start();
    }

    private void connectWithClientCertificateFrom(TestCa clientCa) throws Exception {
        channel = TlsTestChannels.trustedChannel(HOST, server.getPort(), HOST,
                clientCa.issueLeaf("proxy", List.of(HOST), List.of()), opMonitorCa.certificate());
    }

    private void assertRejected() {
        assertThatThrownBy(this::echo)
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE));
    }

    private byte[] echo() {
        return ClientCalls.blockingUnaryCall(channel, ByteEchoService.ECHO,
                CallOptions.DEFAULT.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS), PAYLOAD);
    }
}
