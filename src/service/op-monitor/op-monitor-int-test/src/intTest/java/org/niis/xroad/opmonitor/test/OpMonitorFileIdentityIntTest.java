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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.restassured.path.json.JsonPath;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.RpcProperties;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.TlsRpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.test.TestCa;
import org.niis.xroad.common.rpc.test.TestVaultKeyProvider;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.opmonitor.api.OpMonitorServiceGrpc;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataInterval;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;
import org.niis.xroad.opmonitor.client.OpMonitorChannelTrustResolver;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.test.container.OpMonitorFileIdentityContainerSetup;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.ServiceIdConverter;
import org.niis.xroad.test.apitest.core.config.ApiTestConfigSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * 0220 - op-monitor runs without OpenBao on key material created by the packaged identity tool. The stack has no
 * OpenBao service and no token; a trap at the OpenBao address records any connection. Clients connect as a proxy does:
 * a certificate of the Security Server RPC CA, trust in the tool-generated op-monitor CA. The stack is this class's
 * own, separate from the shared one.
 */
@SuppressWarnings("checkstyle:magicnumber")
class OpMonitorFileIdentityIntTest {

    private static final String STORE_CLIENT = "DEV:COM:222:TestClient";
    private static final String SERVICE_STORED = "DEV:COM:222:TestService:storedWithFileIdentity.v1";
    private static final Instant STORE_WINDOW = Instant.now().minus(20, ChronoUnit.DAYS);
    private static final String ACCEPTED_CONNECTION = "accepting connection";
    private static final long FAIL_FAST_DEADLINE_SECONDS = 10;
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(30);

    private static OpMonitorFileIdentityContainerSetup stack;
    private static FileIdentityMaterial material;

    private final List<OpMonitorClient> clients = new ArrayList<>();

    @BeforeAll
    @SneakyThrows
    static void stackStarted() {
        var properties = ApiTestConfigSource.getInstance().getCoreProperties();
        var root = Files.createTempDirectory(Files.createDirectories(Path.of(properties.workingDir())), "file-identity-")
                .toRealPath();
        material = FileIdentityMaterial.create(root);
        stack = new OpMonitorFileIdentityContainerSetup(properties, material.composeEnvironment());
        stack.start();
    }

    @AfterAll
    static void stackStopped() {
        if (stack != null) {
            stack.stop();
        }
    }

    @AfterEach
    void clientsClosed() {
        clients.forEach(OpMonitorClient::close);
        clients.clear();
    }

    @Test
    @DisplayName("op-monitor starts in file mode with no OpenBao and reports live")
    void startsAndReportsLive() {
        var liveness = when("the op-monitor liveness endpoint is read",
                () -> JsonPath.from(stack.livenessResponse()));
        then("the status is UP", () -> assertThat(liveness.getString("status")).isEqualTo("UP"));
    }

    @Test
    @DisplayName("A client of the Security Server RPC CA that trusts the op-monitor CA stores and queries records "
            + "with no connection to the OpenBao address")
    void trustedClientStoresAndQueriesWithoutOpenBao() {
        var client = given("a client with a Security Server RPC CA certificate that trusts the op-monitor CA",
                () -> newClient(material.trustedClient(), material.opMonitorCa()));

        when("it stores 2 successful records", () -> client.storeOperationalData(List.of(
                storedRecord(0), storedRecord(1))));
        var intervals = when("it queries the traffic data of the stored service", () ->
                client.getOperationalDataIntervals(
                        STORE_WINDOW.minus(30, ChronoUnit.MINUTES).toEpochMilli(),
                        STORE_WINDOW.plus(30, ChronoUnit.MINUTES).toEpochMilli(),
                        60, null, null, new ServiceIdConverter().convertId(SERVICE_STORED)));
        then("the 2 stored records are returned", () ->
                assertThat(intervals.stream().mapToLong(OperationalDataInterval::getSuccessCount).sum()).isEqualTo(2));

        var logBeforeProbe = when("the OpenBao trap log is read", stack::openBaoTrapLog);
        then("the trap recorded no connection at startup, store or query", () ->
                assertThat(logBeforeProbe).doesNotContain(ACCEPTED_CONNECTION));

        when("op-monitor's container connects to the OpenBao address", stack::connectToOpenBaoAddress);
        then("the trap records that connection", () -> await().atMost(POLL_TIMEOUT).untilAsserted(() ->
                assertThat(stack.openBaoTrapLog()).contains(ACCEPTED_CONNECTION)));
    }

    @Test
    @DisplayName("A client certificate from another CA is rejected")
    void foreignClientIsRejected() {
        var client = given("a client with a certificate of another CA",
                () -> newClient(material.foreignClient(), material.opMonitorCa()));

        var failFastStub = OpMonitorServiceGrpc.newBlockingStub(client.getChannel())
                .withDeadlineAfter(FAIL_FAST_DEADLINE_SECONDS, TimeUnit.SECONDS);
        then("storing records fails as unavailable", () ->
                assertThatThrownBy(() -> failFastStub.storeOperationalData(StoreOperationalDataReq.getDefaultInstance()))
                        .isInstanceOfSatisfying(StatusRuntimeException.class,
                                e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE)));
    }

    @Test
    @DisplayName("Startup fails on a private key that does not match the certificate")
    void startupFailsOnMismatchedKey() {
        assertStartupFails(OpMonitorFileIdentityContainerSetup.MISMATCHED_KEY,
                "/identity/server.key: the private key does not match");
    }

    @Test
    @DisplayName("Startup fails on a missing certificate file")
    void startupFailsOnMissingCertificate() {
        assertStartupFails(OpMonitorFileIdentityContainerSetup.MISSING_CERTIFICATE,
                "/identity/server.crt: the file is missing or cannot be read");
    }

    @Test
    @DisplayName("Startup fails on a trusted-CA file with two certificates")
    void startupFailsOnTwoTrustedCas() {
        assertStartupFails(OpMonitorFileIdentityContainerSetup.TWO_TRUSTED_CAS,
                "/identity/trusted-ca.crt: expected exactly one CA certificate but found 2");
    }

    private static void assertStartupFails(String service, String expectedMessage) {
        var outcome = when("the op-monitor service with the broken identity files has stopped",
                () -> stack.awaitExit(service));
        then("it exited with a failure and named the file and the reason", () -> {
            assertThat(outcome.exitCode()).isNotZero();
            assertThat(outcome.log()).contains("Invalid file-based RPC identity: " + expectedMessage);
        });
    }

    @SneakyThrows
    private OpMonitorClient newClient(TestCa.TestLeaf identity, X509Certificate opMonitorCa) {
        var keyProvider = new TestVaultKeyProvider(identity, material.securityServerRpcCa());
        var rpcProperties = mock(RpcProperties.class);
        when(rpcProperties.useTls()).thenReturn(true);
        var vaultClient = mock(VaultClient.class);
        when(vaultClient.getOpMonitorCa()).thenReturn(Optional.of(opMonitorCa));
        var client = OpMonitorClient.create(new RpcChannelFactory(new TlsRpcCredentialsConfigurer(keyProvider)),
                config(), new OpMonitorChannelTrustResolver(rpcProperties, vaultClient));
        clients.add(client);
        return client;
    }

    private static XRoadConfig config() {
        var mapping = stack.opMonitorMapping();
        return XRoadConfigBuilder.create()
                .register(CommonRpcConfigKeys.instance())
                .overrides(Map.of(
                        "xroad.common-rpc.channel.op-monitor.host", mapping.host(),
                        "xroad.common-rpc.channel.op-monitor.port", String.valueOf(mapping.port()),
                        "xroad.common-rpc.channel.op-monitor.deadline-after", "10000"))
                .build();
    }

    private static OperationalDataRecordProto storedRecord(int index) {
        long requestInTs = STORE_WINDOW.plusSeconds(index).toEpochMilli();
        var data = new OpMonitoringData(OpMonitoringData.SecurityServerType.PRODUCER, requestInTs);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setClientId(new ClientIdConverter().convertId(STORE_CLIENT));
        data.setServiceId(new ServiceIdConverter().convertId(SERVICE_STORED));
        data.setResponseOutTs(requestInTs + 100, false);
        data.setSucceeded(true);
        return data.toProto();
    }
}
