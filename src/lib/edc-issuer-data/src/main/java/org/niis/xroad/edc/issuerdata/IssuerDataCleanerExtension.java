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

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ExecutorInstrumentation;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.sql.QueryExecutor;
import org.eclipse.edc.transaction.datasource.spi.DataSourceRegistry;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.niis.xroad.common.properties.NodeProperties;

import java.time.Clock;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Runs the {@link IssuerDataCleaner} periodically on every node except a secondary cluster node. The rows are shared
 * by all nodes of a database, and the deletion is idempotent, so the other nodes may all run it.
 */
@Extension(IssuerDataCleanerExtension.NAME)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@NoArgsConstructor
public class IssuerDataCleanerExtension implements ServiceExtension {

    static final String NAME = "X-Road Issuer Data Cleanup";
    static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(10);

    @Inject
    private DataSourceRegistry dataSourceRegistry;

    @Inject
    private TransactionContext transactionContext;

    @Inject
    private QueryExecutor queryExecutor;

    @Inject
    private ExecutorInstrumentation executorInstrumentation;

    @Inject
    private Clock clock;

    private BooleanSupplier secondaryNode = NodeProperties::isSecondaryNode;

    private Duration interval = DEFAULT_INTERVAL;

    private IssuerDataCleaner cleaner;

    private IssuerDataCleanupScheduler scheduler;

    private Monitor monitor;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        monitor = context.getMonitor();
        var store = new SqlIssuerDataStore(dataSourceRegistry, DataSourceRegistry.DEFAULT_DATASOURCE, transactionContext,
                queryExecutor);
        cleaner = new IssuerDataCleaner(store, clock, monitor);
    }

    @Override
    public void start() {
        if (secondaryNode.getAsBoolean()) {
            monitor.info("Issuer data cleanup not scheduled: this is a secondary cluster node");
            return;
        }
        scheduler = IssuerDataCleanupScheduler.start(cleaner::run, interval, executorInstrumentation, monitor);
    }

    @Override
    public void shutdown() {
        if (scheduler != null) {
            scheduler.close();
        }
    }
}
