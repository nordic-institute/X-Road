/*
 *  Copyright (c) 2023 Metaform Systems, Inc.
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Metaform Systems, Inc. - initial API and implementation
 *
 */

package org.eclipse.edc.identityhub.did.defaults;

import org.eclipse.edc.identithub.spi.did.model.DidResource;
import org.eclipse.edc.identithub.spi.did.store.DidResourceStore;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QueryResolver;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.store.ReflectionBasedQueryResolver;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Stores {@link DidResource} objects in an in-memory map. This implementation is thread-safe
 */
public class InMemoryDidResourceStore implements DidResourceStore {
    private final Map<String, DidResource> store = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final QueryResolver<DidResource> queryResolver;

    public InMemoryDidResourceStore(CriterionOperatorRegistry criterionOperatorRegistry) {
        queryResolver = new ReflectionBasedQueryResolver<>(DidResource.class, criterionOperatorRegistry);
    }

    @Override
    public StoreResult<Void> save(DidResource resource) {
        lock.writeLock().lock();
        try {
            var did = resource.getDid();
            if (store.containsKey(did)) {
                return StoreResult.alreadyExists(alreadyExistsErrorMessage(did));
            }
            store.put(did, resource);
            return StoreResult.success();
        } finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    public StoreResult<Void> update(DidResource resource) {
        lock.writeLock().lock();
        try {
            var did = resource.getDid();
            if (!store.containsKey(did)) {
                return StoreResult.notFound(notFoundErrorMessage(did));
            }
            store.put(did, resource);
            return StoreResult.success();
        } finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    public DidResource findById(String did) {
        lock.readLock().lock();
        try {
            return store.get(did);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Collection<DidResource> query(QuerySpec query) {
        lock.readLock().lock();
        try {
            return queryResolver.query(store.values().stream(), query).toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public StoreResult<Void> deleteById(String did) {
        lock.writeLock().lock();
        try {
            return store.remove(did) == null
                    ? StoreResult.notFound(notFoundErrorMessage(did))
                    : StoreResult.success();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
