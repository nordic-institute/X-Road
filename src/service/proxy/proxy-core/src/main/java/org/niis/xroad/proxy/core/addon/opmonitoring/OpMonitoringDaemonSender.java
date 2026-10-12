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

import com.google.protobuf.CodedOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends operational data gathered in the OpMonitoringBuffer to the operational monitoring daemon.
 */
@Slf4j
public class OpMonitoringDaemonSender {
    private static final int RECORDS_FIELD_NUMBER = StoreOperationalDataReq.RECORDS_FIELD_NUMBER;

    private final OpMonitoringBuffer opMonitoringBuffer;
    private final OperationalDataStoreClient storeClient;
    private final int maxMessageSize;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private final AtomicBoolean processing = new AtomicBoolean(false);

    OpMonitoringDaemonSender(OpMonitoringBuffer opMonitoringBuffer, OperationalDataStoreClient storeClient,
                             int maxMessageSize) {
        this.opMonitoringBuffer = opMonitoringBuffer;
        this.storeClient = storeClient;
        this.maxMessageSize = maxMessageSize;
    }

    void sendMessage(final List<OpMonitoringData> dataToProcess) {
        executorService.execute(() -> {
            processing.set(true);
            List<OpMonitoringData> undelivered = deliver(dataToProcess);
            processing.set(false);
            if (undelivered.isEmpty()) {
                opMonitoringBuffer.sendingSuccess(dataToProcess.size());
            } else {
                opMonitoringBuffer.sendingFailure(undelivered);
            }
        });
    }

    public boolean isReady() {
        return Boolean.FALSE.equals(processing.get());
    }

    private List<OpMonitoringData> deliver(List<OpMonitoringData> data) {
        Deque<List<Item>> pending;
        try {
            pending = splitByMessageSize(data.stream().map(Item::of).toList());
        } catch (Exception e) {
            log.error("Preparing operational monitoring data failed", e);
            return data;
        }
        while (!pending.isEmpty()) {
            var chunk = pending.peekFirst();
            OperationalDataStoreClient.Result result;
            try {
                result = storeClient.store(chunk.stream().map(Item::proto).toList());
            } catch (Exception e) {
                log.error("Sending operational monitoring data failed", e);
                return undelivered(pending);
            }
            pending.removeFirst();
            if (result == OperationalDataStoreClient.Result.TOO_LARGE) {
                splitOrDrop(chunk, pending);
            }
        }
        return List.of();
    }

    private static void splitOrDrop(List<Item> chunk, Deque<List<Item>> pending) {
        if (chunk.size() == 1) {
            logDroppedRecord(chunk.getFirst());
            return;
        }
        log.warn("op-monitor rejected {} operational monitoring records as too large, retrying in two halves", chunk.size());
        int half = chunk.size() / 2;
        pending.addFirst(chunk.subList(half, chunk.size()));
        pending.addFirst(chunk.subList(0, half));
    }

    private static void logDroppedRecord(Item item) {
        var proto = item.proto();
        log.error("op-monitor rejected one operational monitoring record as too large even when sent alone; the record is"
                        + " dropped and not retried (record size {} bytes, securityServerType {}, requestInTs {}, messageId {},"
                        + " xRequestId {}, serviceCode {}). Every valid record fits the smallest accepted"
                        + " xroad.op-monitor.rpc.max-inbound-message-size, so check op-monitor's inbound message size limit",
                item.size(), proto.getSecurityServerType(), proto.getRequestInTs(), proto.getMessageId(),
                proto.getXRequestId(), proto.getServiceCode());
    }

    private Deque<List<Item>> splitByMessageSize(List<Item> items) {
        Deque<List<Item>> chunks = new ArrayDeque<>();
        List<Item> chunk = new ArrayList<>();
        int chunkSize = 0;
        for (Item item : items) {
            if (!chunk.isEmpty() && chunkSize + item.size() > maxMessageSize) {
                chunks.addLast(chunk);
                chunk = new ArrayList<>();
                chunkSize = 0;
            }
            chunk.add(item);
            chunkSize += item.size();
        }
        if (!chunk.isEmpty()) {
            chunks.addLast(chunk);
        }
        return chunks;
    }

    private static List<OpMonitoringData> undelivered(Deque<List<Item>> pending) {
        return pending.stream().flatMap(List::stream).map(Item::data).toList();
    }

    private record Item(OpMonitoringData data, OperationalDataRecordProto proto, int size) {
        static Item of(OpMonitoringData data) {
            var proto = data.toProto();
            return new Item(data, proto, CodedOutputStream.computeMessageSize(RECORDS_FIELD_NUMBER, proto));
        }
    }

    public void destroy() {
        executorService.shutdown();
        storeClient.close();
    }
}
