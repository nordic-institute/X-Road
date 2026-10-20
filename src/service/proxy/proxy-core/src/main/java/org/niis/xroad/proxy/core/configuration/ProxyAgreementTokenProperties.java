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

package org.niis.xroad.proxy.core.configuration;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.agreementtoken.AgreementTokenProtocolProperties;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;
import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_AUDIENCE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_EXPIRY_LEEWAY;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_GRANT_LOOKUP_DEADLINE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_ISSUER;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_KEY_REFRESH_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_KEY_RETENTION;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_MAX_KEY_AGE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.AGREEMENT_TOKEN_TOKEN_TTL;

/**
 * Proxy agreement-token configuration ({@code xroad.proxy.agreement-token.*}). Both the minting data plane and
 * the verifying server proxy read {@link #issuer()}, {@link #audience()}, {@link #tokenTtl()} and
 * {@link #expiryLeeway()} from the same bean; {@link #keyRefreshInterval()} additionally bounds how often the
 * key material bean reloads its snapshot from the secret store, {@link #maxKeyAge()} sets when it rotates the
 * key on its own, {@link #keyRetention()} sets how long it keeps a superseded key before deleting it, and
 * {@link #grantLookupDeadline()} bounds the control-plane grant lookup on the data-flow start path.
 */
@RequiredArgsConstructor
public class ProxyAgreementTokenProperties implements AgreementTokenProtocolProperties {

    /** How many token lifetimes a superseded key is kept at least. */
    static final int MIN_RETENTION_TOKEN_TTL_MULTIPLE = 10;

    /** How many key refresh intervals, besides the token lifetime and expiry leeway, a superseded key is kept at least. */
    static final int MIN_RETENTION_REFRESH_INTERVAL_MULTIPLE = 3;

    private final XRoadConfig xRoadConfig;

    @Override
    public String issuer() {
        return xRoadConfig.value(AGREEMENT_TOKEN_ISSUER);
    }

    @Override
    public String audience() {
        return xRoadConfig.value(AGREEMENT_TOKEN_AUDIENCE);
    }

    @Override
    public Duration tokenTtl() {
        return xRoadConfig.value(AGREEMENT_TOKEN_TOKEN_TTL);
    }

    @Override
    public Duration expiryLeeway() {
        return xRoadConfig.value(AGREEMENT_TOKEN_EXPIRY_LEEWAY);
    }

    /** @return how often the key-material bean retries construction or refreshes its cached snapshot */
    public Duration keyRefreshInterval() {
        return xRoadConfig.value(AGREEMENT_TOKEN_KEY_REFRESH_INTERVAL);
    }

    /** @return how old the newest signing key may get before it is rotated automatically; zero disables it */
    public Duration maxKeyAge() {
        return xRoadConfig.value(AGREEMENT_TOKEN_MAX_KEY_AGE);
    }

    /** @return how long a signing key is kept after it stops signing before it is deleted; zero keeps every key */
    public Duration keyRetention() {
        return xRoadConfig.value(AGREEMENT_TOKEN_KEY_RETENTION);
    }

    /** @return the deadline for the control-plane grant lookup on the data-flow start path */
    public Duration grantLookupDeadline() {
        return xRoadConfig.value(AGREEMENT_TOKEN_GRANT_LOOKUP_DEADLINE);
    }

    /**
     * @return the longest time since the last successful key refresh at which the signing key may still be used
     *         to mint, or empty when {@code key-retention} is zero and minting is never limited. A replica that
     *         cannot reach the secret store for longer than this stops minting, so no token it signs can outlive
     *         the key that signed it
     */
    public Optional<Duration> staleSigningKeyLimit() {
        var retention = keyRetention();
        if (retention.isZero()) {
            return Optional.empty();
        }
        return Optional.of(retention.minus(keyRefreshInterval().plus(tokenTtl()).plus(expiryLeeway())));
    }

    /**
     * Checks the key lifecycle settings against each other.
     *
     * A superseded key can still back tokens for one key refresh interval after it stops signing on the
     * slowest replica, plus the token lifetime and expiry leeway. A replica stops minting once its last
     * successful refresh is older than the retention minus that time (see {@link #staleSigningKeyLimit()}),
     * so the retention must leave that limit at least two key refresh intervals: a healthy replica, or one
     * that misses a single refresh, then keeps minting.
     *
     * @throws IllegalStateException when a non-zero {@code max-key-age} is not longer than the key refresh
     *                               interval, or a non-zero {@code key-retention} is shorter than ten token
     *                               lifetimes or than three key refresh intervals plus the token lifetime and
     *                               expiry leeway
     */
    public void validateKeyLifecycle() {
        var refreshInterval = keyRefreshInterval();
        var maxKeyAge = maxKeyAge();
        if (!maxKeyAge.isZero() && maxKeyAge.compareTo(refreshInterval) <= 0) {
            throw new IllegalStateException(("Invalid agreement-token configuration: max-key-age (%s) must be 0 or longer "
                    + "than key-refresh-interval (%s)").formatted(maxKeyAge, refreshInterval));
        }
        var retention = keyRetention();
        if (retention.isZero()) {
            return;
        }
        var minimumRetention = tokenTtl().multipliedBy(MIN_RETENTION_TOKEN_TTL_MULTIPLE);
        if (retention.compareTo(minimumRetention) < 0) {
            throw new IllegalStateException(("Invalid agreement-token configuration: key-retention (%s) must be 0 or at least "
                    + "%d times token-ttl (%s), which is %s").formatted(
                    retention, MIN_RETENTION_TOKEN_TTL_MULTIPLE, tokenTtl(), minimumRetention));
        }
        var minimumForMinting = refreshInterval.multipliedBy(MIN_RETENTION_REFRESH_INTERVAL_MULTIPLE)
                .plus(tokenTtl()).plus(expiryLeeway());
        if (retention.compareTo(minimumForMinting) < 0) {
            throw new IllegalStateException(("Invalid agreement-token configuration: key-retention (%s) must be 0 or at least "
                    + "%d times key-refresh-interval (%s) plus token-ttl (%s) plus expiry-leeway (%s), which is %s").formatted(
                    retention, MIN_RETENTION_REFRESH_INTERVAL_MULTIPLE, refreshInterval, tokenTtl(), expiryLeeway(),
                    minimumForMinting));
        }
    }
}
