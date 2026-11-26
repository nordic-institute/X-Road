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
import org.niis.xroad.common.properties.config.impl.XRoadConfigBuilder;
import org.niis.xroad.common.properties.config.keys.DspConfigKeys;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_DOWNLOAD_RETRY_SECONDS;

class DownloadRetryTest {

    @Test
    void shouldReturnEntryNAfterFailureNAndRepeatTheLastEntryKeepingDuplicates() {
        var retry = new DownloadRetry(List.of(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(10)));

        assertThat(retry.after(1)).isEqualTo(Duration.ofSeconds(5));
        assertThat(retry.after(2)).isEqualTo(Duration.ofSeconds(5));
        assertThat(retry.after(3)).isEqualTo(Duration.ofSeconds(10));
        assertThat(retry.after(4)).isEqualTo(Duration.ofSeconds(10));
        assertThat(retry.after(Integer.MAX_VALUE)).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldConvertTheConfiguredEntriesFromSeconds() {
        var config = XRoadConfigBuilder.create().register(DspConfigKeys.instance())
                .overrides(Map.of(ISSUER_DATA_DOWNLOAD_RETRY_SECONDS.key(), "5,5,90")).build();

        var retry = DownloadRetry.from(config);

        assertThat(retry.delays()).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(90));
    }
}
