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

package org.niis.xroad.signer.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.Map;

@ConfigMapping(prefix = "xroad.signer.autologin")
public interface SignerAutologinProperties {

    @WithDefault("false")
    boolean enabled();

    Retry retry();

    /**
     * Map of token configurations keyed by token ID.
     * Configuration format:
     * <pre>
     * xroad.signer.autologin.tokens.0.pin=secret123
     * xroad.signer.autologin.tokens.softtoken-1.pin=another-secret
     * </pre>
     * Or via environment variables:
     * <pre>
     * XROAD_SIGNER_AUTOLOGIN_TOKENS__0__PIN=secret123
     * XROAD_SIGNER_AUTOLOGIN_TOKENS__SOFTTOKEN_1__PIN=another-secret
     * </pre>
     *
     * @return map of token ID to TokenConfig
     */
    @WithName("tokens")
    Map<String, TokenConfig> tokens();

    interface TokenConfig {
        String pin();
    }

    interface Retry {
        String DEFAULT_RETRY_DELAY = "3S";
        String DEFAULT_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER = "1.0";
        String DEFAULT_RETRY_MAX_ATTEMPTS = "20";

        @WithDefault(DEFAULT_RETRY_DELAY)
        Duration retryDelay();

        @WithDefault(DEFAULT_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER)
        Double retryExponentialBackoffMultiplier();

        @WithDefault(DEFAULT_RETRY_MAX_ATTEMPTS)
        int retryMaxAttempts();
    }
}
