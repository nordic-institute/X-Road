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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_RETRY_DELAY_INITIAL;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_RETRY_DELAY_MAXIMUM;

class RetryDelayTest {

    private final RecordingMonitor monitor = new RecordingMonitor();

    @Test
    void shouldDoubleTheDelayUpToTheMaximumNumberOfDoublings() {
        var delay = new RetryDelay(Duration.ofSeconds(1), Duration.ofSeconds(10), 2);

        assertThat(delay.after(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(delay.after(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(delay.after(3)).isEqualTo(Duration.ofSeconds(4));
        assertThat(delay.after(4)).isEqualTo(Duration.ofSeconds(4));
    }

    @ParameterizedTest(name = "{0} doublings after {1} failures")
    @CsvSource({
            "-1, 1, 1", "-1, 2147483647, 1",
            "62, 1, 1", "62, 63, 10", "62, 2147483647, 10",
            "100, 101, 10", "100, 2147483647, 10",
            "2147483647, 2, 2", "2147483647, 63, 10", "2147483647, 2147483647, 10"})
    void shouldNeverThrowAndNeverExceedTheMaximum(int maximumDoublings, int failures, long expectedSeconds) {
        var delay = new RetryDelay(Duration.ofSeconds(1), Duration.ofSeconds(10), maximumDoublings);

        assertThat(delay.after(failures)).isEqualTo(Duration.ofSeconds(expectedSeconds));
    }

    @Test
    void shouldUseBothDefaultsAndWarnOnceWhenMaximumIsBelowInitial() {
        var config = config(Map.of(
                ISSUER_DATA_RETRY_DELAY_INITIAL.key(), "PT30S",
                ISSUER_DATA_RETRY_DELAY_MAXIMUM.key(), "PT10S"));

        var delay = RetryDelay.from(config, monitor);

        assertThat(delay.initial()).isEqualTo(ISSUER_DATA_RETRY_DELAY_INITIAL.convertedDefaultValue());
        assertThat(delay.maximum()).isEqualTo(ISSUER_DATA_RETRY_DELAY_MAXIMUM.convertedDefaultValue());
        assertThat(monitor.warnings()).singleElement().satisfies(warning -> assertThat(warning)
                .contains(ISSUER_DATA_RETRY_DELAY_MAXIMUM.key(), ISSUER_DATA_RETRY_DELAY_INITIAL.key(), "PT30S", "PT10S"));
    }

    @Test
    void shouldAcceptEqualInitialAndMaximumWithoutWarning() {
        var config = config(Map.of(
                ISSUER_DATA_RETRY_DELAY_INITIAL.key(), "PT30S",
                ISSUER_DATA_RETRY_DELAY_MAXIMUM.key(), "PT30S"));

        var delay = RetryDelay.from(config, monitor);

        assertThat(delay.initial()).isEqualTo(Duration.ofSeconds(30));
        assertThat(delay.maximum()).isEqualTo(Duration.ofSeconds(30));
        assertThat(monitor.warnings()).isEmpty();
    }

    private static XRoadConfig config(Map<String, String> stored) {
        return XRoadConfigBuilder.create().register(DspConfigKeys.instance()).overrides(stored).build();
    }
}
