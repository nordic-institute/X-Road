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

package org.niis.xroad.signer.core.job;

import io.quarkus.runtime.Startup;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.core.certmanager.OcspClientWorker;
import org.niis.xroad.signer.core.config.SignerProperties;

@Slf4j
@Startup
@RequiredArgsConstructor
@ApplicationScoped
public class OcspClientReloadJob {
    private final OcspClientWorker ocspClientWorker;
    private final OcspClientExecuteScheduler ocspClientExecuteScheduler;
    private final Scheduler scheduler;
    private final SignerProperties signerProperties;
    private final Scheduled.ApplicationNotRunning applicationNotRunning;

    void reload(ScheduledExecution execution) {
        log.trace("OcspClientReloadJob triggered");
        ocspClientWorker.reload(ocspClientExecuteScheduler);
    }

    @PostConstruct
    public void init() {
        if (signerProperties.ocspResponseRetrievalActive()) {
            log.info("Scheduling OcspClientReloadJob");
            scheduler.newJob(getClass().getSimpleName())
                    .setDelayed("100ms")
                    .setInterval("60s")
                    .setTask(this::reload)
                    .setConcurrentExecution(Scheduled.ConcurrentExecution.SKIP)
                    .setSkipPredicate(applicationNotRunning)
                    .schedule();
        } else {
            log.info("OCSP-retrieval configured to be inactive, job auto-scheduling disabled");
        }
    }

}
