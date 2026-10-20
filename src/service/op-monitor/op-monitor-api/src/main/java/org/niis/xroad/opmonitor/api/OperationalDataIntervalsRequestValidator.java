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
package org.niis.xroad.opmonitor.api;

import java.util.concurrent.TimeUnit;

/**
 * Limits for operational data requests that are divided into time intervals.
 */
public final class OperationalDataIntervalsRequestValidator {
    public static final int MIN_INTERVAL_MINUTES = 1;
    /** The largest interval whose length in milliseconds still fits the 32-bit arithmetic of the database query. */
    public static final int MAX_INTERVAL_MINUTES = 35_791;
    public static final long MAX_BUCKETS = 100_000;

    private OperationalDataIntervalsRequestValidator() {
    }

    /**
     * Number of time buckets the interval query produces for the request.
     * The interval must already be within the allowed range.
     */
    public static long bucketCount(long recordsFrom, long recordsTo, int intervalInMinutes) {
        long intervalMillis = TimeUnit.MINUTES.toMillis(intervalInMinutes);
        return Math.max(0, Math.floorDiv(recordsTo, intervalMillis) - Math.floorDiv(recordsFrom, intervalMillis) + 2);
    }

    /**
     * Rejects a request whose interval, time range or bucket count is out of range.
     *
     * @throws IllegalArgumentException if the interval, the time range or the resulting bucket count is out of range
     */
    public static void validate(long recordsFrom, long recordsTo, int intervalInMinutes) {
        if (intervalInMinutes < MIN_INTERVAL_MINUTES || intervalInMinutes > MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("Interval must be between %d and %d minutes, was %d"
                    .formatted(MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES, intervalInMinutes));
        }
        if (recordsTo < recordsFrom) {
            throw new IllegalArgumentException("Records end time %d is before start time %d".formatted(recordsTo, recordsFrom));
        }
        long buckets = bucketCount(recordsFrom, recordsTo, intervalInMinutes);
        if (buckets > MAX_BUCKETS) {
            throw new IllegalArgumentException("Request produces %d time buckets, at most %d are allowed"
                    .formatted(buckets, MAX_BUCKETS));
        }
    }
}
