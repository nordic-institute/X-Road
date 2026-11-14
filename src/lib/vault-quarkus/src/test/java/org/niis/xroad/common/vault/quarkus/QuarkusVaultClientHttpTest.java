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
import org.niis.xroad.common.core.node.NodeIdentity;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QuarkusVaultClientHttpTest {

    private static final Duration RETRY_BUDGET = Duration.ofSeconds(10);

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
    void shouldReadSecretWhenFirstResponseIsServerError() throws IOException {
        try (var server = new FakeOpenBao()) {
            server.failNext(1, 500);
            var client = client(server.port());

            var secret = client.getMLogArchivalSigningSecretKey();

            assertThat(secret).contains("v");
            assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    private static QuarkusVaultClient client(int port) {
        var properties = Map.of(
                "quarkus.vault.url", "http://127.0.0.1:" + port,
                "quarkus.vault.authentication.client-token", "test-token",
                "quarkus.vault.connect-timeout", "1S",
                "quarkus.vault.read-timeout", "1S",
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
