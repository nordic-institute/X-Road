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
package org.niis.xroad.common.vault;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Minimal OpenBao stand-in served over real HTTP. Every secret holds the payload {@code v} and every
 * listing the single key {@code k1}. It keeps the body of every write it accepts. A listening socket in
 * front of the HTTP server owns the port and decides per connection whether to reset or relay it.
 */
public final class FakeOpenBao implements AutoCloseable {

    private static final int HTTP_TEMPORARY_REDIRECT = 307;
    private static final String SECRET_PATH = "/v1/xrd-secret";
    private static final String LEADER_PATH = "/leader";
    private static final String DOWN_LEADER_PATH = "/leader-down";

    /**
     * The KV secrets engine version whose response shapes the fake serves.
     */
    public enum KvVersion {
        V1,
        V2
    }

    /**
     * What the fake does to a secret request instead of answering it.
     */
    public enum Failure {
        /** Answers with the given status. */
        STATUS,
        /** Answers 307 with a {@code Location} on a path the fake serves itself under {@code /leader/}. */
        REDIRECT_TO_LEADER,
        /** Answers 307 with a {@code Location} on a path under which the fake closes the connection. */
        REDIRECT_TO_UNREACHABLE_LEADER,
        /** Waits before answering. */
        SLEEP,
        /** Closes the connection without any response. */
        CLOSE
    }

    private final KvVersion kvVersion;
    private final HttpServer server;
    private final ServerSocket front;
    private final ExecutorService executor = Executors.newCachedThreadPool(daemonThreads());
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();
    private final List<String> hits = new CopyOnWriteArrayList<>();
    private final List<String> storedBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private final AtomicInteger connectionAttempts = new AtomicInteger();
    private volatile boolean serving;
    private volatile Failure failure = Failure.STATUS;
    private volatile int status;
    private volatile long sleepMillis;

    private FakeOpenBao(KvVersion kvVersion) throws IOException {
        this.kvVersion = kvVersion;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        front = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        executor.execute(this::acceptConnections);
    }

    /**
     * A fake that answers requests at once.
     */
    public static FakeOpenBao started(KvVersion kvVersion) throws IOException {
        var fake = new FakeOpenBao(kvVersion);
        fake.serve();
        return fake;
    }

    /**
     * A fake that owns its port from creation but resets every connection until {@link #serve()}, so a
     * client sees a server that is not up yet without another process taking the port.
     */
    public static FakeOpenBao resettingConnections(KvVersion kvVersion) throws IOException {
        return new FakeOpenBao(kvVersion);
    }

    /**
     * Starts answering requests; connections accepted from now on are served.
     */
    public void serve() {
        serving = true;
    }

    public int port() {
        return front.getLocalPort();
    }

    /**
     * Every connection the fake accepted, including those it reset.
     */
    public int connectionAttempts() {
        return connectionAttempts.get();
    }

    /**
     * Applies {@code newFailure} to the next {@code count} secret requests, then answers normally.
     */
    public void failNext(int count, Failure newFailure, int newStatus, long newSleepMillis) {
        failure = newFailure;
        status = newStatus;
        sleepMillis = newSleepMillis;
        failuresLeft.set(count);
    }

    public List<String> secretHits() {
        return hits.stream().filter(h -> h.contains(" " + SECRET_PATH)).toList();
    }

    public List<String> leaderHits() {
        return hits.stream().filter(h -> h.contains(" " + LEADER_PATH + "/")).toList();
    }

    public List<String> storedBodies() {
        return List.copyOf(storedBodies);
    }

    @Override
    public void close() {
        closeQuietly(front);
        server.stop(0);
        openSockets.forEach(FakeOpenBao::closeQuietly);
        executor.shutdownNow();
    }

    private void acceptConnections() {
        try {
            while (true) {
                var client = front.accept();
                connectionAttempts.incrementAndGet();
                if (serving) {
                    executor.execute(() -> relay(client));
                } else {
                    reset(client);
                }
            }
        } catch (IOException _) {
            // the listening socket was closed
        }
    }

    private static void reset(Socket client) {
        try {
            client.setSoLinger(true, 0);
        } catch (IOException _) {
            // closing without the linger option still ends the connection
        }
        closeQuietly(client);
    }

    private void relay(Socket client) {
        try {
            var backend = new Socket(InetAddress.getLoopbackAddress(), server.getAddress().getPort());
            openSockets.add(client);
            openSockets.add(backend);
            executor.execute(() -> pump(client, backend));
            executor.execute(() -> pump(backend, client));
        } catch (IOException _) {
            closeQuietly(client);
        }
    }

    private void pump(Socket from, Socket to) {
        try {
            from.getInputStream().transferTo(to.getOutputStream());
        } catch (IOException _) {
            // a closed peer ends the relay
        } finally {
            openSockets.remove(from);
            openSockets.remove(to);
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception _) {
            // already closed
        }
    }

    private static ThreadFactory daemonThreads() {
        return runnable -> {
            var thread = new Thread(runnable, "fake-openbao");
            thread.setDaemon(true);
            return thread;
        };
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            var method = ex.getRequestMethod();
            var uri = ex.getRequestURI();
            var body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            hits.add(method + " " + uri.getPath());
            var path = uri.getPath();
            if (path.startsWith(DOWN_LEADER_PATH + "/")) {
                throw new IOException("fake leader is unreachable");
            } else if (path.startsWith(LEADER_PATH + "/") || !path.startsWith(SECRET_PATH)) {
                send(ex, HTTP_OK, "{\"data\":{}}");
            } else if (failuresLeft.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                fail(ex, method, uri, body);
            } else {
                succeed(ex, method, uri, body);
            }
        }
    }

    private void fail(HttpExchange ex, String method, URI uri, String body) throws IOException {
        switch (failure) {
            case STATUS -> send(ex, status, "{\"errors\":[\"fake failure\"]}");
            case REDIRECT_TO_LEADER -> redirect(ex, LEADER_PATH + uri);
            case REDIRECT_TO_UNREACHABLE_LEADER -> redirect(ex, DOWN_LEADER_PATH + uri);
            case SLEEP -> {
                sleep();
                succeed(ex, method, uri, body);
            }
            case CLOSE -> throw new IOException("fake closes the connection without a response");
            default -> throw new IllegalStateException("Unexpected failure " + failure);
        }
    }

    private void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + port() + location);
        ex.sendResponseHeaders(HTTP_TEMPORARY_REDIRECT, -1);
    }

    private void sleep() {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private void succeed(HttpExchange ex, String method, URI uri, String body) throws IOException {
        var query = uri.getQuery() == null ? "" : uri.getQuery();
        if ("LIST".equals(method) || query.contains("list=true")) {
            send(ex, HTTP_OK, "{\"data\":{\"keys\":[\"k1\"]}}");
        } else if ("GET".equals(method)) {
            send(ex, HTTP_OK, readBody());
        } else if ("DELETE".equals(method)) {
            ex.sendResponseHeaders(HTTP_NO_CONTENT, -1);
        } else {
            storedBodies.add(body);
            if (kvVersion == KvVersion.V2) {
                send(ex, HTTP_OK, "{\"data\":{\"version\":2}}");
            } else {
                ex.sendResponseHeaders(HTTP_NO_CONTENT, -1);
            }
        }
    }

    private String readBody() {
        return kvVersion == KvVersion.V2
                ? "{\"data\":{\"data\":{\"payload\":\"v\"},\"metadata\":{\"version\":1}}}"
                : "{\"data\":{\"payload\":\"v\"}}";
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        ex.getResponseBody().write(bytes);
    }
}
