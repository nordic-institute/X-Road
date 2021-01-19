package ee.ria.xroad.common.validation;

import com.google.common.base.CharMatcher;

import java.io.IOException;
import java.io.Writer;

import static ee.ria.xroad.common.validation.SpringFirewallValidationRules.FORBIDDEN_BOM;
import static ee.ria.xroad.common.validation.SpringFirewallValidationRules.FORBIDDEN_ZWSP;

/**
 * Custom writer used to escape problematic characters
 */
public class EscapedWriter extends Writer {

    private final Writer writer;

    public EscapedWriter(final Writer writer) {
        this.writer = writer;
    }

    @Override
    public void write(final char[] buffer, final int offset, final int len)
            throws IOException {
        for (int i = offset; i < len; i++) {
            if (buffer[i] != '\u0020' && CharMatcher.whitespace()
                    .or(CharMatcher.breakingWhitespace())
                    .or(CharMatcher.javaIsoControl())
                    .or(CharMatcher.is(FORBIDDEN_BOM))
                    .or(CharMatcher.is(FORBIDDEN_ZWSP))
                    .matchesAnyOf(String.valueOf(buffer[i]))) {
                writer.write(String.format("\\u%04X", (int) buffer[i]));
            } else {
                writer.write(buffer[i]);
            }
        }
    }

    @Override
    public void flush()
            throws IOException {
        writer.flush();
    }

    @Override
    public void close()
            throws IOException {
        writer.close();
    }

    @Override
    public String toString() {
        return writer.toString();
    }
}
