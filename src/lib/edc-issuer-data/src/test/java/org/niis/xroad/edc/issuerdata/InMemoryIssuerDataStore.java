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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.spi.result.StoreResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory {@link IssuerDataStore} for tests: the last write wins, and the switches make reads and writes fail
 * or throw, and the write gate holds a write back. It does not apply the "replace only a later send time" rule of the SQL store.
 */
final class InMemoryIssuerDataStore implements IssuerDataStore {

    final AtomicInteger reads = new AtomicInteger();
    final AtomicInteger writes = new AtomicInteger();
    final List<Thread> readThreads = new CopyOnWriteArrayList<>();
    volatile boolean failingReads;
    volatile boolean throwingReads;
    volatile boolean failingWrites;
    volatile boolean throwingWrites;
    volatile CountDownLatch writeGate;
    private final Map<String, StoredIssuerData> rows = new ConcurrentHashMap<>();

    @Override
    public StoreResult<Void> write(IssuerDataKind kind, String key, StoredIssuerData data) {
        writes.incrementAndGet();
        awaitWriteGate();
        if (throwingWrites) {
            throw new IllegalStateException("database broke");
        }
        if (failingWrites) {
            return StoreResult.generalError("database down");
        }
        rows.put(rowKey(kind, key), data);
        return StoreResult.success();
    }

    @Override
    public StoreResult<StoredIssuerData> read(IssuerDataKind kind, String key) {
        reads.incrementAndGet();
        readThreads.add(Thread.currentThread());
        if (throwingReads) {
            throw new IllegalStateException("database broke");
        }
        if (failingReads) {
            return StoreResult.generalError("database down");
        }
        var row = rows.get(rowKey(kind, key));
        return row == null ? StoreResult.notFound("no row") : StoreResult.success(row);
    }

    @Override
    public StoreResult<Integer> deleteExpired(Instant now) {
        throw new UnsupportedOperationException();
    }

    private void awaitWriteGate() {
        var gate = writeGate;
        if (gate == null) {
            return;
        }
        try {
            gate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    void put(IssuerDataKind kind, String key, StoredIssuerData data) {
        rows.put(rowKey(kind, key), data);
    }

    StoredIssuerData row(IssuerDataKind kind, String key) {
        return rows.get(rowKey(kind, key));
    }

    private static String rowKey(IssuerDataKind kind, String key) {
        return kind + "|" + key;
    }
}
