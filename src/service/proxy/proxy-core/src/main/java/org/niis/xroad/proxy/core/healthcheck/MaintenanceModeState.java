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
package org.niis.xroad.proxy.core.healthcheck;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Process-wide maintenance-mode flag shared by the legacy {@link HealthCheckPortImpl} Jetty
 * listener and the SmallRye {@code /q/health/ready} route filter. Single source of truth —
 * toggled via {@code AdminPort /maintenance}.
 */
@Slf4j
@ApplicationScoped
public class MaintenanceModeState {

    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    /**
     * Returns whether maintenance mode is currently active.
     *
     * @return {@code true} if the maintenance flag is set, {@code false} otherwise
     */
    public boolean isMaintenanceMode() {
        return maintenanceMode.get();
    }

    /**
     * Atomically sets the maintenance flag to {@code targetState} and returns a human-readable
     * transition message. The returned string format is preserved byte-for-byte from the legacy
     * {@code HealthCheckPortImpl.setMaintenanceMode} output because admin operators grep for it.
     *
     * @param targetState desired maintenance-mode value
     * @return transition message in the form {@code "Maintenance mode set: {old} => {new}"}
     */
    public String setMaintenanceMode(boolean targetState) {
        boolean oldValue = maintenanceMode.getAndSet(targetState);
        log.info("Maintenance mode set: {} => {}", oldValue, targetState);
        return "Maintenance mode set: "
                + oldValue
                + " => "
                + targetState;
    }
}
