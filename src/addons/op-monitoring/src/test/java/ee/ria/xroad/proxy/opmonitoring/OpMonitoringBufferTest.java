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
package ee.ria.xroad.proxy.opmonitoring;

import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests operational monitoring buffer.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class OpMonitoringBufferTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CloseableHttpClient httpClient;

    private class TestOpMonitoringBuffer extends OpMonitoringBuffer {
        TestOpMonitoringBuffer() throws Exception {
            super();
        }

        @Override
        OpMonitoringDaemonSender createSender() throws Exception {
            return new OpMonitoringDaemonSender(this) {
                @Override
                CloseableHttpClient createHttpClient() {
                    return httpClient;
                }
            };
        }

        @Override
        OpMonitoringDataProcessor createDataProcessor() {
            return new TestOpMonitoringDataProcessor();
        }
    }

    private static class TestOpMonitoringDataProcessor extends OpMonitoringDataProcessor {
        @Override
        String getIpAddress() {
            return "127.0.0.1";
        }
    }

    @AfterEach
    void cleanUp() {
        System.clearProperty("xroad.op-monitor-buffer.size");
    }

    @Test
    void bufferSaturatesUnderLoad() throws Exception {
        final ExecutorService executorService = Executors.newFixedThreadPool(80);

        when(httpClient.execute(any(HttpRequestBase.class), any(HttpContext.class))).thenAnswer(invocation -> {
            doSleep(20, 80);

            CloseableHttpResponse response = mock(CloseableHttpResponse.class, RETURNS_DEEP_STUBS);
            when(response.getStatusLine().getStatusCode()).thenReturn(200);
            when(response.getAllHeaders()).thenReturn(new Header[0]);

            when(response.getEntity().getContent())
                    .thenReturn(IOUtils.toInputStream(objectMapper.writeValueAsString(new StoreOpMonitoringDataResponse()), UTF_8));
            return response;
        });
        System.setProperty("xroad.op-monitor-buffer.size", "10000");

        final TestOpMonitoringBuffer opMonitoringBuffer = new TestOpMonitoringBuffer();
        int requestCount = 30_000;
        AtomicInteger processedCounter = new AtomicInteger();
        try {
            IntStream.range(0, requestCount).forEach(index -> {
                executorService.execute(() -> {
                    doSleep(0, 50);
                    OpMonitoringData opMonitoringData = new OpMonitoringData(
                            OpMonitoringData.SecurityServerType.CLIENT, RandomUtils.nextLong());

                    try {
                        opMonitoringBuffer.store(opMonitoringData);
                        processedCounter.incrementAndGet();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    if (index % 10000 == 0) {
                        log.info("Current execution {}+", index);
                    }
                });

            });

            Awaitility.await()
                    .atMost(Duration.ofSeconds(120))
                    .pollDelay(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        assertEquals(requestCount, processedCounter.get());
                        assertEquals(0, opMonitoringBuffer.getCurrentBufferSize());
                    });
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void bufferOverflow() throws Exception {

        System.setProperty("xroad.op-monitor-buffer.size", "2");

        final TestOpMonitoringBuffer opMonitoringBuffer = new TestOpMonitoringBuffer() {
            @Override
            OpMonitoringDaemonSender createSender() throws Exception {
                var mockedSender = mock(OpMonitoringDaemonSender.class);
                when(mockedSender.isReady()).thenReturn(false);
                return mockedSender;
            }
        };
        OpMonitoringData opMonitoringData1 = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 100);
        OpMonitoringData opMonitoringData2 = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 200);
        OpMonitoringData opMonitoringData3 = new OpMonitoringData(
                OpMonitoringData.SecurityServerType.CLIENT, 300);

        opMonitoringBuffer.store(opMonitoringData1);
        opMonitoringBuffer.store(opMonitoringData2);
        opMonitoringBuffer.store(opMonitoringData3);

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollDelay(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    assertEquals(2, opMonitoringBuffer.buffer.size());
                    assertFalse(opMonitoringBuffer.buffer.contains(opMonitoringData1));
                    assertTrue(opMonitoringBuffer.buffer.contains(opMonitoringData2));
                    assertTrue(opMonitoringBuffer.buffer.contains(opMonitoringData3));
                });

//
    }

    @SneakyThrows
    private void doSleep(long min, long max) {
        var sleep = RandomUtils.nextLong(min, max);
        Thread.sleep(sleep);
    }
}
