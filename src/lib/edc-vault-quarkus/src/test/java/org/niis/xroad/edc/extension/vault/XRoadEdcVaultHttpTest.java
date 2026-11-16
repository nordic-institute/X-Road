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
package org.niis.xroad.edc.extension.vault;

import io.quarkus.vault.client.VaultClientException;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.FakeOpenBao;
import org.niis.xroad.common.vault.ResilientVaultCall;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultFailures;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.CLOSE;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.REDIRECT_TO_UNREACHABLE_LEADER;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.SLEEP;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.STATUS;
import static org.niis.xroad.common.vault.FakeOpenBao.KvVersion.V2;

class XRoadEdcVaultHttpTest {

    private static final String MOUNT = "xrd-ds-secret";
    private static final Duration RETRY_BUDGET = Duration.ofSeconds(10);
    private static final Duration EXHAUSTED_BUDGET = Duration.ofSeconds(1);
    private static final long SERVER_SLEEP_MILLIS = 2500;
    private static final long DELAYED_START_MILLIS = 1500;
    private static final String SECRET_KEY = "signing-key";

    enum Operation {
        RESOLVE,
        STORE,
        DELETE
    }

    enum Shape {
        SERVER_ERROR(server -> server.failNext(1, STATUS, 500, 0)),
        DEAD_LEADER_REDIRECT(server -> server.failNext(1, REDIRECT_TO_UNREACHABLE_LEADER, 307, 0)),
        READ_TIMEOUT(server -> server.failNext(1, SLEEP, 200, SERVER_SLEEP_MILLIS)),
        CONNECTION_CLOSED(server -> server.failNext(1, CLOSE, 0, 0));
        private final Consumer<FakeOpenBao> arm;

        Shape(Consumer<FakeOpenBao> arm) {
            this.arm = arm;
        }
    }

    private static Vertx vertx;

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() {
        vertx.close().toCompletionStage().toCompletableFuture().join();
    }

    @Test
    void shouldStoreAndResolveSecretUnderFolderInContentField() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());

            var stored = vault.storeSecret("signing-key", "secret-1");

            assertThat(stored.succeeded()).isTrue();
            assertThat(vault.resolveSecret("signing-key")).isEqualTo("secret-1");
            assertThat(server.secretHits()).containsExactly(
                    "POST /v1/xrd-ds-secret/data/ds/signing-key",
                    "GET /v1/xrd-ds-secret/data/ds/signing-key");
            assertThat(server.storedBodies()).singleElement().asString().contains("\"content\":\"secret-1\"");
        }
    }

    @Test
    void shouldResolveMissingSecretToNull() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());

            assertThat(vault.resolveSecret("absent")).isNull();
            assertThat(server.secretHits()).containsExactly("GET /v1/xrd-ds-secret/data/ds/absent");
        }
    }

    @Test
    void shouldKeepOneCurrentValueWhenStoringSameKeyTwice() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());

            vault.storeSecret("signing-key", "secret-1");
            vault.storeSecret("signing-key", "secret-2");

            assertThat(vault.resolveSecret("signing-key")).isEqualTo("secret-2");
        }
    }

    @Test
    void shouldDeleteThroughMetadataPath() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            vault.storeSecret("signing-key", "secret-1");

            var deleted = vault.deleteSecret("signing-key");

            assertThat(deleted.succeeded()).isTrue();
            assertThat(server.secretHits()).contains("DELETE /v1/xrd-ds-secret/metadata/ds/signing-key");
            assertThat(vault.resolveSecret("signing-key")).isNull();
        }
    }

    @Test
    void shouldTreatDeleteOfMissingSecretAsSuccess() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            vault.storeSecret("signing-key", "secret-1");
            vault.deleteSecret("signing-key");
            server.failNext(1, STATUS, 404, 0);

            var deletedAgain = vault.deleteSecret("signing-key");

            assertThat(deletedAgain.succeeded()).isTrue();
            assertThat(server.secretHits()).filteredOn(hit -> hit.startsWith("DELETE"))
                    .containsExactly("DELETE /v1/xrd-ds-secret/metadata/ds/signing-key",
                            "DELETE /v1/xrd-ds-secret/metadata/ds/signing-key");
        }
    }

    @Test
    void shouldKeepKeysWithSpecialCharactersApart() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());

            vault.storeSecret("did/key#1", "secret-1");
            vault.storeSecret("did/key", "secret-2");

            assertThat(vault.resolveSecret("did/key#1")).isEqualTo("secret-1");
            assertThat(vault.resolveSecret("did/key")).isEqualTo("secret-2");
            assertThat(server.secretHits()).contains("POST /v1/xrd-ds-secret/data/ds/did/key#1");
        }
    }

    static Stream<Arguments> recoveringShapes() {
        return Stream.of(Shape.values()).flatMap(shape ->
                Stream.of(Operation.values()).map(operation -> Arguments.of(shape, operation)));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("recoveringShapes")
    void shouldSucceedWithinBudgetWhenFailureShapePasses(Shape shape, Operation operation) throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            vault.storeSecret(SECRET_KEY, "secret-1");
            shape.arm.accept(server);
            switch (operation) {
                case RESOLVE -> assertThat(vault.resolveSecret(SECRET_KEY)).isEqualTo("secret-1");
                case STORE -> {
                    assertThat(vault.storeSecret(SECRET_KEY, "secret-2").succeeded()).isTrue();
                    assertThat(vault.resolveSecret(SECRET_KEY)).isEqualTo("secret-2");
                }
                case DELETE -> {
                    assertThat(vault.deleteSecret(SECRET_KEY).succeeded()).isTrue();
                    assertThat(vault.resolveSecret(SECRET_KEY)).isNull();
                }
                default -> throw new IllegalStateException("Unexpected operation " + operation);
            }
            assertThat(server.leaderHits()).isEmpty();
        }
    }

    @ParameterizedTest(name = "refused connection on {0}")
    @EnumSource(Operation.class)
    void shouldSucceedWithinBudgetWhenConnectionsAreRefusedUntilServerServes(Operation operation) throws IOException {
        try (var server = FakeOpenBao.resettingConnections(V2, MOUNT)) {
            var vault = vault(server.port());
            var delayedServe = CompletableFuture.runAsync(server::serve,
                    CompletableFuture.delayedExecutor(DELAYED_START_MILLIS, TimeUnit.MILLISECONDS));
            switch (operation) {
                case RESOLVE -> assertThat(vault.resolveSecret(SECRET_KEY)).isNull();
                case STORE -> assertThat(vault.storeSecret(SECRET_KEY, "secret-1").succeeded()).isTrue();
                case DELETE -> assertThat(vault.deleteSecret(SECRET_KEY).succeeded()).isTrue();
                default -> throw new IllegalStateException("Unexpected operation " + operation);
            }
            delayedServe.join();
            assertThat(server.connectionAttempts()).isGreaterThanOrEqualTo(2);
        }
    }

    @ParameterizedTest(name = "403 on {0}")
    @EnumSource(Operation.class)
    void shouldNotRetryForbiddenRequest(Operation operation) throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            server.failNext(1, STATUS, 403, 0);
            switch (operation) {
                case RESOLVE -> assertThat(vault.resolveSecret(SECRET_KEY)).isNull();
                case STORE -> assertThat(vault.storeSecret(SECRET_KEY, "secret-1"))
                        .satisfies(result -> {
                            assertThat(result.failed()).isTrue();
                            assertThat(result.isRetryable()).isFalse();
                        });
                case DELETE -> assertThat(vault.deleteSecret(SECRET_KEY))
                        .satisfies(result -> {
                            assertThat(result.failed()).isTrue();
                            assertThat(result.isRetryable()).isFalse();
                        });
                default -> throw new IllegalStateException("Unexpected operation " + operation);
            }
            assertThat(server.secretHits()).hasSize(1);
        }
    }

    @ParameterizedTest(name = "non-retryable non-refusal failure on {0}")
    @EnumSource(value = Operation.class, names = {"STORE", "DELETE"})
    void shouldReturnFailureWhenFailureIsNeitherRefusalNorRetryable(Operation operation) throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port(), RETRY_BUDGET, failure -> false);
            server.failNext(1, CLOSE, 0, 0);

            var result = operation == Operation.STORE
                    ? vault.storeSecret(SECRET_KEY, "secret-1")
                    : vault.deleteSecret(SECRET_KEY);

            assertThat(result.failed()).isTrue();
            assertThat(result.isRetryable()).isFalse();
            assertThat(result.getFailureDetail()).contains(SECRET_KEY);
        }
    }

    @Test
    void shouldFailResolveAsUnavailableWithLastFailureWhenBudgetIsExhausted() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port(), EXHAUSTED_BUDGET);
            server.failNext(Integer.MAX_VALUE, STATUS, 500, 0);
            assertThatThrownBy(() -> vault.resolveSecret(SECRET_KEY))
                    .isInstanceOfSatisfying(XrdRuntimeException.class,
                            e -> assertThat(e.isCausedBy(ErrorCode.SECRET_STORE_UNAVAILABLE)).isTrue())
                    .hasCauseInstanceOf(VaultClientException.class);
        }
    }

    @ParameterizedTest(name = "exhausted budget on {0}")
    @EnumSource(value = Operation.class, names = {"STORE", "DELETE"})
    void shouldReturnRetryableFailureWhenBudgetIsExhausted(Operation operation) throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port(), EXHAUSTED_BUDGET);
            server.failNext(Integer.MAX_VALUE, STATUS, 500, 0);
            var result = operation == Operation.STORE
                    ? vault.storeSecret(SECRET_KEY, "secret-1")
                    : vault.deleteSecret(SECRET_KEY);
            assertThat(result.failed()).isTrue();
            assertThat(result.isRetryable()).isTrue();
        }
    }

    @Test
    void shouldSendKeyOnTheWireAsStockEdcDoes() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            vault.storeSecret("DEV:COM:222-key", "secret-1");
            vault.resolveSecret("DEV:COM:222-key");
            vault.deleteSecret("DEV:COM:222-key");
            assertThat(server.rawSecretHits()).containsExactly(
                    "POST /v1/xrd-ds-secret/data/ds/DEV%3ACOM%3A222-key",
                    "GET /v1/xrd-ds-secret/data/ds/DEV%3ACOM%3A222-key",
                    "DELETE /v1/xrd-ds-secret/metadata/ds/DEV%3ACOM%3A222-key");
        }
    }

    @Test
    void shouldKeepFragmentSuffixOfKeyOnTheWire() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            vault.storeSecret("did:web:example#key-1", "secret-1");
            assertThat(vault.resolveSecret("did:web:example#key-1")).isEqualTo("secret-1");
            assertThat(server.rawSecretHits()).contains("POST /v1/xrd-ds-secret/data/ds/did%3Aweb%3Aexample%23key-1");
            assertThat(server.secretHits()).contains("POST /v1/xrd-ds-secret/data/ds/did:web:example#key-1");
        }
    }

    private static XRoadEdcVault vault(int port) {
        return vault(port, RETRY_BUDGET);
    }

    private static XRoadEdcVault vault(int port, Duration retryBudget) {
        return vault(port, retryBudget, QuarkusVaultFailures::isRetryable);
    }

    private static XRoadEdcVault vault(int port, Duration retryBudget, Predicate<Throwable> retryable) {
        return new XRoadEdcVault(TestVaultClients.connectedTo(vertx, port),
                new ResilientVaultCall(retryable, retryBudget));
    }
}
