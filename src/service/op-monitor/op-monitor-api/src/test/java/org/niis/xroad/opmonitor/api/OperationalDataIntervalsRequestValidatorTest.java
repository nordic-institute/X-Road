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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.niis.xroad.opmonitor.api.OperationalDataIntervalsRequestValidator.MAX_BUCKETS;
import static org.niis.xroad.opmonitor.api.OperationalDataIntervalsRequestValidator.MAX_INTERVAL_MINUTES;
import static org.niis.xroad.opmonitor.api.OperationalDataIntervalsRequestValidator.bucketCount;
import static org.niis.xroad.opmonitor.api.OperationalDataIntervalsRequestValidator.validate;

class OperationalDataIntervalsRequestValidatorTest {
    private static final long MINUTE = 60_000L;
    private static final long FROM = 1_700_000_040_000L;
    private static final long UNALIGNED_FROM = FROM + 30_000L;

    @ParameterizedTest
    @CsvSource({
            "0, 0, 1, 2",
            "0, 59999, 1, 2",
            "0, 60000, 1, 3",
            "30000, 89999, 1, 3",
            "0, -1, 1, 1",
            "0, -60000, 1, 1",
            "0, -60001, 1, 0",
            "0, 5999880000, 1, 100000",
            "0, 5999940000, 1, 100001",
            "-90000, 30000, 1, 4",
            "0, 43200000000, 1440, 502"
    })
    void bucketCountMatchesTheQueryRowCount(long from, long to, int interval, long expected) {
        assertThat(bucketCount(from, to, interval)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 15, MAX_INTERVAL_MINUTES})
    void intervalInRangeIsAccepted(int interval) {
        assertThatCode(() -> validate(FROM, FROM + MINUTE * interval, interval)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, MAX_INTERVAL_MINUTES + 1, Integer.MAX_VALUE})
    void intervalOutOfRangeIsRejected(int interval) {
        assertThatThrownBy(() -> validate(FROM, FROM, interval)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void endEqualToStartIsAccepted() {
        assertThatCode(() -> validate(FROM, FROM, 1)).doesNotThrowAnyException();
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThatThrownBy(() -> validate(FROM, FROM - 1, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maximumBucketCountIsAcceptedForAlignedRange() {
        assertThat(bucketCount(FROM, FROM + (MAX_BUCKETS - 2) * MINUTE, 1)).isEqualTo(MAX_BUCKETS);

        assertThatCode(() -> validate(FROM, FROM + (MAX_BUCKETS - 2) * MINUTE, 1)).doesNotThrowAnyException();
    }

    @Test
    void oneBucketOverTheMaximumIsRejectedForAlignedRange() {
        assertThatThrownBy(() -> validate(FROM, FROM + (MAX_BUCKETS - 1) * MINUTE, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void maximumBucketCountIsAcceptedForUnalignedRange() {
        long to = FROM + (MAX_BUCKETS - 1) * MINUTE - 1;
        assertThat(bucketCount(UNALIGNED_FROM, to, 1)).isEqualTo(MAX_BUCKETS);

        assertThatCode(() -> validate(UNALIGNED_FROM, to, 1)).doesNotThrowAnyException();
    }

    @Test
    void oneBucketOverTheMaximumIsRejectedForUnalignedRange() {
        long to = FROM + (MAX_BUCKETS - 1) * MINUTE;
        assertThat(bucketCount(UNALIGNED_FROM, to, 1)).isEqualTo(MAX_BUCKETS + 1);

        assertThatThrownBy(() -> validate(UNALIGNED_FROM, to, 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
