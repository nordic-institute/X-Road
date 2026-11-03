/*
 * The MIT License
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
package org.niis.xroad.opmonitor.test;

import io.grpc.ConnectivityState;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
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
import org.niis.xroad.opmonitor.client.OpMonitorChannelTrustResolver;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorContainerSetup;
import org.niis.xroad.test.apitest.core.junit.ApiStackExtension;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * 0210 - The proxy's op-monitor channel trust follows the op-monitor CA record of the real OpenBao. The resolver and
 * {@link OpMonitorClient} are built as the proxy builds them: the Quarkus vault client reads the record and RPC TLS is
 * on. In-JVM op-monitor servers require mutual TLS; one presents a certificate of the Security Server CA, the other one of
 * the op-monitor CA. Creating a channel again stands for a restart. Each case starts and ends without an op-monitor CA
 * record.
 */
@ExtendWith(ApiStackExtension.class)
@ResourceLock(OpenBaoTestSupport.OP_MONITOR_CA_RECORD)
class OpMonitorChannelTrustOpenBaoIntTest {

    private static final String HOST = "localhost";
    private static final long FAIL_FAST_DEADLINE_SECONDS = 10;

    private final List<AutoCloseable> closeables = new ArrayList<>();

    private OpMonitorContainerSetup stack;
    private VaultClient vaultClient;
    private TestCa opMonitorCa;
    private OpMonitorChannelTrustResolver resolver;
    private TlsTestServer securityServerCaServer;
    private TlsTestServer opMonitorCaServer;

    @BeforeEach
    @SneakyThrows
    void stackPrepared(OpMonitorContainerSetup setup) {
        stack = setup;
        OpenBaoTestSupport.deleteOpMonitorCaRecord(stack);
        vaultClient = OpenBaoTestSupport.quarkusVaultClient(OpenBaoTestSupport.vaultApi(stack));

        var securityServerCa = TestCa.selfSigned("Security Server CA " + System.nanoTime());
        opMonitorCa = TestCa.selfSigned("Op-monitor CA " + System.nanoTime());

        var proxyIdentity = securityServerCa.issueLeaf("proxy", List.of(HOST), List.of());
        var keyProvider = new TestVaultKeyProvider(proxyIdentity, securityServerCa.certificate());
        RpcCredentialsConfigurer sharedCredentials = new TlsRpcCredentialsConfigurer(keyProvider);
        var rpcProperties = mock(RpcProperties.class);
        when(rpcProperties.useTls()).thenReturn(true);
        resolver = new OpMonitorChannelTrustResolver(rpcProperties, vaultClient, keyProvider, sharedCredentials);

        securityServerCaServer = startServer(securityServerCa, securityServerCa.certificate());
        opMonitorCaServer = startServer(opMonitorCa, securityServerCa.certificate());
    }

    @AfterEach
    void stackCleaned() {
        closeAll();
        OpenBaoTestSupport.deleteOpMonitorCaRecord(stack);
    }

    @Test
    @DisplayName("With an imported CA the channel trusts only that CA")
    void importedCaIsTheOnlyTrustedCa() {
        given("the op-monitor CA is imported", () -> vaultClient.createOpMonitorCa(opMonitorCa.certificate()));

        var toOpMonitorCaServer = when("a channel is created to the server of the op-monitor CA",
                () -> newChannel(opMonitorCaServer));
        var toSecurityServerCaServer = when("a channel is created to the server of the Security Server CA",
                () -> newChannel(securityServerCaServer));

        then("the first trusts the op-monitor certificate of the imported CA", () -> assertAccepted(toOpMonitorCaServer));
        and("the second rejects the op-monitor certificate of the Security Server CA",
                () -> assertRejected(toSecurityServerCaServer));
    }

    @Test
    @DisplayName("Without an imported CA the channel trusts the Security Server CA")
    void securityServerCaIsTrustedWithoutAnImportedCa() {
        var toSecurityServerCaServer = when("a channel is created to the server of the Security Server CA with no record",
                () -> newChannel(securityServerCaServer));
        var toOpMonitorCaServer = when("a channel is created to the server of the op-monitor CA with no record",
                () -> newChannel(opMonitorCaServer));

        then("the first trusts the op-monitor certificate of the Security Server CA",
                () -> assertAccepted(toSecurityServerCaServer));
        and("the second rejects the op-monitor certificate of the op-monitor CA",
                () -> assertRejected(toOpMonitorCaServer));
    }

    @Test
    @DisplayName("After the CA is deleted and the channel is recreated, the Security Server CA is trusted again")
    void securityServerCaIsTrustedAgainAfterTheCaIsDeleted() {
        given("the op-monitor CA is imported", () -> vaultClient.createOpMonitorCa(opMonitorCa.certificate()));
        var importedChannel = when("a channel is created to the server of the Security Server CA",
                () -> newChannel(securityServerCaServer));
        then("it rejects the op-monitor certificate of the Security Server CA", () -> assertRejected(importedChannel));

        given("the op-monitor CA is deleted", () -> vaultClient.clearOpMonitorCa());
        var toSecurityServerCaServer = when("the channel is recreated", () -> newChannel(securityServerCaServer));
        var toOpMonitorCaServer = when("a channel is created to the server of the op-monitor CA",
                () -> newChannel(opMonitorCaServer));

        then("the recreated channel trusts the op-monitor certificate of the Security Server CA",
                () -> assertAccepted(toSecurityServerCaServer));
        and("a channel rejects the op-monitor certificate of the deleted CA", () -> assertRejected(toOpMonitorCaServer));
    }

    @Test
    @DisplayName("A channel created before an import keeps its trust until it is recreated")
    void channelKeepsItsTrustUntilRecreated() {
        var earlierChannel = when("a channel is created to the server of the Security Server CA with no record",
                () -> newChannel(securityServerCaServer));
        given("the op-monitor CA is imported", () -> vaultClient.createOpMonitorCa(opMonitorCa.certificate()));

        then("the earlier channel still trusts the Security Server CA", () -> assertAccepted(earlierChannel));

        var recreatedChannel = when("the channel is recreated", () -> newChannel(securityServerCaServer));
        then("it rejects the op-monitor certificate of the Security Server CA", () -> assertRejected(recreatedChannel));
    }

    private OpMonitorClient newChannel(TlsTestServer server) {
        var client = OpMonitorClient.create(new RpcChannelFactory(mock(RpcCredentialsConfigurer.class)), config(server.port()),
                resolver);
        closeables.add(client);
        return client;
    }

    private static void assertAccepted(OpMonitorClient client) {
        assertThatCode(() -> client.storeOperationalData(List.of())).doesNotThrowAnyException();
    }

    private static void assertRejected(OpMonitorClient client) {
        var failFastStub = OpMonitorServiceGrpc.newBlockingStub(client.getChannel())
                .withDeadlineAfter(FAIL_FAST_DEADLINE_SECONDS, TimeUnit.SECONDS);

        assertThatThrownBy(() -> failFastStub.storeOperationalData(StoreOperationalDataReq.getDefaultInstance()))
                .isInstanceOfSatisfying(StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE))
                .satisfies(e -> assertThat(causalChain(e)).anyMatch(CertificateException.class::isInstance));
        assertThat(client.getChannel().getState(false)).isEqualTo(ConnectivityState.TRANSIENT_FAILURE);
    }

    private static Stream<Throwable> causalChain(Throwable throwable) {
        return Stream.iterate(throwable, Objects::nonNull, Throwable::getCause);
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

    @SneakyThrows
    private TlsTestServer startServer(TestCa serverCa, X509Certificate clientTrustAnchor) {
        var identity = serverCa.issueLeaf("op-monitor", List.of(HOST), List.of());
        var server = TlsTestServer.start(identity, clientTrustAnchor, new StoreService());
        closeables.add(server);
        return server;
    }

    @SneakyThrows
    private void closeAll() {
        for (var closeable : closeables.reversed()) {
            closeable.close();
        }
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
