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
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
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
    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Setting(description = "The datasource the task store uses; the process lock must run on this connection so it is "
            + "released with the task transaction",
            defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE, key = "edc.sql.store.tasks.datasource")
    private String taskDataSourceName;

    @Configuration
    private TaskPollConfig taskPollConfig;

    private TaskPollExecutor executor;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private Clock clock;

    @Override
    public void initialize(ServiceExtensionContext context) {
        var processLock = new ProcessLock(dataSourceRegistry, taskDataSourceName);
        int workers = resolveWorkerCount(context, monitor);
        logPoolSize(context, monitor, workers);
        executor = new TaskPollExecutor(taskPollConfig, workers, executorInstrumentation,
                contractNegotiationTaskExecutor, transferProcessTaskExecutor,
                taskStore, transactionContext, monitor, clock, processLock);
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

    static void logPoolSize(ServiceExtensionContext context, Monitor monitor, int workers) {
        var maxTotalKey = DspConfigKeys.CONTROL_PLANE_POOL_MAX_TOTAL;
        var maxIdleKey = DspConfigKeys.CONTROL_PLANE_POOL_MAX_IDLE;
        int maxTotal = context.getSetting(maxTotalKey.key(), maxTotalKey.convertedDefaultValue());
        int maxIdle = context.getSetting(maxIdleKey.key(), maxIdleKey.convertedDefaultValue());

        monitor.info("TaskPollExecutorExtension: datasource pool max-total=%d, max-idle=%d, workers=%d"
                .formatted(maxTotal, maxIdle, workers));

        int recommendedMinimum = 2 * workers + 2;
        if (maxTotal < recommendedMinimum) {
            monitor.warning(("TaskPollExecutorExtension: %s=%d is below 2 * workers + 2 (%d) for %d workers; "
                    + "raise %s and set %s to match")
                    .formatted(maxTotalKey.key(), maxTotal, recommendedMinimum, workers, maxTotalKey.key(), maxIdleKey.key()));
        }
    }

}
