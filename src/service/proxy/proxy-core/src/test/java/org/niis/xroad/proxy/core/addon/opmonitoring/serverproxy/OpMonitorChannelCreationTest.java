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
package org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy;

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TestVaultKeyProvider;
import org.niis.xroad.common.rpc.test.TlsTestServer;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsReq;
import org.niis.xroad.opmonitor.api.GetOperationalDataIntervalsResp;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.QueryOperationalDataReq;
import org.niis.xroad.opmonitor.api.QueryOperationalDataResp;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;
import org.niis.xroad.opmonitor.api.StoreOperationalDataResp;
import org.niis.xroad.opmonitor.client.OpMonitorChannelTrustResolver;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitorStoreClient;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImplTest.globalConfProvider;
import static org.niis.xroad.proxy.core.addon.opmonitoring.serverproxy.OpMonitoringServiceHandlerImplTest.proxyMessage;

/**
 * Creation of the op-monitor channel by the proxy store, the proxy query and the admin-service call path, each over a
 * real mutual TLS connection with the same channel-trust resolver.
 */
class OpMonitorChannelCreationTest {
    private static final String HOST = "localhost";
    private static final String READ_FAILURE = "Failed to read the imported op-monitor CA";
    private static final String MALFORMED_CERTIFICATE_FAILURE = "Could not parse certificate";

    private static TestCa securityServerCa;
    private static TestCa opMonitorCa;
    private static TestVaultKeyProvider keyProvider;

    private TlsTestServer server;

    @BeforeAll
    static void createCertificates() throws Exception {
        securityServerCa = TestCa.selfSigned("Security Server CA");
        opMonitorCa = TestCa.selfSigned("Op-monitor CA");
        keyProvider = new TestVaultKeyProvider(securityServerCa.issueLeaf("proxy", List.of(HOST), List.of()),
                securityServerCa.certificate());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest(name = "{0}, {1}")
    @MethodSource("callersWithSituations")
    void everyCallerGetsTheSameOutcomeForTheSameSetup(Caller caller, Situation situation) throws Exception {
        var wiring = wiring(vaultClient(situation.read()), situation.serverCa());
        var invocation = caller.open(wiring, wiring.proxyClientFactory());
        try {
            if (situation.expectedFailure() == null) {
                assertThatCode(invocation::call).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(invocation::call)
                        .satisfies(e -> assertThat(messages(e)).contains(situation.expectedFailure()));
            }
        } finally {
            invocation.close();
        }
    }

    @ParameterizedTest
    @EnumSource(value = Caller.class, names = {"PROXY_STORE", "PROXY_QUERY"})
    void aFailedCreationIsNotRememberedAndTheNextCallCreatesTheChannel(Caller caller) throws Exception {
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getOpMonitorCa())
                .thenThrow(new IllegalStateException("vault is sealed"))
                .thenReturn(Optional.empty());
        var wiring = wiring(vaultClient, securityServerCa);
        var creations = new AtomicInteger();
        Supplier<OpMonitorClient> countingFactory = () -> {
            creations.incrementAndGet();
            return wiring.proxyClientFactory().get();
        };
        var invocation = caller.open(wiring, countingFactory);
        try {
            assertThatThrownBy(invocation::call).satisfies(e -> assertThat(messages(e)).contains(READ_FAILURE));

            assertThatCode(invocation::call).doesNotThrowAnyException();
            assertThatCode(invocation::call).doesNotThrowAnyException();

            assertThat(creations).hasValue(2);
            verify(vaultClient, times(2)).getOpMonitorCa();
        } finally {
            invocation.close();
        }
    }

    static Stream<Arguments> callersWithSituations() {
        return Stream.of(Caller.values())
                .flatMap(caller -> Stream.of(Situation.values()).map(situation -> Arguments.of(caller, situation)));
    }

    private Wiring wiring(VaultClient vaultClient, TestCa serverCa) throws Exception {
        var serverIdentity = serverCa.issueLeaf("op-monitor", List.of(HOST), List.of());
        server = TlsTestServer.start(serverIdentity, securityServerCa.certificate(), new AnsweringService());
        var rpcProperties = mock(RpcProperties.class);
        when(rpcProperties.useTls()).thenReturn(true);
        var shared = new TlsRpcCredentialsConfigurer(keyProvider);
        var config = XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.common-rpc.channel.op-monitor.host", HOST,
                        "xroad.common-rpc.channel.op-monitor.port", String.valueOf(server.port()),
                        "xroad.common-rpc.channel.op-monitor.deadline-after", "10000"))
                .build();
        return new Wiring(new RpcChannelFactory(shared), new OpMonitorRpcChannelProperties(config),
                new OpMonitorChannelTrustResolver(rpcProperties, vaultClient, keyProvider, shared), config);
    }

    private static VaultClient vaultClient(ImportedCaRead read) {
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getOpMonitorCa()).thenAnswer(_ -> read.read());
        return vaultClient;
    }

    private static String messages(Throwable throwable) {
        var messages = new StringBuilder();
        for (var current = throwable; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append('\n');
        }
        return messages.toString();
    }

    @FunctionalInterface
    private interface ImportedCaRead {
        Optional<X509Certificate> read();
    }

    private enum Situation {
        IMPORTED_CA(() -> Optional.of(opMonitorCa.certificate()), true, null),
        BLANK_OR_MISSING_RECORD(Optional::empty, false, null),
        OPENBAO_ERROR(() -> {
            throw new IllegalStateException("vault is sealed");
        }, false, READ_FAILURE),
        MALFORMED_PEM(() -> mock(VaultClient.class, CALLS_REAL_METHODS)
                .toOpMonitorCa(Map.of(VaultClient.CERTIFICATE_KEY, "not a certificate")), false, MALFORMED_CERTIFICATE_FAILURE),
        KV_ENGINE_NOT_INITIALISED(() -> {
            throw new IllegalStateException("Vault KV Secret Engine is not initialized. Check configuration.");
        }, false, READ_FAILURE);

        private final ImportedCaRead read;
        private final boolean serverIssuedByImportedCa;
        private final String expectedFailure;

        Situation(ImportedCaRead read, boolean serverIssuedByImportedCa, String expectedFailure) {
            this.read = read;
            this.serverIssuedByImportedCa = serverIssuedByImportedCa;
            this.expectedFailure = expectedFailure;
        }

        ImportedCaRead read() {
            return read;
        }

        TestCa serverCa() {
            return serverIssuedByImportedCa ? opMonitorCa : securityServerCa;
        }

        String expectedFailure() {
            return expectedFailure;
        }
    }

    private record Wiring(RpcChannelFactory channelFactory, OpMonitorRpcChannelProperties channelProperties,
                          OpMonitorChannelTrustResolver resolver, XRoadConfig config) {

        Supplier<OpMonitorClient> proxyClientFactory() {
            return () -> OpMonitorClient.create(channelFactory, config, resolver);
        }

        OpMonitorClient adminServiceBean() {
            var bean = new OpMonitorClient(channelFactory, channelProperties, resolver);
            bean.init();
            return bean;
        }
    }

    private interface Invocation extends AutoCloseable {
        void call() throws Exception;

        @Override
        void close();
    }

    private enum Caller {
        PROXY_STORE {
            @Override
            Invocation open(Wiring wiring, Supplier<OpMonitorClient> proxyClientFactory) {
                var storeClient = new OpMonitorStoreClient(proxyClientFactory);
                return new Invocation() {
                    @Override
                    public void call() {
                        storeClient.store(List.of());
                    }

                    @Override
                    public void close() {
                        storeClient.close();
                    }
                };
            }
        },
        PROXY_QUERY {
            @Override
            Invocation open(Wiring wiring, Supplier<OpMonitorClient> proxyClientFactory) {
                var handler = new OpMonitoringServiceHandlerImpl(mock(ServerConfProvider.class), globalConfProvider(),
                        proxyClientFactory);
                return new Invocation() {
                    @Override
                    public void call() throws Exception {
                        handler.startHandling(null, proxyMessage(),
                                new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, 1L));
                    }

                    @Override
                    public void close() {
                        handler.destroy();
                    }
                };
            }
        },
        ADMIN_SERVICE {
            @Override
            Invocation open(Wiring wiring, Supplier<OpMonitorClient> proxyClientFactory) {
                return new Invocation() {
                    private OpMonitorClient bean;

                    @Override
                    public void call() {
                        bean = wiring.adminServiceBean();
                        bean.getOperationalDataIntervals(0L, 1L, 1, null, null, null);
                    }

                    @Override
                    public void close() {
                        if (bean != null) {
                            bean.close();
                        }
                    }
                };
            }
        };

        abstract Invocation open(Wiring wiring, Supplier<OpMonitorClient> proxyClientFactory);
    }

    private static final class AnsweringService extends OpMonitorServiceGrpc.OpMonitorServiceImplBase {
        @Override
        public void storeOperationalData(StoreOperationalDataReq request,
                                         StreamObserver<StoreOperationalDataResp> responseObserver) {
            responseObserver.onNext(StoreOperationalDataResp.getDefaultInstance());
            responseObserver.onCompleted();
        }

        @Override
        public void queryOperationalData(QueryOperationalDataReq request,
                                         StreamObserver<QueryOperationalDataResp> responseObserver) {
            responseObserver.onNext(QueryOperationalDataResp.newBuilder()
                    .setResponse(ByteString.copyFromUtf8("<response/>"))
                    .setContentType("text/xml;charset=utf-8")
                    .build());
            responseObserver.onCompleted();
        }

        @Override
        public void getOperationalDataIntervals(GetOperationalDataIntervalsReq request,
                                                StreamObserver<GetOperationalDataIntervalsResp> responseObserver) {
            responseObserver.onNext(GetOperationalDataIntervalsResp.getDefaultInstance());
            responseObserver.onCompleted();
        }
    }
}
