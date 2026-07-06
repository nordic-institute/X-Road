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
package org.niis.xroad.proxy.core.configuration;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * Proxy health-check configuration (@ConfigMapping at prefix "xroad.proxy.health-check").
 * <p>
 * Exposes two independent {@link TtlGroup} sub-groups: {@link #authKey()} and {@link #hsm()}.
 * Each group tunes success-ttl / error-ttl / max-error-ttl / backoff-multiplier / timeout
 * independently. Defaults on both groups are identical:
 * success-ttl=2s, error-ttl=5s, max-error-ttl=30s, backoff-multiplier=2, timeout=5s.
 */
@ConfigMapping(prefix = "xroad.proxy.health-check")
public interface ProxyHealthCheckProperties {

    /**
     * TTL/backoff/timeout tunables for the AuthKey OCSP readiness check.
     * Defaults identical to {@link #hsm()} on day one — tune independently.
     */
    @WithName("auth-key")
    TtlGroup authKey();

    /**
     * TTL/backoff/timeout tunables for the HSM operational readiness check.
     * Defaults identical to {@link #authKey()} on day one — tune independently.
     */
    @WithName("hsm")
    TtlGroup hsm();

    /**
     * Per-check TTL / backoff / timeout group.
     * <p>
     * Each group carries its own full default set: success-ttl=2s, error-ttl=5s,
     * max-error-ttl=30s, backoff-multiplier=2, timeout=5s. No inheritance/fallback
     * between groups — operators tune each group independently.
     */
    interface TtlGroup {

        /** Cache duration for an OK response. Default: 2s. */
        @WithName("success-ttl")
        @WithDefault("2s")
        Duration successTtl();

        /**
         * Initial cache duration for a non-OK response, before exponential backoff
         * kicks in. Default: 5s.
         */
        @WithName("error-ttl")
        @WithDefault("5s")
        Duration errorTtl();

        /** Ceiling for the error-TTL after repeated failures. Default: 30s. */
        @WithName("max-error-ttl")
        @WithDefault("30s")
        Duration maxErrorTtl();

        /**
         * Multiplier applied to the current error-TTL on each consecutive failure.
         * Default: 2 (5s -&gt; 10s -&gt; 20s -&gt; 30s cap).
         */
        @WithName("backoff-multiplier")
        @WithDefault("2")
        int backoffMultiplier();

        /** Per-call timeout bound applied by {@code TimedHealthCheck}. Default: 5s. */
        @WithName("timeout")
        @WithDefault("5s")
        Duration timeout();
    }
}
