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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * Operator settings for tests that build a {@link LastGoodSource} directly: a fixed retry delay list and reset time,
 * and the limit and download wait that each test chooses.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TestSettings {

    static final List<Duration> DELAYS = List.of(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(15),
            Duration.ofSeconds(20), Duration.ofSeconds(30), Duration.ofSeconds(60));
    static final Duration LONGEST_DELAY = Duration.ofSeconds(60);
    static final Duration RESET_AFTER = Duration.ofMinutes(10);

    static IssuerDataSettings settings(long maximumEntries, Duration downloadWait) {
        return settings(maximumEntries, downloadWait, new DownloadRetry(DELAYS), RESET_AFTER);
    }

    static IssuerDataSettings settings(long maximumEntries, Duration downloadWait, DownloadRetry downloadRetry,
                                       Duration resetAfter) {
        return new IssuerDataSettings(maximumEntries, downloadWait, downloadRetry, resetAfter);
    }
}
