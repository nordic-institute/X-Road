/**
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

import java.nio.charset.StandardCharsets;

/**
 * URI utilities
 */
public final class UriUtils {

    private UriUtils() {
    }

    /**
     * Percent-decodes a URI segment to a string, assuming UTF-8 character set.
     * The allowed chars in a URI segment are defined as follows:
     * <pre>
     * segment       = *pchar
     * pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
     * unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
     * sub-delims    = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
     * pct-encoded   = "%" HEXDIG HEXDIG
     * </pre>
     * @see <a href="https://tools.ietf.org/html/rfc3986#section-3.3">RFC 3986</a>
     */
    public static String uriSegmentPercentDecode(final String src) {
        return uriPathPercentDecode(src, false);
    }

    /**
     * Percent-decodes a URI path, assuming UTF-8 character set; optionally allows a path separator ('/').
     * @param src            URI path to percent-decode
     * @param allowSeparator If true, path separators are allowed and any %2d ('/') escape sequence is preserved
     *                       (normalized to %2D) so that it is possible to distinguish literal '/' from an encoded one.
     *                       If false, unencoded path separators are not allowed (becomes a path segment decoder).
     * @see #uriSegmentPercentDecode(String)
     */
    @SuppressWarnings({"squid:S3776", "checkstyle:magicnumber"})
    public static String uriPathPercentDecode(final String src, final boolean allowSeparator) {
        final int length = src.length();
        if (length == 0) {
            return src;
        }

        /* the result can not be longer than the source:
           - %XY -> one byte (-2) or kept as is (allowSlash, +-0)
           - safe chars copied as is (+-0)
           - other chars rejected */
        final byte[] buf = new byte[length];
        boolean changed = false;
        int i = 0;
        int pos = 0;

        while (i < length) {
            char ch = src.charAt(i);

            if (ch == '/' && allowSeparator) {
                buf[pos++] = (byte)ch;
                i++;
            } else if (ch == '%') {
                if (i + 2 >= length) {
                    throw new IllegalArgumentException("Incomplete percent encoding");
                }
                final int d1 = hexdigit(src.charAt(i + 1));
                final int d2 = hexdigit(src.charAt(i + 2));
                if (d1 == -1 || d2 == -1) {
                    throw new IllegalArgumentException("Invalid percent encoding");
                }
                if (allowSeparator && d1 == 0x02 && d2 == 0x0D) {
                    buf[pos++] = '%';
                    buf[pos++] = '2';
                    buf[pos++] = 'D';
                } else {
                    buf[pos++] = (byte)((d1 << 4) + d2);
                }
                i += 3;
                changed = true;
            } else if (safeChar(ch)) {
                buf[pos++] = (byte)ch;
                i++;
            } else {
                throw new IllegalArgumentException("Invalid character in path segment");
            }
        }
        return (changed ? new String(buf, 0, pos, StandardCharsets.UTF_8) : src);
    }

    private static final char[] SAFE_DELIMS = {
            // part of unreserved
            '-', '.', '_', '~',
            // sub-delims
            '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '=',
            // part of pchar
            ':', '@'
    };

    @SuppressWarnings("checkstyle:magicnumber")
    private static boolean safeChar(final char ch) {
        if (ch < 0x21 || ch > 0x7E) return false;
        if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) return true;
        for (final char c : SAFE_DELIMS) {
            if (ch == c) return true;
        }
        return false;
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private static int hexdigit(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
        if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
        return -1;
    }
}
