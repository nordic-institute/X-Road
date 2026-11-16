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

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.CommonConfigKeys;
import org.niis.xroad.common.vault.FakeOpenBao;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.STATUS;
import static org.niis.xroad.common.vault.FakeOpenBao.KvVersion.V2;

class XRoadEdcVaultExtensionTest {

    private static final String MOUNT = "xrd-ds-secret";
    private static final Duration MAX_CALL_DURATION = Duration.ofSeconds(10);

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
    void shouldRetryServerErrorsAndGiveUpAsRetryableFailureWithinConfiguredBudget() throws IOException {
        var config = XRoadConfigBuilder.create()
                .overrides(Map.of(CommonConfigKeys.VAULT_RETRY_BUDGET.key(), "1s"))
                .build();
        try (var server = FakeOpenBao.started(V2, MOUNT)) {
            var vault = new XRoadEdcVaultExtension(TestVaultClients.connectedTo(vertx, server.port()), config).vault();
            server.failNext(Integer.MAX_VALUE, STATUS, 500, 0);
            var start = System.nanoTime();

            var result = vault.storeSecret("signing-key", "secret-1");

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(MAX_CALL_DURATION);
            assertThat(result.failed()).isTrue();
            assertThat(result.isRetryable()).isTrue();
            assertThat(server.secretHits().size()).isGreaterThan(1);
        }
    }
}
