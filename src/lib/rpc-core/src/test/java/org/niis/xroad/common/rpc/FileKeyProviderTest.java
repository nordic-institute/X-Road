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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
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
    void missingFileFailsInit() throws Exception {
        Files.delete(privateKeyPath);
        assertInitFails();
    }

    @Test
    void emptyFileFailsInit() throws Exception {
        PemFiles.write(certificatePath, "");
        assertInitFails();
    }

    @Test
    void privateKeyFileWithoutAKeyFailsInit() throws Exception {
        PemFiles.write(privateKeyPath, "not a key");
        assertInitFails();
    }

    @Test
    void certificateFileWithoutACertificateFailsInit() throws Exception {
        PemFiles.write(certificatePath, "not a certificate");
        assertInitFails();
    }

    @Test
    void trustedCaFileWithTwoCertificatesFailsInit() throws Exception {
        PemFiles.writeCertificates(trustedCaPath, securityServerRpcCa.certificate(), opMonitorCa.certificate());
        assertInitFails();
    }

    private FileKeyProvider newProvider() throws Exception {
        return new FileKeyProvider(new RpcProperties.FileIdentity(privateKeyPath, certificatePath, trustedCaPath));
    }

    private FileKeyProvider loadedProvider() throws Exception {
        var provider = newProvider();
        provider.init();
        return provider;
    }

    private void assertInitFails() throws Exception {
        assertThatThrownBy(newProvider()::init).isInstanceOf(XrdRuntimeException.class);
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
