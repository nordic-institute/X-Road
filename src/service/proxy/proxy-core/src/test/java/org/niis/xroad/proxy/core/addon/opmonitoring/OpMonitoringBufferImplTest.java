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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.configuration.ProxyProperties;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests operational monitoring buffer.
 */
@Slf4j
class OpMonitoringBufferImplTest {

    private final FakeOperationalDataStoreClient client = new FakeOperationalDataStoreClient();

    @SuppressWarnings("checkstyle:FinalClass")
    private class TestOpMonitoringBufferImpl extends OpMonitoringBufferImpl {
        TestOpMonitoringBufferImpl(ProxyProperties.Addon.ProxyAddonOpMonitorProperties opMonitorProperties) {
            super(mock(ServerConfProvider.class), opMonitorProperties, () -> client);
        }

        @Override
        OpMonitoringDataProcessor createDataProcessor() {
            return new TestOpMonitoringDataProcessor();
        }
    }

    private static final class TestOpMonitoringDataProcessor extends OpMonitoringDataProcessor {
        @Override
        String getIpAddress() {
            return "127.0.0.1";
        }
    }

    @Test
    void bufferSaturatesUnderLoad() {
        var opMonitoringBuffer = new TestOpMonitoringBufferImpl(properties(Map.of("xroad.proxy.addon.op-monitor.buffer.size", "10000")));
        int requestCount = 30_000;
        AtomicInteger processedCounter = new AtomicInteger();
        try (ExecutorService executorService = Executors.newFixedThreadPool(80)) {
            IntStream.range(0, requestCount).forEach(index -> executorService.execute(() -> {
                doSleep(0, 50);
                opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT,
                        RandomUtils.secure().randomLong()));
                processedCounter.incrementAndGet();
            }));

            Awaitility.await()
                    .atMost(Duration.ofSeconds(120))
                    .pollDelay(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        assertThat(processedCounter.get()).isEqualTo(requestCount);
                        assertThat(opMonitoringBuffer.getCurrentBufferSize()).isZero();
                        assertThat(client.storedRecords()).hasSize(requestCount);
                    });
        } finally {
            opMonitoringBuffer.destroy();
        }
    }

    @Test
    void bufferOverflow() {
        client.holdUntil(new CountDownLatch(1));
        var opMonitoringBuffer = new TestOpMonitoringBufferImpl(properties(Map.of("xroad.proxy.addon.op-monitor.buffer.size", "2")));
        var first = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 100);
        var second = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 200);
        var third = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 300);
        var fourth = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 400);

        opMonitoringBuffer.store(first);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !client.attempts().isEmpty());
        opMonitoringBuffer.store(second);
        opMonitoringBuffer.store(third);
        opMonitoringBuffer.store(fourth);

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(opMonitoringBuffer.buffer).containsExactly(third, fourth));
        opMonitoringBuffer.destroy();
    }

    @Test
    void failedRecordIsRetriedBySchedulerWithoutFurtherStores() {
        client.script(FakeOperationalDataStoreClient.Outcome.FAIL);
        var opMonitoringBuffer = new TestOpMonitoringBufferImpl(properties(Map.of(
                "xroad.proxy.addon.op-monitor.buffer.sending-interval-seconds", "1")));
        opMonitoringBuffer.init();
        var data = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 100);

        opMonitoringBuffer.store(data);

        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(client.storedRecords()).containsExactly(data.toProto()));
        assertThat(client.attempts()).hasSize(2);
        assertThat(opMonitoringBuffer.getCurrentBufferSize()).isZero();
        opMonitoringBuffer.destroy();
    }

    @Test
    void batchesHoldAtMostMaxRecordsInMessage() {
        var hold = new CountDownLatch(1);
        client.holdUntil(hold);
        var opMonitoringBuffer = new TestOpMonitoringBufferImpl(properties(Map.of(
                "xroad.proxy.addon.op-monitor.buffer.max-records-in-message", "5")));
        opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 0));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !client.attempts().isEmpty());
        IntStream.range(1, 13).forEach(i -> opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, i)));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> opMonitoringBuffer.getCurrentBufferSize() == 12);

        hold.countDown();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(client.storedRecords()).hasSize(13));
        assertThat(client.storedBatches()).allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(5));
        opMonitoringBuffer.destroy();
    }

    @Test
    void failedRecordsAreRequeuedAtTheHeadInReverseOrder() {
        client.holdUntil(new CountDownLatch(1));
        var opMonitoringBuffer = new TestOpMonitoringBufferImpl(properties(Map.of()));
        var queued = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 0);
        opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, -1));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !client.attempts().isEmpty());
        opMonitoringBuffer.store(queued);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> opMonitoringBuffer.getCurrentBufferSize() == 1);
        var first = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 1);
        var second = new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 2);

        opMonitoringBuffer.sendingFailure(List.of(first, second));

        assertThat(opMonitoringBuffer.buffer).containsExactly(second, first, queued);
        opMonitoringBuffer.destroy();
    }

    @Test
    void shutdownWhileSendingClosesTheStoreClientAndDoesNotFlushBufferedRecords() {
        var hold = new CountDownLatch(1);
        client.holdUntil(hold);
        var opMonitoringBuffer = new TestOpMonitoringBufferImpl(properties(Map.of()));
        opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 0));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> !client.attempts().isEmpty());
        opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 1));
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> opMonitoringBuffer.getCurrentBufferSize() == 1);

        opMonitoringBuffer.destroy();
        hold.countDown();
        opMonitoringBuffer.store(new OpMonitoringData(OpMonitoringData.SecurityServerType.CLIENT, 2));

        assertThat(client.isClosed()).isTrue();
        assertThat(client.attempts()).hasSize(1);
        assertThat(opMonitoringBuffer.getCurrentBufferSize()).isEqualTo(1);
    }

    @Test
    void noOpMonitoringDataIsStored() {
        var serverConfProvider = mock(ServerConfProvider.class);
        var clientRequested = new AtomicBoolean();

        new OpMonitoringBufferImpl(serverConfProvider, properties(Map.of("xroad.proxy.addon.op-monitor.buffer.size", "0")),
                () -> {
                    clientRequested.set(true);
                    return client;
                });

        verifyNoInteractions(serverConfProvider);
        assertThat(clientRequested).isFalse();
    }

    static ProxyProperties.Addon.ProxyAddonOpMonitorProperties properties(Map<String, String> overrides) {
        return new ProxyProperties(XRoadConfigBuilder.create().register(ProxyConfigKeys.instance()).overrides(overrides).build())
                .addon().opMonitor();
    }

    @SneakyThrows
    @SuppressWarnings("squid:S2925")
    private static void doSleep(long min, long max) {
        Thread.sleep(RandomUtils.secure().randomLong(min, max));
    }
}
