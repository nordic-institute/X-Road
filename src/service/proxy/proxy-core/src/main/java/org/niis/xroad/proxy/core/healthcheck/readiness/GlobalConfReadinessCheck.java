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
package org.niis.xroad.proxy.core.healthcheck.readiness;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.niis.xroad.globalconf.GlobalConfSource;
import org.niis.xroad.globalconf.model.GlobalConfInitState;

import static org.niis.xroad.common.healthcheck.HealthCheckConstants.STATUS;

/**
 * Readiness check for GlobalConf status.
 * <p>
 * IMPORTANT: UNINITIALIZED and related startup states are considered UP, not DOWN.
 * This is critical to prevent cascade failures during system startup:
 * - New Security Server installation starts with no globalconf
 * - Configuration-client needs time to download globalconf on first boot
 * - Marking as DOWN would cause unnecessary pod restarts and prevent initial setup
 * <p>
 * Only actual failure states are considered failures.
 */
@Slf4j
@Readiness
@ApplicationScoped
@RequiredArgsConstructor
public class GlobalConfReadinessCheck implements HealthCheck {
    private static final String NAME = "PROXY_GLOBALCONF_READINESS_CHECK";

    private final GlobalConfSource globalConfSource;

    @Override
    public HealthCheckResponse call() {
        GlobalConfInitState state = globalConfSource.getReadinessState();

        return switch (state) {
            case INITIALIZED -> {
                // Verify the config is also valid (not expired)
                if (!globalConfSource.isExpired()) {
                    yield HealthCheckResponse.builder()
                            .name(NAME)
                            .up()
                            .withData(STATUS, "OK")
                            .build();
                } else {
                    log.warn("GlobalConf is initialized but expired, reporting readiness as DOWN");
                    yield HealthCheckResponse.builder()
                            .name(NAME)
                            .down()
                            .withData(STATUS, "EXPIRED")
                            .build();
                }
            }
            case UNKNOWN, UNINITIALIZED, READY_TO_INIT,
                    FAILURE_MISSING_ANCHOR, FAILURE_MISSING_INSTANCE_IDENTIFIER -> {
                // These states are acceptable during startup - system not yet initialized/configured
                log.debug("GlobalConf is {}, reporting readiness as UP (startup in progress)", state);
                yield HealthCheckResponse.builder()
                        .name(NAME)
                        .up()
                        .withData(STATUS, state.name())
                        .build();
            }
            default -> {
                log.warn("GlobalConf status is {}, reporting readiness as DOWN", state);
                yield HealthCheckResponse.builder()
                        .name(NAME)
                        .down()
                        .withData(STATUS, state.name())
                        .build();
            }
        };
    }
}
