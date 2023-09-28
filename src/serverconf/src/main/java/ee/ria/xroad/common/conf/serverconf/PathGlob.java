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
package ee.ria.xroad.common.conf.serverconf;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

/**
 * Helper class to compile a glob patter to equivalent regular expression
 */
public final class PathGlob {
    private static final String REGEX_META = ".^$+{[]|()";
    public static final int MAXIMUM_CACHE_SIZE = 1000;

    private static final Cache<String, Pattern> PATTERN_CACHE =
            CacheBuilder.newBuilder().maximumSize(MAXIMUM_CACHE_SIZE).build();

    private PathGlob() {
        // Utility class
    }

    /**
     * Compiles a path glob pattern to equivalent regular expression. The compiled globs are cached for fast
     * subsequent use.
     *
     * <br/>
     * In a path glob pattern, '*' matches zero or more characters in a path segment (any char except '/'),
     * and '**' matches any character (zero or more).<br/>
     * For example:
     * <ul>
     * <li>/foo/*&#47;bar/ matches /foo/zyggy/bar but not /foo/zyggy/quux/bar</li>
     * <li>/foo/**&#47bar matches /foo/whatever//bar and /foo/zyggybar</li>
     * </ul>
     * Note that '..', '.', and percent-encoded characters are not treated specially by the returned Pattern.
     * @param glob glob pattern to compile
     * @return compiled regular expression
     * @see Pattern
     */
    public static Pattern compile(String glob) {
        try {
            return PATTERN_CACHE.get(glob, () -> toPattern(glob));
        } catch (ExecutionException e) {
            //should not happen since toPattern does not throw checked exceptions
            throw new IllegalStateException("Unable to compile", e.getCause());
        }
    }

    /**
     * Convenience method, equivalent of calling PathGlob.compile(glob).matcher(path).matches();
     */
    public static boolean matches(String glob, String path) {
        return compile(glob).matcher(path).matches();
    }

    @SuppressWarnings({"squid:S3776", "checkstyle:avoidnestedblocks"})
    private static Pattern toPattern(String glob) {
        StringBuilder b = new StringBuilder(glob.length());
        b.append("^");
        Cursor cursor = new Cursor(glob);
        while (cursor.hasNext()) {
            final char ch = cursor.next();
            switch (ch) {
                case '\\': {
                    final int lookup = cursor.peek();
                    if (lookup == '*' || lookup == '\\') {
                        b.append('\\');
                        b.append((char)lookup);
                        cursor.skip();
                    } else {
                        b.append("\\\\");
                    }
                    break;
                }
                case '*':
                    if (cursor.peek() == '*') {
                        b.append(".*");
                        do {
                            cursor.skip();
                        } while (cursor.peek() == '*');
                    } else {
                        b.append("[^/]*+");
                    }
                    break;
                default:
                    if (REGEX_META.indexOf(ch) != -1) {
                        b.append("\\");
                    }
                    b.append(ch);
            }
        }
        b.append("$");
        return Pattern.compile(b.toString());
    }

    private static final class Cursor {
        private final String arg;

        private final int lastPost;
        private int pos = -1;

        Cursor(String arg) {
            this.arg = arg;
            this.lastPost = arg.length() - 1;
        }

        boolean hasNext() {
            return pos < lastPost;
        }

        char next() {
            pos++;
            return arg.charAt(pos);
        }

        void skip() {
            pos++;
        }

        int peek() {
            if (pos < lastPost) {
                return arg.charAt(pos + 1);
            }
            return -1;
        }

    }
}
