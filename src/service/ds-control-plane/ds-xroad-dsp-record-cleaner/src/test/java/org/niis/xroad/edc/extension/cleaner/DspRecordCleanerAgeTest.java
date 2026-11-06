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
package org.niis.xroad.edc.extension.cleaner;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A data-flow state row that is deleted can be recreated by a start signal still being retried for its flow, so
 * the cleaner refuses an age at or below the policy monitor period plus the signal retry window.
 */
class DspRecordCleanerAgeTest {

    @Test
    void minimumAgeIsThePolicyMonitorPeriodPlusTheSignalRetryWindow() {
        assertThat(DspRecordCleaner.MINIMUM_AGE)
                .isEqualTo(DspRecordCleaner.POLICY_MONITOR_PERIOD.plus(DspRecordCleaner.SIGNAL_RETRY_WINDOW))
                .isGreaterThan(Duration.ofHours(1));
    }

    @Test
    void ageAtOrBelowTheMinimumIsRejected() {
        var minimum = DspRecordCleaner.MINIMUM_AGE;
        var belowMinimum = Duration.ofHours(1);

        assertThatThrownBy(() -> DspRecordCleaner.requireValidAge(minimum))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DspRecordCleaner.requireValidAge(belowMinimum))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ageAboveTheMinimumIsAccepted() {
        var age = DspRecordCleaner.MINIMUM_AGE.plusSeconds(1);

        assertThat(DspRecordCleaner.requireValidAge(age)).isEqualTo(age);
    }

    @Test
    void nonPositiveIntervalIsRejected() {
        var zero = Duration.ZERO;
        var negative = Duration.ofMinutes(-1);

        assertThatThrownBy(() -> DspRecordCleaner.requireValidInterval(zero))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DspRecordCleaner.requireValidInterval(negative))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positiveIntervalIsAccepted() {
        var interval = Duration.ofMillis(1);

        assertThat(DspRecordCleaner.requireValidInterval(interval)).isEqualTo(interval);
    }
}
