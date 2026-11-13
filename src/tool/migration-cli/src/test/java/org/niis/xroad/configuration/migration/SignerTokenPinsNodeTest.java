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
package org.niis.xroad.configuration.migration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(SystemStubsExtension.class)
class SignerTokenPinsNodeTest {

    private static final String KV_PREFIX = "/v1/xrd-secret/";
    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;

    @SystemStub
    private final EnvironmentVariables envVars = new EnvironmentVariables();

    @TempDir
    Path tempDir;

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private HttpServer server;

    @BeforeEach
    void startFakeOpenBao() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/auth/token/lookup-self", exchange -> reply(exchange, 200, "{\"data\":{}}"));
        server.createContext(KV_PREFIX, this::handleKv);
        server.start();

        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_PORT, String.valueOf(server.getAddress().getPort()));
        envVars.set(MigrationVaultClient.ENV_SCHEME, "http");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");
    }

    @AfterEach
    void stopFakeOpenBao() {
        server.stop(0);
    }

    @Test
    void shouldStorePinsUnderTheNodeGivenAndSkipExistingPinsOnRerun() throws IOException {
        String[] args = {"signer-token-pins", "--node", "node_b", fetchPinScript().toString()};

        LegacyConfigMigrationCLI.main(args);
        Map<String, String> afterFirstRun = Map.copyOf(store);
        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args)).isInstanceOf(MigrationException.class);

        assertThat(afterFirstRun).containsOnlyKeys("signer/token-pins/node_b/0");
        assertThat(store).isEqualTo(afterFirstRun);
    }

    @Test
    void shouldAcceptCentralServerInstallationOptionNextToTheNodeOption() throws IOException {
        String[] args = {"signer-token-pins", "--node", "node_b", "--installation=cs", fetchPinScript().toString()};

        LegacyConfigMigrationCLI.main(args);

        assertThat(store).containsOnlyKeys("signer/token-pins/node_b/0");
    }

    @Test
    void shouldStoreNothingWhenTheNodeNameIsInvalid() throws IOException {
        String[] args = {"signer-token-pins", "--node", "_bad", fetchPinScript().toString()};

        assertThatThrownBy(() -> LegacyConfigMigrationCLI.main(args))
                .isInstanceOf(MigrationException.class)
                .cause()
                .isInstanceOf(XrdRuntimeException.class)
                .hasMessageContaining("^[A-Za-z0-9][A-Za-z0-9_-]{0,62}$");
        assertThat(store).isEmpty();
    }

    private Path fetchPinScript() throws IOException {
        Path script = tempDir.resolve("fetch-pin.sh");
        Files.writeString(script, "#!/bin/sh\necho 0:1111\n");
        assertThat(script.toFile().setExecutable(true)).isTrue();
        return script;
    }

    private void handleKv(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring(KV_PREFIX.length());
        switch (exchange.getRequestMethod()) {
            case "PUT", "POST" -> {
                store.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                reply(exchange, NO_CONTENT, "");
            }
            case "GET" -> {
                String stored = store.get(path);
                if (stored == null) {
                    reply(exchange, NOT_FOUND, "");
                } else {
                    reply(exchange, 200, "{\"data\":" + stored + "}");
                }
            }
            default -> reply(exchange, NO_CONTENT, "");
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == NO_CONTENT ? -1 : bytes.length);
        if (status != NO_CONTENT) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
