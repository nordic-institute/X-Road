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
package org.niis.xroad.edc.extension.cleaner;

import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.services.spi.transferprocess.TransferProcessService;
import org.eclipse.edc.connector.controlplane.store.sql.contractnegotiation.store.schema.postgres.PostgresDialectStatements;
import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.policy.monitor.store.sql.schema.PolicyMonitorStatements;
import org.eclipse.edc.connector.policy.monitor.store.sql.schema.PostgresPolicyMonitorStatements;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs {@link DspRecordCleaner} periodically on every control-plane instance.
 */
@Extension(value = DspRecordCleanerExtension.EXTENSION_NAME)
public class DspRecordCleanerExtension implements ServiceExtension {

    public static final String EXTENSION_NAME = "X-Road DSP Record Cleaner";

    private static final String THREAD_NAME = "DspRecordCleaner";
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    @Setting(key = "xroad.dsp-record-cleaner.interval",
            description = "How often the DSP record cleaner runs, in ISO-8601 duration format",
            defaultValue = "PT10M")
    private Duration interval;

    @Setting(key = "xroad.dsp-record-cleaner.age",
            description = "How old DSP records must be before the cleaner closes or deletes them, in ISO-8601 "
                    + "duration format. Must exceed PT1H10M: the policy monitor period plus the data-plane signal retry window.",
            defaultValue = "PT6H")
    private Duration age;

    @Setting(key = "edc.sql.store.policy-monitor.datasource",
            description = "The datasource backing the policy monitor table",
            defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE)
    private String policyMonitorDataSourceName;

    @Setting(key = "edc.sql.store.contractnegotiation.datasource",
            description = "The datasource backing the contract negotiation tables",
            defaultValue = DataSourceRegistry.DEFAULT_DATASOURCE)
    private String negotiationDataSourceName;

    @Inject
    private TransferProcessStore transferProcessStore;

    @Inject
    private TransferProcessService transferProcessService;

    @Inject
    private ContractNegotiationStore contractNegotiationStore;

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject(required = false)
    private PolicyMonitorStatements policyMonitorStatements;

    @Inject
    private ServerConfDatabaseCtx serverConfDatabaseCtx;

    @Inject
    private Clock clock;

    @Inject
    private Monitor monitor;

    private DspRecordCleaner cleaner;
    private ScheduledExecutorService executor;

    @Override
    public String name() {
        return EXTENSION_NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        DspRecordCleaner.requireValidInterval(interval);
        var statements = policyMonitorStatements != null ? policyMonitorStatements : new PostgresPolicyMonitorStatements();
        var policyMonitorEntryPurger = new PolicyMonitorEntryPurger(dataSourceRegistry, policyMonitorDataSourceName,
                transactionContext, queryExecutor, statements);
        var negotiationsWithoutAgreement = new NegotiationsWithoutAgreementReader(dataSourceRegistry,
                negotiationDataSourceName, transactionContext, queryExecutor, new PostgresDialectStatements(clock));
        cleaner = new DspRecordCleaner(transferProcessStore, transferProcessService, contractNegotiationStore,
                negotiationsWithoutAgreement, policyMonitorEntryPurger, serverConfDatabaseCtx, clock, monitor,
                DspRecordCleaner.requireValidAge(age));
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name(THREAD_NAME).daemon().factory());
        scheduleNextPass();
    }

    @Override
    public void shutdown() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException _) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void runPass() {
        try {
            cleaner.run();
        } catch (Exception e) {
            monitor.severe("DSP record cleaner pass failed", e);
        } finally {
            scheduleNextPass();
        }
    }

    private void scheduleNextPass() {
        try {
            executor.schedule(this::runPass, interval.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException _) {
            monitor.debug("DSP record cleaner stopped");
        }
    }
}
