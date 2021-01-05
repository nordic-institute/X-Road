package ee.ria.xroad.common.logging;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LogbackCharacterConverter}
 */
public class LogbackCharacterConverterTest {

    @Test
    public void testIllegalCharReplacement() {
        assertEquals("hello_world", LogbackCharacterConverter.replaceLogForgingCharacters("hello\tworld"));
        assertEquals("hello_world", LogbackCharacterConverter.replaceLogForgingCharacters("hello\nworld"));
        assertEquals("hello_world", LogbackCharacterConverter.replaceLogForgingCharacters("hello\u0085world"));
        assertEquals("hello_world", LogbackCharacterConverter.replaceLogForgingCharacters("hello\u008Dworld"));
        assertEquals("hello world A", LogbackCharacterConverter.replaceLogForgingCharacters("hello world A"));
    }
}
