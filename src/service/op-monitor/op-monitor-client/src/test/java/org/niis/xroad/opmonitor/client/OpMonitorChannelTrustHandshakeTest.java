/*
 * The MIT License
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
package org.niis.xroad.opmonitor.client;

import io.grpc.ConnectivityState;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TestVaultKeyProvider;
import org.niis.xroad.common.rpc.test.TlsTestServer;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;
import org.niis.xroad.opmonitor.api.StoreOperationalDataResp;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpMonitorChannelTrustHandshakeTest {
    private static final String HOST = "localhost";
    private static final String OTHER_HOST = "other.example.org";
    private static final long FAIL_FAST_DEADLINE_SECONDS = 10;

    private TestCa securityServerCa;
    private TestCa opMonitorCa;
    private TestCa untrustedCa;
    private OpMonitorChannelTrustResolver resolverWithImportedCa;
    private OpMonitorChannelTrustResolver resolverWithoutImportedCa;
    private TlsTestServer server;
    private OpMonitorClient client;

    @BeforeEach
    void setUp() throws Exception {
        securityServerCa = TestCa.selfSigned("Security Server CA");
        opMonitorCa = TestCa.selfSigned("Op-monitor CA");
        untrustedCa = TestCa.selfSigned("Untrusted CA");

        var clientIdentity = securityServerCa.issueLeaf("proxy", List.of(HOST), List.of());
        var keyProvider = new TestVaultKeyProvider(clientIdentity, securityServerCa.certificate());
        RpcCredentialsConfigurer shared = new TlsRpcCredentialsConfigurer(keyProvider);

        var rpcProperties = mock(RpcProperties.class);
        when(rpcProperties.useTls()).thenReturn(true);
        resolverWithImportedCa = resolver(rpcProperties, Optional.of(opMonitorCa.certificate()), keyProvider, shared);
        resolverWithoutImportedCa = resolver(rpcProperties, Optional.empty(), keyProvider, shared);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void importedCaIsTrustedForTheServerCertificateItIssued() throws Exception {
        startServer(opMonitorCa, HOST, resolverWithImportedCa);

        assertThatCode(() -> client.storeOperationalData(List.of())).doesNotThrowAnyException();
    }

    @Test
    void securityServerCaCertificateIsRejectedWhenACaIsImported() throws Exception {
        startServer(securityServerCa, HOST, resolverWithImportedCa);

        assertRejectedByCertificateVerification();
    }

    @Test
    void certificateFromAnUntrustedCaIsRejectedWhenACaIsImported() throws Exception {
        startServer(untrustedCa, HOST, resolverWithImportedCa);

        assertRejectedByCertificateVerification();
    }

    @Test
    void certificateWithANameThatDoesNotMatchTheDialedHostIsRejectedWhenACaIsImported() throws Exception {
        startServer(opMonitorCa, OTHER_HOST, resolverWithImportedCa);

        assertRejectedByCertificateVerification();
    }

    @Test
    void securityServerCaIsTrustedWhenNoCaIsImported() throws Exception {
        startServer(securityServerCa, HOST, resolverWithoutImportedCa);

        assertThatCode(() -> client.storeOperationalData(List.of())).doesNotThrowAnyException();
    }

    @Test
    void certificateFromAnUntrustedCaIsRejectedWhenNoCaIsImported() throws Exception {
        startServer(untrustedCa, HOST, resolverWithoutImportedCa);

        assertRejectedByCertificateVerification();
    }

    @Test
    void certificateWithANameThatDoesNotMatchTheDialedHostIsRejectedWhenNoCaIsImported() throws Exception {
        startServer(securityServerCa, OTHER_HOST, resolverWithoutImportedCa);

        assertRejectedByCertificateVerification();
    }

    private void assertRejectedByCertificateVerification() {
        var failFastStub = OpMonitorServiceGrpc.newBlockingStub(client.getChannel())
                .withDeadlineAfter(FAIL_FAST_DEADLINE_SECONDS, TimeUnit.SECONDS);

        assertThatThrownBy(() -> failFastStub.storeOperationalData(StoreOperationalDataReq.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE))
                .satisfies(e -> assertThat(causalChain(e)).anyMatch(CertificateException.class::isInstance));
        assertThat(client.getChannel().getState(false)).isEqualTo(ConnectivityState.TRANSIENT_FAILURE);
    }

    private static OpMonitorChannelTrustResolver resolver(RpcProperties rpcProperties, Optional<X509Certificate> importedCa,
                                                          TestVaultKeyProvider keyProvider, RpcCredentialsConfigurer shared) {
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getOpMonitorCa()).thenReturn(importedCa);
        return new OpMonitorChannelTrustResolver(rpcProperties, vaultClient, keyProvider, shared);
    }

    private static Stream<Throwable> causalChain(Throwable throwable) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause);
    }

    private void startServer(TestCa serverCa, String serverName, OpMonitorChannelTrustResolver resolver) throws Exception {
        var serverIdentity = serverCa.issueLeaf("op-monitor", List.of(serverName), List.of());
        server = TlsTestServer.start(serverIdentity, securityServerCa.certificate(), new StoreService());
        client = OpMonitorClient.create(new RpcChannelFactory(mock(RpcCredentialsConfigurer.class)), config(server.port()),
                resolver);
    }

    private static XRoadConfig config(int port) {
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.common-rpc.channel.op-monitor.host", HOST,
                        "xroad.common-rpc.channel.op-monitor.port", String.valueOf(port),
                        "xroad.common-rpc.channel.op-monitor.deadline-after", "3000"))
                .build();
    }

    private static final class StoreService extends OpMonitorServiceGrpc.OpMonitorServiceImplBase {
        @Override
        public void storeOperationalData(StoreOperationalDataReq request,
                                         StreamObserver<StoreOperationalDataResp> responseObserver) {
            responseObserver.onNext(StoreOperationalDataResp.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
