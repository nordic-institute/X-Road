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
