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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;

import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_RETRY_DELAY_INITIAL;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_RETRY_DELAY_MAXIMUM;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_RETRY_DELAY_MAXIMUM_DOUBLINGS;

/**
 * The delay before the next download of a key after a failed refresh: the initial delay, doubled after each further
 * failure up to the maximum number of doublings, and never longer than the maximum delay.
 *
 * @param initial          the delay after the first failure
 * @param maximum          the longest delay
 * @param maximumDoublings the most times the delay doubles; a negative value counts as 0
 */
public record RetryDelay(Duration initial, Duration maximum, int maximumDoublings) {

    /**
     * Reads the delay from the settings. A maximum below the initial delay cannot hold, so both settings fall back
     * to their defaults and one warning names both.
     */
    public static RetryDelay from(XRoadConfig config, Monitor monitor) {
        var initial = config.value(ISSUER_DATA_RETRY_DELAY_INITIAL);
        var maximum = config.value(ISSUER_DATA_RETRY_DELAY_MAXIMUM);
        var maximumDoublings = config.value(ISSUER_DATA_RETRY_DELAY_MAXIMUM_DOUBLINGS);
        if (maximum.compareTo(initial) < 0) {
            var defaultInitial = ISSUER_DATA_RETRY_DELAY_INITIAL.convertedDefaultValue();
            var defaultMaximum = ISSUER_DATA_RETRY_DELAY_MAXIMUM.convertedDefaultValue();
            monitor.warning("Issuer data retry delay: %s (%s) is lower than %s (%s), using the defaults %s and %s".formatted(
                    ISSUER_DATA_RETRY_DELAY_MAXIMUM.key(), maximum, ISSUER_DATA_RETRY_DELAY_INITIAL.key(), initial,
                    defaultMaximum, defaultInitial));
            return new RetryDelay(defaultInitial, defaultMaximum, maximumDoublings);
        }
        return new RetryDelay(initial, maximum, maximumDoublings);
    }

    /**
     * Never returns more than the maximum, and does not throw for any number of doublings and any number of failures,
     * when the initial delay is not negative and the maximum is at most {@code Long.MAX_VALUE / 2} seconds. A
     * negative initial delay or a larger maximum can overflow a {@link Duration} and throw an
     * {@link ArithmeticException}.
     *
     * @param failures the failed refreshes in a row, at least 1
     * @return the delay after the last failure
     */
    public Duration after(int failures) {
        var doublings = Math.max(0, Math.min(failures - 1, maximumDoublings));
        var delay = initial;
        for (var done = 0; done < doublings && !delay.isZero() && delay.compareTo(maximum) < 0; done++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maximum) > 0 ? maximum : delay;
    }
}
