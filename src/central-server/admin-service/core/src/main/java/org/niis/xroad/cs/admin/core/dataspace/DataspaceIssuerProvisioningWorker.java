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
package org.niis.xroad.cs.admin.core.dataspace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.service.DataspaceIssuerProvisioningService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Level-triggered worker that provisions the co-located data space issuer once the Central Server
 * is initialized. The issuer service cannot start before its DS TLS certificate is provisioned,
 * and that certificate is obtained after initialization (ACME enrollment or the manual CSR
 * upload), so initialization must not depend on a live issuer; this worker retries until the
 * issuer accepts the provisioning calls, which are idempotent on the issuer side.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataspaceIssuerProvisioningWorker {

    static final int JOB_REPEAT_INTERVAL_MS = 30000;
    static final int INITIAL_DELAY_MS = 30000;

    private final DataspaceIssuerProvisioningService dataspaceIssuerProvisioningService;
    private final SystemParameterService systemParameterService;

    private final AtomicBoolean provisioned = new AtomicBoolean(false);

    /**
     * Scheduled provisioning tick. Skips until the Central Server is initialized, then retries
     * on every tick until one full provisioning pass succeeds; failures are non-fatal.
     */
    @Scheduled(fixedRate = JOB_REPEAT_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void scheduledProvision() {
        if (provisioned.get()) {
            return;
        }
        if (systemParameterService.getInstanceIdentifier().isEmpty()) {
            log.debug("Data space issuer provisioning: Central Server not yet initialized, skipping");
            return;
        }
        provisionBestEffort();
    }

    /**
     * Non-blocking eager trigger for the initialization request path: runs one best-effort
     * provisioning attempt on a background thread; the scheduled tick remains the convergence
     * guarantee.
     */
    public void provisionAsync() {
        CompletableFuture.runAsync(this::provisionBestEffort);
    }

    private void provisionBestEffort() {
        if (provisioned.get()) {
            return;
        }
        try {
            dataspaceIssuerProvisioningService.provisionIssuer();
            provisioned.set(true);
            log.info("Data space issuer provisioned");
        } catch (Exception e) {
            log.error("Data space issuer provisioning failed; will retry once the issuer service becomes available", e);
        }
    }
}
