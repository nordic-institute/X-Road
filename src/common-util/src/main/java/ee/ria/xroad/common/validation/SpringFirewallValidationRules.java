/**
 * The MIT License
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

import com.google.common.base.CharMatcher;

/**
 * Encapsulates validation logic that is copied from Spring firewall internal methods and
 * variables
 */
public final class SpringFirewallValidationRules {
    private SpringFirewallValidationRules() {
    }

    private static final String FORBIDDEN_PERCENT = "%";

    private static final String FORBIDDEN_SEMICOLON = ";";

    private static final String FORBIDDEN_FORWARDSLASH = "/";

    private static final String FORBIDDEN_BACKSLASH = "\\";

    public static boolean containsPercent(String s) {
        return s.contains(FORBIDDEN_PERCENT);
    }

    public static boolean containsSemicolon(String s) {
        return s.contains(FORBIDDEN_SEMICOLON);
    }

    public static boolean containsForwardslash(String s) {
        return s.contains(FORBIDDEN_FORWARDSLASH);
    }

    public static boolean containsBackslash(String s) {
        return s.contains(FORBIDDEN_BACKSLASH);
    }

    public static boolean containsNonPrintable(String s) {
        return CharMatcher.javaIsoControl().matchesAnyOf(s);
    }

    /**
     * from {@link org.springframework.security.web.firewall.StrictHttpFirewall#isNormalized(String)}
     *
     * Checks whether a path is normalized (doesn't contain path traversal
     * sequences like "./", "/../" or "/.")
     *
     * @param path
     *            the path to test
     * @return true if the path doesn't contain any path-traversal character
     *         sequences.
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public static boolean isNormalized(String path) {
        if (path == null) {
            return true;
        }

        if (path.indexOf("//") > -1) {
            return false;
        }

        for (int j = path.length(); j > 0;) {
            int i = path.lastIndexOf('/', j - 1);
            int gap = j - i;

            if (gap == 2 && path.charAt(i + 1) == '.') {
                // ".", "/./" or "/."
                return false;
            } else if (gap == 3 && path.charAt(i + 1) == '.' && path.charAt(i + 2) == '.') {
                return false;
            }

            j = i;
        }

        return true;
    }
}
