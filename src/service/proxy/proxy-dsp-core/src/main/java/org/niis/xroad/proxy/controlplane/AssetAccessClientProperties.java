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
package org.niis.xroad.proxy.controlplane;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * Business configuration for DSP asset access requests.
 */
@ConfigMapping(prefix = "xroad.proxy.dsp")
public interface AssetAccessClientProperties {

    /**
     * The participant context ID used when calling the control plane asset access endpoint.
     *
     * <p>Per-SS local routing label that must match the {@code @id} used when seeding the
     * Control Plane {@code ParticipantContext} on this SS. Convention: equal to the SS hostname
     * (e.g. {@code xrd-ss0}, {@code xrd-ss1}). No default — must be set explicitly per SS via
     * local config so a misconfigured deployment fails fast at startup rather than silently
     * routing through a shared placeholder.
     *
     * @return participant context ID
     */
    @WithName("participant-context-id")
    String participantContextId();

    /**
     * The DSP protocol identifier for negotiation.
     *
     * @return protocol identifier
     */
    @WithDefault("dataspace-protocol-http:2025-1")
    String protocol();

    /**
     * Asset access response cache configuration.
     *
     * @return cache configuration
     */
    Cache cache();

    /**
     * Cache configuration for acquired asset access responses.
     */
    interface Cache {

        /**
         * Whether the cache is enabled. When disabled, every {@code acquireAssetAccess} call
         * results in a fresh gRPC round-trip to the control plane.
         *
         * @return {@code true} if caching is enabled
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Default TTL applied to a cached entry when the control plane response does not
         * carry an explicit {@code expiresAtEpochSeconds}.
         *
         * @return default TTL duration
         */
        @WithName("default-ttl")
        @WithDefault("PT5M")
        Duration defaultTtl();

        /**
         * Maximum number of entries kept in the cache. Once exceeded, entries are evicted
         * by Caffeine's size-based policy.
         *
         * @return maximum number of cache entries
         */
        @WithName("maximum-size")
        @WithDefault("10000")
        long maximumSize();
    }
}
