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
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.vault.config.VaultAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.VaultException;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.common.vault.spring.FakeOpenBao.Failure.CLOSE;
import static org.niis.xroad.common.vault.spring.FakeOpenBao.Failure.REDIRECT_TO_DEAD_LEADER;
import static org.niis.xroad.common.vault.spring.FakeOpenBao.Failure.SLEEP;
import static org.niis.xroad.common.vault.spring.FakeOpenBao.Failure.STATUS;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.DELETE;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.LIST;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.READ;
import static org.niis.xroad.common.vault.spring.SpringVaultClientHttpTest.Operation.WRITE;

class SpringVaultClientHttpTest {

    private static final int CLIENT_TIMEOUT_MILLIS = 1000;
    private static final long SERVER_SLEEP_MILLIS = 2500;
    private static final long DELAYED_START_MILLIS = 1500;

    enum Operation {
        READ(client -> client.getMLogArchivalSigningSecretKey()),
        WRITE(client -> client.setMLogArchivalSigningSecretKey("v")),
        DELETE(client -> client.deleteAgreementTokenSigningKey("k1")),
        LIST(client -> client.getAgreementTokenSigningKeys());

        private final Consumer<SpringVaultClient> call;

        Operation(Consumer<SpringVaultClient> call) {
            this.call = call;
        }
    }

    static Stream<Arguments> recoveringScenarios() {
        return Stream.of(
                Arguments.of("500", STATUS, 500, READ),
                Arguments.of("307 to a dead leader", REDIRECT_TO_DEAD_LEADER, 307, LIST),
                Arguments.of("read timeout", SLEEP, 200, DELETE),
                Arguments.of("connection closed with no response", CLOSE, 0, WRITE));
    }

    @ParameterizedTest(name = "{0} on {3}")
    @MethodSource("recoveringScenarios")
    void shouldRecoverWhenFakeRecovers(String scenario, FakeOpenBao.Failure failure, int status, Operation operation)
            throws IOException {
        try (var server = new FakeOpenBao()) {
            server.failNext(1, failure, status, SERVER_SLEEP_MILLIS);

            runner(server.port()).run(context -> operation.call.accept(context.getBean(SpringVaultClient.class)));

            assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    @Test
    void shouldStoreWriteWhenRedirectedToDeadLeaderFirst() throws IOException {
        try (var server = new FakeOpenBao()) {
            server.failNext(1, REDIRECT_TO_DEAD_LEADER, 307, 0);

            runner(server.port()).run(context -> WRITE.call.accept(context.getBean(SpringVaultClient.class)));

            assertThat(server.storedBodies()).singleElement().asString().contains("\"v\"");
        }
    }

    @Test
    void shouldRecoverWhenConnectionIsRefusedUntilFakeStarts() throws IOException {
        var port = FakeOpenBao.unusedPort();
        var delayedServer = CompletableFuture.supplyAsync(() -> startAt(port),
                CompletableFuture.delayedExecutor(DELAYED_START_MILLIS, TimeUnit.MILLISECONDS));
        try {
            runner(port).run(context -> LIST.call.accept(context.getBean(SpringVaultClient.class)));
        } finally {
            try (var server = delayedServer.join()) {
                assertThat(server.secretHits()).hasSizeGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    void shouldFailAtOnceWithOriginalExceptionWhenWriteIsForbidden() throws IOException {
        try (var server = new FakeOpenBao()) {
            server.failNext(1, STATUS, 403, 0);

            runner(server.port()).run(context -> {
                var client = context.getBean(SpringVaultClient.class);

                assertThatThrownBy(() -> WRITE.call.accept(client))
                        .isExactlyInstanceOf(VaultException.class)
                        .hasCauseInstanceOf(HttpClientErrorException.Forbidden.class);
            });

            assertThat(server.secretHits()).hasSize(1);
            assertThat(server.storedBodies()).isEmpty();
        }
    }

    private static FakeOpenBao startAt(int port) {
        try {
            return new FakeOpenBao(port);
        } catch (IOException e) {
            throw new IllegalStateException(e);
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
