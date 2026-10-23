/*
 *  Copyright (c) 2026 Metaform Systems, Inc.
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

package org.eclipse.edc.virtual.controlplane.tasks.executor;

import org.eclipse.edc.controlplane.contract.spi.negotiation.ContractNegotiationTaskExecutor;
import org.eclipse.edc.controlplane.tasks.store.TaskStore;
import org.eclipse.edc.controlplane.transfer.spi.TransferProcessTaskExecutor;
import org.eclipse.edc.runtime.metamodel.annotation.Configuration;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;

import java.time.Clock;

public class TaskPollExecutorExtension implements ServiceExtension {

    @Inject
    private TaskStore taskStore;
    @Inject
    private Monitor monitor;
    @Inject
    private TransactionContext transactionContext;
    @Inject
    private TransferProcessTaskExecutor transferProcessTaskExecutor;
    @Inject
    private ContractNegotiationTaskExecutor contractNegotiationTaskExecutor;

    @Configuration
    private TaskPollConfig taskPollConfig;

    private TaskPollExecutor executor;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private Clock clock;

    @Override
    public void initialize(ServiceExtensionContext context) {
        executor = new TaskPollExecutor(taskPollConfig, resolveWorkerCount(context, monitor), executorInstrumentation,
                contractNegotiationTaskExecutor, transferProcessTaskExecutor,
                taskStore, transactionContext, monitor, clock);
    }

    @Override
    public void start() {
        executor.start();
    }

    @Override
    public void shutdown() {
        executor.stop();
    }

    static int resolveWorkerCount(ServiceExtensionContext context, Monitor monitor) {
        var key = DspConfigKeys.TASK_POLL_WORKERS;
        int defaultWorkers = key.convertedDefaultValue();
        int configuredWorkers = context.getSetting(key.key(), defaultWorkers);
        if (configuredWorkers < 1) {
            monitor.warning("TaskPollExecutorExtension: %s must be at least 1, got %d; using %d"
                    .formatted(key.key(), configuredWorkers, defaultWorkers));
            return defaultWorkers;
        }
        return configuredWorkers;
    }

}
