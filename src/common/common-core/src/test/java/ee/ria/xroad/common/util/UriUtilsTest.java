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
package ee.ria.xroad.common.util;

import com.google.common.net.UrlEscapers;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import static ee.ria.xroad.common.util.UriUtils.uriPathPercentDecode;
import static ee.ria.xroad.common.util.UriUtils.uriSegmentPercentDecode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * Unit tests for uri path segment percent decoding
 */
@Slf4j
public class UriUtilsTest {

    @Test
    public void shouldAcceptSafeChars() {
        String safeChars = "!$&'()*+,;=:@-._~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        assertEquals(safeChars, uriSegmentPercentDecode(safeChars));
    }

    @Test
    public void shouldAcceptEmpty() {
        assertEquals("", uriSegmentPercentDecode(""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidChars() {
        uriSegmentPercentDecode("this/is/not a valid segment");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectInvalidEncoding() {
        uriSegmentPercentDecode("%XY");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectIncompleteEncoding() {
        uriSegmentPercentDecode("Test%");
    }

    @Test
    public void shouldPercentDecode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Character.MAX_CODE_POINT; i++) {
            if (i < 256 || Character.isAlphabetic(i) || Character.isDigit(i)) {
                b.appendCodePoint(i);
            }
        }
        final String expected = b.toString();
        final String escaped = UrlEscapers.urlPathSegmentEscaper().escape(expected);
        assertEquals(expected, uriSegmentPercentDecode(escaped));
    }

    @Test
    public void shouldKeepPathSeparator() {
        assertEquals("/foo/bar/zy%2Dggy", uriPathPercentDecode("/foo/bar/zy%2dggy", true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldFailIfPathSeparatorPresent() {
        uriPathPercentDecode("zy%2dggy/", false);
    }

    @Test
    public void shouldSuccessfullyNormalizeTraversalPaths() {
        assertEquals("/a/b/c", UriUtils.decodeAndNormalize("/a/b/c"));
        assertEquals("/a/b/c", UriUtils.decodeAndNormalize("/a/b/./c"));
        assertEquals("/a/b/c", UriUtils.decodeAndNormalize("/a/b/d/../c"));
        assertEquals("/c", UriUtils.decodeAndNormalize("/a/b/../../c"));
        //Encoded variants
        assertEquals("/a/b/c", UriUtils.decodeAndNormalize("/a/b/%2e/c"));
        assertEquals("/a/b/c", UriUtils.decodeAndNormalize("/a/b/d/%2e%2e/c"));
        assertEquals("/c", UriUtils.decodeAndNormalize("/a/b/%2e%2e/%2e%2e/c"));
        assertEquals("/../c/d", UriUtils.decodeAndNormalize("/a/b/../../%2e%2e/c/d"));
    }

    @Test
    public void shouldRejectEncodedQueryDelimiter() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/b%3Fc"));
    }

    @Test
    public void shouldRejectEncodedFragmentDelimiter() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/b%23c"));
    }

    @Test
    public void shouldRejectEncodedSlashLeadingToEmptySegment() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/%2F../a"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a%2F%2Fb"));
    }

    @Test
    public void shouldRejectLiteralEmptySegment() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("//a/b"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a//b"));
    }

    @Test
    public void shouldRejectDotSegmentWithParameters() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/.;x/../b"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/..;x/b"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/%2e;x/b"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/.%3Bx/b"));
    }

    @Test
    public void shouldRejectParameterOnlySegment() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/;x/b"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/api/test/;x/../../admin"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/%3Bx/b"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/b/;"));
    }
    @Test
    public void shouldRejectRepeatedPercentEncoding() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/foo%252Fbar"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/foo%25"));
    }

    @Test
    public void shouldRejectUnparsablePath() {
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/b c"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("/a/%XY"));
        assertThrows(UriPathRejectedException.class, () -> UriUtils.decodeAndNormalize("a:b"));
    }

    @Test
    public void shouldAcceptOrdinaryPathsWithEncodedCharacters() {
        assertEquals("/a/b;c", UriUtils.decodeAndNormalize("/a/b%3Bc"));
        assertEquals("/a/b;c/d", UriUtils.decodeAndNormalize("/a/b%3Bc/d"));
        assertEquals("/a/b;c", UriUtils.decodeAndNormalize("/a/b;c"));
        assertEquals("/a/bc", UriUtils.decodeAndNormalize("/a/%62c"));
        assertEquals("/a/b-c", UriUtils.decodeAndNormalize("/a/b%2dc"));
        assertEquals("/b", UriUtils.decodeAndNormalize("/a/../b"));
        assertEquals("/a/", UriUtils.decodeAndNormalize("/a/"));
        assertEquals("", UriUtils.decodeAndNormalize(""));
    }

}
