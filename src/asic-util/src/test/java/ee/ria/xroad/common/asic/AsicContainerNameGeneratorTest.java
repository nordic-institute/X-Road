/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.common.asic;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link AsicContainerNameGenerator}
 */
public class AsicContainerNameGeneratorTest {

    private static final String LONG_QUERY_ID = "2871039339481435975/%3Cuox+xmlns%3D%22http%3A%2F%2Fa.b%2F%22+xmlns"
            + "%3Axsi%3D%22http%3A%2F%2Fwww.w3.org%2F2001%2FXMLSchema-instance%22+xsi%3AschemaLocation%3D%22http%3A%2F%"
            + "2Fa.b%2F+http%3A%2F%2Flj8x2nig9nwry1gc2kw5maxtgkmcacy4lvdj2.burpcollaborator.net%2Fuox.xsd%22%3Euox%3C%2"
            + "Fuox%3E";
    private static final String SHORT_QUERY_ID = "myquery1234";
    private static final boolean QUERY_TYPE_REQUEST = false;
    private static final boolean QUERY_TYPE_RESPONSE = true;
    private static final int FILENAME_MAX = 255;

    @Test
    public void testGeneratedFilename() {
        AsicContainerNameGenerator nameGenerator = new AsicContainerNameGenerator();
        String s1 = nameGenerator.getArchiveFilename(LONG_QUERY_ID, QUERY_TYPE_REQUEST, Long.MAX_VALUE);
        assertTrue("The generated filename was too long", s1.length() <= FILENAME_MAX);
        assertTrue("The sequence was not present", s1.contains(Long.toString(Long.MAX_VALUE, 32)));
        String s2 = nameGenerator.getArchiveFilename(LONG_QUERY_ID, QUERY_TYPE_RESPONSE, Long.MAX_VALUE);
        assertTrue("The generated filename was too long", s2.length() <= FILENAME_MAX);
        String s3 = nameGenerator.getArchiveFilename(SHORT_QUERY_ID, QUERY_TYPE_REQUEST, Long.MAX_VALUE);
        assertTrue("The generated filename was too long", s3.length() <= FILENAME_MAX);
        String s4 = nameGenerator.getArchiveFilename(SHORT_QUERY_ID, QUERY_TYPE_RESPONSE, Long.MAX_VALUE);
        assertTrue("The generated filename was too long", s4.length() <= FILENAME_MAX);
    }
}
