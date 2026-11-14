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

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.vault.quarkus.FakeOpenBao.Failure.CLOSE;
import static org.niis.xroad.common.vault.quarkus.FakeOpenBao.Failure.REDIRECT_TO_DEAD_LEADER;
import static org.niis.xroad.common.vault.quarkus.FakeOpenBao.Failure.SLEEP;
import static org.niis.xroad.common.vault.quarkus.FakeOpenBao.Failure.STATUS;
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
        READ(client -> client.getMLogArchivalSigningSecretKey()),
        WRITE(client -> client.setMLogArchivalSigningSecretKey("v")),
        DELETE(client -> client.deleteAgreementTokenSigningKey("k1")),
        LIST(client -> client.getAgreementTokenSigningKeys());

        private final Consumer<QuarkusVaultClient> call;

        Operation(Consumer<QuarkusVaultClient> call) {
            this.call = call;
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
                Arguments.of("500", STATUS, 500, READ),
                Arguments.of("307 to a dead leader", REDIRECT_TO_DEAD_LEADER, 307, WRITE),
                Arguments.of("read timeout", SLEEP, 200, DELETE),
                Arguments.of("connection closed with no response", CLOSE, 0, LIST));
    }

    @ParameterizedTest(name = "{0} on {3}")
    @MethodSource("recoveringScenarios")
    void shouldRecoverWhenFakeRecovers(String scenario, FakeOpenBao.Failure failure, int status, Operation operation)
            throws IOException {
        try (var server = new FakeOpenBao()) {
            server.failNext(1, failure, status, SERVER_SLEEP_MILLIS);
            var client = client(server.port());

            operation.call.accept(client);

            assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(server.leaderHits()).isEmpty();
        }
    }

    @Test
    void shouldRecoverWhenConnectionIsRefusedUntilFakeStarts() throws IOException {
        var port = FakeOpenBao.unusedPort();
        var client = client(port);
        var delayedServer = CompletableFuture.supplyAsync(() -> startAt(port),
                CompletableFuture.delayedExecutor(DELAYED_START_MILLIS, TimeUnit.MILLISECONDS));
        try {
            LIST.call.accept(client);
        } finally {
            try (var server = delayedServer.join()) {
                assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(2);
                assertThat(server.leaderHits()).isEmpty();
            }
        }
    }

    @Test
    void shouldFailAtOnceWithOriginalExceptionWhenWriteIsForbidden() throws IOException {
        try (var server = new FakeOpenBao()) {
            server.failNext(1, STATUS, 403, 0);
            var client = client(server.port());

            assertThatThrownBy(() -> WRITE.call.accept(client))
                    .isExactlyInstanceOf(VaultClientException.class)
                    .hasFieldOrPropertyWithValue("status", 403);
            assertThat(server.secretHits()).hasSize(1);
        }
    }

    private static FakeOpenBao startAt(int port) {
        try {
            return new FakeOpenBao(port);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static QuarkusVaultClient client(int port) {
        var properties = Map.of(
                "quarkus.vault.url", "http://127.0.0.1:" + port,
                "quarkus.vault.authentication.client-token", "test-token",
                "quarkus.vault.connect-timeout", CLIENT_TIMEOUT_SECONDS + "S",
                "quarkus.vault.read-timeout", CLIENT_TIMEOUT_SECONDS + "S",
                "quarkus.vault.kv-secret-engine-version", "2",
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
