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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;
import org.niis.xroad.opmonitor.api.StoreOperationalDataReq;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;

/**
 * Records every store attempt. Scripted outcomes simulate ordinary failures, a response lost after the records were
 * stored, and "message too large" responses;
 * a configured message limit makes larger requests too large, like op-monitor's inbound limit.
 */
final class FakeOperationalDataStoreClient implements OperationalDataStoreClient {
    enum Outcome { NORMAL, FAIL, STORE_THEN_FAIL, TOO_LARGE }

    private final List<List<OperationalDataRecordProto>> attempts = Collections.synchronizedList(new ArrayList<>());
    private final List<List<OperationalDataRecordProto>> stored = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Outcome> scriptedOutcomes = new ArrayDeque<>();
    private volatile int maxMessageSize = Integer.MAX_VALUE;
    private volatile CountDownLatch hold;
    private volatile boolean closed;
    private volatile Predicate<OperationalDataRecordProto> tooLargeRecord = record -> false;

    FakeOperationalDataStoreClient script(Outcome... outcomes) {
        synchronized (scriptedOutcomes) {
            scriptedOutcomes.addAll(List.of(outcomes));
        }
        return this;
    }

    FakeOperationalDataStoreClient withMaxMessageSize(int size) {
        this.maxMessageSize = size;
        return this;
    }

    FakeOperationalDataStoreClient rejectAsTooLarge(Predicate<OperationalDataRecordProto> record) {
        this.tooLargeRecord = record;
        return this;
    }

    FakeOperationalDataStoreClient holdUntil(CountDownLatch latch) {
        this.hold = latch;
        return this;
    }

    @Override
    public Result store(List<OperationalDataRecordProto> records) {
        attempts.add(List.copyOf(records));
        awaitHold();
        Outcome outcome;
        synchronized (scriptedOutcomes) {
            outcome = scriptedOutcomes.pollFirst();
        }
        if (outcome == Outcome.FAIL) {
            throw XrdRuntimeException.systemInternalError("simulated store failure");
        }
        if (outcome == Outcome.STORE_THEN_FAIL) {
            stored.add(List.copyOf(records));
            throw XrdRuntimeException.systemInternalError("simulated lost response after the records were stored");
        }
        if (outcome == Outcome.TOO_LARGE || requestSize(records) > maxMessageSize
                || records.stream().anyMatch(tooLargeRecord)) {
            return Result.TOO_LARGE;
        }
        stored.add(List.copyOf(records));
        return Result.STORED;
    }

    @Override
    public void close() {
        closed = true;
    }

    boolean isClosed() {
        return closed;
    }

    List<List<OperationalDataRecordProto>> attempts() {
        return List.copyOf(attempts);
    }

    List<List<OperationalDataRecordProto>> storedBatches() {
        return List.copyOf(stored);
    }

    List<OperationalDataRecordProto> storedRecords() {
        return storedBatches().stream().flatMap(List::stream).toList();
    }

    static int requestSize(List<OperationalDataRecordProto> records) {
        return StoreOperationalDataReq.newBuilder().addAllRecords(records).build().getSerializedSize();
    }

    private void awaitHold() {
        var latch = hold;
        if (latch != null) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
