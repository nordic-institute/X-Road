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

package org.niis.xroad.edc.extension.assetaccess.agreement;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-instance set of agreement ids whose transfer was refused, each held for a fixed cooldown. The
 * agreement lookup skips an id while it is cooling down, so a consumer that is genuinely denied does not
 * spend a refused transfer on every acquisition. Entries expire on their own; expired ones are dropped
 * on access and on every insertion, and the set never holds more than {@value #MAX_ENTRIES} ids, the
 * one closest to expiry making room for a new one.
 */
public class RefusedAgreementCooldown {

    static final int MAX_ENTRIES = 1_000;

    private final Clock clock;
    private final Duration cooldown;
    private final Map<String, Instant> expiryByAgreementId = new ConcurrentHashMap<>();

    public RefusedAgreementCooldown(Clock clock, Duration cooldown) {
        if (cooldown.isZero() || cooldown.isNegative()) {
            throw new IllegalArgumentException("Refused agreement cooldown must be positive: " + cooldown);
        }
        this.clock = clock;
        this.cooldown = cooldown;
    }

    /** Starts (or restarts) the cooldown of the given agreement id. */
    public void markRefused(String agreementId) {
        var now = clock.instant();
        expiryByAgreementId.values().removeIf(expiry -> !expiry.isAfter(now));
        if (expiryByAgreementId.size() >= MAX_ENTRIES && !expiryByAgreementId.containsKey(agreementId)) {
            expiryByAgreementId.entrySet().stream()
                    .min(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                    .ifPresent(oldest -> expiryByAgreementId.remove(oldest.getKey()));
        }
        expiryByAgreementId.put(agreementId, now.plus(cooldown));
    }

    /** Whether the given agreement id was refused less than one cooldown ago. */
    public boolean isCoolingDown(String agreementId) {
        var expiry = expiryByAgreementId.get(agreementId);
        if (expiry == null) {
            return false;
        }
        if (expiry.isAfter(clock.instant())) {
            return true;
        }
        expiryByAgreementId.remove(agreementId, expiry);
        return false;
    }
}
