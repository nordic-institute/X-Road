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
package org.niis.xroad.restapi.service;

import org.niis.xroad.restapi.openapi.model.ConfigurablePropertyRestartPolicy;

import java.util.List;

/**
 * What an administrator restarts for a change of a configurable property to take effect.
 *
 * @param policy   the kind of restart instruction
 * @param services the names to restart, non-empty only for {@link ConfigurablePropertyRestartPolicy#SERVICES}
 */
public record RestartTarget(ConfigurablePropertyRestartPolicy policy, List<String> services) {

    public RestartTarget {
        var named = policy == ConfigurablePropertyRestartPolicy.SERVICES;
        if (named == services.isEmpty()) {
            throw new IllegalArgumentException(
                    "Restart policy %s requires %s service names".formatted(policy, named ? "non-empty" : "no"));
        }
    }

    public static RestartTarget of(String... names) {
        return new RestartTarget(ConfigurablePropertyRestartPolicy.SERVICES, List.of(names));
    }

    public static RestartTarget allServices() {
        return new RestartTarget(ConfigurablePropertyRestartPolicy.ALL_SERVICES, List.of());
    }

    public static RestartTarget nextRun() {
        return new RestartTarget(ConfigurablePropertyRestartPolicy.NEXT_RUN, List.of());
    }
}
