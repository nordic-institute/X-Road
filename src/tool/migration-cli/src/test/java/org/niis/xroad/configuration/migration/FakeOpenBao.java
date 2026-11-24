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

package org.niis.xroad.configuration.migration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory stand-in for the OpenBao KV store that migration-cli talks to.
 */
final class FakeOpenBao implements AutoCloseable {

    private static final String KV_PREFIX = "/v1/xrd-secret/";
    private static final int NO_CONTENT = 204;
    private static final int NOT_FOUND = 404;

    private static final int SERVER_ERROR = 500;

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final Set<String> rejectedWrites = ConcurrentHashMap.newKeySet();
    private final HttpServer server;

    FakeOpenBao() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/auth/token/lookup-self", exchange -> reply(exchange, 200, "{\"data\":{}}"));
        server.createContext(KV_PREFIX, this::handleKv);
        server.start();
    }

    Map<String, String> store() {
        return store;
    }

    void rejectWritesTo(String path) {
        rejectedWrites.add(path);
    }

    void pointEnvironmentAt(EnvironmentVariables envVars) {
        envVars.set(MigrationVaultClient.ENV_HOST, "127.0.0.1");
        envVars.set(MigrationVaultClient.ENV_PORT, String.valueOf(server.getAddress().getPort()));
        envVars.set(MigrationVaultClient.ENV_SCHEME, "http");
        envVars.set(MigrationVaultClient.ENV_TOKEN, "test-token");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleKv(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath().substring(KV_PREFIX.length());
        switch (exchange.getRequestMethod()) {
            case "PUT", "POST" -> {
                if (rejectedWrites.contains(path)) {
                    reply(exchange, SERVER_ERROR, "");
                    return;
                }
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
