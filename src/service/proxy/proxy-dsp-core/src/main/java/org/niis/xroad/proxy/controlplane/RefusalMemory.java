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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import jakarta.annotation.Nullable;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_ACCESS_REFUSED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_DATASET_NOT_FOUND;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_OFFERS_NOT_FOUND;

/**
 * Remembers for a short time that a provider refused a negotiation, so a retrying client does not start a new
 * negotiation, and a new refused agreement, with every call.
 *
 * <p>Only the refusals that end a negotiation are remembered: {@code DSP_ACCESS_REFUSED},
 * {@code DSP_DATASET_NOT_FOUND} and {@code DSP_OFFERS_NOT_FOUND}. Transfer failures, even refused ones, and
 * transport faults are never remembered. An entry is keyed like {@link AssetAccessCache}, so it is per caller,
 * asset and provider server, and it replays the code of the refusal it remembers.
 */
class RefusalMemory {

    static final Duration LIFETIME = Duration.ofSeconds(10);
    static final long MAXIMUM_SIZE = 10_000;

    private static final Set<ErrorCode> NEGOTIATION_REFUSALS =
            Set.of(DSP_ACCESS_REFUSED, DSP_DATASET_NOT_FOUND, DSP_OFFERS_NOT_FOUND);

    private final Cache<AssetAccessCache.CacheKey, String> refusals;

    RefusalMemory(Clock clock) {
        Ticker ticker = () -> TimeUnit.MILLISECONDS.toNanos(clock.millis());
        this.refusals = Caffeine.newBuilder()
                .maximumSize(MAXIMUM_SIZE)
                .expireAfterWrite(LIFETIME)
                .ticker(ticker)
                .build();
    }

    /**
     * Remembers {@code failure} under {@code key} when it is a negotiation refusal; any other failure, or a missing
     * key, is ignored.
     */
    void rememberIfRefusal(@Nullable AssetAccessCache.CacheKey key, RuntimeException failure) {
        if (key != null && failure instanceof XrdRuntimeException xrdFailure
                && NEGOTIATION_REFUSALS.stream().anyMatch(xrdFailure::isCausedBy)) {
            refusals.put(key, xrdFailure.getCode());
        }
    }

    /**
     * A fresh exception carrying the code of the remembered refusal, or empty when the key has none or it is older
     * than {@link #LIFETIME}.
     */
    Optional<XrdRuntimeException> recall(AssetAccessCache.CacheKey key) {
        return Optional.ofNullable(refusals.getIfPresent(key))
                .map(code -> XrdRuntimeException.systemException(ErrorCode.withCode(code))
                        .details("The provider refused this call less than %d seconds ago".formatted(LIFETIME.toSeconds()))
                        .build());
    }
}
