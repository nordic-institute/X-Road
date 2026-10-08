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
package org.niis.xroad.proxy.dataplane;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

/**
 * Periodically deletes {@code dataflow_state} rows that have sat in a terminal state (completed or
 * terminated) for longer than {@link DataFlowStateRetentionProperties#retention()}, off the request
 * path, on a programmatic {@link Scheduler} job.
 *
 * <p>The job runs {@link #CHECKS_PER_RETENTION_PERIOD} times per retention window rather than once:
 * a row becomes eligible at an arbitrary point between two runs, so checking only once per window
 * would let it sit for up to another full window past its cutoff. Dividing by four bounds that extra
 * staleness to a quarter of the retention period while keeping the query cheap and infrequent.
 */
@Startup
@ApplicationScoped
@Slf4j
@RequiredArgsConstructor
public class DataFlowStateRetentionJob {

    private static final int CHECKS_PER_RETENTION_PERIOD = 4;

    private final Scheduler scheduler;
    private final DataFlowStateStore dataFlowStateStore;
    private final DataFlowStateRetentionProperties properties;
    private final Scheduled.ApplicationNotRunning applicationNotRunning;

    @PostConstruct
    public void init() {
        var retention = properties.retention();
        var period = retention.dividedBy(CHECKS_PER_RETENTION_PERIOD);
        log.info("Scheduling dataflow-state retention cleanup every {} (retention {})", period, retention);
        scheduler.newJob(getClass().getSimpleName())
                .setInterval(period.toString())
                .setTask(_ -> prune(retention))
                .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                .setSkipPredicate(applicationNotRunning)
                .schedule();
    }

    private void prune(Duration retention) {
        var deleted = dataFlowStateStore.pruneTerminal(retention);
        if (deleted > 0) {
            log.debug("Pruned {} terminal dataflow_state row(s) unchanged for longer than {}", deleted, retention);
        }
    }
}
