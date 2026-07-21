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
package org.niis.xroad.signer.core.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;
import java.util.Map;

import static org.niis.xroad.signer.core.config.SignerConfigKeys.AUTOLOGIN_ENABLED;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.AUTOLOGIN_RETRY_DELAY;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.AUTOLOGIN_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.AUTOLOGIN_RETRY_MAX_ATTEMPTS;
import static org.niis.xroad.signer.core.config.SignerConfigKeys.AUTOLOGIN_RETRY_TIMEOUT;

/**
 * Autologin scalar properties ({@code xroad.signer.autologin.*}).
 *
 * <p>Secret token PINs remain on SmallRye — see {@link Tokens}.
 */
@RequiredArgsConstructor
public class SignerAutologinProperties {

    private final XRoadConfig xRoadConfig;
    private final Retry retry;
    private final Tokens tokens;

    /** @param xRoadConfig DSL config; {@code tokens} is the SmallRye tokens-only mapping */
    public SignerAutologinProperties(XRoadConfig xRoadConfig, Tokens tokens) {
        this.xRoadConfig = xRoadConfig;
        this.tokens = tokens;
        this.retry = new Retry(xRoadConfig);
    }

    /** @return whether autologin is enabled */
    public boolean enabled() {
        return xRoadConfig.value(AUTOLOGIN_ENABLED);
    }

    /** @return retry configuration */
    public Retry retry() {
        return retry;
    }

    /** @return token PIN map (from SmallRye, never migrated to DB layer) */
    public Map<String, TokenConfig> tokens() {
        return tokens.tokens();
    }

    /** Retry parameters for autologin ({@code xroad.signer.autologin.retry.*}). */
    @RequiredArgsConstructor
    public static class Retry {

        private final XRoadConfig xRoadConfig;

        /** @return delay between retry attempts */
        public Duration retryDelay() {
            return xRoadConfig.value(AUTOLOGIN_RETRY_DELAY);
        }

        /** @return exponential backoff multiplier */
        public Double retryExponentialBackoffMultiplier() {
            return Double.parseDouble(xRoadConfig.value(AUTOLOGIN_RETRY_EXPONENTIAL_BACKOFF_MULTIPLIER));
        }

        /** @return maximum number of retry attempts */
        public int retryMaxAttempts() {
            return xRoadConfig.value(AUTOLOGIN_RETRY_MAX_ATTEMPTS);
        }

        /** @return total autologin timeout */
        public Duration retryTimeout() {
            return xRoadConfig.value(AUTOLOGIN_RETRY_TIMEOUT);
        }
    }

    /** Token PIN holder — remains on SmallRye so secrets stay in the env/file layer. */
    @ConfigMapping(prefix = "xroad.signer.autologin.tokens")
    public interface Tokens {
        /**
         * Map of token PINs keyed by token ID for initial insertion into OpenBao.
         * These PINs are stored into OpenBao on startup (if not already present).
         * The actual autologin process retrieves PINs from OpenBao, not from this configuration.
         * <p>
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
         */
        @WithParentName
        Map<String, TokenConfig> tokens();
    }

    /** Single-token PIN entry. */
    public interface TokenConfig {
        /** @return the token PIN */
        String pin();
    }
}
