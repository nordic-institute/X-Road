/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogUtilsTest {

    @Test
    void sanitize() {
        var normalLogEntry = "2024-02-15T12:21:06.997+02:00  "
                + "INFO ee.ria.xroad.common.util.LogUtilsTest : System is ready";
        var fakeLogEntry = "2024-02-15T12:21:06.997+02:00  "
                + "INFO ee.ria.xroad.common.util.LogUtilsTest : Payment of $1000 was made";

        var fakeLogData = "to receive $1000";

        assertThat(LogUtils.sanitize(normalLogEntry)).isEqualTo(normalLogEntry);

        assertThat(LogUtils.sanitize(normalLogEntry + "\n" + fakeLogEntry))
                .isEqualTo(normalLogEntry + "_" + fakeLogEntry);

        assertThat(LogUtils.sanitize(normalLogEntry + "\n\r" + fakeLogEntry))
                .isEqualTo(normalLogEntry + "__" + fakeLogEntry);

        assertThat(LogUtils.sanitize(normalLogEntry + "\r" + fakeLogEntry))
                .isEqualTo(normalLogEntry + "_" + fakeLogEntry);

        assertThat(LogUtils.sanitize(normalLogEntry + "\t" + fakeLogData))
                .isEqualTo(normalLogEntry + "_" + fakeLogData);
    }
}
