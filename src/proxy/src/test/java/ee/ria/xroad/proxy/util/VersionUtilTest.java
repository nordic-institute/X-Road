/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.proxy.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link VersionUtilTest}
 */
public class VersionUtilTest {

    @Test
    public void testParseVersion() {
        assertEquals("unknown", VersionUtil.parseVersion(""));
        assertEquals("unknown", VersionUtil.parseVersion("foobar"));
        assertEquals("unknown", VersionUtil.parseVersion("111"));
        assertEquals("unknown",
                VersionUtil.parseVersion("error: cannot open Packages index using db5 - Permission denied (13)\n"
                        + "error: cannot open Packages database in /var/lib/rpm\n"
                        + "error: cannot open Packages database in /var/lib/rpm\n"
                        + "package xroad-proxy is not installed"));
        assertEquals("6.19.0-1", VersionUtil.parseVersion("6.19.0-1"));
        assertEquals("6.19.0-0.20180709122743git861f417",
                VersionUtil.parseVersion("6.19.0-0.20180709122743git861f417"));
    }
}
