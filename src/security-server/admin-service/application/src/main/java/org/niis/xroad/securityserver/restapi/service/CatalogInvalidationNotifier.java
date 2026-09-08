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
package org.niis.xroad.securityserver.restapi.service;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Signals the data space control plane to flush its catalog caches on a local client add or remove,
 * so the change is visible without waiting out the cache's own expiry. Keeps {@link ClientService}
 * free of a direct dependency on dataspace internals.
 *
 * <p>Best-effort by design: the cache expiry stays in place as the correctness backstop, so a lost
 * or failed signal degrades latency, never correctness. A no-op when the data space feature is
 * disabled, so a non-dataspace deployment never attempts the call.</p>
 *
 * <p>The gRPC call is dispatched off the caller's thread, and — when a transaction is active around
 * the caller — deferred until that transaction commits. Firing beforehand would let a concurrent
 * catalog read re-cache pre-commit state; firing on the caller's thread would hold the transaction
 * open for up to the RPC deadline.</p>
 */
@Slf4j
@Service
public class CatalogInvalidationNotifier implements DisposableBean {

    private final ControlPlaneProvisioningClient controlPlaneProvisioningClient;
    private final AdminServiceProperties adminServiceProperties;
    private final ExecutorService executorService;

    @Autowired
    public CatalogInvalidationNotifier(ControlPlaneProvisioningClient controlPlaneProvisioningClient,
                                       AdminServiceProperties adminServiceProperties) {
        this(controlPlaneProvisioningClient, adminServiceProperties,
                Executors.newSingleThreadExecutor(CatalogInvalidationNotifier::newDaemonThread));
    }

    CatalogInvalidationNotifier(ControlPlaneProvisioningClient controlPlaneProvisioningClient,
                                AdminServiceProperties adminServiceProperties,
                                ExecutorService executorService) {
        this.controlPlaneProvisioningClient = controlPlaneProvisioningClient;
        this.adminServiceProperties = adminServiceProperties;
        this.executorService = executorService;
    }

    /**
     * Notifies the control plane that the local client set changed. Swallows and logs any failure —
     * the caller's operation must never fail because this signal could not be delivered.
     */
    public void invalidateCatalogCaches() {
        if (!adminServiceProperties.getDataspace().isEnabled()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new AfterCommitInvalidation());
        } else {
            dispatch();
        }
    }

    private void dispatch() {
        try {
            executorService.execute(this::invalidateNow);
        } catch (RejectedExecutionException e) {
            log.warn("Data space: catalog invalidation dispatch rejected (executor shutting down); the "
                    + "catalog cache will refresh once its normal expiry elapses", e);
        }
    }

    private void invalidateNow() {
        try {
            controlPlaneProvisioningClient.invalidateCatalogCaches();
        } catch (Exception e) {
            log.warn("Data space: failed to notify the control plane of a client change; the catalog "
                    + "cache will refresh once its normal expiry elapses", e);
        }
    }

    @Override
    public void destroy() {
        executorService.shutdown();
    }

    private static Thread newDaemonThread(Runnable runnable) {
        var thread = new Thread(runnable, "catalog-invalidation-notifier");
        thread.setDaemon(true);
        return thread;
    }

    private final class AfterCommitInvalidation implements TransactionSynchronization {
        @Override
        public void afterCommit() {
            dispatch();
        }
    }
}
