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
package org.niis.xroad.common.healthcheck;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * Shared health-check configuration (@ConfigMapping at prefix "xroad.health-check").
 * <p>
 * Currently exposes a single {@link Memory} sub-group for heap-memory liveness check
 * tuning.
 */
@ConfigMapping(prefix = "xroad.health-check")
public interface HealthCheckProperties {

    /**
     * Heap-memory liveness check tuning. See {@link Memory#thresholdPercent()}.
     */
    Memory memory();

    /**
     * Heap-memory liveness check tuning.
     * <p>
     * The threshold is intentionally {@link Optional} so an operator can disable the
     * check (always-UP) by configuring an empty value in any source — typically via a
     * YAML alias from a service-specific legacy key. When unset across all sources, the
     * default value of 95 is applied.
     */
    interface Memory {
        /**
         * Heap-usage percentage above which the liveness probe reports DOWN. When absent
         * (e.g. an explicit empty override), the bean reports UP unconditionally.
         * Default: 95.
         *
         * @return the configured threshold percent or empty when explicitly disabled.
         */
        @WithName("threshold-percent")
        @WithDefault("95")
        Optional<Integer> thresholdPercent();
    }
}
