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
package org.niis.xroad.confclient.application.healthcheck;

import ee.ria.xroad.common.DiagnosticStatus;
import ee.ria.xroad.common.DiagnosticsStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.confclient.core.config.ConfClientJobConfig;

/**
 * Readiness check for GlobalConf status.
 * <p>
 * IMPORTANT: UNINITIALIZED status is considered UP, not DOWN.
 * This is critical to prevent cascade failures during system startup:
 * - New Security Server installation starts with no globalconf
 * - Configuration-client needs time to download globalconf on first boot
 * - Marking as DOWN would cause unnecessary pod restarts and prevent initial setup
 * <p>
 * Only ERROR and UNKNOWN statuses are considered failures.
 */
@Slf4j
@Readiness
@ApplicationScoped
public class GlobalConfReadinessCheck implements HealthCheck {
    private static final String NAME = "GLOBALCONF_READINESS_CHECK";

    @Inject
    ConfClientJobConfig.ConfigurationClientJobListener jobListener;

    @Override
    public HealthCheckResponse call() {
        DiagnosticsStatus diagnosticsStatus = jobListener.getStatus();
        if (diagnosticsStatus == null) {
            return HealthCheckResponse.builder()
                    .name(NAME)
                    .up()
                    .withData("status", "NOT_YET_CHECKED")
                    .build();
        }

        DiagnosticStatus status = diagnosticsStatus.getStatus();
        return switch (status) {
            case OK -> HealthCheckResponse.builder()
                    .name(NAME)
                    .up()
                    .withData("status", "OK")
                    .build();
            case UNINITIALIZED -> {
                // UNINITIALIZED is acceptable - system not initialized
                log.debug("GlobalConf is UNINITIALIZED, reporting readiness as UP (startup in progress)");
                yield HealthCheckResponse.builder()
                        .name(NAME)
                        .up()
                        .withData("status", "UNINITIALIZED")
                        .build();
            }
            case ERROR, UNKNOWN -> {
                log.warn("GlobalConf status is {}, reporting readiness as DOWN", status);
                yield HealthCheckResponse.builder()
                        .name(NAME)
                        .down()
                        .withData("status", status.name())
                        .withData("description", diagnosticsStatus.getDescription() != null
                                ? diagnosticsStatus.getDescription() : "No description available")
                        .build();
            }
        };
    }
}
