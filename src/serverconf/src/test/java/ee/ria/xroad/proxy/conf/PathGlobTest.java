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
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.conf.serverconf.PathGlob;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;

/**
 * PathGlob Unit Tests
 */
@RunWith(Parameterized.class)
public class PathGlobTest {

    /**
     * test data
     * glob, string, expected result
     */
    @Parameterized.Parameters(name = "{index}: <{0}> matches <{1}> is {2}")
    public static List<Object[]> params() {
        return Arrays.asList(new Object[][] {
                {"**", "", true},
                {"***", "match/anything/*", true},
                {"**", "/a/b/c", true},
                {"/*/", "/a/", true},
                {"/*/", "/a/b/", false},
                {"", "", true},
                {"", " ", false},
                {".^$+{[]|()", ".^$+{[]|()", true},
                {"**/bar/**", "a/b/c/bar/e/f", true},
                {"**/bar/**", "a/b/c/foo/e/f", false},
                {"**\\*", "/what/ever*", true},
                {"**\\*", "/what/ever!", false},
                {"simple", "simple", true},
                {"simple", "prefix.simple.suffix", false},
                {"**/*/**", "something/bar/something/else", true},
                {"**/*/**", "/bar/", true},
                {"**/*/**", "//", true},
                {"**/*/**", "something/else", false},
                //escaping non-special has no effect
                {"\\A", "\\A", true},
                //escaping special
                {"\\*", "*", true},
                //escaped special is really escaped
                {"\\*", "\\*", false},
                {"\\\\", "\\", true},
                {"aa\\", "aa\\", true},
        });
    }

    @Parameterized.Parameter(0)
    public String glob;

    @Parameterized.Parameter(1)
    public String path;

    @Parameterized.Parameter(2)
    public Boolean expected;

    @Test
    public void testGlobCompiler() {
        final Pattern pattern = PathGlob.compile(glob);
        assertEquals(expected, pattern.matcher(path).matches());
    }

}
