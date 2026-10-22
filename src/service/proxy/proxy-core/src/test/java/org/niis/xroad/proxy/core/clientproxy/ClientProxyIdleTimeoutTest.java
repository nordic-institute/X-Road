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
package org.niis.xroad.proxy.core.clientproxy;

import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.RequestWrapper;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.proxy.core.test.util.ListInstanceWrapper;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * A client proxy request that blocks longer than the connector idle timeout before reading the request
 * body, as the REST path does while it acquires DSP asset access, succeeds through the production
 * idle-timeout handler in {@link ClientProxy}. {@code client-connector-max-idle-time} still bounds that
 * request when it is set to a positive value, and bounds continuous idle time rather than request age:
 * a request whose reads and writes keep it active for longer than the bound still succeeds, as long as
 * it is never continuously idle for longer than the bound.
 */
class ClientProxyIdleTimeoutTest {

    private static final int INITIAL_IDLE_TIME_MILLIS = 1000;
    private static final long HANDLER_BLOCK_MILLIS = 2500;
    private static final long BELOW_BOUND_HANDLER_BLOCK_MILLIS = 3500;

    private static final int FIRST_HALF_INITIAL_IDLE_TIME_MILLIS = 2500;
    private static final int FIRST_HALF_MAX_IDLE_TIME_MILLIS = 2900;
    private static final long FIRST_HALF_SLEEP_BEFORE_CHUNK_READ_MILLIS = 3000;
    private static final long FIRST_HALF_SLEEP_BEFORE_TERMINATOR_READ_MILLIS = 3000;
    private static final long FIRST_HALF_CHUNK_SEND_DELAY_MILLIS = 3500;
    private static final long FIRST_HALF_TERMINATOR_SEND_DELAY_MILLIS = 500;

    private ClientProxy clientProxy;

    @AfterEach
    void tearDown() throws Exception {
        if (clientProxy != null) {
            clientProxy.destroy();
        }
    }

    @Test
    void defaultMaxIdleTimeAllowsProcessingPastTheInitialIdleTimeout() throws Exception {
        var handler = new BlockThenReadBodyHandler(HANDLER_BLOCK_MILLIS);
        startClientProxy(handler, Map.of());

        var response = sendGet();

        assertThat(handler.readFailure.get()).isNull();
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void positiveMaxIdleTimeBelowTheBlockingDurationStillFailsTheRead() throws Exception {
        var handler = new BlockThenReadBodyHandler(BELOW_BOUND_HANDLER_BLOCK_MILLIS);
        startClientProxy(handler, Map.of(
                "xroad.proxy.client-proxy.client-connector-max-idle-time", "1500"));

        var response = sendGet();

        assertThat(handler.readFailure.get())
                .isInstanceOf(IOException.class)
                .hasRootCauseInstanceOf(TimeoutException.class);
        assertThat(response.statusCode()).isEqualTo(500);
    }

    @Test
    void positiveMaxIdleTimeAboveTheBlockingDurationSucceeds() throws Exception {
        var handler = new BlockThenReadBodyHandler(HANDLER_BLOCK_MILLIS);
        startClientProxy(handler, Map.of(
                "xroad.proxy.client-proxy.client-connector-max-idle-time", "5000"));

        var response = sendGet();

        assertThat(handler.readFailure.get()).isNull();
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void positiveMaxIdleTimeBoundsContinuousIdleTimeNotRequestAge() throws Exception {
        var handler = new ReadLoopHandler(10);
        startClientProxy(handler, Map.of(
                "xroad.proxy.client-proxy.client-connector-max-idle-time", "3000"));

        int statusCode = sendTricklingBodyThenBlock(10, 400, 1500);

        assertThat(handler.readFailure.get()).isNull();
        assertThat(statusCode).isEqualTo(200);
    }

    /**
     * The handler reads chunk 0, then sleeps past the first idle-timeout firing before asking for
     * chunk 1: nothing is pending at that firing, so the connector delivers it to the idle-timeout
     * listener instead of failing a pending read directly. The second chunk is a genuinely fresh arrival,
     * sent only after that read is already pending, and it lands inside the first half of the idle
     * interval that follows the first firing: the gap the firing after it measures is {@code
     * initialIdleTime} plus that early offset, clear of the jitter margin but short of a full extra
     * interval. The idle streak must still reset on it, rather than add that gap to the first firing's
     * interval. The handler then sleeps past that second firing too before reading the rest of the body.
     */
    @Test
    void positiveMaxIdleTimeResetsOnActivityInTheFirstHalfOfAnIdleInterval() throws Exception {
        var handler = new TwoReadsWithSleepsHandler(
                FIRST_HALF_SLEEP_BEFORE_CHUNK_READ_MILLIS, FIRST_HALF_SLEEP_BEFORE_TERMINATOR_READ_MILLIS);
        startClientProxy(handler, Map.of(
                "xroad.proxy.client-proxy.client-connector-initial-idle-time",
                String.valueOf(FIRST_HALF_INITIAL_IDLE_TIME_MILLIS),
                "xroad.proxy.client-proxy.client-connector-max-idle-time",
                String.valueOf(FIRST_HALF_MAX_IDLE_TIME_MILLIS)));

        int statusCode = sendTricklingBodyThenBlock(2, FIRST_HALF_CHUNK_SEND_DELAY_MILLIS, FIRST_HALF_TERMINATOR_SEND_DELAY_MILLIS);

        assertThat(handler.readFailure.get()).isNull();
        assertThat(statusCode).isEqualTo(200);
    }

    private static final class TwoReadsWithSleepsHandler extends HandlerBase {
        private final long sleepBeforeChunkReadMillis;
        private final long sleepBeforeTerminatorReadMillis;
        private final AtomicReference<Throwable> readFailure = new AtomicReference<>();

        TwoReadsWithSleepsHandler(long sleepBeforeChunkReadMillis, long sleepBeforeTerminatorReadMillis) {
            this.sleepBeforeChunkReadMillis = sleepBeforeChunkReadMillis;
            this.sleepBeforeTerminatorReadMillis = sleepBeforeTerminatorReadMillis;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            int status = 200;
            try (InputStream in = RequestWrapper.of(request).getInputStream()) {
                in.read(new byte[1]);
                Thread.sleep(sleepBeforeChunkReadMillis);
                in.read(new byte[1]);
                Thread.sleep(sleepBeforeTerminatorReadMillis);
                in.read(new byte[16]);
            } catch (IOException e) {
                readFailure.set(e);
                status = 500;
            }

            response.setStatus(status);
            response.write(true, ByteBuffer.wrap(String.valueOf(status).getBytes(StandardCharsets.UTF_8)), callback);
            return true;
        }
    }

    private int sendTricklingBodyThenBlock(int chunkCount, long chunkIntervalMillis, long finalIdleMillis) throws Exception {
        try (var socket = new Socket("127.0.0.1", clientProxy.getClientHttpPort())) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30000);
            var out = socket.getOutputStream();
            out.write(("PUT /r1/x HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Transfer-Encoding: chunked\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();

            for (int i = 0; i < chunkCount; i++) {
                if (i > 0) {
                    Thread.sleep(chunkIntervalMillis);
                }
                out.write("1\r\nx\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            Thread.sleep(finalIdleMillis);

            out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            try (var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                var statusLine = reader.readLine();
                return Integer.parseInt(statusLine.split(" ")[1]);
            }
        }
    }

    private static final class ReadLoopHandler extends HandlerBase {
        private final int chunkCount;
        private final AtomicReference<Throwable> readFailure = new AtomicReference<>();

        ReadLoopHandler(int chunkCount) {
            this.chunkCount = chunkCount;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            int status = 200;
            try (InputStream in = RequestWrapper.of(request).getInputStream()) {
                for (int i = 0; i < chunkCount; i++) {
                    in.read(new byte[1]);
                }
                Thread.sleep(1500);
                in.read(new byte[16]);
            } catch (IOException e) {
                readFailure.set(e);
                status = 500;
            }

            response.setStatus(status);
            response.write(true, ByteBuffer.wrap(String.valueOf(status).getBytes(StandardCharsets.UTF_8)), callback);
            return true;
        }
    }

    private void startClientProxy(HandlerBase handler, Map<String, String> additionalOverrides) throws Exception {
        var overrides = new HashMap<>(Map.of(
                "xroad.proxy.client-proxy.connector-host", "127.0.0.1",
                "xroad.proxy.client-proxy.client-http-port", "0",
                "xroad.proxy.client-proxy.client-https-port", "0",
                "xroad.proxy.client-proxy.jetty-configuration-file", "src/test/clientproxy.xml",
                "xroad.proxy.client-proxy.client-connector-initial-idle-time", String.valueOf(INITIAL_IDLE_TIME_MILLIS)));
        overrides.putAll(additionalOverrides);

        var config = XRoadConfigBuilder.create()
                .register(ProxyConfigKeys.instance())
                .overrides(overrides)
                .build();
        clientProxy = new ClientProxy(mock(ServerConfProvider.class), new ProxyProperties(config).clientProxy(),
                mock(ReloadingSSLSocketFactory.class), new ListInstanceWrapper<>(List.of(handler)));
        clientProxy.init();
    }

    private HttpResponse<String> sendGet() throws Exception {
        try (var client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + clientProxy.getClientHttpPort() + "/r1/x"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private static final class BlockThenReadBodyHandler extends HandlerBase {
        private final long blockMillis;
        private final AtomicReference<Throwable> readFailure = new AtomicReference<>();

        BlockThenReadBodyHandler(long blockMillis) {
            this.blockMillis = blockMillis;
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) throws Exception {
            Thread.sleep(blockMillis);

            int status = 200;
            try (InputStream in = RequestWrapper.of(request).getInputStream()) {
                in.read(new byte[4096]);
            } catch (IOException e) {
                readFailure.set(e);
                status = 500;
            }

            response.setStatus(status);
            response.write(true, ByteBuffer.wrap(String.valueOf(status).getBytes(StandardCharsets.UTF_8)), callback);
            return true;
        }
    }

}
