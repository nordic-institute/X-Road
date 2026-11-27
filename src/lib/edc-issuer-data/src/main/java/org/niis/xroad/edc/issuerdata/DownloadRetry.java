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

import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.niis.xroad.common.properties.config.keys.DspConfigKeys.ISSUER_DATA_DOWNLOAD_RETRY_SECONDS;

/**
 * The delay before the next download of a key after a failed one: entry n is the wait after failure n, and the last
 * entry repeats.
 *
 * @param delays the waits, at least one
 */
public record DownloadRetry(List<Duration> delays) {

    /**
     * Reads the delays from the settings; the key validator has already checked every entry.
     */
    public static DownloadRetry from(XRoadConfig config) {
        return new DownloadRetry(Arrays.stream(config.value(ISSUER_DATA_DOWNLOAD_RETRY_SECONDS))
                .map(Integer::parseInt)
                .map(Duration::ofSeconds)
                .toList());
    }

    /**
     * @param failures the failed downloads in a row, at least 1
     * @return the delay after the last failure
     */
    public Duration after(int failures) {
        return delays.get(Math.clamp(failures, 1, delays.size()) - 1);
    }
}
