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
package org.niis.xroad.common.vault.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.vault.FakeOpenBao;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.vault.config.VaultAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.VaultException;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.CLOSE;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.REDIRECT_TO_UNREACHABLE_LEADER;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.SLEEP;
import static org.niis.xroad.common.vault.FakeOpenBao.Failure.STATUS;
import static org.niis.xroad.common.vault.FakeOpenBao.KvVersion.V1;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.DELETE;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.LIST;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.READ;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.WRITE;

class SpringVaultClientHttpTest {

    private static final int CLIENT_TIMEOUT_MILLIS = 1000;
    private static final long SERVER_SLEEP_MILLIS = 2500;
    private static final long DELAYED_START_MILLIS = 1500;

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

        private final Function<SpringVaultClient, Object> call;
        private final Object expectedResult;

        Operation(Function<SpringVaultClient, Object> call, Object expectedResult) {
            this.call = call;
            this.expectedResult = expectedResult;
        }
    }

    static Stream<Arguments> recoveringScenarios() {
        return Stream.of(
                Arguments.of("500", STATUS, 500, READ),
                Arguments.of("307 to an unreachable leader", REDIRECT_TO_UNREACHABLE_LEADER, 307, LIST),
                Arguments.of("read timeout", SLEEP, 200, DELETE),
                Arguments.of("connection closed with no response", CLOSE, 0, WRITE));
    }

    @ParameterizedTest(name = "{0} on {3}")
    @MethodSource("recoveringScenarios")
    void shouldRecoverWhenFakeRecovers(String scenario, FakeOpenBao.Failure failure, int status, Operation operation)
            throws IOException {
        try (var server = FakeOpenBao.started(V1)) {
            server.failNext(1, failure, status, SERVER_SLEEP_MILLIS);

            runner(server.port()).run(context -> assertThat(operation.call.apply(context.getBean(SpringVaultClient.class)))
                    .isEqualTo(operation.expectedResult));

            assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void shouldStoreWriteWhenRedirectedToUnreachableLeaderFirst() throws IOException {
        try (var server = FakeOpenBao.started(V1)) {
            server.failNext(1, REDIRECT_TO_UNREACHABLE_LEADER, 307, 0);

            runner(server.port()).run(context -> WRITE.call.apply(context.getBean(SpringVaultClient.class)));

            assertThat(server.storedBodies()).singleElement().asString().contains("\"v\"");
        }
    }

    @Test
    void shouldRetryResetConnectionsUntilServerStartsServing() throws IOException {
        try (var server = FakeOpenBao.resettingConnections(V1)) {
            var delayedServe = CompletableFuture.runAsync(server::serve,
                    CompletableFuture.delayedExecutor(DELAYED_START_MILLIS, TimeUnit.MILLISECONDS));

            runner(server.port()).run(context -> assertThat(LIST.call.apply(context.getBean(SpringVaultClient.class)))
                    .isEqualTo(LIST.expectedResult));

            delayedServe.join();
            assertThat(server.connectionAttempts()).isGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void shouldFailAtOnceWithOriginalExceptionWhenWriteIsForbidden() throws IOException {
        try (var server = FakeOpenBao.started(V1)) {
            server.failNext(1, STATUS, 403, 0);

            runner(server.port()).run(context -> {
                var client = context.getBean(SpringVaultClient.class);

                assertThatThrownBy(() -> WRITE.call.apply(client))
                        .isExactlyInstanceOf(VaultException.class)
                        .hasCauseInstanceOf(HttpClientErrorException.Forbidden.class);
            });

            assertThat(server.secretHits()).hasSize(1);
            assertThat(server.storedBodies()).isEmpty();
        }
    }

    private static ApplicationContextRunner runner(int port) {
        return new ApplicationContextRunner()
                .withUserConfiguration(VaultAutoConfigurationImport.class, SpringVaultClientConfig.class)
                .withPropertyValues(
                        "spring.cloud.vault.enabled=true",
                        "spring.cloud.vault.host=127.0.0.1",
                        "spring.cloud.vault.port=" + port,
                        "spring.cloud.vault.scheme=http",
                        "spring.cloud.vault.authentication=TOKEN",
                        "spring.cloud.vault.token=test-token",
                        "spring.cloud.vault.connection-timeout=" + CLIENT_TIMEOUT_MILLIS,
                        "spring.cloud.vault.read-timeout=" + CLIENT_TIMEOUT_MILLIS,
                        "spring.cloud.vault.kv.backend=xrd-secret",
                        "spring.cloud.vault.kv.backend-version=1");
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(VaultAutoConfiguration.class)
    static class VaultAutoConfigurationImport {
    }
}
