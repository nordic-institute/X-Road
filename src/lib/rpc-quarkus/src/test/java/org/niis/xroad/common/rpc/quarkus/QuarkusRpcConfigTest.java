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
package org.niis.xroad.common.rpc.quarkus;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ClientCalls;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import jakarta.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.NoopVaultKeyProvider;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.file.FileKeyProvider;
import org.niis.xroad.common.rpc.test.PemFiles;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TlsTestChannels;
import org.niis.xroad.common.rpc.vault.ReloadableVaultKeyManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QuarkusRpcConfigTest {
    private static final String HOST = "localhost";
    private static final long DEADLINE_SECONDS = 10;
    private static final MethodDescriptor<byte[], byte[]> UNKNOWN_METHOD = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName(MethodDescriptor.generateFullMethodName("test.Unknown", "Call"))
            .setRequestMarshaller(new BytesMarshaller())
            .setResponseMarshaller(new BytesMarshaller())
            .build();

    @TempDir
    Path dir;

    private final QuarkusRpcConfig config = new QuarkusRpcConfig();
    private static final Optional<String> TOKEN = Optional.of("fake-openbao-token");
    private static final Optional<String> NO_TOKEN = Optional.empty();

    private final RpcProperties rpcProperties = mock(RpcProperties.class);
    @SuppressWarnings("unchecked")
    private final Provider<VaultPKISecretEngineFactory> pkiFactory = mock(Provider.class);

    private final List<VaultKeyProvider> keyProviders = new ArrayList<>();
    private VaultKeyProvider keyProvider;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() {
        when(rpcProperties.useTls()).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        keyProviders.forEach(VaultKeyProvider::shutdown);
        if (channel != null) {
            channel.shutdownNow().awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(DEADLINE_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    void fileSourceServesMutualTlsWithoutCreatingTheOpenBaoPkiFactory() throws Exception {
        var securityServerRpcCa = TestCa.selfSigned("Security Server RPC CA");
        var opMonitorCa = TestCa.selfSigned("op-monitor CA");
        useFileIdentity(opMonitorCa, securityServerRpcCa);

        keyProvider = produce(NO_TOKEN);

        assertThat(keyProvider).isInstanceOf(FileKeyProvider.class);
        verifyNoInteractions(pkiFactory);
        var credentials = new TlsRpcCredentialsConfigurer(keyProvider).createServerCredentials();
        server = NettyServerBuilder.forPort(0, credentials).build().start();
        channel = TlsTestChannels.trustedChannel(HOST, server.getPort(), HOST,
                securityServerRpcCa.issueLeaf("proxy", List.of(HOST), List.of()), opMonitorCa.certificate());
        assertThatThrownBy(this::callUnknownMethod)
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED));
    }

    @Test
    void fileSourceWithUnreadableFilesFailsTheProducer() {
        var missing = dir.resolve("missing");
        when(rpcProperties.identitySource()).thenReturn(RpcProperties.IdentitySource.FILE);
        when(rpcProperties.fileIdentity()).thenReturn(new RpcProperties.FileIdentity(missing, missing, missing));

        assertThatThrownBy(() -> produce(NO_TOKEN)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(pkiFactory);
    }

    @Test
    void fileSourceWithATokenSetNeverCallsOpenBao() throws Exception {
        var securityServerRpcCa = TestCa.selfSigned("Security Server RPC CA");
        useFileIdentity(TestCa.selfSigned("op-monitor CA"), securityServerRpcCa);

        keyProvider = produce(TOKEN);

        assertThat(keyProvider).isInstanceOf(FileKeyProvider.class);
        verifyNoInteractions(pkiFactory);
    }

    @Test
    void openBaoSourceWithATokenReturnsTheReloadableOpenBaoKeyManager() throws Exception {
        useOpenBaoIdentity();

        keyProvider = produce(TOKEN);

        assertThat(keyProvider).isInstanceOf(ReloadableVaultKeyManager.class);
    }

    @Test
    void openBaoSourceWithoutAUsableTokenFailsTheProducer() {
        useOpenBaoIdentity();

        for (var token : List.of(NO_TOKEN, Optional.of(""), Optional.of("  "))) {
            assertThatThrownBy(() -> produce(token))
                    .isInstanceOf(XrdRuntimeException.class)
                    .hasMessageContaining("OpenBao token")
                    .hasMessageContaining("XROAD_SECRET_STORE_TOKEN")
                    .hasMessageNotContaining("identity-source");
        }
        verifyNoInteractions(pkiFactory);
    }

    @Test
    void producerCarriesNoStateFromOpenBaoToFile() throws Exception {
        useOpenBaoIdentity();
        assertThat(produce(TOKEN)).isInstanceOf(ReloadableVaultKeyManager.class);

        useFileIdentity(TestCa.selfSigned("op-monitor CA"), TestCa.selfSigned("Security Server RPC CA"));
        assertThat(produce(NO_TOKEN)).isInstanceOf(FileKeyProvider.class);
    }

    @Test
    void producerCarriesNoStateFromFileToOpenBao() throws Exception {
        useFileIdentity(TestCa.selfSigned("op-monitor CA"), TestCa.selfSigned("Security Server RPC CA"));
        assertThat(produce(NO_TOKEN)).isInstanceOf(FileKeyProvider.class);

        useOpenBaoIdentity();
        assertThat(produce(TOKEN)).isInstanceOf(ReloadableVaultKeyManager.class);
        assertThatThrownBy(() -> produce(NO_TOKEN)).isInstanceOf(XrdRuntimeException.class);
    }

    @Test
    void disabledTlsReturnsTheNoopProviderWhateverTheSource() throws Exception {
        when(rpcProperties.useTls()).thenReturn(false);
        for (var source : RpcProperties.IdentitySource.values()) {
            when(rpcProperties.identitySource()).thenReturn(source);

            assertThat(produce(NO_TOKEN)).isInstanceOf(NoopVaultKeyProvider.class);
        }
        verifyNoInteractions(pkiFactory);
    }

    private VaultKeyProvider produce(Optional<String> token) throws Exception {
        var produced = config.vaultKeyProvider(rpcProperties, pkiFactory, token);
        keyProviders.add(produced);
        return produced;
    }

    private void useOpenBaoIdentity() {
        when(rpcProperties.identitySource()).thenReturn(RpcProperties.IdentitySource.OPENBAO);
        var provisioning = mock(RpcProperties.RpcCertificateProvisioningProperties.class);
        when(provisioning.waitDuration()).thenReturn(Duration.ofSeconds(5));
        when(provisioning.exponentialBackoffMultiplier()).thenReturn(1.5);
        when(provisioning.exponentialMaxWaitDuration()).thenReturn(Duration.ofMinutes(5));
        when(provisioning.refreshInterval()).thenReturn(Duration.ofHours(5));
        when(rpcProperties.certificateProvisioning()).thenReturn(provisioning);
        when(pkiFactory.get()).thenReturn(mock(VaultPKISecretEngineFactory.class));
    }

    private void useFileIdentity(TestCa serverCa, TestCa trustedCa) throws Exception {
        var serverIdentity = serverCa.issueLeaf("op-monitor", List.of(HOST), List.of());
        var privateKey = PemFiles.writePrivateKey(dir.resolve("server.key"), serverIdentity.privateKey());
        var certificate = PemFiles.writeCertificates(dir.resolve("server.crt"), serverIdentity.chain()[0]);
        var trustedCaFile = PemFiles.writeCertificates(dir.resolve("trusted-ca.crt"), trustedCa.certificate());
        when(rpcProperties.identitySource()).thenReturn(RpcProperties.IdentitySource.FILE);
        when(rpcProperties.fileIdentity()).thenReturn(new RpcProperties.FileIdentity(privateKey, certificate, trustedCaFile));
    }

    private void callUnknownMethod() {
        ClientCalls.blockingUnaryCall(channel, UNKNOWN_METHOD,
                CallOptions.DEFAULT.withDeadlineAfter(DEADLINE_SECONDS, TimeUnit.SECONDS), new byte[0]);
    }

    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            return new byte[0];
        }
    }
}
