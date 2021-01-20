/**
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
package ee.ria.xroad.common.validation;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link XroadLogbackUtils}
 */
public class XroadLogbackUtilsTest {

    private static final Set DEFAULT_WHITELIST = new HashSet<Character>(Arrays.asList('\u0020'));
    private static final Set EXTENDED_WHITELIST = new HashSet<Character>(Arrays.asList('\u0020', '\n'));

    @Test
    public void testLoremIpsum() {
        final String testMsg = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        assertEquals(testMsg, XroadLogbackUtils.replaceLogForgingCharacters(testMsg,
                DEFAULT_WHITELIST));
    }

    @Test
    public void testNonAscii() {
        final String testMsg = "Jukolan talo, eteläisessä Hämeessä, seisoo erään mäen "
                + "pohjoisella rinteellä, liki Toukolan kylää.";
        assertEquals(testMsg, XroadLogbackUtils.replaceLogForgingCharacters(testMsg,
                DEFAULT_WHITELIST));
    }

    @Test
    public void testIllegalCharReplacement() {
        assertEquals("hello\\u0009world", XroadLogbackUtils.replaceLogForgingCharacters("hello\tworld",
                DEFAULT_WHITELIST));
        assertEquals("hello\\u000Aworld", XroadLogbackUtils.replaceLogForgingCharacters("hello\nworld",
                DEFAULT_WHITELIST));
        assertEquals("hello\\u000Dworld", XroadLogbackUtils.replaceLogForgingCharacters("hello\rworld",
                DEFAULT_WHITELIST));
        assertEquals("hello\\u000D\\u000Aworld",
                XroadLogbackUtils.replaceLogForgingCharacters("hello\r\nworld", DEFAULT_WHITELIST));
        assertEquals("hello\\u0085world",
                XroadLogbackUtils.replaceLogForgingCharacters("hello\u0085world", DEFAULT_WHITELIST));
        assertEquals("hello\\u008Dworld",
                XroadLogbackUtils.replaceLogForgingCharacters("hello\u008Dworld", DEFAULT_WHITELIST));
        assertEquals("hello\\uFEFFworld",
                XroadLogbackUtils.replaceLogForgingCharacters("hello\uFEFFworld", DEFAULT_WHITELIST));
        assertEquals("hello\\u200Bworld",
                XroadLogbackUtils.replaceLogForgingCharacters("hello\u200Bworld", DEFAULT_WHITELIST));
    }

    @Test
    public void testWhitelistOption() {
        assertEquals("hello world\\u000D\n",
                XroadLogbackUtils.replaceLogForgingCharacters("hello world\r\n", EXTENDED_WHITELIST));
    }
}
