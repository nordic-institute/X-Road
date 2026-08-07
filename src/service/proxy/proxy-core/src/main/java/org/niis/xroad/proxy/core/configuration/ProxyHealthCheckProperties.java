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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_AUTH_KEY_BACKOFF_MULTIPLIER;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_AUTH_KEY_ERROR_TTL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_AUTH_KEY_MAX_ERROR_TTL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_AUTH_KEY_SUCCESS_TTL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_AUTH_KEY_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_HSM_BACKOFF_MULTIPLIER;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_HSM_ERROR_TTL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_HSM_MAX_ERROR_TTL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_HSM_SUCCESS_TTL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_HSM_TIMEOUT;

/**
 * Proxy health-check configuration ({@code xroad.proxy.health-check.*}).
 * <p>
 * Exposes two independent {@link TtlGroup} sub-groups: {@link #authKey()} and {@link #hsm()}.
 */
@RequiredArgsConstructor
public class ProxyHealthCheckProperties {

    private final XRoadConfig xRoadConfig;

    /** @return TTL/backoff/timeout tunables for the AuthKey OCSP readiness check */
    public TtlGroup authKey() {
        return new TtlGroup(
                HEALTH_CHECK_AUTH_KEY_SUCCESS_TTL,
                HEALTH_CHECK_AUTH_KEY_ERROR_TTL,
                HEALTH_CHECK_AUTH_KEY_MAX_ERROR_TTL,
                HEALTH_CHECK_AUTH_KEY_BACKOFF_MULTIPLIER,
                HEALTH_CHECK_AUTH_KEY_TIMEOUT,
                xRoadConfig);
    }

    /** @return TTL/backoff/timeout tunables for the HSM operational readiness check */
    public TtlGroup hsm() {
        return new TtlGroup(
                HEALTH_CHECK_HSM_SUCCESS_TTL,
                HEALTH_CHECK_HSM_ERROR_TTL,
                HEALTH_CHECK_HSM_MAX_ERROR_TTL,
                HEALTH_CHECK_HSM_BACKOFF_MULTIPLIER,
                HEALTH_CHECK_HSM_TIMEOUT,
                xRoadConfig);
    }

    /** Per-check TTL / backoff / timeout group. */
    public record TtlGroup(
            ConfigKey<Duration> successTtlKey,
            ConfigKey<Duration> errorTtlKey,
            ConfigKey<Duration> maxErrorTtlKey,
            ConfigKey<Integer> backoffMultiplierKey,
            ConfigKey<Duration> timeoutKey,
            XRoadConfig xRoadConfig) {

        /** @return cache duration for an OK response */
        public Duration successTtl() {
            return xRoadConfig.value(successTtlKey);
        }

        /** @return initial cache duration for a non-OK response */
        public Duration errorTtl() {
            return xRoadConfig.value(errorTtlKey);
        }

        /** @return ceiling for the error-TTL after repeated failures */
        public Duration maxErrorTtl() {
            return xRoadConfig.value(maxErrorTtlKey);
        }

        /** @return multiplier applied to error-TTL on each consecutive failure */
        public int backoffMultiplier() {
            return xRoadConfig.value(backoffMultiplierKey);
        }

        /** @return per-call timeout bound */
        public Duration timeout() {
            return xRoadConfig.value(timeoutKey);
        }
    }
}
