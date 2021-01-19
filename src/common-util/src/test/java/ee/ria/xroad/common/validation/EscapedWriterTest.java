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

import ee.ria.xroad.common.util.JsonUtils;

import com.google.gson.Gson;
import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link EscapedWriter}
 */
public class EscapedWriterTest {

    private Gson serializer = JsonUtils.getSerializer();
    private EscapedWriter escapedWriter = new EscapedWriter(new StringWriter());

    @Test
    public void testLoremIpsum() {
        final String testMsg = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        serializer.toJson(testMsg, escapedWriter);
        assertEquals("\"Lorem ipsum dolor sit amet, consectetur adipiscing elit.\"", escapedWriter.toString());
    }

    @Test
    public void testNonAscii() {
        final String testMsg = "Jukolan talo, eteläisessä Hämeessä, seisoo erään mäen "
                + "pohjoisella rinteellä, liki Toukolan kylää.";
        serializer.toJson(testMsg, escapedWriter);
        assertEquals("\"Jukolan talo, eteläisessä Hämeessä, seisoo erään mäen "
                + "pohjoisella rinteellä, liki Toukolan kylää.\"", escapedWriter.toString());
    }

    @Test
    public void testDoubleQuoteBackslash() {
        String testMsg = "Hello \"World\"";
        serializer.toJson(testMsg, escapedWriter);
        assertEquals("\"Hello \\\"World\\\"\"", escapedWriter.toString());
    }

    @Test
    public void testNewline() {
        final String testMsg = "Hello World\r\n, and others\b\t\f";
        serializer.toJson(testMsg, escapedWriter);
        assertEquals("\"Hello World\\r\\n, and others\\b\\t\\f\"", escapedWriter.toString());
    }

    @Test
    public void testUnicodeSpecialCharacterEncoding() {
        final String testMsg = "Hello\u0085World\u008D";
        serializer.toJson(testMsg, escapedWriter);
        assertEquals("\"Hello\\u0085World\\u008D\"", escapedWriter.toString());
    }
}
