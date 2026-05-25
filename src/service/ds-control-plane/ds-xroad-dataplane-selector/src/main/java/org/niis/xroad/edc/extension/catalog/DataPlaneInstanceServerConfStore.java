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
package org.niis.xroad.edc.extension.catalog;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.selector.spi.store.DataPlaneInstanceStore;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * In-memory single-slot {@link DataPlaneInstanceStore} that overrides the default SQL store on the classpath.
 * Holds at most one data plane instance per ID with idempotent upsert semantics.
 * State machine methods (nextNotLeased) are not used by control-plane routing and return empty results.
 */
@Slf4j
class DataPlaneInstanceServerConfStore implements DataPlaneInstanceStore {

    private final ConcurrentHashMap<String, DataPlaneInstance> store = new ConcurrentHashMap<>();

    @Override
    public StoreResult<Void> save(DataPlaneInstance instance) {
        store.put(instance.getId(), instance);
        if (log.isTraceEnabled()) {
            log.trace("save instanceId={} stored, total={}", instance.getId(), store.size());
        }
        return StoreResult.success();
    }

    @Override
    @Nullable
    public DataPlaneInstance findById(String id) {
        log.trace("findById instanceId={}", id);
        var instance = store.get(id);
        log.trace("findById instanceId={} result={}", id, instance != null ? "found" : "not found");
        return instance;
    }

    @Override
    public Stream<DataPlaneInstance> getAll() {
        if (log.isTraceEnabled()) {
            log.trace("getAll storeSize={}", store.size());
        }
        return store.values().stream();
    }

    @Override
    public Stream<DataPlaneInstance> query(QuerySpec querySpec) {
        return store.values().stream()
                .skip(querySpec.getOffset())
                .limit(querySpec.getLimit());
    }

    @Override
    public StoreResult<DataPlaneInstance> deleteById(String instanceId) {
        log.trace("deleteById instanceId={}", instanceId);
        var removed = store.remove(instanceId);
        log.trace("deleteById instanceId={} result={}", instanceId, removed != null ? "removed" : "not found");
        if (removed != null) {
            return StoreResult.success(removed);
        }
        return StoreResult.notFound("Data plane instance %s not found".formatted(instanceId));
    }

    @Override
    @NotNull
    public List<DataPlaneInstance> nextNotLeased(int max, Criterion... criteria) {
        return List.of();
    }

    @Override
    public StoreResult<DataPlaneInstance> findByIdAndLease(String id) {
        var instance = store.get(id);
        if (instance != null) {
            return StoreResult.success(instance);
        }
        return StoreResult.notFound("Data plane instance %s not found".formatted(id));
    }

    @Override
    public StoreResult<Void> breakLease(DataPlaneInstance entity) {
        return StoreResult.success();
    }
}
