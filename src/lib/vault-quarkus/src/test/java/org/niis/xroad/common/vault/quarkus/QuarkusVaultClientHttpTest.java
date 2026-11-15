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
package org.niis.xroad.common.vault.quarkus;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.client.VaultClientException;
import io.quarkus.vault.runtime.VaultConfigHolder;
import io.quarkus.vault.runtime.VaultKvManager;
import io.quarkus.vault.runtime.client.VaultClientProducer;
import io.quarkus.vault.runtime.config.VaultRuntimeConfig;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.vault.FakeOpenBao;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.CLOSE;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.REDIRECT_TO_LEADER;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.SLEEP;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.STATUS;
import static org.niis.xroad.common.vault.FakeOpenBao.KvVersion.V1;
import static org.niis.xroad.common.vault.quarkus.QuarkusVaultClientHttpTest.Operation.DELETE;
import static org.niis.xroad.common.vault.quarkus.QuarkusVaultClientHttpTest.Operation.LIST;
import static org.niis.xroad.common.vault.quarkus.QuarkusVaultClientHttpTest.Operation.READ;
import static org.niis.xroad.common.vault.quarkus.QuarkusVaultClientHttpTest.Operation.WRITE;

class QuarkusVaultClientHttpTest {

    private static final Duration RETRY_BUDGET = Duration.ofSeconds(10);
    private static final int CLIENT_TIMEOUT_SECONDS = 1;
    private static final long SERVER_SLEEP_MILLIS = 2500;
    private static final long DELAYED_START_MILLIS = 1500;

    private static Vertx vertx;

    enum Operation {
        READ(client -> client.getMLogArchivalSigningSecretKey(), Optional.of("v")),
        WRITE(client -> {
            client.setMLogArchivalSigningSecretKey("v");
            return null;
        }, null),
        DELETE(client -> {
            client.deleteAgreementTokenSigningKey("k1");
            return null;
        }, null),
        LIST(client -> client.getAgreementTokenSigningKeys(), Map.of("k1", "v"));

        private final Function<QuarkusVaultClient, Object> call;
        private final Object expectedResult;

        Operation(Function<QuarkusVaultClient, Object> call, Object expectedResult) {
            this.call = call;
            this.expectedResult = expectedResult;
        }
    }

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() {
        vertx.close().toCompletionStage().toCompletableFuture().join();
    }

    static Stream<Arguments> recoveringScenarios() {
        return Stream.of(
                Arguments.of("500", STATUS, 500, WRITE),
                Arguments.of("307 to a leader path", REDIRECT_TO_LEADER, 307, READ),
                Arguments.of("read timeout", SLEEP, 200, DELETE),
                Arguments.of("connection closed with no response", CLOSE, 0, LIST));
    }

    @ParameterizedTest(name = "{0} on {3}")
    @MethodSource("recoveringScenarios")
    void shouldRecoverWhenFakeRecovers(String scenario, FakeOpenBao.Failure failure, int status, Operation operation)
            throws IOException {
        try (var server = FakeOpenBao.started(V1)) {
            server.failNext(1, failure, status, SERVER_SLEEP_MILLIS);
            var client = client(server.port());

            var result = operation.call.apply(client);

            assertThat(result).isEqualTo(operation.expectedResult);
            assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(server.leaderHits()).isEmpty();
        }
    }

    @Test
    void shouldRetryResetConnectionsUntilServerStartsServing() throws IOException {
        try (var server = FakeOpenBao.resettingConnections(V1)) {
            var client = client(server.port());
            var delayedServe = CompletableFuture.runAsync(server::serve,
                    CompletableFuture.delayedExecutor(DELAYED_START_MILLIS, TimeUnit.MILLISECONDS));

            var result = LIST.call.apply(client);

            delayedServe.join();
            assertThat(result).isEqualTo(LIST.expectedResult);
            assertThat(server.connectionAttempts()).isGreaterThanOrEqualTo(2);
            assertThat(server.leaderHits()).isEmpty();
        }
    }

    @Test
    void shouldReturnEmptyAtOnceWhenSecretIsMissing() throws IOException {
        try (var server = FakeOpenBao.started(V1)) {
            server.failNext(1, STATUS, 404, 0);
            var client = client(server.port());

            var result = READ.call.apply(client);

            assertThat(result).isEqualTo(Optional.empty());
            assertThat(server.secretHits()).hasSize(1);
        }
    }

    @Test
    void shouldFailAtOnceWithOriginalExceptionWhenWriteIsForbidden() throws IOException {
        try (var server = FakeOpenBao.started(V1)) {
            server.failNext(1, STATUS, 403, 0);
            var client = client(server.port());

            assertThatThrownBy(() -> WRITE.call.apply(client))
                    .isExactlyInstanceOf(VaultClientException.class)
                    .hasFieldOrPropertyWithValue("status", 403);
            assertThat(server.secretHits()).hasSize(1);
        }
    }

    private static QuarkusVaultClient client(int port) {
        var properties = Map.of(
                "quarkus.vault.url", "http://127.0.0.1:" + port,
                "quarkus.vault.authentication.client-token", "test-token",
                "quarkus.vault.connect-timeout", CLIENT_TIMEOUT_SECONDS + "S",
                "quarkus.vault.read-timeout", CLIENT_TIMEOUT_SECONDS + "S",
                "quarkus.vault.kv-secret-engine-version", "1",
                "quarkus.vault.kv-secret-engine-mount-path", "xrd-secret");
        var config = new SmallRyeConfigBuilder()
                .withMapping(VaultRuntimeConfig.class)
                .withSources(new PropertiesConfigSource(properties, "test", 500))
                .build()
                .getConfigMapping(VaultRuntimeConfig.class);
        var holder = new VaultConfigHolder().setVaultRuntimeConfig(config);
        var vaultClient = new VaultClientProducer().sharedVaultClient(vertx, holder, false);
        var engine = new VaultKVSecretEngine(new VaultKvManager(vaultClient, holder));
        return new QuarkusVaultClient(engine, NodeIdentity.resolve(null), RETRY_BUDGET);
    }
}
