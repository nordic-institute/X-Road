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

import io.quarkus.vault.runtime.VaultConfigHolder;
import io.quarkus.vault.runtime.client.VaultClientProducer;
import io.quarkus.vault.runtime.config.VaultRuntimeConfig;
import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.vault.FakeOpenBao;
import org.niis.xroad.common.vault.ResilientVaultCall;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultFailures;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.STATUS;
import static org.niis.xroad.common.vault.FakeOpenBao.KvVersion.V2;

class XRoadEdcVaultHttpTest {

    private static final String MOUNT = "xrd-ds-secret";
    private static final Duration RETRY_BUDGET = Duration.ofSeconds(10);
    private static final int CLIENT_TIMEOUT_SECONDS = 1;

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
    void shouldRecoverReadAfterServerError() throws IOException {
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = vault(server.port());
            vault.storeSecret("signing-key", "secret-1");
            server.failNext(1, STATUS, 500, 0);

            var resolved = vault.resolveSecret("signing-key");

            assertThat(resolved).isEqualTo("secret-1");
            assertThat(server.secretHits()).containsExactly(
                    "POST /v1/xrd-ds-secret/data/ds/signing-key",
                    "GET /v1/xrd-ds-secret/data/ds/signing-key",
                    "GET /v1/xrd-ds-secret/data/ds/signing-key");
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
            assertThat(server.secretHits()).filteredOn(hit -> hit.startsWith("DELETE")).hasSize(2);
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

    private static XRoadEdcVault vault(int port) {
        var properties = Map.of(
                "quarkus.vault.url", "http://127.0.0.1:" + port,
                "quarkus.vault.authentication.client-token", "test-token",
                "quarkus.vault.connect-timeout", CLIENT_TIMEOUT_SECONDS + "S",
                "quarkus.vault.read-timeout", CLIENT_TIMEOUT_SECONDS + "S");
        var config = new SmallRyeConfigBuilder()
                .withMapping(VaultRuntimeConfig.class)
                .withSources(new PropertiesConfigSource(properties, "test", 500))
                .build()
                .getConfigMapping(VaultRuntimeConfig.class);
        var holder = new VaultConfigHolder().setVaultRuntimeConfig(config);
        var vaultClient = new VaultClientProducer().sharedVaultClient(vertx, holder, false);
        return new XRoadEdcVault(vaultClient,
                new ResilientVaultCall(QuarkusVaultFailures::isRetryable, RETRY_BUDGET));
    }
}
