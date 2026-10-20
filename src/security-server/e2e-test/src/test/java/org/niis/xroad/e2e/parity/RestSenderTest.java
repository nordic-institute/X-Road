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

package org.niis.xroad.e2e.parity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RestSenderTest {

    private static final String ANSWER = "HTTP/1.1 200 OK\r\nmock-response: true\r\nContent-Length: 0\r\n"
            + "Connection: close\r\n\r\n";

    private record Exchange(String requestHead, Reply reply) {

        String requestLine() {
            return requestHead.substring(0, requestHead.indexOf("\r\n"));
        }
    }

    private static Exchange exchange(String method, String path) throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var requestHead = CompletableFuture.supplyAsync(() -> answerOneRequest(server));
            var url = RestRequests.url("http://127.0.0.1:" + server.getLocalPort(), "DEV:COM:1234:TestService",
                    "svc", path);
            var reply = RestSender.send(method, url, "DEV/COM/4321/TestClient");
            return new Exchange(requestHead.get(), reply);
        }
    }

    private static String answerOneRequest(ServerSocket server) {
        try (var socket = server.accept()) {
            var received = new StringBuilder();
            var in = socket.getInputStream();
            while (!received.toString().endsWith("\r\n\r\n")) {
                received.append((char) in.read());
            }
            socket.getOutputStream().write(ANSWER.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return received.toString();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void sendsEncodedPathsAndDotSegmentsExactlyAsWritten() throws Exception {
        for (var path : new String[] {"/api/t%65st/foo", "/api/%2e%2e/secret", "/api/test%2Dbar", "/%2F/%25",
                "/api/test/foo/../bar", "/../x", "/a/./b", "/foo?x=1&y=%20"}) {
            assertThat(exchange("GET", path).requestLine())
                    .isEqualTo("GET /r1/DEV/COM/1234/TestService/svc" + path + " HTTP/1.1");
        }
    }

    @Test
    void sendsUnusualMethodsAndReturnsTheAnswer() throws Exception {
        for (var method : new String[] {"HEAD", "OPTIONS", "TRACE", "PATCH"}) {
            var exchange = exchange(method, "/a");

            assertThat(exchange.requestLine()).startsWith(method + " /r1/");
            assertThat(exchange.reply().status()).isEqualTo(200);
        }
    }

    @Test
    void sendsTheClientHeaderAndReturnsTheResponseHeaders() throws Exception {
        var exchange = exchange("GET", "/a");

        assertThat(exchange.requestHead().toLowerCase()).contains("x-road-client: dev/com/4321/testclient");
        assertThat(exchange.reply().header("Mock-Response")).isEqualTo("true");
    }
}
