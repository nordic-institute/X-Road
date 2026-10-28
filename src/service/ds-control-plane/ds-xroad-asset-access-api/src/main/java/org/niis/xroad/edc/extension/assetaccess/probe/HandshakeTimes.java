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

package org.niis.xroad.edc.extension.assetaccess.probe;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps an exponentially weighted moving average (EWMA) of the connection setup time of each provider Security
 * Server, keyed by counter-party address. A sample is the time from the first TCP connect attempt to the end of the
 * TLS handshake. The first sample is the average; every later sample moves it by the smoothing factor of the
 * distance to the new sample, so recent samples weigh more than old ones. State is held in memory for this process
 * only.
 */
final class HandshakeTimes {

    private final double smoothingFactor;
    private final Map<String, Double> averageNanos = new ConcurrentHashMap<>();

    /**
     * @param smoothingFactor weight of the newest sample, above 0 and at most 1
     */
    HandshakeTimes(double smoothingFactor) {
        this.smoothingFactor = smoothingFactor;
    }

    void record(String counterPartyAddress, Duration connectionSetupTime) {
        var sample = (double) connectionSetupTime.toNanos();
        averageNanos.merge(counterPartyAddress, sample, this::updatedAverage);
    }

    private double updatedAverage(double average, double latest) {
        return smoothingFactor * latest + (1 - smoothingFactor) * average;
    }

    void forget(String counterPartyAddress) {
        averageNanos.remove(counterPartyAddress);
    }

    Optional<Duration> average(String counterPartyAddress) {
        return Optional.ofNullable(averageNanos.get(counterPartyAddress)).map(nanos -> Duration.ofNanos(Math.round(nanos)));
    }
}
