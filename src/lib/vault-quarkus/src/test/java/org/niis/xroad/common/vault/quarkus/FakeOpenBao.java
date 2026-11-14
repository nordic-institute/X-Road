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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal OpenBao KV v2 stand-in served over real HTTP.
 */
final class FakeOpenBao implements AutoCloseable {

    private static final String LEADER_PATH = "/leader";
    private static final String SECRET_PATH = "/v1/xrd-secret";

    /**
     * What the fake does to a secret request instead of answering it.
     */
    enum Failure {
        /** Answers with the given status. */
        STATUS,
        /** Answers 307 with a {@code Location} on a port nothing listens on. */
        REDIRECT_TO_DEAD_LEADER,
        /** Waits before answering. */
        SLEEP,
        /** Closes the connection without any response. */
        CLOSE
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<String> hits = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final int deadLeaderPort = unusedPort();
    private volatile Failure failure = Failure.STATUS;
    private volatile int status;
    private volatile long sleepMillis;

    FakeOpenBao() throws IOException {
        this(0);
    }

    FakeOpenBao(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    static int unusedPort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    int port() {
        return server.getAddress().getPort();
    }

    /**
     * Applies {@code newFailure} to the next {@code count} secret requests, then answers normally.
     */
    void failNext(int count, Failure newFailure, int newStatus, long newSleepMillis) {
        failure = newFailure;
        status = newStatus;
        sleepMillis = newSleepMillis;
        failuresLeft.set(count);
    }

    List<String> secretHits() {
        return hits.stream().filter(h -> h.contains(SECRET_PATH)).toList();
    }

    List<String> leaderHits() {
        return hits.stream().filter(h -> h.contains(" " + LEADER_PATH + "/")).toList();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            var method = ex.getRequestMethod();
            var uri = ex.getRequestURI();
            ex.getRequestBody().readAllBytes();
            hits.add(method + " " + uri.getPath());
            var path = uri.getPath();
            if (path.startsWith(LEADER_PATH + "/") || !path.startsWith(SECRET_PATH)) {
                send(ex, 200, "{\"data\":{}}");
            } else if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                fail(ex, method, uri);
            } else {
                succeed(ex, method, uri);
            }
        }
    }

    private void fail(HttpExchange ex, String method, URI uri) throws IOException {
        switch (failure) {
            case STATUS -> send(ex, status, "{\"errors\":[\"fake failure\"]}");
            case REDIRECT_TO_DEAD_LEADER -> {
                ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + deadLeaderPort + uri);
                ex.sendResponseHeaders(307, -1);
            }
            case SLEEP -> {
                sleep();
                succeed(ex, method, uri);
            }
            case CLOSE -> throw new IOException("fake closes the connection without a response");
            default -> throw new IllegalStateException("Unexpected failure " + failure);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void succeed(HttpExchange ex, String method, URI uri) throws IOException {
        var query = uri.getQuery() == null ? "" : uri.getQuery();
        if ("LIST".equals(method) || query.contains("list=true")) {
            send(ex, 200, "{\"data\":{\"keys\":[\"k1\"]}}");
        } else if ("GET".equals(method)) {
            send(ex, 200, "{\"data\":{\"data\":{\"payload\":\"v\"},\"metadata\":{\"version\":1}}}");
        } else if ("DELETE".equals(method)) {
            ex.sendResponseHeaders(204, -1);
        } else {
            send(ex, 200, "{\"data\":{\"version\":2}}");
        }
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
