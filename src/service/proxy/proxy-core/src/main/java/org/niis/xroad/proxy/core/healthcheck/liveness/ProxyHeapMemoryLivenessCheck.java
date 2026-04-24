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
package org.niis.xroad.proxy.core.healthcheck.liveness;

import ee.ria.xroad.common.ProxyMemory;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.niis.xroad.proxy.core.admin.ProxyMemoryStatusService;

/**
 * Proxy-specific heap memory liveness check.
 * <p>
 * The check always runs. It can only report DOWN when
 * {@code xroad.proxy.memory-usage-threshold} is configured AND current heap usage is above
 * that threshold. If the property is absent (the operator opt-out), the check reports UP
 * unconditionally — the bean remains registered but never fails liveness.
 * <p>
 * Threshold and live heap metrics are sourced from {@link ProxyMemoryStatusService}, which is
 * the single source of truth for the operator-visible {@code xroad.proxy.memory-usage-threshold}
 * contract (percentage in {@code [0, 100]}).
 */
@Liveness
@ApplicationScoped
@RequiredArgsConstructor
public class ProxyHeapMemoryLivenessCheck implements HealthCheck {

    private static final String NAME = "PROXY_HEAP_MEMORY_CHECK";

    private final ProxyMemoryStatusService proxyMemoryStatusService;

    @Override
    public HealthCheckResponse call() {
        ProxyMemory memory = proxyMemoryStatusService.getMemoryStatus();

        HealthCheckResponseBuilder response = HealthCheckResponse.named(NAME)
                .withData("used_percent", memory.usedPercent())
                .withData("used_bytes", memory.usedMemory())
                .withData("max_bytes", memory.maxMemory());

        if (memory.threshold() != null) {
            response.withData("threshold_percent", memory.threshold());
        } else {
            response.withData("threshold_configured", false);
        }

        return memory.isUsedAboveThreshold() ? response.down().build() : response.up().build();
    }
}
